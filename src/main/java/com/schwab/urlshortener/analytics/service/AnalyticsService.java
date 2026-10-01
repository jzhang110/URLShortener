package com.schwab.urlshortener.analytics.service;

import com.schwab.urlshortener.analytics.domain.ClickEvent;
import com.schwab.urlshortener.analytics.domain.ClickStats;
import com.schwab.urlshortener.analytics.repository.ClickEventRepository;
import com.schwab.urlshortener.url.service.ResolvedUrl;
import com.schwab.urlshortener.url.service.UrlService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Records click events and derives analytics from them. */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ClickEventRepository clickEventRepository;
    private final UrlService urlService;
    private final TransactionTemplate independentTransaction;

    public AnalyticsService(ClickEventRepository clickEventRepository, UrlService urlService,
                            PlatformTransactionManager transactionManager) {
        this.clickEventRepository = clickEventRepository;
        this.urlService = urlService;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Best-effort click recording (BR-8, ADR 0006). The insert runs in its own REQUIRES_NEW
     * transaction and the catch sits outside that boundary, so neither a failed insert nor a
     * failed commit can propagate to the caller or mark a caller's transaction rollback-only.
     * Never throws.
     */
    public void recordClick(ResolvedUrl resolved) {
        try {
            independentTransaction.executeWithoutResult(status ->
                    clickEventRepository.save(new ClickEvent(resolved.mappingId(), now())));
        } catch (RuntimeException e) {
            log.warn("Failed to record click for short code {}; redirect proceeds without analytics",
                    resolved.shortCode(), e);
        }
    }

    /** @throws com.schwab.urlshortener.url.domain.ShortCodeNotFoundException if the code is unknown */
    public ClickStats analyticsFor(String shortCode) {
        ResolvedUrl resolved = urlService.resolve(shortCode);
        return clickEventRepository.statsFor(resolved.mappingId());
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
