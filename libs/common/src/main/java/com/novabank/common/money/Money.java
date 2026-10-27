package com.novabank.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private Money() {}

    public static BigDecimal of(String value) {
        return new BigDecimal(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("amount required");
        }
        return value.setScale(SCALE, ROUNDING);
    }

    public static boolean isPositive(BigDecimal value) {
        return normalize(value).signum() > 0;
    }
}
