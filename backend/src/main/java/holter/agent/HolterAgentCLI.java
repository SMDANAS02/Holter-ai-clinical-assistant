package holter.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import holter.schema.FindingsJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line interface for {@link HolterAgent}.
 * <p>
 * Enables the Python dashboard to invoke the Java agent via subprocess calls.
 *
 * <p><strong>Usage:</strong>
 * <pre>
 *   # Generate narrative
 *   java -cp holter.jar holter.agent.HolterAgentCLI --findings findings.json --narrative
 *
 *   # Answer question
 *   java -cp holter.jar holter.agent.HolterAgentCLI --findings findings.json --question "How many events during sleep?"
 *
 *   # With specific provider
 *   java -cp holter.jar holter.agent.HolterAgentCLI --findings findings.json --question "..." --provider gemini
 * </pre>
 */
public class HolterAgentCLI {

    private static final Logger logger = LoggerFactory.getLogger(HolterAgentCLI.class);

    public static void main(String[] args) {
        try {
            // Ensure UTF-8 output encoding for cross-platform compatibility
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
            
            // Parse arguments
            String findingsPath = null;
            String question = null;
            boolean narrative = false;
            String provider = null;
            String groqKey = null;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--findings":
                        if (i + 1 < args.length) findingsPath = args[++i];
                        break;
                    case "--question":
                        if (i + 1 < args.length) question = args[++i];
                        break;
                    case "--narrative":
                        narrative = true;
                        break;
                    case "--provider":
                        if (i + 1 < args.length) provider = args[++i];
                        break;
                    case "--groq-key":
                        if (i + 1 < args.length) groqKey = args[++i];
                        break;
                    case "--help":
                    case "-h":
                        printUsage();
                        return;
                }
            }

            // Validate inputs
            if (findingsPath == null) {
                System.err.println("Error: --findings path is required");
                printUsage();
                System.exit(1);
            }

            if (!narrative && question == null) {
                System.err.println("Error: Either --narrative or --question must be specified");
                printUsage();
                System.exit(1);
            }

            // Load findings
            Path path = Paths.get(findingsPath);
            if (!Files.exists(path)) {
                System.err.println("Error: Findings file not found: " + findingsPath);
                System.exit(1);
            }

            ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
            FindingsJson findings = mapper.readValue(path.toFile(), FindingsJson.class);

            // Create agent and process request
            HolterAgent agent = new HolterAgent();

            // Set Groq API key if provided
            if (groqKey != null && !groqKey.isEmpty()) {
                agent.setGroqApiKey(groqKey);
            }

            // Set provider if specified
            if (provider != null) {
                try {
                    HolterAgent.AiProvider aiProvider = HolterAgent.AiProvider.valueOf(provider.toUpperCase());
                    agent.setProvider(aiProvider);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: Unknown provider '" + provider + "'. Valid options: groq, offline");
                    System.exit(1);
                } catch (IllegalStateException e) {
                    System.err.println("Error: " + e.getMessage());
                    System.exit(1);
                }
            }

            if (narrative) {
                String result = agent.generateNarrative(findings);
                System.out.println(result);
            } else {
                String result = agent.answerQuestion(question, findings);
                System.out.println(result);
            }

        } catch (IOException e) {
            logger.error("Failed to process request", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java holter.agent.HolterAgentCLI [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --findings <path>        Path to findings.json file (required)");
        System.out.println("  --narrative              Generate clinical narrative");
        System.out.println("  --question <text>        Answer a question about the findings");
        System.out.println("  --provider <name>        AI provider: groq, offline (default: auto-select)");
        System.out.println("  --groq-key <key>         Groq API key");
        System.out.println("  --help, -h               Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # With Groq key");
        System.out.println("  java holter.agent.HolterAgentCLI --findings findings.json --question \"Hi\" --groq-key YOUR-KEY --provider groq");
        System.out.println();
        System.out.println("  # Offline mode");
        System.out.println("  java holter.agent.HolterAgentCLI --findings findings.json --question \"Hi\" --provider offline");
    }
}
