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
@Table(name = "fact_bookings", schema = "app")
public class FactBooking {

    @Id
    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "booking_code", nullable = false, length = 40)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_key")
    private DimProperty property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_key")
    private DimCustomerMasked customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_key")
    private DimChannel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_staff_key")
    private DimStaff createdByStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_key")
    private DimStaff assignedStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_date_key")
    private DimDate bookingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_date_key")
    private DimDate checkInDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_out_date_key")
    private DimDate checkOutDate;

    @Column(name = "stay_type", nullable = false, length = 24)
    private String stayType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "duration_value", nullable = false, precision = 12, scale = 3)
    private BigDecimal durationValue;

    @Column(name = "price_unit", nullable = false, length = 16)
    private String priceUnit;

    @Column(name = "adults", nullable = false)
    private Short adults;

    @Column(name = "children", nullable = false)
    private Short children;

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "booking_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal bookingValue;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    @Column(name = "warehouse_updated_at", nullable = false)
    private Instant warehouseUpdatedAt;

    protected FactBooking() {
    }

    public FactBooking(UUID bookingId) {
        this.bookingId = bookingId;
        this.warehouseUpdatedAt = Instant.now();
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public DimProperty getProperty() {
        return property;
    }

    public void setProperty(DimProperty property) {
        this.property = property;
    }

    public DimCustomerMasked getCustomer() {
        return customer;
    }

    public void setCustomer(DimCustomerMasked customer) {
        this.customer = customer;
    }

    public DimChannel getChannel() {
        return channel;
    }

    public void setChannel(DimChannel channel) {
        this.channel = channel;
    }

    public DimStaff getCreatedByStaff() {
        return createdByStaff;
    }

    public void setCreatedByStaff(DimStaff createdByStaff) {
        this.createdByStaff = createdByStaff;
    }

    public DimStaff getAssignedStaff() {
        return assignedStaff;
    }

    public void setAssignedStaff(DimStaff assignedStaff) {
        this.assignedStaff = assignedStaff;
    }

    public DimDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(DimDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public DimDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(DimDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public DimDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(DimDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public String getStayType() {
        return stayType;
    }

    public void setStayType(String stayType) {
        this.stayType = stayType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getDurationValue() {
        return durationValue;
    }

    public void setDurationValue(BigDecimal durationValue) {
        this.durationValue = durationValue;
    }

    public String getPriceUnit() {
        return priceUnit;
    }

    public void setPriceUnit(String priceUnit) {
        this.priceUnit = priceUnit;
    }

    public Short getAdults() {
        return adults;
    }

    public void setAdults(Short adults) {
        this.adults = adults;
    }

    public Short getChildren() {
        return children;
    }

    public void setChildren(Short children) {
        this.children = children;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getBookingValue() {
        return bookingValue;
    }

    public void setBookingValue(BigDecimal bookingValue) {
        this.bookingValue = bookingValue;
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

    public Instant getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(Instant sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public Instant getWarehouseUpdatedAt() {
        return warehouseUpdatedAt;
    }

    public void setWarehouseUpdatedAt(Instant warehouseUpdatedAt) {
        this.warehouseUpdatedAt = warehouseUpdatedAt;
    }
}
