package holter.signal;

import holter.schema.BeatRecord;
import holter.ingestion.HolterStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 2: BeatExtractor – Signal Processing pipeline.
 * <p>
 * Orchestrates the full signal-processing chain:
 * <ol>
 *   <li>Accepts a {@link HolterStreamReader} (or any raw {@code double[]} array)</li>
 *   <li>Applies {@link ButterworthFilter} bandpass filtering to each chunk</li>
 *   <li>Detects R-peaks with {@link PanTompkinsDetector}</li>
 *   <li>Computes per-beat metrics (RR interval, QRS width, R amplitude, dayIndex)</li>
 *   <li>Flags beat quality with {@link QualityChecker}</li>
 *   <li>Returns a {@code List<BeatRecord>} conforming to the locked schema</li>
 * </ol>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * BeatExtractor extractor = BeatExtractor.builder()
 *     .samplingRateHz(360)
 *     .recordingStart(Instant.now())
 *     .build();
 * List<BeatRecord> beats = extractor.process(holterStreamReader);
 * }</pre>
 */
public class BeatExtractor {

    private static final Logger logger = LoggerFactory.getLogger(BeatExtractor.class);

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private final int     samplingRateHz;
    private final Instant recordingStart;
    private final ButterworthFilter filter;
    private final PanTompkinsDetector detector;
    private final QualityChecker qualityChecker;

