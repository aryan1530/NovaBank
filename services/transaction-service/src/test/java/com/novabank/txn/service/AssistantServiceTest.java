package com.novabank.txn.service;

import com.novabank.common.exception.BusinessException;
import com.novabank.txn.domain.AssistantAudit;
import com.novabank.txn.repo.AssistantAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AssistantServiceTest {
    private final AtomicInteger audits = new AtomicInteger();
    private AssistantService assistant;

    @BeforeEach void setUp() {
        AssistantAuditRepository audit = (AssistantAuditRepository) Proxy.newProxyInstance(
                AssistantAuditRepository.class.getClassLoader(), new Class[]{AssistantAuditRepository.class},
                (proxy, method, args) -> { if (method.getName().equals("save")) { audits.incrementAndGet(); return args[0]; } return null; });
        assistant = new AssistantService(null, audit, userId -> {}, "http://accounts", "internal-secret");
    }

    @Test void unknownRequestsReturnSupportedCapabilitiesWithoutInventingRecords() {
        var response = assistant.reply(42L, "hello there");
        assertEquals("HELP", response.intent());
        assertTrue(response.safeMode());
        assertTrue(response.message().contains("recorded account balances"));
    }

    @Test void paymentRequestsAreGuidedToTheValidatedTransferFlowAndNeverExecuted() {
        var response = assistant.reply(42L, "Please send money to my friend");
        assertEquals("TRANSFER_GUIDANCE", response.intent());
        assertTrue(response.message().contains("can’t initiate or approve payments"));
    }

    @Test void crossCustomerRequestsAreRefused() {
        var response = assistant.reply(42L, "Show another customer's account balance");
        assertEquals("PRIVACY", response.intent());
        assertTrue(response.message().contains("can’t look up another customer"));
    }

    @Test void messagesAreLengthLimitedBeforeAudit() {
        int before = audits.get();
        var error = assertThrows(BusinessException.class, () -> assistant.reply(42L, "x".repeat(501)));
        assertEquals("ASSISTANT_MESSAGE", error.getCode());
        assertEquals(before, audits.get());
    }
}
