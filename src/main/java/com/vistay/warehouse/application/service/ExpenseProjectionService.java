package com.vistay.warehouse.application.service;

import com.vistay.warehouse.application.dto.ExpenseEventPayload;
import com.vistay.warehouse.domain.model.DataQualityIssue;
import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.DimProperty;
import com.vistay.warehouse.domain.model.FactExpense;
import com.vistay.warehouse.domain.model.IngestionCheckpoint;
import com.vistay.warehouse.domain.model.ProcessedEvent;
import com.vistay.warehouse.domain.repository.DataQualityIssueRepository;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import com.vistay.warehouse.domain.repository.DimPropertyRepository;
import com.vistay.warehouse.domain.repository.FactExpenseRepository;
import com.vistay.warehouse.domain.repository.IngestedEventRepository;
import com.vistay.warehouse.domain.repository.IngestionCheckpointRepository;
import com.vistay.warehouse.domain.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExpenseProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseProjectionService.class);

    public static final String CONSUMER_NAME = "expense-projection";
    public static final String SOURCE_SERVICE = "service_finance";
    public static final String EVENT_TYPE = "vistay.finance.expense-approved";

    private static final Set<String> AUTHORITATIVE_STATUSES = Set.of(
            "APPROVED", "POSTED", "PAID", "SETTLED"
    );

    private final ObjectMapper objectMapper;
    private final FactExpenseRepository expenses;
    private final DimPropertyRepository properties;
    private final DimDateRepository dates;
    private final ProcessedEventRepository processedEvents;
    private final IngestedEventRepository ingestedEvents;
    private final IngestionCheckpointRepository checkpoints;
    private final DataQualityIssueRepository dataQualityIssues;

    public ExpenseProjectionService(
            ObjectMapper objectMapper,
            FactExpenseRepository expenses,
            DimPropertyRepository properties,
            DimDateRepository dates,
            ProcessedEventRepository processedEvents,
            IngestedEventRepository ingestedEvents,
            IngestionCheckpointRepository checkpoints,
            DataQualityIssueRepository dataQualityIssues) {
        this.objectMapper = objectMapper;
        this.expenses = expenses;
        this.properties = properties;
        this.dates = dates;
        this.processedEvents = processedEvents;
        this.ingestedEvents = ingestedEvents;
        this.checkpoints = checkpoints;
        this.dataQualityIssues = dataQualityIssues;
    }

    @KafkaListener(
            topics = "${vistay.topics.expense-events:vistay.finance.expense-approved.v1}",
            groupId = "${vistay.kafka.consumers.expense.group-id:warehouse-expense-consumer}"
    )
    @ConditionalOnProperty(name = "vistay.kafka.consumers.expense.enabled", havingValue = "true", matchIfMissing = false)
    @Transactional
    public void onExpenseEvent(String rawMessage) {
        try {
            ExpenseEventPayload payload = objectMapper.readValue(rawMessage, ExpenseEventPayload.class);
            projectExpense(payload);
        } catch (Exception e) {
            log.warn("Malformed expense event message: {}", e.getMessage());
            recordDataQualityIssue(null, "MALFORMED_JSON", "CRITICAL",
                    "Malformed JSON expense message: " + e.getMessage());
        }
    }

    @Transactional
    public FactExpense projectExpense(ExpenseEventPayload payload) {
        if (payload == null) {
            return null;
        }

        UUID expenseId = payload.expenseId();
        String category = payload.expenseCategory();
        LocalDate incurredDate = payload.incurredDate();
        String status = payload.status();
        BigDecimal amount = payload.amount();
        UUID sourceEventId = payload.sourceEventId() != null ? payload.sourceEventId() : expenseId;
        Instant occurredAt = payload.occurredAt() != null ? payload.occurredAt() : Instant.now();

        if (expenseId == null || category == null || incurredDate == null || amount == null || status == null) {
            log.warn("Missing mandatory attributes for expense: expenseId={}", expenseId);
            recordDataQualityIssue(expenseId != null ? expenseId.toString() : null,
                    "MISSING_MANDATORY_FIELDS", "ERROR",
                    "Missing mandatory attributes for expense");
            return null;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Non-positive expense amount rejected: expenseId={}, amount={}", expenseId, amount);
            recordDataQualityIssue(expenseId.toString(), "NON_POSITIVE_EXPENSE", "ERROR",
                    "Expense amount must be strictly positive: " + amount);
            return null;
        }

        if (!AUTHORITATIVE_STATUSES.contains(status.toUpperCase())) {
            log.info("Non-authoritative expense status ignored: expenseId={}, status={}", expenseId, status);
            return null;
        }

        // Idempotency check
        if (processedEvents != null && sourceEventId != null
                && processedEvents.existsByEventIdAndConsumerName(sourceEventId, CONSUMER_NAME)) {
            log.debug("Expense event already processed, skipping: eventId={}", sourceEventId);
            return expenses.findById(expenseId).orElse(null);
        }

        // Stale update check
        Optional<FactExpense> existing = expenses.findById(expenseId);
        if (existing.isPresent()) {
            Instant existingUpdated = existing.get().getSourceUpdatedAt();
            if (existingUpdated != null && occurredAt.isBefore(existingUpdated)) {
                log.warn("Stale expense event ignored: incoming occurredAt={}, existing sourceUpdatedAt={}",
                        occurredAt, existingUpdated);
                recordDataQualityIssue(expenseId.toString(), "STALE_EVENT_OUT_OF_ORDER", "WARNING",
                        "Stale expense event ignored");
                if (processedEvents != null && sourceEventId != null) {
                    processedEvents.save(new ProcessedEvent(sourceEventId, CONSUMER_NAME, SOURCE_SERVICE, EVENT_TYPE, 1));
                }
                return existing.get();
            }
        }

        DimProperty dimProperty = null;
        if (payload.propertyId() != null && properties != null) {
            dimProperty = properties.findByPropertyIdAndCurrentRowTrue(payload.propertyId()).orElse(null);
        }

        DimDate dimDate = resolveDate(incurredDate);
        String currency = payload.currency() != null && payload.currency().length() == 3 ? payload.currency() : "VND";

        FactExpense fact = existing.orElseGet(() -> new FactExpense(expenseId));
        fact.setProperty(dimProperty);
        fact.setWorkOrderId(payload.workOrderId());
        fact.setIncurredDate(dimDate);
        fact.setExpenseCategory(category);
        fact.setStatus(status.toUpperCase());
        fact.setAmount(amount);
        fact.setCurrency(currency);
        fact.setSourceEventId(sourceEventId);
        fact.setSourceUpdatedAt(occurredAt);

        FactExpense saved = expenses.save(fact);

        if (sourceEventId != null && processedEvents != null) {
            processedEvents.save(new ProcessedEvent(sourceEventId, CONSUMER_NAME, SOURCE_SERVICE, EVENT_TYPE, 1));
        }
        if (sourceEventId != null && checkpoints != null) {
            updateCheckpoint(sourceEventId, occurredAt);
        }

        log.info("Projected expense fact: expenseId={}, category={}, amount={}, status={}",
                expenseId, category, amount, status);
        return saved;
    }

    private DimDate resolveDate(LocalDate date) {
        if (dates == null) {
            return null;
        }
        int key = DimDate.dateKey(date);
        return dates.findById(key).orElseGet(() -> dates.save(DimDate.of(date)));
    }

    private void recordDataQualityIssue(String recordId, String ruleCode, String severity, String description) {
        if (dataQualityIssues == null) {
            return;
        }
        DataQualityIssue issue = new DataQualityIssue(
                SOURCE_SERVICE, "Expense", recordId, ruleCode, severity, description);
        dataQualityIssues.save(issue);
    }

    private void updateCheckpoint(UUID eventId, Instant occurredAt) {
        if (checkpoints == null) {
            return;
        }
        IngestionCheckpoint cp = checkpoints.findBySourceNameAndPartitionKey(SOURCE_SERVICE, "default")
                .orElseGet(() -> new IngestionCheckpoint(SOURCE_SERVICE));
        cp.setLastEventId(eventId);
        cp.setLastSourceUpdatedAt(occurredAt);
        cp.setLastCursor(eventId.toString());
        cp.setUpdatedAt(Instant.now());
        checkpoints.save(cp);
    }
}

