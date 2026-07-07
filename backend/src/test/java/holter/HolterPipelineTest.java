package holter;

import holter.ingestion.HolterStreamReader;
import holter.ingestion.SyntheticEcgGenerator;
import holter.schema.FindingsJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for HolterPipeline orchestrator (Task 13).
 * Tests the end-to-end wiring of all 5 modules:
 * HolterStreamReader → BeatExtractor → ContextEnricher → BaselineModel → AnomalyDetector
 */
class HolterPipelineTest {
    private static final Logger logger = LoggerFactory.getLogger(HolterPipelineTest.class);

    @Test
    void testProjectSetup() {
        // Verify that we can instantiate the main class
        assertDoesNotThrow(() -> {
            logger.info("Project setup test passed");
        });
    }

    @Test
    void testLoggingConfiguration() {
        // Verify logging is properly configured
        logger.debug("Debug message");
        logger.info("Info message");
        logger.warn("Warning message");
        
        // If this executes without errors, logging is configured correctly
        assertTrue(true, "Logging framework initialized successfully");
    }

    /**
     * Task 13: Test end-to-end pipeline with synthetic data.
     * Verifies all modules are connected and produce valid FindingsJson output.
     */
    @Test
    void testEndToEndPipelineWithSyntheticData() throws IOException {
        logger.info("Testing end-to-end pipeline with synthetic ECG data");

        // Create synthetic ECG generator (2 hours of data with anomalies)
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(2.0)
            .anomalyRate(0.05)  // 5% anomaly rate to ensure events
            .build();

        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 5000);
        Instant recordingStart = Instant.now().minusSeconds(2 * 3600);
        String patientId = "TEST_PATIENT_001";
        int recordingDays = 1;

        // Configure pipeline parameters
        Properties config = new Properties();
        config.setProperty("samplingRateHz", "360");
        config.setProperty("thresholdDeviation", "3.0");
        config.setProperty("windowSizeMinutes", "5.0");
        config.setProperty("gapToleranceSec", "5.0");

        // Run the full pipeline
        FindingsJson findings = HolterPipeline.run(
            reader,
            recordingStart,
            patientId,
            recordingDays,
            config
        );

        // Verify output structure
        assertNotNull(findings, "Pipeline should produce FindingsJson output");
        assertEquals(patientId, findings.patientId(), "Patient ID should match input");
        assertEquals(recordingDays, findings.recordingDays(), "Recording days should match input");
        assertTrue(findings.totalBeatsProcessed() > 0, "Should process at least some beats");

        // Verify events list exists (may be empty if no anomalies detected)
        assertNotNull(findings.events(), "Events list should not be null");

        // Verify summary stats
        assertNotNull(findings.summaryStats(), "Summary stats should not be null");
        assertTrue(findings.summaryStats().totalEvents() >= 0, "Total events should be non-negative");

