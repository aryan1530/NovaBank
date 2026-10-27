package com.novabank.account.api;

import com.novabank.account.service.AccountService;
import com.novabank.common.security.SecuritySupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    public record OpenRequest(String type) {}
    public record ApplyRequest(@NotNull Long transferId, @NotNull Long userId, @NotNull Long debitAccountId, @NotNull Long creditAccountId, @NotNull @Positive BigDecimal amount) {}

    @PostMapping("/api/v1/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.AccountView open(@RequestBody OpenRequest req) {
        return service.open(SecuritySupport.currentUserId(), req == null ? "SAVINGS" : req.type());
    }

    @GetMapping("/api/v1/accounts")
    public List<AccountService.AccountView> mine() {
        return service.mine(SecuritySupport.currentUserId());
    }

    @GetMapping("/internal/accounts/by-user/{userId}")
    public List<AccountService.AccountView> internalByUser(@PathVariable Long userId) {
        return service.internalAccountsForUser(userId);
    }

    @GetMapping("/api/v1/accounts/{id}")
    public AccountService.AccountView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/internal/accounts/apply-transfer")
    public void apply(@Valid @RequestBody ApplyRequest req) {
        service.applyTransfer(req.transferId(), req.userId(), req.debitAccountId(), req.creditAccountId(), req.amount());
    }

    @PostMapping("/api/v1/fixed-deposits")
    public Map<String, Object> fd(@RequestBody AccountService.FdRequest req) {
        return service.openFd(SecuritySupport.currentUserId(), req);
    }

    @GetMapping("/api/v1/accounts/{id}/fixed-deposits")
    public Object fds(@PathVariable Long id) {
        return service.fdsForAccount(id);
    }

    @PostMapping("/api/v1/loans")
    public Object loan(@RequestBody AccountService.LoanRequest req) {
        return service.applyLoan(SecuritySupport.currentUserId(), req);
    }

    @GetMapping("/api/v1/loans")
    public Object loans() {
        return service.loansFor(SecuritySupport.currentUserId());
    }

    @PostMapping("/api/v1/loans/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public Object decide(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.decideLoan(id, body.getOrDefault("status", "APPROVED"));
    }
}
