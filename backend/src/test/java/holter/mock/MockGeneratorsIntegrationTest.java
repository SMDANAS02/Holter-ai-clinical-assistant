package holter.mock;

import holter.schema.BeatRecord;
import holter.schema.FindingsJson;
import holter.schema.FlaggedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests demonstrating how mock generators work together
 * and can be used for parallel development scenarios.
 */
class MockGeneratorsIntegrationTest {

    @Test
    @DisplayName("Mock generators should produce compatible data for full pipeline simulation")
    void testFullPipelineSimulation() {
        // Scenario: Simulating a 7-day recording with 100k beats and 15 anomalous events
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        
        // Generate mock beat data (simulates output of Modules 1 + 2)
        MockBeatTableGenerator beatGen = MockBeatTableGenerator.builder()
            .numberOfBeats(100000)
            .anomalyRate(0.05)
            .startTime(startTime)
            .randomSeed(42)
            .build();
        
        List<BeatRecord> beats = beatGen.generateWithCircadianRhythm();
        
        // Generate mock findings (simulates output of Module 5)
        MockFindingsGenerator findingsGen = MockFindingsGenerator.builder()
            .numberOfEvents(15)
            .patientId("patient-001")
            .recordingDays(7)
            .totalBeatsProcessed(100000L)
            .startTime(startTime)
            .randomSeed(42)
            .build();
        
        FindingsJson findings = findingsGen.generate();
        
        // Verify data compatibility
        assertEquals(100000, beats.size());
        assertEquals(15, findings.events().size());
        assertEquals(100000L, findings.totalBeatsProcessed());
        assertEquals(7, findings.recordingDays());
        
        // Verify temporal consistency
        Instant firstBeatTime = beats.get(0).timestamp();
        Instant lastBeatTime = beats.get(beats.size() - 1).timestamp();
        
        for (FlaggedEvent event : findings.events()) {
            assertTrue(event.startTime().compareTo(firstBeatTime) >= 0,
                "Event should start after first beat");
            assertTrue(event.endTime().compareTo(lastBeatTime) <= 0,
                "Event should end before last beat");
        }
    }

    @Test
    @DisplayName("Mock beat data should span multiple days as expected")
    void testMultiDayGeneration() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(200000) // Enough beats to span ~2 days
            .anomalyRate(0.0)
            .startTime(Instant.parse("2024-01-01T00:00:00Z"))
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        // Check day indices
        long uniqueDays = beats.stream()
            .map(BeatRecord::dayIndex)
            .distinct()
            .count();
        
