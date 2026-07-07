# Schema Contract Data Flow

This document visualizes how data transforms through the pipeline using the locked schema contracts.

## Pipeline Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     ECG Signal Input Files                           │
│                  (WFDB, CSV, Synthetic Data)                         │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Module 1: HolterStream + Module 2: BeatExtractor                    │
│  ────────────────────────────────────────────────────                │
│  • Read ECG signal chunks                                            │
│  • Apply bandpass filter (0.5-50 Hz)                                 │
│  • Detect R-peaks (Pan-Tompkins)                                     │
│  • Compute RR intervals, QRS width, R amplitude                      │
│  • Assess beat quality                                               │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
                          ▼
                   ┌──────────────┐
                   │  BeatRecord  │
                   ├──────────────┤
                   │ timestamp    │
                   │ rrIntervalMs │
                   │ qrsWidthMs   │
                   │ rAmplitude   │
                   │ qualityFlag  │
                   │ dayIndex     │
                   └──────┬───────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Module 3: ContextEnricher                                           │
│  ─────────────────────────                                           │
│  • Calculate hour_of_day from timestamp                              │
│  • Infer sleep_state (awake/sleep/transition)                        │
│  • Compute rolling HRV metrics (SDNN, RMSSD, pNN50)                  │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
                          ▼
                ┌────────────────────┐
                │ EnrichedBeatRecord │
                ├────────────────────┤
                │ timestamp          │
                │ rrIntervalMs       │
                │ qrsWidthMs         │
                │ rAmplitude         │
                │ qualityFlag        │
                │ dayIndex           │
                │ ─────────────────  │
                │ hourOfDay          │ ← Added by Module 3
                │ sleepState         │
                │ rollingSdnn        │
                │ rollingRmssd       │
                │ rollingPnn50       │
                └────────┬───────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Module 4: BaselineModel                                             │
│  ────────────────────────                                            │
│  • Create context buckets (sleep_state + hour)                       │
│  • Fit Gaussian distributions per context                            │
│  • Compute deviation scores (z-scores)                               │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
                          ▼
                ┌────────────────────┐
                │ ScoredBeatRecord   │
                ├────────────────────┤
                │ timestamp          │
                │ rrIntervalMs       │
                │ qrsWidthMs         │
                │ rAmplitude         │
                │ qualityFlag        │
                │ dayIndex           │
                │ hourOfDay          │
                │ sleepState         │
                │ rollingSdnn        │
                │ rollingRmssd       │
                │ rollingPnn50       │
                │ ─────────────────  │
                │ deviationScore     │ ← Added by Module 4
                │ contextBucket      │
                └────────┬───────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Module 5: AnomalyDetector                                           │
│  ──────────────────────────                                          │
│  • Identify contiguous high-deviation sequences                      │
│  • Create FlaggedEvent records                                       │
│  • Deduplicate and merge overlapping events                          │
│  • Compute summary statistics                                        │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
                          ▼
                    ┌─────────────┐
                    │ FindingsJson│
                    ├─────────────┤
                    │ patientId   │
                    │ recordingDays
                    │ totalBeats  │
                    │             │
                    │ events: [   │
                    │  ┌────────────────┐
                    │  │ FlaggedEvent   │
                    │  ├────────────────┤
                    │  │ eventId        │
                    │  │ startTime      │
                    │  │ endTime        │
                    │  │ durationSec    │
                    │  │ beatsInvolved  │
                    │  │ deviationScore │
                    │  │ contextBucket  │
                    │  │ dayIndex       │
                    │  │ hourOfDay      │
                    │  │ sleepState     │
                    │  └────────────────┘
                    │ ]              │
                    │                │
                    │ summaryStats:  │
                    │  ┌──────────────────┐
                    │  │ SummaryStats     │
                    │  ├──────────────────┤
                    │  │ totalEvents      │
                    │  │ avgDeviationScore│
                    │  │ mostCommonContext│
                    │  └──────────────────┘
                    └─────┬───────┬───────┘
                          │       │
                 ┌────────┘       └────────┐
                 ▼                         ▼
        ┌─────────────────┐      ┌─────────────────┐
        │  Agent Layer    │      │   Dashboard     │
        │  ─────────────  │      │   ──────────    │
        │  • Generate     │      │  • Timeline viz │
        │    narrative    │      │  • Event detail │
        │  • Answer       │      │  • Chat panel   │
        │    questions    │      │                 │
        └─────────────────┘      └─────────────────┘
