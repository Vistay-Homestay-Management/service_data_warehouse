package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.EtlRun;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlRunRepository extends JpaRepository<EtlRun, UUID> {
}

