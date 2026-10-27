package com.novabank.txn.service;

import com.novabank.txn.domain.ScheduledPayment;
import com.novabank.txn.repo.ScheduledPaymentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ScheduledPaymentRunner {
    private final ScheduledPaymentRepository scheduled;
    private final TransferService transfers;

    public ScheduledPaymentRunner(ScheduledPaymentRepository scheduled, TransferService transfers) {
        this.scheduled = scheduled;
        this.transfers = transfers;
    }

    public int runDue() {
        int executed = 0;
        for (ScheduledPayment payment : scheduled.findByStatusAndRunAtLessThanEqual("PENDING", Instant.now())) {
            TransferService.TransferView result = transfers.transfer(payment.getUserId(), "sched-" + payment.getId(),
                    new TransferService.TransferRequest(payment.getDebitAccountId(), payment.getCreditAccountId(),
                            payment.getAmount(), payment.getChannel(), "Scheduled"));
            payment.setStatus("COMPLETED".equals(result.status()) ? "EXECUTED" : "HELD_FRAUD");
            scheduled.save(payment);
            if ("EXECUTED".equals(payment.getStatus())) executed++;
        }
        return executed;
    }
}
