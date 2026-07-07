package holter.baseline;

import holter.features.ContextEnricher;
import holter.schema.EnrichedBeatRecord;
import holter.schema.ScoredBeatRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 4: BaselineModel – Personalized Context-Aware Baseline Learning.
 * <p>
 * Learns a per-patient, per-context-bucket Gaussian baseline from the enriched
 * beat table and uses it to score each beat with a <em>deviation score</em>
 * (multi-feature z-score).
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Bucket each beat by its {@code contextBucket} string (see
 *       {@link #computeContextBucket(EnrichedBeatRecord)}).</li>
 *   <li>For each bucket with ≥ {@code minBucketSize} beats, fit a Gaussian
 *       (mean + stddev) over three features: RR interval, SDNN, RMSSD.</li>
 *   <li>For sparse buckets fall back to the global baseline.</li>
 *   <li>Score each beat as the Euclidean z-score across the three features.</li>
 * </ol>
 */
public class BaselineModel {

    private static final Logger logger = LoggerFactory.getLogger(BaselineModel.class);

    // -----------------------------------------------------------------------
    // Inner record for per-bucket Gaussian parameters
    // -----------------------------------------------------------------------

    record BucketStats(
        double rrMean, double rrStd,
        double sdnnMean, double sdnnStd,
        double rmssdMean, double rmssdStd,
        int count
    ) {}

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /** Minimum number of beats required to fit a per-bucket baseline. */
    private final int minBucketSize;

    // -----------------------------------------------------------------------
    // State (populated by fit())
    // -----------------------------------------------------------------------

    private final Map<String, BucketStats> bucketStats = new HashMap<>();
    private BucketStats globalStats = null;
    private boolean fitted = false;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public BaselineModel() { this(10); }

    /** @param minBucketSize  Min beats per bucket before using global fallback. */
    public BaselineModel(int minBucketSize) {
        this.minBucketSize = minBucketSize;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Fit the baseline model on the enriched beat table.
     * Call this once before {@link #score(List)}.
     *
     * @param beats  Enriched beat list
     */
    public void fit(List<EnrichedBeatRecord> beats) {
        logger.info("Fitting BaselineModel on {} beats...", beats.size());

        // Group by context bucket
        Map<String, List<EnrichedBeatRecord>> groups = beats.stream()
            .collect(Collectors.groupingBy(b -> computeContextBucket(b)));

        // Fit per-bucket stats
        for (var entry : groups.entrySet()) {
            String bucket        = entry.getKey();
            List<EnrichedBeatRecord> group = entry.getValue();
            if (group.size() >= minBucketSize) {
                bucketStats.put(bucket, computeStats(group));
                logger.debug("Bucket '{}': {} beats fitted", bucket, group.size());
            } else {
                logger.debug("Bucket '{}': only {} beats – will use global fallback", bucket, group.size());
            }
        }

        // Fit global fallback
        globalStats = computeStats(beats);
        fitted = true;

        logger.info("BaselineModel fitted: {} context buckets, global baseline from {} beats",
                    bucketStats.size(), beats.size());
    }

    /**
     * Score each beat against its context-bucket baseline (or global if sparse).
     *
     * @param beats  Enriched beats (must come from the same or similar recording)
     * @return List of {@link ScoredBeatRecord} with {@code deviationScore} and
     *         {@code contextBucket} filled in
     * @throws IllegalStateException if {@link #fit} has not been called yet
     */
    public List<ScoredBeatRecord> score(List<EnrichedBeatRecord> beats) {
        if (!fitted) throw new IllegalStateException("Call fit() before score()");

        List<ScoredBeatRecord> scored = new ArrayList<>(beats.size());
        for (EnrichedBeatRecord b : beats) {
            String bucket = computeContextBucket(b);
            BucketStats stats = bucketStats.getOrDefault(bucket, globalStats);
            double dev = computeDeviationScore(b, stats);
            scored.add(ScoredBeatRecord.fromEnrichedBeatRecord(b, dev, bucket));
        }

        logger.info("BaselineModel scored {} beats", scored.size());
        return scored;
    }

    /**
     * Convenience method: fit then score on the same dataset (for single-pass usage).
     */
    public List<ScoredBeatRecord> fitAndScore(List<EnrichedBeatRecord> beats) {
        fit(beats);
        return score(beats);
    }

    // -----------------------------------------------------------------------
    // Context bucketing
    // -----------------------------------------------------------------------

    /**
     * Generate the context bucket string from a beat's sleep state and hour.
     * Format: {@code "{sleepState}_{hourBucket}"}, e.g. {@code "sleep_night_2"},
     * {@code "awake_afternoon_14"}.
     */
    public static String computeContextBucket(EnrichedBeatRecord beat) {
        String sleepState = beat.sleepState() != null ? beat.sleepState() : "awake";
        int hour = (int) Math.floor(beat.hourOfDay());
        String timePart = hourToTimePart(hour);
        return sleepState + "_" + timePart + "_" + hour;
    }

    /** Map hour 0-23 to a descriptive time-of-day label. */
    private static String hourToTimePart(int hour) {
        if (hour >= 0  && hour < 6)  return "night";
        if (hour >= 6  && hour < 12) return "morning";
        if (hour >= 12 && hour < 18) return "afternoon";
        return "evening";
    }

    // -----------------------------------------------------------------------
    // Statistics and scoring
    // -----------------------------------------------------------------------

    private static BucketStats computeStats(List<EnrichedBeatRecord> group) {
        double[] rr    = group.stream().mapToDouble(EnrichedBeatRecord::rrIntervalMs).toArray();
        double[] sdnn  = group.stream().mapToDouble(EnrichedBeatRecord::rollingSdnn).toArray();
        double[] rmssd = group.stream().mapToDouble(EnrichedBeatRecord::rollingRmssd).toArray();

        return new BucketStats(
            mean(rr),   stddev(rr,   mean(rr)),
            mean(sdnn), stddev(sdnn, mean(sdnn)),
            mean(rmssd),stddev(rmssd,mean(rmssd)),
            group.size()
        );
    }

    /**
     * Euclidean z-score across three features.
     * Each feature contributes one standardised dimension.
     * The overall deviation is the L2-norm of the feature z-scores.
     */
    private static double computeDeviationScore(EnrichedBeatRecord b, BucketStats s) {
        double zRr    = zscore(b.rrIntervalMs(), s.rrMean(),   s.rrStd());
        double zSdnn  = zscore(b.rollingSdnn(),  s.sdnnMean(), s.sdnnStd());
        double zRmssd = zscore(b.rollingRmssd(), s.rmssdMean(),s.rmssdStd());
        return Math.sqrt(zRr * zRr + zSdnn * zSdnn + zRmssd * zRmssd);
    }

    private static double zscore(double value, double mean, double std) {
        if (std < 1e-9) return 0.0;   // flat distribution – no deviation
        return (value - mean) / std;
    }

    private static double mean(double[] vals) {
        double sum = 0;
        for (double v : vals) sum += v;
        return vals.length == 0 ? 0 : sum / vals.length;
    }

    private static double stddev(double[] vals, double mu) {
        if (vals.length < 2) return 0.0;
        double sumSq = 0;
        for (double v : vals) sumSq += (v - mu) * (v - mu);
        return Math.sqrt(sumSq / vals.length);
    }
}
