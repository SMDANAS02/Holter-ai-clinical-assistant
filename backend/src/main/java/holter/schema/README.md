# Schema Contracts

This package defines the locked data schema contracts for the Holter Monitor AI Pipeline. These immutable Java records enable parallel module development by establishing clear data interfaces between pipeline stages.

## Design Philosophy

### Locked Schema Contracts
Each module in the pipeline receives a record type, adds fields, and produces an enhanced version. The schema is "locked" meaning:
- Existing fields never change or get removed
- Field additions are one-way (downstream only)
- All records are immutable (Java records)
- Type safety catches integration errors at compile time

### Progressive Enhancement Pattern
```
BeatRecord (base fields)
    ↓ + context features
EnrichedBeatRecord (base + context)
    ↓ + deviation scoring
ScoredBeatRecord (base + context + scoring)
    ↓ + event aggregation
FindingsJson (final output)
```

## Record Types

### 1. BeatRecord
**Producer:** Module 1 (HolterStream) + Module 2 (BeatExtractor)  
**Purpose:** Fundamental ECG beat measurements

**Fields:**
- `timestamp` (Instant): Exact time of beat
- `rrIntervalMs` (Double): Time since previous R-peak in milliseconds
- `qrsWidthMs` (Double): QRS complex duration in milliseconds
- `rAmplitude` (Double): R-peak amplitude
- `qualityFlag` (Boolean): True if beat quality is acceptable
- `dayIndex` (Integer): Zero-indexed day number (0 = first 24 hours)

**Example:**
```java
BeatRecord beat = new BeatRecord(
    Instant.parse("2024-01-05T03:15:22Z"),
    852.0,  // RR interval
    88.0,   // QRS width
    1.25,   // R amplitude
    true,   // good quality
    4       // day 4
);
```

### 2. EnrichedBeatRecord
**Producer:** Module 3 (ContextEnricher)  
**Purpose:** Beat data enriched with temporal and HRV context

**Additional Fields:**
- `hourOfDay` (Double): Hour as float between 0 and 23.99
- `sleepState` (String): "awake", "sleep", or "transition"
- `rollingSdnn` (Double): Rolling standard deviation of RR intervals (5-min window)
- `rollingRmssd` (Double): Rolling RMSSD of RR intervals (5-min window)
- `rollingPnn50` (Double): Rolling pNN50 percentage (5-min window)

**Factory Method:**
```java
EnrichedBeatRecord enriched = EnrichedBeatRecord.fromBeatRecord(
    baseBeat,
    3.256,    // hourOfDay
    "sleep",  // sleepState
    42.5,     // rollingSdnn
    38.2,     // rollingRmssd
    22.8      // rollingPnn50
);
```

### 3. ScoredBeatRecord
**Producer:** Module 4 (BaselineModel)  
**Purpose:** Beat data with personalized deviation scoring

**Additional Fields:**
- `deviationScore` (Double): Z-score measuring deviation from patient baseline
- `contextBucket` (String): Context identifier (e.g., "sleep_night_3")

**Factory Method:**
```java
ScoredBeatRecord scored = ScoredBeatRecord.fromEnrichedBeatRecord(
    enrichedBeat,
    5.2,              // deviationScore
    "sleep_night_3"   // contextBucket
);
```

### 4. FlaggedEvent
**Producer:** Module 5 (AnomalyDetector)  
**Purpose:** Represents a detected anomalous sequence

**Fields:**
- `eventId` (String): Unique identifier (typically UUID)
- `startTime` (Instant): First beat in sequence
- `endTime` (Instant): Last beat in sequence
- `durationSec` (Double): Event duration in seconds
- `beatsInvolved` (Integer): Number of beats in sequence
- `deviationScore` (Double): Average deviation score over sequence
- `contextBucket` (String): Context where anomaly occurred
- `dayIndex` (Integer): Day of recording
- `hourOfDay` (Double): Hour when event occurred
- `sleepState` (String): Sleep state during event

**Example:**
```java
FlaggedEvent event = new FlaggedEvent(
    "550e8400-e29b-41d4-a716-446655440000",
    Instant.parse("2024-01-05T03:15:00Z"),
    Instant.parse("2024-01-05T03:15:30Z"),
    30.0,             // 30 seconds
    35,               // 35 beats
    5.2,              // avg deviation
    "sleep_night_3",
    4,                // day 4
    3.25,             // 3:15 AM
    "sleep"
);
```

