package holter.database;

import holter.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Handles batch insertion of pipeline results into Supabase PostgreSQL.
 * Uses prepared statements with batch execution for efficiency.
 * 
 * <p>Writes after pipeline completion:
 * 1. Insert/update patient record
 * 2. Create recording entry
 * 3. Batch insert all events
 * 4. Batch insert all daily summaries
 * 
 * <p>Failures are logged but do not block pipeline (graceful degradation).
 */
public class DatabaseWriter {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseWriter.class);

    /**
     * Write pipeline findings to database.
     * Inserts patient, recording, events, and daily summaries in batch.
     * 
     * @param findings Complete pipeline output
     * @return true if write successful, false if database unavailable or error occurred
     */
    public static boolean writeFindings(FindingsJson findings) {
        return writeFindings(findings, null);
    }

    /**
     * Write pipeline findings to database with optional authenticated user_id.
     * Inserts patient, recording, events, and daily summaries in batch.
     * 
     * @param findings Complete pipeline output
     * @param userId Authenticated user ID from Supabase (optional, may be null)
     * @return true if write successful, false if database unavailable or error occurred
     */
    public static boolean writeFindings(FindingsJson findings, String userId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            
            logger.info("Writing findings to database: {} events, {} daily summaries, user={}",
                    findings.events().size(),
                    findings.dailySummaries() != null ? findings.dailySummaries().size() : 0,
                    userId != null ? userId : "null");

            // 1. Ensure patient record exists
            ensurePatient(conn, findings.patientId());

            // 2. Create recording entry (with optional user_id)
            int recordingId = insertRecording(conn, findings, userId);
            if (recordingId == 0) {
                logger.error("Failed to insert recording");
                return false;
            }

            // 3. Batch insert events
            if (!findings.events().isEmpty()) {
                insertEventsBatch(conn, recordingId, findings.events());
            }

            // 4. Batch insert daily summaries
            if (findings.dailySummaries() != null && !findings.dailySummaries().isEmpty()) {
                insertDailySummariesBatch(conn, recordingId, findings.dailySummaries());
            }

            logger.info("✅ Successfully wrote findings to database");
            return true;

        } catch (SQLException e) {
            logger.error("❌ Database write failed (graceful degradation): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ensure patient record exists (insert if not present).
     */
    private static void ensurePatient(Connection conn, String patientId) throws SQLException {
        String sql = "INSERT INTO patients (patient_id) VALUES (?) ON CONFLICT (patient_id) DO NOTHING";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, patientId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Insert recording record and return its ID.
     */
    private static int insertRecording(Connection conn, FindingsJson findings) throws SQLException {
        return insertRecording(conn, findings, null);
    }

    /**
     * Insert recording record and return its ID, with optional user_id.
     */
    private static int insertRecording(Connection conn, FindingsJson findings, String userId) throws SQLException {
        String sql = userId != null ?
            """
            INSERT INTO recordings (user_id, patient_id, recording_days, total_beats_processed)
            VALUES (?, ?, ?, ?)
            RETURNING recording_id
            """ :
            """
            INSERT INTO recordings (patient_id, recording_days, total_beats_processed)
            VALUES (?, ?, ?)
            RETURNING recording_id
            """;
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (userId != null) {
                pstmt.setString(1, userId);
                pstmt.setString(2, findings.patientId());
                pstmt.setInt(3, findings.recordingDays());
                pstmt.setLong(4, findings.totalBeatsProcessed());
            } else {
                pstmt.setString(1, findings.patientId());
                pstmt.setInt(2, findings.recordingDays());
                pstmt.setLong(3, findings.totalBeatsProcessed());
            }
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("recording_id");
            }
            return 0;
        }
    }

    /**
     * Batch insert all events using prepared statements.
     */
    private static void insertEventsBatch(Connection conn, int recordingId, List<FlaggedEvent> events)
            throws SQLException {
        String sql = """
            INSERT INTO events (event_id, recording_id, event_timestamp, start_time, end_time,
                                duration_sec, beats_involved, deviation_score, context_bucket,
                                day_index, hour_of_day, sleep_state)
            VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (FlaggedEvent event : events) {
                pstmt.setString(1, event.eventId());
                pstmt.setInt(2, recordingId);
                pstmt.setTimestamp(3, Timestamp.from(event.startTime()));
                pstmt.setTimestamp(4, Timestamp.from(event.endTime()));
                pstmt.setDouble(5, event.durationSec());
                pstmt.setInt(6, event.beatsInvolved());
                pstmt.setDouble(7, event.deviationScore());
                pstmt.setString(8, event.contextBucket());
                pstmt.setInt(9, event.dayIndex());
                pstmt.setDouble(10, event.hourOfDay());
                pstmt.setString(11, event.sleepState());
                pstmt.addBatch();
            }
            
            int[] results = pstmt.executeBatch();
            logger.info("Batch inserted {} events", results.length);
        }
    }

    /**
     * Batch insert all daily summaries using prepared statements.
     */
    private static void insertDailySummariesBatch(Connection conn, int recordingId, 
                                                  List<DailySummary> summaries) throws SQLException {
        String sql = """
            INSERT INTO daily_summaries (recording_id, day_index, avg_hr_bpm, min_hr_bpm, max_hr_bpm,
                                        avg_sdnn, avg_rmssd, sleep_hours_estimate, events_this_day)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (DailySummary summary : summaries) {
                pstmt.setInt(1, recordingId);
                pstmt.setInt(2, summary.dayIndex());
                pstmt.setDouble(3, summary.avgHrBpm() != null ? summary.avgHrBpm() : 0);
                pstmt.setDouble(4, summary.minHrBpm() != null ? summary.minHrBpm() : 0);
                pstmt.setDouble(5, summary.maxHrBpm() != null ? summary.maxHrBpm() : 0);
                pstmt.setDouble(6, summary.avgSdnn() != null ? summary.avgSdnn() : 0);
                pstmt.setDouble(7, summary.avgRmssd() != null ? summary.avgRmssd() : 0);
                pstmt.setDouble(8, summary.sleepHoursEstimate() != null ? summary.sleepHoursEstimate() : 0);
                pstmt.setInt(9, summary.eventsThisDay() != null ? summary.eventsThisDay() : 0);
                pstmt.addBatch();
            }
            
            int[] results = pstmt.executeBatch();
            logger.info("Batch inserted {} daily summaries", results.length);
        }
    }

    /**
     * Query list of previous recordings for a patient.
     * 
     * @param patientId Patient identifier
     * @return List of recording metadata maps
     */
    public static List<Map<String, Object>> getPreviousRecordings(String patientId) {
        return getPreviousRecordings(patientId, null);
    }

    /**
     * Query list of previous recordings, optionally filtered by authenticated user_id.
     * 
     * @param patientId Patient identifier
     * @param userId Optional user_id to filter by (if provided, only that user's recordings returned)
     * @return List of recording metadata maps
     */
    public static List<Map<String, Object>> getPreviousRecordings(String patientId, String userId) {
        List<Map<String, Object>> recordings = new ArrayList<>();
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            
            String sql;
            if (userId != null) {
                // If user_id is provided, filter by both user_id AND patient_id
                sql = """
                    SELECT recording_id, patient_id, recording_days, total_beats_processed, created_at
                    FROM recordings
                    WHERE user_id = ? AND patient_id = ?
                    ORDER BY created_at DESC
                    LIMIT 20
                    """;
            } else {
                // Otherwise just filter by patient_id (backward compatibility)
                sql = """
                    SELECT recording_id, recording_days, total_beats_processed, created_at
                    FROM recordings
                    WHERE patient_id = ?
                    ORDER BY created_at DESC
                    LIMIT 20
                    """;
            }
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (userId != null) {
                    pstmt.setString(1, userId);
                    pstmt.setString(2, patientId);
                } else {
                    pstmt.setString(1, patientId);
                }
                
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("recording_id", rs.getInt("recording_id"));
                    rec.put("recording_days", rs.getInt("recording_days"));
                    rec.put("total_beats_processed", rs.getLong("total_beats_processed"));
                    rec.put("created_at", rs.getTimestamp("created_at").toString());
                    recordings.add(rec);
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not fetch previous recordings: {}", e.getMessage());
        }
        
        return recordings;
    }

    /**
     * Load a previous recording's findings from database.
     * 
     * @param recordingId Recording ID to load
     * @return FindingsJson reconstructed from database, or null if not found
     */
    public static FindingsJson loadRecording(int recordingId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            
            // Get recording metadata
            String recSql = "SELECT patient_id, recording_days, total_beats_processed FROM recordings WHERE recording_id = ?";
            String patientId = null;
            int recordingDays = 0;
            long totalBeats = 0;
            
            try (PreparedStatement pstmt = conn.prepareStatement(recSql)) {
                pstmt.setInt(1, recordingId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    patientId = rs.getString("patient_id");
                    recordingDays = rs.getInt("recording_days");
                    totalBeats = rs.getLong("total_beats_processed");
                } else {
                    return null; // Recording not found
                }
            }
            
            // Get events
            List<FlaggedEvent> events = new ArrayList<>();
            String eventSql = """
                SELECT event_id, start_time, end_time, duration_sec, beats_involved, deviation_score,
                       context_bucket, day_index, hour_of_day, sleep_state
                FROM events WHERE recording_id = ? ORDER BY start_time
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(eventSql)) {
                pstmt.setInt(1, recordingId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    events.add(new FlaggedEvent(
                        rs.getString("event_id"),
                        rs.getTimestamp("start_time").toInstant(),
                        rs.getTimestamp("end_time").toInstant(),
                        rs.getDouble("duration_sec"),
                        rs.getInt("beats_involved"),
                        rs.getDouble("deviation_score"),
                        rs.getString("context_bucket"),
                        rs.getInt("day_index"),
                        rs.getDouble("hour_of_day"),
                        rs.getString("sleep_state")
                    ));
                }
            }
            
            // Get daily summaries
            List<DailySummary> summaries = new ArrayList<>();
            String sumSql = """
                SELECT day_index, avg_hr_bpm, min_hr_bpm, max_hr_bpm, avg_sdnn, avg_rmssd,
                       sleep_hours_estimate, events_this_day
                FROM daily_summaries WHERE recording_id = ? ORDER BY day_index
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sumSql)) {
                pstmt.setInt(1, recordingId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    summaries.add(new DailySummary(
                        rs.getInt("day_index"),
                        rs.getDouble("avg_hr_bpm"),
                        rs.getDouble("min_hr_bpm"),
                        rs.getDouble("max_hr_bpm"),
                        rs.getDouble("avg_sdnn"),
                        rs.getDouble("avg_rmssd"),
                        rs.getDouble("sleep_hours_estimate"),
                        rs.getInt("events_this_day")
                    ));
                }
            }
            
            // Compute summary stats
            double avgScore = events.isEmpty() ? 0 : 
                events.stream().mapToDouble(FlaggedEvent::deviationScore).average().orElse(0);
            String mostCommon = findMostCommonContext(events);
            SummaryStats stats = new SummaryStats(events.size(), avgScore, mostCommon);
            
            return new FindingsJson(patientId, recordingDays, totalBeats, events, stats, summaries, null);
            
        } catch (SQLException e) {
            logger.warn("Could not load recording {}: {}", recordingId, e.getMessage());
            return null;
        }
    }

    /**
     * Find most common context bucket in events list.
     */
    private static String findMostCommonContext(List<FlaggedEvent> events) {
        if (events.isEmpty()) return "—";
        
        Map<String, Integer> counts = new HashMap<>();
        for (FlaggedEvent e : events) {
            counts.merge(e.contextBucket(), 1, Integer::sum);
        }
        
        return counts.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse("—");
    }
}
