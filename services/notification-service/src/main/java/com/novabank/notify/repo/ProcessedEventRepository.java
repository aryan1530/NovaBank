package com.novabank.notify.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<com.novabank.notify.domain.ProcessedEvent, String> {
    @Modifying
    @Query(value = "insert into processed_events(event_id) values (:id) on conflict do nothing", nativeQuery = true)
    int tryInsert(@Param("id") String id);
}
