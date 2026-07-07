package holter.features;

import holter.schema.BeatRecord;
import holter.schema.EnrichedBeatRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Module 3: ContextEnricher – Feature Engineering.
 * <p>
 * Takes a {@code List<BeatRecord>} and produces a {@code List<EnrichedBeatRecord>}
 * by adding:
 * <ul>
 *   <li>{@code hourOfDay}   – fractional hour of day (0–23.99) derived from the beat
 *       timestamp (UTC)</li>
 *   <li>{@code sleepState}  – inferred state from heuristic time-of-day rules:
 *       {@code "sleep"}, {@code "transition"}, or {@code "awake"}</li>
 *   <li>{@code rollingSdnn} – SDNN over a configurable rolling window (default 5 min)</li>
 *   <li>{@code rollingRmssd}– RMSSD over the same window</li>
 *   <li>{@code rollingPnn50}– pNN50 over the same window</li>
 * </ul>
 */
public class ContextEnricher {

    private static final Logger logger = LoggerFactory.getLogger(ContextEnricher.class);

    // -----------------------------------------------------------------------
    // Sleep-state thresholds (heuristic)
    // -----------------------------------------------------------------------

    /** Hour at which the sleep period starts (22:00). */
    private static final double SLEEP_START_HOUR = 22.0;
    /** Hour at which the sleep period ends (06:00). */
    private static final double SLEEP_END_HOUR   = 6.0;
    /** Morning transition ends (08:00). */
    private static final double MORNING_TRANS_END  = 8.0;
    /** Evening transition starts (20:00). */
    private static final double EVENING_TRANS_START = 20.0;

    // -----------------------------------------------------------------------
    // HRV window parameters
    // -----------------------------------------------------------------------

    /** Rolling window duration in minutes for HRV calculations. */
    private final double windowMinutes;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** Default 5-minute rolling HRV window. */
    public ContextEnricher() { this(5.0); }

