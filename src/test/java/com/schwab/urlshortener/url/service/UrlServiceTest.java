package com.schwab.urlshortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.domain.InvalidShortCodeException;
import com.schwab.urlshortener.url.domain.InvalidUrlException;
import com.schwab.urlshortener.url.domain.ShortCodeDeactivatedException;
import com.schwab.urlshortener.url.domain.ShortCodeExhaustedException;
import com.schwab.urlshortener.url.domain.ShortCodeNotFoundException;
import com.schwab.urlshortener.url.domain.UrlDeactivatedException;
import com.schwab.urlshortener.url.domain.UrlMapping;
import com.schwab.urlshortener.url.generation.Sha256;
import com.schwab.urlshortener.url.generation.ShortCodeGenerator;
import com.schwab.urlshortener.url.repository.UrlMappingRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class UrlServiceTest {

    private static final String URL = "https://example.com/page";
    private static final int MAX_ATTEMPTS = 3;

    private final ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
    private final UrlMappingRepository repository = mock(UrlMappingRepository.class);
    private UrlService service;

    @BeforeEach
    void setUp() {
        ShortenerProperties properties =
                new ShortenerProperties(URI.create("https://sho.rt"), List.of("localhost"), MAX_ATTEMPTS, 2048);
        service = new UrlService(new UrlValidator(properties), List.of(new SelfReferencePolicy(properties)),
                new UrlNormalizer(), generator, repository, properties);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(generator.generate(anyString(), anyInt()))
                .thenAnswer(invocation -> "00000" + invocation.getArgument(1, Integer.class));
    }

    @Test
    void createsMappingOnFirstAttemptWhenCodeIsFree() {
        ShortenResult result = service.shorten("  " + URL + "  ");

        assertThat(result.created()).isTrue();
        assertThat(result.mapping().getShortCode()).isEqualTo("000000");
        assertThat(result.mapping().getDestinationUrl()).isEqualTo(URL);
        assertThat(result.mapping().getNormalizedUrl()).isEqualTo(URL);
        assertThat(result.mapping().getNormalizedUrlHash()).hasSize(64);
    }

    @Test
    void hashesTheNormalizedUrlNotTheRawInput() {
        service.shorten("HTTPS://Example.COM:443/page");

        verify(generator).generate("https://example.com/page", 0);
        ArgumentCaptor<UrlMapping> saved = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDestinationUrl()).isEqualTo("HTTPS://Example.COM:443/page");
        assertThat(saved.getValue().getNormalizedUrlHash()).isEqualTo(Sha256.hex("https://example.com/page"));
    }

    @Test
    void returnsExistingMappingForEquivalentUrlWithoutInserting() {
        UrlMapping existing = mapping("abcdef");
        when(repository.findByNormalizedUrlHash(Sha256.hex(URL))).thenReturn(Optional.of(existing));

        ShortenResult result = service.shorten(URL);

        assertThat(result.created()).isFalse();
        assertThat(result.mapping()).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void retriesWithNextAttemptWhenCodeBelongsToAnotherUrl() {
        when(repository.existsByShortCode("000000")).thenReturn(true);

        ShortenResult result = service.shorten(URL);

        assertThat(result.mapping().getShortCode()).isEqualTo("000001");
        verify(generator).generate(URL, 1);
    }

    @Test
    void returnsConcurrentWinnerWhenInsertLosesDuplicateRace() {
        UrlMapping winner = mapping("000000");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repository.findByNormalizedUrlHash(Sha256.hex(URL)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));

        ShortenResult result = service.shorten(URL);

        assertThat(result.created()).isFalse();
        assertThat(result.mapping()).isSameAs(winner);
    }

    @Test
    void movesToNextAttemptWhenInsertLosesCodeRaceToDifferentUrl() {
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("code taken"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // Free at the pre-insert check, taken by a concurrent writer when re-checked after the violation.
        when(repository.existsByShortCode("000000")).thenReturn(false).thenReturn(true);

        ShortenResult result = service.shorten(URL);

        assertThat(result.created()).isTrue();
        assertThat(result.mapping().getShortCode()).isEqualTo("000001");
    }

    @Test
    void rethrowsIntegrityViolationExplainedByNeitherRace() {
        DataIntegrityViolationException unexpected = new DataIntegrityViolationException("value too long");
        when(repository.saveAndFlush(any())).thenThrow(unexpected);
        // Neither race explains the violation: no row for this URL, and the candidate code is still free.
        when(repository.findByNormalizedUrlHash(Sha256.hex(URL))).thenReturn(Optional.empty());
        when(repository.existsByShortCode("000000")).thenReturn(false);

        assertThatThrownBy(() -> service.shorten(URL)).isSameAs(unexpected);
        verify(repository, times(1)).saveAndFlush(any());
        verify(generator, never()).generate(URL, 1);
        // Each race check runs once before the insert and once to classify the violation.
        verify(repository, times(2)).findByNormalizedUrlHash(Sha256.hex(URL));
        verify(repository, times(2)).existsByShortCode("000000");
    }

    @Test
    void failsWithExhaustedWhenEveryAttemptCollides() {
        when(repository.existsByShortCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.shorten(URL)).isInstanceOf(ShortCodeExhaustedException.class);
        verify(generator).generate(URL, MAX_ATTEMPTS - 1);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidUrlBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.shorten("javascript:alert(1)")).isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.shorten("https://sho.rt/abc123")).isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(repository, generator);
    }

    @Test
    void resolvesKnownCode() {
        when(repository.findByShortCode("abcdef")).thenReturn(Optional.of(mapping("abcdef")));

        assertThat(service.resolve("abcdef").destinationUrl()).isEqualTo(URL);
    }

    @Test
    void unknownCodeIsNotFound() {
        when(repository.findByShortCode("abcdef")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("abcdef")).isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    void malformedCodeIsRejectedWithoutQueryingTheDatabase() {
        for (String code : new String[] {"ABCDEF", "abcde", "abcdefg", "zzzzzz", "../etc", "", " ", "null"}) {
            assertThatThrownBy(() -> service.resolve(code)).isInstanceOf(InvalidShortCodeException.class);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void activeMappingIsEligibleForRedirect() {
        when(repository.findByShortCode("abcdef")).thenReturn(Optional.of(mapping("abcdef")));

        assertThat(service.resolveForRedirect("abcdef").destinationUrl()).isEqualTo(URL);
    }

    @Test
    void deactivatedMappingIsNotEligibleForRedirect() {
        when(repository.findByShortCode("abcdef")).thenReturn(Optional.of(deactivatedMapping("abcdef")));

        assertThatThrownBy(() -> service.resolveForRedirect("abcdef"))
                .isInstanceOf(ShortCodeDeactivatedException.class);
    }

    @Test
    void redirectResolutionStillDistinguishesUnknownAndMalformedCodes() {
        when(repository.findByShortCode("abcdef")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveForRedirect("abcdef")).isInstanceOf(ShortCodeNotFoundException.class);
        assertThatThrownBy(() -> service.resolveForRedirect("ABCDEF")).isInstanceOf(InvalidShortCodeException.class);
    }

    // Guard for analytics: the lifecycle-agnostic lookup must keep finding deactivated mappings.
    @Test
    void lookupStillResolvesDeactivatedMapping() {
        when(repository.findByShortCode("abcdef")).thenReturn(Optional.of(deactivatedMapping("abcdef")));

        assertThat(service.resolve("abcdef").destinationUrl()).isEqualTo(URL);
    }

    @Test
    void shorteningAnEquivalentOfADeactivatedUrlConflictsWithoutInserting() {
        when(repository.findByNormalizedUrlHash(Sha256.hex(URL))).thenReturn(Optional.of(deactivatedMapping("abcdef")));

        assertThatThrownBy(() -> service.shorten(URL)).isInstanceOf(UrlDeactivatedException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(generator);
    }

    @Test
    void deactivatedConcurrentWinnerAlsoConflicts() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repository.findByNormalizedUrlHash(Sha256.hex(URL)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(deactivatedMapping("000000")));

        assertThatThrownBy(() -> service.shorten(URL)).isInstanceOf(UrlDeactivatedException.class);
    }

    private static UrlMapping mapping(String code) {
        UrlMapping mapping = new UrlMapping(code, URL, URL, Sha256.hex(URL), Instant.now());
        ReflectionTestUtils.setField(mapping, "id", 1L);
        return mapping;
    }

    private static UrlMapping deactivatedMapping(String code) {
        UrlMapping mapping = mapping(code);
        mapping.deactivate(Instant.parse("2026-10-01T07:30:00Z"));
        return mapping;
    }
}
