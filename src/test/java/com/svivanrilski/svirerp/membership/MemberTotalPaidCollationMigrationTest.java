package com.svivanrilski.svirerp.membership;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reproduces an upgraded MariaDB installation: existing columns use general_ci, while newly
 * parsed JSON_TABLE text uses MariaDB 11.8's uca1400_ai_ci default. Requires the same disposable
 * local TOTAL_PAID_TEST_URL as MemberTotalPaidRepositoryTest. Creates and removes its own schema.
 */
@EnabledIfEnvironmentVariable(named = "TOTAL_PAID_TEST_URL",
        matches = "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/svirerp_total_paid_test(?:_[a-zA-Z0-9_]+)?(?:\\?.*)?")
class MemberTotalPaidCollationMigrationTest {

    private static final Pattern LOCAL_TEST_URL = Pattern.compile(
            "(jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+)/svirerp_total_paid_test(?:_[a-zA-Z0-9_]+)?(\\?.*)?");
    private static final String SCHEMA_PREFIX = "svirerp_total_paid_test_collation_";
    private static final String OLD_SESSION = "SET SESSION character_set_collations = 'utf8mb4=utf8mb4_general_ci', "
            + "collation_connection = 'utf8mb4_general_ci'";
    private static final String NEW_SESSION = "SET SESSION character_set_collations = 'utf8mb4=utf8mb4_uca1400_ai_ci', "
            + "collation_connection = 'utf8mb4_uca1400_ai_ci'";

    @Test
    void migratesAndReappliesViewsAcrossLegacyAndCurrentCollations() throws Exception {
        String configuredUrl = System.getenv("TOTAL_PAID_TEST_URL");
        var location = LOCAL_TEST_URL.matcher(configuredUrl);
        assertThat(location.matches()).as("Only a disposable local test database is allowed").isTrue();
        String schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        assertThat(schema).matches(SCHEMA_PREFIX + "[a-f0-9]{16}");
        String testUrl = location.group(1) + "/" + schema + (location.group(2) == null ? "" : location.group(2));

        try (Connection admin = DriverManager.getConnection(configuredUrl, "root", "")) {
            assumeTrue(admin.getMetaData().getDatabaseProductVersion().contains("MariaDB"),
                    "This regression models the MariaDB 11.8 collation change");
            boolean created = false;
            try {
                execute(admin, "CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
                created = true;
                // V1-V62 specify CHARSET without COLLATE, so the session's charset default must
                // model the older server too; changing only the schema default is insufficient.
                Flyway.configure().dataSource(testUrl, "root", "")
                        .locations("classpath:db/migration").target("62")
                        .baselineOnMigrate(false).initSql(OLD_SESSION).load().migrate();

                assertThat(text(admin, """
                        SELECT COLLATION_NAME FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'zeffy_payment' AND COLUMN_NAME = 'currency'
                        """, schema)).isEqualTo("utf8mb4_general_ci");
                execute(admin, "ALTER DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci");

                try (Connection current = DriverManager.getConnection(testUrl, "root", "")) {
                    execute(current, NEW_SESSION);
                    assertThat(text(current, """
                            SELECT COLLATION(snapshot.currency)
                            FROM JSON_TABLE('{"currency":"USD"}', '$'
                                COLUMNS (currency VARCHAR(10) PATH '$.currency')) snapshot
                            """)).isEqualTo("utf8mb4_uca1400_ai_ci");

                    var result = Flyway.configure().dataSource(testUrl, "root", "")
                            .locations("classpath:db/migration").target("63")
                            .baselineOnMigrate(false).initSql(NEW_SESSION).load().migrate();
                    assertThat(result.migrationsExecuted).isEqualTo(1);

                    String person = UUID.randomUUID().toString();
                    String payment = UUID.randomUUID().toString();
                    execute(current, "INSERT INTO person (id, first_name, last_name, email) VALUES (?, 'Mixed', 'Collation', ?)",
                            person, "mixed-collation@example.test");
                    execute(current, """
                            INSERT INTO zeffy_payment
                                (id, zeffy_payment_id, person_id, amount, currency, status, refund_status,
                                 latest_payload, processing_status, first_seen_source, first_seen_at)
                            VALUES (?, ?, ?, 100, 'USD', 'succeeded', 'partial', ?, 'RECEIVED', 'API_SYNC', NOW())
                            """, payment, "payment-mixed-collation", person, """
                            {"refunds":[{"id":"refund-in-both","amount":2000,"currency":"USD","status":"succeeded"}]}
                            """);
                    refund(current, payment, "refund-in-both", "20.00");
                    refund(current, payment, "refund-in-lifecycle-only", "5.00");
                    assertTotals(current, person, payment);

                    // A repaired Flyway migration may be replayed after some DDL has already
                    // committed. Replacing all views must preserve the same calculated total.
                    ScriptUtils.executeSqlScript(current,
                            new ClassPathResource("db/migration/V63__member_total_paid.sql"));
                    assertTotals(current, person, payment);
                }
            } finally {
                if (created) {
                    // Only this invocation's generated schema can be removed, never the URL's database.
                    assertThat(schema).matches(SCHEMA_PREFIX + "[a-f0-9]{16}");
                    execute(admin, "DROP DATABASE `" + schema + "`");
                }
            }
        }
    }

    private void assertTotals(Connection connection, String person, String payment) throws SQLException {
        assertThat(new BigDecimal(text(connection, "SELECT amount FROM zeffy_payment_loss WHERE payment_id = ?", payment)))
                .isEqualByComparingTo("25.00");
        assertThat(new BigDecimal(text(connection, "SELECT total_paid FROM person_payment_total WHERE person_id = ?", person)))
                .isEqualByComparingTo("75.00");
    }

    private void refund(Connection connection, String payment, String externalId, String amount) throws SQLException {
        execute(connection, """
                INSERT INTO zeffy_refund
                    (id, zeffy_refund_id, zeffy_payment_record_id, amount, currency, status,
                     refund_created_at, correction_status, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, 'USD', 'succeeded', NOW(), 'NOT_REQUIRED', NOW(), NOW())
                """, UUID.randomUUID().toString(), externalId, payment, new BigDecimal(amount));
    }

    private void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            statement.execute();
        }
    }

    private String text(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).as("Expected a result for %s", sql).isTrue();
                return result.getString(1);
            }
        }
    }
}
