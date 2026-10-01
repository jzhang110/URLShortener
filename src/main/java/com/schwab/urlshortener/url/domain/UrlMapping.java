package com.schwab.urlshortener.url.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The durable association between a short code and a destination. Immutable once persisted:
 * there are no setters, so a mapping can never be overwritten (BR-4).
 */
@Entity
@Table(name = "url_mapping")
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, updatable = false, length = 6)
    private String shortCode;

    /** What the client submitted (trimmed); used as the redirect target. */
    @Column(name = "destination_url", nullable = false, updatable = false, length = 2048)
    private String destinationUrl;

    /** The URL identity per ADR 0002; used for duplicate detection. */
    // One longer than destination_url: N5 can add "/" to a maximum-length input (V2 migration).
    @Column(name = "normalized_url", nullable = false, updatable = false, length = 2049)
    private String normalizedUrl;

    @Column(name = "normalized_url_hash", nullable = false, updatable = false, length = 64)
    private String normalizedUrlHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UrlMapping() {
        // for JPA
    }

    public UrlMapping(String shortCode, String destinationUrl, String normalizedUrl, String normalizedUrlHash,
               Instant createdAt) {
        this.shortCode = shortCode;
        this.destinationUrl = destinationUrl;
        this.normalizedUrl = normalizedUrl;
        this.normalizedUrlHash = normalizedUrlHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getDestinationUrl() {
        return destinationUrl;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public String getNormalizedUrlHash() {
        return normalizedUrlHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
