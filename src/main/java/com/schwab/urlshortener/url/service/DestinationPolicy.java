package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.url.domain.InvalidUrlException;
import java.net.URI;

/**
 * A rule deciding whether a syntactically valid URL may be shortened.
 * Extension point: future reputation / malicious-link checks are added as further beans.
 */
public interface DestinationPolicy {

    /**
     * Checks whether a syntactically valid destination is permitted.
     *
     * @throws InvalidUrlException if the destination violates this policy
     */
    void check(URI destination);
}
