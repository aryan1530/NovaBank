package com.novabank.txn.service;

public interface AssistantRateLimiter {
    void check(Long userId);
}
