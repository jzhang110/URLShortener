package com.schwab.urlshortener.common.error;

import static com.schwab.urlshortener.support.ErrorAssertions.assertNoInternalDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import com.schwab.urlshortener.url.repository.UrlMappingRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

/**
 * Verifies the approved error contract and that error bodies carry no internal details.
 * Assertions target concrete leak markers rather than banning every class-like string.
 */
class ErrorResponseSecurityTest extends IntegrationTest {

    private static final Set<String> CONTRACT_FIELDS =
            Set.of("type", "title", "status", "detail", "instance", "code", "correlationId", "timestamp");
    private static final String INJECTED_SQL = "select * from url_mapping where normalized_url_hash = ?";
    private static final String INTERNAL_MESSAGE =
            "SQL [" + INJECTED_SQL + "]; constraint violated in com.schwab.urlshortener.internal.Secret";

    @MockitoSpyBean
    private UrlMappingRepository urlMappingRepository;

    @Test
    void validationErrorFollowsContract() {
        ApiResponse response = api.shorten("javascript:alert(1)");

        assertContract(response, 400, "UNSUPPORTED_SCHEME");
        assertThat(fieldNames(response.json())).isEqualTo(CONTRACT_FIELDS);
        assertNoInternalDetails(response);
    }

    @Test
    void beanValidationErrorFollowsContractWithFieldErrors() {
        ApiResponse response = api.postJson("/api/v1/urls", "{}");

        assertContract(response, 400, "BAD_REQUEST");
        Set<String> expected = new HashSet<>(CONTRACT_FIELDS);
        expected.add("errors");
        assertThat(fieldNames(response.json())).isEqualTo(expected);
        assertThat(response.json().get("errors").get(0).get("field").asString()).isEqualTo("url");
        assertNoInternalDetails(response);
    }

    @Test
    void notFoundFollowsContract() {
        ApiResponse response = api.get("/abcdef");

        assertContract(response, 404, "SHORT_CODE_NOT_FOUND");
        assertThat(fieldNames(response.json())).isEqualTo(CONTRACT_FIELDS);
        assertNoInternalDetails(response);
    }

    @Test
    void invalidShortCodeFollowsContract() {
        ApiResponse response = api.get("/abc");

        assertContract(response, 400, "INVALID_SHORT_CODE");
        assertThat(fieldNames(response.json())).isEqualTo(CONTRACT_FIELDS);
        assertThat(response.json().get("title").asString()).isEqualTo("Bad Request");
        assertThat(response.json().get("type").asString())
                .isEqualTo("urn:problem-type:url-shortener:invalid-short-code");
        assertThat(response.json().get("detail").asString())
                .isEqualTo("Short code must contain exactly six lowercase hexadecimal characters.");
        assertThat(response.json().get("instance").asString()).isEqualTo("/abc");
        assertNoInternalDetails(response);
    }

    @Test
    void deactivatedShortCodeFollowsContract() {
        String shortCode = api.shorten("https://example.com/retired").shortCode();
        api.deactivate(shortCode);

        ApiResponse response = api.get("/" + shortCode);

        assertContract(response, 410, "SHORT_CODE_DEACTIVATED");
        assertThat(fieldNames(response.json())).isEqualTo(CONTRACT_FIELDS);
        assertThat(response.json().get("title").asString()).isEqualTo("Gone");
        assertThat(response.json().get("type").asString())
                .isEqualTo("urn:problem-type:url-shortener:short-code-deactivated");
        assertThat(response.json().get("detail").asString()).isEqualTo("This short URL has been deactivated.");
        assertNoInternalDetails(response);
    }

    @Test
    void deactivatedUrlConflictFollowsContract() {
        String shortCode = api.shorten("https://example.com/retired").shortCode();
        api.deactivate(shortCode);

        ApiResponse response = api.shorten("https://example.com/retired");

        assertContract(response, 409, "URL_DEACTIVATED");
        assertThat(fieldNames(response.json())).isEqualTo(CONTRACT_FIELDS);
        assertThat(response.json().get("title").asString()).isEqualTo("Conflict");
        assertThat(response.json().get("type").asString()).isEqualTo("urn:problem-type:url-shortener:url-deactivated");
        assertThat(response.json().get("detail").asString())
                .isEqualTo("This URL already has a deactivated short code.");
        assertNoInternalDetails(response);
        assertThat(response.body()).doesNotContain(shortCode); // the inactive short URL is not handed back
    }

    @Test
    void unexpectedErrorReturnsGeneric500WithoutInternalDetails() {
        doThrow(new IllegalStateException(INTERNAL_MESSAGE))
                .when(urlMappingRepository).findByNormalizedUrlHash(anyString());

        ApiResponse response = api.shorten("https://example.com/boom");

        assertContract(response, 500, "INTERNAL_ERROR");
        assertThat(fieldNames(response.json())).isEqualTo(CONTRACT_FIELDS);
        assertThat(response.json().get("detail").asString()).isEqualTo(GlobalExceptionHandler.GENERIC_ERROR_DETAIL);
        assertNoInternalDetails(response);
        assertThat(response.body()).doesNotContain(INTERNAL_MESSAGE).doesNotContain(INJECTED_SQL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not json", "[]", "{\"url\": 42, \"extra\": {\"$ref\": \"x\"}}"})
    void malformedRequestBodiesAreRejectedSafely(String body) {
        ApiResponse response = api.postJson("/api/v1/urls", body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("correlationId").asString()).isEqualTo(response.header("X-Correlation-Id"));
        assertNoInternalDetails(response);
    }

    @Test
    void wellFormedCallerCorrelationIdIsEchoed() {
        ApiResponse response = api.get("/abcdef", "X-Correlation-Id", "caller-id-123");

        assertThat(response.header("X-Correlation-Id")).isEqualTo("caller-id-123");
        assertThat(response.json().get("correlationId").asString()).isEqualTo("caller-id-123");
    }

    @Test
    void unsafeCallerCorrelationIdIsReplaced() {
        ApiResponse response = api.get("/abcdef", "X-Correlation-Id", "<script>alert(1)</script>");

        assertThat(response.header("X-Correlation-Id")).doesNotContain("<").isNotBlank();
        assertThat(response.body()).doesNotContain("<script>");
    }

    private static void assertContract(ApiResponse response, int status, String code) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.header("Content-Type")).startsWith("application/problem+json");
        JsonNode body = response.json();
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asString()).isEqualTo(code);
        assertThat(body.get("title").asString()).isNotBlank();
        assertThat(body.get("detail").asString()).isNotBlank();
        assertThat(body.get("instance").asString()).startsWith("/");
        assertThat(body.get("timestamp").asString()).isNotBlank();
        assertThat(body.get("correlationId").asString()).isEqualTo(response.header("X-Correlation-Id"));
    }

    private static Set<String> fieldNames(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }
}
