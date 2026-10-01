package com.schwab.urlshortener.url.api;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.api.dto.CreateUrlRequest;
import com.schwab.urlshortener.url.api.dto.UrlResponse;
import com.schwab.urlshortener.url.service.ShortenResult;
import com.schwab.urlshortener.url.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "URLs", description = "Create short URLs")
class UrlController {

    private final UrlService urlService;
    private final ShortenerProperties properties;

    UrlController(UrlService urlService, ShortenerProperties properties) {
        this.urlService = urlService;
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
}
