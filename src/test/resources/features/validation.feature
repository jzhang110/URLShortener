Feature: Reject URLs that cannot be shortened
  As the service operator
  I want invalid or unsafe destinations rejected up front
  So that stored mappings use supported http(s) destinations without direct self-reference loops

  Scenario Outline: Invalid destination is rejected (<case>)
    When the client requests a shortened URL for "<url>"
    Then the response status is 400
    And the error code is "<code>"
    And the error response exposes no internal details

    Examples:
      | case              | url                          | code                    |
      | malformed         | https://exa mple.com         | MALFORMED_URL           |
      | javascript scheme | javascript:alert(1)          | UNSUPPORTED_SCHEME      |
      | ftp scheme        | ftp://example.com/file       | UNSUPPORTED_SCHEME      |
      | missing host      | https:///path-only           | MISSING_HOST            |
      | own domain        | http://localhost:8080/abc123 | DESTINATION_NOT_ALLOWED |
      | IPv6 loopback     | http://[::1]:8080/abc123     | DESTINATION_NOT_ALLOWED |
      | port 0            | https://example.com:0/       | INVALID_PORT            |
      | port 65536        | https://example.com:65536/   | INVALID_PORT            |

  Scenario: URL longer than the maximum length is rejected
    When the client requests a shortened URL that is 2049 characters long
    Then the response status is 400
    And the error code is "URL_TOO_LONG"

  Scenario: Request without a URL is rejected
    When the client submits a shorten request without a URL
    Then the response status is 400
    And the error code is "BAD_REQUEST"
    And the error response exposes no internal details
