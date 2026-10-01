package com.schwab.urlshortener.analytics.api.dto;

import com.schwab.urlshortener.analytics.domain.ClickStats;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Click analytics for a short URL, derived from successful redirects")
public record AnalyticsResponse(
        @Schema(example = "3f2a9c") String shortCode,
        @Schema(description = "Number of successful redirects", example = "42") long totalClicks,
        @Schema(description = "Time of the latest successful redirect (UTC); null if never clicked",
                example = "2026-09-30T12:00:00Z", nullable = true) Instant lastClickedAt) {

    public static AnalyticsResponse from(String shortCode, ClickStats stats) {
        return new AnalyticsResponse(shortCode, stats.totalClicks(), stats.lastClickedAt());
    }
}
