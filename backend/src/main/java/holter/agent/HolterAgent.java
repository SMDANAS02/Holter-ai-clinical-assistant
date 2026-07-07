package holter.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import holter.schema.FindingsJson;
import holter.schema.ExternalReportSummary;
import holter.schema.FlaggedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Stream C – Tasks 11.2–11.4: LLM-powered clinical narrative generation.
 * <p>
 * Wraps {@link GrokClient} to provide two domain-specific operations:
 * <ul>
 *   <li>{@link #generateNarrative(FindingsJson)} – produces a structured clinical
 *       summary of the 30-day Holter findings.</li>
 *   <li>{@link #answerQuestion(String, FindingsJson)} – answers a clinician's
 *       free-text question grounded in the findings data.</li>
 * </ul>
 *
 * <p>Supports Grok AI provider with automatic fallback to offline mode.
 *
 * <p>If the API key is unavailable the methods return graceful fallback strings
 * so the dashboard can still run without credentials.
 *
 * <p>If there are no flagged events the fixed message
 * <em>"No anomalies detected above threshold"</em> is returned immediately
 * without calling the API.
 */
public class HolterAgent {

    private static final Logger logger = LoggerFactory.getLogger(HolterAgent.class);

    // -----------------------------------------------------------------------
    // Fallback message (Task 11.4)
    // -----------------------------------------------------------------------

    public static final String NO_ANOMALIES_MESSAGE = "No anomalies detected above threshold.";

    // -----------------------------------------------------------------------
    // System prompt
    // -----------------------------------------------------------------------

    private static final String SYSTEM_PROMPT = """
        You are a board-certified cardiologist reviewing a patient's Holter monitor report.
        You communicate clearly with both clinicians and patients.
        Always ground your responses in the provided data.
        Never fabricate medical facts beyond what is provided.
        Use precise medical terminology where appropriate, but explain it briefly.
        Structure your responses with clear headings when writing reports.
        """;

    // -----------------------------------------------------------------------
    // AI Provider enum
    // -----------------------------------------------------------------------

    public enum AiProvider {
        GROQ, OFFLINE
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private GroqClient groqClient;
    private final ObjectMapper mapper;
    private AiProvider selectedProvider;
    
    static {
        // Load API key from environment
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            System.setProperty("groq.api.key", apiKey);
        }
    }

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** Create an agent that auto-loads API keys from environment and selects best available provider. */
    public HolterAgent() {
        this(new GroqClient());
    }

    /** Create an agent with pre-configured Groq client. */
    public HolterAgent(GroqClient groqClient) {
        this.groqClient = groqClient;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        selectBestProvider();
    }

    // -----------------------------------------------------------------------
    // Provider selection and availability
    // -----------------------------------------------------------------------

    /**
     * Automatically select the best available AI provider.
     * Priority: Groq > Offline
     */
    private void selectBestProvider() {
        if (groqClient.isAvailable()) {
            this.selectedProvider = AiProvider.GROQ;
            logger.info("Selected provider: Groq");
        } else {
            this.selectedProvider = AiProvider.OFFLINE;
            logger.info("Selected provider: Offline (no API keys configured)");
        }
    }

    /**
     * Explicitly set the AI provider to use.
     * @param provider The provider to use
     */
    public void setProvider(AiProvider provider) {
        switch (provider) {
            case GROQ:
                if (!groqClient.isAvailable()) {
                    throw new IllegalStateException("Groq API key not configured");
                }
                break;
            case OFFLINE:
                break;
        }
        this.selectedProvider = provider;
        logger.info("Provider set to: {}", provider);
    }

    /**
     * Set Groq API key directly (for dashboard integration).
     */
    public void setGroqApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            this.groqClient = new GroqClient(apiKey);
            logger.info("Groq API key set from parameter");
            selectBestProvider();  // Re-select provider after changing key
        }
    }

    /**
     * Get the currently selected provider.
     */
    public AiProvider getProvider() {
        return selectedProvider;
    }

    /**
     * Check which providers are available.
     */
    public boolean isProviderAvailable(AiProvider provider) {
        return switch (provider) {
            case GROQ -> groqClient.isAvailable();
            case OFFLINE -> true;
        };
    }


    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generate a clinical narrative summary from the Holter findings.
     *
     * @param findings  Populated {@link FindingsJson}
     * @return Clinical narrative string (may be a fallback if API unavailable)
     */
    public String generateNarrative(FindingsJson findings) {
        // Task 11.4: Zero-event case
        if (findings.events() == null || findings.events().isEmpty()) {
            logger.info("No events found – returning zero-event message");
            return NO_ANOMALIES_MESSAGE;
        }

        String prompt = buildNarrativePrompt(findings);

        try {
            return switch (selectedProvider) {
                case GROQ -> {
                    logger.info("Generating narrative via Groq API...");
                    String narrative = groqClient.complete(SYSTEM_PROMPT, prompt);
                    logger.info("Narrative generation complete ({} chars)", narrative.length());
                    yield narrative;
                }
                case OFFLINE -> generateOfflineNarrative(findings);
            };
        } catch (IOException e) {
            logger.error("API call failed; falling back to offline narrative", e);
            return generateOfflineNarrative(findings);
        }
    }

        private void appendExternalReportSummary(StringBuilder sb, ExternalReportSummary summary) {
                sb.append("- Patient identifier reported in PDF: ")
                    .append(summary.extractedPatientIdentifier() == null ? "n/a" : summary.extractedPatientIdentifier())
                    .append('\n');
                sb.append("- Reported recording duration: ")
                    .append(summary.recordingDuration() == null ? "n/a" : summary.recordingDuration())
                    .append('\n');
                sb.append("- Reported total beats: ")
                    .append(summary.totalBeats() == null ? "n/a" : summary.totalBeats())
                    .append('\n');
                sb.append("- Reported average heart rate: ")
                    .append(summary.averageHeartRate() == null ? "n/a" : summary.averageHeartRate())
                    .append('\n');
                sb.append("- Reported minimum heart rate: ")
                    .append(summary.minHeartRate() == null ? "n/a" : summary.minHeartRate())
                    .append('\n');
                sb.append("- Reported maximum heart rate: ")
                    .append(summary.maxHeartRate() == null ? "n/a" : summary.maxHeartRate())
                    .append('\n');
                sb.append("- Reported arrhythmia summary: ")
                    .append(summary.arrhythmiaSummary() == null ? "n/a" : summary.arrhythmiaSummary())
                    .append('\n');
                sb.append("- Reported HRV metrics: ")
                    .append(summary.hrvMetrics() == null ? "n/a" : summary.hrvMetrics())
                    .append('\n');
                sb.append("- Extraction note: ")
                    .append(summary.extractionNotes() == null ? "n/a" : summary.extractionNotes())
                    .append('\n');
        }
    /**
     * Answer a clinician's question grounded in the Holter findings.
     *
     * @param question  Free-text question
     * @param findings  Holter findings context
     * @return Grounded answer string
     */
    public String answerQuestion(String question, FindingsJson findings) {
        // Zero-event case
        if (findings.events() == null || findings.events().isEmpty()) {
            return NO_ANOMALIES_MESSAGE + " There is no event data to answer questions about.";
        }

        String prompt = buildQuestionPrompt(question, findings);

        try {
            return switch (selectedProvider) {
                case GROQ -> {
                    logger.info("Answering question via Groq API: {}", question);
                    String answer = groqClient.complete(SYSTEM_PROMPT, prompt);
                    logger.info("Answer generated ({} chars)", answer.length());
                    yield answer;
                }
                case OFFLINE -> "[API key not configured] Cannot answer question: " + question;
            };
        } catch (IOException e) {
            logger.error("API call failed for Q&A", e);
            return "Error contacting AI service: " + e.getMessage();
        }
    }

    // -----------------------------------------------------------------------
    // Prompt builders
    // -----------------------------------------------------------------------

    private String buildNarrativePrompt(FindingsJson findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please produce a structured Holter monitor report for the following findings.\n\n");
        sb.append("## Patient & Recording\n");
        sb.append("- Patient ID: ").append(findings.patientId()).append('\n');
        sb.append("- Recording duration: ").append(findings.recordingDays()).append(" day(s)\n");
        sb.append("- Total beats processed: ").append(findings.totalBeatsProcessed()).append('\n');
        sb.append('\n');

        sb.append("## Summary Statistics\n");
        if (findings.summaryStats() != null) {
            sb.append("- Total flagged events: ").append(findings.summaryStats().totalEvents()).append('\n');
            sb.append("- Average deviation score: ").append(
                String.format("%.2f", findings.summaryStats().avgDeviationScore())).append('\n');
            sb.append("- Most common context: ").append(findings.summaryStats().mostCommonContext()).append('\n');
        }
        if (findings.externalReportSummary() != null) {
            sb.append('\n');
            sb.append("## External Report Summary (PDF)\n");
            appendExternalReportSummary(sb, findings.externalReportSummary());
        }
        sb.append('\n');

        sb.append("## Flagged Events (up to 10 shown)\n");
        int shown = Math.min(10, findings.events().size());
        for (int i = 0; i < shown; i++) {
            FlaggedEvent e = findings.events().get(i);
            sb.append(String.format(
                "Event %d: Day %d, %s, %.0f s duration, %d beats, score=%.2f, context=%s\n",
                i + 1, e.dayIndex(), e.sleepState(),
                e.durationSec(), e.beatsInvolved(),
                e.deviationScore(), e.contextBucket()));
        }
        if (findings.events().size() > 10) {
            sb.append("... and ").append(findings.events().size() - 10).append(" more events.\n");
        }
        sb.append('\n');
        sb.append("Please include: overall rhythm assessment, notable findings, clinical significance, ");
        sb.append("and recommended follow-up actions.");

        return sb.toString();
    }

    private String buildQuestionPrompt(String question, FindingsJson findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("The clinician has the following question about a patient's Holter findings:\n\n");
        sb.append("**Question:** ").append(question).append("\n\n");
        sb.append("## Available Findings Data\n");
        sb.append("- Patient ID: ").append(findings.patientId()).append('\n');
        sb.append("- Recording: ").append(findings.recordingDays()).append(" days, ")
          .append(findings.totalBeatsProcessed()).append(" beats\n");
        if (findings.summaryStats() != null) {
            sb.append("- Events: ").append(findings.summaryStats().totalEvents()).append('\n');
            sb.append("- Avg deviation: ").append(
                String.format("%.2f", findings.summaryStats().avgDeviationScore())).append('\n');
        }
        sb.append("- Top events:\n");
        int shown = Math.min(5, findings.events().size());
        for (int i = 0; i < shown; i++) {
            FlaggedEvent e = findings.events().get(i);
            sb.append(String.format(
                "  * Day %d at %.1fh, %s state, score=%.2f, %d beats\n",
                e.dayIndex(), e.hourOfDay(), e.sleepState(), e.deviationScore(), e.beatsInvolved()));
        }
        sb.append("\nPlease answer the question precisely, grounded only in the data above.");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Offline fallback
    // -----------------------------------------------------------------------

    private String generateOfflineNarrative(FindingsJson findings) {
        int n = findings.events() == null ? 0 : findings.events().size();
        return String.format(
            "[Offline mode – API key not configured]\n\n" +
            "Holter Monitor Report – Patient %s\n" +
            "Recording: %d day(s), %d beats processed.\n" +
            "Flagged events: %d\n" +
            "Please configure ANTHROPIC_API_KEY to enable AI-generated narratives.",
            findings.patientId(),
            findings.recordingDays(),
            findings.totalBeatsProcessed(),
            n
        );
    }
}
