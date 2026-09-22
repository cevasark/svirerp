package com.svivanrilski.svirerp.zeffyimport;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.finance.FinanceService;
import com.svivanrilski.svirerp.finance.Fund;
import com.svivanrilski.svirerp.finance.JournalEntry;
import com.svivanrilski.svirerp.finance.RecordIncomeRequest;
import com.svivanrilski.svirerp.membership.Member;
import com.svivanrilski.svirerp.membership.MemberPayment;
import com.svivanrilski.svirerp.membership.MemberPaymentRepository;
import com.svivanrilski.svirerp.membership.MembershipService;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.util.UUID;

/**
 * Applies a single {@link ZeffyImportRow} — called once per row by {@link ZeffyImportService}, a
 * different bean, so each row gets its own transaction (same idiom as
 * MembershipService#importOrUpdateMemberFromRow / MemberImportService: a shared big transaction
 * risks Hibernate marking the whole batch rollback-only after one bad row).
 */
@Service
@RequiredArgsConstructor
public class ZeffyImportRowApplier {

    private static final int OUTCOME_DETAIL_MAX_LENGTH = 500;

    private final ZeffyImportRowRepository rowRepo;
    private final ZeffyCampaignMappingRepository mappingRepo;
    private final PersonService personService;
    private final MembershipService membershipService;
    private final MemberPaymentRepository memberPaymentRepo;
    private final FinanceService financeService;

    /**
     * Only rows still in outcome 'ready' or 'unmapped_campaign' are processed (idempotent no-op
     * otherwise — e.g. if commit is retried after a partial failure). A campaign mapping added
     * after preview is re-checked here, since commit is the authoritative point of no return.
     * "Donation" rows earn membership tier credit and post to {@code donationAccountId}; "Ticket"
     * rows (a ticket purchase isn't a membership contribution) skip the Member/MemberPayment/tier
     * pipeline entirely and post straight to {@code ticketAccountId} instead — <b>unless</b> the
     * row's campaign mapping has {@code isMembershipPayment=true} (Zeffy implements fixed-price
     * membership registration as a Ticket-category product, not Donation, so some genuinely-dues
     * campaigns need this override), in which case it's treated as a Donation row despite its raw
     * category.
     */
    @Transactional
    public void applyRow(UUID rowId, UUID depositAccountId, UUID donationAccountId, UUID ticketAccountId) {
        ZeffyImportRow row = rowRepo.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy import row not found: " + rowId));
        if (!"ready".equals(row.getOutcome()) && !"unmapped_campaign".equals(row.getOutcome())) {
            return;
        }

        Fund fund = row.getFund();
        // Always looked up (not just when fund isn't already stamped) — isMembershipPayment below
        // needs it regardless, and this is a single cheap indexed lookup either way.
        ZeffyCampaignMapping mapping = (row.getCampaignTitle() != null && !row.getCampaignTitle().isBlank())
                ? mappingRepo.findByOrgIdAndCampaignTitleIgnoreCase(row.getOrg().getId(), row.getCampaignTitle())
                        .orElse(null)
                : null;
        if (fund == null && row.getCampaignTitle() != null && !row.getCampaignTitle().isBlank()) {
            fund = mapping != null ? mapping.getFund() : null;
            if (fund == null) {
                row.setOutcome("unmapped_campaign");
                row.setOutcomeDetail(row.getCampaignTitle());
                rowRepo.save(row);
                return;
            }
        }

        if (row.getEmail() == null || row.getEmail().isBlank()) {
            throw new IllegalArgumentException("Row " + row.getRowNumber() + " has no email — cannot match/create a person");
        }
        if (row.getAmount() == null || row.getTransactionDate() == null) {
            throw new IllegalArgumentException("Row " + row.getRowNumber() + " is missing amount or transaction date");
        }

        boolean isNewPerson = personService.findByEmailIfExists(row.getEmail()).isEmpty();
        Person person = isNewPerson
                ? personService.create(Person.builder()
                        .firstName(row.getFirstName() != null ? row.getFirstName() : "Unknown")
                        .lastName(row.getLastName() != null ? row.getLastName() : "Unknown")
                        .email(row.getEmail())
                        .build())
                : personService.findByEmail(row.getEmail());

        // Zeffy implements fixed-price membership registration as a Ticket-category product, not
        // Donation — a campaign explicitly flagged as a membership payment overrides the raw
        // category, so it still goes through the Member/MemberPayment/tier pipeline below.
        boolean isTicket = "Ticket".equals(row.getCategory())
                && !(mapping != null && Boolean.TRUE.equals(mapping.getIsMembershipPayment()));
        boolean isNewMember = false;
        Member recomputed = null;
        MemberPayment payment = null;

        if (!isTicket) {
            isNewMember = !membershipService.hasMembership(person.getId(), row.getOrg().getId());
            Member member = membershipService.findOrCreateFollowerMember(
                    person.getId(), row.getOrg().getId(), row.getTransactionDate());

            payment = memberPaymentRepo.save(MemberPayment.builder()
                    .member(member)
                    .amount(row.getAmount())
                    .paymentDate(row.getTransactionDate())
                    .paymentMethod("zeffy")
                    .transactionRef(row.getTransactionId())
                    .status("completed")
                    .notes(buildNotes(row))
                    .build());

            recomputed = membershipService.recomputeTier(member.getId());
        }

        // A $0 row (e.g. a free-RSVP Zeffy signup) has no money to post — FinanceService.recordIncome
        // requires a strictly positive debit/credit on a real journal line (chk_journal_line_one_side),
        // and there's genuinely nothing to record on the Finance page for it. Still create the
        // Person/Member/MemberPayment above so it counts toward tier computation.
        JournalEntry entry = row.getAmount().signum() > 0
                ? financeService.recordIncome(new RecordIncomeRequest(
                        row.getOrg().getId(),
                        row.getTransactionDate(),
                        row.getAmount(),
                        buildDescription(row),
                        isTicket ? ticketAccountId : donationAccountId,
                        depositAccountId,
                        fund != null ? fund.getId() : null,
                        person.getId(),
                        null,
                        "zeffy",
                        null,
                        null,
                        null))
                : null;

        row.setPerson(person);
        row.setMember(recomputed);
        row.setMemberPayment(payment);
        row.setJournalEntry(entry);
        row.setFund(fund);
        row.setIsNewPerson(isNewPerson);
        row.setIsNewMember(isNewMember);
        row.setOutcome("committed");
        row.setOutcomeDetail(null);
        rowRepo.save(row);
    }

