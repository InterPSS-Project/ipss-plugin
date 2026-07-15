package org.interpss.fadapter.psse;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;

/**
 * Direct PSS/E dynamic data file parser that bypasses the ODM XML intermediate layer.
 * Reads PSS/E dynamic model data and populates a DStabilityNetwork via DStabNetworkBuilder.
 *
 * PSS/E dynamic data file format:
 *   Each record: IBUS 'TYPE' ID DATALIST /
 *   Records may span multiple lines; the '/' terminates a record.
 *   Lines starting with '/' or '//' are comments.
 */
public class PSSEDStabDirectParser {
    private static final Logger log = LoggerFactory.getLogger(PSSEDStabDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";

    private final DStabNetworkBuilder builder;

    public PSSEDStabDirectParser(DStabNetworkBuilder builder) {
        this.builder = builder;
    }

    public DStabilityNetwork parseDynFile(String dynFilePath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(dynFilePath))) {
            parseDynData(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading dynamic file: " + dynFilePath + " - " + e.toString());
        }
        return builder.getDStabNetwork();
    }

    private void parseDynData(BufferedReader reader) throws IOException, InterpssException {
        String line;
        int lineNo = 0;
        int modelCount = 0;
        int unsupportedCount = 0;

        while ((line = reader.readLine()) != null) {
            lineNo++;
            if (skipInvalidLine(line)) continue;
            line = line.trim();
            if (line.isEmpty()) continue;

            while (!isModelDataCompleted(line)) {
                String next = reader.readLine();
                if (next == null) break;
                lineNo++;
                line += " " + next.trim();
            }

            int slashIdx = line.lastIndexOf("/");
            if (slashIdx > 0) {
                line = line.substring(0, slashIdx);
            }

            try {
                String modelType = getModelType(line);
                if (modelType == null) {
                    log.debug("Skipping line {}: cannot determine model type", lineNo);
                    continue;
                }
                if (processModelRecord(modelType.toUpperCase(), line)) {
                    modelCount++;
                } else {
                    unsupportedCount++;
                }
            } catch (Exception e) {
                log.warn("Error processing dynamic record at line {}: {}", lineNo, e.getMessage());
            }
        }
        log.info("Dynamic models loaded: {}, unsupported/skipped: {}", modelCount, unsupportedCount);
    }

    private boolean processModelRecord(String type, String lineStr) throws InterpssException {
        String[] fields = splitFields(lineStr);
        if (fields.length < 3) return false;

        int busNum = Math.abs(Integer.parseInt(fields[0]));
        String busId = BUS_ID_PREFIX + busNum;
        String genId = fields[2].trim();

        switch (type) {
            case "GENCLS":
                return procGencls(busId, genId, fields);
            case "GENROU":
            case "GENROE":
                return procGenrou(busId, genId, fields);
            case "GENSAL":
            case "GENSAE":
                return procGensal(busId, genId, fields);
            case "GENTPF":
            case "GENTPJ":
            case "GENTPJU1":
            case "GENTPJ1":
                return procGenrou(busId, genId, fields);

            case "IEEET1":
                return procExcIeeet1(busId, genId, fields);
            case "IEEEX1":
                return procExcIeeex1(busId, genId, fields);
            case "EXST1":
                return procExcExst1(busId, genId, fields);
            case "EXAC1":
                return procExcExac1(busId, genId, fields);
            case "ESST3A":
            case "ESST4B":
                log.debug("Exciter model {} at bus {} - parsed as IEEET1 fallback", type, busId);
                return false;

            case "IEEEG1":
                return procGovIeeeg1(busId, genId, fields);
            case "TGOV1":
                return procGovTgov1(busId, genId, fields);
            case "GAST":
                return procGovGast(busId, genId, fields);
            case "IEESGO":
                return procGovIeesgo(busId, genId, fields);
            case "IEEEG3":
                log.debug("Governor model IEEEG3 at bus {} - not yet implemented", busId);
                return false;

            case "CMPLDW":
            case "ACMTBLU1":
            case "CIM6BL":
            case "CMLDBLU2":
            case "LDS3BL":
            case "LVS3BL":
            case "FRQTPAT":
            case "VTGTPAT":
                log.debug("Dynamic load/relay model {} at bus {} - skipped in direct parser", type, busId);
                return false;

            default:
                log.debug("Unsupported dynamic model type: {} at bus {}", type, busId);
                return false;
        }
    }

    // ==================== Generator Model Parsers ====================

