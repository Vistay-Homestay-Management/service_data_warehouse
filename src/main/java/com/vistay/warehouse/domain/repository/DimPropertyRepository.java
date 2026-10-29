package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.DimProperty;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DimPropertyRepository extends JpaRepository<DimProperty, Long> {

    Optional<DimProperty> findByPropertyIdAndCurrentRowTrue(UUID propertyId);
}
