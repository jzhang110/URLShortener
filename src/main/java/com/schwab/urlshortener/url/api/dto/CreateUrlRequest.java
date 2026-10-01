package com.schwab.urlshortener.url.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to shorten a URL")
public record CreateUrlRequest(
        @Schema(description = "Absolute http or https URL to shorten (max 2048 characters)",
                example = "https://example.com/some/long/path?q=1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String url) {
}
