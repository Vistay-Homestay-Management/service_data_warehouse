package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.DimCustomerMasked;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DimCustomerMaskedRepository extends JpaRepository<DimCustomerMasked, Long> {

    Optional<DimCustomerMasked> findByCustomerId(UUID customerId);
}
