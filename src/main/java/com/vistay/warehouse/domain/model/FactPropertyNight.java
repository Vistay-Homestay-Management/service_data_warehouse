package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fact_property_nights", schema = "app")
@IdClass(FactPropertyNightId.class)
public class FactPropertyNight {

    @Id
    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Id
    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_key", nullable = false)
    private DimProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "date_key", nullable = false)
    private DimDate date;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "eligible_for_sale", nullable = false)
    private boolean eligibleForSale;

    @Column(name = "occupied", nullable = false)
    private boolean occupied;

    @Column(name = "unavailable_reason", length = 32)
    private String unavailableReason;

    @Column(name = "allocated_booking_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedBookingValue;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FactPropertyNight() {
    }

    public FactPropertyNight(UUID propertyId, LocalDate serviceDate) {
        this.propertyId = propertyId;
        this.serviceDate = serviceDate;
        this.allocatedBookingValue = BigDecimal.ZERO;
        this.currency = "VND";
        this.updatedAt = Instant.now();
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public DimProperty getProperty() {
        return property;
    }

    public void setProperty(DimProperty property) {
        this.property = property;
    }

    public DimDate getDate() {
        return date;
    }

    public void setDate(DimDate date) {
        this.date = date;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public boolean isEligibleForSale() {
        return eligibleForSale;
    }

    public void setEligibleForSale(boolean eligibleForSale) {
        this.eligibleForSale = eligibleForSale;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }

    public BigDecimal getAllocatedBookingValue() {
        return allocatedBookingValue;
    }

    public void setAllocatedBookingValue(BigDecimal allocatedBookingValue) {
        this.allocatedBookingValue = allocatedBookingValue;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
