package com.vistay.warehouse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ExpenseProjectionServiceTest {

    private JsonMapper objectMapper;
    private FactExpenseRepository expenses;
    private DimPropertyRepository properties;
    private DimDateRepository dates;
    private ProcessedEventRepository processedEvents;
    private IngestedEventRepository ingestedEvents;
    private IngestionCheckpointRepository checkpoints;
    private DataQualityIssueRepository dataQualityIssues;
    private ExpenseProjectionService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        expenses = mock(FactExpenseRepository.class);
        properties = mock(DimPropertyRepository.class);
        dates = mock(DimDateRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        ingestedEvents = mock(IngestedEventRepository.class);
        checkpoints = mock(IngestionCheckpointRepository.class);
        dataQualityIssues = mock(DataQualityIssueRepository.class);

        lenient().when(expenses.save(any(FactExpense.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(expenses.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(dates.findById(any())).thenReturn(Optional.empty());
        lenient().when(dates.save(any(DimDate.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(processedEvents.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        lenient().when(checkpoints.findBySourceNameAndPartitionKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(dataQualityIssues.save(any(DataQualityIssue.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ExpenseProjectionService(
                objectMapper, expenses, properties, dates,
                processedEvents, ingestedEvents, checkpoints, dataQualityIssues);
    }

    @Test
    void projectsApprovedExpenseFactSuccessfully() {
        UUID expenseId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        LocalDate incurred = LocalDate.of(2026, 2, 5);
        BigDecimal amount = new BigDecimal("450000.00");
        DimProperty dimProp = new DimProperty(propertyId, "Villa C", Instant.now(), UUID.randomUUID(), Instant.now());
        when(properties.findByPropertyIdAndCurrentRowTrue(propertyId)).thenReturn(Optional.of(dimProp));

        ExpenseEventPayload payload = new ExpenseEventPayload(
                expenseId, propertyId, workOrderId, incurred,
                "MAINTENANCE", "APPROVED", amount, "VND", UUID.randomUUID(), Instant.now());

        FactExpense result = service.projectExpense(payload);

        assertThat(result).isNotNull();
        ArgumentCaptor<FactExpense> captor = ArgumentCaptor.forClass(FactExpense.class);
        verify(expenses).save(captor.capture());
        FactExpense fact = captor.getValue();
        assertThat(fact.getExpenseId()).isEqualTo(expenseId);
        assertThat(fact.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(fact.getExpenseCategory()).isEqualTo("MAINTENANCE");
        assertThat(fact.getStatus()).isEqualTo("APPROVED");
        assertThat(fact.getAmount()).isEqualByComparingTo(amount);
        assertThat(fact.getCurrency()).isEqualTo("VND");
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void ignoresNonAuthoritativeStatusDraftOrEstimated() {
        ExpenseEventPayload payload = new ExpenseEventPayload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 2, 5),
                "CLEANING", "ESTIMATED", new BigDecimal("200000"), "VND", UUID.randomUUID(), Instant.now());

        FactExpense result = service.projectExpense(payload);

        assertThat(result).isNull();
        verifyNoInteractions(expenses);
    }

    @Test
    void rejectsNonPositiveAmount() {
        ExpenseEventPayload payload = new ExpenseEventPayload(
                UUID.randomUUID(), UUID.randomUUID(), null, LocalDate.of(2026, 2, 5),
                "UTILITIES", "APPROVED", new BigDecimal("-100000"), "VND", UUID.randomUUID(), Instant.now());

        FactExpense result = service.projectExpense(payload);

        assertThat(result).isNull();
        verifyNoInteractions(expenses);
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
    }

    @Test
    void skipsDuplicateExpenseEventForIdempotency() {
        UUID sourceEventId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        when(processedEvents.existsByEventIdAndConsumerName(sourceEventId, ExpenseProjectionService.CONSUMER_NAME))
                .thenReturn(true);
        FactExpense existing = new FactExpense(expenseId);
        when(expenses.findById(expenseId)).thenReturn(Optional.of(existing));

        ExpenseEventPayload payload = new ExpenseEventPayload(
                expenseId, UUID.randomUUID(), null, LocalDate.of(2026, 2, 5),
                "SUPPLIES", "POSTED", new BigDecimal("50000"), "VND", sourceEventId, Instant.now());

        FactExpense result = service.projectExpense(payload);

        assertThat(result).isSameAs(existing);
        verify(expenses, never()).save(any(FactExpense.class));
    }

    @Test
    void ignoresStaleExpenseEventWhenExistingExpenseHasNewerTimestamp() {
        UUID expenseId = UUID.randomUUID();
        FactExpense existing = new FactExpense(expenseId);
        existing.setSourceUpdatedAt(Instant.parse("2026-02-05T12:00:00Z"));
        existing.setStatus("POSTED");
        when(expenses.findById(expenseId)).thenReturn(Optional.of(existing));

        ExpenseEventPayload payload = new ExpenseEventPayload(
                expenseId, UUID.randomUUID(), null, LocalDate.of(2026, 2, 5),
                "SUPPLIES", "APPROVED", new BigDecimal("50000"), "VND", UUID.randomUUID(),
                Instant.parse("2026-02-05T10:00:00Z")); // older

        FactExpense result = service.projectExpense(payload);

        assertThat(result.getStatus()).isEqualTo("POSTED"); // untouched
        verify(expenses, never()).save(any(FactExpense.class));
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
    }
}

