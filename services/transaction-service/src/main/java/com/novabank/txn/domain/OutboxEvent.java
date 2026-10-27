package com.novabank.txn.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String topic;
    @Column(columnDefinition = "TEXT")
    private String payload;
    private boolean published;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "published_at")
    private Instant publishedAt;

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
