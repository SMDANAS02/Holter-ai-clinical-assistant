package holter.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import holter.schema.ExternalReportSummary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured summary data from a Holter monitor PDF report.
 *
 * <p>This extractor pulls reported summary values from text, not raw ECG signal.
 * It is explicitly designed to extract reported metadata and event summaries
 * from the PDF content itself.
 */
public class PdfReportExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PdfReportExtractor.class);

    public static void main(String[] args) {
        String inputPath = null;
        String outputPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> {
                    if (i + 1 < args.length) inputPath = args[++i];
                }
                case "--output" -> {
                    if (i + 1 < args.length) outputPath = args[++i];
                }
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
            }
        }

        if (inputPath == null) {
            System.err.println("Error: --input <pdf_path> is required");
            printUsage();
            System.exit(1);
        }

        try {
            ExternalReportSummary summary = extractSummary(Paths.get(inputPath));
            if (outputPath != null) {
                writeJson(summary, Paths.get(outputPath));
            } else {
                System.out.println(toJson(summary));
            }
        } catch (IOException e) {
            logger.error("Failed to extract PDF report: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java holter.ingestion.PdfReportExtractor --input <file.pdf> [--output <summary.json>]");
        System.out.println("Extracts reported summary fields from a Holter monitor PDF report.");
    }

    public static ExternalReportSummary extractSummary(Path pdfPath) throws IOException {
        String text = extractText(pdfPath);
        String normalized = text.replaceAll("\r\n", "\n").replaceAll("\t", " ");

        String patientIdentifier = findFirstMatch(normalized,
            "(?i)(?:patient(?:\\s*id| identifier| name)?)[\\s:]+([A-Za-z0-9\\- _]{3,80})");
        String duration = findFirstMatch(normalized,
            "(?i)(?:recording duration|duration|recorded for)[\\s:]+([0-9]+(?:\\.[0-9]+)?\\s*(?:days?|hours?|hrs?|minutes?|mins?))");
        String totalBeats = findFirstMatch(normalized,
            "(?i)(?:total beats|beat count|beats processed|total heartbeats)[\\s:]+([0-9,]+)");
        String averageHr = findFirstMatch(normalized,
            "(?i)(?:average heart rate|avg heart rate|mean heart rate)[\\s:]+([0-9]{2,3}\\s*bpm)");
        String minHr = findFirstMatch(normalized,
            "(?i)(?:minimum heart rate|min heart rate|lowest heart rate)[\\s:]+([0-9]{2,3}\\s*bpm)");
        String maxHr = findFirstMatch(normalized,
            "(?i)(?:maximum heart rate|max heart rate|highest heart rate)[\\s:]+([0-9]{2,3}\\s*bpm)");

        List<String> arrhythmiaLines = findMatches(normalized,
            "(?i)(?:arrhythmia|atrial fibrillation|afib|ventricular tachycardia|pauses|flutter|tachycardia|bradycardia)[^\\n]{0,120}");
        String arrhythmiaSummary = arrhythmiaLines.isEmpty()
            ? "No structured arrhythmia summary detected in the PDF text."
            : String.join("; ", arrhythmiaLines);

        List<String> hrvLines = findMatches(normalized,
            "(?i)(?:sdnn|rmssd|pnn50|hrv)[^\\n]{0,120}");
        String hrvMetrics = hrvLines.isEmpty()
            ? "No HRV metrics found in the PDF text."
            : String.join("; ", hrvLines);

        String extractionNotes = "";
        if (patientIdentifier == null && duration == null && totalBeats == null
            && averageHr == null && minHr == null && maxHr == null
            && arrhythmiaLines.isEmpty() && hrvLines.isEmpty()) {
            extractionNotes = "Could not extract structured data from this PDF.";
        } else {
            extractionNotes = "Extracted reported summary data from PDF text only. This does not reconstruct raw ECG signal or beat-level data.";
        }

        return new ExternalReportSummary(
            "Holter PDF report",
            pdfPath.toAbsolutePath().toString(),
            patientIdentifier != null ? patientIdentifier.trim() : null,
            duration != null ? duration.trim() : null,
            totalBeats != null ? Long.parseLong(totalBeats.replaceAll("[,\\s]", "")) : null,
            arrhythmiaSummary,
            averageHr != null ? averageHr.trim() : null,
            minHr != null ? minHr.trim() : null,
            maxHr != null ? maxHr.trim() : null,
            hrvMetrics,
            extractionNotes
        );
    }

    private static String extractText(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private static String findFirstMatch(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static List<String> findMatches(String text, String regex) {
        List<String> matches = new ArrayList<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String match = matcher.group().trim();
            if (!match.isEmpty()) {
                matches.add(match);
            }
        }
        return matches;
    }

    private static void writeJson(ExternalReportSummary summary, Path outputPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputPath.toFile(), summary);
    }

    private static String toJson(ExternalReportSummary summary) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(summary);
    }
}