    /**
     * One-time backfill for a Ticket row that was committed *before* its campaign was flagged as a
     * membership payment (see ZeffyImportService#reprocessMembershipRows) — runs the same
     * Member/MemberPayment/tier logic applyRow's Donation branch does, then reclassifies the
     * already-posted income from the ticket account to the donation account. Idempotent: a no-op if
     * the row isn't committed or already has a member (so it's safe to re-run after a partial
     * failure, or if called again after the row is already fixed).
     */
    @Transactional
    public void reprocessAsMembership(UUID rowId, UUID donationAccountId, UUID ticketAccountId) {
        ZeffyImportRow row = rowRepo.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy import row not found: " + rowId));
        if (!"committed".equals(row.getOutcome()) || row.getMember() != null) {
            return;
        }
        Person person = row.getPerson();
        if (person == null) {
            throw new IllegalStateException("Row " + row.getRowNumber() + " has no linked person to reprocess");
        }

        Member member = membershipService.findOrCreateFollowerMember(
                person.getId(), row.getOrg().getId(), row.getTransactionDate());

        MemberPayment payment = memberPaymentRepo.save(MemberPayment.builder()
                .member(member)
                .amount(row.getAmount())
                .paymentDate(row.getTransactionDate())
                .paymentMethod("zeffy")
                .transactionRef(row.getTransactionId())
                .status("completed")
                .notes(buildNotes(row) + " — reclassified from Ticket to membership")
                .build());

        Member recomputed = membershipService.recomputeTier(member.getId());

        // Same $0-row guard as applyRow: a $0 Ticket row never posted a JournalEntry, so there's
        // nothing to reclassify.
        if (row.getAmount().signum() > 0) {
            String campaign = row.getCampaignTitle() != null && !row.getCampaignTitle().isBlank()
                    ? row.getCampaignTitle() : "Zeffy donation";
            financeService.reclassifyIncome(row.getOrg().getId(), row.getTransactionDate(), row.getAmount(),
                    "Reclassify Zeffy Ticket → membership: " + campaign,
                    ticketAccountId, donationAccountId);
        }

        row.setMember(recomputed);
        row.setMemberPayment(payment);
        rowRepo.save(row);
    }

    /** Runs in its own transaction so it still commits even though the failed applyRow() call rolled back. */
    @Transactional
    public void markRowError(UUID rowId, String detail) {
        ZeffyImportRow row = rowRepo.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Zeffy import row not found: " + rowId));
        row.setOutcome("error");
        row.setOutcomeDetail(truncate(detail));
        rowRepo.save(row);
    }

    private String buildNotes(ZeffyImportRow row) {
        StringBuilder notes = new StringBuilder("Imported from Zeffy");
        if (row.getCampaignTitle() != null && !row.getCampaignTitle().isBlank()) {
            notes.append(" — campaign: ").append(row.getCampaignTitle());
        }
        notes.append(" — transaction: ").append(row.getTransactionId());
        return notes.toString();
    }

    private String buildDescription(ZeffyImportRow row) {
        String prefix = "Ticket".equals(row.getCategory()) ? "Zeffy Ticket: " : "Zeffy: ";
        return (row.getCampaignTitle() != null && !row.getCampaignTitle().isBlank())
                ? prefix + row.getCampaignTitle()
                : "Zeffy donation";
    }

    private String truncate(String detail) {
        if (detail == null) return null;
        return detail.length() > OUTCOME_DETAIL_MAX_LENGTH ? detail.substring(0, OUTCOME_DETAIL_MAX_LENGTH) : detail;
    }
}
