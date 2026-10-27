package com.novabank.txn.repo;

import com.novabank.txn.domain.AssistantAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantAuditRepository extends JpaRepository<AssistantAudit, Long> {}