    // GENCLS: IBUS 'GENCLS' ID H D
    //         idx:  0    1    2  3 4
    private boolean procGencls(String busId, String genId, String[] f) throws InterpssException {
        double h = getDouble(f, 3, 0);
        double d = getDouble(f, 4, 0);
        double[] rating = getGenRating(busId, genId);
        builder.addGencls(busId, genId, rating[0], rating[1], h, d, 0, 0);
        return true;
    }

    // GENROU: IBUS 'GENROU' ID T'do T''do T'qo T''qo H D Xd Xq X'd X'q X''d Xl S(1.0) S(1.2)
    //         idx:  0    1    2   3    4    5    6   7 8  9  10  11  12  13  14   15     16
    private boolean procGenrou(String busId, String genId, String[] f) throws InterpssException {
        double td10 = getDouble(f, 3, 0);
        double td110 = getDouble(f, 4, 0);
        double tq10 = getDouble(f, 5, 0);
        double tq110 = getDouble(f, 6, 0);
        double h = getDouble(f, 7, 0);
        double d = getDouble(f, 8, 0);
        double xd = getDouble(f, 9, 0);
        double xq = getDouble(f, 10, 0);
        double xd1 = getDouble(f, 11, 0);
        double xq1 = getDouble(f, 12, 0);
        double xd11 = getDouble(f, 13, 0);
        double xl = getDouble(f, 14, 0);
        double s100 = getDouble(f, 15, 0) * 100;
        double s120 = getDouble(f, 16, 0) * 100;
        double[] rating = getGenRating(busId, genId);
        builder.addGenrou(busId, genId, rating[0], rating[1],
                td10, td110, tq10, tq110, h, d, xd, xq, xd1, xq1, xd11, xl, s100, s120);
        return true;
    }

    // GENSAL: IBUS 'GENSAL' ID T'do T''do T''qo H D Xd Xq X'd X''d Xl S(1.0) S(1.2)
    //         idx:  0    1   2   3    4     5   6 7  8  9  10  11  12   13     14
    private boolean procGensal(String busId, String genId, String[] f) throws InterpssException {
        double td10 = getDouble(f, 3, 0);
        double td110 = getDouble(f, 4, 0);
        double tq110 = getDouble(f, 5, 0);
        double h = getDouble(f, 6, 0);
        double d = getDouble(f, 7, 0);
        double xd = getDouble(f, 8, 0);
        double xq = getDouble(f, 9, 0);
        double xd1 = getDouble(f, 10, 0);
        double xd11 = getDouble(f, 11, 0);
        double xl = getDouble(f, 12, 0);
        double s100 = getDouble(f, 13, 0) * 100;
        double s120 = getDouble(f, 14, 0) * 100;
        double[] rating = getGenRating(busId, genId);
        builder.addGensal(busId, genId, rating[0], rating[1],
                td10, td110, tq110, h, d, xd, xq, xd1, xd11, xl, s100, s120);
        return true;
    }

    // ==================== Exciter Model Parsers ====================

    // IEEET1: IBUS 'IEEET1' ID TR KA TA TB TC VRMAX VRMIN KE TE KF TF1 Switch E1 SE(E1) E2 SE(E2)
    //         idx:  0    1    2  3  4  5  6  7   8     9   10 11 12 13   14    15  16    17   18
    private boolean procExcIeeet1(String busId, String genId, String[] f) throws InterpssException {
        double tr = getDouble(f, 3, 0);
        double ka = getDouble(f, 4, 0);
        double ta = getDouble(f, 5, 0);
        double vrmax = getDouble(f, 8, 0);
        double vrmin = getDouble(f, 9, 0);
        double ke = getDouble(f, 10, 0);
        double te = getDouble(f, 11, 0);
        double kf = getDouble(f, 12, 0);
        double tf = getDouble(f, 13, 0);
        double e1 = getDouble(f, 15, 0);
        double seE1 = getDouble(f, 16, 0);
        double e2 = getDouble(f, 17, 0);
        double seE2 = getDouble(f, 18, 0);
        builder.addExcIeeet1(busId, genId, tr, ka, ta, vrmax, vrmin, ke, te, kf, tf, e1, seE1, e2, seE2);
        return true;
    }

    // IEEEX1: same format as IEEET1 -> maps to IEEE1981DC1
    private boolean procExcIeeex1(String busId, String genId, String[] f) throws InterpssException {
        double ka = getDouble(f, 4, 0);
        double ta = getDouble(f, 5, 0);
        double tb = getDouble(f, 6, 0);
        double tc = getDouble(f, 7, 0);
        double vrmax = getDouble(f, 8, 0);
        double vrmin = getDouble(f, 9, 0);
        double ke = getDouble(f, 10, 0);
        double te = getDouble(f, 11, 0);
        double kf = getDouble(f, 12, 0);
        double tf = getDouble(f, 13, 0);
        double e1 = getDouble(f, 15, 0);
        double seE1 = getDouble(f, 16, 0);
        double e2 = getDouble(f, 17, 0);
        double seE2 = getDouble(f, 18, 0);
        builder.addExcIeee1981Dc1(busId, genId, ka, ta, tc, tb, vrmax, vrmin, ke, te, kf, tf, e1, seE1, e2, seE2);
        return true;
    }

