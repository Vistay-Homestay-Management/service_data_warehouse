package com.vistay.warehouse.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseEventPayload(
        UUID expenseId,
        UUID propertyId,
        UUID workOrderId,
        LocalDate incurredDate,
        String expenseCategory,
        String status,
        BigDecimal amount,
        String currency,
        UUID sourceEventId,
        Instant occurredAt
) {
}

