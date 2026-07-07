package holter.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module 2 – Task 5.1: Butterworth bandpass filter for ECG signal conditioning.
 * <p>
 * Implements a 2nd-order Butterworth bandpass filter using cascaded biquad sections
 * (Direct Form II).  The default passband is <b>0.5 – 50 Hz</b>, which is the
 * clinical standard for diagnostic-quality ECG (IEC 60601-2-25).
 *
 * <p>Coefficients are pre-computed at construction time for the configured
 * {@code samplingRateHz}, {@code lowCutHz}, and {@code highCutHz}.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * ButterworthFilter filter = new ButterworthFilter(360, 0.5, 50.0);
 * double[] filtered = filter.apply(rawChunk);
 * }</pre>
 */
public class ButterworthFilter {

    private static final Logger logger = LoggerFactory.getLogger(ButterworthFilter.class);

    // -----------------------------------------------------------------------
    // Filter coefficients (biquad Direct Form II)
    // -----------------------------------------------------------------------

    // High-pass section (removes baseline wander below lowCutHz)
    private final double[] hpB;  // b0, b1, b2
    private final double[] hpA;  // a1, a2  (a0 = 1 normalised)
    private double hpW1 = 0, hpW2 = 0;  // delay elements

    // Low-pass section (removes EMG noise above highCutHz)
    private final double[] lpB;
    private final double[] lpA;
    private double lpW1 = 0, lpW2 = 0;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Create a bandpass filter with the standard ECG passband.
     *
     * @param samplingRateHz  ADC sample rate in Hz (e.g. 360 for MIT-BIH)
     */
    public ButterworthFilter(int samplingRateHz) {
        this(samplingRateHz, 0.5, 50.0);
    }

    /**
     * Create a bandpass filter with custom cutoff frequencies.
     *
     * @param samplingRateHz  ADC sample rate in Hz
     * @param lowCutHz        High-pass corner frequency (Hz); removes baseline wander
     * @param highCutHz       Low-pass corner frequency (Hz); removes high-freq noise
     */
    public ButterworthFilter(int samplingRateHz, double lowCutHz, double highCutHz) {
        if (lowCutHz <= 0 || highCutHz <= 0 || lowCutHz >= highCutHz) {
            throw new IllegalArgumentException(
                "Invalid cutoff frequencies: lowCut=" + lowCutHz + " highCut=" + highCutHz);
        }
        if (highCutHz >= samplingRateHz / 2.0) {
            throw new IllegalArgumentException(
                "highCutHz must be less than Nyquist (" + (samplingRateHz / 2.0) + " Hz)");
        }

        double[] hp = designHighPass(samplingRateHz, lowCutHz);
        double[] lp = designLowPass (samplingRateHz, highCutHz);
        hpB = new double[]{hp[0], hp[1], hp[2]};
        hpA = new double[]{hp[3], hp[4]};
        lpB = new double[]{lp[0], lp[1], lp[2]};
        lpA = new double[]{lp[3], lp[4]};

        logger.debug("ButterworthFilter: fs={} Hz, hp={} Hz, lp={} Hz", samplingRateHz, lowCutHz, highCutHz);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Apply the bandpass filter to a chunk of samples.
     *
     * @param samples  Input raw samples (modified IN-PLACE for efficiency)
     * @return The same array with filtered values (mV or ADU)
     */
    public double[] apply(double[] samples) {
        if (samples == null || samples.length == 0) return samples;

        // Pass 1: high-pass (cascade Direct Form II)
        for (int i = 0; i < samples.length; i++) {
            double x  = samples[i];
            double w  = x - hpA[0] * hpW1 - hpA[1] * hpW2;
            samples[i] = hpB[0] * w + hpB[1] * hpW1 + hpB[2] * hpW2;
            hpW2 = hpW1;
            hpW1 = w;
        }

        // Pass 2: low-pass
        for (int i = 0; i < samples.length; i++) {
            double x  = samples[i];
            double w  = x - lpA[0] * lpW1 - lpA[1] * lpW2;
            samples[i] = lpB[0] * w + lpB[1] * lpW1 + lpB[2] * lpW2;
            lpW2 = lpW1;
            lpW1 = w;
        }

        return samples;
    }

    /**
     * Apply the filter to a copy of the input (non-destructive).
     */
    public double[] applyCopy(double[] samples) {
        double[] copy = samples.clone();
        return apply(copy);
    }

    /** Reset the internal filter state (delay elements) to zero. */
    public void reset() {
        hpW1 = hpW2 = 0;
        lpW1 = lpW2 = 0;
    }

    // -----------------------------------------------------------------------
    // Coefficient design helpers
    // -----------------------------------------------------------------------

    /**
     * Design 1st-order Butterworth high-pass biquad coefficients using the
     * bilinear transform.
     *
     * Returns [b0, b1, b2, a1, a2].
     */
    private static double[] designHighPass(int fs, double fc) {
        double omega = 2.0 * Math.PI * fc / fs;
        double k     = Math.tan(omega / 2.0);
        double norm  = 1.0 + k;

        double b0 =  1.0 / norm;
        double b1 = -1.0 / norm;
        double b2 =  0.0;
        double a1 = -(1.0 - k) / norm;
        double a2 =  0.0;

        return new double[]{b0, b1, b2, a1, a2};
    }

    /**
     * Design 1st-order Butterworth low-pass biquad coefficients using the
     * bilinear transform.
     *
     * Returns [b0, b1, b2, a1, a2].
     */
    private static double[] designLowPass(int fs, double fc) {
        double omega = 2.0 * Math.PI * fc / fs;
        double k     = Math.tan(omega / 2.0);
        double norm  = 1.0 + k;

        double b0 = k / norm;
        double b1 = k / norm;
        double b2 = 0.0;
        double a1 = -(1.0 - k) / norm;
        double a2 =  0.0;

        return new double[]{b0, b1, b2, a1, a2};
    }
}
