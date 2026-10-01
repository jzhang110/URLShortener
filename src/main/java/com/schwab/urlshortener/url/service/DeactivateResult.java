package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.url.domain.UrlMapping;
import com.schwab.urlshortener.url.domain.UrlStatus;
import java.time.Instant;

/** Detached, immutable lifecycle state after a deactivate request; the entity never leaves the service. */
public record DeactivateResult(String shortCode, UrlStatus status, Instant deactivatedAt) {

    static DeactivateResult from(UrlMapping mapping) {
        return new DeactivateResult(mapping.getShortCode(), mapping.getStatus(), mapping.getDeactivatedAt());
    }
}
