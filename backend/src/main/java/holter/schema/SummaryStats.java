package holter.schema;

/**
 * Summary statistics computed across all flagged events.
 * 
 * @param totalEvents Total number of anomalous events detected
 * @param avgDeviationScore Average deviation score across all events
 * @param mostCommonContext The context bucket with the highest frequency of events
 */
public record SummaryStats(
    Integer totalEvents,
    Double avgDeviationScore,
    String mostCommonContext
) {
}
