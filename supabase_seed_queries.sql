-- ============================================================================
-- Supabase SQL Seed Queries for Holter Monitor AI Database
-- ============================================================================
-- Run these queries in Supabase SQL Editor to:
-- 1. Create all required tables
-- 2. Seed sample data
-- 3. Verify database connectivity
--
-- Instructions:
-- 1. Go to: https://supabase.com/dashboard/project/[PROJECT_ID]/sql
-- 2. Create a new query
-- 3. Copy and paste each section below (or the entire file)
-- 4. Click "Run" to execute
-- ============================================================================

-- ============================================================================
-- STEP 1: CREATE TABLES
-- ============================================================================

-- Create patients table
CREATE TABLE IF NOT EXISTS patients (
    patient_id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create recordings table (with user_id for multi-user support)
CREATE TABLE IF NOT EXISTS recordings (
    recording_id SERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    patient_id VARCHAR(255) NOT NULL REFERENCES patients(patient_id),
    recording_days INTEGER NOT NULL,
    total_beats_processed BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_patient FOREIGN KEY(patient_id) REFERENCES patients(patient_id)
);

-- Create index on user_id for efficient filtering
CREATE INDEX IF NOT EXISTS idx_recordings_user_id ON recordings(user_id);

-- Create events table
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
);

-- Create daily_summaries table
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
);

-- Create reports table (for AI-generated insights)
CREATE TABLE IF NOT EXISTS reports (
    report_id SERIAL PRIMARY KEY,
    recording_id INTEGER NOT NULL REFERENCES recordings(recording_id),
    report_type VARCHAR(100),
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recording FOREIGN KEY(recording_id) REFERENCES recordings(recording_id)
);

-- ============================================================================
-- STEP 2: SEED SAMPLE DATA
-- ============================================================================

-- Insert sample patients
INSERT INTO patients (patient_id) VALUES
    ('DEMO_PATIENT'),
    ('PATIENT_001'),
    ('PATIENT_002')
ON CONFLICT (patient_id) DO NOTHING;

-- Insert sample recordings (with user IDs for multi-user testing)
INSERT INTO recordings (user_id, patient_id, recording_days, total_beats_processed) VALUES
    ('demo-user-001', 'DEMO_PATIENT', 7, 714240),
    ('demo-user-001', 'PATIENT_001', 7, 720000),
    ('demo-user-002', 'PATIENT_002', 7, 710000)
ON CONFLICT DO NOTHING;

