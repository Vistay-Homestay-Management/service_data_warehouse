package com.vistay.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.WeekFields;

@Entity
@Table(name = "dim_date", schema = "app")
public class DimDate {

    @Id
    @Column(name = "date_key", nullable = false, updatable = false)
    private Integer dateKey;

    @Column(name = "calendar_date", nullable = false, unique = true)
    private LocalDate calendarDate;

    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "day_name", nullable = false, length = 16)
    private String dayName;

    @Column(name = "day_of_month", nullable = false)
    private Short dayOfMonth;

    @Column(name = "week_of_year", nullable = false)
    private Short weekOfYear;

    @Column(name = "month_number", nullable = false)
    private Short monthNumber;

    @Column(name = "month_name", nullable = false, length = 16)
    private String monthName;

    @Column(name = "quarter_number", nullable = false)
    private Short quarterNumber;

    @Column(name = "year_number", nullable = false)
    private Short yearNumber;

    @Column(name = "is_weekend", nullable = false)
    private boolean weekend;

    @Column(name = "is_holiday", nullable = false)
    private boolean holiday;

    @Column(name = "holiday_name", length = 120)
    private String holidayName;

    protected DimDate() {
    }

    private DimDate(Integer dateKey, LocalDate calendarDate) {
        this.dateKey = dateKey;
        this.calendarDate = calendarDate;
        this.dayOfWeek = (short) calendarDate.getDayOfWeek().getValue();
        this.dayName = calendarDate.getDayOfWeek().name();
        this.dayOfMonth = (short) calendarDate.getDayOfMonth();
        this.weekOfYear = (short) calendarDate.get(WeekFields.ISO.weekOfWeekBasedYear());
        this.monthNumber = (short) calendarDate.getMonthValue();
        this.monthName = calendarDate.getMonth().name();
        this.quarterNumber = (short) ((calendarDate.getMonthValue() - 1) / 3 + 1);
        this.yearNumber = (short) calendarDate.getYear();
        this.weekend = calendarDate.getDayOfWeek().getValue() >= 6;
        this.holiday = false;
    }

    public static int dateKey(LocalDate calendarDate) {
        return calendarDate.getYear() * 10000 + calendarDate.getMonthValue() * 100 + calendarDate.getDayOfMonth();
    }

    public static DimDate of(LocalDate calendarDate) {
        return new DimDate(dateKey(calendarDate), calendarDate);
    }

    public Integer getDateKey() {
        return dateKey;
    }

    public LocalDate getCalendarDate() {
        return calendarDate;
    }

    public Short getDayOfWeek() {
        return dayOfWeek;
    }

    public String getDayName() {
        return dayName;
    }

    public Short getDayOfMonth() {
        return dayOfMonth;
    }

    public Short getWeekOfYear() {
        return weekOfYear;
    }

    public Short getMonthNumber() {
        return monthNumber;
    }

    public String getMonthName() {
        return monthName;
    }

    public Short getQuarterNumber() {
        return quarterNumber;
    }

    public Short getYearNumber() {
        return yearNumber;
    }

    public boolean isWeekend() {
        return weekend;
    }

    public boolean isHoliday() {
        return holiday;
    }

    public String getHolidayName() {
        return holidayName;
    }
}
