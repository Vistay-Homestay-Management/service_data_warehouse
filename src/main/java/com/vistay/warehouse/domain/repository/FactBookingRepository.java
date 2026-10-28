package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.FactBooking;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactBookingRepository extends JpaRepository<FactBooking, UUID> {
}