-- Insert sample events for first recording (demo-user-001, DEMO_PATIENT) - 29 events across 7 days
INSERT INTO events (
    event_id, recording_id, event_timestamp, start_time, end_time, 
    duration_sec, beats_involved, deviation_score, context_bucket, 
    day_index, hour_of_day, sleep_state
) VALUES
    -- Day 0 (5 events)
    ('evt-001', 1, NOW(), '2024-01-01 02:14:32', '2024-01-01 02:14:58', 26.0, 28, 4.72, 'sleep_night_2', 0, 2.24, 'sleep'),
    ('evt-002', 1, NOW(), '2024-01-01 07:33:10', '2024-01-01 07:33:47', 37.0, 42, 5.18, 'transition_morning_7', 0, 7.55, 'transition'),
    ('evt-003', 1, NOW(), '2024-01-01 09:22:01', '2024-01-01 09:22:28', 27.0, 31, 4.85, 'awake_morning_9', 0, 9.38, 'awake'),
    ('evt-004', 1, NOW(), '2024-01-01 14:15:45', '2024-01-01 14:16:12', 27.0, 33, 5.41, 'awake_afternoon_14', 0, 14.26, 'awake'),
    ('evt-005', 1, NOW(), '2024-01-01 23:44:00', '2024-01-01 23:44:52', 52.0, 61, 7.21, 'sleep_night_23', 0, 23.73, 'sleep'),
    -- Day 1 (5 events)
    ('evt-006', 1, NOW(), '2024-01-02 01:22:15', '2024-01-02 01:22:48', 33.0, 38, 5.92, 'sleep_night_1', 1, 1.37, 'sleep'),
    ('evt-007', 1, NOW(), '2024-01-02 06:55:30', '2024-01-02 06:56:05', 35.0, 40, 5.64, 'transition_morning_6', 1, 6.92, 'transition'),
    ('evt-008', 1, NOW(), '2024-01-02 10:11:22', '2024-01-02 10:11:56', 34.0, 39, 5.33, 'awake_morning_10', 1, 10.19, 'awake'),
    ('evt-009', 1, NOW(), '2024-01-02 15:44:18', '2024-01-02 15:45:00', 42.0, 48, 6.15, 'awake_afternoon_15', 1, 15.74, 'awake'),
    ('evt-010', 1, NOW(), '2024-01-02 22:33:44', '2024-01-02 22:34:28', 44.0, 52, 6.87, 'sleep_night_22', 1, 22.56, 'sleep'),
    -- Day 2 (4 events)
    ('evt-011', 1, NOW(), '2024-01-03 03:05:10', '2024-01-03 03:05:47', 37.0, 43, 6.22, 'sleep_night_3', 2, 3.09, 'sleep'),
    ('evt-012', 1, NOW(), '2024-01-03 08:20:33', '2024-01-03 08:21:08', 35.0, 41, 5.75, 'awake_morning_8', 2, 8.34, 'awake'),
    ('evt-013', 1, NOW(), '2024-01-03 13:42:55', '2024-01-03 13:43:32', 37.0, 44, 5.98, 'awake_afternoon_13', 2, 13.71, 'awake'),
    ('evt-014', 1, NOW(), '2024-01-03 23:07:01', '2024-01-03 23:07:22', 21.0, 22, 3.94, 'sleep_night_23', 2, 23.12, 'sleep'),
    -- Day 3 (4 events)
    ('evt-015', 1, NOW(), '2024-01-04 02:18:44', '2024-01-04 02:19:25', 41.0, 47, 6.45, 'sleep_night_2', 3, 2.31, 'sleep'),
    ('evt-016', 1, NOW(), '2024-01-04 07:55:12', '2024-01-04 07:55:51', 39.0, 45, 6.11, 'transition_morning_7', 3, 7.92, 'transition'),
    ('evt-017', 1, NOW(), '2024-01-04 12:33:20', '2024-01-04 12:34:05', 45.0, 52, 6.73, 'awake_afternoon_12', 3, 12.56, 'awake'),
    ('evt-018', 1, NOW(), '2024-01-04 21:44:10', '2024-01-04 21:44:58', 48.0, 56, 7.05, 'sleep_night_21', 3, 21.74, 'sleep'),
    -- Day 4 (4 events)
    ('evt-019', 1, NOW(), '2024-01-05 01:30:05', '2024-01-05 01:30:48', 43.0, 49, 6.58, 'sleep_night_1', 4, 1.50, 'sleep'),
    ('evt-020', 1, NOW(), '2024-01-05 08:22:45', '2024-01-05 08:23:15', 30.0, 34, 6.03, 'transition_morning_8', 4, 8.38, 'transition'),
    ('evt-021', 1, NOW(), '2024-01-05 11:15:33', '2024-01-05 11:16:14', 41.0, 47, 6.34, 'awake_morning_11', 4, 11.26, 'awake'),
    ('evt-022', 1, NOW(), '2024-01-05 17:22:50', '2024-01-05 17:23:42', 52.0, 60, 7.18, 'awake_afternoon_17', 4, 17.38, 'awake'),
    -- Day 5 (4 events)
    ('evt-023', 1, NOW(), '2024-01-06 02:44:22', '2024-01-06 02:45:11', 49.0, 56, 6.89, 'sleep_night_2', 5, 2.74, 'sleep'),
    ('evt-024', 1, NOW(), '2024-01-06 09:11:05', '2024-01-06 09:11:48', 43.0, 50, 6.42, 'awake_morning_9', 5, 9.18, 'awake'),
    ('evt-025', 1, NOW(), '2024-01-06 14:33:14', '2024-01-06 14:34:02', 48.0, 55, 6.94, 'awake_afternoon_14', 5, 14.55, 'awake'),
    ('evt-026', 1, NOW(), '2024-01-06 22:55:30', '2024-01-06 22:56:25', 55.0, 64, 7.44, 'sleep_night_22', 5, 22.92, 'sleep'),
    -- Day 6 (3 events)
    ('evt-027', 1, NOW(), '2024-01-07 03:22:18', '2024-01-07 03:23:15', 57.0, 66, 7.62, 'sleep_night_3', 6, 3.37, 'sleep'),
    ('evt-028', 1, NOW(), '2024-01-07 10:44:40', '2024-01-07 10:45:35', 55.0, 63, 7.28, 'awake_morning_10', 6, 10.74, 'awake'),
    ('evt-029', 1, NOW(), '2024-01-07 16:18:25', '2024-01-07 16:19:18', 53.0, 61, 7.15, 'awake_afternoon_16', 6, 16.31, 'awake')
