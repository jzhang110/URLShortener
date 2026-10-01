package com.schwab.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schwab.urlshortener.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The application context only starts if Hibernate's ddl-auto=validate accepts the Flyway-created
 * schema, so reaching these tests proves entity/migration consistency. The tests then pin the
 * constraints that carry correctness guarantees.
 */
class SchemaMigrationTest extends IntegrationTest {

    @Test
    void flywayAppliedAllMigrationsSuccessfully() {
        List<String> applied = jdbc.queryForList(
                "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\" = TRUE", String.class);
        assertThat(applied).contains("1", "2", "3");
    }

    @Test
    void lifecycleColumnsHaveTheExpectedShape() {
        Map<String, Object> status = column("status");
        assertThat(((Number) status.get("character_maximum_length")).intValue()).isEqualTo(32);
        assertThat(status.get("is_nullable")).isEqualTo("NO");
        assertThat(column("deactivated_at").get("is_nullable")).isEqualTo("YES");
    }

    @Test
    void rowsInsertedWithoutAStatusDefaultToActive() {
        insertMapping("abcdef", "hash-1");

        Map<String, Object> row = jdbc.queryForMap("SELECT status, deactivated_at FROM url_mapping");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("deactivated_at")).isNull();
    }

    @Test
    void databaseEnforcesTheLifecycleInvariant() {
        insertMapping("abcdef", "hash-1");

        assertThatThrownBy(() -> jdbc.update("UPDATE url_mapping SET status = 'DEACTIVATED'"))
                .as("DEACTIVATED without a deactivation time")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE url_mapping SET deactivated_at = CURRENT_TIMESTAMP"))
                .as("ACTIVE with a deactivation time")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE url_mapping SET status = 'EXPIRED', deactivated_at = CURRENT_TIMESTAMP"))
                .as("a status the schema does not define")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void normalizedUrlFitsMaximumInputPlusTheRootSlashAddedByN5() {
        Map<String, Object> column = jdbc.queryForMap("""
                SELECT character_maximum_length, is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'url_mapping' AND column_name = 'normalized_url'
                """);
        assertThat(((Number) column.get("character_maximum_length")).intValue()).isEqualTo(2049);
        assertThat(column.get("is_nullable")).isEqualTo("NO");
    }

    @Test
    void uniqueConstraintsAndForeignKeyExist() {
        List<String> constraints = jdbc.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name IN ('url_mapping', 'click_event')
                """, String.class);
        assertThat(constraints).contains(
                "uk_url_mapping_short_code",
                "uk_url_mapping_normalized_url_hash",
                "ck_url_mapping_lifecycle",
                "fk_click_event_url_mapping");
    }

    @Test
    void clickEventIndexExists() {
        List<String> indexes = jdbc.queryForList("""
                SELECT index_name FROM information_schema.indexes
                WHERE table_schema = 'public' AND table_name = 'click_event'
                """, String.class);
        assertThat(indexes).contains("ix_click_event_mapping_clicked_at");
    }

    @Test
    void databaseRejectsDuplicateShortCode() {
        insertMapping("abcdef", "hash-1");
        assertThatThrownBy(() -> insertMapping("abcdef", "hash-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateNormalizedUrlHash() {
        insertMapping("abcdef", "same-hash");
        assertThatThrownBy(() -> insertMapping("fedcba", "same-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsClickForUnknownMapping() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO click_event (url_mapping_id, clicked_at) VALUES (999999, CURRENT_TIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Map<String, Object> column(String name) {
        return jdbc.queryForMap("""
                SELECT character_maximum_length, is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'url_mapping' AND column_name = ?
                """, name);
    }

    private void insertMapping(String shortCode, String hash) {
        jdbc.update("""
                INSERT INTO url_mapping (short_code, destination_url, normalized_url, normalized_url_hash, created_at)
                VALUES (?, 'https://example.com/', 'https://example.com/', ?, CURRENT_TIMESTAMP)
                """, shortCode, hash);
    }
}
