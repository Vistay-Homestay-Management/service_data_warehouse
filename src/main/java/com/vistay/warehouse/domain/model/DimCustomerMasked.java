package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dim_customer_masked", schema = "app")
public class DimCustomerMasked {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_key", nullable = false, updatable = false)
    private Long customerKey;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Column(name = "nationality_code", columnDefinition = "char(3)")
    private String nationalityCode;

    @Column(name = "age_band", length = 16)
    private String ageBand;

    @Column(name = "returning_guest", nullable = false)
    private boolean returningGuest;

    @Column(name = "first_seen_date")
    private LocalDate firstSeenDate;

    @Column(name = "last_seen_date")
    private LocalDate lastSeenDate;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DimCustomerMasked() {
    }

    public DimCustomerMasked(UUID customerId) {
        this.customerId = customerId;
        this.returningGuest = false;
        this.updatedAt = Instant.now();
    }

    public void recordSeen(LocalDate seenDate) {
        if (seenDate == null) {
            return;
        }
        if (firstSeenDate == null || seenDate.isBefore(firstSeenDate)) {
            this.firstSeenDate = seenDate;
        }
        if (lastSeenDate == null || seenDate.isAfter(lastSeenDate)) {
            this.lastSeenDate = seenDate;
        }
        this.returningGuest = firstSeenDate != null && lastSeenDate != null && !lastSeenDate.equals(firstSeenDate);
    }

    public Long getCustomerKey() {
        return customerKey;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getNationalityCode() {
        return nationalityCode;
    }

    public String getAgeBand() {
        return ageBand;
    }

    public boolean isReturningGuest() {
        return returningGuest;
    }

    public LocalDate getFirstSeenDate() {
        return firstSeenDate;
    }

    public LocalDate getLastSeenDate() {
        return lastSeenDate;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