ON CONFLICT (event_id) DO NOTHING;

-- Insert events for second recording (demo-user-001, PATIENT_001) - 20 events
INSERT INTO events (
    event_id, recording_id, event_timestamp, start_time, end_time, 
    duration_sec, beats_involved, deviation_score, context_bucket, 
    day_index, hour_of_day, sleep_state
) VALUES
    ('evt-030', 2, NOW(), '2024-01-01 02:10:00', '2024-01-01 02:10:35', 35.0, 40, 5.45, 'sleep_night_2', 0, 2.17, 'sleep'),
    ('evt-031', 2, NOW(), '2024-01-01 08:15:22', '2024-01-01 08:15:58', 36.0, 41, 5.82, 'transition_morning_8', 0, 8.26, 'transition'),
    ('evt-032', 2, NOW(), '2024-01-01 12:44:10', '2024-01-01 12:44:50', 40.0, 46, 6.15, 'awake_afternoon_12', 0, 12.74, 'awake'),
    ('evt-033', 2, NOW(), '2024-01-02 01:33:44', '2024-01-02 01:34:25', 41.0, 47, 6.38, 'sleep_night_1', 1, 1.56, 'sleep'),
    ('evt-034', 2, NOW(), '2024-01-02 07:22:15', '2024-01-02 07:22:58', 43.0, 49, 6.72, 'transition_morning_7', 1, 7.37, 'transition'),
    ('evt-035', 2, NOW(), '2024-01-02 10:55:30', '2024-01-02 10:56:18', 48.0, 55, 7.01, 'awake_morning_10', 1, 10.92, 'awake'),
    ('evt-036', 2, NOW(), '2024-01-03 03:11:44', '2024-01-03 03:12:30', 46.0, 53, 6.85, 'sleep_night_3', 2, 3.19, 'sleep'),
    ('evt-037', 2, NOW(), '2024-01-03 09:33:22', '2024-01-03 09:34:12', 50.0, 57, 7.25, 'awake_morning_9', 2, 9.56, 'awake'),
    ('evt-038', 2, NOW(), '2024-01-04 02:44:05', '2024-01-04 02:44:58', 53.0, 61, 7.44, 'sleep_night_2', 3, 2.73, 'sleep'),
    ('evt-039', 2, NOW(), '2024-01-04 08:10:33', '2024-01-04 08:11:28', 55.0, 63, 7.68, 'transition_morning_8', 3, 8.18, 'transition'),
    ('evt-040', 2, NOW(), '2024-01-05 01:55:10', '2024-01-05 01:56:08', 58.0, 67, 7.92, 'sleep_night_1', 4, 1.92, 'sleep'),
    ('evt-041', 2, NOW(), '2024-01-05 09:22:44', '2024-01-05 09:23:48', 64.0, 74, 8.15, 'awake_morning_9', 4, 9.38, 'awake'),
    ('evt-042', 2, NOW(), '2024-01-06 03:33:22', '2024-01-06 03:34:32', 70.0, 81, 8.44, 'sleep_night_3', 5, 3.56, 'sleep'),
    ('evt-043', 2, NOW(), '2024-01-06 10:11:05', '2024-01-06 10:12:20', 75.0, 87, 8.72, 'awake_morning_10', 5, 10.19, 'awake'),
    ('evt-044', 2, NOW(), '2024-01-07 02:22:18', '2024-01-07 02:23:40', 82.0, 95, 8.95, 'sleep_night_2', 6, 2.37, 'sleep'),
    ('evt-045', 2, NOW(), '2024-01-07 08:55:44', '2024-01-07 08:57:15', 91.0, 105, 9.25, 'awake_morning_8', 6, 8.93, 'awake'),
    ('evt-046', 2, NOW(), '2024-01-01 16:44:33', '2024-01-01 16:45:27', 54.0, 62, 7.52, 'awake_afternoon_16', 0, 16.74, 'awake'),
    ('evt-047', 2, NOW(), '2024-01-02 15:33:10', '2024-01-02 15:34:08', 58.0, 67, 7.88, 'awake_afternoon_15', 1, 15.55, 'awake'),
    ('evt-048', 2, NOW(), '2024-01-03 14:22:50', '2024-01-03 14:23:55', 65.0, 75, 8.22, 'awake_afternoon_14', 2, 14.38, 'awake'),
    ('evt-049', 2, NOW(), '2024-01-04 13:10:22', '2024-01-04 13:11:32', 70.0, 81, 8.58, 'awake_afternoon_13', 3, 13.17, 'awake')
