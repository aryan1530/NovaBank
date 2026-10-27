package com.novabank.ledger.repo;

import com.novabank.ledger.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {
}
