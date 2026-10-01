package com.schwab.urlshortener.ui.api;

import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the demo UI page at {@code /} (ADR 0010). An explicit route instead of Spring Boot's welcome
 * page: the welcome page forwards to {@code /index.html}, which the redirect route {@code /{shortCode}}
 * would capture. Security headers come from {@code UiSecurityHeadersFilter}. A page, not an API, so it
 * is hidden from OpenAPI.
 */
@Hidden
@Controller
class UiController {

    private static final Resource INDEX = new ClassPathResource("static/index.html");
    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    @GetMapping("/")
    ResponseEntity<Resource> index() {
        return ResponseEntity.ok().contentType(HTML_UTF8).body(INDEX);
    }
}
