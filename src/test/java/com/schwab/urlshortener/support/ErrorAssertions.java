package com.schwab.urlshortener.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.support.TestApiClient.ApiResponse;

/** Shared checks that an error body leaks no internal implementation details. */
public final class ErrorAssertions {

    private ErrorAssertions() {
    }

    public static void assertNoInternalDetails(ApiResponse response) {
        assertThat(response.body())
                .doesNotContain("at com.", "at org.", "at java.")      // stack frames
                .doesNotContain("Exception", "Caused by")               // exception types / chains
                .doesNotContainIgnoringCase("sql")                      // SQL details
                .doesNotContainIgnoringCase("constraint")
                .doesNotContainIgnoringCase("h2")
                .doesNotContain("com.schwab.")                          // internal packages
                .doesNotContain("\"trace\"", "\"exception\"");          // Spring default error attributes
    }
}
