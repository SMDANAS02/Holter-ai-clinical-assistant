package holter.mock;

import holter.schema.FlaggedEvent;
import holter.schema.FindingsJson;
import holter.schema.SummaryStats;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates mock FindingsJson data for testing and parallel development.
 * Creates realistic synthetic flagged events with configurable parameters.
 */
public class MockFindingsGenerator {
    
    private static final double MIN_DEVIATION_SCORE = 3.0;
    private static final double MAX_DEVIATION_SCORE = 8.0;
    private static final int MIN_BEATS_INVOLVED = 5;
    private static final int MAX_BEATS_INVOLVED = 100;
    private static final double MIN_DURATION_SEC = 5.0;
    private static final double MAX_DURATION_SEC = 120.0;
    
    private static final String[] SLEEP_STATES = {"awake", "sleep", "transition"};
    private static final String[] CONTEXT_BUCKETS = {
        "sleep_night_2", "sleep_night_3", "sleep_night_4",
        "awake_morning_8", "awake_afternoon_14", "awake_evening_18",
        "transition_morning_7", "transition_evening_21"
    };
    
    private final Random random;
    private final int numberOfEvents;
    private final String patientId;
    private final int recordingDays;
    private final long totalBeatsProcessed;
    private final Instant startTime;
    
    /**
     * Create a generator with default random seed.
     * 
     * @param numberOfEvents Total number of flagged events to generate
     * @param patientId Patient identifier
     * @param recordingDays Number of days in the recording
     * @param totalBeatsProcessed Total number of beats analyzed
     * @param startTime Starting timestamp for the recording
     */
    public MockFindingsGenerator(int numberOfEvents, String patientId, int recordingDays, 
                                 long totalBeatsProcessed, Instant startTime) {
        this(numberOfEvents, patientId, recordingDays, totalBeatsProcessed, startTime, new Random(42));
    }
    
    /**
     * Create a generator with custom random seed for reproducibility.
     * 
     * @param numberOfEvents Total number of flagged events to generate
     * @param patientId Patient identifier
     * @param recordingDays Number of days in the recording
     * @param totalBeatsProcessed Total number of beats analyzed
     * @param startTime Starting timestamp for the recording
     * @param random Random number generator
     */
    public MockFindingsGenerator(int numberOfEvents, String patientId, int recordingDays,
                                long totalBeatsProcessed, Instant startTime, Random random) {
        if (numberOfEvents < 0) {
            throw new IllegalArgumentException("numberOfEvents must be non-negative");
        }
        if (recordingDays <= 0) {
            throw new IllegalArgumentException("recordingDays must be positive");
        }
        if (totalBeatsProcessed < 0) {
            throw new IllegalArgumentException("totalBeatsProcessed must be non-negative");
        }
        this.numberOfEvents = numberOfEvents;
        this.patientId = patientId;
        this.recordingDays = recordingDays;
        this.totalBeatsProcessed = totalBeatsProcessed;
        this.startTime = startTime;
        this.random = random;
    }
    
