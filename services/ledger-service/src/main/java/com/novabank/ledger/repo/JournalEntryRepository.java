package com.novabank.ledger.repo;

import com.novabank.ledger.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    Optional<JournalEntry> findByTransferId(Long transferId);
    @Query("select distinct e from JournalEntry e join e.lines l where l.accountId = :accountId")
    List<JournalEntry> findByAccountId(Long accountId);

    @Query("select coalesce(sum(l.debit),0) from JournalLine l")
    BigDecimal sumDebits();

    @Query("select coalesce(sum(l.credit),0) from JournalLine l")
    BigDecimal sumCredits();
}
