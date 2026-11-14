package com.vistay.warehouse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vistay.warehouse.application.dto.RevenueRecognitionEventPayload;
import com.vistay.warehouse.domain.model.DataQualityIssue;
import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.DimProperty;
import com.vistay.warehouse.domain.model.FactRevenueRecognition;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class RevenueRecognitionProjectionServiceTest {

    private JsonMapper objectMapper;
    private FactRevenueRecognitionRepository revenues;
    private DimPropertyRepository properties;
    private DimDateRepository dates;
    private ProcessedEventRepository processedEvents;
    private IngestedEventRepository ingestedEvents;
    private IngestionCheckpointRepository checkpoints;
    private DataQualityIssueRepository dataQualityIssues;
    private RevenueRecognitionProjectionService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        revenues = mock(FactRevenueRecognitionRepository.class);
        properties = mock(DimPropertyRepository.class);
        dates = mock(DimDateRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        ingestedEvents = mock(IngestedEventRepository.class);
        checkpoints = mock(IngestionCheckpointRepository.class);
        dataQualityIssues = mock(DataQualityIssueRepository.class);

        lenient().when(revenues.save(any(FactRevenueRecognition.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(revenues.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(dates.findById(any())).thenReturn(Optional.empty());
        lenient().when(dates.save(any(DimDate.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(processedEvents.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        lenient().when(checkpoints.findBySourceNameAndPartitionKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(dataQualityIssues.save(any(DataQualityIssue.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new RevenueRecognitionProjectionService(
                objectMapper, revenues, properties, dates,
                processedEvents, ingestedEvents, checkpoints, dataQualityIssues);
    }

    @Test
    void projectsRevenueRecognitionFactSuccessfully() {
        UUID recId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        LocalDate recDate = LocalDate.of(2026, 1, 12);
        BigDecimal amount = new BigDecimal("2000000.00");
        DimProperty dimProp = new DimProperty(propertyId, "Villa B", Instant.now(), UUID.randomUUID(), Instant.now());
        when(properties.findByPropertyIdAndCurrentRowTrue(propertyId)).thenReturn(Optional.of(dimProp));

        RevenueRecognitionEventPayload payload = new RevenueRecognitionEventPayload(
                recId, bookingId, propertyId, recDate, "CHECKOUT_EARNED",
                amount, "VND", UUID.randomUUID(), Instant.now());

        FactRevenueRecognition result = service.projectRevenue(payload);

        assertThat(result).isNotNull();
        ArgumentCaptor<FactRevenueRecognition> captor = ArgumentCaptor.forClass(FactRevenueRecognition.class);
        verify(revenues).save(captor.capture());
        FactRevenueRecognition fact = captor.getValue();
        assertThat(fact.getRecognitionId()).isEqualTo(recId);
        assertThat(fact.getBookingId()).isEqualTo(bookingId);
        assertThat(fact.getPolicyCode()).isEqualTo("CHECKOUT_EARNED");
        assertThat(fact.getRecognizedAmount()).isEqualByComparingTo(amount);
        assertThat(fact.getCurrency()).isEqualTo("VND");
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void skipsDuplicateRevenueEventIdForIdempotency() {
        UUID sourceEventId = UUID.randomUUID();
        UUID recId = UUID.randomUUID();
        when(processedEvents.existsByEventIdAndConsumerName(sourceEventId, RevenueRecognitionProjectionService.CONSUMER_NAME))
                .thenReturn(true);
        FactRevenueRecognition existing = new FactRevenueRecognition(recId, UUID.randomUUID());
        when(revenues.findById(recId)).thenReturn(Optional.of(existing));

        RevenueRecognitionEventPayload payload = new RevenueRecognitionEventPayload(
                recId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 10),
                "CHECKOUT", BigDecimal.TEN, "VND", sourceEventId, Instant.now());

        FactRevenueRecognition result = service.projectRevenue(payload);

        assertThat(result).isSameAs(existing);
        verify(revenues, never()).save(any(FactRevenueRecognition.class));
    }

    @Test
    void rejectsNegativeRevenueAmount() {
        RevenueRecognitionEventPayload payload = new RevenueRecognitionEventPayload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 10),
                "CHECKOUT", new BigDecimal("-50000"), "VND", UUID.randomUUID(), Instant.now());

        FactRevenueRecognition result = service.projectRevenue(payload);

        assertThat(result).isNull();
        verifyNoInteractions(revenues);
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
    }

    @Test
    void rejectsMissingMandatoryFields() {
        RevenueRecognitionEventPayload payload = new RevenueRecognitionEventPayload(
                null, UUID.randomUUID(), null, null, null, null, "VND", null, null);

        FactRevenueRecognition result = service.projectRevenue(payload);

        assertThat(result).isNull();
        verifyNoInteractions(revenues);
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
    }
}

