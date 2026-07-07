/**
 * Mock data generators for parallel development and testing.
 * 
 * <p>This package provides utilities for generating realistic synthetic ECG data
 * and findings that enable parallel development of downstream modules without
 * waiting for upstream signal processing modules to be completed.
 * 
 * <h2>Key Classes:</h2>
 * <ul>
 *   <li>{@link holter.mock.MockBeatTableGenerator} - Generates synthetic BeatRecord data
 *       with realistic ECG metrics and configurable anomaly rates</li>
 *   <li>{@link holter.mock.MockFindingsGenerator} - Generates synthetic FindingsJson
 *       with flagged events and summary statistics</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Generate 10,000 beats with 5% anomaly rate
 * MockBeatTableGenerator beatGen = MockBeatTableGenerator.builder()
 *     .numberOfBeats(10000)
 *     .anomalyRate(0.05)
 *     .startTime(Instant.parse("2024-01-01T00:00:00Z"))
 *     .build();
 * List<BeatRecord> beats = beatGen.generateWithCircadianRhythm();
 * 
 * // Generate findings with 15 flagged events
 * MockFindingsGenerator findingsGen = MockFindingsGenerator.builder()
 *     .numberOfEvents(15)
 *     .patientId("patient-001")
 *     .recordingDays(7)
 *     .totalBeatsProcessed(100000L)
 *     .build();
 * FindingsJson findings = findingsGen.generate();
 * }</pre>
 * 
 * @see holter.schema
 */
package holter.mock;
