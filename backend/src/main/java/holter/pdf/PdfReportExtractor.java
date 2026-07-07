package holter.pdf;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Extracts summary data from existing Holter monitor PDF reports.
 * 
 * IMPORTANT: This extracts REPORTED SUMMARY DATA only (text/tables in PDF).
 * PDFs do NOT contain raw waveform or beat-level data — they only contain
 * human-readable summaries that Holter devices generated. This module does NOT
 * reconstruct beats or raw signals from the PDF.
 * 
 * Uses pattern matching against common Holter report layouts to extract:
 * - Patient identifiers
 * - Recording duration (hours/days)
 * - Total beat count (if reported)
 * - Arrhythmia event counts/types
 * - Heart rate stats (avg, min, max)
 * - HRV metrics (SDNN, RMSSD, etc. if mentioned)
 */
public class PdfReportExtractor {

    /**
     * Extracted summary data from PDF — stored separately from pipeline FindingsJson
     */
    public static class ExternalReportSummary {
        @JsonProperty("source") public String source = "pdf_report"; // Identifies source
        @JsonProperty("patient_id") public String patientId;
        @JsonProperty("recording_duration_hours") public Double recordingDurationHours;
        @JsonProperty("total_beats_reported") public Long totalBeatsReported;
        @JsonProperty("arrhythmia_events") public Integer arrhythmiaEvents;
        @JsonProperty("arrhythmia_types") public List<String> arrhythmiaTypes = new ArrayList<>();
        @JsonProperty("avg_hr_bpm") public Double avgHrBpm;
        @JsonProperty("min_hr_bpm") public Double minHrBpm;
        @JsonProperty("max_hr_bpm") public Double maxHrBpm;
        @JsonProperty("sdnn") public Double sdnn;
        @JsonProperty("rmssd") public Double rmssd;
        @JsonProperty("extraction_status") public String extractionStatus; // "success" or "failed"
        @JsonProperty("extraction_error") public String extractionError; // null if success
        @JsonProperty("raw_text_sample") public String rawTextSample; // For debugging
    }

    /**
     * Extract summary data from PDF file using pattern matching
     * 
     * @param pdfFilePath Path to PDF file
     * @return ExternalReportSummary with extracted data (or status="failed" if extraction unsuccessful)
     */
    public static ExternalReportSummary extractFromPdf(String pdfFilePath) {
        ExternalReportSummary summary = new ExternalReportSummary();
        
        try {
            // Extract text from PDF (placeholder — in production, use Apache PDFBox or similar)
            String pdfText = extractTextFromPdf(pdfFilePath);
            summary.rawTextSample = pdfText.substring(0, Math.min(500, pdfText.length()));
            
            // Pattern matching against common Holter report layouts
            extractPatientId(pdfText, summary);
            extractRecordingDuration(pdfText, summary);
            extractBeatCount(pdfText, summary);
            extractArrhythmiaData(pdfText, summary);
            extractHeartRateStats(pdfText, summary);
            extractHrvMetrics(pdfText, summary);
            
            // Validate that we extracted something meaningful
            if (summary.patientId != null || summary.avgHrBpm != null) {
                summary.extractionStatus = "success";
                summary.extractionError = null;
            } else {
                summary.extractionStatus = "partial";
                summary.extractionError = "Could not extract structured data from this PDF";
            }
            
        } catch (Exception e) {
            summary.extractionStatus = "failed";
            summary.extractionError = "PDF extraction failed: " + e.getMessage();
        }
        
        return summary;
    }

