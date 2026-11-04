package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events", schema = "app")
@IdClass(ProcessedEventId.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Id
    @Column(name = "consumer_name", nullable = false, updatable = false, length = 120)
    private String consumerName;

    @Column(name = "source_service", nullable = false, length = 80)
    private String sourceService;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String consumerName, String sourceService, String eventType, Integer eventVersion) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.sourceService = sourceService;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getSourceService() {
        return sourceService;
    }

    public String getEventType() {
        return eventType;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
