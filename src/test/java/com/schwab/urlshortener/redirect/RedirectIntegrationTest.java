package com.schwab.urlshortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.schwab.urlshortener.analytics.repository.ClickEventRepository;
import com.schwab.urlshortener.support.IntegrationTest;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ExtendWith(OutputCaptureExtension.class)
class RedirectIntegrationTest extends IntegrationTest {

    private static final String DESTINATION = "https://example.com/landing?campaign=x";
    private static final String INTERNAL_FAILURE_MESSAGE = "simulated failure: jdbc:h2 connection pool exhausted";

    @MockitoSpyBean
    private ClickEventRepository clickEventRepository;

    private String shortCode;

    @BeforeEach
    void createMapping() {
        shortCode = api.shorten(DESTINATION).shortCode();
    }

    @Test
    void validShortCodeRecordsClickAndRedirectsWith302() {
        ApiResponse response = api.get("/" + shortCode);

        assertThat(response.status()).isEqualTo(302);
        assertThat(response.header("Location")).isEqualTo(DESTINATION);
        assertThat(countRows("click_event")).isEqualTo(1);
    }

    @Test
    void unknownShortCodeReturns404AndRecordsNoClick() {
        ApiResponse response = api.get("/" + unknownCodeOtherThan(shortCode));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("code").asString()).isEqualTo("SHORT_CODE_NOT_FOUND");
        assertThat(countRows("click_event")).isZero();
    }

    @Test
    void malformedShortCodeReturns400AndRecordsNoClick() {
        assertThat(api.get("/NOT-A-CODE").status()).isEqualTo(400);
        assertThat(api.get("/abc").json().get("code").asString()).isEqualTo("INVALID_SHORT_CODE");
        assertThat(countRows("click_event")).isZero();
    }

    @Test
    void analyticsFailureAfterResolutionStillRedirects() {
        doThrow(new DataAccessResourceFailureException(INTERNAL_FAILURE_MESSAGE))
                .when(clickEventRepository).save(any());

        ApiResponse response = api.get("/" + shortCode);

        assertThat(response.status()).isEqualTo(302);
        assertThat(response.header("Location")).isEqualTo(DESTINATION);
        assertThat(countRows("click_event")).isZero();
    }

    @Test
    void analyticsFailureIsLoggedInternallyButNotExposedToClient(CapturedOutput output) {
        doThrow(new DataAccessResourceFailureException(INTERNAL_FAILURE_MESSAGE))
                .when(clickEventRepository).save(any());

        ApiResponse response = api.get("/" + shortCode, "X-Correlation-Id", "redirect-test-42");

        assertThat(response.status()).isEqualTo(302);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().toString()).doesNotContain(INTERNAL_FAILURE_MESSAGE);
        assertThat(output.getOut())
                .contains("WARN")
                .contains("redirect-test-42")
                .contains("Failed to record click for short code " + shortCode)
                .contains(INTERNAL_FAILURE_MESSAGE);
    }
}
