package com.vistay.warehouse.application.service;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseReconciliationService {

    private final FactBookingRepository bookings;
    private final FactFinancialTransactionRepository transactions;
    private final FactRevenueRecognitionRepository revenues;
    private final FactExpenseRepository expenses;
    private final FactPropertyNightRepository propertyNights;
    private final IngestedEventRepository ingestedEvents;
    private final ProcessedEventRepository processedEvents;
    private final DataQualityIssueRepository dataQualityIssues;

    public WarehouseReconciliationService(
            FactBookingRepository bookings,
            FactFinancialTransactionRepository transactions,
            FactRevenueRecognitionRepository revenues,
            FactExpenseRepository expenses,
            FactPropertyNightRepository propertyNights,
            IngestedEventRepository ingestedEvents,
            ProcessedEventRepository processedEvents,
            DataQualityIssueRepository dataQualityIssues) {
        this.bookings = bookings;
        this.transactions = transactions;
        this.revenues = revenues;
        this.expenses = expenses;
        this.propertyNights = propertyNights;
        this.ingestedEvents = ingestedEvents;
        this.processedEvents = processedEvents;
        this.dataQualityIssues = dataQualityIssues;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reconcileWarehouseState() {
        List<FactBooking> allBookings = bookings.findAll();
        BigDecimal totalGrossBookingValue = allBookings.stream()
                .map(FactBooking::getBookingValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FactFinancialTransaction> allTransactions = transactions.findAll();
        BigDecimal totalConfirmedPayments = allTransactions.stream()
                .filter(tx -> "CONFIRMED".equalsIgnoreCase(tx.getStatus()) && "PAYMENT".equalsIgnoreCase(tx.getTransactionType()))
                .map(FactFinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRefunds = allTransactions.stream()
                .filter(tx -> "CONFIRMED".equalsIgnoreCase(tx.getStatus()) && "REFUND".equalsIgnoreCase(tx.getTransactionType()))
                .map(FactFinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FactRevenueRecognition> allRevenues = revenues.findAll();
        BigDecimal totalRecognizedRevenue = allRevenues.stream()
                .map(FactRevenueRecognition::getRecognizedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FactPropertyNight> allNights = propertyNights.findAll();
        long eligibleNights = allNights.stream().filter(FactPropertyNight::isEligibleForSale).count();
        long occupiedNights = allNights.stream().filter(FactPropertyNight::isOccupied).count();

        List<DataQualityIssue> openIssues = dataQualityIssues.findByStatus("OPEN");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBookings", allBookings.size());
        result.put("grossBookingValue", totalGrossBookingValue);
        result.put("confirmedCashIn", totalConfirmedPayments);
        result.put("confirmedRefunds", totalRefunds);
        result.put("netCashIn", totalConfirmedPayments.subtract(totalRefunds));
        result.put("recognizedRevenue", totalRecognizedRevenue);
        result.put("totalPropertyNights", allNights.size());
        result.put("eligibleNights", eligibleNights);
        result.put("occupiedNights", occupiedNights);
        result.put("occupancyRate", eligibleNights > 0 ? (double) occupiedNights / eligibleNights : 0.0);
        result.put("openDataQualityIssues", openIssues.size());
        result.put("totalProcessedEvents", processedEvents.count());
        result.put("totalIngestedEvents", ingestedEvents.count());
        return result;
    }
}
