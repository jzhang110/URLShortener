package com.schwab.urlshortener.url.repository;

import com.schwab.urlshortener.url.domain.UrlMapping;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    /**
     * Reads the mapping with a row write lock ({@code SELECT ... FOR UPDATE}) held until the caller's
     * transaction ends, so concurrent lifecycle changes to one mapping are serialized (ADR 0009).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UrlMapping m where m.shortCode = :shortCode")
    Optional<UrlMapping> findForUpdateByShortCode(@Param("shortCode") String shortCode);

    Optional<UrlMapping> findByNormalizedUrlHash(String normalizedUrlHash);

    boolean existsByShortCode(String shortCode);
}