    // QRS width estimation parameters
    /** Half-width of QRS search window in ms for width estimation. */
    private static final double QRS_HALF_WINDOW_MS = 60.0;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    private BeatExtractor(Builder b) {
        this.samplingRateHz  = b.samplingRateHz;
        this.recordingStart  = b.recordingStart;
        this.filter          = new ButterworthFilter(samplingRateHz, b.lowCutHz, b.highCutHz);
        this.detector        = new PanTompkinsDetector(samplingRateHz);
        this.qualityChecker  = new QualityChecker(b.minRrMs, b.maxRrMs, b.maxRrRelChange, b.minRAmplMv);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Process a full {@link HolterStreamReader} stream and return all detected beats.
     * Chunks are concatenated internally before detection (suitable for recordings
     * up to ~24h on modern hardware).
     *
     * @param reader  Open, not-yet-consumed reader
     * @return List of BeatRecord (may be empty if no peaks detected)
     */
    public List<BeatRecord> process(HolterStreamReader reader) {
        logger.info("BeatExtractor: loading and filtering ECG stream...");

        // Concatenate all chunks into one buffer
        List<double[]> chunks = new ArrayList<>();
        int totalSamples = 0;
        while (reader.hasNext()) {
            double[] chunk = reader.next();
            chunks.add(chunk);
            totalSamples += chunk.length;
        }

        double[] raw = new double[totalSamples];
        int pos = 0;
        for (double[] c : chunks) {
            System.arraycopy(c, 0, raw, pos, c.length);
            pos += c.length;
        }

        return processSignal(raw);
    }

    /**
     * Process a pre-loaded raw ECG signal array directly.
     *
     * @param raw  Raw ECG samples (mV or ADU – will be filtered)
     * @return List of BeatRecord
     */
    public List<BeatRecord> processSignal(double[] raw) {
        logger.info("BeatExtractor: processing {} samples @ {} Hz", raw.length, samplingRateHz);

        // Step 1: Bandpass filter
        double[] filtered = filter.applyCopy(raw);

        // Step 2: R-peak detection
        int[] rPeakIndices = detector.detect(filtered);
        logger.info("Detected {} R-peaks", rPeakIndices.length);

        if (rPeakIndices.length == 0) {
            return new ArrayList<>();
        }

        // Step 3 & 4 & 5: Compute per-beat metrics
        return computeBeatRecords(filtered, rPeakIndices);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<BeatRecord> computeBeatRecords(double[] signal, int[] rPeaks) {
        List<BeatRecord> beats = new ArrayList<>(rPeaks.length);

        int qrsHalfWindow = Math.max(1, (int)(QRS_HALF_WINDOW_MS * samplingRateHz / 1000.0));
        double prevRrMs = Double.NaN;

        for (int i = 0; i < rPeaks.length; i++) {
            int rIdx = rPeaks[i];

            // RR interval
            double rrMs;
            if (i == 0) {
                // No previous peak – estimate from signal start
                rrMs = (rIdx * 1000.0) / samplingRateHz;
                // Use a plausible default if the first beat is at sample 0
                if (rrMs < 300) rrMs = 857.0; // ~70 bpm
            } else {
                rrMs = (rIdx - rPeaks[i - 1]) * 1000.0 / samplingRateHz;
            }

            // R amplitude (raw signal value at R-peak index)
            double rAmp = (rIdx >= 0 && rIdx < signal.length) ? signal[rIdx] : 0.0;

            // QRS width: distance from start of Q-wave to end of S-wave
            double qrsWidthMs = estimateQrsWidth(signal, rIdx, qrsHalfWindow);

            // Timestamp for this beat
            long offsetMs = (long)((rIdx * 1000L) / samplingRateHz);
            Instant beatTime = recordingStart.plusMillis(offsetMs);

            // Day index (day 0 = first 24 hours)
            int dayIndex = (int)(offsetMs / 86_400_000L);

            // Quality flag
            boolean goodQuality = qualityChecker.isGoodQuality(rrMs, prevRrMs, rAmp);

            beats.add(new BeatRecord(beatTime, rrMs, qrsWidthMs, rAmp, goodQuality, dayIndex));
            prevRrMs = rrMs;
        }

        long goodCount = beats.stream().filter(BeatRecord::qualityFlag).count();
        logger.info("BeatExtractor: {} beats total, {} good quality ({:.1f}%)",
                    beats.size(), goodCount, 100.0 * goodCount / beats.size());
        return beats;
    }

    /**
     * Estimate QRS complex width by walking outward from the R-peak until the
     * signal magnitude drops below 10% of the R-peak amplitude.
     */
    private double estimateQrsWidth(double[] signal, int rIdx, int halfWindow) {
        double rAmp = Math.abs(signal[rIdx]);
        double threshold = rAmp * 0.1;

        int lo = rIdx;
        while (lo > rIdx - halfWindow && lo > 0 && Math.abs(signal[lo]) > threshold) lo--;

        int hi = rIdx;
        while (hi < rIdx + halfWindow && hi < signal.length - 1 && Math.abs(signal[hi]) > threshold) hi++;

        double widthMs = (hi - lo) * 1000.0 / samplingRateHz;
        // Clamp to physiological range [60, 120] ms
        return Math.max(60.0, Math.min(120.0, widthMs));
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int     samplingRateHz = 360;
        private Instant recordingStart = Instant.EPOCH;
        private double  lowCutHz       = 0.5;
        private double  highCutHz      = 50.0;
        private double  minRrMs        = 300.0;
        private double  maxRrMs        = 2000.0;
        private double  maxRrRelChange = 0.50;
        private double  minRAmplMv     = 0.1;

        public Builder samplingRateHz(int v)    { this.samplingRateHz = v; return this; }
        public Builder recordingStart(Instant v){ this.recordingStart = v; return this; }
        public Builder lowCutHz(double v)       { this.lowCutHz       = v; return this; }
        public Builder highCutHz(double v)      { this.highCutHz      = v; return this; }
        public Builder minRrMs(double v)        { this.minRrMs        = v; return this; }
        public Builder maxRrMs(double v)        { this.maxRrMs        = v; return this; }
        public Builder maxRrRelativeChange(double v){ this.maxRrRelChange = v; return this; }
        public Builder minRAmplitudeMv(double v){ this.minRAmplMv     = v; return this; }

        public BeatExtractor build() { return new BeatExtractor(this); }
    }
}