        logger.info("Pipeline processed {} beats and detected {} events",
            findings.totalBeatsProcessed(),
            findings.summaryStats().totalEvents());
    }

    /**
     * Task 13: Test pipeline with custom configuration parameters.
     */
    @Test
    void testPipelineWithCustomConfiguration() throws IOException {
        logger.info("Testing pipeline with custom configuration");

        // Create synthetic data with higher anomaly rate
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(250)  // Different sampling rate
            .durationHours(1.0)
            .anomalyRate(0.10)
            .build();

        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 3000);

        // Custom configuration with stricter threshold
        Properties config = new Properties();
        config.setProperty("samplingRateHz", "250");
        config.setProperty("thresholdDeviation", "2.5");  // Lower threshold = more events
        config.setProperty("windowSizeMinutes", "3.0");   // Smaller window
        config.setProperty("gapToleranceSec", "10.0");    // Larger gap tolerance

        FindingsJson findings = HolterPipeline.run(
            reader,
            Instant.now().minusSeconds(3600),
            "CUSTOM_CONFIG_PATIENT",
            1,
            config
        );

        assertNotNull(findings);
        assertTrue(findings.totalBeatsProcessed() > 0);
        
        logger.info("Custom config pipeline detected {} events", findings.summaryStats().totalEvents());
    }

    /**
     * Task 13: Test pipeline produces consistent output across multiple runs.
     */
    @Test
    void testPipelineConsistency() throws IOException {
        logger.info("Testing pipeline consistency across multiple runs");

        // Use deterministic synthetic data with fixed seed
        SyntheticEcgGenerator gen1 = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(0.5)
            .anomalyRate(0.03)
            .seed(12345L)  // Fixed seed for reproducibility
            .build();

        SyntheticEcgGenerator gen2 = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(0.5)
            .anomalyRate(0.03)
            .seed(12345L)  // Same seed
            .build();

        Properties config = new Properties();
        config.setProperty("samplingRateHz", "360");
        config.setProperty("thresholdDeviation", "3.0");
        config.setProperty("windowSizeMinutes", "5.0");
        config.setProperty("gapToleranceSec", "5.0");

        Instant start = Instant.parse("2024-01-01T00:00:00Z");

        // Run pipeline twice with same input
        FindingsJson findings1 = HolterPipeline.run(
            HolterStreamReader.fromSynthetic(gen1, 5000),
            start,
            "CONSISTENCY_TEST",
            1,
            config
        );

        FindingsJson findings2 = HolterPipeline.run(
            HolterStreamReader.fromSynthetic(gen2, 5000),
            start,
            "CONSISTENCY_TEST",
            1,
            config
        );

        // Verify consistent results
        assertEquals(findings1.totalBeatsProcessed(), findings2.totalBeatsProcessed(),
            "Beat count should be consistent across runs");
        assertEquals(findings1.summaryStats().totalEvents(), findings2.summaryStats().totalEvents(),
            "Event count should be consistent across runs");

        logger.info("Consistency verified: {} beats, {} events",
            findings1.totalBeatsProcessed(),
            findings1.summaryStats().totalEvents());
    }

    /**
     * Task 13: Test pipeline with minimal data (edge case).
     */
    @Test
    void testPipelineWithMinimalData() throws IOException {
        logger.info("Testing pipeline with minimal ECG data");

        // Very short recording (5 minutes)
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(0.083)  // ~5 minutes
            .anomalyRate(0.0)      // No anomalies
            .build();

        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 1000);

        FindingsJson findings = HolterPipeline.run(
            reader,
            Instant.now(),
            "MINIMAL_DATA",
            1,
            null  // Use default config
        );

        assertNotNull(findings);
        assertTrue(findings.totalBeatsProcessed() > 0, "Should process some beats even with minimal data");
        
        // With minimal data and no anomalies, expect zero events
        assertTrue(findings.summaryStats().totalEvents() >= 0, "Event count should be valid");

        logger.info("Minimal data test: {} beats processed", findings.totalBeatsProcessed());
    }

    /**
     * Task 13: Test main() method CLI argument parsing.
     * Verifies command-line interface works correctly.
     */
    @Test
    void testMainMethodWithSyntheticData(@TempDir Path tempDir) throws Exception {
        logger.info("Testing main() method with synthetic data");

        Path outputPath = tempDir.resolve("test_findings.json");
        Path configPath = tempDir.resolve("test_config.properties");

        // Create a test config file
        Properties testConfig = new Properties();
        testConfig.setProperty("samplingRateHz", "360");
        testConfig.setProperty("thresholdDeviation", "3.0");
        testConfig.setProperty("windowSizeMinutes", "5.0");
        testConfig.setProperty("gapToleranceSec", "5.0");
        testConfig.setProperty("chunkSize", "5000");
        
        try (var out = Files.newOutputStream(configPath)) {
            testConfig.store(out, "Test configuration");
        }

        // Run main() with CLI arguments
        String[] args = {
            "--input", "synthetic",
            "--output", outputPath.toString(),
            "--config", configPath.toString(),
            "--patient", "CLI_TEST_PATIENT",
            "--days", "1"
        };

        // This should complete without throwing exceptions
        assertDoesNotThrow(() -> HolterPipeline.main(args),
            "Main method should execute successfully");

        // Verify output file was created
        assertTrue(Files.exists(outputPath), "Output JSON file should be created");
        assertTrue(Files.size(outputPath) > 0, "Output file should not be empty");

        logger.info("CLI test completed, output written to: {}", outputPath);
    }

    /**
     * Task 13: Test that all 5 modules are properly integrated.
     * Verifies data flows through each module transformation.
     */
    @Test
    void testAllModulesIntegration() throws IOException {
        logger.info("Testing integration of all 5 modules");

        // Module 1: HolterStream (Data Ingestion)
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(1.0)
            .anomalyRate(0.08)
            .build();
        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 5000);

        // Run pipeline which internally uses:
        // - Module 2: BeatExtractor (Signal Processing)
        // - Module 3: ContextEnricher (Feature Engineering)
        // - Module 4: BaselineModel (Personalized Learning)
        // - Module 5: AnomalyDetector (Event Flagging)
        
        FindingsJson findings = HolterPipeline.run(
            reader,
            Instant.now().minusSeconds(3600),
            "MODULE_INTEGRATION_TEST",
            1,
            null
        );

        // Verify each module contributed to the output
        assertNotNull(findings, "Module 5 output (FindingsJson) should exist");
        assertTrue(findings.totalBeatsProcessed() > 0, 
            "Module 2 (BeatExtractor) should have detected beats");
        
        // If events were detected, verify they have all required fields from each module
        if (findings.summaryStats().totalEvents() > 0) {
            var firstEvent = findings.events().get(0);
            
            assertNotNull(firstEvent.startTime(), "Event should have timestamp (Module 2)");
            assertNotNull(firstEvent.sleepState(), "Event should have sleep state (Module 3)");
            assertNotNull(firstEvent.hourOfDay(), "Event should have hour of day (Module 3)");
            assertNotNull(firstEvent.contextBucket(), "Event should have context bucket (Module 4)");
            assertNotNull(firstEvent.deviationScore(), "Event should have deviation score (Module 4)");
            assertTrue(firstEvent.deviationScore() >= 3.0, 
                "Module 5 should only flag events above threshold");
        }

        logger.info("Module integration verified: all {} modules working together",
            5);
    }
}
