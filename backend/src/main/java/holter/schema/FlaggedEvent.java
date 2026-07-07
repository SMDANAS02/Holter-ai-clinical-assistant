package holter.schema;

import java.time.Instant;

/**
 * Record representing a detected anomalous sequence of beats.
 * Contains temporal, contextual, and severity metadata for the event.
 * 
 * @param eventId Unique identifier for this event (typically UUID)
 * @param startTime Timestamp of the first beat in the anomalous sequence
 * @param endTime Timestamp of the last beat in the anomalous sequence
 * @param durationSec Duration of the event in seconds
 * @param beatsInvolved Number of beats in the anomalous sequence
 * @param deviationScore Average deviation score across all beats in the sequence
 * @param contextBucket Context bucket where the anomaly occurred (e.g., "sleep_night_2")
 * @param dayIndex Day of recording when event occurred
 * @param hourOfDay Hour of day when event occurred (0-23.99)
 * @param sleepState Sleep state during the event ("awake", "sleep", or "transition")
 */
public record FlaggedEvent(
    String eventId,
    Instant startTime,
    Instant endTime,
    Double durationSec,
    Integer beatsInvolved,
    Double deviationScore,
    String contextBucket,
    Integer dayIndex,
    Double hourOfDay,
    String sleepState
) {
}
