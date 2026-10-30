package com.vistay.warehouse.application.service;

import com.vistay.warehouse.application.dto.RevenueRecognitionEventPayload;
import com.vistay.warehouse.domain.model.DataQualityIssue;
import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.DimProperty;
import com.vistay.warehouse.domain.model.FactRevenueRecognition;
import com.vistay.warehouse.domain.model.IngestedEvent;
import com.vistay.warehouse.domain.model.IngestionCheckpoint;
import com.vistay.warehouse.domain.model.ProcessedEvent;
import com.vistay.warehouse.domain.repository.DataQualityIssueRepository;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import com.vistay.warehouse.domain.repository.DimPropertyRepository;
import com.vistay.warehouse.domain.repository.FactRevenueRecognitionRepository;
import com.vistay.warehouse.domain.repository.IngestedEventRepository;
import com.vistay.warehouse.domain.repository.IngestionCheckpointRepository;
import com.vistay.warehouse.domain.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RevenueRecognitionProjectionService {

    private static final Logger log = LoggerFactory.getLogger(RevenueRecognitionProjectionService.class);

    public static final String CONSUMER_NAME = "revenue-recognition-projection";
    public static final String SOURCE_SERVICE = "service_finance";
    public static final String EVENT_TYPE = "vistay.finance.revenue-recognized";

    private final ObjectMapper objectMapper;
    private final FactRevenueRecognitionRepository revenues;
    private final DimPropertyRepository properties;
    private final DimDateRepository dates;
    private final ProcessedEventRepository processedEvents;
    private final IngestedEventRepository ingestedEvents;
    private final IngestionCheckpointRepository checkpoints;
    private final DataQualityIssueRepository dataQualityIssues;

    public RevenueRecognitionProjectionService(
            ObjectMapper objectMapper,
            FactRevenueRecognitionRepository revenues,
            DimPropertyRepository properties,
            DimDateRepository dates,
            ProcessedEventRepository processedEvents,
            IngestedEventRepository ingestedEvents,
            IngestionCheckpointRepository checkpoints,
            DataQualityIssueRepository dataQualityIssues) {
        this.objectMapper = objectMapper;
        this.revenues = revenues;
        this.properties = properties;
        this.dates = dates;
        this.processedEvents = processedEvents;
        this.ingestedEvents = ingestedEvents;
        this.checkpoints = checkpoints;
        this.dataQualityIssues = dataQualityIssues;
    }

    @KafkaListener(
            topics = "${vistay.topics.revenue-recognition:vistay.finance.revenue-recognized.v1}",
            groupId = "${vistay.kafka.consumers.revenue.group-id:warehouse-revenue-consumer}"
    )
    @ConditionalOnProperty(name = "vistay.kafka.consumers.revenue-recognition.enabled", havingValue = "true", matchIfMissing = false)
    @Transactional
    public void onRevenueEvent(String rawMessage) {
        try {
            RevenueRecognitionEventPayload payload = objectMapper.readValue(rawMessage, RevenueRecognitionEventPayload.class);
            projectRevenue(payload);
        } catch (Exception e) {
            log.warn("Malformed revenue recognition event message: {}", e.getMessage());
            recordDataQualityIssue(null, "MALFORMED_JSON", "CRITICAL",
                    "Malformed JSON revenue recognition event: " + e.getMessage());
        }
    }

    @Transactional
    public FactRevenueRecognition projectRevenue(RevenueRecognitionEventPayload payload) {
        if (payload == null) {
            return null;
        }

        UUID recognitionId = payload.recognitionId();
        UUID bookingId = payload.bookingId();
        LocalDate recognitionDate = payload.recognitionDate();
        String policyCode = payload.policyCode();
        BigDecimal recognizedAmount = payload.recognizedAmount();
        UUID sourceEventId = payload.sourceEventId() != null ? payload.sourceEventId() : recognitionId;
        Instant occurredAt = payload.occurredAt() != null ? payload.occurredAt() : Instant.now();

        if (recognitionId == null || bookingId == null || recognitionDate == null
                || policyCode == null || recognizedAmount == null) {
            log.warn("Missing mandatory attributes for revenue recognition: recognitionId={}, bookingId={}",
                    recognitionId, bookingId);
            recordDataQualityIssue(recognitionId != null ? recognitionId.toString() : null,
                    "MISSING_MANDATORY_FIELDS", "ERROR",
                    "Missing mandatory attributes for revenue recognition");
            return null;
        }

        if (recognizedAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Negative recognized revenue amount rejected: amount={}", recognizedAmount);
            recordDataQualityIssue(recognitionId.toString(), "NEGATIVE_REVENUE_AMOUNT", "ERROR",
                    "Negative recognized revenue rejected: " + recognizedAmount);
            return null;
        }

        // Idempotency check
        if (processedEvents != null && sourceEventId != null
                && processedEvents.existsByEventIdAndConsumerName(sourceEventId, CONSUMER_NAME)) {
            log.debug("Revenue recognition event already processed, skipping: eventId={}", sourceEventId);
            return revenues.findById(recognitionId).orElse(null);
        }

        DimProperty dimProperty = null;
        if (payload.propertyId() != null && properties != null) {
            dimProperty = properties.findByPropertyIdAndCurrentRowTrue(payload.propertyId()).orElse(null);
        }

        DimDate dimDate = resolveDate(recognitionDate);
        String currency = payload.currency() != null && payload.currency().length() == 3 ? payload.currency() : "VND";

        FactRevenueRecognition fact = revenues.findById(recognitionId)
                .orElseGet(() -> new FactRevenueRecognition(recognitionId, bookingId));
        fact.setProperty(dimProperty);
        fact.setRecognitionDate(dimDate);
        fact.setPolicyCode(policyCode);
        fact.setRecognizedAmount(recognizedAmount);
        fact.setCurrency(currency);
        fact.setSourceEventId(sourceEventId);

        FactRevenueRecognition saved = revenues.save(fact);

        if (sourceEventId != null && processedEvents != null) {
            processedEvents.save(new ProcessedEvent(sourceEventId, CONSUMER_NAME, SOURCE_SERVICE, EVENT_TYPE, 1));
        }
        if (sourceEventId != null && checkpoints != null) {
            updateCheckpoint(sourceEventId, occurredAt);
        }

        log.info("Projected revenue recognition fact: recognitionId={}, bookingId={}, amount={}, policy={}",
                recognitionId, bookingId, recognizedAmount, policyCode);
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
                SOURCE_SERVICE, "RevenueRecognition", recordId, ruleCode, severity, description);
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

