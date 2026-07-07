# Mock Data Generators

This package provides mock data generators that enable parallel development of downstream modules without waiting for upstream signal processing modules to be completed.

## Overview

The mock generators create realistic synthetic ECG data that conforms to the locked schema contracts defined in `holter.schema`. This allows developers to:

- Build and test ContextEnricher, BaselineModel, and AnomalyDetector modules independently
- Develop the Agent Layer and Dashboard with sample findings data
- Run integration tests before signal processing modules are complete
- Experiment with different data scenarios (anomaly rates, recording durations, etc.)

## Available Generators

### MockBeatTableGenerator

Generates `List<BeatRecord>` with realistic synthetic ECG beat data.

**Features:**
- Configurable number of beats
- Configurable anomaly rate (0.0 to 1.0)
- Physiologically realistic metrics (RR interval, QRS width, R amplitude)
- Random noise/artifact injection (~2% of beats)
- Optional circadian rhythm patterns (lower HR during sleep hours)
- Reproducible results with seed control

**Basic Usage:**

```java
// Generate 10,000 beats with 5% anomaly rate
MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
    .numberOfBeats(10000)
    .anomalyRate(0.05)
    .startTime(Instant.parse("2024-01-01T00:00:00Z"))
    .build();

List<BeatRecord> beats = generator.generate();
```

**With Circadian Rhythm:**

```java
// Generate beats with realistic day/night heart rate variation
List<BeatRecord> beats = generator.generateWithCircadianRhythm();
```

**Custom Parameters:**

```java
MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
    .numberOfBeats(50000)           // 50k beats (~10 hours at 70 bpm)
    .anomalyRate(0.1)               // 10% anomalous beats
    .startTime(Instant.parse("2024-01-01T00:00:00Z"))
    .randomSeed(12345)              // For reproducible results
    .build();

List<BeatRecord> beats = generator.generate();
```

### MockFindingsGenerator

Generates `FindingsJson` with realistic flagged anomalous events.

**Features:**
- Configurable number of events
- Automatic summary statistics calculation
- Realistic temporal distribution of events
- Context-aware event generation (sleep state, hour of day)
- Optional event clustering (multiple events near same time)
- Reproducible results with seed control

**Basic Usage:**

```java
// Generate findings with 15 flagged events
MockFindingsGenerator generator = MockFindingsGenerator.builder()
    .numberOfEvents(15)
    .patientId("patient-001")
    .recordingDays(7)
    .totalBeatsProcessed(100000L)
    .startTime(Instant.parse("2024-01-01T00:00:00Z"))
    .build();

FindingsJson findings = generator.generate();
```

**With Event Clustering:**

```java
// Generate events in temporal clusters (useful for testing dashboard)
FindingsJson findings = generator.generateWithClustering();
```

**Custom Parameters:**

```java
MockFindingsGenerator generator = MockFindingsGenerator.builder()
    .numberOfEvents(50)             // 50 anomalous events
    .patientId("patient-test-456")
    .recordingDays(30)              // 30-day recording
    .totalBeatsProcessed(3000000L)  // ~3M beats
    .startTime(Instant.parse("2024-01-01T00:00:00Z"))
    .randomSeed(67890)              // For reproducible results
    .build();

FindingsJson findings = generator.generate();
```

**Zero Events (No Anomalies):**

```java
// Generate findings with no detected anomalies
MockFindingsGenerator generator = MockFindingsGenerator.builder()
    .numberOfEvents(0)
    .patientId("patient-normal-001")
    .recordingDays(7)
    .totalBeatsProcessed(100000L)
    .build();

FindingsJson findings = generator.generate();
// findings.events() will be empty
// findings.summaryStats().mostCommonContext() will be "none"
```

## Usage Examples

### Testing ContextEnricher

```java
// Generate input data
MockBeatTableGenerator beatGen = MockBeatTableGenerator.builder()
    .numberOfBeats(1000)
    .anomalyRate(0.0)  // No anomalies for baseline testing
    .build();

List<BeatRecord> beats = beatGen.generateWithCircadianRhythm();

// Test your ContextEnricher
ContextEnricher enricher = new ContextEnricher();
List<EnrichedBeatRecord> enriched = enricher.enrich(beats);

// Verify enrichment
assertNotNull(enriched.get(0).hourOfDay());
assertNotNull(enriched.get(0).sleepState());
```

