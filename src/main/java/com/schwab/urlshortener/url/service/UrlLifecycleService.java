package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.url.domain.ShortCode;
import com.schwab.urlshortener.url.domain.ShortCodeNotFoundException;
import com.schwab.urlshortener.url.domain.UrlMapping;
import com.schwab.urlshortener.url.repository.UrlMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle use cases for existing mappings (ADR 0009). Separate from {@link UrlService}, which owns
 * creation and resolution: lifecycle changes are locked read-modify-write transactions on one row.
 */
@Service
public class UrlLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(UrlLifecycleService.class);

    private final UrlMappingRepository repository;
    private final Clock clock;

    public UrlLifecycleService(UrlMappingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Deactivates the mapping; idempotent. The row lock serializes concurrent requests, so every
     * caller observes the single committed {@code deactivatedAt}.
     *
     * @throws com.schwab.urlshortener.url.domain.InvalidShortCodeException if the code is malformed (before any lookup)
     * @throws ShortCodeNotFoundException if the code is well-formed but unknown
     */
    @Transactional
    public DeactivateResult deactivate(String shortCode) {
        ShortCode.requireValid(shortCode);
        UrlMapping mapping = repository.findForUpdateByShortCode(shortCode)
                .orElseThrow(ShortCodeNotFoundException::new);
        if (mapping.deactivate(now())) {
            log.info("Deactivated short code {}", shortCode);
        } else {
            log.debug("Short code {} was already deactivated", shortCode);
        }
        return DeactivateResult.from(mapping);
    }

    // Truncated to the database's microsecond precision so a repeated request returns an identical value.
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
