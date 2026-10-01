package com.schwab.urlshortener.url.domain;

public class ShortCodeNotFoundException extends RuntimeException {

    public ShortCodeNotFoundException() {
        super("Short code not found.");
    }
}
