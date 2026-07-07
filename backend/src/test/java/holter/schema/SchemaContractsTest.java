package holter.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for schema contract records.
 * Validates that all record types can be instantiated and accessed correctly.
 */
class SchemaContractsTest {

    @Test
    @DisplayName("BeatRecord should store all required fields")
    void testBeatRecordCreation() {
        Instant timestamp = Instant.parse("2024-01-01T12:00:00Z");
        BeatRecord beat = new BeatRecord(
            timestamp,
            850.0,
            90.0,
            1.2,
            true,
            0
        );

        assertEquals(timestamp, beat.timestamp());
        assertEquals(850.0, beat.rrIntervalMs());
        assertEquals(90.0, beat.qrsWidthMs());
        assertEquals(1.2, beat.rAmplitude());
        assertTrue(beat.qualityFlag());
        assertEquals(0, beat.dayIndex());
    }

    @Test
    @DisplayName("EnrichedBeatRecord should extend BeatRecord with context features")
    void testEnrichedBeatRecordCreation() {
        Instant timestamp = Instant.parse("2024-01-01T14:30:00Z");
        EnrichedBeatRecord enriched = new EnrichedBeatRecord(
            timestamp,
            850.0,
            90.0,
            1.2,
            true,
            0,
            14.5,
            "awake",
            45.0,
            35.0,
            25.0
        );

        // Verify base fields
        assertEquals(timestamp, enriched.timestamp());
        assertEquals(850.0, enriched.rrIntervalMs());
        assertEquals(90.0, enriched.qrsWidthMs());
        assertEquals(1.2, enriched.rAmplitude());
        assertTrue(enriched.qualityFlag());
        assertEquals(0, enriched.dayIndex());
        
        // Verify enriched fields
        assertEquals(14.5, enriched.hourOfDay());
        assertEquals("awake", enriched.sleepState());
        assertEquals(45.0, enriched.rollingSdnn());
        assertEquals(35.0, enriched.rollingRmssd());
        assertEquals(25.0, enriched.rollingPnn50());
    }

    @Test
    @DisplayName("EnrichedBeatRecord.fromBeatRecord should create enriched record from base")
    void testEnrichedBeatRecordFactory() {
        Instant timestamp = Instant.parse("2024-01-01T12:00:00Z");
        BeatRecord base = new BeatRecord(timestamp, 850.0, 90.0, 1.2, true, 0);
        
        EnrichedBeatRecord enriched = EnrichedBeatRecord.fromBeatRecord(
            base,
            12.0,
            "awake",
            45.0,
            35.0,
            25.0
        );

        assertEquals(base.timestamp(), enriched.timestamp());
        assertEquals(base.rrIntervalMs(), enriched.rrIntervalMs());
        assertEquals(base.qrsWidthMs(), enriched.qrsWidthMs());
        assertEquals(base.rAmplitude(), enriched.rAmplitude());
        assertEquals(base.qualityFlag(), enriched.qualityFlag());
        assertEquals(base.dayIndex(), enriched.dayIndex());
        assertEquals(12.0, enriched.hourOfDay());
        assertEquals("awake", enriched.sleepState());
    }

    @Test
    @DisplayName("ScoredBeatRecord should extend EnrichedBeatRecord with deviation score")
    void testScoredBeatRecordCreation() {
        Instant timestamp = Instant.parse("2024-01-01T03:00:00Z");
        ScoredBeatRecord scored = new ScoredBeatRecord(
            timestamp,
            850.0,
            90.0,
            1.2,
            true,
            0,
            3.0,
            "sleep",
            45.0,
            35.0,
            25.0,
            4.5,
            "sleep_night_3"
        );

        // Verify all fields including scoring
        assertEquals(timestamp, scored.timestamp());
        assertEquals(3.0, scored.hourOfDay());
        assertEquals("sleep", scored.sleepState());
        assertEquals(4.5, scored.deviationScore());
        assertEquals("sleep_night_3", scored.contextBucket());
    }

    @Test
    @DisplayName("ScoredBeatRecord.fromEnrichedBeatRecord should create scored record from enriched")
    void testScoredBeatRecordFactory() {
        Instant timestamp = Instant.parse("2024-01-01T03:00:00Z");
        EnrichedBeatRecord enriched = new EnrichedBeatRecord(
            timestamp, 850.0, 90.0, 1.2, true, 0,
            3.0, "sleep", 45.0, 35.0, 25.0
        );
        
        ScoredBeatRecord scored = ScoredBeatRecord.fromEnrichedBeatRecord(
            enriched,
            4.5,
            "sleep_night_3"
        );

        assertEquals(enriched.timestamp(), scored.timestamp());
        assertEquals(enriched.hourOfDay(), scored.hourOfDay());
        assertEquals(enriched.sleepState(), scored.sleepState());
        assertEquals(4.5, scored.deviationScore());
        assertEquals("sleep_night_3", scored.contextBucket());
    }

    @Test
    @DisplayName("FlaggedEvent should store anomaly event metadata")
    void testFlaggedEventCreation() {
        Instant startTime = Instant.parse("2024-01-05T03:15:00Z");
        Instant endTime = Instant.parse("2024-01-05T03:15:30Z");
        
        FlaggedEvent event = new FlaggedEvent(
            "event-123",
            startTime,
            endTime,
            30.0,
            35,
            5.2,
            "sleep_night_3",
            4,
            3.25,
            "sleep"
        );

        assertEquals("event-123", event.eventId());
        assertEquals(startTime, event.startTime());
        assertEquals(endTime, event.endTime());
        assertEquals(30.0, event.durationSec());
        assertEquals(35, event.beatsInvolved());
        assertEquals(5.2, event.deviationScore());
        assertEquals("sleep_night_3", event.contextBucket());
        assertEquals(4, event.dayIndex());
        assertEquals(3.25, event.hourOfDay());
        assertEquals("sleep", event.sleepState());
    }

