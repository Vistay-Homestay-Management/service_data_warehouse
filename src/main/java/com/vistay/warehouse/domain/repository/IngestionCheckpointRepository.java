package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.IngestionCheckpoint;
import com.vistay.warehouse.domain.model.IngestionCheckpointId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionCheckpointRepository extends JpaRepository<IngestionCheckpoint, IngestionCheckpointId> {

    Optional<IngestionCheckpoint> findBySourceNameAndPartitionKey(String sourceName, String partitionKey);
}

