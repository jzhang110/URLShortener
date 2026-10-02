# Ambiguous requirement: what counts as an "identical" URL

## 1. Original requirement
> "Return the existing shortened URL when an identical original URL is submitted again."

From `../../InitialDesignDoc.md`: the duplicate-detection bullets (lines 15 and 126), "Identical original URLs should resolve to the same existing mapping" (line 134), and "A duplicate original URL should reuse its existing mapping" (line 200). The design document asks for a database unique key, but never defines "identical".

## 2. Ambiguity identified
"Identical" could mean the exact submitted bytes, or URLs that lead to the same place. Many syntactically different URLs fall in between. Some differ only in ways the URL specification itself defines as equivalent (scheme case, host case, a default port, an empty path). Others *may or may not* reach the same content depending on the destination server (path case, trailing slash, query order, fragment). The requirement doesn't say which of these differences should be ignored.

## 3. Alternatives considered
| # | Interpretation | Outcome |
|---|---|---|
| A | Exact string after trimming | Rejected. `https://example.com` and `https://example.com/` would get two mappings, even though RFC 3986 defines them as equivalent |
| B | **Conservative normalization:** a closed set of spec-level equivalences only | **Chosen** |
| C | Full RFC 3986 §6.2.2 normalization (percent-encoding case and decoding, dot segments) | Not needed. It would merge more forms that are safe in theory, but each rule added later changes stored identities |
| D | Aggressive "semantic" rules: sort the query, strip trailing slashes, drop fragments, ignore `www.` | Rejected. These can merge URLs that lead to different content |
| E | Resolve or fetch both URLs and compare the final destinations | Rejected. It needs network access (an SSRF risk), adds latency, and gives answers that change over time |

## 4. Chosen interpretation
Two URLs are **equivalent under this application's URL identity rules** when they produce the same normalized URL (rules N1–N6, [ADR 0002](../adr/0002-url-identity-and-normalization.md)). This is a lexical identity. It doesn't claim the URLs are universally semantically equivalent, and anything not covered by a rule is treated as a different URL.

## 5. Normalization rules applied
| Rule | Behavior | Example |
|---|---|---|
| N1 | Trim surrounding whitespace (when the URL is parsed) | `" https://a.com/ "` → `https://a.com/` |
| N2 | Lowercase the scheme | `HTTPS://a.com/` → `https://a.com/` |
| N3 | Lowercase the host | `https://A.COM/` → `https://a.com/` |
| N4 | Drop the port only when it's the scheme default; an empty port counts as no port | `:443` on https, `:80` on http, `https://a.com:/` |
| N5 | An empty path becomes `/` | `https://a.com?q=1` → `https://a.com/?q=1` |
| N6 | Path, query and fragment are kept byte-for-byte | |

## 6. Rules intentionally not applied
These are **conservative identity decisions**. The system cannot safely assume these differences are semantically irrelevant, so it keeps them:

- **Path case:** `/Path` vs `/path`
- **Trailing slash** on a non-root path: `/path` vs `/path/`
- **Query parameter order and values:** `?a=1&b=2` vs `?b=2&a=1`
- **Fragment:** `/page#top` vs `/page`. Fragments choose the landing point and drive client-side routing, and the redirect keeps them.
- **Empty `?` and empty `#`**
- **Percent-encoding case and unreserved decoding:** `%2f` vs `%2F`, `%7E` vs `~`
- **Dot segments:** `/a/./b`
- **Host differences:** a trailing dot or `www.`. Treating them as equivalent would need DNS, which the service never uses.
- **Scheme and non-default port:** `http` vs `https`, `:8443`, `https://h:80`