ON CONFLICT (event_id) DO NOTHING;

-- Insert events for third recording (demo-user-002, PATIENT_002) - 15 events
INSERT INTO events (
    event_id, recording_id, event_timestamp, start_time, end_time, 
    duration_sec, beats_involved, deviation_score, context_bucket, 
    day_index, hour_of_day, sleep_state
) VALUES
    ('evt-050', 3, NOW(), '2024-01-01 02:05:00', '2024-01-01 02:05:40', 40.0, 46, 5.92, 'sleep_night_2', 0, 2.08, 'sleep'),
    ('evt-051', 3, NOW(), '2024-01-01 07:44:15', '2024-01-01 07:45:00', 45.0, 52, 6.45, 'transition_morning_7', 0, 7.73, 'transition'),
    ('evt-052', 3, NOW(), '2024-01-02 03:22:10', '2024-01-02 03:23:05', 55.0, 63, 7.15, 'sleep_night_3', 1, 3.37, 'sleep'),
    ('evt-053', 3, NOW(), '2024-01-02 09:10:33', '2024-01-02 09:11:35', 62.0, 71, 7.68, 'awake_morning_9', 1, 9.18, 'awake'),
    ('evt-054', 3, NOW(), '2024-01-03 02:15:44', '2024-01-03 02:16:52', 68.0, 78, 8.02, 'sleep_night_2', 2, 2.26, 'sleep'),
    ('evt-055', 3, NOW(), '2024-01-03 08:33:22', '2024-01-03 08:34:38', 76.0, 87, 8.55, 'awake_morning_8', 2, 8.56, 'awake'),
    ('evt-056', 3, NOW(), '2024-01-04 01:50:10', '2024-01-04 01:51:32', 82.0, 94, 8.92, 'sleep_night_1', 3, 1.84, 'sleep'),
    ('evt-057', 3, NOW(), '2024-01-04 07:22:55', '2024-01-04 07:24:25', 90.0, 103, 9.38, 'transition_morning_7', 3, 7.38, 'transition'),
    ('evt-058', 3, NOW(), '2024-01-05 03:11:33', '2024-01-05 03:13:10', 97.0, 112, 9.72, 'sleep_night_3', 4, 3.19, 'sleep'),
    ('evt-059', 3, NOW(), '2024-01-05 09:44:22', '2024-01-05 09:46:05', 103.0, 119, 10.05, 'awake_morning_9', 4, 9.74, 'awake'),
    ('evt-060', 3, NOW(), '2024-01-06 02:33:44', '2024-01-06 02:35:40', 116.0, 134, 10.42, 'sleep_night_2', 5, 2.56, 'sleep'),
    ('evt-061', 3, NOW(), '2024-01-06 08:10:15', '2024-01-06 08:12:18', 123.0, 142, 10.88, 'awake_morning_8', 5, 8.17, 'awake'),
    ('evt-062', 3, NOW(), '2024-01-07 01:55:30', '2024-01-07 01:57:45', 135.0, 156, 11.25, 'sleep_night_1', 6, 1.92, 'sleep'),
    ('evt-063', 3, NOW(), '2024-01-07 07:33:22', '2024-01-07 07:35:50', 148.0, 171, 11.68, 'transition_morning_7', 6, 7.56, 'transition'),
    ('evt-064', 3, NOW(), '2024-01-07 14:22:10', '2024-01-07 14:24:55', 165.0, 190, 12.15, 'awake_afternoon_14', 6, 14.37, 'awake')
ON CONFLICT (event_id) DO NOTHING;

