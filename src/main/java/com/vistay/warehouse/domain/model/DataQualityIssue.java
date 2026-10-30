package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_quality_issues", schema = "app")
public class DataQualityIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "etl_run_id")
    private UUID etlRunId;

    @Column(name = "source_service", nullable = false, length = 80)
    private String sourceService;

    @Column(name = "source_record_type", nullable = false, length = 100)
    private String sourceRecordType;

    @Column(name = "source_record_id", length = 180)
    private String sourceRecordId;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;

    protected DataQualityIssue() {
    }

    public DataQualityIssue(
            String sourceService,
            String sourceRecordType,
            String sourceRecordId,
            String ruleCode,
            String severity,
            String description) {
        this.sourceService = sourceService;
        this.sourceRecordType = sourceRecordType;
        this.sourceRecordId = sourceRecordId;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.description = description != null && description.length() > 2000
                ? description.substring(0, 2000) : description;
        this.status = "OPEN";
        this.detectedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEtlRunId() {
        return etlRunId;
    }

    public void setEtlRunId(UUID etlRunId) {
        this.etlRunId = etlRunId;
    }

    public String getSourceService() {
        return sourceService;
    }

    public String getSourceRecordType() {
        return sourceRecordType;
    }

    public String getSourceRecordId() {
        return sourceRecordId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }
}

