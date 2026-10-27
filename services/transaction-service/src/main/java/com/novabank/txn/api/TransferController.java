package com.novabank.txn.api;

import com.novabank.common.exception.BusinessException;
import com.novabank.common.security.SecuritySupport;
import com.novabank.txn.domain.ScheduledPayment;
import com.novabank.txn.service.TransferService;
import com.novabank.txn.service.ScheduledPaymentRunner;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TransferController {
    private final TransferService service;
    private final ScheduledPaymentRunner scheduledRunner;

    public TransferController(TransferService service, ScheduledPaymentRunner scheduledRunner) {
        this.service = service;
        this.scheduledRunner = scheduledRunner;
    }

    @PostMapping("/transfers")
    public TransferService.TransferView transfer(@RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody TransferService.TransferRequest req) {
        Long userId = SecuritySupport.currentUserId();
        if (service.rateLimit(userId)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT", "Too many transfers");
        }
        return service.transfer(userId, key, req);
    }

    @GetMapping("/transfers")
    public List<TransferService.TransferView> history(@RequestParam(required = false) Long accountId) {
        return service.history(SecuritySupport.currentUserId(), accountId);
    }

    @GetMapping("/statements/{accountId}")
    public Map<String, Object> statement(@PathVariable Long accountId) {
        return service.statement(SecuritySupport.currentUserId(), accountId);
    }

    @PostMapping("/scheduled-payments")
    public ScheduledPayment schedule(@RequestBody ScheduledPayment p) {
        return service.schedule(SecuritySupport.currentUserId(), p);
    }

    @GetMapping("/scheduled-payments")
    public List<ScheduledPayment> scheduled() {
        return service.mySchedule(SecuritySupport.currentUserId());
    }

    @PostMapping("/scheduled-payments/run-due")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> runDue() {
        return Map.of("executed", scheduledRunner.runDue());
    }
}