    /**
     * @param windowMinutes  Duration of the HRV rolling window in minutes.
     */
    public ContextEnricher(double windowMinutes) {
        if (windowMinutes <= 0) throw new IllegalArgumentException("windowMinutes must be positive");
        this.windowMinutes = windowMinutes;
        logger.debug("ContextEnricher: HRV window = {} min", windowMinutes);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Enrich a list of beat records with temporal and HRV context features.
     *
     * @param beats  Input beat table (must be sorted chronologically)
     * @return Enriched beat table ready for {@code BaselineModel}
     */
    public List<EnrichedBeatRecord> enrich(List<BeatRecord> beats) {
        if (beats == null || beats.isEmpty()) {
            logger.warn("ContextEnricher.enrich() called with empty beat list");
            return Collections.emptyList();
        }

        logger.info("Enriching {} beats with context features (window={} min)...",
                    beats.size(), windowMinutes);

        // Pre-compute HRV window for all beats
        double[] sdnn   = computeRollingSdnn  (beats);
        double[] rmssd  = computeRollingRmssd (beats);
        double[] pnn50  = computeRollingPnn50 (beats);

        List<EnrichedBeatRecord> enriched = new ArrayList<>(beats.size());

        for (int i = 0; i < beats.size(); i++) {
            BeatRecord b    = beats.get(i);
            double hourOfDay = toHourOfDay(b);
            String sleepState = inferSleepState(hourOfDay);

            enriched.add(EnrichedBeatRecord.fromBeatRecord(
                b,
                hourOfDay,
                sleepState,
                sdnn[i],
                rmssd[i],
                pnn50[i]
            ));
        }

        logger.info("Context enrichment complete: {} EnrichedBeatRecords", enriched.size());
        return enriched;
    }

    // -----------------------------------------------------------------------
    // Temporal helpers
    // -----------------------------------------------------------------------

    /** Extract fractional hour of day in UTC from a beat's timestamp. */
    public static double toHourOfDay(BeatRecord beat) {
        ZonedDateTime zdt = ZonedDateTime.ofInstant(beat.timestamp(), ZoneOffset.UTC);
        return zdt.getHour() + zdt.getMinute() / 60.0 + zdt.getSecond() / 3600.0;
    }

    /**
     * Infer sleep state from hour of day using hard-coded heuristics.
     *
     * <ul>
     *   <li><b>sleep</b>: 22:00 – 06:00</li>
     *   <li><b>transition</b>: 06:00 – 08:00 (waking up) and 20:00 – 22:00 (winding down)</li>
     *   <li><b>awake</b>: 08:00 – 20:00</li>
     * </ul>
     */
    public static String inferSleepState(double hourOfDay) {
        if (hourOfDay >= SLEEP_START_HOUR || hourOfDay < SLEEP_END_HOUR) return "sleep";
        if (hourOfDay < MORNING_TRANS_END || hourOfDay >= EVENING_TRANS_START) return "transition";
        return "awake";
    }

    // -----------------------------------------------------------------------
    // HRV rolling window calculations
    // -----------------------------------------------------------------------

    /**
     * Compute rolling SDNN (standard deviation of RR intervals) for each beat
     * using a time-based window of {@link #windowMinutes}.
     */
    private double[] computeRollingSdnn(List<BeatRecord> beats) {
        double[] result = new double[beats.size()];
        long windowMs   = (long)(windowMinutes * 60_000);

        for (int i = 0; i < beats.size(); i++) {
            long endMs   = beats.get(i).timestamp().toEpochMilli();
            long startMs = endMs - windowMs;

            // Collect RR values in window ending at beat i
            List<Double> rr = new ArrayList<>();
            for (int j = i; j >= 0; j--) {
                long t = beats.get(j).timestamp().toEpochMilli();
                if (t < startMs) break;
                rr.add(beats.get(j).rrIntervalMs());
            }

            result[i] = rr.size() < 2 ? 0.0 : stddev(rr);
        }
        return result;
    }

    /**
     * Compute rolling RMSSD (root mean square of successive RR differences).
     */
    private double[] computeRollingRmssd(List<BeatRecord> beats) {
        double[] result = new double[beats.size()];
        long windowMs   = (long)(windowMinutes * 60_000);

        for (int i = 0; i < beats.size(); i++) {
            long endMs   = beats.get(i).timestamp().toEpochMilli();
            long startMs = endMs - windowMs;

            List<Double> rr = new ArrayList<>();
            for (int j = i; j >= 0; j--) {
                long t = beats.get(j).timestamp().toEpochMilli();
                if (t < startMs) break;
                rr.add(beats.get(j).rrIntervalMs());
            }

            if (rr.size() < 2) { result[i] = 0.0; continue; }

            double sumSqDiffs = 0;
            for (int k = 0; k < rr.size() - 1; k++) {
                double diff = rr.get(k) - rr.get(k + 1);
                sumSqDiffs += diff * diff;
            }
            result[i] = Math.sqrt(sumSqDiffs / (rr.size() - 1));
        }
        return result;
    }

    /**
     * Compute rolling pNN50 (percentage of successive RR differences &gt;50 ms).
     */
    private double[] computeRollingPnn50(List<BeatRecord> beats) {
        double[] result = new double[beats.size()];
        long windowMs   = (long)(windowMinutes * 60_000);

        for (int i = 0; i < beats.size(); i++) {
            long endMs   = beats.get(i).timestamp().toEpochMilli();
            long startMs = endMs - windowMs;

            List<Double> rr = new ArrayList<>();
            for (int j = i; j >= 0; j--) {
                long t = beats.get(j).timestamp().toEpochMilli();
                if (t < startMs) break;
                rr.add(beats.get(j).rrIntervalMs());
            }

            if (rr.size() < 2) { result[i] = 0.0; continue; }

            int count50 = 0;
            for (int k = 0; k < rr.size() - 1; k++) {
                if (Math.abs(rr.get(k) - rr.get(k + 1)) > 50.0) count50++;
            }
            result[i] = 100.0 * count50 / (rr.size() - 1);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Statistical helpers
    // -----------------------------------------------------------------------

    private static double mean(List<Double> vals) {
        double sum = 0;
        for (double v : vals) sum += v;
        return sum / vals.size();
    }

    private static double stddev(List<Double> vals) {
        double mu = mean(vals);
        double sumSq = 0;
        for (double v : vals) sumSq += (v - mu) * (v - mu);
        return Math.sqrt(sumSq / vals.size());
    }
}
