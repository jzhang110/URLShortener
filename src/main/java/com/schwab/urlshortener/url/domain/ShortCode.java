package com.schwab.urlshortener.url.domain;

import java.util.regex.Pattern;

/** The short-code format: exactly six lowercase hex characters, as produced by the generator. */
public final class ShortCode {

    public static final String FORMAT = "^[0-9a-f]{6}$";
    private static final Pattern PATTERN = Pattern.compile(FORMAT);

    private ShortCode() {
    }

    public static boolean isValid(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }

    /** @throws InvalidShortCodeException if the code does not match {@link #FORMAT} */
    public static String requireValid(String code) {
        if (!isValid(code)) {
            throw new InvalidShortCodeException();
        }
        return code;
    }
}
