package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.domain.InvalidUrlException;
import com.schwab.urlshortener.url.domain.InvalidUrlException.Reason;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Syntactic validation of a submitted URL, in the order defined by the design document's flowchart.
 * Destination-level rules (e.g. redirect-loop prevention) live in {@link DestinationPolicy} implementations.
 */
@Component
public class UrlValidator {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    private final int maxUrlLength;

    public UrlValidator(ShortenerProperties properties) {
        this.maxUrlLength = properties.maxUrlLength();
    }

    /**
     * @return the parsed URI of the trimmed input
     * @throws InvalidUrlException if the URL is not acceptable
     */
    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException(Reason.URL_REQUIRED);
        }
        String url = rawUrl.strip();
        if (url.length() > maxUrlLength) {
            throw new InvalidUrlException(Reason.URL_TOO_LONG);
        }
        URI uri = parse(url);
        String scheme = uri.getScheme();
        if (scheme == null || !SUPPORTED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException(Reason.UNSUPPORTED_SCHEME);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException(Reason.MISSING_HOST);
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidUrlException(Reason.USERINFO_NOT_ALLOWED);
        }
        return uri;
    }

    private static URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException(Reason.MALFORMED_URL);
        }
    }
}
