package holter.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Daily aggregate statistics for one recording day.
 * Computed from beat_table after Module 3 (ContextEnricher).
 * 
 * <p>Satisfies Requirements 2.1-2.8 and 3.1-3.8 from dashboard-enhancements spec:
 * <ul>
 *   <li>2.1: Group beat_table by day_index</li>
 *   <li>2.2: Compute one DailySummary per day</li>
 *   <li>2.3: Compute avg_hr_bpm, min_hr_bpm, max_hr_bpm from RR intervals</li>
 *   <li>2.4: Compute avg_sdnn and avg_rmssd from HRV metrics</li>
 *   <li>2.5: Estimate sleep_hours from "sleep" state beats</li>
 *   <li>2.6: Count events_this_day from flagged events</li>
 *   <li>3.1-3.8: Standardized schema fields</li>
 * </ul>
 * 
 * @param dayIndex Zero-indexed day number (0 = first 24 hours of recording)
 * @param avgHrBpm Average heart rate in beats per minute for this day
 * @param minHrBpm Minimum heart rate in beats per minute for this day
 * @param maxHrBpm Maximum heart rate in beats per minute for this day
 * @param avgSdnn Average SDNN (standard deviation of NN intervals) HRV metric in milliseconds
 * @param avgRmssd Average RMSSD (root mean square of successive differences) HRV metric in milliseconds
 * @param sleepHoursEstimate Estimated sleep hours based on sleep state classification
 * @param eventsThisDay Count of anomaly events that occurred on this day
 */
public record DailySummary(
    @JsonProperty("day_index")
    Integer dayIndex,
    
    @JsonProperty("avg_hr_bpm")
    Double avgHrBpm,
    
    @JsonProperty("min_hr_bpm")
    Double minHrBpm,
    
    @JsonProperty("max_hr_bpm")
    Double maxHrBpm,
    
    @JsonProperty("avg_sdnn")
    Double avgSdnn,
    
    @JsonProperty("avg_rmssd")
    Double avgRmssd,
    
    @JsonProperty("sleep_hours_estimate")
    Double sleepHoursEstimate,
    
    @JsonProperty("events_this_day")
    Integer eventsThisDay
) {
}