    // EXST1: IBUS 'EXST1' ID TR VIMAX VIMIN TC TB KA TA VRMAX VRMIN KC KF TF
    //        idx:  0   1   2  3   4     5    6  7  8  9   10    11   12 13 14
    private boolean procExcExst1(String busId, String genId, String[] f) throws InterpssException {
        double ka = getDouble(f, 8, 0);
        double ta = getDouble(f, 9, 0);
        double tc = getDouble(f, 6, 0);
        double tb = getDouble(f, 7, 0);
        double vrmax = getDouble(f, 10, 0);
        double vrmin = getDouble(f, 11, 0);
        double kf = getDouble(f, 13, 0);
        double tf = getDouble(f, 14, 0);
        double kc = getDouble(f, 12, 0);
        double vimax = getDouble(f, 4, 0);
        double vimin = getDouble(f, 5, 0);
        builder.addExcIeee1981St1(busId, genId, ka, ta, tc, tb, vrmax, vrmin, kf, tf, kc, vimax, vimin);
        return true;
    }

    // EXAC1: maps to IEEE1981AC1 - use DC1 as close approximation
    private boolean procExcExac1(String busId, String genId, String[] f) throws InterpssException {
        double ka = getDouble(f, 6, 0);
        double ta = getDouble(f, 7, 0);
        double tb = getDouble(f, 4, 0);
        double tc = getDouble(f, 5, 0);
        double vrmax = getDouble(f, 8, 0);
        double vrmin = getDouble(f, 9, 0);
        double ke = getDouble(f, 15, 0);
        double te = getDouble(f, 10, 0);
        double kf = getDouble(f, 11, 0);
        double tf = getDouble(f, 12, 0);
        double e1 = getDouble(f, 16, 0);
        double seE1 = getDouble(f, 17, 0);
        double e2 = getDouble(f, 18, 0);
        double seE2 = getDouble(f, 19, 0);
        builder.addExcIeee1981Dc1(busId, genId, ka, ta, tc, tb, vrmax, vrmin, ke, te, kf, tf, e1, seE1, e2, seE2);
        return true;
    }

    // ==================== Governor Model Parsers ====================

    // IEEEG1: IBUS 'IEEEG1' ID JBUS K T1 T2 T3 Uo Uc PMAX PMIN T4 K1 K2 T5 K3 K4 T6 K5 K6 T7 K7 K8
    //         idx:  0    1    2   3   4 5  6  7  8  9  10   11   12 13 14 15 16 17 18 19 20 21 22 23
    private boolean procGovIeeeg1(String busId, String genId, String[] f) throws InterpssException {
        double k = getDouble(f, 4, 0);
        double t1 = getDouble(f, 5, 0);
        double t2 = getDouble(f, 6, 0);
        double t3 = getDouble(f, 7, 0);
        double uo = getDouble(f, 8, 0);
        double uc = getDouble(f, 9, 0);
        double pmax = getDouble(f, 10, 0);
        double pmin = getDouble(f, 11, 0);
        double t4 = getDouble(f, 12, 0);   // Tch
        double k1 = getDouble(f, 13, 0);   // Fvhp
        double k2 = getDouble(f, 14, 0);
        double t5 = getDouble(f, 15, 0);   // Trh1
        double k3 = getDouble(f, 16, 0);   // Fhp
        double k4 = getDouble(f, 17, 0);
        double t6 = getDouble(f, 18, 0);   // Trh2
        double k5 = getDouble(f, 19, 0);   // Fip
        double k6 = getDouble(f, 20, 0);
        double t7 = getDouble(f, 21, 0);   // Tco
        double k7 = getDouble(f, 22, 0);   // Flp
        double k8 = getDouble(f, 23, 0);

        if (k2 != 0 || k4 != 0 || k6 != 0 || k8 != 0) {
            log.warn("IEEEG1 with dual PMech outputs not supported. Bus: {}", busId);
            return false;
        }

        builder.addGovIeeeg1(busId, genId, k, t1, t2, t3,
                k1, k3, t4, k5, t5, k7, t6, t7, uc, uo, pmax, pmin);
        return true;
    }

