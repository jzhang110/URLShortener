package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.url.domain.UrlMapping;

/** Outcome of a shorten request; {@code created} is false when an existing mapping was reused. */
public record ShortenResult(UrlMapping mapping, boolean created) {

    static ShortenResult created(UrlMapping mapping) {
        return new ShortenResult(mapping, true);
    }

    static ShortenResult existing(UrlMapping mapping) {
        return new ShortenResult(mapping, false);
    }
}
