# ADR 0008: 302 Found for redirects

**Status:** Accepted

## Problem
Which redirect status should `GET /{shortCode}` return?

## Alternatives
1. **301 Moved Permanently.** Browsers and proxies cache it, so repeat visits skip the service entirely.
2. **302 Found.** Not cached by default, so every visit reaches the service.
3. **307/308.** These preserve the request method. That's irrelevant for GET links, and older clients support them less well.

## Decision
302. Analytics (BR-6, BR-9) require every visit to reach the service. A cached 301 would make click counts meaningless and would stop a future deactivate/suspend from taking effect for users who already visited.

## Tradeoffs
- (−) There's more load on the service than with 301, and SEO "link equity" doesn't pass through as strongly. Neither matters for this use case.
- Malformed codes return 400 `INVALID_SHORT_CODE` without touching the database. Unknown, well-formed codes return 404.
