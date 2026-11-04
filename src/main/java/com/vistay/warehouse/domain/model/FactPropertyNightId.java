package com.vistay.warehouse.domain.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class FactPropertyNightId implements Serializable {

    private UUID propertyId;
    private LocalDate serviceDate;

    public FactPropertyNightId() {
    }

    public FactPropertyNightId(UUID propertyId, LocalDate serviceDate) {
        this.propertyId = propertyId;
        this.serviceDate = serviceDate;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FactPropertyNightId that = (FactPropertyNightId) o;
        return Objects.equals(propertyId, that.propertyId) && Objects.equals(serviceDate, that.serviceDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, serviceDate);
    }
}
