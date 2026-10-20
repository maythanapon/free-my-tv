package io.freemytv;

import java.time.LocalDate;
import java.time.ZoneId;

/** Validated optional dashboard settings. Personal values only come from the ignored config. */
final class DashboardConfig {
    static final ZoneId HOME_ZONE = zone(Config.HOME_TIMEZONE, ZoneId.systemDefault());
    static final ZoneId TRIP_ZONE = zone(Config.TRIP_TIMEZONE, ZoneId.of("UTC"));
    static final LocalDate TRIP_DAY = date(Config.TRIP_DATE);
    static final boolean TRIP_ENABLED = !Config.TRIP_NAME.trim().isEmpty() && TRIP_DAY != null;
    private static ZoneId zone(String value, ZoneId fallback) {
        try { return ZoneId.of(value.trim()); } catch (RuntimeException ignored) { return fallback; }
    }
    private static LocalDate date(String value) {
        try { return LocalDate.parse(value.trim()); } catch (RuntimeException ignored) { return null; }
    }
    private DashboardConfig() {}
}
