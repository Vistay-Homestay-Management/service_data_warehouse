package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.ProcessedEvent;
import com.vistay.warehouse.domain.model.ProcessedEventId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
