package holter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import holter.anomaly.AnomalyDetector;
import holter.baseline.BaselineModel;
import holter.baseline.DailySummaryCalculator;
import holter.database.DatabaseWriter;
import holter.features.ContextEnricher;
import holter.ingestion.HolterStreamReader;
import holter.ingestion.SyntheticEcgGenerator;
import holter.schema.*;
import holter.signal.BeatExtractor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Phase 3 – Task 13: Main pipeline orchestrator.
 * <p>
 * Connects all modules end-to-end:
 * <pre>
 *   HolterStreamReader → BeatExtractor → ContextEnricher
 *       → BaselineModel → AnomalyDetector → FindingsJson → JSON file
 * </pre>
 *
 * <p><strong>CLI usage:</strong>
 * <pre>
 *   java -jar holter-monitor.jar [OPTIONS]
 *
 *   Options:
 *     --input    &lt;path&gt;        ECG input file (.csv | .hea | "synthetic")
 *     --output   &lt;path&gt;        Output JSON file for findings (default: findings.json)
 *     --config   &lt;path&gt;        Properties file (default: config.properties)
 *     --patient  &lt;id&gt;          Patient identifier (default: PATIENT001)
 *     --days     &lt;n&gt;           Recording duration in days for synthetic mode (default: 7)
 *     --user-id  &lt;id&gt;          Authenticated Supabase user ID for database association (optional)
 * </pre>
 */
public class HolterPipeline {

    private static final Logger logger = LoggerFactory.getLogger(HolterPipeline.class);

    // -----------------------------------------------------------------------
    // Main entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        logger.info("=== Holter Monitor AI Pipeline ===");

        // Parse CLI arguments
        Map<String, String> opts = parseArgs(args);

        String input      = opts.getOrDefault("--input",   "synthetic");
        String output     = opts.getOrDefault("--output",  "findings.json");
        String configPath = opts.getOrDefault("--config",  "config.properties");
        String patientId  = opts.getOrDefault("--patient", "PATIENT001");
        String userId     = opts.get("--user-id");  // Optional, may be null
        int    days       = Integer.parseInt(opts.getOrDefault("--days", "7"));

        // Load configuration
        Properties config = loadConfig(configPath);
        int    samplingRate      = Integer.parseInt(config.getProperty("samplingRateHz",     "360"));
        double thresholdDev      = Double.parseDouble(config.getProperty("thresholdDeviation","3.0"));
        double windowMin         = Double.parseDouble(config.getProperty("windowSizeMinutes", "5.0"));
        double gapTolSec         = Double.parseDouble(config.getProperty("gapToleranceSec",   "5.0"));
        int    chunkSize         = Integer.parseInt(config.getProperty("chunkSize",          "10000"));

        logger.info("Config: input={}, output={}, patient={}, days={}, user={}", input, output, patientId, days, userId);

        // ---- Module 1: Data Ingestion ----
        logger.info("[1/5] Data ingestion...");
        HolterStreamReader reader;
        Instant recordingStart = Instant.now().minusSeconds((long)(days * 86400L));

        if ("synthetic".equalsIgnoreCase(input)) {
            logger.info("  Using synthetic ECG generator ({} days)", days);
            SyntheticEcgGenerator gen = SyntheticEcgGenerator.builder()
                .samplingRateHz(samplingRate)
                .durationHours(days * 24.0)
                .anomalyRate(0.03)
                .build();
            reader = HolterStreamReader.fromSynthetic(gen, chunkSize);
        } else if (input.endsWith(".hea")) {
            reader = HolterStreamReader.fromWfdb(Paths.get(input), chunkSize);
            recordingStart = Instant.EPOCH;
        } else {
            reader = HolterStreamReader.fromCsv(Paths.get(input), 1, true, chunkSize);
            recordingStart = Instant.EPOCH;
        }

        // ---- Module 2: Beat Extraction ----
        logger.info("[2/5] Beat extraction...");
        BeatExtractor extractor = BeatExtractor.builder()
            .samplingRateHz(samplingRate)
            .recordingStart(recordingStart)
            .build();

        List<BeatRecord> beatTable;
        try {
            beatTable = extractor.process(reader);
        } finally {
            reader.close();
        }
        logger.info("  Extracted {} beats", beatTable.size());

        if (beatTable.isEmpty()) {
            logger.warn("No beats detected. Exiting.");
            return;
        }

        // ---- Module 3: Context Enrichment ----
        logger.info("[3/5] Context enrichment...");
        ContextEnricher enricher = new ContextEnricher(windowMin);
        List<EnrichedBeatRecord> enrichedTable = enricher.enrich(beatTable);

        // ---- Module 4: Baseline Model ----
        logger.info("[4/5] Baseline modelling...");
        BaselineModel baseline = new BaselineModel();
        List<ScoredBeatRecord> scoredTable = baseline.fitAndScore(enrichedTable);

        // ---- Module 5: Anomaly Detection ----
        logger.info("[5/5] Anomaly detection...");
        AnomalyDetector detector = new AnomalyDetector(thresholdDev, gapTolSec);
        FindingsJson findings = detector.detect(scoredTable, patientId, days);

        // ---- Write output JSON ----
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

