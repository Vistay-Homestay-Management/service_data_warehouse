package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.IngestedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestedEventRepository extends JpaRepository<IngestedEvent, UUID> {
}
