package com.lightweightai.tools.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeTools")
class TimeToolsTest {

    private TimeTools timeTools;

    @BeforeEach
    void setUp() {
        timeTools = new TimeTools();
    }

    @Test
    @DisplayName("getTime should return non-null string")
    void getTimeReturnsNonNull() {
        String result = timeTools.getTime();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getTime should return valid ISO date time format")
    void getTimeReturnsValidIsoFormat() {
        String result = timeTools.getTime();

        // Should be parseable as ISO_LOCAL_DATE_TIME
        assertDoesNotThrow(() -> {
            LocalDateTime parsed = LocalDateTime.parse(result, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            assertNotNull(parsed);
        });
    }

    @Test
    @DisplayName("getTime should return a time close to now")
    void getTimeReturnsCurrentTime() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        String result = timeTools.getTime();
        LocalDateTime parsed = LocalDateTime.parse(result, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertTrue(parsed.isAfter(before) || parsed.isEqual(before));
        assertTrue(parsed.isBefore(after) || parsed.isEqual(after));
    }
}
