package holter.mock;

import holter.schema.BeatRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockBeatTableGenerator.
 */
class MockBeatTableGeneratorTest {

    @Test
    @DisplayName("Should generate correct number of beats")
    void testGenerateCorrectNumberOfBeats() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(1000)
            .anomalyRate(0.05)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        assertEquals(1000, beats.size());
    }

    @Test
    @DisplayName("Should generate zero beats when requested")
    void testGenerateZeroBeats() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(0)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        assertEquals(0, beats.size());
    }

    @Test
    @DisplayName("Should throw exception for negative number of beats")
    void testNegativeNumberOfBeats() {
        assertThrows(IllegalArgumentException.class, () -> {
            MockBeatTableGenerator.builder()
                .numberOfBeats(-1)
                .build();
        });
    }

    @Test
    @DisplayName("Should throw exception for invalid anomaly rate")
    void testInvalidAnomalyRate() {
        assertThrows(IllegalArgumentException.class, () -> {
            MockBeatTableGenerator.builder()
                .anomalyRate(-0.1)
                .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            MockBeatTableGenerator.builder()
                .anomalyRate(1.5)
                .build();
        });
    }

    @Test
    @DisplayName("All beats should have non-null timestamps")
    void testAllBeatsHaveTimestamps() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        for (BeatRecord beat : beats) {
            assertNotNull(beat.timestamp());
        }
    }

    @Test
    @DisplayName("Timestamps should be monotonically increasing")
    void testTimestampsMonotonicallyIncrease() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        for (int i = 1; i < beats.size(); i++) {
            assertTrue(beats.get(i).timestamp().isAfter(beats.get(i - 1).timestamp()),
                "Timestamp at index " + i + " should be after timestamp at index " + (i - 1));
        }
    }

    @Test
    @DisplayName("RR intervals should be within physiological bounds")
    void testRRIntervalsWithinBounds() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        for (BeatRecord beat : beats) {
            assertNotNull(beat.rrIntervalMs());
            assertTrue(beat.rrIntervalMs() >= 300.0,
                "RR interval should be >= 300ms (too fast)");
            assertTrue(beat.rrIntervalMs() <= 2000.0,
                "RR interval should be <= 2000ms (too slow)");
        }
    }

    @Test
    @DisplayName("QRS widths should be within physiological bounds")
    void testQRSWidthsWithinBounds() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        for (BeatRecord beat : beats) {
            assertNotNull(beat.qrsWidthMs());
            assertTrue(beat.qrsWidthMs() >= 60.0,
                "QRS width should be >= 60ms");
            assertTrue(beat.qrsWidthMs() <= 120.0,
                "QRS width should be <= 120ms");
        }
    }

    @Test
    @DisplayName("R amplitudes should be within physiological bounds")
    void testRAmplitudesWithinBounds() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        for (BeatRecord beat : beats) {
            assertNotNull(beat.rAmplitude());
            assertTrue(beat.rAmplitude() >= 0.5,
                "R amplitude should be >= 0.5");
            assertTrue(beat.rAmplitude() <= 2.0,
                "R amplitude should be <= 2.0");
        }
    }

    @Test
    @DisplayName("Quality flags should include some false values for noisy beats")
    void testQualityFlagsIncludeFalseValues() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(1000)
            .randomSeed(42)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        long falseCount = beats.stream()
            .filter(beat -> !beat.qualityFlag())
            .count();
        
        // With 2% noise probability and 1000 beats, expect around 20 noisy beats
        // Allow for some variance (5-50 beats)
        assertTrue(falseCount >= 5 && falseCount <= 50,
            "Expected 5-50 noisy beats, got " + falseCount);
    }

    @Test
    @DisplayName("Day index should be correctly calculated")
    void testDayIndexCalculation() {
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(200000) // Enough beats to span multiple days
            .anomalyRate(0.0)
            .startTime(startTime)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        // First beat should be day 0
        assertEquals(0, beats.get(0).dayIndex());
        
        // Should have beats in multiple days
        int maxDayIndex = beats.stream()
            .mapToInt(BeatRecord::dayIndex)
            .max()
            .orElse(0);
        
        assertTrue(maxDayIndex >= 1,
            "With 200k beats, should span at least 2 days");
    }

    @Test
    @DisplayName("Should start at specified start time")
    void testStartTime() {
        Instant startTime = Instant.parse("2024-06-15T12:30:00Z");
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(10)
            .startTime(startTime)
            .build();
        
        List<BeatRecord> beats = generator.generate();
        
        assertEquals(startTime, beats.get(0).timestamp());
    }

    @Test
    @DisplayName("Should produce reproducible results with same seed")
    void testReproducibility() {
        MockBeatTableGenerator gen1 = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .randomSeed(12345)
            .build();
        
        MockBeatTableGenerator gen2 = MockBeatTableGenerator.builder()
            .numberOfBeats(100)
            .randomSeed(12345)
            .build();
        
        List<BeatRecord> beats1 = gen1.generate();
        List<BeatRecord> beats2 = gen2.generate();
        
        assertEquals(beats1.size(), beats2.size());
        
        for (int i = 0; i < beats1.size(); i++) {
            BeatRecord b1 = beats1.get(i);
            BeatRecord b2 = beats2.get(i);
            
            assertEquals(b1.timestamp(), b2.timestamp());
            assertEquals(b1.rrIntervalMs(), b2.rrIntervalMs());
            assertEquals(b1.qrsWidthMs(), b2.qrsWidthMs());
            assertEquals(b1.rAmplitude(), b2.rAmplitude());
            assertEquals(b1.qualityFlag(), b2.qualityFlag());
            assertEquals(b1.dayIndex(), b2.dayIndex());
        }
    }

    @Test
    @DisplayName("Higher anomaly rate should produce more varied RR intervals")
    void testAnomalyRateEffect() {
        Random sharedRandom1 = new Random(42);
        Random sharedRandom2 = new Random(42);
        
        MockBeatTableGenerator normalGen = MockBeatTableGenerator.builder()
            .numberOfBeats(1000)
            .anomalyRate(0.0)
            .random(sharedRandom1)
            .build();
        
        MockBeatTableGenerator anomalousGen = MockBeatTableGenerator.builder()
            .numberOfBeats(1000)
            .anomalyRate(0.5)
            .random(sharedRandom2)
            .build();
        
        List<BeatRecord> normalBeats = normalGen.generate();
        List<BeatRecord> anomalousBeats = anomalousGen.generate();
        
        double normalVariance = calculateVariance(normalBeats);
        double anomalousVariance = calculateVariance(anomalousBeats);
        
        assertTrue(anomalousVariance > normalVariance,
            "Higher anomaly rate should produce higher variance in RR intervals");
    }

    @Test
    @DisplayName("Circadian rhythm generation should vary heart rate by time of day")
    void testCircadianRhythmEffect() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(200000) // Span multiple days
            .anomalyRate(0.0)
            .startTime(Instant.parse("2024-01-01T00:00:00Z"))
            .randomSeed(42)
            .build();
        
        List<BeatRecord> beats = generator.generateWithCircadianRhythm();
        
        // Calculate average RR interval during sleep hours (midnight to 6am)
        double sleepAvgRR = beats.stream()
            .filter(beat -> {
                int hour = beat.timestamp().atZone(java.time.ZoneOffset.UTC).getHour();
                return hour >= 0 && hour < 6;
            })
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        // Calculate average RR interval during awake hours (10am to 6pm)
        double awakeAvgRR = beats.stream()
            .filter(beat -> {
                int hour = beat.timestamp().atZone(java.time.ZoneOffset.UTC).getHour();
                return hour >= 10 && hour < 18;
            })
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        // Sleep RR intervals should be longer (lower heart rate)
        assertTrue(sleepAvgRR > awakeAvgRR,
            "Sleep RR intervals should be longer than awake RR intervals");
    }

    @Test
    @DisplayName("Circadian rhythm generation should produce correct number of beats")
    void testCircadianRhythmGenerateCorrectNumber() {
        MockBeatTableGenerator generator = MockBeatTableGenerator.builder()
            .numberOfBeats(500)
            .build();
        
        List<BeatRecord> beats = generator.generateWithCircadianRhythm();
        
        assertEquals(500, beats.size());
    }

    private double calculateVariance(List<BeatRecord> beats) {
        double mean = beats.stream()
            .mapToDouble(BeatRecord::rrIntervalMs)
            .average()
            .orElse(0.0);
        
        double sumSquaredDiff = beats.stream()
            .mapToDouble(beat -> {
                double diff = beat.rrIntervalMs() - mean;
                return diff * diff;
            })
            .sum();
        
        return sumSquaredDiff / beats.size();
    }
}
