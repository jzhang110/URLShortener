package com.schwab.urlshortener.url.domain;

public class InvalidShortCodeException extends RuntimeException {

    public InvalidShortCodeException() {
        super("Short code is malformed.");
    }
}
