package holter.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Module 1 – Task 4.2: Synthetic ECG data generator.
 * <p>
 * Produces a realistic multi-day ECG voltage signal (in millivolts) sampled at a
 * configurable rate.  The generator simulates:
 * <ul>
 *   <li>P-wave, QRS complex, and T-wave morphology for each beat</li>
 *   <li>Configurable heart-rate variability (HRV)</li>
 *   <li>Circadian HR modulation (lower during sleep 22:00-06:00)</li>
 *   <li>Optional injected anomalies (premature beats / pauses)</li>
 * </ul>
 */
public class SyntheticEcgGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SyntheticEcgGenerator.class);

    // -----------------------------------------------------------------------
    // Parameters
    // -----------------------------------------------------------------------

    /** Sampling frequency in Hz. */
    private final int samplingRateHz;
    /** Total recording duration in hours. */
    private final double durationHours;
    /** Mean heart rate in BPM during awake state. */
    private final double baselineHrBpm;
    /** Standard deviation of RR-interval variability in seconds (HRV). */
    private final double rrSdSec;
    /** Fraction of beats that are injected anomalies (premature / pause). */
    private final double anomalyRate;
    /** Reproducible random source. */
    private final Random random;

    // -----------------------------------------------------------------------
    // Derived
    // -----------------------------------------------------------------------

    /** Total number of samples in the generated signal. */
    private final int totalSamples;

    // -----------------------------------------------------------------------
    // Constructor / Builder
    // -----------------------------------------------------------------------

    private SyntheticEcgGenerator(Builder b) {
        this.samplingRateHz = b.samplingRateHz;
        this.durationHours  = b.durationHours;
        this.baselineHrBpm  = b.baselineHrBpm;
        this.rrSdSec        = b.rrSdSec;
        this.anomalyRate    = b.anomalyRate;
        this.random         = new Random(b.seed);
        this.totalSamples   = (int) (samplingRateHz * durationHours * 3600.0);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** @return Total number of samples this generator will produce. */
    public int totalSamples() { return totalSamples; }

    /** @return Sampling rate configured for this generator (Hz). */
    public int samplingRateHz() { return samplingRateHz; }

    /** @return Duration in hours. */
    public double durationHours() { return durationHours; }

    /**
     * Generate the full ECG signal.
     *
     * @return double array of length {@link #totalSamples()} with voltage in mV
     */
    public double[] generate() {
        logger.info("Generating synthetic ECG: {:.1f}h @ {}Hz = {} samples",
                    durationHours, samplingRateHz, totalSamples);

        double[] signal = new double[totalSamples];
        int samplePos   = 0;  // current write position in signal[]
        double timeSec  = 0;  // running wall-clock time

        // We write beat templates into the signal one beat at a time.
        while (samplePos < totalSamples) {
            // Determine HR with circadian modulation
            double hourOfDay = (timeSec / 3600.0) % 24.0;
            double hrBpm     = circadianHr(hourOfDay);

            // Add HRV jitter
            double meanRrSec = 60.0 / hrBpm;
            double rrSec     = Math.max(0.3, meanRrSec + random.nextGaussian() * rrSdSec);

            // Inject anomalies
            boolean isPremature = random.nextDouble() < anomalyRate / 2.0;
            boolean isPause     = random.nextDouble() < anomalyRate / 2.0;
            if (isPremature) rrSec *= 0.6;
            if (isPause)     rrSec *= 2.2;

            int rrSamples = (int) (rrSec * samplingRateHz);

            // Write beat template starting at ~60% of RR (place QRS after P-wave onset)
            int qrsOffset = (int) (rrSamples * 0.6);
            writeBeatTemplate(signal, samplePos, qrsOffset, rrSamples);

            samplePos += rrSamples;
            timeSec   += rrSec;
        }

        logger.info("Synthetic ECG generation complete ({} samples)", totalSamples);
        return signal;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Circadian HR modulation – lower by 15 % during sleep 22:00-06:00. */
    private double circadianHr(double hourOfDay) {
        if (hourOfDay >= 22.0 || hourOfDay < 6.0) {
            return baselineHrBpm * 0.85;
        } else if (hourOfDay < 8.0) {
            double t = (hourOfDay - 6.0) / 2.0;
            return baselineHrBpm * (0.85 + 0.15 * t);
        } else if (hourOfDay >= 20.0) {
            double t = (hourOfDay - 20.0) / 2.0;
            return baselineHrBpm * (1.0 - 0.15 * t);
        }
        return baselineHrBpm;
    }

    /**
     * Writes a simplified P-QRS-T morphology into {@code signal} starting at
     * {@code start}, with the QRS complex placed at {@code start + qrsOffset}.
     *
     * @param signal     Output buffer
     * @param start      Beat start index
     * @param qrsOffset  Samples from beat start to QRS peak
     * @param rrSamples  Total beat duration in samples
     */
    private void writeBeatTemplate(double[] signal, int start, int qrsOffset, int rrSamples) {
        // Gaussian-shaped P-wave (small positive bump, ~80 ms wide)
        int pCenter = start + (int) (qrsOffset * 0.55);
        int pWidth  = Math.max(1, (int) (0.04 * samplingRateHz));
        writeGaussian(signal, pCenter, pWidth, 0.15);

        // QRS complex: narrow negative dip (Q), sharp positive spike (R), small neg dip (S)
        int qrsCenter = start + qrsOffset;
        int qrsWidth  = Math.max(1, (int) (0.04 * samplingRateHz));
        // Q
        writeGaussian(signal, qrsCenter - qrsWidth, Math.max(1, qrsWidth / 2), -0.15);
        // R (tall positive spike)
        double rAmplitude = 1.0 + random.nextGaussian() * 0.1;
        writeGaussian(signal, qrsCenter, qrsWidth, rAmplitude);
        // S
        writeGaussian(signal, qrsCenter + qrsWidth, Math.max(1, qrsWidth / 2), -0.25);

        // T-wave (broad positive bump after QRS)
        int tCenter = start + qrsOffset + (int) (rrSamples * 0.18);
        int tWidth  = Math.max(1, (int) (0.07 * samplingRateHz));
        writeGaussian(signal, tCenter, tWidth, 0.35);
    }

    /** Adds a Gaussian-shaped pulse centred at {@code centre} with given sigma and amplitude. */
    private void writeGaussian(double[] signal, int centre, int sigma, double amplitude) {
        int halfWidth = sigma * 3;
        for (int i = centre - halfWidth; i <= centre + halfWidth; i++) {
            if (i < 0 || i >= signal.length) continue;
            double x = (i - centre) / (double) sigma;
            signal[i] += amplitude * Math.exp(-0.5 * x * x);
        }
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int    samplingRateHz = 360;    // MIT-BIH standard
        private double durationHours = 24.0;   // 1 day default
        private double baselineHrBpm = 70.0;
        private double rrSdSec       = 0.05;   // ~50 ms HRV
        private double anomalyRate   = 0.02;
        private long   seed          = 42L;

        public Builder samplingRateHz(int v)    { this.samplingRateHz = v; return this; }
        public Builder durationHours(double v)  { this.durationHours  = v; return this; }
        public Builder baselineHrBpm(double v)  { this.baselineHrBpm  = v; return this; }
        public Builder rrSdSec(double v)        { this.rrSdSec        = v; return this; }
        public Builder anomalyRate(double v)    { this.anomalyRate    = v; return this; }
        public Builder seed(long v)             { this.seed           = v; return this; }

        public SyntheticEcgGenerator build() { return new SyntheticEcgGenerator(this); }
    }
}
