package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.domain.InvalidShortCodeException;
import com.schwab.urlshortener.url.domain.ShortCode;
import com.schwab.urlshortener.url.domain.ShortCodeDeactivatedException;
import com.schwab.urlshortener.url.domain.ShortCodeExhaustedException;
import com.schwab.urlshortener.url.domain.ShortCodeNotFoundException;
import com.schwab.urlshortener.url.domain.UrlDeactivatedException;
import com.schwab.urlshortener.url.domain.UrlMapping;
import com.schwab.urlshortener.url.domain.UrlStatus;
import com.schwab.urlshortener.url.generation.Sha256;
import com.schwab.urlshortener.url.generation.ShortCodeGenerator;
import com.schwab.urlshortener.url.repository.UrlMappingRepository;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates URL creation (validation, duplicate detection, collision resolution) and resolution.
 *
 * <p>Stateless singleton: all mutable state lives in local variables or the database. Uniqueness is
 * guaranteed by the database constraints on {@code short_code} and {@code normalized_url_hash}; the
 * reads here are fast paths only (ADR 0004).
 */
@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private final UrlValidator validator;
    private final List<DestinationPolicy> destinationPolicies;
    private final UrlNormalizer normalizer;
    private final ShortCodeGenerator generator;
    private final UrlMappingRepository repository;
    private final int maxAttempts;

    public UrlService(UrlValidator validator, List<DestinationPolicy> destinationPolicies, UrlNormalizer normalizer,
                      ShortCodeGenerator generator, UrlMappingRepository repository, ShortenerProperties properties) {
        this.validator = validator;
        this.destinationPolicies = List.copyOf(destinationPolicies);
        this.normalizer = normalizer;
        this.generator = generator;
        this.repository = repository;
        this.maxAttempts = properties.maxAttempts();
    }

    /**
     * Deliberately not {@code @Transactional}: each insert attempt runs in its own transaction
     * (via {@code saveAndFlush}), so a constraint violation never poisons a surrounding transaction.
     * PostgreSQL in particular aborts the whole transaction after any failed statement.
     */
    public ShortenResult shorten(String rawUrl) {
        URI destination = validator.validate(rawUrl);
        destinationPolicies.forEach(policy -> policy.check(destination));

        String destinationUrl = rawUrl.strip();
        String normalizedUrl = normalizer.normalize(destination);
        String normalizedUrlHash = Sha256.hex(normalizedUrl);

        Optional<UrlMapping> existing = repository.findByNormalizedUrlHash(normalizedUrlHash);
        if (existing.isPresent()) {
            return reuse(existing.get());
        }

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = generator.generate(normalizedUrl, attempt);
            if (repository.existsByShortCode(candidate)) {
                log.debug("Short code {} already taken, trying attempt {}", candidate, attempt + 1);
                continue;
            }
            try {
                UrlMapping saved = repository.saveAndFlush(new UrlMapping(
                        candidate, destinationUrl, normalizedUrl, normalizedUrlHash, now()));
                log.info("Created short code {}", saved.getShortCode());
                return ShortenResult.created(saved);
            } catch (DataIntegrityViolationException e) {
                // Expected only if a concurrent request inserted this URL or this code between our read and write.
                Optional<UrlMapping> winner = repository.findByNormalizedUrlHash(normalizedUrlHash);
                if (winner.isPresent()) {
                    return reuse(winner.get());
                }
                if (!repository.existsByShortCode(candidate)) {
                    throw e; // neither race explains it: an unexpected violation, not a collision to retry
                }
                log.debug("Short code {} taken concurrently, trying attempt {}", candidate, attempt + 1);
            }
        }
        log.error("Short code allocation exhausted after {} attempts", maxAttempts);
        throw new ShortCodeExhaustedException();
    }

    /**
     * The single place deciding what an equivalent existing mapping means for a shorten request.
     * Exhaustive over {@link UrlStatus} on purpose: a new lifecycle state fails to compile here until
     * its shortening behavior is decided (ADR 0009). A deactivated mapping is never reused or reactivated.
     */
    private static ShortenResult reuse(UrlMapping existing) {
        return switch (existing.getStatus()) {
            case ACTIVE -> ShortenResult.existing(existing);
            case DEACTIVATED -> throw new UrlDeactivatedException();
        };
    }

    /**
     * Looks up a mapping whatever its lifecycle state, in its own read-only transaction. Used by
     * analytics, so history stays available for deactivated mappings: do not add lifecycle checks
     * here; redirects go through {@link #resolveForRedirect(String)}.
     *
     * @throws InvalidShortCodeException if the code is malformed (checked before any database access)
     * @throws ShortCodeNotFoundException if the code is well-formed but unknown
     */
    @Transactional(readOnly = true)
    public ResolvedUrl resolve(String shortCode) {
        return toResolved(find(shortCode));
    }

    /**
     * Resolves a short code for a redirect: the mapping must exist and its lifecycle must permit
     * redirects. Runs in its own read-only transaction, which is complete before the caller records
     * analytics, and sees the last committed lifecycle state.
     *
     * @throws InvalidShortCodeException if the code is malformed (checked before any database access)
     * @throws ShortCodeNotFoundException if the code is well-formed but unknown
     * @throws ShortCodeDeactivatedException if the mapping exists but no longer permits redirects
     */
    @Transactional(readOnly = true)
    public ResolvedUrl resolveForRedirect(String shortCode) {
        UrlMapping mapping = find(shortCode);
        if (!mapping.canRedirect()) {
            throw new ShortCodeDeactivatedException();
        }
        return toResolved(mapping);
    }

    private UrlMapping find(String shortCode) {
        ShortCode.requireValid(shortCode);
        return repository.findByShortCode(shortCode).orElseThrow(ShortCodeNotFoundException::new);
    }

    private static ResolvedUrl toResolved(UrlMapping mapping) {
        return new ResolvedUrl(mapping.getId(), mapping.getShortCode(), mapping.getDestinationUrl());
    }

    // Truncated to the database's microsecond precision so a re-read returns an identical value.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