The reason for each is in [ADR 0002](../adr/0002-url-identity-and-normalization.md#deliberately-not-normalized).

## 7. Rationale and tradeoffs
- **Merging wrongly is a correctness and safety bug; splitting wrongly only costs storage.** Every equivalent form redirects to the **first-submitted** destination. So if a rule merged two URLs that lead to different content, a later submitter would silently be sent to a resource someone else chose. If a rule is missing, the only cost is an extra mapping.
- **Rules are deliberately hard to change.** Adding a rule changes the identity of existing data, which needs a migration that recomputes `normalized_url` and its hash. That's a reason to add rules only when a real defect requires one.
- **The trade we accept:** URLs that are equivalent in practice, such as a reordered query, get different codes.

## 8. Implementation impact
**No production-code change was needed.** The existing implementation already applies exactly the chosen interpretation:

1. `UrlValidator` parses the trimmed input once (N1).
2. `UrlNormalizer` applies N2–N6.
3. `UrlService.shorten` hashes **only** the normalized URL (`normalized_url_hash`, which has a `UNIQUE` constraint).
4. It looks the hash up: an ACTIVE match returns 200 with the existing mapping; a DEACTIVATED match returns 409 `URL_DEACTIVATED` ([ADR 0009](../adr/0009-url-lifecycle-deactivation.md)).
5. With no match, short-code candidates are generated from the normalized URL.

Equivalent forms share a hash, so the unique constraint also makes **concurrent** equivalent submissions converge on one mapping ([ADR 0004](../adr/0004-database-as-concurrency-authority.md)).

This scenario added tests and documentation only: three new or extended tests, five new feature-file rows, ADR 0002 corrections, and this write-up.

## 9. Automated validation
| # | Pair | Result | Proven by |
|---|---|---|---|
| 1 | `https://example.com` vs `https://example.com/` | Equivalent | `UrlNormalizerTest.n5_emptyPathBecomesRoot`; `ShortenIntegrationTest.equivalentUrlsUnderNormalizationRulesShareOneMapping`; `shorten.feature` (N5) |
| 2 | `HTTPS://EXAMPLE.COM/` vs `https://example.com/` | Equivalent | `UrlNormalizerTest.n2_*`, `n3_*`; `ShortenIntegrationTest.equivalentPairsShareOneMapping`; `shorten.feature` (N2 + N3 combined) |
| 3 | `https://example.com:443/` vs `https://example.com/` | Equivalent | `UrlNormalizerTest.n4_removesDefaultHttpsPort`; `ShortenIntegrationTest` (N4); `shorten.feature` (N4) |
| 4 | `http://example.com:80/` vs `http://example.com/` | Equivalent | `UrlNormalizerTest.n4_removesDefaultHttpPort`; `ShortenIntegrationTest.equivalentPairsShareOneMapping` |
| 5 | `https://example.com:8443/` vs `https://example.com/` | Distinct | `UrlNormalizerTest.NotNormalized.nonDefaultPortIsKept`; `ShortenIntegrationTest.urlsOutsideTheNormalizationRulesAreDistinct`; `shorten.feature` |
| 6 | `/path` vs `/path/` | Distinct | `NotNormalized.trailingSlashOnNonRootPathIsKept`; `ShortenIntegrationTest`; `shorten.feature` |
| 7 | `/Path` vs `/path` | Distinct | `NotNormalized.pathCaseIsKept`; `ShortenIntegrationTest`; `shorten.feature` |
| 8 | `?a=1&b=2` vs `?b=2&a=1` | Distinct | `NotNormalized.queryParameterOrderIsKept`; `ShortenIntegrationTest`; `shorten.feature` |
| 9 | `/page#top` vs `/page` | Distinct | `NotNormalized.fragmentIsKept`; `ShortenIntegrationTest`; `shorten.feature` |

Also covered:
- `ShortenConcurrencyTest.concurrentEquivalentSubmissionsCreateExactlyOneMapping`: racing equivalent forms create one row.
- `UrlServiceTest.hashesTheNormalizedUrlNotTheRawInput`.

At this stage, `./mvnw verify` reported 212 tests, 0 failures, 0 errors.

## 10. Manual validation
- **Engineer (manual):** I personally ran these comparison cases against the running application.
- **AI-executed (recorded below):** Claude Code ran the nine pairs with `curl` (`POST /api/v1/urls`) on 2026-10-01, against the live application (`./mvnw spring-boot:run`, port 8081). Each pair used its own host (`pN.example.com`), so the pairs couldn't affect each other. The table below records that run.

| # | First → result | Second → result | Outcome |
|---|---|---|---|
| 1 | `https://p1.example.com` → 201 `54a926` | `https://p1.example.com/` → 200 `54a926` | Same mapping |
| 2 | `HTTPS://P2.EXAMPLE.COM/` → 201 `d360c5` | `https://p2.example.com/` → 200 `d360c5` | Same mapping |
| 3 | `https://p3.example.com:443/` → 201 `d18b99` | `https://p3.example.com/` → 200 `d18b99` | Same mapping |
| 4 | `http://p4.example.com:80/` → 201 `19e25b` | `http://p4.example.com/` → 200 `19e25b` | Same mapping |
| 5 | `https://p5.example.com:8443/` → 201 `d5cd91` | `https://p5.example.com/` → 201 `82a1be` | Separate mappings |
| 6 | `https://p6.example.com/path` → 201 `dfe495` | `https://p6.example.com/path/` → 201 `421084` | Separate mappings |
| 7 | `https://p7.example.com/Path` → 201 `7d1295` | `https://p7.example.com/path` → 201 `64d0b7` | Separate mappings |
| 8 | `https://p8.example.com?a=1&b=2` → 201 `480191` | `https://p8.example.com?b=2&a=1` → 201 `264349` | Separate mappings |
| 9 | `https://p9.example.com/page#top` → 201 `b90fc2` | `https://p9.example.com/page` → 201 `4ed762` | Separate mappings |

In every equivalent pair, the 200 response returned the **first-submitted** `destinationUrl`. For example, pair 2 returned `HTTPS://P2.EXAMPLE.COM/`, and following that code gave `302 Location: HTTPS://P2.EXAMPLE.COM/`.

The same cases can be run from Swagger UI (`/swagger-ui.html`, "Shorten a URL").

## 11. Assumptions and known limitations
- **Equivalence is lexical**, under this application's rules. It isn't verified against the destination server.
- **The first-submitted form is the redirect target** for every equivalent submission.
- **No DNS or network access:** `www.example.com` and `example.com`, or a host with a trailing dot, stay distinct.
- **No IDN or punycode unification:** a Unicode host and its punycode form are not merged.
- **Percent-encoding variants and dot segments aren't merged.** Possible future candidates are percent-encoding case and unreserved decoding (RFC 3986 §6.2.2.1–2), but only with a data migration.
- **A deactivated equivalent isn't reused:** shortening it returns 409 `URL_DEACTIVATED`, and it is never silently reactivated.
