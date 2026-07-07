/**
 * Locked data schema contracts for the Holter Monitor AI Pipeline.
 * 
 * <p>This package defines immutable Java records that serve as data contracts
 * between pipeline modules, enabling parallel development. Each module receives
 * a record type, enriches it with additional fields, and passes the enhanced
 * version to the next module.
 * 
 * <h2>Pipeline Data Flow</h2>
 * <pre>
 * Module 1 (HolterStream) + Module 2 (BeatExtractor)
 *     ↓ produces
 * {@link holter.schema.BeatRecord}
 *     ↓ enriched by Module 3 (ContextEnricher)
 * {@link holter.schema.EnrichedBeatRecord}
 *     ↓ scored by Module 4 (BaselineModel)
 * {@link holter.schema.ScoredBeatRecord}
 *     ↓ aggregated by Module 5 (AnomalyDetector)
 * {@link holter.schema.FindingsJson}
 *     ↓ consumed by
 * Agent Layer + Dashboard
 * </pre>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Immutability:</b> All records are immutable to prevent accidental modifications</li>
 *   <li><b>Type Safety:</b> Strong typing catches integration errors at compile time</li>
 *   <li><b>Locked Schemas:</b> Field additions are one-way; existing fields never change</li>
 *   <li><b>Factory Methods:</b> Helper methods like {@code fromBeatRecord()} simplify record creation</li>
 * </ul>
 * 
 * @since 1.0
 */
package holter.schema;
