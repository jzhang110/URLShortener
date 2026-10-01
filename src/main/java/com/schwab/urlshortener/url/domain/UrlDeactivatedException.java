package com.schwab.urlshortener.url.domain;

/** A shorten request matched an existing mapping that is deactivated; it is neither reused nor reactivated. */
public class UrlDeactivatedException extends RuntimeException {

    public UrlDeactivatedException() {
        super("URL already has a deactivated short code.");
    }
}
