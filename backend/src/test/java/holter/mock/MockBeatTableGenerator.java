package holter.mock;

import holter.schema.BeatRecord;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates mock BeatRecord data for testing and parallel development.
 * Creates realistic synthetic ECG beat data with configurable parameters.
 */
public class MockBeatTableGenerator {
    
    private static final double BASELINE_HR = 70.0; // beats per minute
    private static final double BASELINE_RR_INTERVAL_MS = 60000.0 / BASELINE_HR; // ~857ms
    private static final double RR_STDDEV = 50.0; // normal HRV
    private static final double QRS_MEAN = 90.0; // milliseconds
    private static final double QRS_STDDEV = 10.0;
    private static final double R_AMPLITUDE_MEAN = 1.2;
    private static final double R_AMPLITUDE_STDDEV = 0.2;
    private static final double NOISE_PROBABILITY = 0.02; // 2% of beats are noisy
    
    private final Random random;
    private final int numberOfBeats;
    private final double anomalyRate;
    private final Instant startTime;
    
    /**
     * Create a generator with default random seed.
     * 
     * @param numberOfBeats Total number of beats to generate
     * @param anomalyRate Fraction of beats that should be anomalous (0.0 to 1.0)
     * @param startTime Starting timestamp for the recording
     */
    public MockBeatTableGenerator(int numberOfBeats, double anomalyRate, Instant startTime) {
        this(numberOfBeats, anomalyRate, startTime, new Random(42));
    }
    
    /**
     * Create a generator with custom random seed for reproducibility.
     * 
     * @param numberOfBeats Total number of beats to generate
     * @param anomalyRate Fraction of beats that should be anomalous (0.0 to 1.0)
     * @param startTime Starting timestamp for the recording
     * @param random Random number generator
     */
    public MockBeatTableGenerator(int numberOfBeats, double anomalyRate, Instant startTime, Random random) {
        if (numberOfBeats < 0) {
            throw new IllegalArgumentException("numberOfBeats must be non-negative");
        }
        if (anomalyRate < 0.0 || anomalyRate > 1.0) {
            throw new IllegalArgumentException("anomalyRate must be between 0.0 and 1.0");
        }
        this.numberOfBeats = numberOfBeats;
        this.anomalyRate = anomalyRate;
        this.startTime = startTime;
        this.random = random;
    }
    
    /**
     * Generate the mock beat table with realistic synthetic ECG data.
     * 
     * @return List of BeatRecord instances with realistic timing and metrics
     */
    public List<BeatRecord> generate() {
        List<BeatRecord> beats = new ArrayList<>(numberOfBeats);
        Instant currentTime = startTime;
        
        for (int i = 0; i < numberOfBeats; i++) {
            boolean isAnomalous = random.nextDouble() < anomalyRate;
            boolean isNoisy = random.nextDouble() < NOISE_PROBABILITY;
            
            // Generate RR interval with optional anomaly
            double rrInterval;
            if (isAnomalous) {
                // Anomalous beats have significantly different RR intervals
                rrInterval = BASELINE_RR_INTERVAL_MS + random.nextGaussian() * RR_STDDEV * 5.0;
            } else {
                // Normal beats follow expected HRV pattern
                rrInterval = BASELINE_RR_INTERVAL_MS + random.nextGaussian() * RR_STDDEV;
            }
            
            // Ensure RR interval stays within physiologically plausible bounds
            rrInterval = Math.max(300.0, Math.min(2000.0, rrInterval));
            
            // Generate QRS width with slight variation
            double qrsWidth = QRS_MEAN + random.nextGaussian() * QRS_STDDEV;
            qrsWidth = Math.max(60.0, Math.min(120.0, qrsWidth));
            
            // Generate R amplitude with variation
            double rAmplitude = R_AMPLITUDE_MEAN + random.nextGaussian() * R_AMPLITUDE_STDDEV;
            rAmplitude = Math.max(0.5, Math.min(2.0, rAmplitude));
            
            // Quality flag is false for noisy beats
            boolean qualityFlag = !isNoisy;
            
            // Calculate day index (0-indexed)
            long secondsFromStart = ChronoUnit.SECONDS.between(startTime, currentTime);
            int dayIndex = (int) (secondsFromStart / 86400);
            
            // Create beat record
            BeatRecord beat = new BeatRecord(
                currentTime,
                rrInterval,
                qrsWidth,
                rAmplitude,
                qualityFlag,
                dayIndex
            );
            
            beats.add(beat);
            
            // Advance time by RR interval
            currentTime = currentTime.plusMillis((long) rrInterval);
        }
        
        return beats;
    }
    
