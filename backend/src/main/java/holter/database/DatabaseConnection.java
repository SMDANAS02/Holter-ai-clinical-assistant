package holter.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Manages database connection to Supabase PostgreSQL and initializes schema.
 * Uses CONNECTION_URL from DATABASE_URL environment variable.
 * 
 * <p>Follows fail-fast pattern: exits immediately if DATABASE_URL is missing
 * or if connection/initialization fails.
 */
public class DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    private static Connection connection = null;

    /**
     * Get or create a database connection.
     * Initializes schema on first connection.
     * 
     * @return Active Connection to Supabase PostgreSQL
     * @throws SQLException if connection fails or DATABASE_URL is missing
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        // Read DATABASE_URL from environment
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            String msg = "DATABASE_URL environment variable not set. Cannot connect to database.";
            logger.error(msg);
            throw new SQLException(msg);
        }

        logger.info("Connecting to Supabase PostgreSQL...");
        try {
            connection = DriverManager.getConnection(databaseUrl);
            logger.info("Database connection successful");
            
            // Initialize schema on first connection
            initializeSchema(connection);
            
            return connection;
        } catch (SQLException e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Close the database connection.
     */
    public static synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.warn("Error closing database connection: {}", e.getMessage());
            }
        }
    }

    /**
     * Initialize database schema if not already present.
     * Creates tables: patients, recordings, events, daily_summaries, reports.
     * 
     * @param conn Active database connection
     */
    private static void initializeSchema(Connection conn) throws SQLException {
        logger.info("Initializing database schema...");

        // Create patients table
        executeUpdate(conn, """
            CREATE TABLE IF NOT EXISTS patients (
                patient_id VARCHAR(255) PRIMARY KEY,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // Create recordings table (one per pipeline run)
        executeUpdate(conn, """
            CREATE TABLE IF NOT EXISTS recordings (
                recording_id SERIAL PRIMARY KEY,
                user_id VARCHAR(255),
                patient_id VARCHAR(255) NOT NULL REFERENCES patients(patient_id),
                recording_days INTEGER NOT NULL,
                total_beats_processed BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_patient FOREIGN KEY(patient_id) REFERENCES patients(patient_id)
            )
        """);

        // Create index on user_id for efficient filtering
        executeUpdate(conn, """
            CREATE INDEX IF NOT EXISTS idx_recordings_user_id ON recordings(user_id)
        """);

        // Create events table
        executeUpdate(conn, """
            CREATE TABLE IF NOT EXISTS events (
                event_id VARCHAR(255) PRIMARY KEY,
                recording_id INTEGER NOT NULL REFERENCES recordings(recording_id),
                event_timestamp TIMESTAMP NOT NULL,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP NOT NULL,
                duration_sec DOUBLE PRECISION NOT NULL,
                beats_involved INTEGER NOT NULL,
                deviation_score DOUBLE PRECISION NOT NULL,
                context_bucket VARCHAR(255),
                day_index INTEGER NOT NULL,
                hour_of_day DOUBLE PRECISION NOT NULL,
                sleep_state VARCHAR(50),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_recording FOREIGN KEY(recording_id) REFERENCES recordings(recording_id)
            )
        """);

        // Create daily_summaries table
        executeUpdate(conn, """
            CREATE TABLE IF NOT EXISTS daily_summaries (
                summary_id SERIAL PRIMARY KEY,
                recording_id INTEGER NOT NULL REFERENCES recordings(recording_id),
                day_index INTEGER NOT NULL,
                avg_hr_bpm DOUBLE PRECISION,
                min_hr_bpm DOUBLE PRECISION,
                max_hr_bpm DOUBLE PRECISION,
                avg_sdnn DOUBLE PRECISION,
                avg_rmssd DOUBLE PRECISION,
                sleep_hours_estimate DOUBLE PRECISION,
                events_this_day INTEGER,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_recording FOREIGN KEY(recording_id) REFERENCES recordings(recording_id)
            )
        """);

        // Create reports table (for AI-generated insights)
        executeUpdate(conn, """
            CREATE TABLE IF NOT EXISTS reports (
                report_id SERIAL PRIMARY KEY,
                recording_id INTEGER NOT NULL REFERENCES recordings(recording_id),
                report_type VARCHAR(100),
                content TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_recording FOREIGN KEY(recording_id) REFERENCES recordings(recording_id)
            )
        """);

        logger.info("Database schema initialized successfully");
    }

    /**
     * Execute an SQL update statement (CREATE TABLE, INSERT, etc.)
     */
    private static void executeUpdate(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Test database connection and schema.
     */
    public static void main(String[] args) {
        try {
            Connection conn = getConnection();
            logger.info("✅ Database connection test passed");
            close();
        } catch (SQLException e) {
            logger.error("❌ Database connection test failed: {}", e.getMessage());
            System.exit(1);
        }
    }
}
