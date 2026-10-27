package com.novabank.ledger.api;

import com.novabank.common.exception.BusinessException;
import com.novabank.common.money.Money;
import com.novabank.ledger.domain.JournalEntry;
import com.novabank.ledger.domain.JournalLine;
import com.novabank.ledger.domain.ReconciliationRun;
import com.novabank.ledger.repo.JournalEntryRepository;
import com.novabank.ledger.repo.ReconciliationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class LedgerController {
    private final JournalEntryRepository entries;
    private final ReconciliationRunRepository recon;

    public LedgerController(JournalEntryRepository entries, ReconciliationRunRepository recon) {
        this.entries = entries;
        this.recon = recon;
    }

    public record PostRequest(Long transferId, String description, Long debitAccountId, Long creditAccountId, BigDecimal amount) {}
    public record EntryView(Long id, Long transferId, String description, List<LineView> lines) {}
    public record LineView(Long accountId, BigDecimal debit, BigDecimal credit) {}

    @PostMapping("/internal/ledger/entries")
    @Transactional
    public EntryView post(@RequestBody PostRequest req) {
        return entries.findByTransferId(req.transferId()).map(this::view).orElseGet(() -> {
            BigDecimal amt = Money.normalize(req.amount());
            JournalEntry entry = new JournalEntry();
            entry.setTransferId(req.transferId());
            entry.setDescription(req.description());
            JournalLine debit = new JournalLine();
            debit.setEntry(entry);
            debit.setAccountId(req.debitAccountId());
            debit.setDebit(amt);
            JournalLine credit = new JournalLine();
            credit.setEntry(entry);
            credit.setAccountId(req.creditAccountId());
            credit.setCredit(amt);
            entry.getLines().add(debit);
            entry.getLines().add(credit);
            assertBalanced(entry);
            return view(entries.save(entry));
        });
    }

    @GetMapping("/api/v1/ledger/accounts/{accountId}")
    public List<EntryView> byAccount(@PathVariable Long accountId) {
        return entries.findByAccountId(accountId).stream().map(this::view).toList();
    }

    @PostMapping("/api/v1/ledger/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN','FRAUD_ANALYST')")
    public ReconciliationRun reconcile() {
        BigDecimal d = entries.sumDebits();
        BigDecimal c = entries.sumCredits();
        ReconciliationRun run = new ReconciliationRun();
        run.setDebitTotal(d);
        run.setCreditTotal(c);
        run.setBalanced(d.compareTo(c) == 0);
        run.setNotes(run.isBalanced() ? "Double-entry balanced" : "IMBALANCE");
        if (!run.isBalanced()) {
            throw new BusinessException(HttpStatus.CONFLICT, "LEDGER_IMBALANCE", "Debits do not equal credits");
        }
        return recon.save(run);
    }

    private void assertBalanced(JournalEntry entry) {
        BigDecimal d = entry.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = entry.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (d.compareTo(c) != 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "UNBALANCED_ENTRY", "Journal must balance");
        }
    }

    private EntryView view(JournalEntry e) {
        return new EntryView(e.getId(), e.getTransferId(), e.getDescription(),
                e.getLines().stream().map(l -> new LineView(l.getAccountId(), l.getDebit(), l.getCredit())).toList());
    }
}
