package com.schwab.urlshortener.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import com.schwab.urlshortener.url.repository.UrlMappingRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Short-code format is enforced at the API boundary for both the redirect and analytics endpoints. */
class ShortCodeValidationIntegrationTest extends IntegrationTest {

    private static final String INVALID_DETAIL = "Short code must contain exactly six lowercase hexadecimal characters.";

    @MockitoSpyBean
    private UrlMappingRepository urlMappingRepository;

    // "%20" is a single space once decoded; "null" is the literal four-character string.
    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcdefg", "abc$12", "ABCDEF", "hello", "not-valid-url", "plano.gov", "null", "%20"})
    void malformedShortCodeIsRejectedBeforeAnyLookup(String code) {
        assertInvalidShortCode(api.get("/" + code));
        assertInvalidShortCode(api.analytics(code));
        assertInvalidShortCode(api.deactivate(code));

        verify(urlMappingRepository, never()).findByShortCode(any());
        verify(urlMappingRepository, never()).findForUpdateByShortCode(any());
        assertThat(countRows("click_event")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdef", "123456", "a1b2c3"})
    void wellFormedUnknownShortCodeIsNotFound(String code) {
        assertNotFound(api.get("/" + code));
        assertNotFound(api.analytics(code));
        assertNotFound(api.deactivate(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/foo/bar", "/://test"}) // "/" now serves the UI (ADR 0010)
    void pathsThatDoNotMatchTheRedirectRouteAreOrdinary404s(String path) {
        ApiResponse response = api.get(path);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("INVALID_SHORT_CODE");
        verify(urlMappingRepository, never()).findByShortCode(any());
    }

    private static void assertInvalidShortCode(ApiResponse response) {
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("code").asString()).isEqualTo("INVALID_SHORT_CODE");
        assertThat(response.json().get("detail").asString()).isEqualTo(INVALID_DETAIL);
    }

    private static void assertNotFound(ApiResponse response) {
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("code").asString()).isEqualTo("SHORT_CODE_NOT_FOUND");
    }
}