### Testing Dashboard with Sample Findings

```java
// Generate sample findings for dashboard development
MockFindingsGenerator findingsGen = MockFindingsGenerator.builder()
    .numberOfEvents(20)
    .patientId("dashboard-test-001")
    .recordingDays(30)
    .totalBeatsProcessed(250000L)
    .build();

FindingsJson findings = findingsGen.generateWithClustering();

// Use findings in your dashboard
Dashboard dashboard = new Dashboard();
dashboard.loadFindings(findings);
dashboard.render();
```

### Testing Agent Layer

```java
// Generate findings for agent testing
MockFindingsGenerator findingsGen = MockFindingsGenerator.builder()
    .numberOfEvents(15)
    .patientId("agent-test-001")
    .recordingDays(7)
    .totalBeatsProcessed(100000L)
    .build();

FindingsJson findings = findingsGen.generate();

// Test agent narrative generation
HolterAgent agent = new HolterAgent();
String narrative = agent.generateNarrative(findings);

assertNotNull(narrative);
assertTrue(narrative.contains("15"));  // Should mention event count
```

### Creating Reproducible Test Scenarios

```java
// Same seed produces identical results
long seed = 42;

MockBeatTableGenerator gen1 = MockBeatTableGenerator.builder()
    .numberOfBeats(1000)
    .randomSeed(seed)
    .build();

MockBeatTableGenerator gen2 = MockBeatTableGenerator.builder()
    .numberOfBeats(1000)
    .randomSeed(seed)
    .build();

List<BeatRecord> beats1 = gen1.generate();
List<BeatRecord> beats2 = gen2.generate();

assertEquals(beats1, beats2);  // Identical data
```

## Data Characteristics

### MockBeatTableGenerator

- **RR Interval**: 300-2000 ms (physiologically plausible)
- **Baseline HR**: 70 bpm (~857 ms RR interval)
- **HRV**: ±50 ms standard deviation for normal beats
- **Anomalous Beats**: 5x higher variance in RR intervals
- **QRS Width**: 60-120 ms
- **R Amplitude**: 0.5-2.0 mV
- **Noise Rate**: ~2% of beats marked as low quality
- **Circadian Effect**: -15% HR during sleep (22:00-06:00)

### MockFindingsGenerator

- **Deviation Scores**: 3.0-8.0 (above detection threshold)
- **Event Duration**: 5-120 seconds
- **Beats per Event**: 5-100 beats
- **Sleep States**: awake, sleep, transition (based on hour of day)
- **Context Buckets**: Realistic combinations like "sleep_night_3", "awake_afternoon_14"
- **Temporal Distribution**: Uniform across recording period (or clustered)

## Best Practices

1. **Use Circadian Rhythm Generation**: When testing modules that depend on temporal patterns (ContextEnricher, BaselineModel), use `generateWithCircadianRhythm()` for more realistic data.

2. **Set Appropriate Anomaly Rates**: 
   - 0.0 for baseline testing
   - 0.05-0.1 for normal anomaly detection testing
   - 0.5+ for stress testing

3. **Match Recording Duration to Use Case**:
   - Short tests: 1,000-10,000 beats (~15 minutes - 2 hours)
   - Integration tests: 50,000-100,000 beats (~12-24 hours)
   - Full pipeline tests: 500,000+ beats (multiple days)

4. **Use Reproducible Seeds**: For unit tests and regression testing, always set a specific random seed to ensure consistent results.

5. **Test Edge Cases**:
   - Zero events (no anomalies detected)
   - Zero beats (empty input)
   - Single day vs multi-day recordings
   - High anomaly rates

## Integration with Real Pipeline

Once signal processing modules are complete, replace mock data with real pipeline outputs:

```java
// Development (using mocks)
List<BeatRecord> beats = MockBeatTableGenerator.builder()
    .numberOfBeats(10000)
    .build()
    .generate();

// Production (using real data)
HolterStreamReader reader = new HolterStreamReader("patient-001.wfdb");
BeatExtractor extractor = new BeatExtractor();
List<BeatRecord> beats = extractor.extractBeats(reader);
```

The locked schema contracts ensure that downstream modules work identically with both mock and real data.
