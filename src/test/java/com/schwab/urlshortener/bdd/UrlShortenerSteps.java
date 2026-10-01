package com.schwab.urlshortener.bdd;

import static com.schwab.urlshortener.support.ErrorAssertions.assertNoInternalDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.schwab.urlshortener.analytics.repository.ClickEventRepository;
import com.schwab.urlshortener.support.DatabaseCleaner;
import com.schwab.urlshortener.support.TestApiClient.ApiResponse;
import com.schwab.urlshortener.support.TestApiClient;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Thin step definitions: each step is one HTTP interaction or one assertion, delegating to
 * {@link TestApiClient}. Cucumber creates a fresh instance per scenario, so fields are scenario state.
 */
public class UrlShortenerSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ClickEventRepository clickEventRepository;

    private TestApiClient api;
    private String destinationUrl;
    private String existingShortCode;
    private String firstDeactivatedAt;
    private ApiResponse response;

    @Before
    public void resetState() {
        DatabaseCleaner.clean(jdbc);
        reset(clickEventRepository);
        api = new TestApiClient(port);
    }

    @Given("a valid destination URL {string}")
    public void aValidDestinationUrl(String url) {
        destinationUrl = url;
    }

    @Given("the URL {string} has been shortened")
    public void theUrlHasBeenShortened(String url) {
        ApiResponse created = api.shorten(url);
        assertThat(created.status()).isEqualTo(201);
        existingShortCode = created.shortCode();
    }

    @Given("click analytics cannot be recorded")
    public void clickAnalyticsCannotBeRecorded() {
        doThrow(new DataAccessResourceFailureException("simulated analytics outage"))
                .when(clickEventRepository).save(any());
    }

    @Given("the short URL has been followed {int} times")
    public void theShortUrlHasBeenFollowed(int times) {
        for (int i = 0; i < times; i++) {
            assertThat(api.get("/" + existingShortCode).status()).isEqualTo(302);
        }
    }

    @Given("the short URL has been deactivated")
    public void theShortUrlHasBeenDeactivated() {
        ApiResponse deactivated = api.deactivate(existingShortCode);
        assertThat(deactivated.status()).isEqualTo(200);
        firstDeactivatedAt = deactivated.json().get("deactivatedAt").asString();
    }

    @When("the client deactivates the short URL")
    public void theClientDeactivatesTheShortUrl() {
        response = api.deactivate(existingShortCode);
    }

    @When("the client deactivates an unknown short code")
    public void theClientDeactivatesAnUnknownShortCode() {
        response = api.deactivate(unknownShortCode());
    }

    @When("the client requests a shortened URL")
    public void theClientRequestsAShortenedUrl() {
        response = api.shorten(destinationUrl);
    }

    @When("the client requests a shortened URL for {string}")
    public void theClientRequestsAShortenedUrlFor(String url) {
        response = api.shorten(url);
    }

    @When("the client requests a shortened URL that is {int} characters long")
    public void theClientRequestsAShortenedUrlOfLength(int length) {
        String prefix = "https://example.com/";
        response = api.shorten(prefix + "a".repeat(length - prefix.length()));
    }

    @When("the client submits a shorten request without a URL")
    public void theClientSubmitsAShortenRequestWithoutAUrl() {
        response = api.postJson("/api/v1/urls", "{}");
    }

    @When("the client follows the short URL")
    public void theClientFollowsTheShortUrl() {
        response = api.get("/" + existingShortCode);
    }

    @When("the client follows an unknown short code")
    public void theClientFollowsAnUnknownShortCode() {
        response = api.get("/" + unknownShortCode());
    }

    @When("the client requests the path {string}")
    public void theClientRequestsThePath(String path) {
        response = api.get(path);
    }

    @When("the client requests analytics for the short URL")
    public void theClientRequestsAnalyticsForTheShortUrl() {
        response = api.analytics(existingShortCode);
    }

    @When("the client requests analytics for an unknown short code")
    public void theClientRequestsAnalyticsForAnUnknownShortCode() {
        response = api.analytics(unknownShortCode());
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertThat(response.status()).isEqualTo(status);
    }

    @And("a six-character short code is returned")
    public void aSixCharacterShortCodeIsReturned() {
        assertThat(response.shortCode()).matches("^[0-9a-f]{6}$");
    }

    @And("the returned short URL redirects to {string}")
    public void theReturnedShortUrlRedirectsTo(String url) {
        ApiResponse redirect = api.get("/" + response.shortCode());
        assertThat(redirect.status()).isEqualTo(302);
        assertThat(redirect.header("Location")).isEqualTo(url);
    }

    @And("the same short code is returned")
    public void theSameShortCodeIsReturned() {
        assertThat(response.shortCode()).isEqualTo(existingShortCode);
    }

    @And("a different short code is returned")
    public void aDifferentShortCodeIsReturned() {
        assertThat(response.shortCode()).isNotEqualTo(existingShortCode);
    }

    @And("the error code is {string}")
    public void theErrorCodeIs(String code) {
        assertThat(response.header("Content-Type")).startsWith("application/problem+json");
        assertThat(response.json().get("code").asString()).isEqualTo(code);
    }

    @And("the error response exposes no internal details")
    public void theErrorResponseExposesNoInternalDetails() {
        assertNoInternalDetails(response);
    }

    @And("the client is redirected to {string}")
    public void theClientIsRedirectedTo(String url) {
        assertThat(response.header("Location")).isEqualTo(url);
    }

    @And("the short URL has {int} recorded click(s)")
    public void theShortUrlHasRecordedClicks(long clicks) {
        assertThat(api.totalClicks(existingShortCode)).isEqualTo(clicks);
    }

    @And("the analytics report {int} total clicks")
    public void theAnalyticsReportTotalClicks(long clicks) {
        assertThat(response.json().get("totalClicks").asLong()).isEqualTo(clicks);
    }

    @And("the analytics report a last click time")
    public void theAnalyticsReportALastClickTime() {
        assertThat(response.json().get("lastClickedAt").asString()).isNotBlank();
    }

    @And("the analytics report no last click time")
    public void theAnalyticsReportNoLastClickTime() {
        assertThat(response.json().get("lastClickedAt").isNull()).isTrue();
    }

    @And("the short URL status is {string}")
    public void theShortUrlStatusIs(String status) {
        assertThat(response.json().get("status").asString()).isEqualTo(status);
        assertThat(response.json().get("deactivatedAt").asString()).isNotBlank();
    }

    @And("the original deactivation time is preserved")
    public void theOriginalDeactivationTimeIsPreserved() {
        assertThat(response.json().get("deactivatedAt").asString()).isEqualTo(firstDeactivatedAt);
    }

    private String unknownShortCode() {
        return "ffffff".equals(existingShortCode) ? "eeeeee" : "ffffff";
    }
}
