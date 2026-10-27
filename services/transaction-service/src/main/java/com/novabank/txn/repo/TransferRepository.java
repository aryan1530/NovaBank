package com.novabank.txn.repo;

import com.novabank.txn.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface TransferRepository extends JpaRepository<Transfer, Long>, JpaSpecificationExecutor<Transfer> {
    Optional<Transfer> findByIdempotencyKey(String key);
    List<Transfer> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndDebitAccountIdAndCreatedAtAfter(Long userId, Long debitAccountId, Instant createdAt);
    boolean existsByUserIdAndDebitAccountIdAndCreditAccountIdAndStatus(Long userId, Long debitAccountId, Long creditAccountId, String status);
    List<Transfer> findByDebitAccountIdOrCreditAccountIdOrderByCreatedAtDesc(Long debit, Long credit);
}
