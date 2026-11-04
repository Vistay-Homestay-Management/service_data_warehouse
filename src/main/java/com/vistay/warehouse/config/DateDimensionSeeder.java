package com.vistay.warehouse.config;

import com.vistay.warehouse.domain.model.DimDate;
import com.vistay.warehouse.domain.repository.DimDateRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DateDimensionSeeder {

    private static final Logger log = LoggerFactory.getLogger(DateDimensionSeeder.class);

    static final LocalDate START_DATE = LocalDate.of(2024, 1, 1);
    static final LocalDate END_DATE = LocalDate.of(2028, 12, 31);

    private final DimDateRepository dates;

    public DateDimensionSeeder(DimDateRepository dates) {
        this.dates = dates;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        if (dates.count() > 0) {
            log.info("dim_date already populated ({} rows), skipping seed", dates.count());
            return;
        }
        List<DimDate> rows = new ArrayList<>();
        for (LocalDate date = START_DATE; !date.isAfter(END_DATE); date = date.plusDays(1)) {
            rows.add(DimDate.of(date));
        }
        dates.saveAll(rows);
        log.info("Seeded dim_date with {} rows ({}..{})", rows.size(), START_DATE, END_DATE);
    }
}
