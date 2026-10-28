package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "etl_runs", schema = "app")
public class EtlRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @Column(name = "pipeline_name", nullable = false, length = 160)
    private String pipelineName;

    @Column(name = "source_service", nullable = false, length = 80)
    private String sourceService;

    @Column(name = "load_type", nullable = false, length = 24)
    private String loadType;

    @Column(name = "watermark_from")
    private Instant watermarkFrom;

    @Column(name = "watermark_to")
    private Instant watermarkTo;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "rows_read", nullable = false)
    private Long rowsRead;

    @Column(name = "rows_inserted", nullable = false)
    private Long rowsInserted;

    @Column(name = "rows_updated", nullable = false)
    private Long rowsUpdated;

    @Column(name = "rows_rejected", nullable = false)
    private Long rowsRejected;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata;

    protected EtlRun() {
    }

    public EtlRun(String pipelineName, String sourceService, String loadType) {
        this.pipelineName = pipelineName;
        this.sourceService = sourceService;
        this.loadType = loadType;
        this.status = "RUNNING";
        this.rowsRead = 0L;
        this.rowsInserted = 0L;
        this.rowsUpdated = 0L;
        this.rowsRejected = 0L;
        this.startedAt = Instant.now();
        this.metadata = "{}";
    }

    public java.util.UUID getId() {
        return id;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getLoadType() {
        return loadType;
    }

    public void setLoadType(String loadType) {
        this.loadType = loadType;
    }

    public Instant getWatermarkFrom() {
        return watermarkFrom;
    }

    public void setWatermarkFrom(Instant watermarkFrom) {
        this.watermarkFrom = watermarkFrom;
    }

    public Instant getWatermarkTo() {
        return watermarkTo;
    }

    public void setWatermarkTo(Instant watermarkTo) {
        this.watermarkTo = watermarkTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getRowsRead() {
        return rowsRead;
    }

    public void setRowsRead(Long rowsRead) {
        this.rowsRead = rowsRead;
    }

    public Long getRowsInserted() {
        return rowsInserted;
    }

    public void setRowsInserted(Long rowsInserted) {
        this.rowsInserted = rowsInserted;
    }

    public Long getRowsUpdated() {
        return rowsUpdated;
    }

    public void setRowsUpdated(Long rowsUpdated) {
        this.rowsUpdated = rowsUpdated;
    }

    public Long getRowsRejected() {
        return rowsRejected;
    }

    public void setRowsRejected(Long rowsRejected) {
        this.rowsRejected = rowsRejected;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
