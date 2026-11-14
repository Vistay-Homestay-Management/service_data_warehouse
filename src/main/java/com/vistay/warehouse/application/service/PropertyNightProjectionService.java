package com.vistay.warehouse.application.service;

import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.DimProperty;
import com.vistay.warehouse.domain.model.FactPropertyNight;
import com.vistay.warehouse.domain.model.FactPropertyNightId;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import com.vistay.warehouse.domain.repository.DimPropertyRepository;
import com.vistay.warehouse.domain.repository.FactPropertyNightRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyNightProjectionService {

    private static final Logger log = LoggerFactory.getLogger(PropertyNightProjectionService.class);

    private static final Set<String> ACTIVE_BOOKING_STATUSES = Set.of(
            "CONFIRMED", "CHECKED_IN", "CHECKED_OUT", "COMPLETED", "BOOKED", "ACTIVE"
    );

    private static final Set<String> VALID_UNAVAILABLE_REASONS = Set.of(
            "MAINTENANCE", "OWNER_USE", "OFFBOARDING", "SUSPENDED", "OTHER"
    );

    private final FactPropertyNightRepository propertyNights;
    private final DimPropertyRepository properties;
    private final DimDateRepository dates;

    public PropertyNightProjectionService(
            FactPropertyNightRepository propertyNights,
            DimPropertyRepository properties,
            DimDateRepository dates) {
        this.propertyNights = propertyNights;
        this.properties = properties;
        this.dates = dates;
    }

    @Transactional
    public List<FactPropertyNight> projectBookingNights(
            UUID propertyId,
            DimProperty property,
            UUID bookingId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            String bookingStatus,
            BigDecimal bookingValue,
            String currency,
            UUID sourceEventId) {

        if (propertyId == null || checkInDate == null || checkOutDate == null) {
            return List.of();
        }

        long nightsCount = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        if (nightsCount <= 0) {
            log.warn("Check-out date {} is not after check-in date {} for booking {}",
                    checkOutDate, checkInDate, bookingId);
            return List.of();
        }

        DimProperty dimProperty = property != null ? property
                : properties.findByPropertyIdAndCurrentRowTrue(propertyId).orElse(null);
        if (dimProperty == null) {
            log.warn("Cannot project property nights without conformed DimProperty for {}", propertyId);
            return List.of();
        }

        BigDecimal nightlyValue = BigDecimal.ZERO;
        if (bookingValue != null && bookingValue.compareTo(BigDecimal.ZERO) > 0) {
            nightlyValue = bookingValue.divide(BigDecimal.valueOf(nightsCount), 4, RoundingMode.HALF_UP);
        }
        String resolvedCurrency = (currency != null && currency.length() == 3) ? currency : "VND";
        boolean isActive = bookingStatus != null && ACTIVE_BOOKING_STATUSES.contains(bookingStatus.toUpperCase());
        boolean isCancelled = "CANCELLED".equalsIgnoreCase(bookingStatus);

        List<FactPropertyNight> updatedNights = new ArrayList<>();
        Instant now = Instant.now();

        for (LocalDate date = checkInDate; date.isBefore(checkOutDate); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            FactPropertyNight night = propertyNights.findById(new FactPropertyNightId(propertyId, currentDate))
                    .orElseGet(() -> {
                        FactPropertyNight fn = new FactPropertyNight(propertyId, currentDate);
                        fn.setProperty(dimProperty);
                        fn.setDate(resolveDate(fn.getServiceDate()));
                        fn.setEligibleForSale(true);
                        fn.setOccupied(false);
                        fn.setUnavailableReason(null);
                        return fn;
                    });

            if (isActive) {
                // Booking occupies the room
                night.setProperty(dimProperty);
                night.setDate(resolveDate(currentDate));
                night.setEligibleForSale(true);
                night.setOccupied(true);
                night.setUnavailableReason(null);
                night.setBookingId(bookingId);
                night.setAllocatedBookingValue(nightlyValue);
                night.setCurrency(resolvedCurrency);
                night.setSourceEventId(sourceEventId);
                night.setUpdatedAt(now);
                updatedNights.add(propertyNights.save(night));
            } else if (isCancelled) {
                // If previously occupied by this booking, release occupancy
                if (bookingId != null && bookingId.equals(night.getBookingId())) {
                    night.setOccupied(false);
                    night.setBookingId(null);
                    night.setAllocatedBookingValue(BigDecimal.ZERO);
                    night.setEligibleForSale(true);
                    night.setUnavailableReason(null);
                    night.setSourceEventId(sourceEventId);
                    night.setUpdatedAt(now);
                    updatedNights.add(propertyNights.save(night));
                }
            }
        }
        return updatedNights;
    }

    @Transactional
    public List<FactPropertyNight> projectMaintenancePeriod(
            UUID propertyId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            UUID sourceEventId) {

        if (propertyId == null || startDate == null || endDate == null) {
            return List.of();
        }
        if (!startDate.isBefore(endDate)) {
            return List.of();
        }

        DimProperty dimProperty = properties.findByPropertyIdAndCurrentRowTrue(propertyId).orElse(null);
        if (dimProperty == null) {
            log.warn("Cannot project maintenance nights without DimProperty for {}", propertyId);
            return List.of();
        }

        String validReason = (reason != null && VALID_UNAVAILABLE_REASONS.contains(reason.toUpperCase()))
                ? reason.toUpperCase() : "MAINTENANCE";

        List<FactPropertyNight> result = new ArrayList<>();
        Instant now = Instant.now();

        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            FactPropertyNight night = propertyNights.findById(new FactPropertyNightId(propertyId, currentDate))
                    .orElseGet(() -> {
                        FactPropertyNight fn = new FactPropertyNight(propertyId, currentDate);
                        fn.setProperty(dimProperty);
                        fn.setDate(resolveDate(currentDate));
                        return fn;
                    });

            night.setProperty(dimProperty);
            night.setDate(resolveDate(currentDate));
            night.setEligibleForSale(false);
            night.setOccupied(false);
            night.setUnavailableReason(validReason);
            night.setBookingId(null);
            night.setAllocatedBookingValue(BigDecimal.ZERO);
            night.setSourceEventId(sourceEventId);
            night.setUpdatedAt(now);
            result.add(propertyNights.save(night));
        }
        return result;
    }

    private DimDate resolveDate(LocalDate date) {
        int key = DimDate.dateKey(date);
        return dates.findById(key).orElseGet(() -> dates.save(DimDate.of(date)));
    }
}
