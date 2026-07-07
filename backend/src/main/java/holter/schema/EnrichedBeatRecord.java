package holter.schema;

import java.time.Instant;

/**
 * Beat record enriched with contextual features.
 * Extends BeatRecord with temporal context and HRV rolling window metrics.
 * 
 * @param timestamp The exact time when the beat occurred
 * @param rrIntervalMs Time in milliseconds since the previous R-peak
 * @param qrsWidthMs Duration of the QRS complex in milliseconds
 * @param rAmplitude Amplitude of the R-peak
 * @param qualityFlag True if beat quality is acceptable, false if noisy or artifact
 * @param dayIndex Zero-indexed day number (0 = first 24 hours of recording)
 * @param hourOfDay Hour of day as float between 0 and 23.99
 * @param sleepState Inferred sleep state: "awake", "sleep", or "transition"
 * @param rollingSdnn Rolling standard deviation of RR intervals (5-min window)
 * @param rollingRmssd Rolling root mean square of successive RR differences (5-min window)
 * @param rollingPnn50 Rolling percentage of RR interval differences >50ms (5-min window)
 */
public record EnrichedBeatRecord(
    Instant timestamp,
    Double rrIntervalMs,
    Double qrsWidthMs,
    Double rAmplitude,
    Boolean qualityFlag,
    Integer dayIndex,
    Double hourOfDay,
    String sleepState,
    Double rollingSdnn,
    Double rollingRmssd,
    Double rollingPnn50
) {
    /**
     * Creates an EnrichedBeatRecord from a base BeatRecord with additional context features.
     */
    public static EnrichedBeatRecord fromBeatRecord(
        BeatRecord base,
        Double hourOfDay,
        String sleepState,
        Double rollingSdnn,
        Double rollingRmssd,
        Double rollingPnn50
    ) {
        return new EnrichedBeatRecord(
            base.timestamp(),
            base.rrIntervalMs(),
            base.qrsWidthMs(),
            base.rAmplitude(),
            base.qualityFlag(),
            base.dayIndex(),
            hourOfDay,
            sleepState,
            rollingSdnn,
            rollingRmssd,
            rollingPnn50
        );
    }
}
