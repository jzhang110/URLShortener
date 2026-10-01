package com.schwab.urlshortener.url.service;

/** Detached, immutable view of a resolved mapping, safe to use outside the resolving transaction. */
public record ResolvedUrl(long mappingId, String shortCode, String destinationUrl) {
}
