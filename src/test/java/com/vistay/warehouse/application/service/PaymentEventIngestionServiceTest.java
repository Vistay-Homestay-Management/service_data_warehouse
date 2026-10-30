package com.vistay.warehouse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vistay.warehouse.domain.model.DataQualityIssue;
import com.vistay.warehouse.domain.model.DimDate;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class PaymentEventIngestionServiceTest {

    private JsonMapper objectMapper;
    private FactFinancialTransactionRepository transactions;
    private FactBookingRepository bookings;
    private DimDateRepository dates;
    private ProcessedEventRepository processedEvents;
    private IngestedEventRepository ingestedEvents;
    private IngestionCheckpointRepository checkpoints;
    private DataQualityIssueRepository dataQualityIssues;
    private PaymentEventIngestionService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        transactions = mock(FactFinancialTransactionRepository.class);
        bookings = mock(FactBookingRepository.class);
        dates = mock(DimDateRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        ingestedEvents = mock(IngestedEventRepository.class);
        checkpoints = mock(IngestionCheckpointRepository.class);
        dataQualityIssues = mock(DataQualityIssueRepository.class);

        lenient().when(transactions.save(any(FactFinancialTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactions.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(dates.findById(any())).thenReturn(Optional.empty());
        lenient().when(dates.save(any(DimDate.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(processedEvents.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
        lenient().when(processedEvents.save(any(ProcessedEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ingestedEvents.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(ingestedEvents.save(any(IngestedEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(checkpoints.findBySourceNameAndPartitionKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(checkpoints.save(any(IngestionCheckpoint.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dataQualityIssues.save(any(DataQualityIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bookings.findById(any(UUID.class))).thenReturn(Optional.empty());

        service = new PaymentEventIngestionService(
                objectMapper, transactions, bookings, dates,
                processedEvents, ingestedEvents, checkpoints, dataQualityIssues);
    }

    private String validPaymentMessage(UUID eventId, UUID providerTxId, UUID paymentIntentId, BigDecimal amount) {
        return """
                {
                  "eventId": "%s",
                  "type": "vistay.payment.transaction-confirmed",
                  "version": 1,
                  "aggregateId": "%s",
                  "aggregateVersion": 1,
                  "correlationId": "corr-101",
                  "occurredAt": "2026-01-10T14:30:00Z",
                  "data": {
                    "providerTransactionId": "%s",
                    "paymentIntentId": "%s",
                    "financePaymentRequestId": "%s",
                    "referenceNumber": "FT2601001",
                    "amount": "%s",
                    "currency": "VND",
                    "matchStatus": "MATCHED",
                    "mismatchReason": null,
                    "occurredAt": "2026-01-10T14:30:00Z"
                  }
                }
                """.formatted(eventId, paymentIntentId, providerTxId, paymentIntentId, UUID.randomUUID(), amount.toPlainString());
    }

    @Test
    void ingestsMatchedPaymentEventSuccessfully() {
        UUID eventId = UUID.randomUUID();
        UUID providerTxId = UUID.randomUUID();
        UUID paymentIntentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1500000.00");

        service.onPaymentConfirmed(validPaymentMessage(eventId, providerTxId, paymentIntentId, amount));

        ArgumentCaptor<FactFinancialTransaction> captor = ArgumentCaptor.forClass(FactFinancialTransaction.class);
        verify(transactions).save(captor.capture());
        FactFinancialTransaction fact = captor.getValue();

        assertThat(fact.getTransactionId()).isEqualTo(providerTxId);
        assertThat(fact.getBookingId()).isEqualTo(paymentIntentId);
        assertThat(fact.getTransactionType()).isEqualTo("PAYMENT");
        assertThat(fact.getPaymentMethod()).isEqualTo("VIETQR");
        assertThat(fact.getProvider()).isEqualTo("NAPAS247");
        assertThat(fact.getStatus()).isEqualTo("CONFIRMED");
        assertThat(fact.getAmount()).isEqualByComparingTo(amount);
        assertThat(fact.getCurrency()).isEqualTo("VND");
        assertThat(fact.getSourceEventId()).isEqualTo(eventId);
        assertThat(fact.getTransactionDate()).isNotNull();

        verify(processedEvents).save(any(ProcessedEvent.class));
        verify(ingestedEvents).save(any(IngestedEvent.class));
        verify(checkpoints).save(any(IngestionCheckpoint.class));
    }

    @Test
    void skipsDuplicatePaymentEventIdForIdempotency() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.existsByEventIdAndConsumerName(eventId, PaymentEventIngestionService.CONSUMER_NAME))
                .thenReturn(true);

        service.onPaymentConfirmed(validPaymentMessage(eventId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000")));

        verifyNoInteractions(transactions);
        verify(processedEvents, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void quarantinesNonMatchedTransactionWithoutCreatingPaymentFact() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "type": "vistay.payment.transaction-confirmed",
                  "version": 1,
                  "data": {
                    "providerTransactionId": "%s",
                    "amount": "500000",
                    "currency": "VND",
                    "matchStatus": "UNMATCHED",
                    "mismatchReason": "Amount mismatch"
                  }
                }
                """.formatted(eventId, UUID.randomUUID());

        service.onPaymentConfirmed(json);

        verifyNoInteractions(transactions);
        verify(ingestedEvents).save(any(IngestedEvent.class));
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void quarantinesUnsupportedEventTypeOrVersion() {
        String json = """
                {
                  "eventId": "%s",
                  "type": "unknown.event.type",
                  "version": 2,
                  "data": {
                    "providerTransactionId": "%s",
                    "amount": "100000"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        service.onPaymentConfirmed(json);

        verifyNoInteractions(transactions);
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verify(ingestedEvents).save(any(IngestedEvent.class));
    }

    @Test
    void quarantinesEventWithNonPositiveAmount() {
        UUID eventId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        service.onPaymentConfirmed(validPaymentMessage(eventId, txId, UUID.randomUUID(), new BigDecimal("-50000")));

        verifyNoInteractions(transactions);
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
    }

    @Test
    void detectsIntegrityConflictWhenSamePaymentEventIdHasDifferentHash() {
        UUID eventId = UUID.randomUUID();
        IngestedEvent existing = new IngestedEvent(eventId);
        existing.setPayloadSha256("diff-hash-11111111111111111111111111111111111111111111111111111111");
        when(ingestedEvents.findById(eventId)).thenReturn(Optional.of(existing));

        service.onPaymentConfirmed(validPaymentMessage(eventId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000")));

        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verifyNoInteractions(transactions);
        assertThat(existing.getProcessingStatus()).isEqualTo("QUARANTINED");
    }

    @Test
    void ignoresStalePaymentEventWhenExistingTransactionHasNewerTimestamp() {
        UUID eventId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        FactFinancialTransaction existing = new FactFinancialTransaction(txId);
        existing.setSourceUpdatedAt(Instant.parse("2026-01-10T15:00:00Z")); // newer than 14:30:00Z
        existing.setStatus("CONFIRMED");
        when(transactions.findById(txId)).thenReturn(Optional.of(existing));

        service.onPaymentConfirmed(validPaymentMessage(eventId, txId, UUID.randomUUID(), new BigDecimal("100000")));

        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void handlesMalformedJsonWithoutCrashing() {
        assertThatCode(() -> service.onPaymentConfirmed("malformed json")).doesNotThrowAnyException();
        verify(dataQualityIssues).save(any(DataQualityIssue.class));
        verifyNoInteractions(transactions);
    }
}