    /**
     * Generate mock beat table with circadian rhythm patterns.
     * Heart rate varies throughout the day, with lower rates during sleep hours.
     * 
     * @return List of BeatRecord instances with realistic circadian patterns
     */
    public List<BeatRecord> generateWithCircadianRhythm() {
        List<BeatRecord> beats = new ArrayList<>(numberOfBeats);
        Instant currentTime = startTime;
        
        for (int i = 0; i < numberOfBeats; i++) {
            // Calculate hour of day for circadian adjustment
            long secondsFromStart = ChronoUnit.SECONDS.between(startTime, currentTime);
            double hourOfDay = ((secondsFromStart / 3600.0) % 24.0);
            
            // Adjust baseline HR based on hour (lower during sleep: 22:00-06:00)
            double circadianHR = BASELINE_HR;
            if (hourOfDay >= 22.0 || hourOfDay < 6.0) {
                // Sleep hours: reduce HR by 15%
                circadianHR *= 0.85;
            } else if (hourOfDay >= 6.0 && hourOfDay < 8.0) {
                // Wake transition: gradual increase
                double transitionFactor = (hourOfDay - 6.0) / 2.0;
                circadianHR = (BASELINE_HR * 0.85) + (BASELINE_HR * 0.15 * transitionFactor);
            } else if (hourOfDay >= 20.0 && hourOfDay < 22.0) {
                // Sleep transition: gradual decrease
                double transitionFactor = (hourOfDay - 20.0) / 2.0;
                circadianHR = BASELINE_HR - (BASELINE_HR * 0.15 * transitionFactor);
            }
            
            double baseRRInterval = 60000.0 / circadianHR;
            
            boolean isAnomalous = random.nextDouble() < anomalyRate;
            boolean isNoisy = random.nextDouble() < NOISE_PROBABILITY;
            
            // Generate RR interval
            double rrInterval;
            if (isAnomalous) {
                rrInterval = baseRRInterval + random.nextGaussian() * RR_STDDEV * 5.0;
            } else {
                rrInterval = baseRRInterval + random.nextGaussian() * RR_STDDEV;
            }
            
            rrInterval = Math.max(300.0, Math.min(2000.0, rrInterval));
            
            double qrsWidth = QRS_MEAN + random.nextGaussian() * QRS_STDDEV;
            qrsWidth = Math.max(60.0, Math.min(120.0, qrsWidth));
            
            double rAmplitude = R_AMPLITUDE_MEAN + random.nextGaussian() * R_AMPLITUDE_STDDEV;
            rAmplitude = Math.max(0.5, Math.min(2.0, rAmplitude));
            
            boolean qualityFlag = !isNoisy;
            
            int dayIndex = (int) (secondsFromStart / 86400);
            
            BeatRecord beat = new BeatRecord(
                currentTime,
                rrInterval,
                qrsWidth,
                rAmplitude,
                qualityFlag,
                dayIndex
            );
            
            beats.add(beat);
            currentTime = currentTime.plusMillis((long) rrInterval);
        }
        
        return beats;
    }
    
    /**
     * Create a builder for configurable mock beat generation.
     * 
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for creating MockBeatTableGenerator with custom parameters.
     */
    public static class Builder {
        private int numberOfBeats = 10000;
        private double anomalyRate = 0.05;
        private Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        private Random random = new Random(42);
        
        public Builder numberOfBeats(int numberOfBeats) {
            this.numberOfBeats = numberOfBeats;
            return this;
        }
        
        public Builder anomalyRate(double anomalyRate) {
            this.anomalyRate = anomalyRate;
            return this;
        }
        
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder randomSeed(long seed) {
            this.random = new Random(seed);
            return this;
        }
        
        public Builder random(Random random) {
            this.random = random;
            return this;
        }
        
        public MockBeatTableGenerator build() {
            return new MockBeatTableGenerator(numberOfBeats, anomalyRate, startTime, random);
        }
    }
}
