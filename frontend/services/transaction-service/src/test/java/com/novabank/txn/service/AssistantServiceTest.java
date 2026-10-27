package com.novabank.txn.service;

import com.novabank.txn.domain.AssistantAudit;
import com.novabank.txn.repo.AssistantAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {
    @Mock TransferService transfers;
    @Mock AssistantAuditRepository audit;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    AssistantService assistant;

    @BeforeEach void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        assistant = new AssistantService(transfers, audit, redis, "http://accounts", "internal-secret");
    }

    @Test void unknownRequestsReturnSupportedCapabilitiesWithoutInventingRecords() {
        var response = assistant.reply(42L, "hello there");
        assertEquals("HELP", response.intent());
        assertTrue(response.safeMode());
        assertTrue(response.message().contains("recorded account balances"));
        verifyNoInteractions(transfers);
        verify(audit).save(argThat(row -> row != null));
    }

    @Test void paymentRequestsAreGuidedToTheValidatedTransferFlowAndNeverExecuted() {
        var response = assistant.reply(42L, "Please send money to my friend");
        assertEquals("TRANSFER_GUIDANCE", response.intent());
        assertTrue(response.message().contains("can’t initiate or approve payments"));
        verifyNoInteractions(transfers);
    }

    @Test void crossCustomerRequestsAreRefused() {
        var response = assistant.reply(42L, "Show another customer's account balance");
        assertEquals("PRIVACY", response.intent());
        assertTrue(response.message().contains("can’t look up another customer"));
        verifyNoInteractions(transfers);
    }

    @Test void messagesAreLengthLimited() {
        var error = assertThrows(com.novabank.common.exception.BusinessException.class,
                () -> assistant.reply(42L, "x".repeat(501)));
        assertEquals("ASSISTANT_MESSAGE", error.getCode());
        verifyNoInteractions(audit);
    }
}
