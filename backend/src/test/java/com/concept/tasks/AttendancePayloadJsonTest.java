package com.concept.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.concept.shared.data.AttendanceStatus;
import com.concept.tasks.app.AttendancePayload;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The wire contract for the attendance endpoint.
 *
 * <p>{@code date} was added to a record that already had clients, and the record
 * now carries a second, non-canonical constructor for the old shape. Jackson
 * picks a record's canonical constructor, but "should" is not "does" — and if it
 * picked wrong the mobile app's register submission would break in production
 * with nothing here to catch it. Hence a test of the actual deserialisation
 * rather than of the Java constructors.
 */
class AttendancePayloadJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void readsTheExistingClientShapeWithNoDate() throws Exception {
        String json = """
                {"attendance":[{"studentId":"11111111-1111-1111-1111-111111111111",
                                "status":"PRESENT","remarks":""}]}
                """;

        AttendancePayload payload = mapper.readValue(json, AttendancePayload.class);

        assertNull(payload.date(), "no date means today; the service applies that default");
        assertEquals(1, payload.attendance().size());
        assertEquals(AttendanceStatus.PRESENT, payload.attendance().get(0).status());
    }

    @Test
    void readsAnExplicitDate() throws Exception {
        String json = """
                {"date":"2026-09-14",
                 "attendance":[{"studentId":"11111111-1111-1111-1111-111111111111",
                                "status":"ABSENT","remarks":"Off sick"}]}
                """;

        AttendancePayload payload = mapper.readValue(json, AttendancePayload.class);

        assertEquals(LocalDate.of(2026, 9, 14), payload.date());
        assertEquals(AttendanceStatus.ABSENT, payload.attendance().get(0).status());
        assertEquals("Off sick", payload.attendance().get(0).remarks());
    }
}
