package com.schwab.urlshortener.url.api.dto;

import com.schwab.urlshortener.url.domain.UrlStatus;
import com.schwab.urlshortener.url.service.DeactivateResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Lifecycle state of a short URL after a deactivate request")
public record DeactivateUrlResponse(
        @Schema(description = "The short code", example = "3f2a9c")
        String shortCode,
        @Schema(description = "Lifecycle status; DEACTIVATED after this call", example = "DEACTIVATED")
        UrlStatus status,
        @Schema(description = "When the URL was first deactivated (UTC); unchanged by repeated requests",
                example = "2026-10-01T07:30:00Z")
        Instant deactivatedAt) {

    public static DeactivateUrlResponse from(DeactivateResult result) {
        return new DeactivateUrlResponse(result.shortCode(), result.status(), result.deactivatedAt());
    }
}
