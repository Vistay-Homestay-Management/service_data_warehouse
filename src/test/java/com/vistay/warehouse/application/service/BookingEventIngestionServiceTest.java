package com.vistay.warehouse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vistay.warehouse.application.dto.BookingEventPayload;
import com.vistay.warehouse.domain.model.DataQualityIssue;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class BookingEventIngestionServiceTest {

    private JsonMapper objectMapper;
    private DimPropertyRepository properties;
    private DimCustomerMaskedRepository customers;
    private DimDateRepository dates;
    private FactBookingRepository bookings;
    private ProcessedEventRepository processedEvents;
    private IngestedEventRepository ingestedEvents;
    private IngestionCheckpointRepository checkpoints;
    private DataQualityIssueRepository dataQualityIssues;
    private PropertyNightProjectionService propertyNightProjection;
    private BookingEventIngestionService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        properties = mock(DimPropertyRepository.class);
        customers = mock(DimCustomerMaskedRepository.class);
        dates = mock(DimDateRepository.class);
        bookings = mock(FactBookingRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        ingestedEvents = mock(IngestedEventRepository.class);
        checkpoints = mock(IngestionCheckpointRepository.class);
        dataQualityIssues = mock(DataQualityIssueRepository.class);
        propertyNightProjection = mock(PropertyNightProjectionService.class);

        lenient().when(properties.save(any(DimProperty.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(customers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dates.save(any(DimDate.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dates.findById(any())).thenAnswer(inv -> Optional.empty());
        lenient().when(bookings.save(any(FactBooking.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bookings.findById(any(UUID.class))).thenAnswer(inv -> Optional.empty());
        lenient().when(processedEvents.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        lenient().when(processedEvents.save(any(ProcessedEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ingestedEvents.save(any(IngestedEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ingestedEvents.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(checkpoints.findBySourceNameAndPartitionKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(checkpoints.save(any(IngestionCheckpoint.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dataQualityIssues.save(any(DataQualityIssue.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new BookingEventIngestionService(
                objectMapper, properties, customers, dates, bookings, processedEvents,
                ingestedEvents, checkpoints, dataQualityIssues, propertyNightProjection);
    }

    private BookingEventPayload validPayload() {
        return new BookingEventPayload(
                UUID.randomUUID(),
                "booking.created",
                UUID.randomUUID(),
                "BK-1001",
                UUID.randomUUID(),
                "Vistay Riverside Villa",
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12),
                "NIGHTLY",
                "CONFIRMED",
                new BigDecimal("2000000"),
                BigDecimal.ZERO,
                new BigDecimal("50000"),
                new BigDecimal("100000"),
                new BigDecimal("2250000"),
                "VND",
                2,
                1,
                Instant.parse("2026-01-05T08:30:00Z"));
    }

    private String toJson(BookingEventPayload payload) {
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void ingestsValidBookingEventIntoFactBookingAndProjectsNights() {
        BookingEventPayload payload = validPayload();

        service.onBookingEvent(toJson(payload));

        ArgumentCaptor<FactBooking> captor = ArgumentCaptor.forClass(FactBooking.class);
        verify(bookings).save(captor.capture());
        FactBooking fact = captor.getValue();
        assertThat(fact.getBookingId()).isEqualTo(payload.bookingId());
        assertThat(fact.getBookingCode()).isEqualTo("BK-1001");
        assertThat(fact.getStayType()).isEqualTo("NIGHTLY");
        assertThat(fact.getPriceUnit()).isEqualTo("NIGHT");
        assertThat(fact.getDurationValue()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(fact.getProperty().getPropertyId()).isEqualTo(payload.propertyId());
        assertThat(fact.getAdults()).isEqualTo((short) 2);
        assertThat(fact.getChildren()).isEqualTo((short) 1);
        assertThat(fact.getCurrency()).isEqualTo("VND");
        assertThat(fact.getSourceEventId()).isEqualTo(payload.eventId());

        verify(propertyNightProjection).projectBookingNights(
                eq(payload.propertyId()),
                any(),
                eq(payload.bookingId()),
                eq(payload.checkInDate()),
                eq(payload.checkOutDate()),
                eq("CONFIRMED"),
                eq(new BigDecimal("2250000")),
                eq("VND"),
                eq(payload.eventId())
        );

        verify(processedEvents).save(any(ProcessedEvent.class));
        verify(ingestedEvents).save(any(IngestedEvent.class));
        verify(checkpoints).save(any(IngestionCheckpoint.class));
    }

    @Test
    void skipsDuplicateEventIdForIdempotency() {
        BookingEventPayload payload = validPayload();
        when(processedEvents.existsByEventIdAndConsumerName(payload.eventId(), BookingEventIngestionService.CONSUMER_NAME))
                .thenReturn(true);

        service.onBookingEvent(toJson(payload));

        verify(bookings, never()).save(any(FactBooking.class));
        verify(processedEvents, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void detectsIntegrityConflictWhenSameEventIdHasDifferentPayloadHash() {
        BookingEventPayload payload = validPayload();
        IngestedEvent existing = new IngestedEvent(payload.eventId());
        existing.setPayloadSha256("0000000000000000000000000000000000000000000000000000000000000000");
        when(ingestedEvents.findById(payload.eventId())).thenReturn(Optional.of(existing));

        service.onBookingEvent(toJson(payload));

        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verify(bookings, never()).save(any(FactBooking.class));
        verify(processedEvents, never()).save(any(ProcessedEvent.class));
        assertThat(existing.getProcessingStatus()).isEqualTo("QUARANTINED");
    }

    @Test
    void ignoresStaleBookingEventWhenExistingFactHasNewerTimestamp() {
        BookingEventPayload payload = validPayload();
        FactBooking existingFact = new FactBooking(payload.bookingId());
        existingFact.setSourceUpdatedAt(Instant.parse("2026-01-05T09:00:00Z")); // newer than payload (08:30:00Z)
        existingFact.setBookingCode("BK-EXISTING-NEWER");
        when(bookings.findById(payload.bookingId())).thenReturn(Optional.of(existingFact));

        service.onBookingEvent(toJson(payload));

        // New state must NOT overwrite existing fact
        assertThat(existingFact.getBookingCode()).isEqualTo("BK-EXISTING-NEWER");
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void recordsDataQualityIssueAndQuarantinesWhenMandatoryFieldsMissing() {
        BookingEventPayload payload = new BookingEventPayload(
                UUID.randomUUID(), "booking.created", UUID.randomUUID(), "BK-1002",
                null, null, UUID.randomUUID(), LocalDate.of(2026, 1, 5),
                null, null, "NIGHTLY", "CONFIRMED",
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, "VND", 1, 0, Instant.now());

        service.onBookingEvent(toJson(payload));

        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verify(ingestedEvents).save(any(IngestedEvent.class));
        verifyNoInteractions(bookings);
        verify(processedEvents, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void recordsDataQualityIssueAndQuarantinesWhenGuestCountInvalid() {
        BookingEventPayload payload = new BookingEventPayload(
                UUID.randomUUID(), "booking.created", UUID.randomUUID(), "BK-1003",
                UUID.randomUUID(), "Prop", UUID.randomUUID(), LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 12), "NIGHTLY", "CONFIRMED",
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, "VND", 0, -1, Instant.now());

        service.onBookingEvent(toJson(payload));

        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verify(ingestedEvents).save(any(IngestedEvent.class));
        verifyNoInteractions(bookings);
    }

    @Test
    void recordsDataQualityIssueOnMalformedJsonWithoutCrashing() {
        assertThatCode(() -> service.onBookingEvent("this is not json")).doesNotThrowAnyException();

        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verifyNoInteractions(bookings, processedEvents);
    }
}
