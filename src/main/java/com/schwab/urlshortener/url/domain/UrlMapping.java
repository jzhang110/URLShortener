package com.schwab.urlshortener.url.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * The durable association between a short code and a destination. Its identity (code, destination,
 * normalized URL and hash) is immutable once persisted, so a mapping can never be overwritten (BR-4).
 * Only the lifecycle state changes, and only through intent methods that keep {@code status} and
 * {@code deactivatedAt} consistent (ADR 0009).
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

    // Lifecycle columns are deliberately updatable; invariant: deactivatedAt is null exactly when ACTIVE.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UrlStatus status;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

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
        this.status = UrlStatus.ACTIVE;
    }

    /**
     * Moves an active mapping to {@link UrlStatus#DEACTIVATED} at the given time. Idempotent: an
     * already deactivated mapping keeps its original {@code deactivatedAt}. Exhaustive over
     * {@link UrlStatus} on purpose: a new state fails to compile here until it is decided whether it
     * may transition to DEACTIVATED.
     *
     * @return true if this call changed the state, false if it was already deactivated
     */
    public boolean deactivate(Instant at) {
        Objects.requireNonNull(at, "deactivation time");
        return switch (status) {
            case ACTIVE -> {
                status = UrlStatus.DEACTIVATED;
                deactivatedAt = at;
                yield true;
            }
            case DEACTIVATED -> false; // idempotent: the original deactivatedAt is kept
        };
    }

    /** Redirect eligibility, decided by the lifecycle state. */
    public boolean canRedirect() {
        return status.allowsRedirect();
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

    public UrlStatus getStatus() {
        return status;
    }

    /** Null while the mapping is active. */
    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }
}