### 5. SummaryStats
**Producer:** Module 5 (AnomalyDetector)  
**Purpose:** Aggregate statistics across all events

**Fields:**
- `totalEvents` (Integer): Total number of flagged events
- `avgDeviationScore` (Double): Average deviation score across all events
- `mostCommonContext` (String): Context bucket with highest event frequency

### 6. FindingsJson
**Producer:** Module 5 (AnomalyDetector)  
**Consumers:** Agent Layer, Dashboard  
**Purpose:** Final aggregated output of the pipeline

**Fields:**
- `patientId` (String): Patient identifier
- `recordingDays` (Integer): Total days in recording
- `totalBeatsProcessed` (Long): Total beats analyzed
- `events` (List<FlaggedEvent>): All detected anomalous events
- `summaryStats` (SummaryStats): Aggregate statistics

**Example:**
```java
FindingsJson findings = new FindingsJson(
    "patient-001",
    7,                // 7 days
    95432L,           // 95,432 beats
    eventsList,
    summaryStats
);
```

## Usage Patterns

### Parallel Development with Mock Data
Each module can develop independently using mock data generators:

```java
// Module 3 can develop with mocked BeatRecord
List<BeatRecord> mockBeats = MockBeatTableGenerator.generate(1000);
List<EnrichedBeatRecord> enriched = contextEnricher.enrich(mockBeats);
```

### Factory Methods for Progressive Enhancement
Use factory methods to maintain all fields from previous stages:

```java
// Avoid manual field copying - use factory methods
EnrichedBeatRecord enriched = EnrichedBeatRecord.fromBeatRecord(
    base, hourOfDay, sleepState, sdnn, rmssd, pnn50
);

ScoredBeatRecord scored = ScoredBeatRecord.fromEnrichedBeatRecord(
    enriched, deviationScore, contextBucket
);
```

### Immutability Benefits
Records are immutable, preventing accidental modifications:

```java
BeatRecord beat = new BeatRecord(...);
// beat.setRrIntervalMs(900.0); // Compile error - no setters!

// To "modify", create a new record
BeatRecord updated = new BeatRecord(
    beat.timestamp(),
    900.0,  // new RR interval
    beat.qrsWidthMs(),
    beat.rAmplitude(),
    beat.qualityFlag(),
    beat.dayIndex()
);
```

### Handling Zero Events
```java
if (findings.events().isEmpty()) {
    System.out.println("No anomalies detected above threshold");
}
```

## Testing

Run the comprehensive test suite:
```bash
mvn test -Dtest=SchemaContractsTest
```

Run the demonstration example:
```bash
mvn exec:java -Dexec.mainClass="holter.schema.SchemaExample"
```

## Requirements Traceability

This implementation satisfies the following requirements:

- **Requirement 2.5**: BeatRecord output schema
- **Requirement 3.6**: EnrichedBeatRecord with context features
- **Requirement 4.4**: ScoredBeatRecord with deviation scoring
- **Requirement 5.2**: FlaggedEvent structure
- **Requirement 5.3**: FlaggedEvent contextual metadata
- **Requirement 5.5**: FindingsJson aggregation format

## Integration Points

### Input to Agent Layer
```java
HolterAgent agent = new HolterAgent();
String narrative = agent.generateNarrative(findings);
String answer = agent.answerQuestion("What patterns do you see?", findings);
```

### Input to Dashboard
```java
Dashboard dashboard = new Dashboard();
dashboard.displayTimeline(findings.events());
dashboard.displaySummary(findings.summaryStats());
```

## Version History

- **1.0.0** (2024-01-15): Initial locked schema contracts
  - Defined all 6 record types
  - Added factory methods for progressive enhancement
  - Comprehensive test coverage

## See Also

- [Pipeline Architecture](../README.md)
- [Requirements Document](../../../../.kiro/specs/holter-monitor-ai-pipeline/requirements.md)
- [Design Document](../../../../.kiro/specs/holter-monitor-ai-pipeline/design.md)
