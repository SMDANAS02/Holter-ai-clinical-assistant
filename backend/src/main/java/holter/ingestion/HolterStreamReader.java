package holter.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Module 1: HolterStream – Data Ingestion
 * <p>
 * Streams raw ECG signal samples in configurable chunks from different sources:
 * <ul>
 *   <li>CSV files (timestamp, amplitude columns)</li>
 *   <li>MIT-BIH / WFDB .dat + .hea files</li>
 *   <li>Synthetic in-memory data (via {@link SyntheticEcgGenerator})</li>
 * </ul>
 * Implements {@link Iterator}{@code <double[]>} so callers can lazily pull chunks
 * without loading the entire recording into memory.
 */
public class HolterStreamReader implements Iterator<double[]>, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(HolterStreamReader.class);

    /** Default number of samples per yielded chunk (≈ 10 seconds @ 1 kHz). */
    public static final int DEFAULT_CHUNK_SIZE = 10_000;

    /** Source type enumeration. */
    public enum SourceType { CSV, WFDB, SYNTHETIC }

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    private final SourceType sourceType;
    private final int chunkSize;

    // CSV state
    private BufferedReader csvReader;
    private boolean csvHeaderSkipped = false;
    private int csvAmplitudeColumnIndex = 1;   // default: second column
    private String csvLine;
    private boolean csvEof = false;

    // WFDB/synthetic state – delegate to sub-iterators
    private Iterator<double[]> delegate;

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    /**
     * Open a CSV file for streaming.
     *
     * @param csvPath                Path to the CSV file
     * @param amplitudeColumnIndex   Zero-based index of the amplitude column
     * @param skipHeader             Whether to skip the first (header) line
     * @param chunkSize              Samples per chunk
     * @return Configured reader
     */
    public static HolterStreamReader fromCsv(Path csvPath,
                                              int amplitudeColumnIndex,
                                              boolean skipHeader,
                                              int chunkSize) throws IOException {
        logger.info("Opening CSV ECG stream from {}", csvPath);
        HolterStreamReader reader = new HolterStreamReader(SourceType.CSV, chunkSize);
        reader.csvAmplitudeColumnIndex = amplitudeColumnIndex;
        reader.csvReader = Files.newBufferedReader(csvPath);
        if (skipHeader) {
            reader.csvReader.readLine(); // discard header
            reader.csvHeaderSkipped = true;
        }
        return reader;
    }

    /**
     * Open a CSV file with default settings (amplitude in column 1, header skipped).
     */
    public static HolterStreamReader fromCsv(Path csvPath) throws IOException {
        return fromCsv(csvPath, 1, true, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Open a WFDB MIT-BIH recording for streaming.
     *
     * @param heaPath  Path to the .hea header file
     * @param chunkSize Samples per chunk
     * @return Configured reader
     */
    public static HolterStreamReader fromWfdb(Path heaPath, int chunkSize) throws IOException {
        logger.info("Opening WFDB ECG stream from {}", heaPath);
        MitBihParser parser = new MitBihParser(heaPath);
        HolterStreamReader reader = new HolterStreamReader(SourceType.WFDB, chunkSize);
        reader.delegate = parser.chunkIterator(chunkSize);
        return reader;
    }

    /** Open WFDB with default chunk size. */
    public static HolterStreamReader fromWfdb(Path heaPath) throws IOException {
        return fromWfdb(heaPath, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Create a synthetic in-memory stream.
     *
     * @param generator  Pre-configured {@link SyntheticEcgGenerator}
     * @param chunkSize  Samples per chunk
     * @return Configured reader
     */
    public static HolterStreamReader fromSynthetic(SyntheticEcgGenerator generator, int chunkSize) {
        logger.info("Creating synthetic ECG stream ({} samples total)", generator.totalSamples());
        HolterStreamReader reader = new HolterStreamReader(SourceType.SYNTHETIC, chunkSize);
        double[] allSamples = generator.generate();
        reader.delegate = new ArrayChunkIterator(allSamples, chunkSize);
        return reader;
    }

    /** Create synthetic stream with default chunk size. */
    public static HolterStreamReader fromSynthetic(SyntheticEcgGenerator generator) {
        return fromSynthetic(generator, DEFAULT_CHUNK_SIZE);
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    private HolterStreamReader(SourceType sourceType, int chunkSize) {
        this.sourceType = sourceType;
        this.chunkSize = chunkSize;
    }

    // -----------------------------------------------------------------------
    // Iterator interface
    // -----------------------------------------------------------------------

    @Override
    public boolean hasNext() {
        return switch (sourceType) {
            case CSV -> hasNextCsv();
            case WFDB, SYNTHETIC -> delegate != null && delegate.hasNext();
        };
    }

    @Override
    public double[] next() {
        if (!hasNext()) throw new NoSuchElementException();
        return switch (sourceType) {
            case CSV -> nextCsvChunk();
            case WFDB, SYNTHETIC -> delegate.next();
        };
    }

    // -----------------------------------------------------------------------
    // CSV implementation
    // -----------------------------------------------------------------------

    private boolean hasNextCsv() {
        if (csvEof) return false;
        if (csvLine != null) return true;
        try {
            csvLine = csvReader.readLine();
            if (csvLine == null) csvEof = true;
        } catch (IOException e) {
            logger.error("Error reading CSV line", e);
            csvEof = true;
        }
        return !csvEof;
    }

    private double[] nextCsvChunk() {
        List<Double> buffer = new ArrayList<>(chunkSize);
        int count = 0;
        while (count < chunkSize) {
            String line;
            if (csvLine != null) {
                line = csvLine;
                csvLine = null;
            } else {
                try {
                    line = csvReader.readLine();
                } catch (IOException e) {
                    logger.error("Error reading CSV chunk", e);
                    break;
                }
                if (line == null) {
                    csvEof = true;
                    break;
                }
            }
            String[] parts = line.split(",");
            if (parts.length > csvAmplitudeColumnIndex) {
                try {
                    buffer.add(Double.parseDouble(parts[csvAmplitudeColumnIndex].trim()));
                    count++;
                } catch (NumberFormatException nfe) {
                    logger.debug("Skipping non-numeric CSV value: {}", parts[csvAmplitudeColumnIndex]);
                }
            }
        }
        double[] chunk = new double[buffer.size()];
        for (int i = 0; i < buffer.size(); i++) chunk[i] = buffer.get(i);
        logger.debug("CSV chunk yielded {} samples", chunk.length);
        return chunk;
    }

    // -----------------------------------------------------------------------
    // Closeable
    // -----------------------------------------------------------------------

    @Override
    public void close() throws IOException {
        if (csvReader != null) {
            csvReader.close();
            logger.debug("CSV reader closed");
        }
    }

    // -----------------------------------------------------------------------
    // Helper: array chunk iterator (for synthetic / WFDB)
    // -----------------------------------------------------------------------

    static final class ArrayChunkIterator implements Iterator<double[]> {
        private final double[] data;
        private final int chunkSize;
        private int pos = 0;

        ArrayChunkIterator(double[] data, int chunkSize) {
            this.data = data;
            this.chunkSize = chunkSize;
        }

        @Override
        public boolean hasNext() {
            return pos < data.length;
        }

        @Override
        public double[] next() {
            if (!hasNext()) throw new NoSuchElementException();
            int end = Math.min(pos + chunkSize, data.length);
            double[] chunk = Arrays.copyOfRange(data, pos, end);
            pos = end;
            return chunk;
        }
    }
}
