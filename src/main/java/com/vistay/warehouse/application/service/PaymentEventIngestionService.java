package com.vistay.warehouse.application.service;

import com.vistay.warehouse.domain.model.DataQualityIssue;
import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.FactBooking;
import com.vistay.warehouse.domain.model.FactFinancialTransaction;
import com.vistay.warehouse.domain.model.IngestedEvent;
import com.vistay.warehouse.domain.model.IngestionCheckpoint;
import com.vistay.warehouse.domain.model.ProcessedEvent;
import com.vistay.warehouse.domain.repository.DataQualityIssueRepository;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import com.vistay.warehouse.domain.repository.FactBookingRepository;
import com.vistay.warehouse.domain.repository.FactFinancialTransactionRepository;
import com.vistay.warehouse.domain.repository.IngestedEventRepository;
import com.vistay.warehouse.domain.repository.IngestionCheckpointRepository;
import com.vistay.warehouse.domain.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentEventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventIngestionService.class);

    public static final String CONSUMER_NAME = "payment-ingestion";
    public static final String SOURCE_SERVICE = "service_payment";
    public static final String EXPECTED_EVENT_TYPE = "vistay.payment.transaction-confirmed";
    public static final int EXPECTED_VERSION = 1;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ObjectMapper objectMapper;
    private final FactFinancialTransactionRepository transactions;
    private final FactBookingRepository bookings;
    private final DimDateRepository dates;
    private final ProcessedEventRepository processedEvents;
    private final IngestedEventRepository ingestedEvents;
    private final IngestionCheckpointRepository checkpoints;
    private final DataQualityIssueRepository dataQualityIssues;

    public PaymentEventIngestionService(
            ObjectMapper objectMapper,
            FactFinancialTransactionRepository transactions,
            FactBookingRepository bookings,
            DimDateRepository dates,
            ProcessedEventRepository processedEvents,
            IngestedEventRepository ingestedEvents,
            IngestionCheckpointRepository checkpoints,
            DataQualityIssueRepository dataQualityIssues) {
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.bookings = bookings;
        this.dates = dates;
        this.processedEvents = processedEvents;
        this.ingestedEvents = ingestedEvents;
        this.checkpoints = checkpoints;
        this.dataQualityIssues = dataQualityIssues;
    }

    @KafkaListener(topics = "${vistay.topics.payment-confirmed:vistay.payment.transaction-confirmed.v1}", groupId = "${vistay.kafka.consumers.payment.group-id:warehouse-payment-consumer}")
    @Transactional
    public void onPaymentConfirmed(String rawMessage) {
        String payloadSha256 = EventSecurityUtil.computeSha256(rawMessage);
        String sanitizedPayload = EventSecurityUtil.sanitizePayload(rawMessage, objectMapper);

        JsonNode root;
        try {
            root = objectMapper.readTree(rawMessage);
        } catch (Exception e) {
            log.warn("Malformed payment event message: {}", e.getMessage());
            recordDataQualityIssue(null, "MALFORMED_JSON", "CRITICAL",
                    "Malformed JSON payment message: " + e.getMessage());
            return;
        }

        if (root == null || !root.isObject()) {
            return;
        }

        String eventType = textOrNull(root, "type");
        int version = intOrDefault(root, "version", 0);
        String eventIdStr = textOrNull(root, "eventId");

        if (eventIdStr == null) {
            recordDataQualityIssue(null, "MISSING_MANDATORY_FIELDS", "ERROR",
                    "Payment event missing eventId");
            return;
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(eventIdStr);
        } catch (IllegalArgumentException e) {
            recordDataQualityIssue(eventIdStr, "INVALID_UUID", "ERROR",
                    "Invalid eventId UUID: " + eventIdStr);
            return;
        }

        // Validate event type and version
        if (!EXPECTED_EVENT_TYPE.equals(eventType)) {
            log.warn("Unsupported payment event type: {}", eventType);
            recordDataQualityIssue(eventId.toString(), "UNSUPPORTED_EVENT_TYPE", "ERROR",
                    "Unsupported payment event type: " + eventType);
            recordIngestedEvent(eventId, eventType, version, null, sanitizedPayload, payloadSha256,
                    "QUARANTINED", "Unsupported event type: " + eventType, Instant.now());
            return;
        }

        if (version != EXPECTED_VERSION) {
            log.warn("Unsupported payment event version: {}", version);
            recordDataQualityIssue(eventId.toString(), "UNSUPPORTED_VERSION", "ERROR",
                    "Unsupported payment event version: " + version);
            recordIngestedEvent(eventId, eventType, version, null, sanitizedPayload, payloadSha256,
                    "QUARANTINED", "Unsupported version: " + version, Instant.now());
            return;
        }

        // Integrity conflict check
        if (ingestedEvents != null) {
            Optional<IngestedEvent> existingIngested = ingestedEvents.findById(eventId);
            if (existingIngested.isPresent()) {
                if (!payloadSha256.equals(existingIngested.get().getPayloadSha256())) {
                    log.error("Integrity conflict: payment eventId={} already exists with different payload hash!", eventId);
                    recordDataQualityIssue(eventId.toString(), "INTEGRITY_CONFLICT", "CRITICAL",
                            "Payment event duplicate with conflicting payload hash: " + eventId);
                    IngestedEvent quarantined = existingIngested.get();
                    quarantined.setProcessingStatus("QUARANTINED");
                    quarantined.setProcessingError("Duplicate event ID with conflicting payload SHA256");
                    ingestedEvents.save(quarantined);
                    return;
                }
            }
        }

        // Idempotency check
        if (processedEvents.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.debug("Payment event already processed, skipping: eventId={}", eventId);
            return;
        }

        JsonNode data = root.path("data");
        if (data.isMissingNode() || !data.isObject()) {
            recordDataQualityIssue(eventId.toString(), "MISSING_DATA_PAYLOAD", "ERROR",
                    "Payment event missing data block");
            recordIngestedEvent(eventId, eventType, version, null, sanitizedPayload, payloadSha256,
                    "QUARANTINED", "Missing data block", Instant.now());
            return;
        }

        String providerTxStr = textOrNull(data, "providerTransactionId");
        String matchStatus = textOrNull(data, "matchStatus");
        String amountStr = textOrNull(data, "amount");
        String currency = textOrNull(data, "currency");
        if (currency == null) {
            currency = "VND";
        }
        String occurredAtStr = textOrNull(data, "occurredAt");
        String paymentIntentIdStr = textOrNull(data, "paymentIntentId");

        if (providerTxStr == null || amountStr == null) {
            recordDataQualityIssue(eventId.toString(), "MISSING_MANDATORY_FIELDS", "ERROR",
                    "Payment data missing providerTransactionId or amount");
            recordIngestedEvent(eventId, eventType, version, null, sanitizedPayload, payloadSha256,
                    "QUARANTINED", "Missing mandatory payment attributes", Instant.now());
            return;
        }

        if (!"MATCHED".equalsIgnoreCase(matchStatus)) {
            log.info("Ignoring non-matched payment transaction: providerTx={}, status={}",
                    providerTxStr, matchStatus);
            recordIngestedEvent(eventId, eventType, version, null, sanitizedPayload, payloadSha256,
                    "QUARANTINED", "Transaction matchStatus is not MATCHED: " + matchStatus, Instant.now());
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER_NAME, SOURCE_SERVICE, eventType, version));
            return;
        }

        UUID transactionId;
        try {
            transactionId = UUID.fromString(providerTxStr);
        } catch (IllegalArgumentException e) {
            recordDataQualityIssue(providerTxStr, "INVALID_TRANSACTION_ID", "ERROR",
                    "Invalid providerTransactionId: " + providerTxStr);
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            recordDataQualityIssue(providerTxStr, "INVALID_AMOUNT", "ERROR",
                    "Invalid transaction amount: " + amountStr);
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            recordDataQualityIssue(providerTxStr, "NON_POSITIVE_AMOUNT", "ERROR",
                    "Transaction amount must be strictly positive: " + amount);
            return;
        }

        Instant occurredAt = Instant.now();
        if (occurredAtStr != null) {
            try {
                occurredAt = Instant.parse(occurredAtStr);
            } catch (Exception ignored) {
            }
        }

        UUID bookingId = null;
        if (paymentIntentIdStr != null && !paymentIntentIdStr.isBlank()) {
            try {
                bookingId = UUID.fromString(paymentIntentIdStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Stale check for transaction
        Optional<FactFinancialTransaction> existingTx = transactions.findById(transactionId);
        if (existingTx.isPresent()) {
            Instant existingUpdated = existingTx.get().getSourceUpdatedAt();
            if (existingUpdated != null && occurredAt.isBefore(existingUpdated)) {
                log.warn("Stale payment transaction event: incoming occurredAt={}, existing={}",
                        occurredAt, existingUpdated);
                recordDataQualityIssue(transactionId.toString(), "STALE_EVENT_OUT_OF_ORDER", "WARNING",
                        "Stale payment event ignored: incoming occurredAt is before existing sourceUpdatedAt");
                recordIngestedEvent(eventId, eventType, version, transactionId, sanitizedPayload, payloadSha256,
                        "PROJECTED", null, occurredAt);
                processedEvents.save(new ProcessedEvent(eventId, CONSUMER_NAME, SOURCE_SERVICE, eventType, version));
                updateCheckpoint(eventId, occurredAt);
                return;
            }
        }

        LocalDate txDate = occurredAt.atZone(VN_ZONE).toLocalDate();
        DimDate transactionDate = resolveDate(txDate);

        FactFinancialTransaction fact = existingTx.orElseGet(() -> new FactFinancialTransaction(transactionId));
        fact.setBookingId(bookingId);
        fact.setTransactionType("PAYMENT");
        fact.setPaymentMethod("VIETQR");
        fact.setProvider("NAPAS247");
        fact.setStatus("CONFIRMED");
        fact.setAmount(amount);
        fact.setCurrency(currency.length() == 3 ? currency : "VND");
        fact.setTransactionDate(transactionDate);
        fact.setSourceEventId(eventId);
        fact.setSourceUpdatedAt(occurredAt);

        // Link property/customer if linked booking is available
        if (bookingId != null && bookings != null) {
            Optional<FactBooking> linkedBooking = bookings.findById(bookingId);
            if (linkedBooking.isPresent()) {
                fact.setProperty(linkedBooking.get().getProperty());
                fact.setCustomer(linkedBooking.get().getCustomer());
            }
        }

        transactions.save(fact);

        recordIngestedEvent(eventId, eventType, version, transactionId, sanitizedPayload, payloadSha256,
                "PROJECTED", null, occurredAt);
        processedEvents.save(new ProcessedEvent(eventId, CONSUMER_NAME, SOURCE_SERVICE, eventType, version));
        updateCheckpoint(eventId, occurredAt);

        log.info("Ingested financial transaction: txId={}, amount={}, currency={}, bookingId={}",
                transactionId, amount, fact.getCurrency(), bookingId);
    }

    private String textOrNull(JsonNode parent, String fieldName) {
        JsonNode node = parent.path(fieldName);
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    private int intOrDefault(JsonNode parent, String fieldName, int defaultVal) {
        JsonNode node = parent.path(fieldName);
        return node.isNumber() ? node.asInt() : defaultVal;
    }

    private DimDate resolveDate(LocalDate calendarDate) {
        int key = DimDate.dateKey(calendarDate);
        return dates.findById(key).orElseGet(() -> dates.save(DimDate.of(calendarDate)));
    }

    private void recordIngestedEvent(
            UUID eventId,
            String eventType,
            int version,
            UUID aggregateId,
            String sanitizedPayload,
            String payloadSha256,
            String status,
            String error,
            Instant occurredAt) {
        if (ingestedEvents == null || eventId == null) {
            return;
        }
        IngestedEvent entity = ingestedEvents.findById(eventId).orElseGet(() -> new IngestedEvent(eventId));
        entity.setEventType(eventType);
        entity.setEventVersion(version > 0 ? version : 1);
        entity.setSourceService(SOURCE_SERVICE);
        entity.setAggregateType("FinancialTransaction");
        entity.setAggregateId(aggregateId);
        entity.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
        entity.setPayload(sanitizedPayload != null ? sanitizedPayload : "{}");
        entity.setPayloadSha256(payloadSha256);
        entity.setProcessingStatus(status);
        entity.setProcessingError(error);
        ingestedEvents.save(entity);
    }

    private void recordDataQualityIssue(String recordId, String ruleCode, String severity, String description) {
        if (dataQualityIssues == null) {
            return;
        }
        DataQualityIssue issue = new DataQualityIssue(
                SOURCE_SERVICE, "FinancialTransaction", recordId, ruleCode, severity, description);
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
        cp.setLastCursor(eventId != null ? eventId.toString() : null);
        cp.setUpdatedAt(Instant.now());
        checkpoints.save(cp);
    }
}