```

## Field Addition by Module

### Module 1 + 2: Base Beat Detection
```java
BeatRecord {
  ✓ timestamp       // R-peak detection time
  ✓ rrIntervalMs    // Inter-beat interval
  ✓ qrsWidthMs      // QRS complex width
  ✓ rAmplitude      // Peak amplitude
  ✓ qualityFlag     // Quality assessment
  ✓ dayIndex        // Day number
}
```

### Module 3: Context Enrichment
```java
EnrichedBeatRecord extends BeatRecord {
  // Inherited fields...
  ✓ hourOfDay       // 0-23.99 from timestamp
  ✓ sleepState      // awake/sleep/transition
  ✓ rollingSdnn     // HRV metric (5-min window)
  ✓ rollingRmssd    // HRV metric (5-min window)
  ✓ rollingPnn50    // HRV metric (5-min window)
}
```

### Module 4: Baseline Scoring
```java
ScoredBeatRecord extends EnrichedBeatRecord {
  // Inherited fields...
  ✓ deviationScore  // Z-score from patient baseline
  ✓ contextBucket   // "sleep_night_3", etc.
}
```

### Module 5: Event Aggregation
```java
FindingsJson {
  ✓ patientId
  ✓ recordingDays
  ✓ totalBeatsProcessed
  ✓ events: List<FlaggedEvent>
  ✓ summaryStats: SummaryStats
}
```

## Example Data Transformation

### Input: Raw ECG Signal
```
Timestamp: 2024-01-05T03:15:22.145Z
ECG Sample: [0.12, 0.15, 0.89, 1.25, 0.95, 0.18, ...]
```

### After Module 1+2: BeatRecord
```java
BeatRecord(
  timestamp:    2024-01-05T03:15:22.145Z,
  rrIntervalMs: 852.0,
  qrsWidthMs:   88.0,
  rAmplitude:   1.25,
  qualityFlag:  true,
  dayIndex:     4
)
```

### After Module 3: EnrichedBeatRecord
```java
EnrichedBeatRecord(
  // ... all BeatRecord fields ...
  hourOfDay:     3.256,
  sleepState:    "sleep",
  rollingSdnn:   42.5,
  rollingRmssd:  38.2,
  rollingPnn50:  22.8
)
```

### After Module 4: ScoredBeatRecord
```java
ScoredBeatRecord(
  // ... all EnrichedBeatRecord fields ...
  deviationScore: 5.2,      // 5.2 standard deviations!
  contextBucket:  "sleep_night_3"
)
```

### After Module 5: FindingsJson
```java
FindingsJson(
  patientId:           "patient-001",
  recordingDays:       7,
  totalBeatsProcessed: 95432,
  events: [
    FlaggedEvent(
      eventId:        "550e8400-e29b-...",
      startTime:      2024-01-05T03:15:00Z,
      endTime:        2024-01-05T03:15:30Z,
      durationSec:    30.0,
      beatsInvolved:  35,
      deviationScore: 5.2,
      contextBucket:  "sleep_night_3",
      dayIndex:       4,
      hourOfDay:      3.25,
      sleepState:     "sleep"
    )
  ],
  summaryStats: SummaryStats(
    totalEvents:       1,
    avgDeviationScore: 5.2,
    mostCommonContext: "sleep_night_3"
  )
)
```

## Parallel Development Strategy

### Stream A: Modules 1+2 (Beat Detection)
**Develops:** `BeatRecord` production
**Can start:** Immediately
**Depends on:** Nothing

### Stream B: Modules 3+4+5 (Context + Scoring + Detection)
**Develops:** `EnrichedBeatRecord` → `ScoredBeatRecord` → `FindingsJson`
**Can start:** Immediately with mock `BeatRecord` data
**Depends on:** Mock data generator

### Stream C: Agent Layer
**Develops:** Clinical narrative generation
**Can start:** Immediately with mock `FindingsJson`
**Depends on:** Mock data generator

### Stream D: Dashboard
**Develops:** Timeline visualization and chat UI
**Can start:** Immediately with mock `FindingsJson`
**Depends on:** Mock data generator

## Type Safety Benefits

The locked schema contracts provide compile-time safety:

```java
// ✓ This compiles - correct field access
double rr = beatRecord.rrIntervalMs();

// ✗ Compile error - BeatRecord doesn't have hourOfDay yet
double hour = beatRecord.hourOfDay();

// ✓ This compiles - EnrichedBeatRecord has hourOfDay
double hour = enrichedRecord.hourOfDay();

// ✗ Compile error - Records are immutable
beatRecord.setRrIntervalMs(900.0);
```

## Requirements Mapping

| Record Type         | Satisfies Requirements |
|---------------------|------------------------|
| BeatRecord          | 2.5                    |
| EnrichedBeatRecord  | 3.6                    |
| ScoredBeatRecord    | 4.4                    |
| FlaggedEvent        | 5.2, 5.3               |
| SummaryStats        | 5.6                    |
| FindingsJson        | 5.5                    |

## See Also

- [README.md](README.md) - Detailed API documentation
- [SchemaExample.java](SchemaExample.java) - Runnable example code
- [SchemaContractsTest.java](../../test/java/holter/schema/SchemaContractsTest.java) - Test suite
