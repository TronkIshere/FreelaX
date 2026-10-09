package com.marketplace.backend.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessDayClockTest {
    private final BusinessDayClock clock = new BusinessDayClock();

    @Test void skipsWeekendInBangkok() {
        Instant thursday = Instant.parse("2026-10-08T03:00:00Z");
        assertThat(clock.add(thursday, 1)).isEqualTo(Instant.parse("2026-10-09T03:00:00Z"));
        assertThat(clock.add(thursday, 2)).isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
        assertThat(clock.add(thursday, 3)).isEqualTo(Instant.parse("2026-10-13T03:00:00Z"));
    }
}
