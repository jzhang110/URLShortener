# ADR 0002: URL identity and normalization

**Status:** Accepted

## Problem
Duplicate detection needs a definition of "identical URL", which the design document doesn't give (see [the ambiguous-requirement write-up](../ai-workflow/ambiguous-requirement-url-identity.md)). Comparing raw input byte-for-byte makes `https://Example.com` and `https://example.com/` into two mappings. Aggressive normalization (reordering query strings, stripping trailing slashes, resolving dot segments) can merge URLs that servers actually treat as different.

## Alternatives
1. Raw-string identity.
2. Full RFC 3986 normalization plus common "semantic" rules: sort query parameters, drop the trailing slash, decode percent-encoding.
3. A small, closed set of rules drawn from the syntax- and scheme-based equivalences in RFC 3986 §6.2.2–6.2.3. This is a deliberate **subset**: §6.2.2's percent-encoding normalization and dot-segment removal are not applied (see below).

## Decision
Option 3. Two URLs are **equivalent under this application's URL identity rules** when they produce the same normalized URL. This is a lexical identity, not a claim that the URLs are universally semantically equivalent, and these rules are exhaustive:

| Rule | Behavior |
|---|---|
| N1 | Trim leading/trailing whitespace |
| N2 | Lowercase the scheme |
| N3 | Lowercase the host |
| N4 | Remove the port only when it's the scheme default (80/http, 443/https). An empty port (`https://example.com:/`) parses as "no port", so it's equivalent too (RFC 3986 §6.2.3) |
| N5 | An empty path becomes `/` |
| N6 | Path, query and fragment are kept byte-for-byte |

### Deliberately not normalized
These are **conservative identity decisions**. Each difference below is kept because the system cannot safely assume it is semantically irrelevant to the destination server or the page:

| Difference kept | Example | Why it isn't merged |
|---|---|---|
| Path case | `/Path` vs `/path` | Many servers treat paths case-sensitively |
| Trailing slash on a non-root path | `/path` vs `/path/` | They can be different resources, or only one may exist |
| Query parameter order and values | `?a=1&b=2` vs `?b=2&a=1` | Order can matter (signed URLs, repeated keys, arrays); values are application data |
| Fragment, including an empty `#` | `/page#top` vs `/page` | Fragments choose the landing point and drive client-side routing; the redirect target keeps them |
| An empty query `?` | `/?` vs `/` | Kept byte-for-byte under N6 |
| Percent-encoding case and unreserved decoding | `%2f` vs `%2F`, `%7E` vs `~` | Equivalent in theory (RFC 3986 §6.2.2.1–2), but not needed, and adding a rule later would change existing identities |
| Dot segments | `/a/./b` vs `/a/b` | Servers and proxies don't all resolve them the same way |
| Host trailing dot, `www.` prefix | `example.com.` vs `example.com` | Different Host header and certificate name; equivalence would need DNS, which the service never uses |
| Scheme, non-default port | `http` vs `https`, `https://h:80` | Different protocol or endpoint |

There's no DNS lookup, network request or canonicalization library.

Two values are stored:
- `destination_url` is the trimmed input. It's the redirect target.
- `normalized_url` is the identity. It's the value that gets hashed for both the short code and `normalized_url_hash`.

A resubmitted equivalent URL reuses the existing mapping only while that mapping is ACTIVE. If it has been deactivated, the request gets 409 `URL_DEACTIVATED` instead ([ADR 0009](0009-url-lifecycle-deactivation.md)).

The input is parsed once. `UrlValidator` applies N1 as it parses the trimmed input, and `UrlNormalizer` applies N2–N6 to that validated `URI`.

Only one rule can make the identity longer than the input: N5 adds a single `/` to an empty path. A maximum-length (2048-character) input can therefore normalize to 2049 characters, and `normalized_url` is sized for that (V2 migration).

Each rule has its own unit test (`UrlNormalizerTest`), and there are negative tests for what is *not* normalized.

## Tradeoffs
- (+) Never merges URLs that could lead to different content. That matters because every equivalent form is redirected to the **first-submitted** destination: an over-broad rule would silently send one submitter to a resource another submitter chose.
- (−) Some URLs that are equivalent in practice still get different codes, for example `?a=1&b=2` vs `?b=2&a=1`. The cost is only an extra mapping, and it's accepted.
- (−) For equivalent inputs, the first-submitted `destination_url` is the one every user is redirected to. For example, `HTTPS://EXAMPLE.COM` first, then `https://example.com/` → the redirect goes to `HTTPS://EXAMPLE.COM`. Under N2–N5 these differ only in scheme case, host case, a default port or an empty path, which RFC 3986 (§6.2.2.1, §6.2.3) defines as equivalent for http(s).
- Adding a rule later changes identities, so it would need a data migration that recomputes `normalized_url` and its hash.
