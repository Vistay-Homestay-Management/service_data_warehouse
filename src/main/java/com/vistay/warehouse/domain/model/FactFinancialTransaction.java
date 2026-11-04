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
@Table(name = "fact_financial_transactions", schema = "app")
public class FactFinancialTransaction {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_key")
    private DimProperty property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_key")
    private DimCustomerMasked customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_date_key")
    private DimDate transactionDate;

    @Column(name = "transaction_type", nullable = false, length = 24)
    private String transactionType;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "provider", length = 80)
    private String provider;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    @Column(name = "warehouse_updated_at", nullable = false)
    private Instant warehouseUpdatedAt;

    protected FactFinancialTransaction() {
    }

    public FactFinancialTransaction(UUID transactionId) {
        this.transactionId = transactionId;
        this.warehouseUpdatedAt = Instant.now();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
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

    public DimDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(DimDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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
