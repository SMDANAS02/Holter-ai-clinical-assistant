package holter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import holter.agent.HolterAgent;
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
 * Task 16: Final End-to-End Integration Test
 * <p>
 * Comprehensive integration test that validates the entire system works end-to-end:
 * <ul>
 *   <li>Pipeline generates FindingsJson from synthetic 7-day ECG data</li>
 *   <li>FindingsJson contains all required fields with valid data</li>
 *   <li>Dashboard can load the findings file</li>
 *   <li>Agent can answer questions about the findings</li>
 *   <li>All components work together seamlessly</li>
 * </ul>
 */
class EndToEndIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(EndToEndIntegrationTest.class);

    /**
     * Task 16.1: Run full pipeline with synthetic 7-day ECG data
     * Task 16.2: Verify FindingsJson is generated correctly
     */
    @Test
    void testFullPipelineWith7DayEcgData(@TempDir Path tempDir) throws Exception {
        logger.info("=== Task 16: End-to-End Integration Test ===");
        logger.info("Step 1/4: Running full pipeline with synthetic 7-day ECG data...");

        // Create synthetic 7-day ECG data with realistic anomaly rate
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(7 * 24.0)  // 7 days
            .anomalyRate(0.03)        // 3% anomaly rate (realistic)
            .seed(42L)                // Fixed seed for reproducibility
            .build();

        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 10000);
        Instant recordingStart = Instant.parse("2024-01-01T00:00:00Z");
        String patientId = "E2E_TEST_PATIENT_7DAY";
        int recordingDays = 7;

        // Configure pipeline with production-like settings
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

        // Verify FindingsJson structure (Task 16.2)
        logger.info("Step 2/4: Verifying FindingsJson structure...");
        
        assertNotNull(findings, "Pipeline should produce FindingsJson output");
        assertEquals(patientId, findings.patientId(), "Patient ID should match");
        assertEquals(recordingDays, findings.recordingDays(), "Recording days should be 7");
        assertTrue(findings.totalBeatsProcessed() > 0, "Should process beats");
        
        // Verify we processed approximately the right number of beats for 7 days
        // At ~70 bpm average, 7 days = 7*24*60*70 = 705,600 beats (approximate)
        long expectedBeats = 7L * 24 * 60 * 70;
        assertTrue(findings.totalBeatsProcessed() > expectedBeats * 0.5,
            String.format("Should process at least half the expected beats (%d processed)", 
                findings.totalBeatsProcessed()));
        
        // Verify events list
        assertNotNull(findings.events(), "Events list should not be null");
        
        // Verify summary stats
        assertNotNull(findings.summaryStats(), "Summary stats should not be null");
        assertEquals(findings.events().size(), findings.summaryStats().totalEvents(),
            "Summary stats totalEvents should match events list size");
        assertTrue(findings.summaryStats().totalEvents() >= 0, "Total events should be non-negative");
        
        // If events were detected, verify their structure
        if (findings.summaryStats().totalEvents() > 0) {
            logger.info("Detected {} anomalous events", findings.summaryStats().totalEvents());
            
            // Verify first event has all required fields
            var firstEvent = findings.events().get(0);
            assertNotNull(firstEvent.eventId(), "Event should have ID");
            assertNotNull(firstEvent.startTime(), "Event should have start time");
            assertNotNull(firstEvent.endTime(), "Event should have end time");
            assertTrue(firstEvent.durationSec() > 0, "Event duration should be positive");
            assertTrue(firstEvent.beatsInvolved() > 0, "Event should involve beats");
            assertTrue(firstEvent.deviationScore() >= 3.0, "Deviation score should be >= threshold");
            assertNotNull(firstEvent.contextBucket(), "Event should have context bucket");
            assertTrue(firstEvent.dayIndex() >= 0 && firstEvent.dayIndex() < 7,
                "Day index should be in [0,6] range");
            assertTrue(firstEvent.hourOfDay() >= 0 && firstEvent.hourOfDay() < 24,
                "Hour of day should be in [0,24) range");
            assertNotNull(firstEvent.sleepState(), "Event should have sleep state");
            assertTrue(
                firstEvent.sleepState().equals("sleep") ||
                firstEvent.sleepState().equals("awake") ||
                firstEvent.sleepState().equals("transition"),
                "Sleep state should be one of: sleep, awake, transition"
            );
            
            // Verify summary stats calculations
            assertTrue(findings.summaryStats().avgDeviationScore() >= 3.0,
                "Average deviation score should be >= threshold");
            assertNotNull(findings.summaryStats().mostCommonContext(),
                "Most common context should be set");
        } else {
            logger.info("No anomalous events detected (possible with low anomaly rate)");
        }

        logger.info("FindingsJson verified successfully:");
        logger.info("  - Patient: {}", findings.patientId());
        logger.info("  - Recording days: {}", findings.recordingDays());
        logger.info("  - Total beats: {}", findings.totalBeatsProcessed());
        logger.info("  - Total events: {}", findings.summaryStats().totalEvents());
        if (findings.summaryStats().totalEvents() > 0) {
            logger.info("  - Avg deviation score: {}", findings.summaryStats().avgDeviationScore());
            logger.info("  - Most common context: {}", findings.summaryStats().mostCommonContext());
        }
    }

    /**
     * Task 16.2 & 16.3: Verify FindingsJson can be written to file and loaded by dashboard
     */
    @Test
    void testFindingsJsonPersistenceAndDashboardLoading(@TempDir Path tempDir) throws Exception {
        logger.info("Step 3/4: Testing FindingsJson persistence and dashboard loading...");

        // Generate findings
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(24.0)  // 1 day for faster test
            .anomalyRate(0.05)
            .build();

        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 5000);
        FindingsJson findings = HolterPipeline.run(
            reader,
            Instant.now().minusSeconds(86400),
            "DASHBOARD_TEST",
            1,
            null
        );

        // Write to JSON file
        Path findingsPath = tempDir.resolve("findings.json");
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        
        mapper.writeValue(findingsPath.toFile(), findings);
        
        assertTrue(Files.exists(findingsPath), "Findings file should be created");
        assertTrue(Files.size(findingsPath) > 0, "Findings file should not be empty");
        
        // Verify file can be read back (simulates dashboard loading)
        FindingsJson loadedFindings = mapper.readValue(findingsPath.toFile(), FindingsJson.class);
        
        assertNotNull(loadedFindings, "Dashboard should be able to load findings");
        assertEquals(findings.patientId(), loadedFindings.patientId(),
            "Patient ID should match after reload");
        assertEquals(findings.totalBeatsProcessed(), loadedFindings.totalBeatsProcessed(),
            "Beat count should match after reload");
        assertEquals(findings.summaryStats().totalEvents(), loadedFindings.summaryStats().totalEvents(),
            "Event count should match after reload");
        
        logger.info("FindingsJson successfully persisted and reloaded from: {}", findingsPath);
    }

    /**
     * Task 16.4: Verify agent can answer questions about findings
     */
    @Test
    void testAgentQuestionAnswering() throws Exception {
        logger.info("Step 4/4: Testing agent question answering...");

        // Generate findings with known characteristics
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(48.0)  // 2 days
            .anomalyRate(0.06)    // Higher rate to ensure events
            .seed(123L)
            .build();

        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 5000);
        FindingsJson findings = HolterPipeline.run(
            reader,
            Instant.now().minusSeconds(2 * 86400),
            "AGENT_TEST",
            2,
            null
        );

        // Create agent
        HolterAgent agent = new HolterAgent();

        // Test 1: Generate narrative
        String narrative = agent.generateNarrative(findings);
        assertNotNull(narrative, "Agent should generate narrative");
        assertFalse(narrative.trim().isEmpty(), "Narrative should not be empty");
        
        if (findings.summaryStats().totalEvents() == 0) {
            assertTrue(narrative.contains("No anomalies detected") ||
                      narrative.toLowerCase().contains("no events") ||
                      narrative.toLowerCase().contains("normal"),
                "Zero-event narrative should indicate no anomalies");
        } else {
            // Narrative should mention key findings
            String narrativeLower = narrative.toLowerCase();
            assertTrue(narrativeLower.contains("event") || 
                      narrativeLower.contains("anomal") ||
                      narrativeLower.contains("detection"),
                "Narrative should mention events/anomalies");
        }
        
        logger.info("Generated narrative: {}", narrative.substring(0, Math.min(200, narrative.length())));

        // Test 2: Answer specific questions
        if (findings.summaryStats().totalEvents() > 0) {
            // Question about event count
            String answer1 = agent.answerQuestion(
                "How many anomalous events were detected?", 
                findings
            );
            assertNotNull(answer1, "Agent should answer event count question");
            assertFalse(answer1.trim().isEmpty(), "Answer should not be empty");
            logger.info("Q: How many events? A: {}", answer1.substring(0, Math.min(150, answer1.length())));

            // Question about timing
            String answer2 = agent.answerQuestion(
                "When was the most severe event?", 
                findings
            );
            assertNotNull(answer2, "Agent should answer timing question");
            assertFalse(answer2.trim().isEmpty(), "Answer should not be empty");
            logger.info("Q: Most severe event? A: {}", answer2.substring(0, Math.min(150, answer2.length())));

            // Question about context
            String answer3 = agent.answerQuestion(
                "How many events occurred during sleep?", 
                findings
            );
            assertNotNull(answer3, "Agent should answer context question");
            assertFalse(answer3.trim().isEmpty(), "Answer should not be empty");
            logger.info("Q: Sleep events? A: {}", answer3.substring(0, Math.min(150, answer3.length())));
        } else {
            // Test zero-event case (Task 11.4)
            String answer = agent.answerQuestion(
                "What anomalies were found?", 
                findings
            );
            assertNotNull(answer, "Agent should handle zero-event case");
            assertTrue(answer.contains("No anomalies detected") ||
                      answer.toLowerCase().contains("no events"),
                "Agent should indicate no anomalies for zero-event case");
            logger.info("Zero-event response: {}", answer);
        }

        logger.info("Agent successfully answered questions about findings");
    }

    /**
     * Comprehensive end-to-end test combining all Task 16 requirements
     */
    @Test
    void testCompleteEndToEndIntegration(@TempDir Path tempDir) throws Exception {
        logger.info("=== COMPREHENSIVE END-TO-END INTEGRATION TEST ===");

        // Step 1: Run pipeline with 7-day synthetic data
        logger.info("Step 1: Generating and processing 7-day ECG recording...");
        SyntheticEcgGenerator generator = SyntheticEcgGenerator.builder()
            .samplingRateHz(360)
            .durationHours(7 * 24.0)
            .anomalyRate(0.04)
            .seed(99L)
            .build();

        HolterStreamReader reader = HolterStreamReader.fromSynthetic(generator, 10000);
        FindingsJson findings = HolterPipeline.run(
            reader,
            Instant.parse("2024-01-01T00:00:00Z"),
            "COMPREHENSIVE_E2E_TEST",
            7,
            null
        );

        // Step 2: Verify FindingsJson
        logger.info("Step 2: Verifying FindingsJson integrity...");
        assertNotNull(findings);
        assertEquals(7, findings.recordingDays());
        assertTrue(findings.totalBeatsProcessed() > 100000, 
            "Should process many beats over 7 days");

        // Step 3: Persist to file (dashboard requirement)
        logger.info("Step 3: Persisting findings to JSON file...");
        Path findingsPath = tempDir.resolve("findings_e2e.json");
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(findingsPath.toFile(), findings);
        assertTrue(Files.exists(findingsPath));

        // Step 4: Load findings (simulates dashboard loading)
        logger.info("Step 4: Loading findings (simulating dashboard)...");
        FindingsJson loadedFindings = mapper.readValue(findingsPath.toFile(), FindingsJson.class);
        assertEquals(findings.summaryStats().totalEvents(), 
                    loadedFindings.summaryStats().totalEvents());

        // Step 5: Test agent interaction
        logger.info("Step 5: Testing agent interaction...");
        HolterAgent agent = new HolterAgent();
        String narrative = agent.generateNarrative(loadedFindings);
        assertNotNull(narrative);
        assertFalse(narrative.trim().isEmpty());

        if (loadedFindings.summaryStats().totalEvents() > 0) {
            String answer = agent.answerQuestion(
                "Summarize the key findings from this recording", 
                loadedFindings
            );
            assertNotNull(answer);
            assertFalse(answer.trim().isEmpty());
            logger.info("Agent summary: {}", answer.substring(0, Math.min(200, answer.length())));
        }

        // Final verification
        logger.info("=== END-TO-END INTEGRATION TEST COMPLETE ===");
        logger.info("Summary:");
        logger.info("  ✓ Pipeline processed 7-day ECG data");
        logger.info("  ✓ FindingsJson generated with {} beats", findings.totalBeatsProcessed());
        logger.info("  ✓ Detected {} anomalous events", findings.summaryStats().totalEvents());
        logger.info("  ✓ JSON persistence and loading verified");
        logger.info("  ✓ Agent narrative generation functional");
        logger.info("  ✓ Agent question answering functional");
        logger.info("  ✓ All components integrated successfully");
    }

    /**
     * Test CLI main method with full 7-day pipeline run
     */
    @Test
    void testCliMainMethodWith7DayData(@TempDir Path tempDir) throws Exception {
        logger.info("Testing CLI main() method with 7-day synthetic data...");

        Path outputPath = tempDir.resolve("findings_7day.json");
        Path configPath = tempDir.resolve("test_config.properties");

        // Create test config
        Properties testConfig = new Properties();
        testConfig.setProperty("samplingRateHz", "360");
        testConfig.setProperty("thresholdDeviation", "3.0");
        testConfig.setProperty("windowSizeMinutes", "5.0");
        testConfig.setProperty("gapToleranceSec", "5.0");
        testConfig.setProperty("chunkSize", "10000");

        try (var out = Files.newOutputStream(configPath)) {
            testConfig.store(out, "Test configuration for 7-day run");
        }

        // Run main() with 7-day synthetic data
        String[] args = {
            "--input", "synthetic",
            "--output", outputPath.toString(),
            "--config", configPath.toString(),
            "--patient", "CLI_7DAY_TEST",
            "--days", "7"
        };

        assertDoesNotThrow(() -> HolterPipeline.main(args),
            "CLI should complete 7-day pipeline without errors");

        // Verify output
        assertTrue(Files.exists(outputPath), "Output file should be created");
        assertTrue(Files.size(outputPath) > 0, "Output file should contain data");

        // Load and verify findings
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        FindingsJson findings = mapper.readValue(outputPath.toFile(), FindingsJson.class);

        assertEquals("CLI_7DAY_TEST", findings.patientId());
        assertEquals(7, findings.recordingDays());
        assertTrue(findings.totalBeatsProcessed() > 100000,
            "7-day recording should process many beats");

        logger.info("CLI test completed: {} beats, {} events",
            findings.totalBeatsProcessed(),
            findings.summaryStats().totalEvents());
    }
}