    // TGOV1: IBUS 'TGOV1' ID R T1 VMAX VMIN T2 T3 Dt
    //        idx:  0   1   2  3 4   5    6    7  8  9
    private boolean procGovTgov1(String busId, String genId, String[] f) throws InterpssException {
        double r = getDouble(f, 3, 0);
        double t1 = getDouble(f, 4, 0);
        double vmax = getDouble(f, 5, 0);
        double vmin = getDouble(f, 6, 0);
        double t2 = getDouble(f, 7, 0);
        double t3 = getDouble(f, 8, 0);
        double dt = getDouble(f, 9, 0);
        builder.addGovTgov1(busId, genId, r, t1, vmax, vmin, t2, t3, dt);
        return true;
    }

    // GAST: IBUS 'GAST' ID R T1 T2 T3 AT KT VMAX VMIN Dturb
    //       idx:  0   1  2  3 4  5  6  7  8   9   10   11
    private boolean procGovGast(String busId, String genId, String[] f) throws InterpssException {
        double r = getDouble(f, 3, 0);
        double t1 = getDouble(f, 4, 0);
        double t2 = getDouble(f, 5, 0);
        double t3 = getDouble(f, 6, 0);
        double at = getDouble(f, 7, 0);
        double kt = getDouble(f, 8, 0);
        double vmax = getDouble(f, 9, 0);
        double vmin = getDouble(f, 10, 0);
        double dturb = getDouble(f, 11, 0);
        builder.addGovGast(busId, genId, r, t1, t2, t3, at, kt, vmax, vmin, dturb);
        return true;
    }

    // IEESGO: IBUS 'IEESGO' ID T1 T2 T3 T4 T5 T6 K1 K2 K3 PMAX PMIN
    //         idx:  0    1   2  3  4  5  6  7  8  9 10 11  12   13
    private boolean procGovIeesgo(String busId, String genId, String[] f) throws InterpssException {
        double t1 = getDouble(f, 3, 0);
        double t2 = getDouble(f, 4, 0);
        double t3 = getDouble(f, 5, 0);
        double t4 = getDouble(f, 6, 0);
        double t5 = getDouble(f, 7, 0);
        double t6 = getDouble(f, 8, 0);
        double k1 = getDouble(f, 9, 0);
        double k2 = getDouble(f, 10, 0);
        double k3 = getDouble(f, 11, 0);
        double pmax = getDouble(f, 12, 0);
        double pmin = getDouble(f, 13, 0);
        builder.addGovIeesgo(busId, genId, t1, t2, t3, t4, t5, t6, k1, k2, k3, pmax, pmin);
        return true;
    }

    // ==================== Utility Methods ====================

    @SuppressWarnings("unchecked")
    private double[] getGenRating(String busId, String genId) {
        DStabilityNetwork net = builder.getDStabNetwork();
        var bus = net.getDStabBus(busId);
        if (bus != null) {
            DStabGen gen = (DStabGen) bus.getContributeGen(genId);
            if (gen != null) {
                double mbase = gen.getMvaBase();
                double ratedKv = bus.getBaseVoltage() / 1000.0;
                return new double[]{ mbase > 0 ? mbase : net.getBaseKva() / 1000.0, ratedKv };
            }
        }
        return new double[]{ net.getBaseKva() / 1000.0, 1.0 };
    }

    private String[] splitFields(String lineStr) {
        if (lineStr.contains(","))
            return lineStr.split("\\s*(\\s|,)\\s*");
        else
            return lineStr.split("\\s+");
    }

    private String getModelType(String lineStr) {
        String[] strAry = splitFields(lineStr);
        if (strAry.length > 2) {
            String field1 = trimQuote(strAry[1]);
            if (field1.equals("USRLOD") || field1.equals("USRMDL")) {
                return strAry.length > 3 ? trimQuote(strAry[3]) : null;
            }
            return field1;
        }
        return null;
    }

    private boolean isModelDataCompleted(String lineStr) {
        return lineStr.trim().lastIndexOf("/") > 0;
    }

    private boolean skipInvalidLine(String lineStr) {
        String trimmed = lineStr.trim();
        if (trimmed.isEmpty()) return true;
        if (trimmed.startsWith("//") || trimmed.startsWith("/")) return true;
        String[] parts = splitFields(trimmed);
        return !parts[0].matches("-?\\d+");
    }

    private String trimQuote(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("'") && s.endsWith("'") && s.length() > 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private double getDouble(String[] fields, int idx, double defaultVal) {
        if (idx >= fields.length) return defaultVal;
        try {
            return Double.parseDouble(fields[idx].trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
