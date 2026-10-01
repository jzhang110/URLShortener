package com.schwab.urlshortener.url.api;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.api.dto.CreateUrlRequest;
import com.schwab.urlshortener.url.api.dto.DeactivateUrlResponse;
import com.schwab.urlshortener.url.api.dto.UrlResponse;
import com.schwab.urlshortener.url.domain.ShortCode;
import com.schwab.urlshortener.url.service.ShortenResult;
import com.schwab.urlshortener.url.service.UrlLifecycleService;
import com.schwab.urlshortener.url.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "URLs", description = "Create and manage short URLs")
class UrlController {

    private final UrlService urlService;
    private final UrlLifecycleService lifecycleService;
    private final ShortenerProperties properties;

    UrlController(UrlService urlService, UrlLifecycleService lifecycleService, ShortenerProperties properties) {
        this.urlService = urlService;
        this.lifecycleService = lifecycleService;
        this.properties = properties;
    }

    @PostMapping
    @Operation(summary = "Shorten a URL",
            description = "Creates a short code for an http/https URL. Idempotent: submitting a URL that is "
                    + "equivalent under the normalization rules returns the existing mapping with 200.")
    @ApiResponse(responseCode = "201", description = "Created a new short URL; Location is the short URL")
    @ApiResponse(responseCode = "200", description = "An equivalent URL was already shortened; existing mapping returned")
    @ApiResponse(responseCode = "400", description = "Missing, malformed, unsupported, invalid-port or disallowed URL",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "An equivalent URL already has a deactivated short code "
            + "(URL_DEACTIVATED); it is not reactivated and no new short code is created",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "No short code could be allocated",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error; quote the correlationId when reporting it",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<UrlResponse> shorten(@Valid @RequestBody CreateUrlRequest request) {
        ShortenResult result = urlService.shorten(request.url());
        String shortUrl = properties.shortUrlFor(result.mapping().getShortCode());
        UrlResponse body = UrlResponse.from(result.mapping(), shortUrl);
        return result.created()
                ? ResponseEntity.created(URI.create(shortUrl)).body(body)
                : ResponseEntity.ok(body);
    }

    @PostMapping("/{shortCode}/deactivate")
    @Operation(summary = "Deactivate a short URL",
            description = "Stops the short URL from redirecting (it then returns 410) while keeping the mapping "
                    + "and its analytics. Idempotent: repeating the request returns 200 with the original "
                    + "deactivatedAt. Prototype limitation: this endpoint is unauthenticated; production use "
                    + "requires authenticated ownership or administrative authorization.")
    @ApiResponse(responseCode = "200", description = "Deactivated, or already deactivated (idempotent)")
    @ApiResponse(responseCode = "400", description = "Malformed short code (must be six lowercase hex characters)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No URL exists for this well-formed short code",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error; quote the correlationId when reporting it",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    DeactivateUrlResponse deactivate(
            @Parameter(description = "Six lowercase hex characters", example = "3f2a9c",
                    schema = @Schema(pattern = ShortCode.FORMAT)) @PathVariable String shortCode) {
        return DeactivateUrlResponse.from(lifecycleService.deactivate(ShortCode.requireValid(shortCode)));
    }
}