    @Test
    @DisplayName("SummaryStats should store aggregate statistics")
    void testSummaryStatsCreation() {
        SummaryStats stats = new SummaryStats(
            12,
            4.8,
            "sleep_night_3"
        );

        assertEquals(12, stats.totalEvents());
        assertEquals(4.8, stats.avgDeviationScore());
        assertEquals("sleep_night_3", stats.mostCommonContext());
    }

    @Test
    @DisplayName("FindingsJson should aggregate all pipeline outputs")
    void testFindingsJsonCreation() {
        Instant startTime = Instant.parse("2024-01-05T03:15:00Z");
        Instant endTime = Instant.parse("2024-01-05T03:15:30Z");
        
        FlaggedEvent event1 = new FlaggedEvent(
            "event-1", startTime, endTime, 30.0, 35,
            5.2, "sleep_night_3", 4, 3.25, "sleep"
        );
        
        FlaggedEvent event2 = new FlaggedEvent(
            "event-2", startTime.plusSeconds(3600), endTime.plusSeconds(3600),
            25.0, 30, 4.5, "sleep_night_4", 4, 4.25, "sleep"
        );
        
        List<FlaggedEvent> events = new ArrayList<>();
        events.add(event1);
        events.add(event2);
        
        SummaryStats stats = new SummaryStats(2, 4.85, "sleep_night_3");
        
        FindingsJson findings = new FindingsJson(
            "patient-001",
            7,
            95432L,
            events,
            stats,
            null  // Backward compatibility: dailySummaries optional
        );

        assertEquals("patient-001", findings.patientId());
        assertEquals(7, findings.recordingDays());
        assertEquals(95432L, findings.totalBeatsProcessed());
        assertEquals(2, findings.events().size());
        assertEquals(stats, findings.summaryStats());
        assertEquals(2, findings.summaryStats().totalEvents());
        assertEquals(4.85, findings.summaryStats().avgDeviationScore());
    }

    @Test
    @DisplayName("FindingsJson should handle empty events list")
    void testFindingsJsonWithNoEvents() {
        SummaryStats stats = new SummaryStats(0, 0.0, "none");
        FindingsJson findings = new FindingsJson(
            "patient-002",
            30,
            432000L,
            List.of(),
            stats,
            null  // Backward compatibility: dailySummaries optional
        );

        assertEquals("patient-002", findings.patientId());
        assertEquals(30, findings.recordingDays());
        assertTrue(findings.events().isEmpty());
        assertEquals(0, findings.summaryStats().totalEvents());
    }

    @Test
    @DisplayName("Records should be immutable")
    void testRecordImmutability() {
        Instant timestamp = Instant.parse("2024-01-01T12:00:00Z");
        BeatRecord beat = new BeatRecord(timestamp, 850.0, 90.0, 1.2, true, 0);
        
        // Records should not have setters - this is enforced at compile time
        // We verify by checking that accessing fields multiple times returns same values
        assertEquals(beat.timestamp(), beat.timestamp());
        assertEquals(beat.rrIntervalMs(), beat.rrIntervalMs());
        
        // Verify equals and hashCode work correctly for records
        BeatRecord beat2 = new BeatRecord(timestamp, 850.0, 90.0, 1.2, true, 0);
        assertEquals(beat, beat2);
        assertEquals(beat.hashCode(), beat2.hashCode());
    }

    @Test
    @DisplayName("Sleep state values should follow expected patterns")
    void testSleepStateValues() {
        EnrichedBeatRecord awake = new EnrichedBeatRecord(
            Instant.now(), 850.0, 90.0, 1.2, true, 0,
            14.0, "awake", 45.0, 35.0, 25.0
        );
        
        EnrichedBeatRecord sleep = new EnrichedBeatRecord(
            Instant.now(), 850.0, 90.0, 1.2, true, 0,
            3.0, "sleep", 45.0, 35.0, 25.0
        );
        
        EnrichedBeatRecord transition = new EnrichedBeatRecord(
            Instant.now(), 850.0, 90.0, 1.2, true, 0,
            21.0, "transition", 45.0, 35.0, 25.0
        );

        assertEquals("awake", awake.sleepState());
        assertEquals("sleep", sleep.sleepState());
        assertEquals("transition", transition.sleepState());
    }

    @Test
    @DisplayName("Hour of day should be within valid range")
    void testHourOfDayRange() {
        EnrichedBeatRecord morning = new EnrichedBeatRecord(
            Instant.now(), 850.0, 90.0, 1.2, true, 0,
            0.0, "sleep", 45.0, 35.0, 25.0
        );
        
        EnrichedBeatRecord evening = new EnrichedBeatRecord(
            Instant.now(), 850.0, 90.0, 1.2, true, 0,
            23.99, "awake", 45.0, 35.0, 25.0
        );

        assertTrue(morning.hourOfDay() >= 0.0 && morning.hourOfDay() < 24.0);
        assertTrue(evening.hourOfDay() >= 0.0 && evening.hourOfDay() < 24.0);
    }
}
