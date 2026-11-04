package com.vistay.warehouse.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingEventPayload(
        UUID eventId,
        String eventType,
        UUID bookingId,
        String bookingCode,
        UUID propertyId,
        String propertyName,
        UUID customerId,
        LocalDate bookingDate,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String stayType,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        BigDecimal bookingValue,
        String currency,
        Integer adults,
        Integer children,
        Instant occurredAt
) {
}