-- Insert sample daily summaries for all recordings
INSERT INTO daily_summaries (
    recording_id, day_index, avg_hr_bpm, min_hr_bpm, max_hr_bpm, 
    avg_sdnn, avg_rmssd, sleep_hours_estimate, events_this_day
) VALUES
    -- Recording 1 (DEMO_PATIENT)
    (1, 0, 68.5, 58, 92, 45.2, 32.1, 7.5, 5),
    (1, 1, 70.2, 60, 95, 48.1, 35.2, 7.2, 5),
    (1, 2, 69.8, 59, 93, 46.5, 33.8, 7.8, 4),
    (1, 3, 71.1, 61, 96, 47.3, 34.5, 7.0, 4),
    (1, 4, 69.5, 58, 94, 45.9, 32.7, 6.8, 4),
    (1, 5, 70.8, 60, 97, 48.7, 36.1, 7.4, 4),
    (1, 6, 68.9, 57, 91, 44.8, 31.2, 7.6, 3),
    -- Recording 2 (PATIENT_001)
    (2, 0, 72.1, 62, 98, 52.3, 38.5, 7.8, 3),
    (2, 1, 74.5, 64, 102, 55.2, 41.2, 7.5, 4),
    (2, 2, 73.8, 63, 100, 54.1, 40.1, 8.0, 3),
    (2, 3, 75.2, 65, 104, 56.8, 42.5, 7.3, 3),
    (2, 4, 74.1, 63, 102, 55.5, 41.3, 7.6, 3),
    (2, 5, 76.3, 66, 106, 58.2, 43.8, 7.9, 3),
    (2, 6, 75.0, 64, 103, 56.9, 42.4, 8.1, 3),
    -- Recording 3 (PATIENT_002)
    (3, 0, 78.5, 68, 110, 62.1, 45.3, 8.2, 2),
    (3, 1, 81.2, 71, 115, 65.4, 48.6, 8.5, 2),
    (3, 2, 79.8, 69, 112, 63.8, 47.2, 8.8, 2),
    (3, 3, 82.5, 72, 118, 67.1, 49.8, 8.3, 2),
    (3, 4, 80.9, 70, 114, 65.2, 48.4, 8.6, 2),
    (3, 5, 83.1, 73, 120, 68.5, 51.2, 8.9, 2),
    (3, 6, 81.4, 71, 116, 66.3, 49.5, 9.0, 2)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- STEP 3: VERIFICATION QUERIES (Run these to verify data)
-- ============================================================================

-- Check if tables exist and have data
SELECT 'patients' as table_name, COUNT(*) as row_count FROM patients
UNION ALL
SELECT 'recordings', COUNT(*) FROM recordings
UNION ALL
SELECT 'events', COUNT(*) FROM events
UNION ALL
SELECT 'daily_summaries', COUNT(*) FROM daily_summaries
UNION ALL
SELECT 'reports', COUNT(*) FROM reports;

-- View sample recording with events - shows all recordings with event counts
SELECT 
    r.recording_id,
    r.user_id,
    r.patient_id,
    r.recording_days,
    r.total_beats_processed,
    COUNT(e.event_id) as event_count
FROM recordings r
LEFT JOIN events e ON r.recording_id = e.recording_id
GROUP BY r.recording_id, r.user_id, r.patient_id, r.recording_days, r.total_beats_processed
ORDER BY r.recording_id;

-- View events for demo user - should see 29 events from DEMO_PATIENT + 20 from PATIENT_001
SELECT 
    e.event_id,
    e.deviation_score,
    e.sleep_state,
    e.day_index,
    e.hour_of_day,
    e.context_bucket,
    e.duration_sec,
    e.beats_involved,
    r.patient_id
FROM events e
JOIN recordings r ON e.recording_id = r.recording_id
WHERE r.user_id = 'demo-user-001'
ORDER BY e.day_index, e.hour_of_day;

-- Check user data isolation - demo-user-002 should see only their 15 events from PATIENT_002
SELECT 
    r.user_id,
    r.patient_id,
    COUNT(r.recording_id) as recording_count,
    COUNT(e.event_id) as event_count,
    SUM(e.beats_involved) as total_beats_in_events
FROM recordings r
LEFT JOIN events e ON r.recording_id = e.recording_id
WHERE r.user_id = 'demo-user-002'
GROUP BY r.user_id, r.patient_id;

-- Summary stats - shows event distribution across all recordings
SELECT 
    r.patient_id,
    r.user_id,
    COUNT(e.event_id) as total_events,
    AVG(e.deviation_score) as avg_deviation,
    MIN(e.deviation_score) as min_deviation,
    MAX(e.deviation_score) as max_deviation,
    AVG(e.beats_involved) as avg_beats,
    SUM(e.duration_sec) as total_duration_sec
FROM recordings r
LEFT JOIN events e ON r.recording_id = e.recording_id
GROUP BY r.patient_id, r.user_id
ORDER BY r.patient_id;

-- ============================================================================
-- STEP 4: DATABASE CONNECTIVITY TEST
-- ============================================================================

-- Simple connectivity test query (will show current timestamp if connection works)
SELECT NOW() as database_connected_at, current_user as connected_as;

-- Check table structure
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
