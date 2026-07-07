package holter.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 2 – Task 5.2: Pan-Tompkins R-peak detector.
 * <p>
 * Implements the classic Pan-Tompkins (1985) algorithm:
 * <ol>
 *   <li>Low-pass filter (already done by {@link ButterworthFilter})</li>
 *   <li>High-pass filter (ditto)</li>
 *   <li>Derivative filter to highlight QRS slopes</li>
 *   <li>Squaring to amplify large variations</li>
 *   <li>Moving-window integration (150 ms default)</li>
 *   <li>Adaptive threshold + refractory period peak-picking</li>
 * </ol>
 *
 * <p>Accepts a continuous array of <em>already-filtered</em> samples and returns
 * the sample indices of detected R-peaks.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * PanTompkinsDetector detector = new PanTompkinsDetector(360);
 * int[] peaks = detector.detect(filteredSamples);
 * }</pre>
 */
public class PanTompkinsDetector {

    private static final Logger logger = LoggerFactory.getLogger(PanTompkinsDetector.class);

    // -----------------------------------------------------------------------
    // Algorithm parameters
    // -----------------------------------------------------------------------

    private final int samplingRateHz;
    /** Integration window width in samples (default ≈ 150 ms). */
    private final int integrationWindowSamples;
    /** Minimum refractory period between two R-peaks (200 ms). */
    private final int refractorySamples;

    // Adaptive thresholds (initialised from first 2 s of signal)
    private double signalLevel = 0;
    private double noiseLevel  = 0;
    private double threshold1;   // primary threshold
    private double threshold2;   // secondary (T-wave guard)

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Create a detector for the given sampling rate with default Pan-Tompkins
     * parameters (integration window = 150 ms, refractory = 200 ms).
     */
    public PanTompkinsDetector(int samplingRateHz) {
        this(samplingRateHz, 150, 200);
    }

    /**
     * Create a detector with custom window and refractory settings.
     *
     * @param samplingRateHz           ADC sample rate in Hz
     * @param integrationWindowMs      Moving-average window width in ms
     * @param refractoryPeriodMs       Minimum inter-beat interval in ms
     */
    public PanTompkinsDetector(int samplingRateHz, int integrationWindowMs, int refractoryPeriodMs) {
        this.samplingRateHz             = samplingRateHz;
        this.integrationWindowSamples   = Math.max(1, (int) (integrationWindowMs * samplingRateHz / 1000.0));
        this.refractorySamples          = Math.max(1, (int) (refractoryPeriodMs  * samplingRateHz / 1000.0));
        logger.debug("PanTompkinsDetector: fs={} Hz, window={} samples, refractory={} samples",
                     samplingRateHz, integrationWindowSamples, refractorySamples);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Detect R-peaks in a filtered ECG signal.
     *
     * @param filtered  Pre-filtered ECG samples (output of {@link ButterworthFilter})
     * @return Array of sample indices where R-peaks were detected
     */
    public int[] detect(double[] filtered) {
        if (filtered == null || filtered.length < integrationWindowSamples * 2) {
            logger.warn("Signal too short for Pan-Tompkins detection ({} samples)", 
                        filtered == null ? 0 : filtered.length);
            return new int[0];
        }

        // Step 1: Derivative filter (5-point, Pan-Tompkins 1985)
        double[] deriv = derivative(filtered);

        // Step 2: Squaring
        double[] squared = square(deriv);

        // Step 3: Moving-window integration
        double[] integrated = movingWindowIntegrate(squared, integrationWindowSamples);

        // Step 4: Initialise thresholds from first 2 s
        initThresholds(integrated);

        // Step 5: Peak detection with adaptive thresholds
        return findPeaks(integrated, filtered);
    }

    // -----------------------------------------------------------------------
    // Algorithm steps
    // -----------------------------------------------------------------------

    /** 5-point derivative: y[n] = (−2x[n−2] − x[n−1] + x[n+1] + 2x[n+2]) / (8/fs) */
    private double[] derivative(double[] x) {
        double[] y = new double[x.length];
        for (int i = 2; i < x.length - 2; i++) {
            y[i] = (-2*x[i-2] - x[i-1] + x[i+1] + 2*x[i+2]) * samplingRateHz / 8.0;
        }
        return y;
    }

    private static double[] square(double[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) y[i] = x[i] * x[i];
        return y;
    }

    private static double[] movingWindowIntegrate(double[] x, int windowSize) {
        double[] y   = new double[x.length];
        double   sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += x[i];
            if (i >= windowSize) sum -= x[i - windowSize];
            y[i] = sum / windowSize;
        }
        return y;
    }

    /** Initialise signalLevel and noiseLevel from the first 2 s of the integrated signal. */
    private void initThresholds(double[] integrated) {
        int initSamples = Math.min(samplingRateHz * 2, integrated.length);
        double maxVal = 0;
        for (int i = 0; i < initSamples; i++) {
            if (integrated[i] > maxVal) maxVal = integrated[i];
        }
        signalLevel = maxVal * 0.25;
        noiseLevel  = maxVal * 0.1;
        updateThresholds();
    }

    private void updateThresholds() {
        threshold1 = noiseLevel + 0.25 * (signalLevel - noiseLevel);
        threshold2 = threshold1 * 0.5;
    }

    /**
     * Peak-picking pass over the integrated signal with adaptive thresholds
     * and a refractory guard window.
     */
    private int[] findPeaks(double[] integrated, double[] original) {
        List<Integer> peaks = new ArrayList<>();
        int lastPeakSample  = -refractorySamples;   // allow detection from the start

        // Simple local-maximum search with a 25 ms search window
        int searchRadiusSamples = Math.max(1, samplingRateHz / 40);  // 25 ms

        for (int i = searchRadiusSamples; i < integrated.length - searchRadiusSamples; i++) {
            // Refractory guard
            if (i - lastPeakSample < refractorySamples) continue;

            // Check if current sample is a local maximum
            boolean isMax = true;
            for (int j = i - searchRadiusSamples; j <= i + searchRadiusSamples; j++) {
                if (j != i && integrated[j] >= integrated[i]) { isMax = false; break; }
            }
            if (!isMax) continue;

            double peakVal = integrated[i];

            // Threshold decision
            if (peakVal > threshold1) {
                // Confirmed QRS peak – find closest peak in original signal
                int rPeak = refineInOriginal(original, i, searchRadiusSamples * 2);
                peaks.add(rPeak);
                signalLevel = 0.125 * peakVal + 0.875 * signalLevel;
                lastPeakSample = i;
            } else if (peakVal > threshold2) {
                // Possible QRS (T-wave or noise) – treat as noise unless in search-back phase
                noiseLevel = 0.125 * peakVal + 0.875 * noiseLevel;
            }

            updateThresholds();
        }

        logger.debug("Pan-Tompkins detected {} R-peaks in {} samples", peaks.size(), integrated.length);
        return peaks.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Refine the peak index by finding the actual maximum in the original
     * signal within ±{@code radius} samples of the integrated-signal peak.
     */
    private int refineInOriginal(double[] original, int centre, int radius) {
        int lo   = Math.max(0, centre - radius);
        int hi   = Math.min(original.length - 1, centre + radius);
        int best = centre;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int i = lo; i <= hi; i++) {
            if (original[i] > bestVal) { bestVal = original[i]; best = i; }
        }
        return best;
    }
}
