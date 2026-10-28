package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_checkpoints", schema = "app")
@IdClass(IngestionCheckpointId.class)
public class IngestionCheckpoint {

    @Id
    @Column(name = "source_name", nullable = false, updatable = false, length = 160)
    private String sourceName;

    @Id
    @Column(name = "partition_key", nullable = false, updatable = false, length = 160)
    private String partitionKey;

    @Column(name = "last_cursor", length = 500)
    private String lastCursor;

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Column(name = "last_source_updated_at")
    private Instant lastSourceUpdatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IngestionCheckpoint() {
    }

    public IngestionCheckpoint(String sourceName) {
        this.sourceName = sourceName;
        this.partitionKey = "default";
        this.updatedAt = Instant.now();
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getLastCursor() {
        return lastCursor;
    }

    public void setLastCursor(String lastCursor) {
        this.lastCursor = lastCursor;
    }

    public UUID getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(UUID lastEventId) {
        this.lastEventId = lastEventId;
    }

    public Instant getLastSourceUpdatedAt() {
        return lastSourceUpdatedAt;
    }

    public void setLastSourceUpdatedAt(Instant lastSourceUpdatedAt) {
        this.lastSourceUpdatedAt = lastSourceUpdatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
