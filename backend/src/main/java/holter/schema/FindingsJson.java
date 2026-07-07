package holter.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Aggregated output containing all detected anomalous events and summary statistics.
 * This is the final output format of the Holter Monitor AI Pipeline,
 * consumed by the Agent Layer and Dashboard.
 * 
 * <p>Satisfies Requirements 2.7, 8.7 from dashboard-enhancements spec:
 * <ul>
 *   <li>2.7: FindingsJson includes daily_summaries field</li>
 *   <li>8.7: Existing test suite continues to pass (backward compatible)</li>
 * </ul>
 * 
 * @param patientId Identifier for the patient whose data was analyzed
 * @param recordingDays Total number of days in the ECG recording
 * @param totalBeatsProcessed Total number of heartbeats analyzed
 * @param events List of all flagged anomalous events
 * @param summaryStats Aggregate statistics across all events
 * @param dailySummaries Daily aggregate statistics, one per recording day (may be null for backward compatibility)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FindingsJson(
    String patientId,
    Integer recordingDays,
    Long totalBeatsProcessed,
    List<FlaggedEvent> events,
    SummaryStats summaryStats,
    
    @JsonProperty("daily_summaries")
    List<DailySummary> dailySummaries,
    
    @JsonProperty("external_report_summary")
    ExternalReportSummary externalReportSummary
) {
}