    /**
     * Generate mock findings with realistic flagged events.
     * 
     * @return FindingsJson with synthetic events and summary statistics
     */
    public FindingsJson generate() {
        if (numberOfEvents == 0) {
            SummaryStats stats = new SummaryStats(0, 0.0, "none");
            return new FindingsJson(patientId, recordingDays, totalBeatsProcessed, List.of(), stats, null);
        }
        
        List<FlaggedEvent> events = new ArrayList<>(numberOfEvents);
        Map<String, Integer> contextCounts = new HashMap<>();
        double totalDeviationScore = 0.0;
        
        // Distribute events across recording period
        long totalSeconds = (long) recordingDays * 86400L;
        
        for (int i = 0; i < numberOfEvents; i++) {
            // Random timestamp within recording period
            long offsetSeconds = (long) (random.nextDouble() * totalSeconds);
            Instant eventStart = startTime.plus(offsetSeconds, ChronoUnit.SECONDS);
            
            // Generate event parameters
            double durationSec = MIN_DURATION_SEC + 
                random.nextDouble() * (MAX_DURATION_SEC - MIN_DURATION_SEC);
            Instant eventEnd = eventStart.plus((long) durationSec, ChronoUnit.SECONDS);
            
            int beatsInvolved = MIN_BEATS_INVOLVED + 
                random.nextInt(MAX_BEATS_INVOLVED - MIN_BEATS_INVOLVED + 1);
            
            double deviationScore = MIN_DEVIATION_SCORE + 
                random.nextDouble() * (MAX_DEVIATION_SCORE - MIN_DEVIATION_SCORE);
            
            // Calculate hour of day
            long secondsFromStart = ChronoUnit.SECONDS.between(startTime, eventStart);
            double hourOfDay = ((secondsFromStart / 3600.0) % 24.0);
            
            // Determine sleep state based on hour
            String sleepState = determineSleepState(hourOfDay);
            
            // Select context bucket matching the sleep state
            String contextBucket = selectContextBucket(sleepState, hourOfDay);
            
            // Calculate day index
            int dayIndex = (int) (secondsFromStart / 86400L);
            
            // Generate event ID
            String eventId = UUID.randomUUID().toString();
            
            FlaggedEvent event = new FlaggedEvent(
                eventId,
                eventStart,
                eventEnd,
                durationSec,
                beatsInvolved,
                deviationScore,
                contextBucket,
                dayIndex,
                hourOfDay,
                sleepState
            );
            
            events.add(event);
            
            // Update statistics
            totalDeviationScore += deviationScore;
            contextCounts.put(contextBucket, contextCounts.getOrDefault(contextBucket, 0) + 1);
        }
        
        // Calculate summary statistics
        double avgDeviationScore = totalDeviationScore / numberOfEvents;
        String mostCommonContext = findMostCommonContext(contextCounts);
        
        SummaryStats stats = new SummaryStats(
            numberOfEvents,
            avgDeviationScore,
            mostCommonContext
        );
        
        return new FindingsJson(patientId, recordingDays, totalBeatsProcessed, events, stats, null);
    }
    
    /**
     * Generate mock findings with events clustered during specific time periods.
     * Useful for testing scenarios with temporal patterns in anomalies.
     * 
     * @return FindingsJson with clustered events
     */
    public FindingsJson generateWithClustering() {
        if (numberOfEvents == 0) {
            SummaryStats stats = new SummaryStats(0, 0.0, "none");
            return new FindingsJson(patientId, recordingDays, totalBeatsProcessed, List.of(), stats, null);
        }
        
        List<FlaggedEvent> events = new ArrayList<>(numberOfEvents);
        Map<String, Integer> contextCounts = new HashMap<>();
        double totalDeviationScore = 0.0;
        
        // Create 3-5 clusters of events
        int numClusters = 3 + random.nextInt(3);
        int eventsPerCluster = numberOfEvents / numClusters;
        int remainingEvents = numberOfEvents % numClusters;
        
        long totalSeconds = (long) recordingDays * 86400L;
        
        for (int cluster = 0; cluster < numClusters; cluster++) {
            // Pick a random center time for this cluster
            long clusterCenterSeconds = (long) (random.nextDouble() * totalSeconds);
            long clusterSpreadSeconds = 3600L; // Events within 1 hour of center
            
            int eventsInThisCluster = eventsPerCluster + (cluster < remainingEvents ? 1 : 0);
            
            for (int i = 0; i < eventsInThisCluster; i++) {
                // Generate event near cluster center
                long offsetSeconds = clusterCenterSeconds + 
                    (long) ((random.nextDouble() - 0.5) * 2 * clusterSpreadSeconds);
                offsetSeconds = Math.max(0, Math.min(totalSeconds - 1, offsetSeconds));
                
                Instant eventStart = startTime.plus(offsetSeconds, ChronoUnit.SECONDS);
                
                double durationSec = MIN_DURATION_SEC + 
                    random.nextDouble() * (MAX_DURATION_SEC - MIN_DURATION_SEC);
                Instant eventEnd = eventStart.plus((long) durationSec, ChronoUnit.SECONDS);
                
                int beatsInvolved = MIN_BEATS_INVOLVED + 
                    random.nextInt(MAX_BEATS_INVOLVED - MIN_BEATS_INVOLVED + 1);
                
                double deviationScore = MIN_DEVIATION_SCORE + 
                    random.nextDouble() * (MAX_DEVIATION_SCORE - MIN_DEVIATION_SCORE);
                
                long secondsFromStart = ChronoUnit.SECONDS.between(startTime, eventStart);
                double hourOfDay = ((secondsFromStart / 3600.0) % 24.0);
                String sleepState = determineSleepState(hourOfDay);
                String contextBucket = selectContextBucket(sleepState, hourOfDay);
                int dayIndex = (int) (secondsFromStart / 86400L);
                String eventId = UUID.randomUUID().toString();
                
                FlaggedEvent event = new FlaggedEvent(
                    eventId, eventStart, eventEnd, durationSec, beatsInvolved,
                    deviationScore, contextBucket, dayIndex, hourOfDay, sleepState
                );
                
                events.add(event);
                totalDeviationScore += deviationScore;
                contextCounts.put(contextBucket, contextCounts.getOrDefault(contextBucket, 0) + 1);
            }
        }
        
        // Sort events by time
        events.sort((e1, e2) -> e1.startTime().compareTo(e2.startTime()));
        
        double avgDeviationScore = totalDeviationScore / numberOfEvents;
        String mostCommonContext = findMostCommonContext(contextCounts);
        
        SummaryStats stats = new SummaryStats(
            numberOfEvents,
            avgDeviationScore,
            mostCommonContext
        );
        
        return new FindingsJson(patientId, recordingDays, totalBeatsProcessed, events, stats, java.util.Collections.emptyList());
    }
    
