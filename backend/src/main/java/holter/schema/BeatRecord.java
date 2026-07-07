package holter.schema;

import java.time.Instant;

/**
 * Base beat record containing fundamental ECG beat measurements.
 * Represents a single detected heartbeat with its core metrics.
 * 
 * @param timestamp The exact time when the beat occurred
 * @param rrIntervalMs Time in milliseconds since the previous R-peak
 * @param qrsWidthMs Duration of the QRS complex in milliseconds
 * @param rAmplitude Amplitude of the R-peak
 * @param qualityFlag True if beat quality is acceptable, false if noisy or artifact
 * @param dayIndex Zero-indexed day number (0 = first 24 hours of recording)
 */
public record BeatRecord(
    Instant timestamp,
    Double rrIntervalMs,
    Double qrsWidthMs,
    Double rAmplitude,
    Boolean qualityFlag,
    Integer dayIndex
) {
}
