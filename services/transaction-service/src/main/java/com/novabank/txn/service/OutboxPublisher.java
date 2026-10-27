package com.novabank.txn.service;

import com.novabank.txn.domain.OutboxEvent;
import com.novabank.txn.repo.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxEventRepository repo, KafkaTemplate<String, String> kafka) {
        this.repo = repo;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publish() {
        for (OutboxEvent e : repo.findTop50ByPublishedFalseOrderByIdAsc()) {
            try {
                kafka.send(e.getTopic(), String.valueOf(e.getId()), e.getPayload()).get(5, TimeUnit.SECONDS);
                e.setPublished(true);
                e.setPublishedAt(Instant.now());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while publishing outbox event " + e.getId(), ex);
            } catch (ExecutionException | TimeoutException ex) {
                throw new IllegalStateException("Could not publish outbox event " + e.getId(), ex);
            }
        }
    }
}