    private String determineSleepState(double hourOfDay) {
        if (hourOfDay >= 22.0 || hourOfDay < 6.0) {
            return "sleep";
        } else if ((hourOfDay >= 6.0 && hourOfDay < 8.0) || 
                   (hourOfDay >= 20.0 && hourOfDay < 22.0)) {
            return "transition";
        } else {
            return "awake";
        }
    }
    
    private String selectContextBucket(String sleepState, double hourOfDay) {
        List<String> matchingBuckets = new ArrayList<>();
        for (String bucket : CONTEXT_BUCKETS) {
            if (bucket.startsWith(sleepState)) {
                matchingBuckets.add(bucket);
            }
        }
        
        if (matchingBuckets.isEmpty()) {
            // Fallback to any context bucket
            return CONTEXT_BUCKETS[random.nextInt(CONTEXT_BUCKETS.length)];
        }
        
        return matchingBuckets.get(random.nextInt(matchingBuckets.size()));
    }
    
    private String findMostCommonContext(Map<String, Integer> contextCounts) {
        if (contextCounts.isEmpty()) {
            return "none";
        }
        
        return contextCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");
    }
    
    /**
     * Create a builder for configurable mock findings generation.
     * 
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for creating MockFindingsGenerator with custom parameters.
     */
    public static class Builder {
        private int numberOfEvents = 10;
        private String patientId = "patient-001";
        private int recordingDays = 7;
        private long totalBeatsProcessed = 100000L;
        private Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        private Random random = new Random(42);
        
        public Builder numberOfEvents(int numberOfEvents) {
            this.numberOfEvents = numberOfEvents;
            return this;
        }
        
        public Builder patientId(String patientId) {
            this.patientId = patientId;
            return this;
        }
        
        public Builder recordingDays(int recordingDays) {
            this.recordingDays = recordingDays;
            return this;
        }
        
        public Builder totalBeatsProcessed(long totalBeatsProcessed) {
            this.totalBeatsProcessed = totalBeatsProcessed;
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
        
        public MockFindingsGenerator build() {
            return new MockFindingsGenerator(
                numberOfEvents, patientId, recordingDays, 
                totalBeatsProcessed, startTime, random
            );
        }
    }
}
