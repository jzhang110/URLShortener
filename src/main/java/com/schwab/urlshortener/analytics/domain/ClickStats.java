package com.schwab.urlshortener.analytics.domain;

import java.time.Instant;

/** Aggregate over a mapping's click events; {@code lastClickedAt} is null when there are none. */
public record ClickStats(long totalClicks, Instant lastClickedAt) {
}
