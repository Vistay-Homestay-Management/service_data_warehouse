package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.FactPropertyNight;
import com.vistay.warehouse.domain.model.FactPropertyNightId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactPropertyNightRepository extends JpaRepository<FactPropertyNight, FactPropertyNightId> {
}
