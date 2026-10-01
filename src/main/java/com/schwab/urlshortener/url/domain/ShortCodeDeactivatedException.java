package com.schwab.urlshortener.url.domain;

/** The short code exists but its mapping no longer permits redirects. */
public class ShortCodeDeactivatedException extends RuntimeException {

    public ShortCodeDeactivatedException() {
        super("Short code is deactivated.");
    }
}
