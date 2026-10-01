package com.schwab.urlshortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schwab.urlshortener.common.config.ShortenerProperties;
import com.schwab.urlshortener.url.domain.InvalidUrlException;
import com.schwab.urlshortener.url.domain.InvalidUrlException.Reason;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SelfReferencePolicyTest {

    private final SelfReferencePolicy policy = policyWithOwnHosts("localhost", "127.0.0.1", "[::1]");

    @ParameterizedTest
    @ValueSource(strings = {
            "https://sho.rt/abc123",            // base-url host
            "http://SHO.RT/abc123",             // host comparison is case-insensitive
            "https://sho.rt./abc123",           // trailing-dot FQDN form
            "http://localhost:8080/abc123",     // configured own host, any port
            "http://localhost./abc123",
            "http://127.0.0.1/abc123",
            "http://[::1]:8080/abc123",         // IPv6 loopback
            "http://[0:0:0:0:0:0:0:1]/abc123",  // same address, uncompressed
            "http://[::ffff:127.0.0.1]/abc123"  // IPv4-mapped IPv6 form of 127.0.0.1
    })
    void rejectsUrlsPointingAtTheShortener(String url) {
        assertRejected(policy, url);
    }

    @Test
    void configuredIpv6HostMayOmitBrackets() {
        assertRejected(policyWithOwnHosts("::1"), "http://[::1]/abc123");
    }

    @Test
    void allowsOtherHosts() {
        assertThatCode(() -> policy.check(URI.create("https://example.com/sho.rt"))).doesNotThrowAnyException();
        assertThatCode(() -> policy.check(URI.create("https://sub.sho.rt.example.com/"))).doesNotThrowAnyException();
        assertThatCode(() -> policy.check(URI.create("https://[2001:db8::1]/"))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "' LocalHost. ',       localhost",
            "127.0.0.1,            127.0.0.1",
            "[::1],                0:0:0:0:0:0:0:1",
            "::1,                  0:0:0:0:0:0:0:1",
            "[0:0:0:0:0:0:0:1],    0:0:0:0:0:0:0:1",
            "[::ffff:127.0.0.1],   127.0.0.1",
            // Not an IPv6 literal: kept as-is instead of being resolved, so canonicalization never uses DNS.
            "[not-an-ip:x],        [not-an-ip:x]"
    })
    void canonicalHostIsLocalAndDeterministic(String host, String expected) {
        assertThat(SelfReferencePolicy.canonicalHost(host)).isEqualTo(expected);
    }

    private static SelfReferencePolicy policyWithOwnHosts(String... ownHosts) {
        return new SelfReferencePolicy(
                new ShortenerProperties(URI.create("https://sho.rt"), List.of(ownHosts), 10, 2048));
    }

    private static void assertRejected(SelfReferencePolicy policy, String url) {
        assertThatThrownBy(() -> policy.check(URI.create(url)))
                .isInstanceOfSatisfying(InvalidUrlException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.DESTINATION_NOT_ALLOWED));
    }
}