        Path outPath = Paths.get(output);
        mapper.writeValue(outPath.toFile(), findings);

        logger.info("=== Pipeline complete ===");
        logger.info("  Events detected : {}", findings.summaryStats().totalEvents());
        logger.info("  Output written  : {}", outPath.toAbsolutePath());
        System.out.println("Findings written to: " + outPath.toAbsolutePath());
        
        // ---- Write to Supabase (graceful degradation if DATABASE_URL not set) ----
        if (System.getenv("DATABASE_URL") != null && !System.getenv("DATABASE_URL").trim().isEmpty()) {
            boolean dbWriteSuccess = DatabaseWriter.writeFindings(findings, userId);
            if (dbWriteSuccess) {
                logger.info("✅ Findings also written to Supabase");
                System.out.println("Findings also synced to Supabase PostgreSQL");
            } else {
                logger.info("⚠️  Supabase write failed; local JSON backup created");
                System.out.println("Note: Database sync failed, but local findings.json saved");
            }
        } else {
            logger.info("DATABASE_URL not set; skipping Supabase sync");
        }
    }

    // -----------------------------------------------------------------------
    // Programmatic API (used by tests and dashboard)
    // -----------------------------------------------------------------------

    /**
     * Run the full pipeline programmatically and return the findings.
     *
     * @param reader         Pre-configured stream reader (will be closed after use)
     * @param recordingStart Timestamp of first ECG sample
     * @param patientId      Patient identifier
     * @param recordingDays  Recording duration in days
     * @param config         Optional overrides (may be null for defaults)
     * @return Populated {@link FindingsJson}
     */
    public static FindingsJson run(HolterStreamReader reader,
                                   Instant recordingStart,
                                   String patientId,
                                   int recordingDays,
                                   Properties config) throws IOException {
        return run(reader, recordingStart, patientId, recordingDays, config, null);
    }

    /**
     * Run the full pipeline programmatically with optional user_id for database integration.
     *
     * @param reader         Pre-configured stream reader (will be closed after use)
     * @param recordingStart Timestamp of first ECG sample
     * @param patientId      Patient identifier
     * @param recordingDays  Recording duration in days
     * @param config         Optional overrides (may be null for defaults)
     * @param userId         Optional authenticated user ID for database (may be null)
     * @return Populated {@link FindingsJson}
     */
    public static FindingsJson run(HolterStreamReader reader,
                                   Instant recordingStart,
                                   String patientId,
                                   int recordingDays,
                                   Properties config,
                                   String userId) throws IOException {
        Properties cfg = config != null ? config : new Properties();

        int    fs            = Integer.parseInt(cfg.getProperty("samplingRateHz",      "360"));
        double threshold     = Double.parseDouble(cfg.getProperty("thresholdDeviation","3.0"));
        double windowMin     = Double.parseDouble(cfg.getProperty("windowSizeMinutes", "5.0"));
        double gapTol        = Double.parseDouble(cfg.getProperty("gapToleranceSec",   "5.0"));

        BeatExtractor extractor = BeatExtractor.builder()
            .samplingRateHz(fs).recordingStart(recordingStart).build();

        List<BeatRecord> beats;
        try { beats = extractor.process(reader); } finally { reader.close(); }

        ContextEnricher enricher = new ContextEnricher(windowMin);
        List<EnrichedBeatRecord> enriched = enricher.enrich(beats);

        BaselineModel baseline = new BaselineModel();
        List<ScoredBeatRecord> scored = baseline.fitAndScore(enriched);

        AnomalyDetector detector = new AnomalyDetector(threshold, gapTol);
        FindingsJson findings = detector.detect(scored, patientId, recordingDays);
        
        // Calculate daily summaries
        DailySummaryCalculator dailyCalc = new DailySummaryCalculator();
        List<DailySummary> dailySummaries = dailyCalc.calculate(enriched, findings.events());
        
        FindingsJson result = new FindingsJson(
            findings.patientId(),
            findings.recordingDays(),
            findings.totalBeatsProcessed(),
            findings.events(),
            findings.summaryStats(),
            dailySummaries,
            null
        );
        
        // If userId provided and DATABASE_URL is set, write to database
        if (userId != null && System.getenv("DATABASE_URL") != null) {
            boolean dbWriteSuccess = DatabaseWriter.writeFindings(result, userId);
            if (dbWriteSuccess) {
                LoggerFactory.getLogger(HolterPipeline.class).info("✅ Findings written to database with user_id={}", userId);
            }
        }
        
        return result;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Parse "--key value" style CLI arguments into a map. */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            if (args[i].startsWith("--")) map.put(args[i], args[i + 1]);
        }
        return map;
    }

    /** Load a properties file, returning an empty Properties if not found. */
    private static Properties loadConfig(String path) {
        Properties props = new Properties();
        Path p = Paths.get(path);
        if (Files.exists(p)) {
            try (InputStream is = Files.newInputStream(p)) {
                props.load(is);
                logger.info("Config loaded from {}", p.toAbsolutePath());
            } catch (IOException e) {
                logger.warn("Could not load config file {}: {}", path, e.getMessage());
            }
        } else {
            logger.info("No config file found at {}; using defaults", path);
        }
        return props;
    }
}
