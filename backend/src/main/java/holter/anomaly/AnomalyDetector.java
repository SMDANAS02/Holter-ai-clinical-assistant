package holter.anomaly;

import holter.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Module 5: AnomalyDetector – Event Flagging.
 * <p>
 * Scans a {@code List<ScoredBeatRecord>} for contiguous sequences of beats whose
 * {@code deviationScore} exceeds a configurable threshold, merges nearby events,
 * and returns a fully-populated {@link FindingsJson}.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Scan for runs where {@code deviationScore ≥ threshold}.</li>
 *   <li>For each run, create a {@link FlaggedEvent} (UUID, times, beats, avg score …).</li>
 *   <li>Merge adjacent events whose gap is ≤ {@code gapToleranceSec}.</li>
 *   <li>Compute summary statistics and wrap in {@link FindingsJson}.</li>
 * </ol>
 */
public class AnomalyDetector {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetector.class);

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /** Default deviation score threshold. */
    public static final double DEFAULT_THRESHOLD = 3.0;
    /** Default gap tolerance for event merging (seconds). */
    public static final double DEFAULT_GAP_TOLERANCE_SEC = 5.0;

    private final double threshold;
    private final double gapToleranceSec;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public AnomalyDetector() {
        this(DEFAULT_THRESHOLD, DEFAULT_GAP_TOLERANCE_SEC);
    }

    /**
     * @param threshold         Minimum deviation score to flag a beat
     * @param gapToleranceSec   Maximum gap (seconds) between two events before they are merged
     */
    public AnomalyDetector(double threshold, double gapToleranceSec) {
        this.threshold       = threshold;
        this.gapToleranceSec = gapToleranceSec;
        logger.debug("AnomalyDetector: threshold={}, gapTolerance={} s", threshold, gapToleranceSec);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Detect anomalous events in the scored beat table and return findings.
     *
     * @param beats             Scored beat list (sorted chronologically)
     * @param patientId         Patient identifier for the FindingsJson
     * @param recordingDays     Number of recording days
     * @return Populated {@link FindingsJson} ready for downstream use
     */
    public FindingsJson detect(List<ScoredBeatRecord> beats, String patientId, int recordingDays) {
        logger.info("AnomalyDetector: scanning {} beats (threshold={})", beats.size(), threshold);

        // Step 1: Find contiguous anomalous runs
        List<int[]> runs = findAnomalousRuns(beats);
        logger.info("Found {} raw anomalous runs", runs.size());

        // Step 2: Convert runs to FlaggedEvents
        List<FlaggedEvent> events = new ArrayList<>();
        for (int[] run : runs) {
            FlaggedEvent event = createEvent(beats, run[0], run[1]);
            events.add(event);
        }

        // Step 3: Merge nearby events
        events = mergeEvents(events);
        logger.info("After merging: {} events", events.size());

        // Step 4: Compute summary statistics
        SummaryStats stats = computeSummaryStats(events);

        // Step 5: Assemble FindingsJson
        FindingsJson findings = new FindingsJson(
            patientId,
            recordingDays,
            (long) beats.size(),
            events,
            stats,
            null,  // dailySummaries will be added by DailySummaryCalculator in HolterPipeline
            null   // external_report_summary is not part of anomaly detection output
        );

        logger.info("AnomalyDetector complete: {} events, avgScore={:.2f}",
                    stats.totalEvents(), stats.avgDeviationScore());
        return findings;
    }

    // -----------------------------------------------------------------------
    // Step 1: Find anomalous runs
    // -----------------------------------------------------------------------

    /**
     * Returns a list of [startIdx, endIdx] (inclusive) pairs where beats have
     * deviation score ≥ threshold.
     */
    private List<int[]> findAnomalousRuns(List<ScoredBeatRecord> beats) {
        List<int[]> runs = new ArrayList<>();
        int n = beats.size();
        int i = 0;
        while (i < n) {
            if (beats.get(i).deviationScore() >= threshold) {
                int start = i;
                while (i < n && beats.get(i).deviationScore() >= threshold) i++;
                runs.add(new int[]{start, i - 1});
            } else {
                i++;
            }
        }
        return runs;
    }

    // -----------------------------------------------------------------------
    // Step 2: Create FlaggedEvent from a run
    // -----------------------------------------------------------------------

    private FlaggedEvent createEvent(List<ScoredBeatRecord> beats, int startIdx, int endIdx) {
        ScoredBeatRecord first = beats.get(startIdx);
        ScoredBeatRecord last  = beats.get(endIdx);

        java.time.Instant startTime = first.timestamp();
        java.time.Instant endTime   = last.timestamp();
        double durationSec = (endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000.0;
        int beatsInvolved  = endIdx - startIdx + 1;

        // Average deviation score over the run
        double avgScore = 0;
        String dominantBucket = first.contextBucket();
        Map<String, Integer> bucketCounts = new HashMap<>();
        for (int i = startIdx; i <= endIdx; i++) {
            ScoredBeatRecord b = beats.get(i);
            avgScore += b.deviationScore();
            bucketCounts.merge(b.contextBucket(), 1, Integer::sum);
        }
        avgScore /= beatsInvolved;
        // Find dominant context bucket in run
        for (var e : bucketCounts.entrySet()) {
            if (e.getValue() > bucketCounts.getOrDefault(dominantBucket, 0)) {
                dominantBucket = e.getKey();
            }
        }

        return new FlaggedEvent(
            UUID.randomUUID().toString(),
            startTime,
            endTime,
            durationSec,
            beatsInvolved,
            avgScore,
            dominantBucket,
            first.dayIndex(),
            first.hourOfDay(),
            first.sleepState()
        );
    }

    // -----------------------------------------------------------------------
    // Step 3: Merge nearby events
    // -----------------------------------------------------------------------

    private List<FlaggedEvent> mergeEvents(List<FlaggedEvent> events) {
        if (events.size() <= 1) return events;

        List<FlaggedEvent> merged = new ArrayList<>();
        FlaggedEvent current = events.get(0);

        for (int i = 1; i < events.size(); i++) {
            FlaggedEvent next = events.get(i);
            double gapSec = (next.startTime().toEpochMilli() - current.endTime().toEpochMilli()) / 1000.0;

            if (gapSec <= gapToleranceSec) {
                // Merge: extend current event
                double newDuration = (next.endTime().toEpochMilli() - current.startTime().toEpochMilli()) / 1000.0;
                int newBeats = current.beatsInvolved() + next.beatsInvolved();
                double newScore = (current.deviationScore() * current.beatsInvolved()
                    + next.deviationScore() * next.beatsInvolved()) / newBeats;

                current = new FlaggedEvent(
                    current.eventId(),
                    current.startTime(),
                    next.endTime(),
                    newDuration,
                    newBeats,
                    newScore,
                    current.contextBucket(),
                    current.dayIndex(),
                    current.hourOfDay(),
                    current.sleepState()
                );
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    // -----------------------------------------------------------------------
    // Step 4: Summary statistics
    // -----------------------------------------------------------------------

    private SummaryStats computeSummaryStats(List<FlaggedEvent> events) {
        if (events.isEmpty()) {
            return new SummaryStats(0, 0.0, "none");
        }

        double avgScore = events.stream()
            .mapToDouble(FlaggedEvent::deviationScore)
            .average()
            .orElse(0.0);

        // Most common context bucket
        Map<String, Long> freq = new HashMap<>();
        for (FlaggedEvent e : events) freq.merge(e.contextBucket(), 1L, Long::sum);
        String mostCommon = freq.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");

        return new SummaryStats(events.size(), avgScore, mostCommon);
    }
}
