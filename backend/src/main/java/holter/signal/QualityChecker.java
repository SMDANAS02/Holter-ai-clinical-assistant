package holter.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module 2 – Task 5.4: Beat quality assessment.
 * <p>
 * Flags individual beats as noisy or artifact based on two criteria:
 * <ol>
 *   <li><b>RR-interval physiological bounds</b> – beats with RR &lt; 300 ms or
 *       RR &gt; 2 000 ms are considered implausible (equivalent to HR &lt;30 or
 *       HR &gt;200 bpm).</li>
 *   <li><b>RR-interval relative variability</b> – if a beat's RR interval
 *       deviates more than {@code maxRrRelativeChange} (default 50 %) from the
 *       preceding beat it is flagged (sudden large change suggests ectopic beat or
 *       mis-detection).</li>
 * </ol>
 *
 * <p>The class is intentionally stateless between calls so that it can be reused
 * across different signal segments.
 */
public class QualityChecker {

    private static final Logger logger = LoggerFactory.getLogger(QualityChecker.class);

    // -----------------------------------------------------------------------
    // Parameters
    // -----------------------------------------------------------------------

    /** Minimum physiologically plausible RR interval (ms) – HR ≤ 200 bpm. */
    private final double minRrMs;
    /** Maximum physiologically plausible RR interval (ms) – HR ≥ 30 bpm. */
    private final double maxRrMs;
    /**
     * Maximum allowed relative change from previous RR interval (0–1).
     * A value of 0.5 means the RR may change by at most ±50 % beat-to-beat.
     */
    private final double maxRrRelativeChange;
    /**
     * Minimum R-amplitude in mV.  Peaks below this are likely noise or missed
     * detections.
     */
    private final double minRAmplitudeMv;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** Create a checker with default clinical thresholds. */
    public QualityChecker() {
        this(300.0, 2000.0, 0.50, 0.1);
    }

    /**
     * Create a checker with custom thresholds.
     *
     * @param minRrMs              Min acceptable RR interval in ms
     * @param maxRrMs              Max acceptable RR interval in ms
     * @param maxRrRelativeChange  Max fractional change in RR interval (e.g. 0.5 = 50 %)
     * @param minRAmplitudeMv      Min acceptable R amplitude in mV
     */
    public QualityChecker(double minRrMs, double maxRrMs,
                          double maxRrRelativeChange, double minRAmplitudeMv) {
        this.minRrMs             = minRrMs;
        this.maxRrMs             = maxRrMs;
        this.maxRrRelativeChange = maxRrRelativeChange;
        this.minRAmplitudeMv     = minRAmplitudeMv;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Check whether a single beat meets quality criteria.
     *
     * @param rrIntervalMs     RR interval of the current beat in milliseconds
     * @param prevRrIntervalMs RR interval of the preceding beat (NaN if first beat)
     * @param rAmplitudeMv     R-peak amplitude in mV
     * @return {@code true} if the beat passes all quality checks, {@code false}
     *         if it should be flagged as noisy / artifact
     */
    public boolean isGoodQuality(double rrIntervalMs, double prevRrIntervalMs, double rAmplitudeMv) {
        // --- Physiological bounds ---
        if (rrIntervalMs < minRrMs || rrIntervalMs > maxRrMs) {
            logger.trace("RR out of bounds: {} ms", rrIntervalMs);
            return false;
        }

        // --- R-amplitude check ---
        if (rAmplitudeMv < minRAmplitudeMv) {
            logger.trace("R amplitude too small: {} mV", rAmplitudeMv);
            return false;
        }

        // --- Relative RR variability (only when we have a previous beat) ---
        if (!Double.isNaN(prevRrIntervalMs) && prevRrIntervalMs > 0) {
            double relChange = Math.abs(rrIntervalMs - prevRrIntervalMs) / prevRrIntervalMs;
            if (relChange > maxRrRelativeChange) {
                logger.trace("RR relative change too large: {:.2f}", relChange);
                return false;
            }
        }

        return true;
    }

    /**
     * Convenience overload for the first beat in a recording (no previous RR).
     *
     * @param rrIntervalMs  RR interval in ms
     * @param rAmplitudeMv  R amplitude in mV
     * @return {@code true} if the beat passes quality checks
     */
    public boolean isGoodQuality(double rrIntervalMs, double rAmplitudeMv) {
        return isGoodQuality(rrIntervalMs, Double.NaN, rAmplitudeMv);
    }

    /**
     * Bulk quality assessment across parallel arrays.  Returns a boolean array
     * of the same length where {@code true} = good quality.
     *
     * @param rrIntervals   RR intervals in ms (length N)
     * @param rAmplitudes   R amplitudes in mV (length N)
     * @return boolean[] quality flags
     */
    public boolean[] assessAll(double[] rrIntervals, double[] rAmplitudes) {
        if (rrIntervals.length != rAmplitudes.length) {
            throw new IllegalArgumentException("rrIntervals and rAmplitudes must have the same length");
        }
        boolean[] flags = new boolean[rrIntervals.length];
        double prev = Double.NaN;
        for (int i = 0; i < rrIntervals.length; i++) {
            flags[i] = isGoodQuality(rrIntervals[i], prev, rAmplitudes[i]);
            prev = rrIntervals[i];
        }
        long good = 0;
        for (boolean f : flags) if (f) good++;
        logger.debug("Quality assessment: {}/{} beats flagged as good", good, flags.length);
        return flags;
    }
}
