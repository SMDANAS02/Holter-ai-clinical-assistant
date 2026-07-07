package holter.mock;

import holter.schema.BeatRecord;
import holter.schema.FindingsJson;
import holter.schema.FlaggedEvent;

import java.time.Instant;
import java.util.List;

/**
 * Practical examples demonstrating how to use mock data generators
 * for parallel development and testing.
 * 
 * This is NOT a test class - it's a reference for developers.
 */
public class MockGeneratorsExample {

    /**
     * Example 1: Generate simple beat data for ContextEnricher development
     */
    public static void example1_BasicBeatGeneration() {
        System.out.println("=== Example 1: Basic Beat Generation ===");
        
        // Generate 1000 beats with 5% anomaly rate
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(1000)
            .anomalyRate(0.05)
            .startTime(Instant.parse("2024-01-01T00:00:00Z"))
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        System.out.println("Generated " + beats.size() + " beats");
        System.out.println("First beat: " + beats.get(0));
        System.out.println("Last beat: " + beats.get(beats.size() - 1));
    }

    /**
     * Example 2: Generate beats with realistic circadian rhythm
     */
    public static void example2_CircadianRhythm() {
        System.out.println("\n=== Example 2: Circadian Rhythm Generation ===");
        
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(100000) // Enough for ~24 hours
            .anomalyRate(0.0)
            .startTime(Instant.parse("2024-01-01T00:00:00Z"))
            .build();
        
        List<BeatRecord> beats = generator.generateWithCircadianRhythm();
        
        // Analyze sleep vs awake heart rates
        double sleepAvgRR = beats.stream()
            .filter(beat -> {
                int hour = beat.timestamp().atZone(java.time.ZoneOffset.UTC).getHour();
                return hour >= 0 && hour < 6;
            })
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        double awakeAvgRR = beats.stream()
            .filter(beat -> {
                int hour = beat.timestamp().atZone(java.time.ZoneOffset.UTC).getHour();
                return hour >= 10 && hour < 18;
            })
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        System.out.println("Sleep hours avg RR: " + String.format("%.2f ms", sleepAvgRR));
        System.out.println("Awake hours avg RR: " + String.format("%.2f ms", awakeAvgRR));
        System.out.println("Sleep HR is " + String.format("%.1f%%", 
            ((sleepAvgRR - awakeAvgRR) / awakeAvgRR * 100)) + " lower");
    }

    /**
     * Example 3: Generate findings for Dashboard development
     */
    public static void example3_GenerateFindings() {
        System.out.println("\n=== Example 3: Generate Findings ===");
        
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(15)
            .patientId("patient-001")
            .recordingDays(7)
            .totalBeatsProcessed(100000L)
            .startTime(Instant.parse("2024-01-01T00:00:00Z"))
            .build();
        
        FindingsJson findings = generator.generate();
        
        System.out.println("Patient: " + findings.patientId());
        System.out.println("Recording days: " + findings.recordingDays());
        System.out.println("Total beats: " + findings.totalBeatsProcessed());
        System.out.println("Events detected: " + findings.events().size());
        System.out.println("Avg deviation score: " + 
            String.format("%.2f", findings.summaryStats().avgDeviationScore()));
        System.out.println("Most common context: " + 
            findings.summaryStats().mostCommonContext());
        
        // Show first event
        if (!findings.events().isEmpty()) {
            FlaggedEvent firstEvent = findings.events().get(0);
            System.out.println("\nFirst event details:");
            System.out.println("  ID: " + firstEvent.eventId());
            System.out.println("  Time: " + firstEvent.startTime());
            System.out.println("  Duration: " + String.format("%.1f sec", firstEvent.durationSec()));
            System.out.println("  Beats: " + firstEvent.beatsInvolved());
            System.out.println("  Deviation: " + String.format("%.2f", firstEvent.deviationScore()));
            System.out.println("  Context: " + firstEvent.contextBucket());
        }
    }

    /**
     * Example 4: Generate clustered events for timeline testing
     */
    public static void example4_ClusteredEvents() {
        System.out.println("\n=== Example 4: Clustered Events ===");
        
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(30)
            .patientId("patient-clustered-001")
            .recordingDays(7)
            .totalBeatsProcessed(100000L)
            .build();
        
        FindingsJson findings = generator.generateWithClustering();
        
        System.out.println("Generated " + findings.events().size() + " events in clusters");
        
        // Count events per day
        int[] eventsPerDay = new int[7];
        for (FlaggedEvent event : findings.events()) {
            eventsPerDay[event.dayIndex()]++;
        }
        
        System.out.println("Events per day:");
        for (int day = 0; day < 7; day++) {
            System.out.println("  Day " + day + ": " + eventsPerDay[day] + " events");
        }
    }

    /**
     * Example 5: Test scenario with no anomalies
     */
    public static void example5_NoAnomalies() {
        System.out.println("\n=== Example 5: No Anomalies Detected ===");
        
        MockFindingsGenerator generator = MockFindingsGenerator.builder()
            .numberOfEvents(0)
            .patientId("patient-normal-001")
            .recordingDays(30)
            .totalBeatsProcessed(300000L)
            .build();
        
        FindingsJson findings = generator.generate();
        
        System.out.println("Patient: " + findings.patientId());
        System.out.println("Recording days: " + findings.recordingDays());
        System.out.println("Total beats: " + findings.totalBeatsProcessed());
        System.out.println("Events detected: " + findings.events().size());
        System.out.println("Summary: No anomalies detected above threshold");
    }

    /**
     * Example 6: Reproducible data generation for testing
     */
    public static void example6_ReproducibleData() {
        System.out.println("\n=== Example 6: Reproducible Data Generation ===");
        
        long seed = 42;
        
        // First generation
        MockBeatTableGenerator gen1 = MockBeatTableGenerator.builder()
            .numberOfBeats(10)
            .randomSeed(seed)
            .build();
        List<BeatRecord> beats1 = gen1.generate();
        
        // Second generation with same seed
        MockBeatTableGenerator gen2 = MockBeatTableGenerator.builder()
            .numberOfBeats(10)
            .randomSeed(seed)
            .build();
        List<BeatRecord> beats2 = gen2.generate();
        
        // Verify they're identical
        boolean identical = true;
        for (int i = 0; i < beats1.size(); i++) {
            if (!beats1.get(i).equals(beats2.get(i))) {
                identical = false;
                break;
            }
        }
        
        System.out.println("Generated data twice with seed " + seed);
        System.out.println("Data is identical: " + identical);
        System.out.println("First beat RR interval: " + beats1.get(0).rrIntervalMs() + " ms");
    }

    /**
     * Run all examples
     */
    public static void main(String[] args) {
        example1_BasicBeatGeneration();
        example2_CircadianRhythm();
        example3_GenerateFindings();
        example4_ClusteredEvents();
        example5_NoAnomalies();
        example6_ReproducibleData();
        
        System.out.println("\n=== All examples completed ===");
    }
}
