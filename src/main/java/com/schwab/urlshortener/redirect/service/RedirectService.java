package com.schwab.urlshortener.redirect.service;

import com.schwab.urlshortener.analytics.service.AnalyticsService;
import com.schwab.urlshortener.url.service.ResolvedUrl;
import com.schwab.urlshortener.url.service.UrlService;
import java.net.URI;
import org.springframework.stereotype.Service;

/**
 * The redirect workflow: resolve, then record a click (best effort), then hand back the target.
 *
 * <p>Deliberately not transactional. Resolution commits in its own read-only transaction before
 * the click is recorded in a separate one, so an analytics failure can never mark the redirect
 * rollback-only (ADR 0006).
 */
@Service
public class RedirectService {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    public RedirectService(UrlService urlService, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
    }

    /** @throws com.schwab.urlshortener.url.domain.ShortCodeNotFoundException before any click is recorded */
    public URI redirect(String shortCode) {
        ResolvedUrl resolved = urlService.resolve(shortCode);
        analyticsService.recordClick(resolved);
        return URI.create(resolved.destinationUrl());
    }
}
