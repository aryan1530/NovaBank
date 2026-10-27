package com.novabank.common.kafka;

public final class Topics {
    public static final String TRANSFER_COMPLETED = "banking.transfer.completed";
    public static final String TRANSFER_FAILED = "banking.transfer.failed";
    public static final String FRAUD_SCORED = "banking.fraud.scored";
    public static final String NOTIFICATION = "banking.notification";
    public static final String AUDIT = "banking.audit";
    public static final String DLQ = "banking.dlq";
    private Topics() {}
}
