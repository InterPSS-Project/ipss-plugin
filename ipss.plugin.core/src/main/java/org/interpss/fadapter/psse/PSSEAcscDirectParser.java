package org.interpss.fadapter.psse;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.interpss.fadapter.builder.AcscNetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscNetwork;

/**
 * Direct PSS/E sequence data file parser that bypasses the ODM XML intermediate layer.
 * Reads PSS/E sequence data files (v30-v36) and populates an AcscNetwork via AcscNetworkBuilder.
 *
 * PSS/E sequence data file sections (in order):
 *  1. Change code header
 *  2. Positive sequence generator impedance
 *  3. Negative sequence generator impedance
 *  4. Zero sequence generator impedance
 *  5. Negative sequence shunt load
 *  6. Zero sequence shunt load
 *  7. Zero sequence non-transformer branch data
 *  8. Zero sequence mutual impedance
 *  9. Zero sequence transformer data
 * 10. Zero sequence switched shunt data
 */
public class PSSEAcscDirectParser {
    private static final Logger log = LoggerFactory.getLogger(PSSEAcscDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";

    private final AcscNetworkBuilder builder;

    public PSSEAcscDirectParser(AcscNetworkBuilder builder) {
        this.builder = builder;
    }

    /**
     * Parse a PSS/E sequence data file and populate the Acsc network.
     * The ACLF data must already be loaded into the builder's network.
     */
    public AcscNetwork parseSequenceFile(String seqFilePath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(seqFilePath))) {
            parseSequenceData(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading sequence file: " + seqFilePath + " - " + e.toString());
        }
        AcscNetwork net = builder.getAcscNetwork();
        net.setScDataLoaded(true);
        net.setPositiveSeqDataOnly(false);
        return net;
    }

