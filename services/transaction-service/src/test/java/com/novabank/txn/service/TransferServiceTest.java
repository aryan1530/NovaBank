package com.novabank.txn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novabank.common.exception.BusinessException;
import com.novabank.txn.domain.Transfer;
import com.novabank.txn.repo.TransferRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TransferServiceTest {
    @Test void rejectsNegativeAmount() {
        TransferService svc = service(Optional.empty());
        var req = new TransferService.TransferRequest(1L, 2L, new BigDecimal("-1.00"), "UPI", "bad");
        assertThrows(BusinessException.class, () -> svc.transfer(1L, "x", req));
    }

    @Test void returnsExistingIdempotentResult() {
        var existing = transfer(1L, "10.00", "COMPLETED");
        TransferService svc = service(Optional.of(existing));
        var result = svc.transfer(1L, "same", new TransferService.TransferRequest(1L, 2L, new BigDecimal("10.00"), "UPI", "n"));
        assertEquals("COMPLETED", result.status());
    }

    @Test void rejectsIdempotencyKeyReuseByAnotherCustomer() {
        var existing = transfer(2L, "10.00", "COMPLETED");
        TransferService svc = service(Optional.of(existing));
        var request = new TransferService.TransferRequest(1L, 2L, new BigDecimal("10.00"), "UPI", "n");
        assertThrows(BusinessException.class, () -> svc.transfer(1L, "shared", request));
    }

    private TransferService service(Optional<Transfer> existing) {
        TransferRepository transfers = (TransferRepository) Proxy.newProxyInstance(
                TransferRepository.class.getClassLoader(), new Class[]{TransferRepository.class},
                (proxy, method, args) -> method.getName().equals("findByIdempotencyKey") ? existing : null);
        return new TransferService(transfers, null, null, new ObjectMapper(), null,
                "http://acc", "http://led", "http://fr", "token");
    }

    private Transfer transfer(Long userId, String amount, String status) {
        var transfer = new Transfer();
        transfer.setAmount(new BigDecimal(amount));
        transfer.setUserId(userId);
        transfer.setStatus(status);
        transfer.setChannel("UPI");
        transfer.setDebitAccountId(1L);
        transfer.setCreditAccountId(2L);
        return transfer;
    }
}
