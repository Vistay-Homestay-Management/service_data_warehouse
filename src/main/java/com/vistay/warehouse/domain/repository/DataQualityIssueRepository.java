package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.DataQualityIssue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssue, UUID> {

    List<DataQualityIssue> findByStatus(String status);

    List<DataQualityIssue> findByRuleCode(String ruleCode);
}

