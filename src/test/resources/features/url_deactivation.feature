Feature: URL deactivation
  As an API client
  I want to deactivate a short URL
  So that it stops redirecting while its history is kept

  Background:
    Given the URL "https://example.com/retiring" has been shortened

  Scenario: Deactivate an active short URL
    When the client deactivates the short URL
    Then the response status is 200
    And the short URL status is "DEACTIVATED"

  Scenario: A deactivated URL no longer redirects and is not counted
    Given the short URL has been deactivated
    When the client follows the short URL
    Then the response status is 410
    And the error code is "SHORT_CODE_DEACTIVATED"
    And the short URL has 0 recorded clicks

  Scenario: Deactivation is idempotent
    Given the short URL has been deactivated
    When the client deactivates the short URL
    Then the response status is 200
    And the short URL status is "DEACTIVATED"
    And the original deactivation time is preserved

  Scenario: Historical analytics remain available
    Given the short URL has been followed 3 times
    And the short URL has been deactivated
    When the client requests analytics for the short URL
    Then the response status is 200
    And the analytics report 3 total clicks

  Scenario: A deactivated URL cannot be shortened again
    Given the short URL has been deactivated
    When the client requests a shortened URL for "https://example.com/retiring"
    Then the response status is 409
    And the error code is "URL_DEACTIVATED"

  Scenario: Deactivating an unknown short code is not found
    When the client deactivates an unknown short code
    Then the response status is 404
    And the error code is "SHORT_CODE_NOT_FOUND"
