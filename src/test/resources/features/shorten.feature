Feature: Shorten a URL
  As an API client
  I want to exchange a long URL for a short one
  So that I can share a compact link that leads to the same destination

  Scenario: Successfully create a shortened URL
    Given a valid destination URL "https://www.example.com/articles/2026/url-design?ref=bdd"
    When the client requests a shortened URL
    Then the response status is 201
    And a six-character short code is returned
    And the returned short URL redirects to "https://www.example.com/articles/2026/url-design?ref=bdd"

  Scenario: Resubmitting the same URL returns the existing short URL
    Given the URL "https://example.org/same" has been shortened
    When the client requests a shortened URL for "https://example.org/same"
    Then the response status is 200
    And the same short code is returned

  Scenario Outline: Equivalent URLs share one short code (<rule>)
    Given the URL "https://example.com/" has been shortened
    When the client requests a shortened URL for "<equivalent>"
    Then the response status is 200
    And the same short code is returned

    Examples:
      | rule                  | equivalent               |
      | N2 scheme case        | HTTPS://example.com/     |
      | N3 host case          | https://Example.COM/     |
      | N4 default port       | https://example.com:443/ |
      | N5 empty path is root | https://example.com      |
      | N2 + N3 combined      | HTTPS://EXAMPLE.COM/     |

  Scenario Outline: URLs that differ outside the normalization rules get different short codes
    Given the URL "<first>" has been shortened
    When the client requests a shortened URL for "<second>"
    Then the response status is 201
    And a different short code is returned

    Examples:
      | first                        | second                       |
      | https://example.com/         | https://example.com:8443/    |
      | https://example.com/path     | https://example.com/path/    |
      | https://example.com/a        | https://example.com/A        |
      | https://example.com/?a=1&b=2 | https://example.com/?b=2&a=1 |
      | https://example.com/page#top | https://example.com/page     |
