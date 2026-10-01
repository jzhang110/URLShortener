package com.schwab.urlshortener.url.domain;

/** Every collision-retry attempt was taken. Signals that the 6-character code space needs revisiting. */
public class ShortCodeExhaustedException extends RuntimeException {

    public ShortCodeExhaustedException() {
        super("Unable to allocate a short code.");
    }
}
