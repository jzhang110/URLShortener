Feature: Follow a short URL
  As someone who received a short link
  I want it to take me to the original destination
  So that the short link is as good as the long one

  Background:
    Given the URL "https://example.com/landing" has been shortened

  Scenario: Known short code redirects and is counted
    When the client follows the short URL
    Then the response status is 302
    And the client is redirected to "https://example.com/landing"
    And the short URL has 1 recorded click

  Scenario: Unknown short code is not found and not counted
    When the client follows an unknown short code
    Then the response status is 404
    And the error code is "SHORT_CODE_NOT_FOUND"
    And the short URL has 0 recorded clicks

  Scenario: Malformed short code is rejected
    When the client requests the path "/not-a-code"
    Then the response status is 400
    And the error code is "INVALID_SHORT_CODE"
    And the short URL has 0 recorded clicks

  Scenario: Redirect still succeeds when analytics cannot be recorded
    Given click analytics cannot be recorded
    When the client follows the short URL
    Then the response status is 302
    And the client is redirected to "https://example.com/landing"
    And the short URL has 0 recorded clicks
