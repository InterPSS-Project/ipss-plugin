package org.interpss.fadapter.psse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.interpss.dstab.dynLoad.LD1PAC;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportReport;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;
import org.interpss.dstab.renewable.Reecb1Data;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regfma1Data;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Wtara1Data;
import org.interpss.dstab.renewable.Wtpta1Data;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.dstab.mach.GenqecData;
import org.interpss.dstab.mach.GenqejData;
import org.interpss.dstab.control.pss.psse.st2cut.St2cutData;
import org.interpss.dstab.control.pss.psse.st2cut.St2cutStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestData;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernorData;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciterData;
import org.interpss.dstab.control.exc.psse.scrx.ScrxData;
import org.interpss.dstab.control.exc.psse.esac5a.Esac5aData;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Data;
import org.interpss.dstab.control.exc.psse.exac1a.Exac1aData;
import org.interpss.dstab.control.exc.psse.exac2.Exac2Data;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aData;
import org.interpss.dstab.control.exc.psse.esac2a.Esac2aData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;

/**
 * Direct PSS/E dynamic data file parser that bypasses the ODM XML intermediate layer.
 * Reads PSS/E dynamic model data and populates a BaseDStabNetwork via DStabNetworkBuilder.
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
    private final List<PendingSt2cut> pendingSt2cut = new ArrayList<>();
    private final List<PendingIeeest> pendingIeeest = new ArrayList<>();
    private final List<PendingRepca1> pendingRepca1 = new ArrayList<>();
    private boolean strictImport;
    private final Set<GeneratorKey> gnetRemovedGenerators = new HashSet<>();
    private DynamicModelImportReport lastImportReport = DynamicModelImportReport.empty();

    public PSSEDStabDirectParser(DStabNetworkBuilder builder) {
        this.builder = builder;
    }

    /** Enable or disable fail-fast coverage checking after a complete DYR import. */
    public PSSEDStabDirectParser setStrictImport(boolean strictImport) {
        this.strictImport = strictImport;
        return this;
    }

    /** Identify generator records made intentionally non-applicable by GNET. */
    public PSSEDStabDirectParser setGnetRemovedGenerators(Collection<GeneratorKey> keys) {
        gnetRemovedGenerators.clear();
        if (keys != null) gnetRemovedGenerators.addAll(keys);
        return this;
    }

    /** Report for the most recent import, including source line and disposition per record. */
    public DynamicModelImportReport getLastImportReport() {
        return lastImportReport;
    }

    public BaseDStabNetwork<?, ?> parseDynFile(String dynFilePath) throws InterpssException {
        try {
            parseDynData(PsseDyrRecordReader.read(Path.of(dynFilePath)), dynFilePath);
        } catch (IOException e) {
            throw new InterpssException("Error reading dynamic file: " + dynFilePath + " - " + e.toString());
        } catch (IllegalArgumentException e) {
            throw new InterpssException("Invalid dynamic file: " + dynFilePath + " - " + e.getMessage());
        }
        return builder.getBaseDStabNetwork();
    }

    private void parseDynData(List<PsseDyrRecord> records, String source) throws InterpssException {
        pendingSt2cut.clear();
        pendingIeeest.clear();
        pendingRepca1.clear();
        DynamicModelImportReport.Builder report = DynamicModelImportReport.builder(source);
        for (PsseDyrRecord record : records) {
            try {
                String type = record.canonicalModelName();
                GeneratorKey key = new GeneratorKey(BUS_ID_PREFIX + record.busNumber(),
                        record.deviceId());
                if (gnetRemovedGenerators.contains(key)) {
                    report.add(record, DynamicModelImportStatus.SKIPPED_GNET,
                            "generator intentionally removed by GNET preprocessing");
                    continue;
                }
                if (!hasExpectedParameterCount(record)) {
                    report.add(record, DynamicModelImportStatus.REJECTED,
                            rejectionMessage(type, record));
                    continue;
                }
                String missingTarget = missingCatalogTarget(record);
                if (missingTarget != null) {
                    report.add(record, DynamicModelImportStatus.MISSING_TARGET, missingTarget);
                    continue;
                }
                boolean deferred = type.equals("ST2CUT") || type.equals("IEEEST")
                        || type.equals("REPCA1");
                if (processModelRecord(type, record.fields().toArray(String[]::new), record)) {
                    if (!deferred) report.add(record, attachedStatus(record),
                            attachedMessage(record));
                } else {
                    report.add(record, rejectedStatus(type), rejectionMessage(type, record));
                }
            } catch (Exception e) {
                log.warn("Error processing dynamic record at {}:{}: {}",
                        record.source(), record.startLine(), e.getMessage());
                report.add(record, DynamicModelImportStatus.ERROR, e.getMessage());
            }
        }
        for (PendingSt2cut pending : pendingSt2cut) {
            boolean attached = procPssSt2cut(pending.busId(), pending.genId(), pending.fields());
            report.add(pending.record(), attached ? DynamicModelImportStatus.ATTACHED
                    : DynamicModelImportStatus.REJECTED,
                    attached ? "" : "ST2CUT prerequisites or signal mode are unsupported");
        }
        pendingSt2cut.clear();
        for (PendingIeeest pending : pendingIeeest) {
            boolean attached = procPssIeeest(pending.busId(), pending.genId(), pending.fields());
            report.add(pending.record(), attached ? DynamicModelImportStatus.ATTACHED
                    : DynamicModelImportStatus.REJECTED,
                    attached ? "" : "IEEEST prerequisites, remote bus, or signal mode are unsupported");
        }
        pendingIeeest.clear();
        for (PendingRepca1 pending : pendingRepca1) {
            boolean attached = procRepca1(pending.busId(), pending.genId(), pending.fields());
            report.add(pending.record(), attached ? DynamicModelImportStatus.ATTACHED
                    : DynamicModelImportStatus.REJECTED,
                    attached ? "" : "REPCA1 requires REGCA1/REEC or REGFMA1 on the same generator");
        }
        pendingRepca1.clear();
        lastImportReport = report.build();
        log.info("Dynamic model import: {}", lastImportReport.failureSummary());
        if (strictImport && !lastImportReport.isStrictlyComplete()) {
            throw new InterpssException("Strict DYR import failed: " + lastImportReport.failureSummary());
        }
    }

    private boolean hasExpectedParameterCount(PsseDyrRecord record) {
        return DynamicModelCatalog.find(record.canonicalModelName())
                .map(model -> model.recordSchema().accepts(record.parameterCount()))
                .orElse(true);
    }

    @SuppressWarnings("unchecked")
    private String missingCatalogTarget(PsseDyrRecord record) {
        if (DynamicModelCatalog.find(record.canonicalModelName()).isEmpty()) return null;
        String busId = BUS_ID_PREFIX + record.busNumber();
        BaseDStabBus<?, ?> bus = builder.getBaseDStabNetwork().getDStabBus(busId);
        if (bus == null) return "target bus " + busId + " does not exist";
        DStabGen gen = (DStabGen) bus.getContributeGen(record.deviceId());
        return gen == null ? "target generator " + busId + "/" + record.deviceId()
                + " does not exist" : null;
    }

    private boolean processModelRecord(String type, String[] fields, PsseDyrRecord record)
            throws InterpssException {
        if (fields.length < 3) return false;

        String busId = BUS_ID_PREFIX + record.busNumber();
        String genId = record.deviceId();

        switch (type) {
            case "GENCLS":
                return procGencls(busId, genId, fields);
            case "GENROU":
            case "GENROE":
                return procGenrou(busId, genId, fields);
            case "GENQEC":
                return procGenqec(busId, genId, fields);
            case "GENQEJ":
                return procGenqej(busId, genId, fields);
            case "GENSAL":
            case "GENSAE":
                return procGensal(busId, genId, fields);
            case "IEEET1":
                return procExcIeeet1(busId, genId, fields);
            case "IEEEX1":
                return procExcIeeex1(busId, genId, fields);
            case "EXDC2":
                return procExcExdc2(busId, genId, fields);
            case "EXDC2A":
                return procExcExdc2a(busId, genId, fields);
            case "AC8B":
                return procExcAc8b(busId, genId, fields);
            case "IEEET4":
            case "EXDC4":
                return procExcIeeet4(type, busId, genId, fields);
            case "EXST1":
                return procExcExst1(busId, genId, fields);
            case "ESST1A":
                return procExcEsst1a(busId, genId, fields);
            case "EXAC1":
                return procExcExac1(busId, genId, fields);
            case "EXAC1A":
                return procExcExac1a(busId, genId, fields);
            case "EXAC2":
                return procExcExac2(busId, genId, fields);
            case "ESAC1A":
                return procExcEsac1a(busId, genId, fields);
            case "ESAC2A":
                return procExcEsac2a(busId, genId, fields);
            case "ESDC2A":
                return procExcEsdc2a(busId, genId, fields);
            case "ESDC1A":
                return procExcEsdc1a(busId, genId, fields);
            case "ESAC5A":
                return procExcEsac5a(busId, genId, fields);
            case "ESST3A":
                return procExcEsst3a(busId, genId, fields);
            case "ESST4B":
                return procExcEsst4b(busId, genId, fields);
            case "SCRX":
                return procExcScrx(busId, genId, fields);

            case "IEEEG1":
                return procGovIeeeg1(busId, genId, fields);
            case "IEEEG1D":
            case "IEEEG1SDU":
                return procGovIeeeg1d(busId, genId, fields);
            case "TGOV1":
                return procGovTgov1(busId, genId, fields);
            case "TGOV1D":
            case "TGOV1DU":
                return procGovTgov1d(busId, genId, fields);
            case "GAST":
                return procGovGast(busId, genId, fields);
            case "GASTD":
            case "GASTDU":
                return procGovGastd(busId, genId, fields);
            case "IEESGO":
                return procGovIeesgo(busId, genId, fields);
            case "IEESGOD":
            case "IEESGODU":
                return procGovIeesgod(busId, genId, fields);
            case "GGOV1":
                return procGovGgov1(busId, genId, fields, false);
            case "GGOV1D":
            case "GGOV1DU":
                return procGovGgov1(busId, genId, fields, true);
            case "HYGOV":
                return procGovHygov(busId, genId, fields, false);
            case "HYGOVD":
            case "HYGOVDU":
                return procGovHygov(busId, genId, fields, true);
            case "IEEEG3":
                log.debug("Governor model IEEEG3 at bus {} - not yet implemented", busId);
                return false;

            case "ST2CUT":
                pendingSt2cut.add(new PendingSt2cut(busId, genId, fields.clone(), record));
                return true;
            case "IEEEST":
                pendingIeeest.add(new PendingIeeest(busId, genId, fields.clone(), record));
                return true;
            case "PSS2A":
                return procPss2a(busId, genId, fields);
            case "PSS2B":
                return procPss2b(busId, genId, fields);
            case "PSS2C":
                return procPss2c(busId, genId, fields);
            case "PSS3B":
                return procPss3b(busId, genId, fields);
            case "PSS4B":
                return procPss4b(busId, genId, fields);
            case "PSS3C":
                return procPss3c(busId, genId, fields);
            case "PSS4C":
                return procPss4c(busId, genId, fields);
            case "PSS5C":
                return procPss5c(busId, genId, fields);
            case "PSS6C":
                return procPss6c(busId, genId, fields);
            case "PSS7C":
                return procPss7c(busId, genId, fields);
            case "PSS1A":
                return procPss1a(busId, genId, fields);

            case "CMPLDW":
            case "CIM6BL":
            case "CMLDBLU2":
            case "LDS3BL":
            case "LVS3BL":
            case "FRQTPAT":
            case "VTGTPAT":
                log.debug("Dynamic load/relay model {} at bus {} - skipped in direct parser", type, busId);
                return false;

            case "ACMTBLU1":
                return procAcmtblu1(busId, genId, fields);

            case "REGCA1":
            case "REGCAU1":
                return procRegca1(busId, genId, fields);
            case "REGFMA1":
                return procRegfma1(busId, genId, fields);
            case "REECB1":
            case "REECBU1":
                return procReecb1(busId, genId, fields);
            case "REECA1":
            case "REECAU1":
                return procReeca1(busId, genId, fields);
            case "WTARA1":
            case "WTARAU1":
                return procWtara1(busId, genId, fields);
            case "WTPTA1":
            case "WTPTAU1":
                return procWtpta1(busId, genId, fields);
            case "WTTQA1":
            case "WTTQAU1":
                return procWttqa1(busId, genId, fields);
            case "REPCA1":
            case "REPCAU1":
                pendingRepca1.add(new PendingRepca1(busId, genId, fields.clone(), record));
                return true;

            default:
                log.debug("Unsupported dynamic model type: {} at bus {}", type, busId);
                return false;
        }
    }

    // USRLOD/ACMTBLU1:
    // IBUS, 'USRLOD', LID, 'ACMTBLU1', seven model-library fields,
    // then the 35 LD1PAC parameters.
    private boolean procAcmtblu1(String busId, String loadId, String[] f) {
        if (f.length < 46) {
            log.warn("Incomplete ACMTBLU1 record at bus {}: expected 46 fields, found {}", busId, f.length);
            return false;
        }

        BaseDStabBus<?, ?> bus = builder.getBaseDStabNetwork().getDStabBus(busId);
        if (bus == null) {
            log.warn("Cannot attach ACMTBLU1 model {}: bus {} was not found", loadId, busId);
            return false;
        }

        double compLf = getDouble(f, 15, 1.0);
        if (compLf <= 0.0) {
            compLf = 1.0;
        }

        LD1PAC acMotor = new LD1PACImpl(bus, trimQuote(loadId));
        acMotor.setTstall(getDouble(f, 11, 0.0));
        acMotor.setTrst(getDouble(f, 12, 0.0));
        acMotor.setTv(getDouble(f, 13, 0.0));
        acMotor.setLoadPercent(compLf * 100.0);
        acMotor.setLoadFactor(compLf);
        acMotor.setPowerFactor(getDouble(f, 16, 0.0));
        acMotor.setVstall(getDouble(f, 17, 0.0));
        acMotor.setRstall(getDouble(f, 18, 0.0));
        acMotor.setXstall(getDouble(f, 19, 0.0));
        acMotor.setLFadj(getDouble(f, 20, 0.0));
        acMotor.setKp1(getDouble(f, 21, 0.0));
        acMotor.setNp1(getDouble(f, 22, 0.0));
        acMotor.setKq1(getDouble(f, 23, 0.0));
        acMotor.setNq1(getDouble(f, 24, 0.0));
        acMotor.setKp2(getDouble(f, 25, 0.0));
        acMotor.setNp2(getDouble(f, 26, 0.0));
        acMotor.setKq2(getDouble(f, 27, 0.0));
        acMotor.setNq2(getDouble(f, 28, 0.0));
        acMotor.setVbrk(getDouble(f, 29, 0.0));
        acMotor.setFrst(getDouble(f, 30, 0.0));
        acMotor.setVrst(getDouble(f, 31, 0.0));
        acMotor.setCmpKpf(getDouble(f, 32, 0.0));
        acMotor.setCmpKqf(getDouble(f, 33, 0.0));
        acMotor.setVc1off(getDouble(f, 34, 0.0));
        acMotor.setVc2off(getDouble(f, 35, 0.0));
        acMotor.setVc1on(getDouble(f, 36, 0.0));
        acMotor.setVc2on(getDouble(f, 37, 0.0));
        acMotor.setTth(getDouble(f, 38, 0.0));
        acMotor.setTh1t(getDouble(f, 39, 0.0));
        acMotor.setTh2t(getDouble(f, 40, 0.0));
        acMotor.setFuvr(getDouble(f, 41, 0.0));
        acMotor.setUVtr1(getDouble(f, 42, 0.0));
        acMotor.setTtr1(getDouble(f, 43, 0.0));
        acMotor.setUVtr2(getDouble(f, 44, 0.0));
        acMotor.setTtr2(getDouble(f, 45, 0.0));
        return true;
    }

    // ==================== Generator Model Parsers ====================

    // GENCLS: IBUS 'GENCLS' ID H D
    //         idx:  0    1    2  3 4
    private boolean procGencls(String busId, String genId, String[] f) throws InterpssException {
        double h = getDouble(f, 3, 0);
        double d = getDouble(f, 4, 0);
        double[] rating = getGenRating(busId, genId);

        double ra = 0, xd1 = 0;
        BaseDStabNetwork<?, ?> net = builder.getBaseDStabNetwork();
        var bus = net.getDStabBus(busId);
        if (bus != null) {
            DStabGen gen = (DStabGen) bus.getContributeGen(genId);
            if (gen != null && gen.getSourceZ() != null) {
                ra = gen.getSourceZ().getReal();
                xd1 = gen.getSourceZ().getImaginary();
            }
        }
        if (xd1 == 0 && h > 99999) {
            xd1 = 0.00001;
        }

        return builder.addGencls(busId, genId, rating[0], rating[1], h, d, ra, xd1) != null;
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
        return builder.addGenrou(busId, genId, rating[0], rating[1],
                td10, td110, tq10, tq110, h, d, xd, xq, xd1, xq1, xd11, xl,
                s100, s120) != null;
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
        return builder.addGensal(busId, genId, rating[0], rating[1],
                td10, td110, tq110, h, d, xd, xq, xd1, xd11, xl, s100, s120) != null;
    }

    // ==================== Exciter Model Parsers ====================

    // IEEET1: IBUS 'IEEET1' ID TR KA TA VRMAX VRMIN KE TE KF TF1 Switch E1 SE(E1) E2 SE(E2)
    //         idx:  0    1    2  3  4  5   6     7   8  9  10 11   12    13  14    15   16
    private boolean procExcIeeet1(String busId, String genId, String[] f) throws InterpssException {
        double tr = getDouble(f, 3, 0);
        double ka = getDouble(f, 4, 0);
        double ta = getDouble(f, 5, 0);
        double vrmax = getDouble(f, 6, 0);
        double vrmin = getDouble(f, 7, 0);
        double ke = getDouble(f, 8, 0);
        double te = getDouble(f, 9, 0);
        double kf = getDouble(f, 10, 0);
        double tf = getDouble(f, 11, 0);
        double e1 = getDouble(f, 13, 0);
        double seE1 = getDouble(f, 14, 0);
        double e2 = getDouble(f, 15, 0);
        double seE2 = getDouble(f, 16, 0);
        double spdmlt = getDouble(f, 17, 0);
        builder.addExcIeeet1(busId, genId, tr, ka, ta, vrmax, vrmin,
                ke, te, kf, tf, e1, seE1, e2, seE2, spdmlt);
        return true;
    }

    // IEEEX1: IBUS 'IEEEX1' ID TR KA TA TB TC VRMAX VRMIN KE TE KF TF SWITCH E1 SE1 E2 SE2
    private boolean procExcIeeex1(String busId, String genId, String[] f) throws InterpssException {
        double tr = getDouble(f, 3, 0);
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
        double switchValue = getDouble(f, 14, 0);
        double e1 = getDouble(f, 15, 0);
        double seE1 = getDouble(f, 16, 0);
        double e2 = getDouble(f, 17, 0);
        double seE2 = getDouble(f, 18, 0);
        return builder.addExcIeeex1(busId, genId, tr, ka, ta, tb, tc,
                vrmax, vrmin, ke, te, kf, tf, switchValue,
                e1, seE1, e2, seE2) != null;
    }

    // EXDC2: IBUS 'EXDC2' ID TR KA TA TB TC VRMAX VRMIN KE TE KF TF SWITCH E1 SE1 E2 SE2
    private boolean procExcExdc2(String busId, String genId, String[] f) {
        return builder.addExcExdc2(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0), getDouble(f, 17, 0),
                getDouble(f, 18, 0)) != null;
    }

    // EXDC2A: IBUS 'EXDC2A' ID TR KA TA TB TC VRMAX VRMIN KE TE KF TF1 TF2 E1 SE1 E2 SE2
    private boolean procExcExdc2a(String busId, String genId, String[] f) {
        return builder.addExcExdc2a(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0), getDouble(f, 17, 0),
                getDouble(f, 18, 0)) != null;
    }

    // IEEE 421.5-2005/PSS/E AC8B (21 values). This is the schema used by
    // ANDES and the supplied cases, not PowerWorld's newer extended data form.
    // IBUS 'AC8B' ID TR KPR KIR KDR TDR VPIDMAX VPIDMIN VRMAX VRMIN
    //                 VFEMAX VEMIN TA KA TE KC KD KE E1 SE1 E2 SE2
    private boolean procExcAc8b(String busId, String genId, String[] f) {
        if (f.length < 24) return false;
        org.interpss.dstab.control.exc.psse.ac8b.Ac8bData d =
                new org.interpss.dstab.control.exc.psse.ac8b.Ac8bData();
        d.setTr(getDouble(f,3,0)); d.setKpr(getDouble(f,4,0));
        d.setKir(getDouble(f,5,0)); d.setKdr(getDouble(f,6,0));
        d.setTdr(getDouble(f,7,0)); d.setVpidmax(getDouble(f,8,0));
        d.setVpidmin(getDouble(f,9,0)); d.setVrmax(getDouble(f,10,0));
        d.setVrmin(getDouble(f,11,0)); d.setVfemax(getDouble(f,12,0));
        d.setVemin(getDouble(f,13,0)); d.setTa(getDouble(f,14,0));
        d.setKa(getDouble(f,15,0)); d.setTe(getDouble(f,16,0));
        d.setKc(getDouble(f,17,0)); d.setKd(getDouble(f,18,0));
        d.setKe(getDouble(f,19,0)); d.setE1(getDouble(f,20,0));
        d.setSe1(getDouble(f,21,0)); d.setE2(getDouble(f,22,0));
        d.setSe2(getDouble(f,23,0));
        return builder.addExcAc8b(busId,genId,d) != null;
    }

    // IEEET4/EXDC4: IBUS MODEL ID KR TRH KV VRMAX VRMIN TE KE E1 SE1 E2 SE2
    private boolean procExcIeeet4(String modelName, String busId, String genId, String[] f) {
        return builder.addExcIeeet4(busId, genId, modelName,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0)) != null;
    }

    // EXST1: IBUS 'EXST1' ID TR VIMAX VIMIN TC TB KA TA VRMAX VRMIN KC KF TF
    //        idx:  0   1   2  3   4     5    6  7  8  9   10    11   12 13 14
    private boolean procExcExst1(String busId, String genId, String[] f) throws InterpssException {
        double tr = getDouble(f, 3, 0);
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
        builder.addExcIeee1981St1(busId, genId, tr, ka, ta, tc, tb,
                vrmax, vrmin, kf, tf, kc, vimax, vimin);
        return true;
    }

    // EXAC1: IBUS 'EXAC1' ID Tr Tb Tc Ka Ta Vrmax Vrmin Te Kf Tf Kc Kd Ke E1 SE1 E2 SE2 [Spdmlt]
    private boolean procExcExac1(String busId,String genId,String[] f) {
        if (f.length<20) return false;
        Exac1Data d=new Exac1Data();
        d.setTr(getDouble(f,3,0)); d.setTb(getDouble(f,4,0)); d.setTc(getDouble(f,5,0));
        d.setKa(getDouble(f,6,0)); d.setTa(getDouble(f,7,0)); d.setVrmax(getDouble(f,8,0));
        d.setVrmin(getDouble(f,9,0)); d.setTe(getDouble(f,10,0)); d.setKf(getDouble(f,11,0));
        d.setTf(getDouble(f,12,0)); d.setKc(getDouble(f,13,0)); d.setKd(getDouble(f,14,0));
        d.setKe(getDouble(f,15,0)); d.setE1(getDouble(f,16,0)); d.setSe1(getDouble(f,17,0));
        d.setE2(getDouble(f,18,0)); d.setSe2(getDouble(f,19,0)); d.setSpdmlt(getDouble(f,20,0));
        return builder.addExcExac1(busId,genId,d)!=null;
    }

    // EXAC1A: EXAC1 parameters, with EFD rather than VFE driving the washout feedback.
    private boolean procExcExac1a(String busId,String genId,String[] f) {
        if (f.length<20) return false;
        Exac1aData d=new Exac1aData();
        d.setTr(getDouble(f,3,0)); d.setTb(getDouble(f,4,0)); d.setTc(getDouble(f,5,0));
        d.setKa(getDouble(f,6,0)); d.setTa(getDouble(f,7,0)); d.setVrmax(getDouble(f,8,0));
        d.setVrmin(getDouble(f,9,0)); d.setTe(getDouble(f,10,0)); d.setKf(getDouble(f,11,0));
        d.setTf(getDouble(f,12,0)); d.setKc(getDouble(f,13,0)); d.setKd(getDouble(f,14,0));
        d.setKe(getDouble(f,15,0)); d.setE1(getDouble(f,16,0)); d.setSe1(getDouble(f,17,0));
        d.setE2(getDouble(f,18,0)); d.setSe2(getDouble(f,19,0)); d.setSpdmlt(getDouble(f,20,0));
        return builder.addExcExac1a(busId,genId,d)!=null;
    }

    // EXAC2: IBUS MODEL ID Tr Tb Tc Ka Ta VaMax VaMin Kb VrMax VrMin Te Kl Kh Kf Tf Kc Kd Ke VLr E1 SE1 E2 SE2 [Spdmlt]
    private boolean procExcExac2(String busId,String genId,String[] f) {
        if(f.length<26)return false;
        Exac2Data d=new Exac2Data();
        d.setTr(getDouble(f,3,0));d.setTb(getDouble(f,4,0));d.setTc(getDouble(f,5,0));
        d.setKa(getDouble(f,6,0));d.setTa(getDouble(f,7,0));d.setVamax(getDouble(f,8,0));
        d.setVamin(getDouble(f,9,0));d.setKb(getDouble(f,10,0));d.setVrmax(getDouble(f,11,0));
        d.setVrmin(getDouble(f,12,0));d.setTe(getDouble(f,13,0));d.setKl(getDouble(f,14,0));
        d.setKh(getDouble(f,15,0));d.setKf(getDouble(f,16,0));d.setTf(getDouble(f,17,0));
        d.setKc(getDouble(f,18,0));d.setKd(getDouble(f,19,0));d.setKe(getDouble(f,20,0));
        d.setVlr(getDouble(f,21,0));d.setE1(getDouble(f,22,0));d.setSe1(getDouble(f,23,0));
        d.setE2(getDouble(f,24,0));d.setSe2(getDouble(f,25,0));d.setSpdmlt(getDouble(f,26,0));
        return builder.addExcExac2(busId,genId,d)!=null;
    }

    // ESAC1A: IBUS 'ESAC1A' ID Tr Tb Tc Ka Ta VaMax VaMin Te Kf Tf Kc Kd Ke E1 SE1 E2 SE2 VrMax VrMin [Spdmlt]
    private boolean procExcEsac1a(String busId,String genId,String[] f) {
        if (f.length<22) return false;
        Esac1aData d=new Esac1aData();
        d.setTr(getDouble(f,3,0)); d.setTb(getDouble(f,4,0)); d.setTc(getDouble(f,5,0));
        d.setKa(getDouble(f,6,0)); d.setTa(getDouble(f,7,0)); d.setVamax(getDouble(f,8,0));
        d.setVamin(getDouble(f,9,0)); d.setTe(getDouble(f,10,0)); d.setKf(getDouble(f,11,0));
        d.setTf(getDouble(f,12,0)); d.setKc(getDouble(f,13,0)); d.setKd(getDouble(f,14,0));
        d.setKe(getDouble(f,15,0)); d.setE1(getDouble(f,16,0)); d.setSe1(getDouble(f,17,0));
        d.setE2(getDouble(f,18,0)); d.setSe2(getDouble(f,19,0)); d.setVrmax(getDouble(f,20,0));
        d.setVrmin(getDouble(f,21,0)); d.setSpdmlt(getDouble(f,22,0));
        return builder.addExcEsac1a(busId,genId,d)!=null;
    }

    // ESAC2A: IBUS MODEL ID Tr Tb Tc Ka Ta VaMax VaMin Kb VrMax VrMin Te VfeMax Kh Kf Tf Kc Kd Ke E1 SE1 E2 SE2 [Spdmlt]
    private boolean procExcEsac2a(String busId,String genId,String[] f) {
        if (f.length<25) return false;
        Esac2aData d=new Esac2aData();
        d.setTr(getDouble(f,3,0));d.setTb(getDouble(f,4,0));d.setTc(getDouble(f,5,0));
        d.setKa(getDouble(f,6,0));d.setTa(getDouble(f,7,0));d.setVamax(getDouble(f,8,0));
        d.setVamin(getDouble(f,9,0));d.setKb(getDouble(f,10,0));d.setVrmax(getDouble(f,11,0));
        d.setVrmin(getDouble(f,12,0));d.setTe(getDouble(f,13,0));d.setVfemax(getDouble(f,14,0));
        d.setKh(getDouble(f,15,0));d.setKf(getDouble(f,16,0));d.setTf(getDouble(f,17,0));
        d.setKc(getDouble(f,18,0));d.setKd(getDouble(f,19,0));d.setKe(getDouble(f,20,0));
        d.setE1(getDouble(f,21,0));d.setSe1(getDouble(f,22,0));d.setE2(getDouble(f,23,0));
        d.setSe2(getDouble(f,24,0));d.setSpdmlt(getDouble(f,25,0));
        return builder.addExcEsac2a(busId,genId,d)!=null;
    }

    // ==================== Governor Model Parsers ====================

    private boolean procPssSt2cut(String busId, String genId, String[] f) {
        if (f.length < 23) return false;
        int mode1 = getInt(f, 3, 0);
        int mode2 = getInt(f, 5, 0);
        if (!isSupportedSt2cutMode(mode1) || !isSupportedSt2cutMode(mode2)
                || getInt(f, 4, 0) != 0 || getInt(f, 6, 0) != 0) {
            log.warn("ST2CUT remote or unsupported signal mode at bus {}", busId);
            return false;
        }
        var machine = builder.getBaseDStabNetwork().getMachine(busId + "-mach" + genId);
        if (machine == null || machine.getExciter() == null) {
            log.warn("ST2CUT at bus {} requires a loaded exciter", busId);
            return false;
        }
        St2cutData data = new St2cutData(
                mode1, getInt(f, 4, 0), mode2, getInt(f, 6, 0),
                getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0),
                getDouble(f, 11, 0), getDouble(f, 12, 0),
                getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0),
                getDouble(f, 17, 0), getDouble(f, 18, 0),
                getDouble(f, 19, 0), getDouble(f, 20, 0),
                getDouble(f, 21, 0), getDouble(f, 22, 0));
        new St2cutStabilizer(busId + "-st2cut" + genId, data, machine);
        return true;
    }

    // ESST1A: IBUS 'ESST1A' ID UEL VOS TR VIMAX VIMIN TC TB TC1 TB1 KA TA
    //         VAMAX VAMIN VRMAX VRMIN KC KF TF KLR ILR
    private boolean procExcEsst1a(String busId, String genId, String[] f) throws InterpssException {
        int uel = getInt(f, 3, 1);
        int vos = getInt(f, 4, 1);
        double tr = getDouble(f, 5, 0);
        double vimax = getDouble(f, 6, 0);
        double vimin = getDouble(f, 7, 0);
        double tc = getDouble(f, 8, 0);
        double tb = getDouble(f, 9, 0);
        double tc1 = getDouble(f, 10, 0);
        double tb1 = getDouble(f, 11, 0);
        double ka = getDouble(f, 12, 0);
        double ta = getDouble(f, 13, 0);
        double vamax = getDouble(f, 14, 0);
        double vamin = getDouble(f, 15, 0);
        double vrmax = getDouble(f, 16, 0);
        double vrmin = getDouble(f, 17, 0);
        double kc = getDouble(f, 18, 0);
        double kf = getDouble(f, 19, 0);
        double tf = getDouble(f, 20, 0);
        double klr = getDouble(f, 21, 0);
        double ilr = getDouble(f, 22, 0);
        builder.addExcEsst1a(busId, genId, uel, vos, tr, vimax, vimin,
                tc, tb, tc1, tb1, ka, ta, vamax, vamin, vrmax, vrmin,
                kc, kf, tf, klr, ilr);
        return true;
    }

    private boolean procExcEsdc2a(String busId, String genId, String[] f) {
        if (f.length < 19) return false;
        return builder.addExcEsdc2a(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 7, 0), getDouble(f, 6, 0),
                getDouble(f, 8, 0), getDouble(f, 9, 0),
                getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0),
                getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0),
                getDouble(f, 17, 0), getDouble(f, 18, 0)) != null;
    }

    private boolean procExcEsdc1a(String busId, String genId, String[] f) {
        if (f.length < 19) return false;
        return builder.addExcEsdc1a(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 7, 0), getDouble(f, 6, 0),
                getDouble(f, 8, 0), getDouble(f, 9, 0),
                getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0),
                getDouble(f, 17, 0), getDouble(f, 18, 0)) != null;
    }

    // ESAC5A: IBUS 'ESAC5A' ID Tr Ka Ta Vrmax Vrmin Ke Te Kf Tf1 Tf2 Tf3 E1 SE1 E2 SE2 [Spdmlt]
    private boolean procExcEsac5a(String busId, String genId, String[] f) {
        if (f.length < 18) return false;
        Esac5aData data = new Esac5aData();
        data.setTr(getDouble(f, 3, 0)); data.setKa(getDouble(f, 4, 0));
        data.setTa(getDouble(f, 5, 0)); data.setVrmax(getDouble(f, 6, 0));
        data.setVrmin(getDouble(f, 7, 0)); data.setKe(getDouble(f, 8, 0));
        data.setTe(getDouble(f, 9, 0)); data.setKf(getDouble(f, 10, 0));
        data.setTf1(getDouble(f, 11, 0)); data.setTf2(getDouble(f, 12, 0));
        data.setTf3(getDouble(f, 13, 0)); data.setE1(getDouble(f, 14, 0));
        data.setSe1(getDouble(f, 15, 0)); data.setE2(getDouble(f, 16, 0));
        data.setSe2(getDouble(f, 17, 0)); data.setSpdmlt(getDouble(f, 18, 0));
        return builder.addExcEsac5a(busId, genId, data) != null;
    }

    private boolean procExcEsst3a(String busId, String genId, String[] f) {
        if (f.length < 24) return false;
        return builder.addExcEsst3a(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0),
                getDouble(f, 11, 0), getDouble(f, 12, 0),
                getDouble(f, 13, 0), getDouble(f, 14, 0), getDouble(f, 15, 0),
                getDouble(f, 16, 0), getDouble(f, 17, 0), getDouble(f, 18, 0),
                getDouble(f, 19, 0), getDouble(f, 20, 0), getDouble(f, 21, 0),
                getDouble(f, 22, 0), getDouble(f, 23, 0)) != null;
    }

    private boolean procExcEsst4b(String busId, String genId, String[] f) {
        if (f.length < 20) return false;
        IEEE2005ST4BExciterData d = new IEEE2005ST4BExciterData();
        d.setTr(getDouble(f, 3, 0)); d.setKpr(getDouble(f, 4, 0));
        d.setKir(getDouble(f, 5, 0)); d.setVrmax(getDouble(f, 6, 0));
        d.setVrmin(getDouble(f, 7, 0)); d.setTa(getDouble(f, 8, 0));
        d.setKpm(getDouble(f, 9, 0)); d.setKim(getDouble(f, 10, 0));
        d.setVmmax(getDouble(f, 11, 0)); d.setVmmin(getDouble(f, 12, 0));
        d.setKg(getDouble(f, 13, 0)); d.setKp(getDouble(f, 14, 0));
        d.setKi(getDouble(f, 15, 0)); d.setVbmax(getDouble(f, 16, 0));
        d.setKc(getDouble(f, 17, 0)); d.setXl(getDouble(f, 18, 0));
        d.setAngKp(getDouble(f, 19, 0));
        if (f.length > 20) d.setVgmax(getDouble(f, 20, d.getVgmax()));
        return builder.addExcEsst4b(busId, genId, d) != null;
    }

    // SCRX: IBUS 'SCRX' ID Ta/Tb Tb K Te Efdmin Efdmax Cswitch Rc/Rfd
    private boolean procExcScrx(String busId, String genId, String[] f) {
        if (f.length < 11) return false;
        ScrxData data = new ScrxData();
        data.setTaOverTb(getDouble(f, 3, 0));
        data.setTb(getDouble(f, 4, 0));
        data.setK(getDouble(f, 5, 0));
        data.setTe(getDouble(f, 6, 0));
        data.setEfdmin(getDouble(f, 7, 0));
        data.setEfdmax(getDouble(f, 8, 0));
        data.setCswitch(getInt(f, 9, 0));
        data.setRcOverRfd(getDouble(f, 10, 0));
        return builder.addExcScrx(busId, genId, data) != null;
    }

    private static boolean isSupportedSt2cutMode(int mode) {
        return mode == 0 || mode == 1 || mode == 3 || mode == 4;
    }

    private record PendingSt2cut(String busId, String genId, String[] fields,
            PsseDyrRecord record) {}

    // IEEEST: IBUS 'IEEEST' ID MODE BUSR A1 A2 A3 A4 A5 A6
    //          T1 T2 T3 T4 T5 T6 KS LSMAX LSMIN VCU VCL
    private boolean procPssIeeest(String busId, String genId, String[] f) {
        if (f.length < 22) return false;
        int mode = getInt(f, 3, 0);
        int remoteBus = getInt(f, 4, 0);
        if ((mode < 1 || mode > 5 || mode == 2) || remoteBus != 0) {
            log.warn("IEEEST remote-bus or unsupported signal mode {} at bus {}", mode, busId);
            return false;
        }
        var machine = builder.getBaseDStabNetwork().getMachine(busId + "-mach" + genId);
        if (machine == null || machine.getExciter() == null) {
            log.warn("IEEEST at bus {} requires a loaded exciter", busId);
            return false;
        }
        IeeestData data = new IeeestData(mode, remoteBus,
                getDouble(f, 5, 0), getDouble(f, 6, 0),
                getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0),
                getDouble(f, 11, 0), getDouble(f, 12, 0),
                getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0),
                getDouble(f, 17, 0), getDouble(f, 18, 0),
                getDouble(f, 19, 0), getDouble(f, 20, 0),
                getDouble(f, 21, 0));
        new IeeestStabilizer(busId + "-ieeest" + genId, data, machine);
        return true;
    }

    private record PendingIeeest(String busId, String genId, String[] fields,
            PsseDyrRecord record) {}

    private record PendingRepca1(String busId, String genId, String[] fields,
            PsseDyrRecord record) {}

    // PSS2A: IBUS 'PSS2A' ID ICS1 REMBUS1 ICS2 REMBUS2 M N
    //         Tw1 Tw2 T6 Tw3 Tw4 T7 Ks2 Ks3 T8 T9 Ks1 T1 T2 T3 T4 VSTMAX VSTMIN
    private boolean procPss2a(String busId, String genId, String[] f) {
        return builder.addPss2a(busId, genId,
                getInt(f, 3, 0), getInt(f, 4, 0),
                getInt(f, 5, 0), getInt(f, 6, 0),
                getInt(f, 7, 0), getInt(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0),
                getDouble(f, 17, 0), getDouble(f, 18, 0), getDouble(f, 19, 0),
                getDouble(f, 20, 0), getDouble(f, 21, 0),
                getDouble(f, 22, 0), getDouble(f, 23, 0),
                getDouble(f, 24, 0), getDouble(f, 25, 0)) != null;
    }

    // PSS2B: IBUS 'PSS2B' ID ICS1 ICS2 M N Tw1 Tw2 T6 Tw3 Tw4 T7 Ks2 Ks3
    //         T8 T9 Ks1 T1 T2 T3 T4 T10 T11 VSI1MAX VSI1MIN VSI2MAX VSI2MIN
    //         VSTMAX VSTMIN A Ta Tb Ks4
    private boolean procPss2b(String busId, String genId, String[] f) {
        return builder.addPss2b(busId, genId,
                getInt(f, 3, 0), getInt(f, 4, 0), getInt(f, 5, 0), getInt(f, 6, 0),
                getDouble(f, 7, 0), getDouble(f, 8, 0), getDouble(f, 9, 0),
                getDouble(f, 10, 0), getDouble(f, 11, 0), getDouble(f, 12, 0),
                getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0), getDouble(f, 17, 0),
                getDouble(f, 18, 0), getDouble(f, 19, 0), getDouble(f, 20, 0),
                getDouble(f, 21, 0), getDouble(f, 22, 0), getDouble(f, 23, 0),
                getDouble(f, 24, 0), getDouble(f, 25, 0), getDouble(f, 26, 0),
                getDouble(f, 27, 0), getDouble(f, 28, 0), getDouble(f, 29, 0),
                getDouble(f, 30, 1), getDouble(f, 31, 0), getDouble(f, 32, 0),
                getDouble(f, 33, 1)) != null;
    }

    // PSS2C: IBUS 'PSS2C' ID ICS1 REMBUS1 ICS2 REMBUS2 M N
    //         Tw1 Tw2 T6 Tw3 Tw4 T7 Ks2 Ks3 T8 T9 Ks1 T1 T2 T3 T4 T10 T11
    //         VSI1MAX VSI1MIN VSI2MAX VSI2MIN VSTMAX VSTMIN T12 T13
    //         PSSActivation PSSDeactivation Xcomp Tcomp
    private boolean procPss2c(String busId, String genId, String[] f) {
        return builder.addPss2c(busId, genId,
                getInt(f, 3, 0), getInt(f, 4, 0),
                getInt(f, 5, 0), getInt(f, 6, 0),
                getInt(f, 7, 0), getInt(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0),
                getDouble(f, 17, 0), getDouble(f, 18, 0), getDouble(f, 19, 0),
                getDouble(f, 20, 0), getDouble(f, 21, 0), getDouble(f, 22, 0),
                getDouble(f, 23, 0), getDouble(f, 24, 0), getDouble(f, 25, 0),
                getDouble(f, 26, 0), getDouble(f, 27, 0), getDouble(f, 28, 0),
                getDouble(f, 29, 0), getDouble(f, 30, 0), getDouble(f, 31, 0),
                getDouble(f, 32, 0), getDouble(f, 33, 0),
                getDouble(f, 34, 0), getDouble(f, 35, 0),
                0.0, getDouble(f, 36, 0), getDouble(f, 37, 0)) != null;
    }

    // PSS3B: IBUS 'PSS3B' ID ICS1 ICS2 Ks1 T1 Tw1 Ks2 T2 Tw2 Tw3
    //         A1 A2 A3 A4 A5 A6 A7 A8 VSTMAX VSTMIN
    private boolean procPss3b(String busId, String genId, String[] f) {
        return builder.addPss3b(busId, genId,
                getInt(f, 3, 0), getInt(f, 4, 0),
                getDouble(f, 5, 0), getDouble(f, 6, 0), getDouble(f, 7, 0),
                getDouble(f, 8, 0), getDouble(f, 9, 0), getDouble(f, 10, 0),
                getDouble(f, 11, 0), getDouble(f, 12, 0), getDouble(f, 13, 0),
                getDouble(f, 14, 0), getDouble(f, 15, 0), getDouble(f, 16, 0),
                getDouble(f, 17, 0), getDouble(f, 18, 0), getDouble(f, 19, 0),
                getDouble(f, 20, 0), getDouble(f, 21, 0)) != null;
    }

    // PSS4B: IBUS 'PSS4B' ID followed by the 75 IEEE/PSS/E parameters.
    private boolean procPss4b(String busId, String genId, String[] f) {
        if (f.length < 78) return false;
        double[] parameters = new double[75];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getDouble(f, i + 3, 0.0);
        }
        return builder.addPss4b(busId, genId, parameters) != null;
    }

    // PSS3C: IBUS 'PSS3C' ID followed by the 24 IEEE/PSS/E parameters.
    private boolean procPss3c(String busId, String genId, String[] f) {
        if (f.length < 27) return false;
        double[] parameters = new double[24];
        parameters[0] = getInt(f, 3, 0);
        parameters[1] = getInt(f, 4, 0);
        for (int i = 2; i < parameters.length; i++) {
            parameters[i] = getDouble(f, i + 3, 0.0);
        }
        return builder.addPss3c(busId, genId, parameters) != null;
    }

    // PowerWorld DYR extension: PSS4C has no native PSS/E entry in the WECC
    // cross-software table. Its published parameter order contains 94 values.
    private boolean procPss4c(String busId, String genId, String[] f) {
        if (f.length < 97) return false;
        double[] parameters = new double[94];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getDouble(f, i + 3, 0.0);
        }
        return builder.addPss4c(busId, genId, parameters) != null;
    }

    // PowerWorld DYR extension: WECC lists no native PSS/E PSS5C name.
    private boolean procPss5c(String busId, String genId, String[] f) {
        if (f.length < 24) return false;
        double[] parameters = new double[21];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getDouble(f, i + 3, 0.0);
        }
        return builder.addPss5c(busId, genId, parameters) != null;
    }

    // PSS6C: four selectors and 30 constants; PowerWorld adds Tpgfilt before Xcomp.
    private boolean procPss6c(String busId, String genId, String[] f) {
        if (f.length < 37) return false;
        int count = f.length >= 38 ? 35 : 34;
        double[] parameters = new double[count];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getDouble(f, i + 3, 0.0);
        }
        return builder.addPss6c(busId, genId, parameters) != null;
    }

    // PSS7C: four selectors and 34 constants; PowerWorld adds Tpgfilt before Xcomp.
    private boolean procPss7c(String busId, String genId, String[] f) {
        if (f.length < 41) return false;
        int count = f.length >= 42 ? 39 : 38;
        double[] parameters = new double[count];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getDouble(f, i + 3, 0.0);
        }
        return builder.addPss7c(busId, genId, parameters) != null;
    }

    // PSS1A: IBUS 'PSS1A' ID ICS A1 A2 T1 T2 T3 T4 T5 T6 KS LSMAX LSMIN VCU VCL
    private boolean procPss1a(String busId, String genId, String[] f) {
        if (f.length < 17) return false;
        return builder.addPss1a(busId, genId,
                getInt(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0),
                getDouble(f, 8, 0), getDouble(f, 9, 0),
                getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0),
                getDouble(f, 14, 0), getDouble(f, 15, 0), getDouble(f, 16, 0)) != null;
    }

    // IEEEG1: IBUS 'IEEEG1' ID JBUS M K T1 T2 T3 Uo Uc PMAX PMIN T4 K1 K2 T5 K3 K4 T6 K5 K6 T7 K7 K8
    //         idx:  0    1    2   3  4  5  6  7  8  9 10  11   12  13 14 15 16 17 18 19 20 21 22 23 24
    private boolean procGovIeeeg1(String busId, String genId, String[] f) throws InterpssException {
        double k = getDouble(f, 5, 0);
        double t1 = getDouble(f, 6, 0);
        double t2 = getDouble(f, 7, 0);
        double t3 = getDouble(f, 8, 0);
        double uo = getDouble(f, 9, 0);
        double uc = getDouble(f, 10, 0);
        double pmax = getDouble(f, 11, 0);
        double pmin = getDouble(f, 12, 0);
        double t4 = getDouble(f, 13, 0);   // Tch
        double k1 = getDouble(f, 14, 0);   // Fvhp
        double k2 = getDouble(f, 15, 0);
        double t5 = getDouble(f, 16, 0);   // Trh1
        double k3 = getDouble(f, 17, 0);   // Fhp
        double k4 = getDouble(f, 18, 0);
        double t6 = getDouble(f, 19, 0);   // Trh2
        double k5 = getDouble(f, 20, 0);   // Fip
        double k6 = getDouble(f, 21, 0);
        double t7 = getDouble(f, 22, 0);   // Tco
        double k7 = getDouble(f, 23, 0);   // Flp
        double k8 = getDouble(f, 24, 0);

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
        return builder.addGovTgov1(busId, genId, r, t1, vmax, vmin, t2, t3, dt) != null;
    }

    // TGOV1D: IBUS 'TGOV1D' ID R T1 VMAX VMIN T2 T3 Dt dbH dbL Trate
    private boolean procGovTgov1d(String busId, String genId, String[] f) {
        if (f.length < 13) return false;
        return builder.addGovTgov1d(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0),
                getDouble(f, 5, 0), getDouble(f, 6, 0),
                getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0),
                getDouble(f, 11, 0), getDouble(f, 12, 0)) != null;
    }

    // GAST: IBUS 'GAST' ID R T1 T2 T3 AT KT VMAX VMIN Dturb
    //       idx:  0   1  2  3 4  5  6  7  8   9   10   11
    private boolean procGovGast(String busId, String genId, String[] f) throws InterpssException {
		if (f.length < 12) return false;
        double r = getDouble(f, 3, 0);
        double t1 = getDouble(f, 4, 0);
        double t2 = getDouble(f, 5, 0);
        double t3 = getDouble(f, 6, 0);
        double at = getDouble(f, 7, 0);
        double kt = getDouble(f, 8, 0);
        double vmax = getDouble(f, 9, 0);
        double vmin = getDouble(f, 10, 0);
        double dturb = getDouble(f, 11, 0);
		return builder.addGovGast(busId, genId, r, t1, t2, t3,
		        at, kt, vmax, vmin, dturb) != null;
	}

    // GASTD: IBUS 'GASTD' ID R T1 T2 T3 AT KT VMAX VMIN Dturb dbH dbL Trate
    private boolean procGovGastd(String busId, String genId, String[] f) throws InterpssException {
        if (f.length < 15) return false;
        return builder.addGovGastd(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0)) != null;
    }

    // IEEEG1D: K T1 T2 T3 Uo Uc Pmax Pmin T4 K1 K2 T5 K3 K4 T6 K5 K6 T7 K7 K8 dbH dbL Trate
    private boolean procGovIeeeg1d(String busId, String genId, String[] f) throws InterpssException {
        if (f.length < 26) return false;
        return builder.addGovIeeeg1d(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0), getDouble(f, 17, 0),
                getDouble(f, 18, 0), getDouble(f, 19, 0), getDouble(f, 20, 0),
                getDouble(f, 21, 0), getDouble(f, 22, 0), getDouble(f, 23, 0),
                getDouble(f, 24, 0), getDouble(f, 25, 0)) != null;
    }

    // GGOV1 PSS/E record order.  Trate is field 30 in the data list even though
    // PowerWorld's model parameter table presents it first.
    private boolean procGovGgov1(String busId, String genId, String[] f, boolean deadbandVariant) {
        if (f.length < (deadbandVariant ? 40 : 38)) return false;
        PsseGgov1GovernorData d = new PsseGgov1GovernorData();
        d.setRselect(getInt(f, 3, 0)); d.setFlag(getInt(f, 4, 0));
        d.setR(getDouble(f, 5, 0)); d.setTpelec(getDouble(f, 6, 0));
        d.setMaxerr(getDouble(f, 7, 0)); d.setMinerr(getDouble(f, 8, 0));
        d.setKpgov(getDouble(f, 9, 0)); d.setKigov(getDouble(f, 10, 0));
        d.setKdgov(getDouble(f, 11, 0)); d.setTdgov(getDouble(f, 12, 0));
        d.setVmax(getDouble(f, 13, 0)); d.setVmin(getDouble(f, 14, 0));
        d.setTact(getDouble(f, 15, 0)); d.setKturb(getDouble(f, 16, 0));
        d.setWfnl(getDouble(f, 17, 0)); d.setTb(getDouble(f, 18, 0));
        d.setTc(getDouble(f, 19, 0)); d.setTeng(getDouble(f, 20, 0));
        d.setTfload(getDouble(f, 21, 0)); d.setKpload(getDouble(f, 22, 0));
        d.setKiload(getDouble(f, 23, 0)); d.setLdref(getDouble(f, 24, 0));
        d.setDm(getDouble(f, 25, 0)); d.setRopen(getDouble(f, 26, 0));
        d.setRclose(getDouble(f, 27, 0)); d.setKimw(getDouble(f, 28, 0));
        d.setAset(getDouble(f, 29, 0)); d.setKa(getDouble(f, 30, 0));
        d.setTa(getDouble(f, 31, 0)); d.setTrate(getDouble(f, 32, 0));
        d.setDb(getDouble(f, 33, 0)); d.setTsa(getDouble(f, 34, 0));
        d.setTsb(getDouble(f, 35, 0)); d.setRup(getDouble(f, 36, 0));
        d.setRdown(getDouble(f, 37, 0));
        if (deadbandVariant) {
            d.setDbH(getDouble(f, 38, 0));
            d.setDbL(getDouble(f, 39, 0));
            return builder.addGovGgov1d(busId, genId, d) != null;
        }
        return builder.addGovGgov1(busId, genId, d) != null;
    }

    private boolean procGovHygov(String busId, String genId, String[] f, boolean deadbandVariant) {
        if (f.length < (deadbandVariant ? 18 : 15)) return false;
        PsseHygovGovernorData d = new PsseHygovGovernorData();
        d.setR(getDouble(f, 3, 0)); d.setRtemp(getDouble(f, 4, 0));
        d.setTr(getDouble(f, 5, 0)); d.setTf(getDouble(f, 6, 0));
        d.setTg(getDouble(f, 7, 0)); d.setVelm(getDouble(f, 8, 0));
        d.setGmax(getDouble(f, 9, 0)); d.setGmin(getDouble(f, 10, 0));
        d.setTw(getDouble(f, 11, 0)); d.setAt(getDouble(f, 12, 0));
        d.setDturb(getDouble(f, 13, 0)); d.setQnl(getDouble(f, 14, 0));
        if (deadbandVariant) {
            d.setDbH(getDouble(f, 15, 0)); d.setDbL(getDouble(f, 16, 0));
            d.setTrate(getDouble(f, 17, 0));
            return builder.addGovHygovd(busId, genId, d) != null;
        }
        return builder.addGovHygov(busId, genId, d) != null;
    }

    // GENQEC (PSLF/PowerDynData order):
    // IBUS 'GENQEC' ID T'do T''do T'qo T''qo H D Xd Xq X'd X'q X''d X''q
    //                    Xl S(1.0) S(1.2) Ra Rcomp Xcomp Kw SatFunc
    private boolean procGenqec(String busId, String genId, String[] f) throws InterpssException {
        if (f.length < 23) {
            log.warn("Incomplete GENQEC record at bus {}: expected 23 fields, found {}", busId, f.length);
            return false;
        }
        GenqecData data = new GenqecData(
                getDouble(f, 7, 0.0), getDouble(f, 8, 0.0), getDouble(f, 18, 0.0),
                getDouble(f, 9, 0.0), getDouble(f, 10, 0.0),
                getDouble(f, 11, 0.0), getDouble(f, 12, 0.0),
                getDouble(f, 13, 0.0), getDouble(f, 14, 0.0), getDouble(f, 15, 0.0),
                getDouble(f, 3, 0.0), getDouble(f, 5, 0.0),
                getDouble(f, 4, 0.0), getDouble(f, 6, 0.0),
                getDouble(f, 16, 0.0), getDouble(f, 17, 0.0),
                getDouble(f, 19, 0.0), getDouble(f, 20, 0.0),
                0.0, getDouble(f, 21, 0.0), (int) getDouble(f, 22, 0.0));
        double[] rating = getGenRating(busId, genId);
        builder.addGenqec(busId, genId, rating[0], rating[1], data);
        return true;
    }

    // GENQEJ has the GENQEC order, with Kis replacing Kw. GENQEJU is
    // canonicalized by the catalog before dispatch.
    private boolean procGenqej(String busId, String genId, String[] f) throws InterpssException {
        if (f.length < 23) {
            log.warn("Incomplete GENQEJ record at bus {}: expected 23 fields, found {}", busId, f.length);
            return false;
        }
        GenqejData data = new GenqejData(
                getDouble(f, 7, 0.0), getDouble(f, 8, 0.0), getDouble(f, 18, 0.0),
                getDouble(f, 9, 0.0), getDouble(f, 10, 0.0),
                getDouble(f, 11, 0.0), getDouble(f, 12, 0.0),
                getDouble(f, 13, 0.0), getDouble(f, 14, 0.0), getDouble(f, 15, 0.0),
                getDouble(f, 3, 0.0), getDouble(f, 5, 0.0),
                getDouble(f, 4, 0.0), getDouble(f, 6, 0.0),
                getDouble(f, 16, 0.0), getDouble(f, 17, 0.0),
                getDouble(f, 19, 0.0), getDouble(f, 20, 0.0),
                0.0, getDouble(f, 21, 0.0), (int) getDouble(f, 22, 0.0));
        double[] rating = getGenRating(busId, genId);
        builder.addGenqej(busId, genId, rating[0], rating[1], data);
        return true;
    }

    // REGCA1: IBUS MODEL ID LVPLSW Tg Rrpwr Brkpt Zerox Lvpl1 Volim
    //         Lvpnt1 Lvpnt0 Iolim Tfltr Khv Iqrmax Iqrmin Accel
    private boolean procRegca1(String busId, String genId, String[] f) {
        if (f.length < 18) return false;
        Regca1Data data = new Regca1Data(
                getInt(f, 3, 1), getDouble(f, 4, 0.02), getDouble(f, 5, 10.0),
                getDouble(f, 6, 0.9), getDouble(f, 7, 0.4), getDouble(f, 8, 1.22),
                getDouble(f, 9, 1.2), getDouble(f, 10, 0.9), getDouble(f, 11, 0.5),
                getDouble(f, 12, -1.3), getDouble(f, 13, 0.02), getDouble(f, 14, 0.0),
                getDouble(f, 15, 100.0), getDouble(f, 16, -100.0), getDouble(f, 17, 0.7));
        return builder.addRegca1(busId, genId, data) != null;
    }

    // PSS/E REGFMA1: IBUS MODEL ID Vflag TPf TQf TVf Imax Emax Emin
    // Pmax Pmin Qmax Qmin Mp Mq Kppmax Kipmax Kpqmax Kiqmax Kpv Kiv
    private boolean procRegfma1(String busId, String genId, String[] f) {
        if (f.length < 22) return false;
        Regfma1Data data = new Regfma1Data(
                getInt(f, 3, 0), getDouble(f, 4, 0.02), getDouble(f, 5, 0.02),
                getDouble(f, 6, 0.02), getDouble(f, 7, 0.0),
                getDouble(f, 8, 1.2), getDouble(f, 9, 0.0),
                getDouble(f, 10, 1.0), getDouble(f, 11, 0.0),
                getDouble(f, 12, 1.0), getDouble(f, 13, -1.0),
                getDouble(f, 14, 0.01), getDouble(f, 15, 0.05),
                getDouble(f, 16, 0.01), getDouble(f, 17, 0.1),
                getDouble(f, 18, 3.0), getDouble(f, 19, 20.0),
                getDouble(f, 20, 0.0), getDouble(f, 21, 6.0));
        return builder.addRegfma1(busId, genId, data) != null;
    }

    // REECB1: IBUS MODEL ID BUSR PFFLAG VFLAG QFLAG PQFLAG followed by
    // voltage, reactive-control, active-control, and current-limit parameters.
    private boolean procReecb1(String busId, String genId, String[] f) {
        if (f.length < 33) return false;
        Reecb1Data data = new Reecb1Data(
                getInt(f, 3, 0), getInt(f, 4, 0), getInt(f, 5, 0), getInt(f, 6, 0), getInt(f, 7, 0),
                getDouble(f, 8, -99), getDouble(f, 9, 99), getDouble(f, 10, 0.02),
                getDouble(f, 11, 0), getDouble(f, 12, 0), getDouble(f, 13, 0),
                getDouble(f, 14, 1.1), getDouble(f, 15, -1.1), getDouble(f, 16, 0),
                getDouble(f, 17, 0.02), getDouble(f, 18, 99), getDouble(f, 19, -99),
                getDouble(f, 20, 1.1), getDouble(f, 21, -1.1), getDouble(f, 22, 0),
                getDouble(f, 23, 0.01), getDouble(f, 24, 10), getDouble(f, 25, 60),
                getDouble(f, 26, 0.02), getDouble(f, 27, 99), getDouble(f, 28, -99),
                getDouble(f, 29, 1), getDouble(f, 30, 0), getDouble(f, 31, 1.1),
                getDouble(f, 32, 0.02));
        return builder.addReecb1(busId, genId, data) != null;
    }

    // REECA1: IBUS MODEL ID BUSR PFFLAG VFLAG QFLAG PFLAG PQFLAG followed by
    // voltage-dip, reactive-control, active-control, current-limit, and VDL data.
    private boolean procReeca1(String busId, String genId, String[] f) {
        if (f.length < 54) return false;
        Reeca1Data data = new Reeca1Data(
                getInt(f, 3, 0), getInt(f, 4, 0), getInt(f, 5, 0),
                getInt(f, 6, 0), getInt(f, 7, 0), getInt(f, 8, 0),
                getDouble(f, 9, .8), getDouble(f, 10, 1.2), getDouble(f, 11, .02),
                getDouble(f, 12, -.02), getDouble(f, 13, .02), getDouble(f, 14, 0),
                getDouble(f, 15, 999), getDouble(f, 16, -999), getDouble(f, 17, 0),
                getDouble(f, 18, 0), getDouble(f, 19, 0), getDouble(f, 20, 0),
                getDouble(f, 21, .02), getDouble(f, 22, 999), getDouble(f, 23, -999),
                getDouble(f, 24, 999), getDouble(f, 25, -999),
                getDouble(f, 26, 0), getDouble(f, 27, 0),
                getDouble(f, 28, 0), getDouble(f, 29, 0), getDouble(f, 30, 0),
                getDouble(f, 31, .02), getDouble(f, 32, 999), getDouble(f, 33, -999),
                getDouble(f, 34, 1), getDouble(f, 35, 0),
                getDouble(f, 36, 1.1), getDouble(f, 37, .02),
                getDouble(f, 38, 0), getDouble(f, 39, 0),
                getDouble(f, 40, 0), getDouble(f, 41, 0),
                getDouble(f, 42, 0), getDouble(f, 43, 0),
                getDouble(f, 44, 0), getDouble(f, 45, 0),
                getDouble(f, 46, 0), getDouble(f, 47, 0),
                getDouble(f, 48, 0), getDouble(f, 49, 0),
                getDouble(f, 50, 0), getDouble(f, 51, 0),
                getDouble(f, 52, 0), getDouble(f, 53, 0));
        return builder.addReeca1(busId, genId, data) != null;
    }

    private boolean procWtara1(String busId, String genId, String[] f) {
        if (f.length < 5) return false;
        return builder.addWtara1(busId, genId,
                new Wtara1Data(getDouble(f, 3, 0), getDouble(f, 4, 0))) != null;
    }

    private boolean procWtpta1(String busId, String genId, String[] f) {
        if (f.length < 13) return false;
        Wtpta1Data data = new Wtpta1Data(
                getDouble(f, 3, 0.1), getDouble(f, 4, 0),
                getDouble(f, 5, 0.1), getDouble(f, 6, 0), getDouble(f, 7, 0),
                getDouble(f, 8, .3), getDouble(f, 9, 30), getDouble(f, 10, 0),
                getDouble(f, 11, 5), getDouble(f, 12, -5));
        return builder.addWtpta1(busId, genId, data) != null;
    }

    // PSS/E order starts with TFLAG, followed by Kpp, Kip, Tp, and Twref.
    private boolean procWttqa1(String busId, String genId, String[] f) {
        if (f.length < 19) return false;
        Wttqa1Data data = new Wttqa1Data(
                getInt(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, .1),
                getDouble(f, 6, .05), getDouble(f, 7, 30),
                getDouble(f, 8, 1.2), getDouble(f, 9, 0),
                getDouble(f, 10, .2), getDouble(f, 11, .58),
                getDouble(f, 12, .4), getDouble(f, 13, .72),
                getDouble(f, 14, .6), getDouble(f, 15, .86),
                getDouble(f, 16, .8), getDouble(f, 17, 1), getDouble(f, 18, 0));
        return builder.addWttqa1(busId, genId, data) != null;
    }

    // REPCA1: IBUS MODEL ID IBRANCH JBUS KBus ID VCFlag RefFlag FFlag ...
    private boolean procRepca1(String busId, String genId, String[] f) {
        if (f.length < 37) return false;
        Repca1Data data = new Repca1Data(
                getInt(f, 3, 0), getInt(f, 4, 0), getInt(f, 5, 0), trimQuote(f[6]),
                getInt(f, 7, 0), getInt(f, 8, 0), getInt(f, 9, 0),
                getDouble(f, 10, 0.02), getDouble(f, 11, 1), getDouble(f, 12, 0.1),
                getDouble(f, 13, 0), getDouble(f, 14, 0.05), getDouble(f, 15, 0),
                getDouble(f, 16, 0), getDouble(f, 17, 0), getDouble(f, 18, 0),
                getDouble(f, 19, 99), getDouble(f, 20, -99), getDouble(f, 21, -0.1),
                getDouble(f, 22, 0.1), getDouble(f, 23, 1), getDouble(f, 24, -1),
                getDouble(f, 25, 1), getDouble(f, 26, 0.05), getDouble(f, 27, 0.25),
                getDouble(f, 28, -1), getDouble(f, 29, 1), getDouble(f, 30, 99),
                getDouble(f, 31, -99), getDouble(f, 32, 1), getDouble(f, 33, 0),
                getDouble(f, 34, 0.1), getDouble(f, 35, 0), getDouble(f, 36, 0),
                getInt(f, 37, 0));
        return builder.addRepca1(busId, genId, data) != null;
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

    private boolean procGovIeesgod(String busId, String genId, String[] f) throws InterpssException {
        if (f.length < 17) return false;
        return builder.addGovIeesgod(busId, genId,
                getDouble(f, 3, 0), getDouble(f, 4, 0), getDouble(f, 5, 0),
                getDouble(f, 6, 0), getDouble(f, 7, 0), getDouble(f, 8, 0),
                getDouble(f, 9, 0), getDouble(f, 10, 0), getDouble(f, 11, 0),
                getDouble(f, 12, 0), getDouble(f, 13, 0), getDouble(f, 14, 0),
                getDouble(f, 15, 0), getDouble(f, 16, 0)) != null;
    }

    // ==================== Utility Methods ====================

    @SuppressWarnings("unchecked")
    private double[] getGenRating(String busId, String genId) {
        BaseDStabNetwork<?, ?> net = builder.getBaseDStabNetwork();
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
            throw new IllegalArgumentException("Invalid floating-point DYR field " + (idx + 1)
                    + ": '" + fields[idx] + "'", e);
        }
    }

    private int getInt(String[] fields, int idx, int defaultVal) {
        if (idx >= fields.length) return defaultVal;
        try {
            return Integer.parseInt(trimQuote(fields[idx]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer DYR field " + (idx + 1)
                    + ": '" + fields[idx] + "'", e);
        }
    }

    private DynamicModelImportStatus rejectedStatus(String type) {
        return DynamicModelCatalog.find(type)
                .filter(model -> model.supportStatus() != DynamicModelSupportStatus.UNSUPPORTED)
                .map(model -> DynamicModelImportStatus.REJECTED)
                .orElse(DynamicModelImportStatus.UNSUPPORTED);
    }

    private DynamicModelImportStatus attachedStatus(PsseDyrRecord record) {
        return DynamicModelCatalog.find(record.canonicalModelName())
                .filter(model -> model.supportStatus() == DynamicModelSupportStatus.LOADABLE)
                .map(model -> DynamicModelImportStatus.ATTACHED)
                .orElse(DynamicModelImportStatus.FALLBACK);
    }

    private String attachedMessage(PsseDyrRecord record) {
        return DynamicModelCatalog.find(record.canonicalModelName())
                .map(model -> model.supportStatus() == DynamicModelSupportStatus.PARTIAL
                        ? "compatibility implementation is partial and is not accepted by strict import"
                        : "cataloged implementation is not accepted by strict import")
                .orElse("uncataloged compatibility implementation is not accepted by strict import");
    }

    private String rejectionMessage(String type, PsseDyrRecord record) {
        return DynamicModelCatalog.find(type)
                .map(model -> !hasExpectedParameterCount(record)
                        ? "expected " + model.recordSchema().expectedCountsDescription()
                                + " parameters but found "
                                + record.parameterCount()
                        : model.supportStatus() == DynamicModelSupportStatus.LOADABLE
                                ? "model could not be attached to its target device"
                                : "model support status is " + model.supportStatus())
                .orElse("dynamic model is not implemented by the direct parser");
    }
}
