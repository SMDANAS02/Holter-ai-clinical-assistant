package holter.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DailySummary schema.
 * Validates JSON serialization/deserialization and field mappings.
 */
class DailySummaryTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void testSerializationWithSnakeCaseFields() throws Exception {
        // Create a DailySummary with sample data
        DailySummary summary = new DailySummary(
            0,      // dayIndex
            72.5,   // avgHrBpm
            55.0,   // minHrBpm
            95.0,   // maxHrBpm
            45.2,   // avgSdnn
            38.7,   // avgRmssd
            7.5,    // sleepHoursEstimate
            3       // eventsThisDay
        );

        // Serialize to JSON
        String json = mapper.writeValueAsString(summary);

        // Verify snake_case field names are used
        assertTrue(json.contains("\"day_index\""), "Should use snake_case: day_index");
        assertTrue(json.contains("\"avg_hr_bpm\""), "Should use snake_case: avg_hr_bpm");
        assertTrue(json.contains("\"min_hr_bpm\""), "Should use snake_case: min_hr_bpm");
        assertTrue(json.contains("\"max_hr_bpm\""), "Should use snake_case: max_hr_bpm");
        assertTrue(json.contains("\"avg_sdnn\""), "Should use snake_case: avg_sdnn");
        assertTrue(json.contains("\"avg_rmssd\""), "Should use snake_case: avg_rmssd");
        assertTrue(json.contains("\"sleep_hours_estimate\""), "Should use snake_case: sleep_hours_estimate");
        assertTrue(json.contains("\"events_this_day\""), "Should use snake_case: events_this_day");

        // Verify values
        assertTrue(json.contains("72.5"), "Should contain avgHrBpm value");
        assertTrue(json.contains("7.5"), "Should contain sleepHoursEstimate value");
        assertTrue(json.contains("3"), "Should contain eventsThisDay value");
    }

    @Test
    void testDeserializationFromSnakeCaseJson() throws Exception {
        // JSON with snake_case field names (as expected from Python dashboard)
        String json = """
            {
              "day_index": 5,
              "avg_hr_bpm": 68.3,
              "min_hr_bpm": 52.0,
              "max_hr_bpm": 98.0,
              "avg_sdnn": 42.1,
              "avg_rmssd": 35.6,
              "sleep_hours_estimate": 8.2,
              "events_this_day": 1
            }
            """;

        // Deserialize from JSON
        DailySummary summary = mapper.readValue(json, DailySummary.class);

        // Verify all fields are correctly mapped
        assertEquals(5, summary.dayIndex(), "dayIndex should be 5");
        assertEquals(68.3, summary.avgHrBpm(), 0.001, "avgHrBpm should be 68.3");
        assertEquals(52.0, summary.minHrBpm(), 0.001, "minHrBpm should be 52.0");
        assertEquals(98.0, summary.maxHrBpm(), 0.001, "maxHrBpm should be 98.0");
        assertEquals(42.1, summary.avgSdnn(), 0.001, "avgSdnn should be 42.1");
        assertEquals(35.6, summary.avgRmssd(), 0.001, "avgRmssd should be 35.6");
        assertEquals(8.2, summary.sleepHoursEstimate(), 0.001, "sleepHoursEstimate should be 8.2");
        assertEquals(1, summary.eventsThisDay(), "eventsThisDay should be 1");
    }

    @Test
    void testRoundTripSerializationDeserialization() throws Exception {
        // Create original summary
        DailySummary original = new DailySummary(
            12,     // dayIndex
            75.0,   // avgHrBpm
            58.0,   // minHrBpm
            102.0,  // maxHrBpm
            48.5,   // avgSdnn
            40.2,   // avgRmssd
            6.8,    // sleepHoursEstimate
            5       // eventsThisDay
        );

        // Serialize and deserialize
        String json = mapper.writeValueAsString(original);
        DailySummary deserialized = mapper.readValue(json, DailySummary.class);

        // Verify all fields match
        assertEquals(original.dayIndex(), deserialized.dayIndex());
        assertEquals(original.avgHrBpm(), deserialized.avgHrBpm());
        assertEquals(original.minHrBpm(), deserialized.minHrBpm());
        assertEquals(original.maxHrBpm(), deserialized.maxHrBpm());
        assertEquals(original.avgSdnn(), deserialized.avgSdnn());
        assertEquals(original.avgRmssd(), deserialized.avgRmssd());
        assertEquals(original.sleepHoursEstimate(), deserialized.sleepHoursEstimate());
        assertEquals(original.eventsThisDay(), deserialized.eventsThisDay());
    }

    @Test
    void testFieldTypes() {
        DailySummary summary = new DailySummary(
            0,      // Integer
            72.5,   // Double
            55.0,   // Double
            95.0,   // Double
            45.2,   // Double
            38.7,   // Double
            7.5,    // Double
            3       // Integer
        );

        // Verify types
        assertInstanceOf(Integer.class, summary.dayIndex());
        assertInstanceOf(Double.class, summary.avgHrBpm());
        assertInstanceOf(Double.class, summary.minHrBpm());
        assertInstanceOf(Double.class, summary.maxHrBpm());
        assertInstanceOf(Double.class, summary.avgSdnn());
        assertInstanceOf(Double.class, summary.avgRmssd());
        assertInstanceOf(Double.class, summary.sleepHoursEstimate());
        assertInstanceOf(Integer.class, summary.eventsThisDay());
    }

    @Test
    void testEdgeCaseValues() throws Exception {
        // Test with edge case values
        DailySummary summary = new DailySummary(
            0,      // First day
            0.0,    // Zero heart rate (edge case)
            0.0,    // Zero min
            0.0,    // Zero max
            0.0,    // Zero SDNN
            0.0,    // Zero RMSSD
            0.0,    // No sleep
            0       // No events
        );

        // Should serialize and deserialize without errors
        String json = mapper.writeValueAsString(summary);
        DailySummary deserialized = mapper.readValue(json, DailySummary.class);

        assertEquals(0, deserialized.dayIndex());
        assertEquals(0.0, deserialized.avgHrBpm());
        assertEquals(0, deserialized.eventsThisDay());
    }
}
