package holter.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Groq API client for clinical narrative generation.
 * Groq is a fast inference provider with models compatible with OpenAI API.
 */
public class GroqClient {
    private static final Logger logger = LoggerFactory.getLogger(GroqClient.class);

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public GroqClient() {
        this.apiKey = System.getenv("GROQ_API_KEY");
        this.httpClient = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    public GroqClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Check if API key is available.
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Generate content using Groq API.
     *
     * @param systemPrompt System instruction (prepended to user message)
     * @param userPrompt   User message
     * @return Generated text response
     * @throws IOException if API call fails
     */
    public String complete(String systemPrompt, String userPrompt) throws IOException {
        if (!isAvailable()) {
            throw new IOException("GROQ_API_KEY environment variable not set");
        }

        // Build request JSON
        String requestJson = buildRequestJson(systemPrompt, userPrompt);
        
        logger.debug("Calling Groq API with prompt length: {}", (systemPrompt + userPrompt).length());

        // Make HTTP POST request with explicit UTF-8 encoding
        RequestBody body = RequestBody.create(
            requestJson.getBytes(StandardCharsets.UTF_8),
            MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
            .url(GROQ_API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json; charset=utf-8")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? 
                    new String(response.body().bytes(), StandardCharsets.UTF_8) : 
                    "Unknown error";
                logger.error("Groq API error ({}): {}", response.code(), errorBody);
                throw new IOException("Groq API error: " + response.code() + " " + errorBody);
            }

            String responseBody = new String(response.body().bytes(), StandardCharsets.UTF_8);
            return parseResponse(responseBody);
        }
    }

    /**
     * Build JSON request body for Groq API.
     */
    private String buildRequestJson(String systemPrompt, String userPrompt) throws IOException {
        String json = String.format("""
            {
              "model": "openai/gpt-oss-120b",
              "messages": [
                {
                  "role": "system",
                  "content": %s
                },
                {
                  "role": "user",
                  "content": %s
                }
              ],
              "temperature": 0.7,
              "max_tokens": 2048
            }
            """, escapeJsonString(systemPrompt), escapeJsonString(userPrompt));
        
        return json;
    }

    /**
     * Parse Groq API response to extract text.
     */
    private String parseResponse(String responseBody) throws IOException {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            
            if (choices.isEmpty()) {
                logger.warn("No choices in Groq response");
                return "No response generated";
            }
            
            JsonNode message = choices.get(0).path("message").path("content");
            String text = message.asText();
            
            if (text.isEmpty()) {
                logger.warn("Empty text in Groq response");
                return "No text generated";
            }
            
            return text;
        } catch (Exception e) {
            logger.error("Failed to parse Groq response: {}", responseBody, e);
            throw new IOException("Failed to parse Groq response", e);
        }
    }

    /**
     * Escape string for JSON.
     */
    private String escapeJsonString(String str) {
        return "\"" + str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }
}
