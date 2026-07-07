package holter.schema;

import java.time.Instant;

/**
 * Beat record with deviation score from personalized baseline model.
 * Extends EnrichedBeatRecord with anomaly scoring information.
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
 * @param deviationScore Numeric measure of how unusual this beat is relative to patient baseline (z-score)
 * @param contextBucket String identifier combining sleep_state and hour bucket (e.g., "sleep_night_2")
 */
public record ScoredBeatRecord(
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
    Double rollingPnn50,
    Double deviationScore,
    String contextBucket
) {
    /**
     * Creates a ScoredBeatRecord from an EnrichedBeatRecord with deviation scoring.
     */
    public static ScoredBeatRecord fromEnrichedBeatRecord(
        EnrichedBeatRecord base,
        Double deviationScore,
        String contextBucket
    ) {
        return new ScoredBeatRecord(
            base.timestamp(),
            base.rrIntervalMs(),
            base.qrsWidthMs(),
            base.rAmplitude(),
            base.qualityFlag(),
            base.dayIndex(),
            base.hourOfDay(),
            base.sleepState(),
            base.rollingSdnn(),
            base.rollingRmssd(),
            base.rollingPnn50(),
            deviationScore,
            contextBucket
        );
    }
}
