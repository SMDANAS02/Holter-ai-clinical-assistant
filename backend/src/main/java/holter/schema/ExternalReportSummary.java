package holter.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured summary extracted from an external Holter report PDF.
 * This is separate from the pipeline-generated findings and is intended
 * to provide reported summary data from the PDF text itself.
 */
public record ExternalReportSummary(
    @JsonProperty("report_source") String reportSource,
    @JsonProperty("pdf_path") String pdfPath,
    @JsonProperty("extracted_patient_identifier") String extractedPatientIdentifier,
    @JsonProperty("recording_duration") String recordingDuration,
    @JsonProperty("total_beats") Long totalBeats,
    @JsonProperty("arrhythmia_summary") String arrhythmiaSummary,
    @JsonProperty("average_heart_rate") String averageHeartRate,
    @JsonProperty("min_heart_rate") String minHeartRate,
    @JsonProperty("max_heart_rate") String maxHeartRate,
    @JsonProperty("hrv_metrics") String hrvMetrics,
    @JsonProperty("extraction_notes") String extractionNotes
) {
}
