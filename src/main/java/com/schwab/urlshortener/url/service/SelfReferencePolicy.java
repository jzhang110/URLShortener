package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.domain.InvalidUrlException;
import com.schwab.urlshortener.url.domain.InvalidUrlException.Reason;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Rejects URLs pointing at this shortener's own hosts, preventing direct redirect loops. */
@Component
class SelfReferencePolicy implements DestinationPolicy {

    private final Set<String> selfHosts;

    SelfReferencePolicy(ShortenerProperties properties) {
        this.selfHosts = properties.selfHosts();
    }

    @Override
    public void check(URI destination) {
        String host = destination.getHost().toLowerCase(Locale.ROOT);
        // A trailing dot is the same DNS name ("localhost." == "localhost").
        String withoutTrailingDot = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (selfHosts.contains(withoutTrailingDot)) {
            throw new InvalidUrlException(Reason.DESTINATION_NOT_ALLOWED);
        }
    }
}
