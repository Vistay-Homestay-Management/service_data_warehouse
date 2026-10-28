package com.vistay.warehouse.domain.model;

import java.io.Serializable;
import java.util.Objects;

public class IngestionCheckpointId implements Serializable {

    private String sourceName;
    private String partitionKey;

    public IngestionCheckpointId() {
    }

    public IngestionCheckpointId(String sourceName, String partitionKey) {
        this.sourceName = sourceName;
        this.partitionKey = partitionKey;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IngestionCheckpointId that = (IngestionCheckpointId) o;
        return Objects.equals(sourceName, that.sourceName) && Objects.equals(partitionKey, that.partitionKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceName, partitionKey);
    }
}
