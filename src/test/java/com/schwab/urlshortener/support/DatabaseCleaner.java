package com.schwab.urlshortener.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Empties all application tables, children first to respect the foreign key. */
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM click_event");
        jdbc.update("DELETE FROM url_mapping");
    }
}
