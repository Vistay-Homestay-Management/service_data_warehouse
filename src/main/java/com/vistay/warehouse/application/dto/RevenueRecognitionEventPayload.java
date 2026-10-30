package com.vistay.warehouse.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RevenueRecognitionEventPayload(
        UUID recognitionId,
        UUID bookingId,
        UUID propertyId,
        LocalDate recognitionDate,
        String policyCode,
        BigDecimal recognizedAmount,
        String currency,
        UUID sourceEventId,
        Instant occurredAt
) {
}

