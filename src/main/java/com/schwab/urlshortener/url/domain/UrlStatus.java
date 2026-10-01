package com.schwab.urlshortener.url.domain;

/**
 * Lifecycle state of a {@link UrlMapping} (ADR 0009). Persisted by name, never by ordinal, and
 * constrained by {@code ck_url_mapping_lifecycle}, so adding a state is a deliberate migration.
 * Each state declares whether it permits redirects; callers ask {@link UrlMapping#canRedirect()}
 * rather than comparing states.
 */
public enum UrlStatus {
    ACTIVE(true),
    DEACTIVATED(false);

    private final boolean allowsRedirect;

    UrlStatus(boolean allowsRedirect) {
        this.allowsRedirect = allowsRedirect;
    }

    public boolean allowsRedirect() {
        return allowsRedirect;
    }
}
