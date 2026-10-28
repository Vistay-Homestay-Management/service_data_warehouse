package com.vistay.warehouse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DateDimensionSeederTest {

    private final DimDateRepository dates = mock(DimDateRepository.class);
    private final DateDimensionSeeder seeder = new DateDimensionSeeder(dates);

    @Test
    void seedsAll1827DaysWhenTableEmpty() {
        when(dates.count()).thenReturn(0L);

        seeder.seedIfEmpty();

        ArgumentCaptor<List<DimDate>> captor = ArgumentCaptor.forClass(List.class);
        verify(dates).saveAll(captor.capture());
        List<DimDate> rows = captor.getValue();

        assertThat(rows).hasSize(1827);
        assertThat(rows.get(0).getDateKey()).isEqualTo(20240101);
        assertThat(rows.get(0).getCalendarDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(rows.get(0).getDayOfWeek()).isEqualTo((short) 1);
        assertThat(rows.get(0).isWeekend()).isFalse();
        assertThat(rows.get(rows.size() - 1).getDateKey()).isEqualTo(20281231);
        assertThat(rows)
                .anyMatch(row -> row.getDateKey() == 20240229)
                .anyMatch(row -> row.isWeekend() && row.getDateKey() == 20240106);
    }

    @Test
    void skipsSeedingWhenTableAlreadyPopulated() {
        when(dates.count()).thenReturn(1827L);

        seeder.seedIfEmpty();

        verify(dates, never()).saveAll(anyList());
    }
}
