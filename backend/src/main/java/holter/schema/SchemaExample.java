package holter.schema;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Example demonstrating how schema contracts flow through the pipeline.
 * This class is for documentation purposes only.
 */
public class SchemaExample {

    /**
     * Demonstrates the complete data transformation pipeline using schema contracts.
     */
    public static void demonstratePipelineFlow() {
        // Step 1: Module 1 (HolterStream) + Module 2 (BeatExtractor) produce BeatRecord
        BeatRecord baseRecord = new BeatRecord(
            Instant.parse("2024-01-05T03:15:22Z"),
            852.0,    // rrIntervalMs
            88.0,     // qrsWidthMs
            1.25,     // rAmplitude
            true,     // qualityFlag
            4         // dayIndex
        );

        // Step 2: Module 3 (ContextEnricher) enriches with context features
        EnrichedBeatRecord enrichedRecord = EnrichedBeatRecord.fromBeatRecord(
            baseRecord,
            3.256,     // hourOfDay
            "sleep",   // sleepState
            42.5,      // rollingSdnn
            38.2,      // rollingRmssd
            22.8       // rollingPnn50
        );

        // Step 3: Module 4 (BaselineModel) adds deviation scoring
        ScoredBeatRecord scoredRecord = ScoredBeatRecord.fromEnrichedBeatRecord(
            enrichedRecord,
            5.2,              // deviationScore (z-score)
            "sleep_night_3"   // contextBucket
        );

        // Step 4: Module 5 (AnomalyDetector) aggregates anomalous sequences into events
        FlaggedEvent event = new FlaggedEvent(
            "550e8400-e29b-41d4-a716-446655440000",
            Instant.parse("2024-01-05T03:15:00Z"),
            Instant.parse("2024-01-05T03:15:30Z"),
            30.0,             // durationSec
            35,               // beatsInvolved
            5.2,              // deviationScore (avg over sequence)
            "sleep_night_3",  // contextBucket
            4,                // dayIndex
            3.25,             // hourOfDay
            "sleep"           // sleepState
        );

        // Step 5: Aggregate all events with summary statistics
        List<FlaggedEvent> events = new ArrayList<>();
        events.add(event);

        SummaryStats summaryStats = new SummaryStats(
            1,                // totalEvents
            5.2,              // avgDeviationScore
            "sleep_night_3"   // mostCommonContext
        );

        // Step 6: Daily aggregate statistics (one per recording day)
        List<DailySummary> dailySummaries = new ArrayList<>();
        dailySummaries.add(new DailySummary(
            0,                // dayIndex (day 0)
            68.5,             // avgHrBpm
            52.0,             // minHrBpm
            95.0,             // maxHrBpm
            45.2,             // avgSdnn
            38.7,             // avgRmssd
            7.5,              // sleepHoursEstimate
            0                 // eventsThisDay
        ));
        dailySummaries.add(new DailySummary(
            4,                // dayIndex (day 4 - matches event above)
            72.1,             // avgHrBpm
            55.0,             // minHrBpm
            102.0,            // maxHrBpm
            42.8,             // avgSdnn
            36.2,             // avgRmssd
            6.8,              // sleepHoursEstimate
            1                 // eventsThisDay (the event above occurred on day 4)
        ));

        FindingsJson findings = new FindingsJson(
            "patient-001",
            7,                // recordingDays
            95432L,           // totalBeatsProcessed
            events,
            summaryStats,
            dailySummaries,   // NEW: daily aggregate statistics
            null              // external_report_summary not available in example data
        );

        // This FindingsJson object is now ready for consumption by:
        // - Agent Layer: Generate clinical narrative
        // - Dashboard: Visualize timeline and event details
        System.out.println("Pipeline complete! Generated findings for patient: " 
            + findings.patientId());
        System.out.println("Total events detected: " + findings.summaryStats().totalEvents());
    }

    /**
     * Demonstrates handling the zero-event case.
     */
    public static void demonstrateZeroEventCase() {
        SummaryStats emptyStats = new SummaryStats(0, 0.0, "none");
        
        // Empty daily summaries for backward compatibility demonstration
        List<DailySummary> dailySummaries = new ArrayList<>();
        dailySummaries.add(new DailySummary(
            0, 70.0, 58.0, 92.0, 48.0, 40.0, 7.2, 0
        ));
        
        FindingsJson noAnomalies = new FindingsJson(
            "patient-002",
            30,               // recordingDays
            432000L,          // totalBeatsProcessed
            List.of(),        // empty events list
            emptyStats,
            dailySummaries,   // NEW: daily summaries even when no events
            null
        );

        if (noAnomalies.events().isEmpty()) {
            System.out.println("No anomalies detected above threshold for patient: " 
                + noAnomalies.patientId());
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Schema Contract Example ===\n");
        demonstratePipelineFlow();
        System.out.println();
        demonstrateZeroEventCase();
    }
}
