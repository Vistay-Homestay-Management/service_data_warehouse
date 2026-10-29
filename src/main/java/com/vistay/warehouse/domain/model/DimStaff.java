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
@Table(name = "dim_staff", schema = "app")
public class DimStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_key", nullable = false, updatable = false)
    private Long staffKey;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "employee_code", length = 40)
    private String employeeCode;

    @Column(name = "display_name", nullable = false, length = 180)
    private String displayName;

    @Column(name = "user_type", length = 24)
    private String userType;

    @Column(name = "primary_role", length = 64)
    private String primaryRole;

    @Column(name = "branch_reference_id")
    private UUID branchReferenceId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "current_row", nullable = false)
    private boolean currentRow;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    protected DimStaff() {
    }

    public DimStaff(UUID adminUserId, String displayName, Instant validFrom, UUID sourceEventId, Instant sourceUpdatedAt) {
        this.adminUserId = adminUserId;
        this.displayName = displayName;
        this.active = true;
        this.validFrom = validFrom;
        this.currentRow = true;
        this.sourceEventId = sourceEventId;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public void supersede(Instant validTo) {
        this.currentRow = false;
        this.validTo = validTo;
    }

    public Long getStaffKey() {
        return staffKey;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUserType() {
        return userType;
    }

    public String getPrimaryRole() {
        return primaryRole;
    }

    public UUID getBranchReferenceId() {
        return branchReferenceId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public boolean isCurrentRow() {
        return currentRow;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public Instant getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }
}
