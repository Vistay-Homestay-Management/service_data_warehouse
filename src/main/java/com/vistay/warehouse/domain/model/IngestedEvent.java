package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingested_events", schema = "app")
public class IngestedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @Column(name = "source_service", nullable = false, length = 80)
    private String sourceService;

    @Column(name = "aggregate_type", length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "causation_id", length = 100)
    private String causationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "payload_sha256", nullable = false, columnDefinition = "char(64)")
    private String payloadSha256;

    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    protected IngestedEvent() {
    }

    public IngestedEvent(UUID eventId) {
        this.eventId = eventId;
        this.eventVersion = 1;
        this.ingestedAt = Instant.now();
        this.processingStatus = "RECEIVED";
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(Integer eventVersion) {
        this.eventVersion = eventVersion;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public void setPayloadSha256(String payloadSha256) {
        this.payloadSha256 = payloadSha256;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }
}
