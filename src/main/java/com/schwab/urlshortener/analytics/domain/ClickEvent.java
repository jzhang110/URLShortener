package com.schwab.urlshortener.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One successful redirect. Insert-only, so concurrent clicks never contend on shared rows.
 * References its mapping by id (backed by a foreign key) rather than an entity association,
 * keeping the analytics feature decoupled from the url feature's entity.
 */
@Entity
@Table(name = "click_event")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_mapping_id", nullable = false, updatable = false)
    private Long urlMappingId;

    @Column(name = "clicked_at", nullable = false, updatable = false)
    private Instant clickedAt;

    protected ClickEvent() {
        // for JPA
    }

    public ClickEvent(long urlMappingId, Instant clickedAt) {
        this.urlMappingId = urlMappingId;
        this.clickedAt = clickedAt;
    }
}
