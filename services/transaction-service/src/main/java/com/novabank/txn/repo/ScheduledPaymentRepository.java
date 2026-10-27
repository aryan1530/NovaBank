package com.novabank.txn.repo;

import com.novabank.txn.domain.ScheduledPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {
    List<ScheduledPayment> findByStatusAndRunAtLessThanEqual(String status, Instant now);
    List<ScheduledPayment> findByUserId(Long userId);
}
