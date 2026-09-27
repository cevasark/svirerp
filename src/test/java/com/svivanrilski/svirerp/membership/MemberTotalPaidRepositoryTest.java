package com.svivanrilski.svirerp.membership;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the actual Flyway views and Hibernate formula on MariaDB 11.8, including JSON_TABLE.
 * Opt in with TOTAL_PAID_TEST_URL pointing to an empty, disposable local database named
 * svirerp_total_paid_test. All fixtures roll back after each test; Flyway schema persists.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate.SQL=WARN"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TOTAL_PAID_TEST_URL",
        matches = "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/svirerp_total_paid_test(?:_[a-zA-Z0-9_]+)?(?:\\?.*)?")
class MemberTotalPaidRepositoryTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TOTAL_PAID_TEST_URL"));
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", () -> "");
    }

    @Autowired private MemberRepository members;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private String followerType;
    private String memberType;
    private String revenueAccount;
    private String depositAccount;
    private String feeAccount;

    @BeforeEach
    void prepareAccountsAndTypes() {
        followerType = insert("membership_type", "name", "Total paid test Follower");
        memberType = insert("membership_type", "name", "Total paid test Member");
        revenueAccount = account("test-income", "revenue", "credit");
        depositAccount = account("test-cash", "asset", "debit");
        feeAccount = account("test-fees", "expense", "debit");
    }

    @Test
    void totalsEveryPaymentSourceOnceWithoutSubtractingProcessingFees() {
        PersonMember payer = personMember("payer@example.test", memberType, "active");
        income(payer.personId(), "cash", "40", "0", "posted");
        income(payer.personId(), "check", "25", "0", "posted");
        income(payer.personId(), "zelle", "30", "0", "posted");
        memberPayment(payer.memberId(), "50", "cash", "completed", null);

        String stripeJournal = income(payer.personId(), "stripe", "100", "3", "posted");
        String stripeEventId = "evt-test-all-sources";
        String stripeContribution = memberPayment(payer.memberId(), "100", "stripe", "completed", stripeEventId);
        String stripe = stripe(payer.personId(), null, "100", "processed", stripeJournal, stripeEventId);
        jdbc.update("UPDATE stripe_webhook_event SET member_payment_id = ? WHERE id = ?", stripeContribution, stripe);
        // An integration contribution repaired by external reference before its direct link is repaired.
        memberPayment(payer.memberId(), "100", "stripe", "completed", stripeEventId);

        String zeffyJournal = income(payer.personId(), "zeffy", "200", "0", "posted");
        String zeffy = zeffy(payer.personId(), "200", "{}", "succeeded", "none");
        String zeffyContribution = memberPayment(payer.memberId(), "200", "zeffy", "completed", zeffy);
        jdbc.update("UPDATE zeffy_payment SET journal_entry_id = ?, member_payment_id = ? WHERE id = ?",
                zeffyJournal, zeffyContribution, zeffy);
        memberPayment(payer.memberId(), "200", "zeffy", "completed", zeffy);

        assertTotal(payer.memberId(), "445.00");

        // A locally recorded partial Stripe refund reduces gross payments, independently of fees.
        reversal(payer.personId(), "stripe", "10", stripeJournal);
        assertTotal(payer.memberId(), "435.00");
    }

    @Test
    void netsSnapshotAndRetainedRefundsAndDisputesWithoutCountingCorrectionsTwice() {
        PersonMember payer = personMember("losses@example.test", memberType, "active");
        String original = income(payer.personId(), "zeffy", "100", "0", "posted");
        String payment = zeffy(payer.personId(), "100", """
                {"refunds":[
                  {"id":"snapshot-refund","amount":1000,"currency":"USD","status":"succeeded"},
                  {"id":"snapshot-refund","amount":1000,"currency":"USD","status":"succeeded"},
                  {"id":"pending-refund","amount":5000,"currency":"USD","status":"pending"},
                  {"id":"failed-refund","amount":5000,"currency":"USD","status":"failed"}],
                 "dispute":{"id":"lost-dispute","amount":500,"currency":"USD","status":"lost"}}
                """, "succeeded", "partial");
        jdbc.update("UPDATE zeffy_payment SET journal_entry_id = ? WHERE id = ?", original, payment);
        String refundCorrection = reversal(payer.personId(), "zeffy", "10", original);
        refund(payment, "snapshot-refund", "10", "succeeded", refundCorrection);
        refund(payment, "retained-refund", "7", "succeeded", null);
        refund(payment, "retained-pending", "60", "pending", null);
        String disputeCorrection = reversal(payer.personId(), "zeffy", "5", original);
        insert("zeffy_dispute", "zeffy_dispute_id", "lost-dispute", "zeffy_payment_record_id", payment,
                "amount", "5", "currency", "USD", "status", "lost", "dispute_created_at", "2020-01-01 00:00:00",
                "correction_status", "CORRECTED", "correction_journal_entry_id", disputeCorrection,
                "first_seen_at", "2020-01-01 00:00:00", "last_seen_at", "2020-01-01 00:00:00");

        assertTotal(payer.memberId(), "78.00");
    }

    @Test
    void latestRefundSnapshotOverridesOlderLifecycleStatus() {
        PersonMember payer = personMember("snapshot@example.test", followerType, "active");
        String payment = zeffy(payer.personId(), "100", """
                {"refunds":[{"id":"changed-refund","amount":1000,"currency":"USD","status":"failed"}]}
                """, "succeeded", "none");
        refund(payment, "changed-refund", "10", "succeeded", null);

        assertTotal(payer.memberId(), "100.00");
    }

    @Test
    void zeroIncludesFollowersAndExcludesPendingFailedVoidAndFullyRefundedPayments() {
        PersonMember payer = personMember("zero@example.test", followerType, "active");
        memberPayment(payer.memberId(), "25", "cash", "pending", null);
        memberPayment(payer.memberId(), "25", "check", "failed", null);
        memberPayment(payer.memberId(), "25", "cash", "refunded", null);
        zeffy(payer.personId(), "80", "{}", "pending", "none");
        zeffy(payer.personId(), "80", "{}", "failed", "none");
        zeffy(payer.personId(), "80", "{}", "succeeded", "full");
        income(payer.personId(), "cash", "100", "0", "draft");
        income(payer.personId(), "check", "100", "0", "void");
        stripe(payer.personId(), null, "100", "ignored", null, "evt-ignored");
        assertTotal(payer.memberId(), "0.00");
        assertTotal(personMember("empty@example.test", memberType, "expired").memberId(), "0.00");
    }

    @Test
    void resolvesUnappliedPaymentsByContactUniqueEmailOrContributionLink() {
        PersonMember payer = personMember("identity@example.test", followerType, "active");
        String contact = "contact-total-paid";
        insert("zeffy_contact", "zeffy_contact_id", contact, "person_id", payer.personId(),
                "processing_status", "DELETED", "first_seen_source", "API_SYNC",
                "first_seen_at", "2020-01-01 00:00:00", "deleted_at", "2021-01-01 00:00:00");
        String contactPayment = zeffy(null, "20", "{}", "succeeded", "none");
        jdbc.update("UPDATE zeffy_payment SET contact_id = ?, processing_status = 'IGNORED' WHERE id = ?",
                contact, contactPayment);
        String emailPayment = zeffy(null, "30", "{}", "succeeded", "none");
        jdbc.update("UPDATE zeffy_payment SET buyer_email = ?, processing_status = 'NEEDS_MAPPING' WHERE id = ?",
                " IDENTITY@example.test ", emailPayment);
        String linkedPayment = zeffy(null, "40", "{}", "succeeded", "none");
        String contribution = memberPayment(payer.memberId(), "40", "zeffy", "completed", linkedPayment);
        jdbc.update("UPDATE zeffy_payment SET member_payment_id = ? WHERE id = ?", contribution, linkedPayment);
        stripe(null, " IDENTITY@example.test ", "50", "needs_mapping", null, "evt-awaiting-map");
        String stripeLinked = stripe(null, null, "60", "error", null, "evt-contribution-link");
        String stripeContribution = memberPayment(payer.memberId(), "60", "stripe", "completed", "evt-contribution-link");
        jdbc.update("UPDATE stripe_webhook_event SET member_payment_id = ? WHERE id = ?", stripeContribution, stripeLinked);

        assertTotal(payer.memberId(), "200.00");
    }

    @Test
    void refusesAmbiguousEmailAndPreservesExplicitIdentityOverChangedEmail() {
        PersonMember first = personMember("ambiguous@example.test", memberType, "active");
        PersonMember second = personMember(" ambiguous@example.test", memberType, "active");
        String unlinked = zeffy(null, "100", "{}", "succeeded", "none");
        jdbc.update("UPDATE zeffy_payment SET buyer_email = 'AMBIGUOUS@example.test' WHERE id = ?", unlinked);
        stripe(null, "ambiguous@example.test", "100", "needs_mapping", null, "evt-ambiguous");
        String explicit = zeffy(first.personId(), "25", "{}", "succeeded", "none");
        jdbc.update("UPDATE zeffy_payment SET buyer_email = ? WHERE id = ?", " ambiguous@example.test", explicit);

        assertTotal(first.memberId(), "25.00");
        assertTotal(second.memberId(), "0.00");
    }

    @Test
    void resolvesSourceIdentityThroughUniqueContributionReferencesBeforeLinksAreRepaired() {
        PersonMember payer = personMember("references@example.test", followerType, "active");
        String zeffy = zeffy(null, "40", "{}", "succeeded", "none");
        memberPayment(payer.memberId(), "40", "zeffy", "completed", zeffy);
        stripe(null, null, "60", "needs_mapping", null, "evt-reference-only");
        memberPayment(payer.memberId(), "60", "stripe", "completed", "evt-reference-only");

        assertTotal(payer.memberId(), "100.00");
    }

    @Test
    void paymentTotalBelongsToPersonAcrossHistoricalMembershipRows() {
        PersonMember payer = personMember("historical@example.test", followerType, "active");
        UUID historicalMember = member(payer.personId(), memberType, "expired");
        memberPayment(payer.memberId(), "30", "cash", "completed", null);
        memberPayment(historicalMember, "70", "check", "completed", null);

        assertTotal(payer.memberId(), "100.00");
        assertTotal(historicalMember, "100.00");
    }

    @Test
    void sortsComputedTotalBeforePagingAndRetainsMembershipFilters() {
        PersonMember low = personMember("low@example.test", memberType, "active");
        PersonMember high = personMember("high@example.test", memberType, "active");
        PersonMember middle = personMember("middle@example.test", followerType, "active");
        PersonMember expired = personMember("expired@example.test", memberType, "expired");
        memberPayment(low.memberId(), "10", "cash", "completed", null);
        memberPayment(high.memberId(), "90", "cash", "completed", null);
        memberPayment(middle.memberId(), "50", "cash", "completed", null);
        memberPayment(expired.memberId(), "200", "cash", "completed", null);
        Sort descending = Sort.by(Sort.Order.desc("totalPaid"), Sort.Order.asc("id"));

        var firstPage = members.findAll(PageRequest.of(0, 2, descending));
        var secondPage = members.findAll(PageRequest.of(1, 2, descending));
        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getContent()).extracting(Member::getId).containsExactly(expired.memberId(), high.memberId());
        assertThat(secondPage.getContent()).extracting(Member::getId).containsExactly(middle.memberId(), low.memberId());
        assertThat(members.findByStatus("active", PageRequest.of(0, 1, descending)).getContent())
                .extracting(Member::getId).containsExactly(high.memberId());
        assertThat(members.findByMembershipTypeId(UUID.fromString(followerType), PageRequest.of(0, 1, descending)).getContent())
                .extracting(Member::getId).containsExactly(middle.memberId());
        var filtered = members.findByStatusAndMembershipTypeId("active", UUID.fromString(memberType),
                PageRequest.of(1, 1, descending));
        assertThat(filtered.getTotalElements()).isEqualTo(2);
        assertThat(filtered.getContent()).extracting(Member::getId).containsExactly(low.memberId());
        assertThat(members.findAll(PageRequest.of(0, 1, Sort.by("totalPaid", "id"))).getContent())
                .extracting(Member::getId).containsExactly(low.memberId());
    }

    private void assertTotal(UUID memberId, String expected) {
        entityManager.clear();
        assertThat(members.findById(memberId).orElseThrow().getTotalPaid()).isEqualByComparingTo(expected);
    }

    private PersonMember personMember(String email, String type, String status) {
        String personId = insert("person", "first_name", "Total", "last_name", "Paid", "email", email);
        return new PersonMember(personId, member(personId, type, status));
    }

    private UUID member(String personId, String type, String status) {
        return UUID.fromString(insert("member", "person_id", personId, "membership_type_id", type,
                "join_date", "2020-01-01", "expiry_date", "2021-01-01", "status", status));
    }

    private String account(String number, String type, String normalBalance) {
        return insert("account", "account_number", number, "account_name", number,
                "account_type", type, "normal_balance", normalBalance);
    }

    private String memberPayment(UUID memberId, String amount, String method, String status, String reference) {
        return insert("member_payment", "member_id", memberId.toString(), "amount", amount,
                "payment_date", "2020-01-01", "payment_method", method, "status", status, "transaction_ref", reference);
    }

    private String income(String personId, String method, String amount, String fee, String status) {
        BigDecimal gross = new BigDecimal(amount);
        BigDecimal processingFee = new BigDecimal(fee);
        String entry = insert("journal_entry", "entry_date", "2020-01-01", "description", "Test income",
                "status", status, "payer_id", personId, "payment_method", method,
                "total_debit", gross, "total_credit", gross, "category_account_id", revenueAccount);
        line(entry, revenueAccount, BigDecimal.ZERO, gross);
        line(entry, depositAccount, gross.subtract(processingFee), BigDecimal.ZERO);
        if (processingFee.signum() > 0) line(entry, feeAccount, processingFee, BigDecimal.ZERO);
        return entry;
    }

    private String reversal(String personId, String method, String amount, String originalEntry) {
        BigDecimal refund = new BigDecimal(amount);
        String entry = insert("journal_entry", "entry_date", "2020-01-01", "description", "Test refund",
                "status", "posted", "entry_type", "reversing", "payer_id", personId, "payment_method", method,
                "total_debit", refund, "total_credit", refund, "corrects_journal_entry_id", originalEntry);
        line(entry, revenueAccount, refund, BigDecimal.ZERO);
        line(entry, depositAccount, BigDecimal.ZERO, refund);
        return entry;
    }

    private void line(String entryId, String accountId, BigDecimal debit, BigDecimal credit) {
        insert("journal_line", "journal_entry_id", entryId, "account_id", accountId,
                "debit_amount", debit, "credit_amount", credit);
    }

    private String zeffy(String personId, String amount, String payload, String status, String refundStatus) {
        String externalId = UUID.randomUUID().toString();
        String id = insert("zeffy_payment", "zeffy_payment_id", externalId, "person_id", personId,
                "amount", amount, "currency", "USD", "status", status, "refund_status", refundStatus,
                "latest_payload", payload, "processing_status", "RECEIVED", "first_seen_source", "API_SYNC",
                "first_seen_at", "2020-01-01 00:00:00");
        // Keep fixture external IDs equal to row IDs to make contribution references clear.
        jdbc.update("UPDATE zeffy_payment SET zeffy_payment_id = ? WHERE id = ?", id, id);
        return id;
    }

    private String stripe(String personId, String email, String amount, String status, String journalId, String externalId) {
        return insert("stripe_webhook_event", "stripe_event_id", externalId,
                "event_type", "checkout.session.completed", "person_id", personId, "email", email,
                "amount", amount, "status", status, "journal_entry_id", journalId, "payload", "{}");
    }

    private void refund(String paymentId, String externalId, String amount, String status, String correctionId) {
        insert("zeffy_refund", "zeffy_refund_id", externalId, "zeffy_payment_record_id", paymentId,
                "amount", amount, "currency", "USD", "status", status, "refund_created_at", "2020-01-01 00:00:00",
                "correction_status", correctionId == null ? "NOT_REQUIRED" : "CORRECTED",
                "correction_journal_entry_id", correctionId, "first_seen_at", "2020-01-01 00:00:00",
                "last_seen_at", "2020-01-01 00:00:00");
    }

    /** Table/column names are constants in this fixture; all values remain bound parameters. */
    private String insert(String table, Object... columnsAndValues) {
        String id = UUID.randomUUID().toString();
        List<String> columns = new ArrayList<>(List.of("id"));
        List<Object> values = new ArrayList<>(List.of(id));
        for (int index = 0; index < columnsAndValues.length; index += 2) {
            columns.add((String) columnsAndValues[index]);
            values.add(columnsAndValues[index + 1]);
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        jdbc.update("INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ")",
                values.toArray());
        return id;
    }

    private record PersonMember(String personId, UUID memberId) {}
}
