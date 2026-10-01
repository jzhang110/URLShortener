package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.common.logging.LogSanitizer;
import com.schwab.urlshortener.url.domain.ShortCodeExhaustedException;
import com.schwab.urlshortener.url.domain.ShortCodeNotFoundException;
import com.schwab.urlshortener.url.domain.UrlMapping;
import com.schwab.urlshortener.url.generation.Sha256;
import com.schwab.urlshortener.url.generation.ShortCodeGenerator;
import com.schwab.urlshortener.url.repository.UrlMappingRepository;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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
    private static final Pattern SHORT_CODE_FORMAT = Pattern.compile("^[0-9a-f]{6}$");

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
        String normalizedUrl = normalizer.normalize(rawUrl);
        String normalizedUrlHash = Sha256.hex(normalizedUrl);

        Optional<UrlMapping> existing = repository.findByNormalizedUrlHash(normalizedUrlHash);
        if (existing.isPresent()) {
            return ShortenResult.existing(existing.get());
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
                // A concurrent request inserted this URL or this code between our read and write.
                Optional<UrlMapping> winner = repository.findByNormalizedUrlHash(normalizedUrlHash);
                if (winner.isPresent()) {
                    return ShortenResult.existing(winner.get());
                }
                log.debug("Short code {} taken concurrently, trying attempt {}", candidate, attempt + 1);
            }
        }
        log.error("Short code allocation exhausted after {} attempts", maxAttempts);
        throw new ShortCodeExhaustedException();
    }

    /**
     * Resolves a short code in its own read-only transaction, which is complete before the caller
     * records analytics.
     *
     * @throws ShortCodeNotFoundException if the code is malformed or unknown (malformed codes skip the database)
     */
    @Transactional(readOnly = true)
    public ResolvedUrl resolve(String shortCode) {
        if (shortCode == null || !SHORT_CODE_FORMAT.matcher(shortCode).matches()) {
            log.debug("Rejected malformed short code {}", LogSanitizer.sanitize(shortCode));
            throw new ShortCodeNotFoundException();
        }
        return repository.findByShortCode(shortCode)
                .map(mapping -> new ResolvedUrl(mapping.getId(), mapping.getShortCode(), mapping.getDestinationUrl()))
                .orElseThrow(ShortCodeNotFoundException::new);
    }

    // Truncated to the database's microsecond precision so a re-read returns an identical value.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