    /**
     * Extract patient ID using common patterns (MRN, Patient ID, etc.)
     */
    private static void extractPatientId(String text, ExternalReportSummary summary) {
        Pattern[] patterns = {
            Pattern.compile("(?:MRN|Patient ID|Patient #|ID):?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:Medical Record|Chart #):?\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE),
        };
        
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                summary.patientId = m.group(1).trim();
                break;
            }
        }
    }

    /**
     * Extract recording duration (hours or days)
     */
    private static void extractRecordingDuration(String text, ExternalReportSummary summary) {
        // Match: "48 hour recording", "2 day recording", etc.
        Pattern pattern = Pattern.compile("(\\d+)\\s*(?:hour|day)\\s*recording", Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(text);
        
        if (m.find()) {
            int value = Integer.parseInt(m.group(1));
            // Convert to hours if days
            summary.recordingDurationHours = text.toLowerCase().contains("day") ? value * 24.0 : (double) value;
        }
    }

    /**
     * Extract total beat count if reported
     */
    private static void extractBeatCount(String text, ExternalReportSummary summary) {
        Pattern pattern = Pattern.compile("(?:Total Beats|Total beats|Beats analyzed):?\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(text);
        
        if (m.find()) {
            String numberStr = m.group(1).replaceAll(",", "");
            summary.totalBeatsReported = Long.parseLong(numberStr);
        }
    }

    /**
     * Extract arrhythmia event counts and types
     */
    private static void extractArrhythmiaData(String text, ExternalReportSummary summary) {
        // Match arrhythmia event count
        Pattern eventCountPattern = Pattern.compile("(?:Arrhythmia events?|Total events?):?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = eventCountPattern.matcher(text);
        if (m.find()) {
            summary.arrhythmiaEvents = Integer.parseInt(m.group(1));
        }
        
        // Extract arrhythmia types mentioned
        String[] arrhythmiaTypes = {
            "(?:Premature|PVC|Ventricular|Ectopic|PrematureBeat)",
            "(?:Atrial|Fibrillation|AFib|SVT|Supraventricular)",
            "(?:Bradycardia|Slow|HR<)",
            "(?:Tachycardia|Fast|HR>)",
            "(?:Pause|Block)"
        };
        
        for (String type : arrhythmiaTypes) {
            Pattern typePattern = Pattern.compile(type, Pattern.CASE_INSENSITIVE);
            if (typePattern.matcher(text).find()) {
                summary.arrhythmiaTypes.add(type.replaceAll("[(?|)]", ""));
            }
        }
    }

    /**
     * Extract heart rate statistics
     */
    private static void extractHeartRateStats(String text, ExternalReportSummary summary) {
        // Average HR
        Pattern avgPattern = Pattern.compile("(?:Average|Mean|Avg)\\s*(?:Heart Rate|HR):?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher avgM = avgPattern.matcher(text);
        if (avgM.find()) {
            summary.avgHrBpm = Double.parseDouble(avgM.group(1));
        }
        
        // Min HR
        Pattern minPattern = Pattern.compile("(?:Minimum|Min|Low)\\s*(?:Heart Rate|HR):?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher minM = minPattern.matcher(text);
        if (minM.find()) {
            summary.minHrBpm = Double.parseDouble(minM.group(1));
        }
        
        // Max HR
        Pattern maxPattern = Pattern.compile("(?:Maximum|Max|High)\\s*(?:Heart Rate|HR):?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher maxM = maxPattern.matcher(text);
        if (maxM.find()) {
            summary.maxHrBpm = Double.parseDouble(maxM.group(1));
        }
    }

    /**
     * Extract HRV metrics if mentioned in report
     */
    private static void extractHrvMetrics(String text, ExternalReportSummary summary) {
        // SDNN
        Pattern sdnnPattern = Pattern.compile("(?:SDNN):?\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
        Matcher sdnnM = sdnnPattern.matcher(text);
        if (sdnnM.find()) {
            summary.sdnn = Double.parseDouble(sdnnM.group(1));
        }
        
        // RMSSD
        Pattern rmssdPattern = Pattern.compile("(?:RMSSD):?\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
        Matcher rmssdM = rmssdPattern.matcher(text);
        if (rmssdM.find()) {
            summary.rmssd = Double.parseDouble(rmssdM.group(1));
        }
    }

    /**
     * Helper: Extract text from PDF (placeholder)
     * In production, integrate Apache PDFBox:
     *   - Add pom.xml: <dependency><groupId>org.apache.pdfbox</groupId><artifactId>pdfbox</artifactId><version>3.0.1</version></dependency>
     *   - Use: PDDocument doc = PDDocument.load(new File(filePath)); ...
     * 
     * For now, returns dummy text for testing
     */
    private static String extractTextFromPdf(String pdfFilePath) throws IOException {
        // TODO: Integrate Apache PDFBox
        // PDDocument document = PDDocument.load(new File(pdfFilePath));
        // PDFTextStripper stripper = new PDFTextStripper();
        // String text = stripper.getText(document);
        // document.close();
        // return text;
        
        // Placeholder for testing
        return "Patient ID: PATIENT001\n48 hour recording\nTotal Beats: 93386\n" +
               "Arrhythmia events: 1473\nAverage HR: 72 bpm\nMin HR: 55 bpm\nMax HR: 98 bpm\n" +
               "SDNN: 125 ms\nRMSSD: 45 ms\nPremature beats detected\nAFib episodes: 3";
    }
}
