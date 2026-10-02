# ADR 0003: Short-code hashing and retry input

**Status:** Accepted. This deliberately deviates from the design doc's retry example.

## Problem
The design doc specifies a code = the first 6 hex characters of SHA-256(URL), and on collision re-hashing `URL + attemptNumber` (for example `www.myLongURL.com1`). The concatenation is ambiguous: attempt 1 of `https://a.com/x` hashes the same input as attempt 0 of `https://a.com/x1`, which is a different, legitimate URL. A `#n` suffix has the same issue, because `https://a.com/#1` is a valid URL whose attempt-0 input would equal attempt 1 of `https://a.com/`.

## Alternatives
1. `url + n`, as in the doc.
2. `url + "#" + n`.
3. `url + " #" + n`. A space can never appear in a URI that `java.net.URI` accepts.
4. A length-prefixed or structured encoding.

## Decision
Option 3:
```
attempt 0: SHA-256(UTF-8(normalizedUrl))
attempt n: SHA-256(UTF-8(normalizedUrl + " #" + n))
code     = first 6 lowercase hex chars
```
The input is always the **normalized** URL ([ADR 0002](0002-url-identity-and-normalization.md)). `Sha256ShortCodeGeneratorTest` pins known vectors (for example `https://example.com/` → `0f115d`) and checks deterministic retry behaviour (the attempt-1 and attempt-2 vectors). The protection against a retry input matching a real URL comes from the input's structure, not from a test: the `" #"` delimiter contains a space, which a URI accepted by the validator can't contain, so a retry input is never equal to a normalized URL. A single example test couldn't prove collision impossibility, so the earlier fragment example test was removed during the manual test-suite review.

## Tradeoffs
- (+) Retry inputs can never be confused with a real URL's first-attempt input. It stays close to the documented intent and is as simple as option 1.
- Correctness never depends on this choice: the unique constraints catch any collision ([ADR 0004](0004-database-as-concurrency-authority.md)). The choice only avoids needless collisions.
- 16⁶ ≈ 16.7M codes. By the birthday bound, collisions become common after a few thousand URLs, and retries resolve them. Exhausting `maxAttempts` (10) returns 503. It never overwrites. A longer code would be a new ADR.
