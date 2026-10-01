package com.schwab.urlshortener.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base for black-box integration tests: full application on a random port, real H2 schema from Flyway,
 * and an empty database before every test so tests are independent and order-free.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected TestApiClient api;

    @BeforeEach
    void resetState() {
        DatabaseCleaner.clean(jdbc);
        api = new TestApiClient(port);
    }

    /** A well-formed short code guaranteed not to be the given one. */
    protected static String unknownCodeOtherThan(String shortCode) {
        return "ffffff".equals(shortCode) ? "eeeeee" : "ffffff";
    }

    protected int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
