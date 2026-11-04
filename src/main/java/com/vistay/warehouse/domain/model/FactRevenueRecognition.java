package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fact_revenue_recognition", schema = "app")
public class FactRevenueRecognition {

    @Id
    @Column(name = "recognition_id", nullable = false, updatable = false)
    private UUID recognitionId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_key")
    private DimProperty property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recognition_date_key")
    private DimDate recognitionDate;

    @Column(name = "policy_code", nullable = false, length = 80)
    private String policyCode;

    @Column(name = "recognized_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal recognizedAmount;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FactRevenueRecognition() {
    }

    public FactRevenueRecognition(UUID recognitionId, UUID bookingId) {
        this.recognitionId = recognitionId;
        this.bookingId = bookingId;
        this.createdAt = Instant.now();
    }

    public UUID getRecognitionId() {
        return recognitionId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public DimProperty getProperty() {
        return property;
    }

    public void setProperty(DimProperty property) {
        this.property = property;
    }

    public DimDate getRecognitionDate() {
        return recognitionDate;
    }

    public void setRecognitionDate(DimDate recognitionDate) {
        this.recognitionDate = recognitionDate;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public BigDecimal getRecognizedAmount() {
        return recognizedAmount;
    }

    public void setRecognizedAmount(BigDecimal recognizedAmount) {
        this.recognizedAmount = recognizedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(UUID sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
