package com.schwab.urlshortener.url.domain;

/** A submitted URL was rejected. The reason's message is safe to return to clients. */
public class InvalidUrlException extends RuntimeException {

    public enum Reason {
        URL_REQUIRED("A URL is required."),
        URL_TOO_LONG("The URL exceeds the maximum allowed length."),
        MALFORMED_URL("The URL is not syntactically valid."),
        UNSUPPORTED_SCHEME("Only http and https URLs are supported."),
        MISSING_HOST("The URL must contain a valid host."),
        USERINFO_NOT_ALLOWED("URLs containing user credentials are not allowed."),
        INVALID_PORT("The URL port must be between 1 and 65535."),
        DESTINATION_NOT_ALLOWED("The URL destination is not allowed.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Reason reason;

    public InvalidUrlException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
