package com.schwab.urlshortener.url.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Lifecycle invariants owned by the entity: ACTIVE has no deactivatedAt, DEACTIVATED always has one. */
class UrlMappingTest {

    private static final Instant T1 = Instant.parse("2026-10-01T07:30:00Z");
    private static final Instant T2 = Instant.parse("2026-10-01T08:00:00Z");

    private final UrlMapping mapping = new UrlMapping(
            "abcdef", "https://example.com/a", "https://example.com/a", "hash", Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void newMappingIsActiveAndCanRedirect() {
        assertThat(mapping.getStatus()).isEqualTo(UrlStatus.ACTIVE);
        assertThat(mapping.getDeactivatedAt()).isNull();
        assertThat(mapping.canRedirect()).isTrue();
    }

    @Test
    void deactivatingAnActiveMappingRecordsTheTimeAndStopsRedirects() {
        boolean changed = mapping.deactivate(T1);

        assertThat(changed).isTrue();
        assertThat(mapping.getStatus()).isEqualTo(UrlStatus.DEACTIVATED);
        assertThat(mapping.getDeactivatedAt()).isEqualTo(T1);
        assertThat(mapping.canRedirect()).isFalse();
    }

    @Test
    void repeatedDeactivationIsIdempotentAndKeepsTheOriginalTime() {
        mapping.deactivate(T1);

        boolean changed = mapping.deactivate(T2);

        assertThat(changed).isFalse();
        assertThat(mapping.getStatus()).isEqualTo(UrlStatus.DEACTIVATED);
        assertThat(mapping.getDeactivatedAt()).isEqualTo(T1);
    }

    @Test
    void deactivationRequiresATime() {
        assertThatThrownBy(() -> mapping.deactivate(null)).isInstanceOf(NullPointerException.class);
        assertThat(mapping.getStatus()).isEqualTo(UrlStatus.ACTIVE);
        assertThat(mapping.getDeactivatedAt()).isNull();
    }
}
