package com.novabank.ledger.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "transfer_id", nullable = false, unique = true)
    private Long transferId;
    private String description;
    @Column(name = "posted_at")
    private Instant postedAt = Instant.now();
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalLine> lines = new ArrayList<>();

    public Long getId() { return id; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getPostedAt() { return postedAt; }
    public List<JournalLine> getLines() { return lines; }
}
