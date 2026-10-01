package com.schwab.urlshortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.url.domain.InvalidShortCodeException;
import com.schwab.urlshortener.url.domain.ShortCodeNotFoundException;
import com.schwab.urlshortener.url.domain.UrlMapping;
import com.schwab.urlshortener.url.domain.UrlStatus;
import com.schwab.urlshortener.url.repository.UrlMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UrlLifecycleServiceTest {

    // Nanosecond clock reading; the service must truncate it to the database's microsecond precision.
    private static final Instant NOW = Instant.parse("2026-10-01T07:30:00.123456789Z");
    private static final Instant TRUNCATED_TO_MICROS = Instant.parse("2026-10-01T07:30:00.123456Z");

    private final UrlMappingRepository repository = mock(UrlMappingRepository.class);
    private final UrlLifecycleService service =
            new UrlLifecycleService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void deactivatesAnActiveMappingAtTheClockTimeTruncatedToMicros() {
        when(repository.findForUpdateByShortCode("abcdef")).thenReturn(Optional.of(activeMapping()));

        DeactivateResult result = service.deactivate("abcdef");

        assertThat(result).isEqualTo(new DeactivateResult("abcdef", UrlStatus.DEACTIVATED, TRUNCATED_TO_MICROS));
    }

    @Test
    void repeatedDeactivationKeepsTheOriginalTime() {
        UrlMapping mapping = activeMapping();
        Instant original = Instant.parse("2026-09-30T12:00:00Z");
        mapping.deactivate(original);
        when(repository.findForUpdateByShortCode("abcdef")).thenReturn(Optional.of(mapping));

        DeactivateResult result = service.deactivate("abcdef");

        assertThat(result).isEqualTo(new DeactivateResult("abcdef", UrlStatus.DEACTIVATED, original));
    }

    @Test
    void unknownShortCodeIsNotFound() {
        when(repository.findForUpdateByShortCode("abcdef")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate("abcdef")).isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    void malformedShortCodeIsRejectedBeforeAnyLookup() {
        for (String code : new String[] {"abc", "ABCDEF", "abcdefg", "null", " ", ""}) {
            assertThatThrownBy(() -> service.deactivate(code)).isInstanceOf(InvalidShortCodeException.class);
        }
        verifyNoInteractions(repository);
    }

    private static UrlMapping activeMapping() {
        return new UrlMapping("abcdef", "https://example.com/a", "https://example.com/a", "hash",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
