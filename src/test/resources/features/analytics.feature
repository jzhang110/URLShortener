Feature: Short URL analytics
  As an API client
  I want to know how often a short link has been followed
  So that I can measure its reach

  Scenario: Analytics count successful redirects
    Given the URL "https://example.com/popular" has been shortened
    And the short URL has been followed 3 times
    When the client requests analytics for the short URL
    Then the response status is 200
    And the analytics report 3 total clicks
    And the analytics report a last click time

  Scenario: A new short URL has no clicks
    Given the URL "https://example.com/fresh" has been shortened
    When the client requests analytics for the short URL
    Then the response status is 200
    And the analytics report 0 total clicks
    And the analytics report no last click time

  Scenario: Analytics for an unknown short code are not found
    When the client requests analytics for an unknown short code
    Then the response status is 404
    And the error code is "SHORT_CODE_NOT_FOUND"

  Scenario: Analytics for a malformed short code are rejected
    When the client requests the path "/api/v1/urls/abc/analytics"
    Then the response status is 400
    And the error code is "INVALID_SHORT_CODE"
