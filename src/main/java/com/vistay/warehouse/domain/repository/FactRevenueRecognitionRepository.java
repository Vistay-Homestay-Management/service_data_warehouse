package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.FactRevenueRecognition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactRevenueRecognitionRepository extends JpaRepository<FactRevenueRecognition, UUID> {

    List<FactRevenueRecognition> findByBookingId(UUID bookingId);
}

