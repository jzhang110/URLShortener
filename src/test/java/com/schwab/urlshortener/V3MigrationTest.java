package com.schwab.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Upgrades a database that already holds V2-era data to the latest schema, as an existing deployment
 * would, and checks nothing is lost (AC10). Uses its own in-memory database, outside the Spring context.
 */
class V3MigrationTest {

    private static final String URL =
            "jdbc:h2:mem:v3-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test
    void existingMappingsBecomeActiveAndKeepTheirClicks() throws SQLException {
        Flyway.configure().dataSource(URL, "sa", "").target("2").load().migrate();
        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO url_mapping (short_code, destination_url, normalized_url, normalized_url_hash, created_at)
                    VALUES ('abcdef', 'https://example.com/', 'https://example.com/', 'hash-1', CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO click_event (url_mapping_id, clicked_at)
                    SELECT id, CURRENT_TIMESTAMP FROM url_mapping WHERE short_code = 'abcdef'
                    """);

            Flyway.configure().dataSource(URL, "sa", "").load().migrate();

            try (ResultSet mapping = statement.executeQuery(
                    "SELECT status, deactivated_at FROM url_mapping WHERE short_code = 'abcdef'")) {
                assertThat(mapping.next()).isTrue();
                assertThat(mapping.getString("status")).isEqualTo("ACTIVE");
                assertThat(mapping.getObject("deactivated_at")).isNull();
                assertThat(mapping.next()).as("no rows added or duplicated").isFalse();
            }
            try (ResultSet clicks = statement.executeQuery("""
                    SELECT COUNT(*) FROM click_event c JOIN url_mapping m ON c.url_mapping_id = m.id
                    WHERE m.short_code = 'abcdef'
                    """)) {
                clicks.next();
                assertThat(clicks.getInt(1)).isEqualTo(1);
            }
        }
    }
}
