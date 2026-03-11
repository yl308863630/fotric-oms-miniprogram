package com.oms.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public class HolidayUtil {

    private static final Set<LocalDate> HOLIDAYS_2026 = new HashSet<>();

    static {
        // 2026年法定节假日（需要根据实际情况更新）
        // 元旦
        HOLIDAYS_2026.add(LocalDate.of(2026, 1, 1));
        HOLIDAYS_2026.add(LocalDate.of(2026, 1, 2));
        HOLIDAYS_2026.add(LocalDate.of(2026, 1, 3));
        
        // 春节
        HOLIDAYS_2026.add(LocalDate.of(2026, 2, 16));
        HOLIDAYS_2026.add(LocalDate.of(2026, 2, 17));
        HOLIDAYS_2026.add(LocalDate.of(2026, 2, 18));
        HOLIDAYS_2026.add(LocalDate.of(2026, 2, 19));
        HOLIDAYS_2026.add(LocalDate.of(2026, 2, 20));
        HOLIDAYS_2026.add(LocalDate.of(2026, 2, 21));
        HOLIDAYS_2026.add(LocalDate.of(2026, 2, 22));
        
        // 清明节
        HOLIDAYS_2026.add(LocalDate.of(2026, 4, 4));
        HOLIDAYS_2026.add(LocalDate.of(2026, 4, 5));
        HOLIDAYS_2026.add(LocalDate.of(2026, 4, 6));
        
        // 劳动节
        HOLIDAYS_2026.add(LocalDate.of(2026, 5, 1));
        HOLIDAYS_2026.add(LocalDate.of(2026, 5, 2));
        HOLIDAYS_2026.add(LocalDate.of(2026, 5, 3));
        HOLIDAYS_2026.add(LocalDate.of(2026, 5, 4));
        HOLIDAYS_2026.add(LocalDate.of(2026, 5, 5));
        
        // 端午节
        HOLIDAYS_2026.add(LocalDate.of(2026, 6, 19));
        HOLIDAYS_2026.add(LocalDate.of(2026, 6, 20));
        HOLIDAYS_2026.add(LocalDate.of(2026, 6, 21));
        
        // 中秋节
        HOLIDAYS_2026.add(LocalDate.of(2026, 9, 25));
        HOLIDAYS_2026.add(LocalDate.of(2026, 9, 26));
        HOLIDAYS_2026.add(LocalDate.of(2026, 9, 27));
        
        // 国庆节
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 1));
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 2));
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 3));
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 4));
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 5));
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 6));
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 7));
        HOLIDAYS_2026.add(LocalDate.of(2026, 10, 8));
    }

    public static boolean isHoliday(LocalDate date) {
        return HOLIDAYS_2026.contains(date);
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    public static boolean isWorkday(LocalDate date) {
        return !isHoliday(date) && !isWeekend(date);
    }

    public static LocalDate getNextWorkday(LocalDate date) {
        LocalDate nextDay = date.plusDays(1);
        while (!isWorkday(nextDay)) {
            nextDay = nextDay.plusDays(1);
        }
        return nextDay;
    }

    public static LocalDateTime getReminderTime(LocalDate baseDate, int daysAfter) {
        LocalDate targetDate = baseDate.plusDays(daysAfter);
        
        // 如果是节假日或周末，顺延到下一个工作日
        if (!isWorkday(targetDate)) {
            targetDate = getNextWorkday(targetDate);
        }
        
        return LocalDateTime.of(targetDate, LocalTime.of(9, 0));
    }
}