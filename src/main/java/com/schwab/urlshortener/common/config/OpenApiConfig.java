package com.schwab.urlshortener.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI urlShortenerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("URL Shortener API")
                .version("v1")
                .description("Create short URLs, follow them, and read click analytics. "
                        + "Errors use RFC 9457 problem details with a stable `code` and a `correlationId` "
                        + "that matches the X-Correlation-Id response header."));
    }
}
