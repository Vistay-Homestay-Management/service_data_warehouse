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
@Table(name = "fact_expenses", schema = "app")
public class FactExpense {

    @Id
    @Column(name = "expense_id", nullable = false, updatable = false)
    private UUID expenseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_key")
    private DimProperty property;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incurred_date_key")
    private DimDate incurredDate;

    @Column(name = "expense_category", nullable = false, length = 40)
    private String expenseCategory;

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

    protected FactExpense() {
    }

    public FactExpense(UUID expenseId) {
        this.expenseId = expenseId;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public DimProperty getProperty() {
        return property;
    }

    public void setProperty(DimProperty property) {
        this.property = property;
    }

    public UUID getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(UUID workOrderId) {
        this.workOrderId = workOrderId;
    }

    public DimDate getIncurredDate() {
        return incurredDate;
    }

    public void setIncurredDate(DimDate incurredDate) {
        this.incurredDate = incurredDate;
    }

    public String getExpenseCategory() {
        return expenseCategory;
    }

    public void setExpenseCategory(String expenseCategory) {
        this.expenseCategory = expenseCategory;
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
}