        assertTrue(uniqueDays >= 2, "Should span at least 2 days");
    }

    @Test
    @DisplayName("Mock findings with zero events should be valid for Agent/Dashboard testing")
    void testZeroEventsScenario() {
        // Scenario: Patient with no detected anomalies
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(0)
            .patientId("patient-normal-001")
            .recordingDays(30)
            .totalBeatsProcessed(300000L)
            .build();
        
        FindingsJson findings = generator.generate();
        
        // Verify this is valid input for Agent/Dashboard
        assertNotNull(findings.events());
        assertTrue(findings.events().isEmpty());
        assertEquals(0, findings.summaryStats().totalEvents());
        assertEquals("none", findings.summaryStats().mostCommonContext());
        assertEquals(0.0, findings.summaryStats().avgDeviationScore());
    }

    @Test
    @DisplayName("Circadian rhythm beats should show temporal patterns")
    void testCircadianRhythmPatterns() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(200000)
            .anomalyRate(0.0)
            .startTime(Instant.parse("2024-01-01T00:00:00Z"))
            .randomSeed(42)
            .build();
        
        List<BeatRecord> beats = generator.generateWithCircadianRhythm();
        
        // Calculate average RR intervals for different time periods
        double sleepAvg = beats.stream()
            .filter(beat -> {
                int hour = beat.timestamp().atZone(java.time.ZoneOffset.UTC).getHour();
                return hour >= 0 && hour < 6; // Sleep hours
            })
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        double awakeAvg = beats.stream()
            .filter(beat -> {
                int hour = beat.timestamp().atZone(java.time.ZoneOffset.UTC).getHour();
                return hour >= 10 && hour < 18; // Awake hours
            })
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        // Sleep RR should be longer (lower heart rate)
        assertTrue(sleepAvg > awakeAvg,
            String.format("Sleep RR (%.2f) should be > Awake RR (%.2f)", sleepAvg, awakeAvg));
        
        // Verify the difference is significant (at least 10%)
        double percentDifference = ((sleepAvg - awakeAvg) / awakeAvg) * 100;
        assertTrue(percentDifference >= 10.0,
            String.format("Sleep-awake RR difference should be >= 10%%, got %.2f%%", percentDifference));
    }

    @Test
    @DisplayName("Clustered findings should be useful for dashboard timeline testing")
    void testClusteredFindingsForDashboard() {
        // Scenario: Events clustered in time for testing timeline visualization
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(30)
            .patientId("patient-clustered-001")
            .recordingDays(7)
            .totalBeatsProcessed(100000L)
            .randomSeed(42)
            .build();
        
        FindingsJson findings = generator.generateWithClustering();
        
        // Verify events are sorted (required for timeline display)
        List<FlaggedEvent> events = findings.events();
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).startTime().compareTo(events.get(i - 1).startTime()) >= 0,
                "Events should be sorted by start time");
        }
        
        // Check that we have events across different days
        long uniqueDays = events.stream()
            .map(FlaggedEvent::dayIndex)
            .distinct()
            .count();
        
        assertTrue(uniqueDays >= 1, "Should have events on at least 1 day");
    }

    @Test
    @DisplayName("High anomaly rate should produce detectable patterns")
    void testHighAnomalyRateDetection() {
        // Scenario: Patient with frequent anomalies (50% of beats)
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(10000)
            .anomalyRate(0.5)
            .randomSeed(42)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        // Calculate variance in RR intervals
        double mean = beats.stream()
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        double variance = beats.stream()
            .mapToDouble(beat -> {
                double diff = beat.rrIntervalMs() - mean;
                return diff * diff;
            })
            .average()
            .orElse(0.0);
        
        // High anomaly rate should produce high variance
        assertTrue(variance > 5000.0,
            String.format("High anomaly rate should produce high variance, got %.2f", variance));
    }

    @Test
    @DisplayName("Mock generators should support reproducible parallel development workflows")
    void testReproducibleWorkflow() {
        // Scenario: Two developers working on different modules with same test data
        long sharedSeed = 12345;
        Instant sharedStartTime = Instant.parse("2024-01-15T00:00:00Z");
        
        // Developer A: Working on ContextEnricher
        MockBeatTableGenerator devAGenerator = MockBeatTableGenerator.builder()
            .numberOfBeats(5000)
            .anomalyRate(0.05)
            .startTime(sharedStartTime)
            .randomSeed(sharedSeed)
            .build();
        
        List<BeatRecord> devABeats = devAGenerator.generate();
        
        // Developer B: Working on same module, different machine
        MockBeatTableGenerator devBGenerator = MockBeatTableGenerator.builder()
            .numberOfBeats(5000)
            .anomalyRate(0.05)
            .startTime(sharedStartTime)
            .randomSeed(sharedSeed)
            .build();
        
        List<BeatRecord> devBBeats = devBGenerator.generate();
        
        // Both developers should get identical data
        assertEquals(devABeats.size(), devBBeats.size());
        for (int i = 0; i < devABeats.size(); i++) {
            assertEquals(devABeats.get(i), devBBeats.get(i),
                "Beat at index " + i + " should be identical");
        }
    }

    @Test
    @DisplayName("Mock data should contain all required fields for downstream processing")
    void testDataCompletenessForDownstreamModules() {
        // Generate sample data
        MockBeatTableGenerator beatGen = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .build();
        
        List<BeatRecord> beats = beatGen.generate();
        
        // Verify all required fields are present and non-null
        for (BeatRecord beat : beats) {
            assertNotNull(beat.timestamp(), "timestamp required for ContextEnricher");
            assertNotNull(beat.rrIntervalMs(), "rrIntervalMs required for HRV calculation");
            assertNotNull(beat.qrsWidthMs(), "qrsWidthMs required for beat classification");
            assertNotNull(beat.rAmplitude(), "rAmplitude required for signal quality");
            assertNotNull(beat.qualityFlag(), "qualityFlag required for filtering");
            assertNotNull(beat.dayIndex(), "dayIndex required for temporal analysis");
        }
        
        // Test findings data completeness
        MockFindingsGenerator findingsGen = MockFindingsGenerator.builder()
            .numberOfEvents(5)
            .build();
        
        FindingsJson findings = findingsGen.generate();
        
        assertNotNull(findings.patientId(), "patientId required for Agent");
        assertNotNull(findings.recordingDays(), "recordingDays required for context");
        assertNotNull(findings.totalBeatsProcessed(), "totalBeatsProcessed required for stats");
        assertNotNull(findings.events(), "events required for Dashboard");
        assertNotNull(findings.summaryStats(), "summaryStats required for Agent");
        
        for (FlaggedEvent event : findings.events()) {
            assertNotNull(event.eventId(), "eventId required for tracking");
            assertNotNull(event.startTime(), "startTime required for timeline");
            assertNotNull(event.endTime(), "endTime required for duration");
            assertNotNull(event.durationSec(), "durationSec required for display");
            assertNotNull(event.beatsInvolved(), "beatsInvolved required for severity");
            assertNotNull(event.deviationScore(), "deviationScore required for ranking");
            assertNotNull(event.contextBucket(), "contextBucket required for clustering");
            assertNotNull(event.dayIndex(), "dayIndex required for timeline");
            assertNotNull(event.hourOfDay(), "hourOfDay required for temporal analysis");
            assertNotNull(event.sleepState(), "sleepState required for context");
        }
    }
}
