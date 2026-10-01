package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.url.domain.InvalidUrlException;
import java.net.URI;

/**
 * A rule deciding whether a syntactically valid URL may be shortened.
 * Extension point: future reputation / malicious-link checks are added as further beans.
 */
public interface DestinationPolicy {

    /** @throws InvalidUrlException with {@code DESTINATION_NOT_ALLOWED} if the destination is rejected */
    void check(URI destination);
}
