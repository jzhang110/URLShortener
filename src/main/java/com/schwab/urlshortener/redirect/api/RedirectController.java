package com.schwab.urlshortener.redirect.api;

import com.schwab.urlshortener.redirect.service.RedirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Redirect", description = "Follow a short URL")
class RedirectController {

    private final RedirectService redirectService;

    RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    /** 302 rather than 301: a permanent redirect is cached by browsers and would bypass click tracking. */
    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to the destination URL",
            description = "Resolves the short code and responds with 302 Found. A click is recorded for analytics.")
    @ApiResponse(responseCode = "302", description = "Redirect to the destination",
            headers = @Header(name = "Location", description = "Destination URL"))
    @ApiResponse(responseCode = "404", description = "Unknown or malformed short code",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Void> redirect(
            @Parameter(description = "Six lowercase hex characters", example = "3f2a9c") @PathVariable String shortCode) {
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectService.redirect(shortCode)).build();
    }
}
