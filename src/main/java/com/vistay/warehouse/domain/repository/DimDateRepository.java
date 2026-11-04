package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.DimDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DimDateRepository extends JpaRepository<DimDate, Integer> {
}
