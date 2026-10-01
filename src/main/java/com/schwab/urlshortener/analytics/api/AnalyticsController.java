package com.schwab.urlshortener.analytics.api;

import com.schwab.urlshortener.analytics.api.dto.AnalyticsResponse;
import com.schwab.urlshortener.analytics.service.AnalyticsService;
import com.schwab.urlshortener.url.domain.ShortCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "Analytics", description = "Click analytics for short URLs")
class AnalyticsController {

    private final AnalyticsService analyticsService;

    AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{shortCode}/analytics")
    @Operation(summary = "Get click analytics",
            description = "Counts successful redirects only; unknown codes and failed redirects are not counted. "
                    + "Historical analytics remain available after the short URL is deactivated.")
    @ApiResponse(responseCode = "200", description = "Analytics for the short code")
    @ApiResponse(responseCode = "400", description = "Malformed short code (must be six lowercase hex characters)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No URL exists for this well-formed short code",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    AnalyticsResponse analytics(
            @Parameter(description = "Six lowercase hex characters", example = "3f2a9c",
                    schema = @Schema(pattern = ShortCode.FORMAT)) @PathVariable String shortCode) {
        return AnalyticsResponse.from(shortCode, analyticsService.analyticsFor(ShortCode.requireValid(shortCode)));
    }
}
