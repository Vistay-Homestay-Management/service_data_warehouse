package com.vistay.warehouse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vistay.warehouse.domain.model.DataQualityIssue;
import com.vistay.warehouse.domain.model.FactBooking;
import com.vistay.warehouse.domain.model.FactFinancialTransaction;
import com.vistay.warehouse.domain.model.FactPropertyNight;
import com.vistay.warehouse.domain.model.FactRevenueRecognition;
import com.vistay.warehouse.domain.repository.DataQualityIssueRepository;
import com.vistay.warehouse.domain.repository.FactBookingRepository;
import com.vistay.warehouse.domain.repository.FactExpenseRepository;
import com.vistay.warehouse.domain.repository.FactFinancialTransactionRepository;
import com.vistay.warehouse.domain.repository.FactPropertyNightRepository;
import com.vistay.warehouse.domain.repository.FactRevenueRecognitionRepository;
import com.vistay.warehouse.domain.repository.IngestedEventRepository;
import com.vistay.warehouse.domain.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseReconciliationServiceTest {

    private FactBookingRepository bookings;
    private FactFinancialTransactionRepository transactions;
    private FactRevenueRecognitionRepository revenues;
    private FactExpenseRepository expenses;
    private FactPropertyNightRepository propertyNights;
    private IngestedEventRepository ingestedEvents;
    private ProcessedEventRepository processedEvents;
    private DataQualityIssueRepository dataQualityIssues;
    private WarehouseReconciliationService service;

    @BeforeEach
    void setUp() {
        bookings = mock(FactBookingRepository.class);
        transactions = mock(FactFinancialTransactionRepository.class);
        revenues = mock(FactRevenueRecognitionRepository.class);
        expenses = mock(FactExpenseRepository.class);
        propertyNights = mock(FactPropertyNightRepository.class);
        ingestedEvents = mock(IngestedEventRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        dataQualityIssues = mock(DataQualityIssueRepository.class);

        service = new WarehouseReconciliationService(
                bookings, transactions, revenues, expenses,
                propertyNights, ingestedEvents, processedEvents, dataQualityIssues);
    }

    @Test
    void reconcilesWarehouseFinancialMetricsSeparatingGBVCashAndRecognizedRevenue() {
        // 1. FactBooking (Gross Booking Value = 5,000,000)
        FactBooking b1 = new FactBooking(UUID.randomUUID());
        b1.setBookingValue(new BigDecimal("5000000.00"));
        when(bookings.findAll()).thenReturn(List.of(b1));

        // 2. FactFinancialTransaction (Confirmed Payment = 3,000,000; Confirmed Refund = 200,000)
        FactFinancialTransaction tx1 = new FactFinancialTransaction(UUID.randomUUID());
        tx1.setTransactionType("PAYMENT");
        tx1.setStatus("CONFIRMED");
        tx1.setAmount(new BigDecimal("3000000.00"));

        FactFinancialTransaction tx2 = new FactFinancialTransaction(UUID.randomUUID());
        tx2.setTransactionType("REFUND");
        tx2.setStatus("CONFIRMED");
        tx2.setAmount(new BigDecimal("200000.00"));

        when(transactions.findAll()).thenReturn(List.of(tx1, tx2));

        // 3. FactRevenueRecognition (Recognized Revenue = 2,500,000)
        FactRevenueRecognition r1 = new FactRevenueRecognition(UUID.randomUUID(), b1.getBookingId());
        r1.setRecognizedAmount(new BigDecimal("2500000.00"));
        when(revenues.findAll()).thenReturn(List.of(r1));

        // 4. Property Nights: 4 eligible, 3 occupied (75% occupancy)
        FactPropertyNight n1 = new FactPropertyNight(UUID.randomUUID(), LocalDate.of(2026, 1, 1));
        n1.setEligibleForSale(true);
        n1.setOccupied(true);

        FactPropertyNight n2 = new FactPropertyNight(UUID.randomUUID(), LocalDate.of(2026, 1, 2));
        n2.setEligibleForSale(true);
        n2.setOccupied(true);

        FactPropertyNight n3 = new FactPropertyNight(UUID.randomUUID(), LocalDate.of(2026, 1, 3));
        n3.setEligibleForSale(true);
        n3.setOccupied(true);

        FactPropertyNight n4 = new FactPropertyNight(UUID.randomUUID(), LocalDate.of(2026, 1, 4));
        n4.setEligibleForSale(true);
        n4.setOccupied(false);

        when(propertyNights.findAll()).thenReturn(List.of(n1, n2, n3, n4));

        // 5. Data Quality
        when(dataQualityIssues.findByStatus("OPEN")).thenReturn(List.of(
                new DataQualityIssue("service_booking", "Booking", "1", "TEST_ISSUE", "WARNING", "Desc")));
        when(processedEvents.count()).thenReturn(10L);
        when(ingestedEvents.count()).thenReturn(12L);

        Map<String, Object> report = service.reconcileWarehouseState();

        assertThat(report.get("totalBookings")).isEqualTo(1);
        assertThat(report.get("grossBookingValue")).isEqualTo(new BigDecimal("5000000.00"));
        assertThat(report.get("confirmedCashIn")).isEqualTo(new BigDecimal("3000000.00"));
        assertThat(report.get("confirmedRefunds")).isEqualTo(new BigDecimal("200000.00"));
        assertThat(report.get("netCashIn")).isEqualTo(new BigDecimal("2800000.00"));
        assertThat(report.get("recognizedRevenue")).isEqualTo(new BigDecimal("2500000.00"));

        // Crucial invariant: GBV != Net Cash In != Recognized Revenue
        BigDecimal gbv = (BigDecimal) report.get("grossBookingValue");
        BigDecimal netCash = (BigDecimal) report.get("netCashIn");
        BigDecimal rev = (BigDecimal) report.get("recognizedRevenue");
        assertThat(gbv).isNotEqualByComparingTo(netCash);
        assertThat(netCash).isNotEqualByComparingTo(rev);

        assertThat(report.get("eligibleNights")).isEqualTo(4L);
        assertThat(report.get("occupiedNights")).isEqualTo(3L);
        assertThat((Double) report.get("occupancyRate")).isEqualTo(0.75);
        assertThat(report.get("openDataQualityIssues")).isEqualTo(1);
    }
}

