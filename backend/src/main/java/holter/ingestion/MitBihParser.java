package holter.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;

/**
 * Module 1 – Task 4.3: MIT-BIH Arrhythmia Database format parser.
 * <p>
 * Supports reading:
 * <ul>
 *   <li>{@code .hea} header files to extract signal metadata (sampling rate, gain,
 *       baseline, number of samples).</li>
 *   <li>{@code .dat} binary files encoded in <em>format 212</em> (the most common
 *       MIT-BIH packing: three bytes encode two 12-bit samples) and
 *       <em>format 16</em> (straight signed 16-bit little-endian).</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * MitBihParser parser = new MitBihParser(Paths.get("100.hea"));
 * Iterator<double[]> it = parser.chunkIterator(10_000);
 * while (it.hasNext()) {
 *     double[] chunk = it.next();   // voltage in mV for lead I (first signal)
 *     // ... process chunk
 * }
 * }</pre>
 */
public class MitBihParser {

    private static final Logger logger = LoggerFactory.getLogger(MitBihParser.class);

    // -----------------------------------------------------------------------
    // Fields extracted from the .hea header
    // -----------------------------------------------------------------------

    private final Path datPath;
    private int    samplingRate     = 360;
    private int    format           = 212;    // default MIT-BIH format
    private double aduGain          = 200.0;  // ADC units / mV
    private int    baseline         = 0;      // ADC baseline
    private int    numberOfSamples  = 0;      // 0 = unknown (read until EOF)
    private int    numberOfSignals  = 1;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Parse the {@code .hea} header and prepare for reading the corresponding
     * {@code .dat} binary file.
     *
     * @param heaPath  Path to the {@code .hea} file (the {@code .dat} is
     *                 assumed to be in the same directory with the same base name)
     */
    public MitBihParser(Path heaPath) throws IOException {
        this.datPath = heaPath.resolveSibling(
            heaPath.getFileName().toString().replaceAll("\\.hea$", ".dat"));
        parseHeader(heaPath);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** @return Sampling rate in Hz extracted from the header. */
    public int samplingRate() { return samplingRate; }

    /** @return Total number of samples (0 if not specified in header). */
    public int numberOfSamples() { return numberOfSamples; }

    /**
     * Return an iterator that yields chunks of {@code chunkSize} mV samples
     * from the first (lead-I) signal.
     *
     * @param chunkSize  Number of samples per chunk
     * @return Lazy iterator over chunks
     */
    public Iterator<double[]> chunkIterator(int chunkSize) throws IOException {
        return switch (format) {
            case 212 -> new Format212Iterator(datPath, chunkSize, aduGain, baseline);
            case 16  -> new Format16Iterator (datPath, chunkSize, aduGain, baseline, numberOfSignals);
            default  -> throw new UnsupportedOperationException(
                "MIT-BIH format " + format + " is not yet supported");
        };
    }

    // -----------------------------------------------------------------------
    // Header parsing
    // -----------------------------------------------------------------------

    private void parseHeader(Path heaPath) throws IOException {
        logger.info("Parsing MIT-BIH header: {}", heaPath);
        try (BufferedReader br = Files.newBufferedReader(heaPath)) {
            // Line 1: record-line
            // Format: <record> <nsig> <fs> [<nsamp>] [<base>]
            String recordLine = br.readLine();
            if (recordLine != null) {
                String[] parts = recordLine.trim().split("\\s+");
                if (parts.length >= 3) {
                    try { samplingRate = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                }
                if (parts.length >= 2) {
                    try { numberOfSignals = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                }
                if (parts.length >= 4) {
                    try { numberOfSamples = Integer.parseInt(parts[3]); } catch (NumberFormatException ignored) {}
                }
            }

            // Subsequent lines: one per signal
            // Format: <filename> <fmt> <gain>/<baseline> <resolution> <zerovalue> <firstvalue> [<checksum>] [<blocksize>] [<desc>]
            String sigLine = br.readLine();
            if (sigLine != null) {
                String[] parts = sigLine.trim().split("\\s+");
                if (parts.length >= 2) {
                    try { format = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                }
                if (parts.length >= 3) {
                    // Gain field: may be "200", "200/0", "200.0/1024" etc.
                    String gainField = parts[2];
                    String[] gainParts = gainField.split("/");
                    try { aduGain = Double.parseDouble(gainParts[0]); } catch (NumberFormatException ignored) {}
                    if (gainParts.length >= 2) {
                        try { baseline = Integer.parseInt(gainParts[1]); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        logger.info("MIT-BIH header parsed: fs={}, format={}, gain={}, baseline={}, nSamples={}",
                    samplingRate, format, aduGain, baseline, numberOfSamples);
    }

    // -----------------------------------------------------------------------
    // Format 212 iterator (3 bytes → 2 twelve-bit samples, interleaved by signal)
    // -----------------------------------------------------------------------

    private static final class Format212Iterator implements Iterator<double[]> {

        private final DataInputStream dis;
        private final int chunkSize;
        private final double gain;
        private final int baseline;

        // Carry-over from previous group (format 212 packs 2 samples per 3 bytes)
        private boolean hasPending = false;
        private double  pendingSample;
        private boolean eof = false;

        Format212Iterator(Path datPath, int chunkSize, double gain, int baseline) throws IOException {
            this.dis       = new DataInputStream(new BufferedInputStream(Files.newInputStream(datPath)));
            this.chunkSize = chunkSize;
            this.gain      = gain;
            this.baseline  = baseline;
        }

        @Override
        public boolean hasNext() { return !eof || hasPending; }

        @Override
        public double[] next() {
            List<Double> buf = new ArrayList<>(chunkSize);
            while (buf.size() < chunkSize) {
                if (hasPending) {
                    buf.add(pendingSample);
                    hasPending = false;
                    continue;
                }
                // Read 3 bytes
                try {
                    int b0 = dis.read();
                    int b1 = dis.read();
                    int b2 = dis.read();
                    if (b0 < 0 || b1 < 0 || b2 < 0) { eof = true; break; }
                    // Decode two 12-bit samples
                    int s1 = b0 | ((b1 & 0x0F) << 8);
                    int s2 = ((b1 & 0xF0) >> 4) | (b2 << 4);
                    // Sign-extend from 12 bits
                    if (s1 > 2047) s1 -= 4096;
                    if (s2 > 2047) s2 -= 4096;
                    double mv1 = (s1 - baseline) / gain;
                    double mv2 = (s2 - baseline) / gain;
                    buf.add(mv1);
                    if (buf.size() < chunkSize) {
                        buf.add(mv2);
                    } else {
                        pendingSample = mv2;
                        hasPending    = true;
                    }
                } catch (IOException e) {
                    eof = true;
                    break;
                }
            }
            double[] chunk = new double[buf.size()];
            for (int i = 0; i < buf.size(); i++) chunk[i] = buf.get(i);
            return chunk;
        }
    }

    // -----------------------------------------------------------------------
    // Format 16 iterator (signed 16-bit little-endian, interleaved signals)
    // -----------------------------------------------------------------------

    private static final class Format16Iterator implements Iterator<double[]> {

        private final DataInputStream dis;
        private final int chunkSize;
        private final double gain;
        private final int baseline;
        private final int nSignals;
        private boolean eof = false;

        Format16Iterator(Path datPath, int chunkSize, double gain, int baseline, int nSignals)
                throws IOException {
            this.dis       = new DataInputStream(new BufferedInputStream(Files.newInputStream(datPath)));
            this.chunkSize = chunkSize;
            this.gain      = gain;
            this.baseline  = baseline;
            this.nSignals  = Math.max(1, nSignals);
        }

        @Override
        public boolean hasNext() { return !eof; }

        @Override
        public double[] next() {
            List<Double> buf = new ArrayList<>(chunkSize);
            byte[] raw = new byte[2 * nSignals];
            while (buf.size() < chunkSize) {
                try {
                    int read = dis.read(raw);
                    if (read < raw.length) { eof = true; break; }
                    // Extract lead-I (first signal)
                    short adc = ByteBuffer.wrap(raw, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                    buf.add((adc - baseline) / gain);
                } catch (IOException e) {
                    eof = true;
                    break;
                }
            }
            double[] chunk = new double[buf.size()];
            for (int i = 0; i < buf.size(); i++) chunk[i] = buf.get(i);
            return chunk;
        }
    }
}
