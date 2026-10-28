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
@Table(name = "dim_property", schema = "app")
public class DimProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "property_key", nullable = false, updatable = false)
    private Long propertyKey;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "property_code", nullable = false, length = 50)
    private String propertyCode;

    @Column(name = "property_name", nullable = false, length = 180)
    private String propertyName;

    @Column(name = "property_type", length = 32)
    private String propertyType;

    @Column(name = "building_id")
    private UUID buildingId;

    @Column(name = "building_name", length = 180)
    private String buildingName;

    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "area_name", length = 160)
    private String areaName;

    @Column(name = "city_id")
    private UUID cityId;

    @Column(name = "city_name", length = 120)
    private String cityName;

    @Column(name = "operating_model", length = 24)
    private String operatingModel;

    @Column(name = "operational_status", length = 32)
    private String operationalStatus;

    @Column(name = "bedroom_count")
    private Short bedroomCount;

    @Column(name = "capacity")
    private Short capacity;

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

    protected DimProperty() {
    }

    public DimProperty(UUID propertyId, String propertyName, Instant validFrom, UUID sourceEventId, Instant sourceUpdatedAt) {
        this.propertyId = propertyId;
        this.propertyCode = propertyId != null ? propertyId.toString() : null;
        this.propertyName = propertyName;
        this.validFrom = validFrom;
        this.currentRow = true;
        this.sourceEventId = sourceEventId;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public void supersede(Instant validTo) {
        this.currentRow = false;
        this.validTo = validTo;
    }

    public Long getPropertyKey() {
        return propertyKey;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getPropertyCode() {
        return propertyCode;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getPropertyType() {
        return propertyType;
    }

    public UUID getBuildingId() {
        return buildingId;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public String getAreaName() {
        return areaName;
    }

    public UUID getCityId() {
        return cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public String getOperatingModel() {
        return operatingModel;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public Short getBedroomCount() {
        return bedroomCount;
    }

    public Short getCapacity() {
        return capacity;
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
