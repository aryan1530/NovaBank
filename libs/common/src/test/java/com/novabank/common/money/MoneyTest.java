package com.novabank.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void normalizesBankersRounding() {
        assertEquals(new BigDecimal("1.24"), Money.normalize(new BigDecimal("1.235")));
        assertTrue(Money.isPositive(Money.of("0.01")));
        assertFalse(Money.isPositive(Money.of("0.00")));
    }
}
