package com.marketplace.backend.service;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** MVP policy: Monday to Friday in Asia/Bangkok; public holidays are not modeled. */
@Component
public class BusinessDayClock {
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    public Instant add(Instant start, int days) {
        if (days < 0) throw new IllegalArgumentException("days must be positive");
        ZonedDateTime at = start.atZone(ZONE);
        for (int added = 0; added < days;) {
            at = at.plusDays(1);
            if (at.getDayOfWeek() != DayOfWeek.SATURDAY && at.getDayOfWeek() != DayOfWeek.SUNDAY) added++;
        }
        return at.toInstant();
    }
}
