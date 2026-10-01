package com.schwab.urlshortener.analytics.repository;

import com.schwab.urlshortener.analytics.domain.ClickEvent;
import com.schwab.urlshortener.analytics.domain.ClickStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    @Query("""
            select new com.schwab.urlshortener.analytics.domain.ClickStats(count(c), max(c.clickedAt))
            from ClickEvent c
            where c.urlMappingId = :urlMappingId
            """)
    ClickStats statsFor(@Param("urlMappingId") long urlMappingId);
}
