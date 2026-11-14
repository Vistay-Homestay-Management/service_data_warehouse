package com.vistay.warehouse.application.service;

import com.vistay.warehouse.application.dto.BookingEventPayload;
import com.vistay.warehouse.domain.model.DataQualityIssue;
import com.vistay.warehouse.domain.model.DimCustomerMasked;
import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.DimProperty;
import com.vistay.warehouse.domain.model.FactBooking;
import com.vistay.warehouse.domain.model.IngestedEvent;
import com.vistay.warehouse.domain.model.IngestionCheckpoint;
import com.vistay.warehouse.domain.model.ProcessedEvent;
import com.vistay.warehouse.domain.repository.DataQualityIssueRepository;
import com.vistay.warehouse.domain.repository.DimCustomerMaskedRepository;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import com.vistay.warehouse.domain.repository.DimPropertyRepository;
import com.vistay.warehouse.domain.repository.FactBookingRepository;
import com.vistay.warehouse.domain.repository.IngestedEventRepository;
import com.vistay.warehouse.domain.repository.IngestionCheckpointRepository;
import com.vistay.warehouse.domain.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class BookingEventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BookingEventIngestionService.class);

    static final String CONSUMER_NAME = "booking-ingestion";
    static final String SOURCE_SERVICE = "service_booking";
    static final String DEFAULT_EVENT_TYPE = "booking.updated";

    private final ObjectMapper objectMapper;
    private final DimPropertyRepository properties;
    private final DimCustomerMaskedRepository customers;
    private final DimDateRepository dates;
    private final FactBookingRepository bookings;
    private final ProcessedEventRepository processedEvents;
    private final IngestedEventRepository ingestedEvents;
    private final IngestionCheckpointRepository checkpoints;
    private final DataQualityIssueRepository dataQualityIssues;
    private final PropertyNightProjectionService propertyNightProjection;

    @Autowired
    public BookingEventIngestionService(
            ObjectMapper objectMapper,
            DimPropertyRepository properties,
            DimCustomerMaskedRepository customers,
            DimDateRepository dates,
            FactBookingRepository bookings,
            ProcessedEventRepository processedEvents,
            IngestedEventRepository ingestedEvents,
            IngestionCheckpointRepository checkpoints,
            DataQualityIssueRepository dataQualityIssues,
            PropertyNightProjectionService propertyNightProjection) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.customers = customers;
        this.dates = dates;
        this.bookings = bookings;
        this.processedEvents = processedEvents;
        this.ingestedEvents = ingestedEvents;
        this.checkpoints = checkpoints;
        this.dataQualityIssues = dataQualityIssues;
        this.propertyNightProjection = propertyNightProjection;
    }

    // Convenience constructor for unit testing
    public BookingEventIngestionService(
            ObjectMapper objectMapper,
            DimPropertyRepository properties,
            DimCustomerMaskedRepository customers,
            DimDateRepository dates,
            FactBookingRepository bookings,
            ProcessedEventRepository processedEvents) {
        this(objectMapper, properties, customers, dates, bookings, processedEvents, null, null, null, null);
    }

    @KafkaListener(topics = "${vistay.topics.booking-events:vistay.booking.events}", groupId = "${vistay.kafka.consumers.booking.group-id:warehouse-booking-consumer}")
    @Transactional
    public void onBookingEvent(String rawMessage) {
        String payloadSha256 = EventSecurityUtil.computeSha256(rawMessage);
        String sanitizedPayload = EventSecurityUtil.sanitizePayload(rawMessage, objectMapper);

        BookingEventPayload event;
        try {
            event = objectMapper.readValue(rawMessage, BookingEventPayload.class);
        } catch (Exception e) {
            log.warn("Malformed booking event message: {}", e.getMessage());
            recordDataQualityIssue(null, "MALFORMED_JSON", "CRITICAL",
                    "Malformed JSON message: " + e.getMessage());
            return;
        }

        if (event == null) {
            return;
        }

        UUID eventId = event.eventId();
        if (isIncomplete(event)) {
            log.warn("Skipping booking event with missing mandatory fields: eventId={}, bookingId={}",
                    eventId, event.bookingId());
            recordDataQualityIssue(event.bookingId() != null ? event.bookingId().toString() : null,
                    "MISSING_MANDATORY_FIELDS", "ERROR",
                    "Missing mandatory attributes for booking event: eventId=" + eventId);
            if (eventId != null) {
                recordIngestedEvent(eventId, event, sanitizedPayload, payloadSha256,
                        "QUARANTINED", "Missing mandatory fields");
            }
            return;
        }

        if (hasInvalidGuestCounts(event)) {
            log.warn("Skipping booking event with invalid guest counts: eventId={}, adults={}, children={}",
                    eventId, event.adults(), event.children());
            recordDataQualityIssue(event.bookingId().toString(), "INVALID_GUEST_COUNTS", "ERROR",
                    "Invalid guest counts: adults=" + event.adults() + ", children=" + event.children());
            recordIngestedEvent(eventId, event, sanitizedPayload, payloadSha256,
                    "QUARANTINED", "Invalid guest counts");
            return;
        }

        // Integrity check: if duplicate eventId exists, payload hash must match
        if (ingestedEvents != null) {
            Optional<IngestedEvent> existingIngested = ingestedEvents.findById(eventId);
            if (existingIngested.isPresent()) {
                if (!payloadSha256.equals(existingIngested.get().getPayloadSha256())) {
                    log.error("Integrity conflict: eventId={} already exists with different payload hash!", eventId);
                    recordDataQualityIssue(event.bookingId().toString(), "INTEGRITY_CONFLICT", "CRITICAL",
                            "Event ID duplicate with conflicting payload hash: " + eventId);
                    IngestedEvent quarantined = existingIngested.get();
                    quarantined.setProcessingStatus("QUARANTINED");
                    quarantined.setProcessingError("Duplicate event ID with conflicting payload SHA256");
                    ingestedEvents.save(quarantined);
                    return;
                }
            }
        }

        // Idempotency check: event already processed by this consumer
        if (processedEvents.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.debug("Booking event already processed, skipping: eventId={}", eventId);
            return;
        }

        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        Instant warehouseUpdatedAt = Instant.now();

        // Stale event check: do not overwrite newer fact state
        Optional<FactBooking> existingFact = bookings.findById(event.bookingId());
        if (existingFact.isPresent()) {
            Instant existingSourceUpdated = existingFact.get().getSourceUpdatedAt();
            if (existingSourceUpdated != null && occurredAt.isBefore(existingSourceUpdated)) {
                log.warn("Stale booking event received: incoming occurredAt={}, existing sourceUpdatedAt={}",
                        occurredAt, existingSourceUpdated);
                recordDataQualityIssue(event.bookingId().toString(), "STALE_EVENT_OUT_OF_ORDER", "WARNING",
                        "Stale event ignored: incoming occurredAt " + occurredAt + " is before " + existingSourceUpdated);
                recordIngestedEvent(eventId, event, sanitizedPayload, payloadSha256, "PROJECTED", null);
                processedEvents.save(new ProcessedEvent(eventId, CONSUMER_NAME, SOURCE_SERVICE, eventTypeOf(event), 1));
                updateCheckpoint(eventId, occurredAt);
                return;
            }
        }

        DimProperty property = upsertProperty(event, occurredAt);
        DimCustomerMasked customer = upsertCustomer(event, occurredAt);
        DimDate bookingDate = upsertDate(event.bookingDate());
        DimDate checkInDate = event.checkInDate() != null ? upsertDate(event.checkInDate()) : null;
        DimDate checkOutDate = event.checkOutDate() != null ? upsertDate(event.checkOutDate()) : null;

        FactBooking fact = existingFact.orElseGet(() -> new FactBooking(event.bookingId()));
        fact.setBookingCode(event.bookingCode() != null ? event.bookingCode() : event.bookingId().toString());
        fact.setProperty(property);
        fact.setCustomer(customer);
        fact.setBookingDate(bookingDate);
        fact.setCheckInDate(checkInDate);
        fact.setCheckOutDate(checkOutDate);
        fact.setStayType(event.stayType() != null ? event.stayType() : "NIGHTLY");
        fact.setStatus(event.status() != null ? event.status() : "UNKNOWN");
        fact.setDurationValue(durationValue(event));
        fact.setPriceUnit(priceUnit(event.stayType()));
        fact.setAdults(event.adults().shortValue());
        fact.setChildren(event.children() != null ? event.children().shortValue() : (short) 0);
        fact.setSubtotalAmount(zeroIfNull(event.subtotalAmount()));
        fact.setDiscountAmount(zeroIfNull(event.discountAmount()));
        fact.setFeeAmount(zeroIfNull(event.feeAmount()));
        fact.setTaxAmount(zeroIfNull(event.taxAmount()));
        fact.setBookingValue(zeroIfNull(event.bookingValue()));
        fact.setCurrency(event.currency() != null ? event.currency() : "VND");
        fact.setSourceEventId(eventId);
        fact.setSourceUpdatedAt(occurredAt);
        fact.setWarehouseUpdatedAt(warehouseUpdatedAt);
        bookings.save(fact);

        // Project property-nights occupancy
        if (propertyNightProjection != null && event.checkInDate() != null && event.checkOutDate() != null) {
            propertyNightProjection.projectBookingNights(
                    event.propertyId(),
                    property,
                    event.bookingId(),
                    event.checkInDate(),
                    event.checkOutDate(),
                    event.status(),
                    fact.getBookingValue(),
                    fact.getCurrency(),
                    eventId
            );
        }

        recordIngestedEvent(eventId, event, sanitizedPayload, payloadSha256, "PROJECTED", null);
        processedEvents.save(new ProcessedEvent(
                eventId, CONSUMER_NAME, SOURCE_SERVICE, eventTypeOf(event), 1));
        updateCheckpoint(eventId, occurredAt);

        log.info("Ingested booking event: eventId={}, bookingId={}, property={}",
                eventId, event.bookingId(), event.propertyId());
    }

    private void recordIngestedEvent(
            UUID eventId,
            BookingEventPayload event,
            String sanitizedPayload,
            String payloadSha256,
            String status,
            String error) {
        if (ingestedEvents == null || eventId == null) {
            return;
        }
        IngestedEvent entity = ingestedEvents.findById(eventId).orElseGet(() -> new IngestedEvent(eventId));
        entity.setEventType(eventTypeOf(event));
        entity.setEventVersion(1);
        entity.setSourceService(SOURCE_SERVICE);
        entity.setAggregateType("Booking");
        entity.setAggregateId(event != null ? event.bookingId() : null);
        entity.setOccurredAt(event != null && event.occurredAt() != null ? event.occurredAt() : Instant.now());
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
                SOURCE_SERVICE, "Booking", recordId, ruleCode, severity, description);
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

    private boolean isIncomplete(BookingEventPayload event) {
        return event.eventId() == null || event.bookingId() == null
                || event.propertyId() == null || event.bookingDate() == null;
    }

    private boolean hasInvalidGuestCounts(BookingEventPayload event) {
        return event.adults() == null || event.adults() <= 0
                || (event.children() != null && event.children() < 0);
    }

    private DimProperty upsertProperty(BookingEventPayload event, Instant occurredAt) {
        DimProperty current = properties.findByPropertyIdAndCurrentRowTrue(event.propertyId()).orElse(null);
        if (current == null) {
            DimProperty created = new DimProperty(event.propertyId(), event.propertyName(),
                    occurredAt, event.eventId(), occurredAt);
            return properties.save(created);
        }
        if (event.propertyName() != null && !event.propertyName().equals(current.getPropertyName())) {
            current.supersede(occurredAt);
            properties.save(current);
            DimProperty nextVersion = new DimProperty(event.propertyId(), event.propertyName(),
                    occurredAt, event.eventId(), occurredAt);
            return properties.save(nextVersion);
        }
        return current;
    }

    private DimCustomerMasked upsertCustomer(BookingEventPayload event, Instant occurredAt) {
        if (event.customerId() == null) {
            return null;
        }
        return customers.findByCustomerId(event.customerId())
                .orElseGet(() -> customers.save(new DimCustomerMasked(event.customerId())));
    }

    private DimDate upsertDate(java.time.LocalDate calendarDate) {
        int key = DimDate.dateKey(calendarDate);
        return dates.findById(key).orElseGet(() -> dates.save(DimDate.of(calendarDate)));
    }

    private BigDecimal durationValue(BookingEventPayload event) {
        if (event.checkInDate() == null || event.checkOutDate() == null) {
            return BigDecimal.ONE;
        }
        long nights = ChronoUnit.DAYS.between(event.checkInDate(), event.checkOutDate());
        return nights > 0 ? BigDecimal.valueOf(nights) : BigDecimal.ONE;
    }

    private String priceUnit(String stayType) {
        if (stayType == null) {
            return "NIGHT";
        }
        return switch (stayType) {
            case "HOURLY" -> "HOUR";
            case "MONTHLY" -> "MONTH";
            default -> "NIGHT";
        };
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String eventTypeOf(BookingEventPayload event) {
        return event != null && event.eventType() != null ? event.eventType() : DEFAULT_EVENT_TYPE;
    }
}
