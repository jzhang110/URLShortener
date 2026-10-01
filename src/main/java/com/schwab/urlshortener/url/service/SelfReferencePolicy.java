package com.schwab.urlshortener.url.service;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.domain.InvalidUrlException;
import com.schwab.urlshortener.url.domain.InvalidUrlException.Reason;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Rejects URLs pointing at this shortener's own hosts, preventing direct redirect loops. */
@Component
class SelfReferencePolicy implements DestinationPolicy {

    private final Set<String> selfHosts;

    SelfReferencePolicy(ShortenerProperties properties) {
        this.selfHosts = properties.selfHosts().stream()
                .map(SelfReferencePolicy::canonicalHost)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void check(URI destination) {
        if (selfHosts.contains(canonicalHost(destination.getHost()))) {
            throw new InvalidUrlException(Reason.DESTINATION_NOT_ALLOWED);
        }
    }

    /**
     * The single comparison form for configured self-hosts and incoming URI hosts. Purely local: no DNS.
     * Lowercases, drops a trailing dot ("localhost." is "localhost"), and rewrites IPv6 literals to one
     * textual form, so {@code [::1]}, {@code ::1} and {@code [0:0:0:0:0:0:0:1]} compare equal.
     */
    static String canonicalHost(String host) {
        String canonical = host.strip().toLowerCase(Locale.ROOT);
        if (canonical.endsWith(".")) {
            canonical = canonical.substring(0, canonical.length() - 1);
        }
        if (!canonical.contains(":")) {
            return canonical; // hostname or IPv4: never handed to InetAddress
        }
        String bare = canonical.startsWith("[") && canonical.endsWith("]")
                ? canonical.substring(1, canonical.length() - 1)
                : canonical;
        try {
            // Brackets make InetAddress require an IPv6 literal: it parses or throws, and never resolves.
            return InetAddress.getByName("[" + bare + "]").getHostAddress();
        } catch (UnknownHostException e) {
            return canonical;
        }
    }
}
