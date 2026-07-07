package holter.baseline;

import holter.schema.DailySummary;
import holter.schema.EnrichedBeatRecord;
import holter.schema.FlaggedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes daily aggregate statistics from enriched beat table.
 * Called after Module 5 (AnomalyDetector) to generate DailySummary records.
 */
public class DailySummaryCalculator {
    private static final Logger logger = LoggerFactory.getLogger(DailySummaryCalculator.class);

    public List<DailySummary> calculate(List<EnrichedBeatRecord> beats, List<FlaggedEvent> events) {
        if (beats == null || beats.isEmpty()) {
            logger.warn("No beats provided for daily summary calculation");
            return Collections.emptyList();
        }

        // Group beats by day
        Map<Integer, List<EnrichedBeatRecord>> beatsByDay = beats.stream()
            .collect(Collectors.groupingBy(EnrichedBeatRecord::dayIndex));

        // Group events by day
        Map<Integer, Long> eventsByDay = events.stream()
            .collect(Collectors.groupingBy(FlaggedEvent::dayIndex, Collectors.counting()));

        // Calculate summary for each day
        List<DailySummary> summaries = new ArrayList<>();
        for (Map.Entry<Integer, List<EnrichedBeatRecord>> entry : beatsByDay.entrySet()) {
            int dayIndex = entry.getKey();
            List<EnrichedBeatRecord> dayBeats = entry.getValue();

            // Calculate heart rates (60000 ms/min / RR interval in ms = BPM)
            List<Double> heartRates = dayBeats.stream()
                .filter(b -> b.rrIntervalMs() != null && b.rrIntervalMs() > 0)
                .map(b -> 60000.0 / b.rrIntervalMs())
                .toList();

            double avgHr = heartRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double minHr = heartRates.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxHr = heartRates.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

            // Calculate average HRV metrics
            double avgSdnn = dayBeats.stream()
                .filter(b -> b.rollingSdnn() != null)
                .mapToDouble(EnrichedBeatRecord::rollingSdnn)
                .average().orElse(0.0);

            double avgRmssd = dayBeats.stream()
                .filter(b -> b.rollingRmssd() != null)
                .mapToDouble(EnrichedBeatRecord::rollingRmssd)
                .average().orElse(0.0);

            // Estimate sleep hours (count sleep state beats / avg HR / 60)
            long sleepBeats = dayBeats.stream()
                .filter(b -> "sleep".equals(b.sleepState()))
                .count();
            double sleepHours = avgHr > 0 ? sleepBeats / (avgHr * 60.0) : 0.0;

            // Count events on this day
            int eventsThisDay = eventsByDay.getOrDefault(dayIndex, 0L).intValue();

            summaries.add(new DailySummary(
                dayIndex,
                avgHr,
                minHr,
                maxHr,
                avgSdnn,
                avgRmssd,
                sleepHours,
                eventsThisDay
            ));
        }

        // Sort by day index
        summaries.sort(Comparator.comparingInt(DailySummary::dayIndex));
        
        logger.info("Calculated daily summaries for {} days", summaries.size());
        return summaries;
    }
}
