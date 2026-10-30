package com.vistay.warehouse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.model.DimProperty;
import com.vistay.warehouse.domain.model.FactPropertyNight;
import com.vistay.warehouse.domain.model.FactPropertyNightId;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import com.vistay.warehouse.domain.repository.DimPropertyRepository;
import com.vistay.warehouse.domain.repository.FactPropertyNightRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropertyNightProjectionServiceTest {

    private FactPropertyNightRepository propertyNights;
    private DimPropertyRepository properties;
    private DimDateRepository dates;
    private PropertyNightProjectionService service;

    @BeforeEach
    void setUp() {
        propertyNights = mock(FactPropertyNightRepository.class);
        properties = mock(DimPropertyRepository.class);
        dates = mock(DimDateRepository.class);

        lenient().when(propertyNights.save(any(FactPropertyNight.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(propertyNights.findById(any(FactPropertyNightId.class))).thenReturn(Optional.empty());
        lenient().when(dates.findById(any())).thenReturn(Optional.empty());
        lenient().when(dates.save(any(DimDate.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new PropertyNightProjectionService(propertyNights, properties, dates);
    }

    @Test
    void projectsNightlyOccupancyForConfirmedBookingExcludingCheckoutDate() {
        UUID propertyId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2026, 2, 1);
        LocalDate checkOut = LocalDate.of(2026, 2, 4); // 3 nights: 2/1, 2/2, 2/3
        BigDecimal bookingValue = new BigDecimal("3000000.00");
        DimProperty dimProp = new DimProperty(propertyId, "Villa A", Instant.now(), UUID.randomUUID(), Instant.now());

        List<FactPropertyNight> nights = service.projectBookingNights(
                propertyId, dimProp, bookingId, checkIn, checkOut,
                "CONFIRMED", bookingValue, "VND", UUID.randomUUID());

        assertThat(nights).hasSize(3);
        // Each night allocated 1,000,000
        for (FactPropertyNight night : nights) {
            assertThat(night.isEligibleForSale()).isTrue();
            assertThat(night.isOccupied()).isTrue();
            assertThat(night.getUnavailableReason()).isNull();
            assertThat(night.getBookingId()).isEqualTo(bookingId);
            assertThat(night.getAllocatedBookingValue()).isEqualByComparingTo(new BigDecimal("1000000.00"));
            assertThat(night.getCurrency()).isEqualTo("VND");
        }

        // Verify dates are 2/1, 2/2, 2/3 (checkout date 2/4 NOT included)
        assertThat(nights.get(0).getServiceDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(nights.get(1).getServiceDate()).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(nights.get(2).getServiceDate()).isEqualTo(LocalDate.of(2026, 2, 3));
    }

    @Test
    void releasesOccupancyWhenBookingCancelled() {
        UUID propertyId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2026, 2, 1);
        LocalDate checkOut = LocalDate.of(2026, 2, 2); // 1 night
        DimProperty dimProp = new DimProperty(propertyId, "Villa A", Instant.now(), UUID.randomUUID(), Instant.now());

        // Existing night was occupied by this booking
        FactPropertyNight existingNight = new FactPropertyNight(propertyId, checkIn);
        existingNight.setBookingId(bookingId);
        existingNight.setOccupied(true);
        existingNight.setAllocatedBookingValue(new BigDecimal("1000000"));
        lenient().when(propertyNights.findById(new FactPropertyNightId(propertyId, checkIn)))
                .thenReturn(Optional.of(existingNight));

        List<FactPropertyNight> updated = service.projectBookingNights(
                propertyId, dimProp, bookingId, checkIn, checkOut,
                "CANCELLED", BigDecimal.ZERO, "VND", UUID.randomUUID());

        assertThat(updated).hasSize(1);
        FactPropertyNight night = updated.get(0);
        assertThat(night.isOccupied()).isFalse();
        assertThat(night.getBookingId()).isNull();
        assertThat(night.getAllocatedBookingValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(night.isEligibleForSale()).isTrue();
    }

    @Test
    void projectsMaintenancePeriodExcludingInventory() {
        UUID propertyId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 3, 10);
        LocalDate end = LocalDate.of(2026, 3, 12); // 2 nights: 3/10, 3/11
        DimProperty dimProp = new DimProperty(propertyId, "Villa A", Instant.now(), UUID.randomUUID(), Instant.now());
        lenient().when(properties.findByPropertyIdAndCurrentRowTrue(propertyId)).thenReturn(Optional.of(dimProp));

        List<FactPropertyNight> nights = service.projectMaintenancePeriod(
                propertyId, start, end, "MAINTENANCE", UUID.randomUUID());

        assertThat(nights).hasSize(2);
        for (FactPropertyNight night : nights) {
            assertThat(night.isEligibleForSale()).isFalse();
            assertThat(night.isOccupied()).isFalse();
            assertThat(night.getUnavailableReason()).isEqualTo("MAINTENANCE");
            assertThat(night.getBookingId()).isNull();
            assertThat(night.getAllocatedBookingValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    void handlesInvalidOrZeroNightRangeGracefully() {
        UUID propertyId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2026, 2, 5);
        LocalDate checkOut = LocalDate.of(2026, 2, 4); // checkOut before checkIn

        List<FactPropertyNight> result = service.projectBookingNights(
                propertyId, null, UUID.randomUUID(), checkIn, checkOut,
                "CONFIRMED", BigDecimal.TEN, "VND", UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}

