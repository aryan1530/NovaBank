package com.novabank.notify.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novabank.common.kafka.Topics;
import com.novabank.notify.domain.Notification;
import com.novabank.notify.repo.NotificationRepository;
import com.novabank.notify.repo.ProcessedEventRepository;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationConsumer {
    private final NotificationRepository notifications;
    private final ProcessedEventRepository processed;
    private final ObjectMapper mapper;

    public NotificationConsumer(NotificationRepository notifications, ProcessedEventRepository processed, ObjectMapper mapper) {
        this.notifications = notifications;
        this.processed = processed;
        this.mapper = mapper;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 500, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE, dltTopicSuffix = ".dlt")
    @KafkaListener(topics = Topics.NOTIFICATION)
    @Transactional
    public void onMessage(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) throws Exception {
        String eventId = topic + ":" + (key == null ? payload.hashCode() : key);
        if (processed.tryInsert(eventId) == 0) {
            return;
        }
        JsonNode n = mapper.readTree(payload);
        Notification note = new Notification();
        note.setUserId(n.path("userId").asLong());
        note.setTitle(n.path("title").asText("Notification"));
        note.setBody(n.path("body").asText());
        notifications.save(note);
    }

    @DltHandler
    public void dlt(String payload) {
        // dead-letter retained by Kafka topic suffix .dlt
    }
}
