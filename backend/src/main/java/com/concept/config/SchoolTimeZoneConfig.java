package com.concept.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Pins the clock the school runs on.
 *
 * <p>Every timestamp in this system is a {@code LocalDateTime} written with
 * {@code LocalDateTime.now()}, which reads the JVM default zone. The container
 * on Render runs UTC, so a payment receipted at 09:30 in Bengaluru was stored
 * and printed as 04:00 -- on the receipt handed to the parent who had just
 * paid.
 *
 * <p>Setting the default here rather than formatting per page is deliberate.
 * The alternative means finding every one of the dozens of {@code now()} calls
 * and every template that prints one, and being right about all of them; miss
 * one and the times on two pages disagree, which is worse than being uniformly
 * wrong. The columns are {@code timestamp without time zone} mapped to
 * {@code LocalDateTime}, so the driver performs no conversion of its own and
 * this changes what is written, not how what is written is read back.
 *
 * <p>Rows written before this are five and a half hours early and stay that
 * way. Rewriting them would mean assuming every historical row was written in
 * UTC, which is true of production but not of anyone's local run.
 *
 * <p>This is a single-region setting, not per-tenant. When the first school
 * outside IST signs up, the zone belongs on the tenant and the timestamps
 * belong in UTC with the zone applied at render time.
 */
@Configuration
public class SchoolTimeZoneConfig {

    private static final Logger log = LoggerFactory.getLogger(SchoolTimeZoneConfig.class);

    private final String zoneName;

    public SchoolTimeZoneConfig(@Value("${app.school-timezone:Asia/Kolkata}") String zoneName) {
        this.zoneName = zoneName;
    }

    @PostConstruct
    public void applyDefaultZone() {
        try {
            ZoneId zone = ZoneId.of(zoneName);
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            log.info("Clock set to {} — timestamps are written in the school's local time", zone);
        } catch (java.time.DateTimeException e) {
            // A typo in the property must not take the application down, but it
            // must not pass unnoticed either: the symptom would be receipts
            // silently five and a half hours out again.
            log.error("app.school-timezone is not a valid zone id: '{}'. Leaving the JVM default ({}) in place.",
                    zoneName, TimeZone.getDefault().getID());
        }
    }
}
