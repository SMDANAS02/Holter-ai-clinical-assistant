package holter.mock;

import holter.schema.FindingsJson;
import holter.schema.FlaggedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockFindingsGenerator.
 */
class MockFindingsGeneratorTest {

    @Test
    @DisplayName("Should generate correct number of events")
    void testGenerateCorrectNumberOfEvents() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(15)
            .build();
        
        FindingsJson findings = generator.generate();
        
        assertEquals(15, findings.events().size());
    }

    @Test
    @DisplayName("Should generate zero events when requested")
    void testGenerateZeroEvents() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(0)
            .build();
        
        FindingsJson findings = generator.generate();
        
        assertEquals(0, findings.events().size());
        assertEquals(0, findings.summaryStats().totalEvents());
        assertEquals(0.0, findings.summaryStats().avgDeviationScore());
        assertEquals("none", findings.summaryStats().mostCommonContext());
    }

    @Test
    @DisplayName("Should throw exception for negative number of events")
    void testNegativeNumberOfEvents() {
        assertThrows(IllegalArgumentException.class, () -> {
            MockFindingsGenerator.builder()
                .numberOfEvents(-1)
                .build();
        });
    }

    @Test
    @DisplayName("Should throw exception for invalid recording days")
    void testInvalidRecordingDays() {
        assertThrows(IllegalArgumentException.class, () -> {
            MockFindingsGenerator.builder()
                .recordingDays(0)
                .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            MockFindingsGenerator.builder()
                .recordingDays(-1)
                .build();
        });
    }

    @Test
    @DisplayName("Should throw exception for negative total beats")
    void testNegativeTotalBeats() {
        assertThrows(IllegalArgumentException.class, () -> {
            MockFindingsGenerator.builder()
                .totalBeatsProcessed(-1L)
                .build();
        });
    }

    @Test
    @DisplayName("Should set patient ID correctly")
    void testPatientId() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .patientId("patient-test-123")
            .numberOfEvents(5)
            .build();
        
        FindingsJson findings = generator.generate();
        
        assertEquals("patient-test-123", findings.patientId());
    }

    @Test
    @DisplayName("Should set recording days correctly")
    void testRecordingDays() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .recordingDays(30)
            .numberOfEvents(5)
            .build();
        
        FindingsJson findings = generator.generate();
        
        assertEquals(30, findings.recordingDays());
    }

    @Test
    @DisplayName("Should set total beats processed correctly")
    void testTotalBeatsProcessed() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .totalBeatsProcessed(250000L)
            .numberOfEvents(5)
            .build();
        
        FindingsJson findings = generator.generate();
        
        assertEquals(250000L, findings.totalBeatsProcessed());
    }

    @Test
    @DisplayName("All events should have unique event IDs")
    void testUniqueEventIds() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(50)
            .build();
        
        FindingsJson findings = generator.generate();
        
        Set<String> eventIds = new HashSet<>();
        for (FlaggedEvent event : findings.events()) {
            assertTrue(eventIds.add(event.eventId()),
                "Event ID should be unique: " + event.eventId());
        }
    }

    @Test
    @DisplayName("All events should have valid timestamps")
    void testValidTimestamps() {
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(20)
            .recordingDays(7)
            .startTime(startTime)
            .build();
        
        FindingsJson findings = generator.generate();
        
        Instant endTime = startTime.plusSeconds(7L * 86400L);
        
        for (FlaggedEvent event : findings.events()) {
            assertNotNull(event.startTime());
            assertNotNull(event.endTime());
            
            assertTrue(event.startTime().compareTo(startTime) >= 0,
                "Event start time should be >= recording start time");
            assertTrue(event.endTime().compareTo(endTime) <= 0,
                "Event end time should be <= recording end time");
            assertTrue(event.endTime().isAfter(event.startTime()),
                "Event end time should be after start time");
        }
    }

    @Test
    @DisplayName("Event durations should match timestamp differences")
    void testEventDurations() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(10)
            .build();
        
        FindingsJson findings = generator.generate();
        
        for (FlaggedEvent event : findings.events()) {
            long actualDurationSec = java.time.Duration.between(
                event.startTime(), event.endTime()
            ).getSeconds();
            
            // Allow small rounding differences
            assertTrue(Math.abs(actualDurationSec - event.durationSec()) <= 1,
                "Duration should match timestamp difference");
        }
    }

    @Test
    @DisplayName("Deviation scores should be within expected range")
    void testDeviationScores() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(50)
            .build();
        
        FindingsJson findings = generator.generate();
        
        for (FlaggedEvent event : findings.events()) {
            assertTrue(event.deviationScore() >= 3.0,
                "Deviation score should be >= 3.0");
            assertTrue(event.deviationScore() <= 8.0,
                "Deviation score should be <= 8.0");
        }
    }

    @Test
    @DisplayName("Beats involved should be within expected range")
    void testBeatsInvolved() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(50)
            .build();
        
        FindingsJson findings = generator.generate();
        
        for (FlaggedEvent event : findings.events()) {
            assertTrue(event.beatsInvolved() >= 5,
                "Beats involved should be >= 5");
            assertTrue(event.beatsInvolved() <= 100,
                "Beats involved should be <= 100");
        }
    }

    @Test
    @DisplayName("Hour of day should be within valid range")
    void testHourOfDay() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(50)
            .build();
        
        FindingsJson findings = generator.generate();
        
        for (FlaggedEvent event : findings.events()) {
            assertTrue(event.hourOfDay() >= 0.0,
                "Hour of day should be >= 0.0");
            assertTrue(event.hourOfDay() < 24.0,
                "Hour of day should be < 24.0");
        }
    }

    @Test
    @DisplayName("Sleep state should be valid")
    void testSleepState() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(50)
            .build();
        
        FindingsJson findings = generator.generate();
        
        Set<String> validSleepStates = Set.of("awake", "sleep", "transition");
        
        for (FlaggedEvent event : findings.events()) {
            assertTrue(validSleepStates.contains(event.sleepState()),
                "Sleep state should be one of: awake, sleep, transition");
        }
    }

    @Test
    @DisplayName("Context bucket should match sleep state")
    void testContextBucketMatchesSleepState() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(50)
            .build();
        
        FindingsJson findings = generator.generate();
        
        for (FlaggedEvent event : findings.events()) {
            String sleepState = event.sleepState();
            String contextBucket = event.contextBucket();
            
            assertTrue(contextBucket.startsWith(sleepState),
                "Context bucket '" + contextBucket + "' should start with sleep state '" + sleepState + "'");
        }
    }

    @Test
    @DisplayName("Day index should be within recording period")
    void testDayIndexWithinRange() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(50)
            .recordingDays(7)
            .build();
        
        FindingsJson findings = generator.generate();
        
        for (FlaggedEvent event : findings.events()) {
            assertTrue(event.dayIndex() >= 0,
                "Day index should be >= 0");
            assertTrue(event.dayIndex() < 7,
                "Day index should be < recording days");
        }
    }

    @Test
    @DisplayName("Summary stats should match event data")
    void testSummaryStats() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(20)
            .build();
        
        FindingsJson findings = generator.generate();
        
        // Verify total events
        assertEquals(20, findings.summaryStats().totalEvents());
        
        // Verify average deviation score
        double calculatedAvg = findings.events().stream()
            .mapToDouble(FlaggedEvent::deviationScore)
            .average()
            .orElse(0.0);
        
        assertEquals(calculatedAvg, findings.summaryStats().avgDeviationScore(), 0.001);
        
        // Verify most common context exists in events
        String mostCommon = findings.summaryStats().mostCommonContext();
        boolean foundInEvents = findings.events().stream()
            .anyMatch(event -> event.contextBucket().equals(mostCommon));
        
        assertTrue(foundInEvents,
            "Most common context should exist in events");
    }

    @Test
    @DisplayName("Should produce reproducible results with same seed")
    void testReproducibility() {
        MockFindingsGenerator gen1 = MockFindingsGenerator.builder()
            .numberOfEvents(10)
            .randomSeed(12345)
            .build();
        
        MockFindingsGenerator gen2 = MockFindingsGenerator.builder()
            .numberOfEvents(10)
            .randomSeed(12345)
            .build();
        
        FindingsJson findings1 = gen1.generate();
        FindingsJson findings2 = gen2.generate();
        
        assertEquals(findings1.events().size(), findings2.events().size());
        
        for (int i = 0; i < findings1.events().size(); i++) {
            FlaggedEvent e1 = findings1.events().get(i);
            FlaggedEvent e2 = findings2.events().get(i);
            
            assertEquals(e1.startTime(), e2.startTime());
            assertEquals(e1.endTime(), e2.endTime());
            assertEquals(e1.durationSec(), e2.durationSec());
            assertEquals(e1.beatsInvolved(), e2.beatsInvolved());
            assertEquals(e1.deviationScore(), e2.deviationScore());
            assertEquals(e1.contextBucket(), e2.contextBucket());
            assertEquals(e1.dayIndex(), e2.dayIndex());
            assertEquals(e1.hourOfDay(), e2.hourOfDay());
            assertEquals(e1.sleepState(), e2.sleepState());
        }
    }

    @Test
    @DisplayName("Clustered generation should produce events sorted by time")
    void testClusteredGenerationSorted() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(30)
            .build();
        
        FindingsJson findings = generator.generateWithClustering();
        
        List<FlaggedEvent> events = findings.events();
        
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).startTime().compareTo(events.get(i - 1).startTime()) >= 0,
                "Events should be sorted by start time");
        }
    }

    @Test
    @DisplayName("Clustered generation should produce correct number of events")
    void testClusteredGenerationCorrectNumber() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(25)
            .build();
        
        FindingsJson findings = generator.generateWithClustering();
        
        assertEquals(25, findings.events().size());
    }

    @Test
    @DisplayName("Clustered generation should handle zero events")
    void testClusteredGenerationZeroEvents() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(0)
            .build();
        
        FindingsJson findings = generator.generateWithClustering();
        
        assertEquals(0, findings.events().size());
        assertEquals("none", findings.summaryStats().mostCommonContext());
    }

    @Test
    @DisplayName("Sleep state should be consistent with hour of day")
    void testSleepStateConsistencyWithHour() {
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(100)
            .randomSeed(42)
            .build();
        
        FindingsJson findings = generator.generate();
        
        for (FlaggedEvent event : findings.events()) {
            double hour = event.hourOfDay();
            String sleepState = event.sleepState();
            
            if (hour >= 22.0 || hour < 6.0) {
                assertEquals("sleep", sleepState,
                    "Hour " + hour + " should be in sleep state");
            } else if ((hour >= 6.0 && hour < 8.0) || (hour >= 20.0 && hour < 22.0)) {
                assertEquals("transition", sleepState,
                    "Hour " + hour + " should be in transition state");
            } else {
                assertEquals("awake", sleepState,
                    "Hour " + hour + " should be in awake state");
            }
        }
    }
}