    private void parseSequenceData(BufferedReader reader) throws IOException, InterpssException {
        String line = reader.readLine();
        if (line == null) return;

        // Section 1: Change code header
        PSSEDataRec headerRec = new PSSEDataRec(line);
        int ic = headerRec.getInt(0);
        if (ic != 0) {
            log.warn("IC={} (modification mode) not fully supported; treating as initialization", ic);
        }

        // Section 2: Positive sequence generator Z
        int cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseGenPosSeqZ(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Positive sequence generator records: {}", cnt);

        // Section 3: Negative sequence generator Z
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseGenNegSeqZ(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Negative sequence generator records: {}", cnt);

        // Section 4: Zero sequence generator Z
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseGenZeroSeqZ(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Zero sequence generator records: {}", cnt);

        // Section 5: Negative sequence shunt load
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseLoadNegSeq(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Negative sequence shunt load records: {}", cnt);

        // Section 6: Zero sequence shunt load
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseLoadZeroSeq(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Zero sequence shunt load records: {}", cnt);

        // Section 7: Zero sequence non-transformer branch data
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseBranchZeroSeq(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Zero sequence branch records: {}", cnt);

        // Section 8: Zero sequence mutual impedance
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseMutualZeroSeq(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Zero sequence mutual impedance records: {}", cnt);

        // Section 9: Zero sequence transformer data
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseXfrZeroSeq(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Zero sequence transformer records: {}", cnt);

        // Section 10: Zero sequence switched shunt data
        cnt = 0;
        while ((line = reader.readLine()) != null) {
            if (PSSEDataRec.isEndRec(line)) break;
            parseSwitchedShuntZeroSeq(new PSSEDataRec(line));
            cnt++;
        }
        log.info("Zero sequence switched shunt records: {}", cnt);
    }

    // Format: I, ID, ZRPOS, ZXPOS
    private void parseGenPosSeqZ(PSSEDataRec rec) {
        int busNum = Math.abs(rec.getInt(0));
        String genId = rec.getString(1, "1").trim();
        double r = rec.getDouble(2);
        double x = rec.getDouble(3);
        String busId = BUS_ID_PREFIX + busNum;
        builder.setGenPosSeqZ(busId, genId, r, x);
    }

    // Format: I, ID, ZRNEG, ZXNEG
    private void parseGenNegSeqZ(PSSEDataRec rec) {
        int busNum = Math.abs(rec.getInt(0));
        String genId = rec.getString(1, "1").trim();
        double r = rec.getDouble(2);
        double x = rec.getDouble(3);
        String busId = BUS_ID_PREFIX + busNum;
        builder.setGenNegSeqZ(busId, genId, r, x);
    }

    // Format: I, ID, ZRZERO, ZXZERO
    private void parseGenZeroSeqZ(PSSEDataRec rec) {
        int busNum = Math.abs(rec.getInt(0));
        String genId = rec.getString(1, "1").trim();
        double r = rec.getDouble(2);
        double x = rec.getDouble(3);
        String busId = BUS_ID_PREFIX + busNum;
        builder.setGenZeroSeqZ(busId, genId, r, x);
    }

    // Format: I, GNEG, BNEG
    private void parseLoadNegSeq(PSSEDataRec rec) {
        int busNum = Math.abs(rec.getInt(0));
        double g = rec.getDouble(1);
        double b = rec.getDouble(2);
        String busId = BUS_ID_PREFIX + busNum;
        builder.setLoadNegSeqShuntY(busId, g, b);
    }

    // Format: I, GZERO, BZERO
    private void parseLoadZeroSeq(PSSEDataRec rec) {
        int busNum = Math.abs(rec.getInt(0));
        double g = rec.getDouble(1);
        double b = rec.getDouble(2);
        String busId = BUS_ID_PREFIX + busNum;
        builder.setLoadZeroSeqShuntY(busId, g, b);
    }

    // Format: I, J, ICKT, RLINZ, XLINZ, BCHZ, GI, BI, GJ, BJ
    private void parseBranchZeroSeq(PSSEDataRec rec) {
        int fromNum = Math.abs(rec.getInt(0));
        int toNum = Math.abs(rec.getInt(1));
        String cirId = rec.getString(2, "1").trim();
        double r0 = rec.getDouble(3);
        double x0 = rec.getDouble(4);
        double b0 = rec.getDouble(5);
        double gi = rec.getDouble(6);
        double bi = rec.getDouble(7);
        double gj = rec.getDouble(8);
        double bj = rec.getDouble(9);

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;
        builder.setLineZeroSeqData(fromBusId, toBusId, cirId, r0, x0, b0, gi, bi, gj, bj);
    }

    // Format: I, J, ICKT1, K, L, ICKT2, RM, XM
    private void parseMutualZeroSeq(PSSEDataRec rec) {
        // Mutual impedance data - store for later use if needed
        log.debug("Mutual zero sequence impedance record parsed (not fully implemented)");
    }

    // Format (2W): I, J, K, ICKT, CC, RG, XG, R1, X1, R2, X2
    // Format (3W): I, J, K, ICKT, CC, RG, XG, R1, X1, R2, X2, R3, X3
    private void parseXfrZeroSeq(PSSEDataRec rec) {
        int fromNum = Math.abs(rec.getInt(0));
        int toNum = Math.abs(rec.getInt(1));
        int tertNum = Math.abs(rec.getInt(2));
        String cirId = rec.getString(3, "1").trim();
        int cc = rec.getInt(4);
        double rg = rec.getDouble(5);
        double xg = rec.getDouble(6);
        double r1 = rec.getDouble(7);
        double x1 = rec.getDouble(8);
        double r2 = rec.getDouble(9);
        double x2 = rec.getDouble(10);

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        if (tertNum == 0) {
            builder.setXfrZeroSeqData(fromBusId, toBusId, cirId, cc, rg, xg, r1, x1, r2, x2);
        } else {
            log.warn("3-winding transformer zero sequence not yet supported: {}-{}-{}", fromNum, toNum, tertNum);
        }
    }

    // Format: I, BZ1, BZ2, ... BZ8
    private void parseSwitchedShuntZeroSeq(PSSEDataRec rec) {
        int busNum = Math.abs(rec.getInt(0));
        String busId = BUS_ID_PREFIX + busNum;
        double totalB0 = 0;
        for (int k = 1; k < rec.size(); k++) {
            totalB0 += rec.getDouble(k);
        }
        builder.setSwitchedShuntZeroSeqY(busId, 0, totalB0);
    }
}
