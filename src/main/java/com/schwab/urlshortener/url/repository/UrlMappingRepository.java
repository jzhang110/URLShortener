package com.schwab.urlshortener.url.repository;

import com.schwab.urlshortener.url.domain.UrlMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    Optional<UrlMapping> findByNormalizedUrlHash(String normalizedUrlHash);

    boolean existsByShortCode(String shortCode);
}
