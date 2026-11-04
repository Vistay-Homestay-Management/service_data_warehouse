package com.vistay.warehouse.application.service;

import com.vistay.warehouse.application.dto.BookingEventPayload;
import com.vistay.warehouse.domain.model.DimCustomerMasked;
import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.DimProperty;
import com.vistay.warehouse.domain.model.FactBooking;
import com.vistay.warehouse.domain.model.ProcessedEvent;
import com.vistay.warehouse.domain.repository.DimCustomerMaskedRepository;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import com.vistay.warehouse.domain.repository.DimPropertyRepository;
import com.vistay.warehouse.domain.repository.FactBookingRepository;
import com.vistay.warehouse.domain.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public BookingEventIngestionService(
            ObjectMapper objectMapper,
            DimPropertyRepository properties,
            DimCustomerMaskedRepository customers,
            DimDateRepository dates,
            FactBookingRepository bookings,
            ProcessedEventRepository processedEvents) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.customers = customers;
        this.dates = dates;
        this.bookings = bookings;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(topics = "vistay.booking.events", groupId = "warehouse-booking-consumer")
    @Transactional
    public void onBookingEvent(String rawMessage) {
        BookingEventPayload event = parse(rawMessage);
        if (event == null) {
            return;
        }
        if (isIncomplete(event)) {
            log.warn("Skipping booking event with missing mandatory fields: eventId={}, bookingId={}",
                    event.eventId(), event.bookingId());
            return;
        }
        if (hasInvalidGuestCounts(event)) {
            log.warn("Skipping booking event with invalid guest counts: eventId={}, adults={}, children={}",
                    event.eventId(), event.adults(), event.children());
            return;
        }
        if (processedEvents.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
            log.debug("Booking event already processed, skipping: eventId={}", event.eventId());
            return;
        }

        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        Instant warehouseUpdatedAt = Instant.now();

        DimProperty property = upsertProperty(event, occurredAt);
        DimCustomerMasked customer = upsertCustomer(event, occurredAt);
        DimDate bookingDate = upsertDate(event.bookingDate());
        DimDate checkInDate = event.checkInDate() != null ? upsertDate(event.checkInDate()) : null;
        DimDate checkOutDate = event.checkOutDate() != null ? upsertDate(event.checkOutDate()) : null;

        FactBooking fact = bookings.findById(event.bookingId())
                .orElseGet(() -> new FactBooking(event.bookingId()));
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
        fact.setSourceEventId(event.eventId());
        fact.setSourceUpdatedAt(occurredAt);
        fact.setWarehouseUpdatedAt(warehouseUpdatedAt);
        bookings.save(fact);

        processedEvents.save(new ProcessedEvent(
                event.eventId(), CONSUMER_NAME, SOURCE_SERVICE, eventTypeOf(event), 1));
        log.info("Ingested booking event: eventId={}, bookingId={}, property={}",
                event.eventId(), event.bookingId(), event.propertyId());
    }

    private BookingEventPayload parse(String rawMessage) {
        try {
            return objectMapper.readValue(rawMessage, BookingEventPayload.class);
        } catch (RuntimeException e) {
            log.warn("Skipping malformed booking event message", e);
            return null;
        }
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
        return event.eventType() != null ? event.eventType() : DEFAULT_EVENT_TYPE;
    }
}
