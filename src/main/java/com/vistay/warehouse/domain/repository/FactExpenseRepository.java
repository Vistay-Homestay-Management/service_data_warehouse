package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.FactExpense;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactExpenseRepository extends JpaRepository<FactExpense, UUID> {

    List<FactExpense> findByWorkOrderId(UUID workOrderId);
}

