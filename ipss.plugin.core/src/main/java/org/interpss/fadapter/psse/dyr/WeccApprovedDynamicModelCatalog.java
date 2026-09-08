package org.interpss.fadapter.psse.dyr;

import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.SYNCHRONOUS_MACHINE;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.STABILIZER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.GOVERNOR;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.EXCITER;
import static org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus.APPROVED;
import static org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus.NEVER_APPROVED;
import static org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus.RETIRED;
import static org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus.TRANSITIONAL;
import static org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus.UNAPPROVED;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Versioned transcription of the generator rows in the WECC May 2026 list. */
public final class WeccApprovedDynamicModelCatalog {
    public static final String VERSION = "May 2026";
    public static final URI SOURCE = URI.create(
            "https://www.wecc.org/sites/default/files/documents/progress_report/2026/"
                    + "Approved%20Dynamic%20Models%20May%202026.pdf");

    /** Excitation-system rows through TEXS; OEL/UEL rows are a separate future wave. */
    private static final List<WeccModelApproval> EXCITERS = List.of(
            exciter("EXAC1", "exac1", "EXAC1", "EXAC1", APPROVED, "2006-08-11", "EXAC1"),
            exciter("ESAC1A", "esac1a", "ESAC1A", "ESAC1A", APPROVED, "2011-01-21", "ESAC1A"),
            exciter("EXAC1A", "exac1a", "EXAC1A", "EXAC1A", APPROVED, "2006-08-11", "EXAC1A"),
            exciter("EXAC1M", "exac1m", "ESURRY", "EXAC1M", APPROVED, "2021-12-02", "ESURRY"),
            exciter("ESAC1C", "esac1c", "AC1C", "AC1C", APPROVED, "2020-04-22", "AC1C"),
            exciter("EXAC2", "exac2", "EXAC2", "EXAC2", APPROVED, "2006-08-11", "EXAC2"),
            exciter("ESAC2A", "esac2a", "ESAC2A", "ESAC2A", APPROVED, "2011-01-21", ""),
            exciter("EXAC3", "exac3", "EXAC3", "EXAC3", NEVER_APPROVED, "", ""),
            exciter("ESAC2C", "esac2c", "AC2C", "AC2C", APPROVED, "2020-04-22", "AC2C"),
            exciter("EXAC3A", "exac3a", "ESAC3A", "EXAC3A", APPROVED, "2006-08-11", ""),
            exciter("ESAC3A", "esac3a", "ESAC3A", "ESAC3A", APPROVED, "2011-01-21", ""),
            exciter("ESAC3C", "esac3c", "AC3C", "AC3C", APPROVED, "2020-04-22", "AC3C"),
            exciter("EXAC4", "exac4", "EXAC4", "EXAC4", APPROVED, "2006-08-11", "EXAC4"),
            exciter("ESAC4A", "esac4a", "ESAC4A", "ESAC4A", APPROVED, "2011-01-21", "ESAC4A"),
            exciter("ESAC4C", "esac4c", "AC4C", "AC4C", APPROVED, "2020-04-22", "AC4C"),
            exciter("ESAC5A", "esac5a", "ESAC5A", "ESAC5A", APPROVED, "2011-01-21", "ESAC5A"),
            exciter("ESAC5C", "esac5c", "AC5C", "AC5C", APPROVED, "2020-04-22", "AC5C"),
            exciter("EXAC6A", "exac6a", "ESAC6A", "EXAC6A", NEVER_APPROVED, "", ""),
            exciter("ESAC6A", "esac6a", "ESAC6A", "ESAC6A", APPROVED, "2011-01-21", "ESAC6A"),
            exciter("ESAC6C", "esac6c", "AC6C", "AC6C", APPROVED, "2020-04-22", "AC6C"),
            exciter("ESAC7B", "esac7b", "AC7B", "ESAC7B and AC7B", APPROVED, "2011-01-21", "AC7B"),
            exciter("ESAC7C", "esac7c", "AC7C", "AC7C", APPROVED, "2020-04-22", "AC7C"),
            exciter("EXAC8B", "exac8b", "ESAC8B", "EXAC8B", APPROVED, "2006-08-11", ""),
            exciter("ESAC8B", "esac8b", "AC8B", "ESAC8B_GE and AC8B", APPROVED, "2011-01-21", "AC8B"),
            exciter("ESAC8C", "esac8c", "AC8C", "AC8C", APPROVED, "2020-04-22", "AC8C"),
            exciter("ESAC9C", "esac9c", "AC9C", "AC9C", APPROVED, "2020-04-22", "AC9C"),
            exciter("ESAC10C", "esac10c", "", "AC10C", APPROVED, "2020-04-22", ""),
            exciter("ESAC11C", "esac11c", "AC11C", "AC11C", APPROVED, "2020-04-22", "AC11C"),
            exciter("EXBBC", "exbbc", "BBSEX1", "EXBBC and BBSEX1", APPROVED, "2006-08-11", "BBSEX1"),
            exciter("EXDC1", "exdc1", "IEEEX1", "EXDC1 and IEEEX1", APPROVED, "2006-08-11", "IEEEX1"),
            exciter("ESDC1A", "esdc1a", "ESDC1A", "ESDC1A", APPROVED, "2011-01-21", "ESDC1A"),
            exciter("ESDC1C", "esdc1c", "DC1C", "DC1C", APPROVED, "2020-04-22", "DC1C"),
            exciter("EXDC2", "exdc2", "EXDC2", "EXDC2_GE and EXDC2_PTI", APPROVED, "2006-08-11", "EXDC2"),
            exciter("EXDC2A", "exdc2a", "EXDC2", "EXDC2A and EXDC2_PTI", APPROVED, "2006-08-11", "EXDC2A"),
            exciter("ESDC2A", "esdc2a", "ESDC2A", "ESDC2A", APPROVED, "2011-01-21", "ESDC2A"),
            exciter("ESDC2C", "esdc2c", "DC2C", "DC2C", APPROVED, "2020-04-22", "DC2C"),
            exciter("EXDC4", "exdc4", "IEEET4", "EXDC4 and IEEET4", APPROVED, "2006-08-11", "IEEET4"),
            exciter("ESDC3A", "esdc3a", "DC3A", "ESDC3A and DC3A", APPROVED, "2011-01-21", "DC3A"),
            exciter("ESDC4B", "esdc4b", "DC4B", "ESDC4B", APPROVED, "2011-01-21", "DC4B"),
            exciter("ESDC4C", "esdc4c", "DC4C", "DC4C", APPROVED, "2020-04-22", "DC4C"),
            exciter("EXELI", "exeli", "EXELI", "EXELI", APPROVED, "2006-08-11", "EXELI"),
            exciter("EXST1", "exst1", "EXST1", "EXST1_GE and EXST1_PTI", APPROVED, "2006-08-11", "EXST1"),
            exciter("ESST1A", "esst1a", "ESST1A", "ESST1A and ESST1A_GE", APPROVED, "2011-01-21", "ESST1A"),
            exciter("ESST1C", "esst1c", "ST1C", "ST1C", APPROVED, "2020-04-22", "ST1C"),
            exciter("EXST2", "exst2", "EXST2", "EXST2", APPROVED, "2006-08-11", "EXST2"),
            exciter("EXST2A", "exst2a", "ESST2A", "EXST2A", APPROVED, "2006-08-11", ""),
            exciter("ESST2A", "esst2a", "ESST2A", "ESST2A", APPROVED, "2011-01-21", "ESST2A"),
            exciter("ESST2C", "esst2c", "ST2C", "ST2C", APPROVED, "2020-04-22", "ST2C"),
            exciter("EXST3", "exst3", "EXST3", "EXST3", APPROVED, "2006-08-11", ""),
            exciter("EXST3A", "exst3a", "ESST3A", "EXST3A", APPROVED, "2006-08-11", ""),
            exciter("ESST3A", "esst3a", "ESST3A", "ESST3A", APPROVED, "2011-01-21", "ESST3A"),
            exciter("ESST3C", "esst3c", "ST3C", "ST3C", APPROVED, "2020-04-22", "ST3C"),
            exciter("EXST4B", "exst4b", "ESST4B", "EXST4B", APPROVED, "2006-08-11", ""),
            exciter("ESST4B", "esst4b", "ESST4B", "ESST4B", APPROVED, "2011-01-21", "ESST4B"),
            exciter("ESST4C", "esst4c", "ST4C", "ST4C", APPROVED, "2020-04-22", "ST4C"),
            exciter("ESST5B", "esst5b", "ST5B", "ESST5B and ST5B", APPROVED, "2011-01-21", "ST5B"),
            exciter("ESST5C", "esst5c", "ST5C", "ST5C", APPROVED, "2020-04-22", "ST5C"),
            exciter("ESST6B", "esst6b", "ST6B", "ESST6B and ST6B", APPROVED, "2011-01-21", "ST6B"),
            exciter("ESST6C", "esst6c", "ST6C", "ST6C", APPROVED, "2020-04-22", "ST6C"),
            exciter("ESST7B", "esst7b", "ST7B", "ESST7B and ST7B", APPROVED, "2011-01-21", "ST7B"),
            exciter("ESST7C", "esst7c", "ST7C", "ST7C", APPROVED, "2020-04-22", "ST7C"),
            exciter("ESST8C", "esst8c", "ST8C", "ST8C", APPROVED, "2020-04-22", "ST8C"),
            exciter("ESST9C", "esst9c", "ST9C", "ST9C", APPROVED, "2020-04-22", "ST9C"),
            exciter("ST10C", "ST10C", "ST10C", "ST10C", APPROVED, "2020-04-22", "ST10C"),
            exciter("IEEET1", "ieeet1", "IEEET1", "IEEET1", APPROVED, "2006-08-11", "IEEET1"),
            exciter("MEXS", "mexs", "Not used", "MEXS", NEVER_APPROVED, "", ""),
            exciter("PFQRG", "pfqrg", "Not used", "PFQRG", NEVER_APPROVED, "", ""),
            exciter("REXS", "rexs", "REXSYS", "REXS", APPROVED, "2006-08-11", "REXSYS"),
            exciter("SCRX", "scrx", "SCRX", "SCRX", APPROVED, "2006-08-11", "SCRX"),
            exciter("SEXS", "sexs", "SEXS_GE and SEXS_PTI", "SEXS", NEVER_APPROVED, "", ""),
            exciter("TEXS", "texs", "Not converted", "TEXS", NEVER_APPROVED, "", ""));

    private static final List<WeccModelApproval> GENERATORS = List.of(
            row("GENTPF", "gentpf", "GENTPF", "GENTPF", UNAPPROVED, "2022-01-27", "",
                    "WECC directs transition to GENQEC."),
            row("GENROU", "genrou", "GENROU/IEEEVC", "GENROU", APPROVED, "2006-08-11",
                    "GENROU", "Round-rotor generator model."),
            row("GENSAL", "gensal", "GENSAL/IEEEVC", "GENSAL", RETIRED, "2011-01",
                    "GENSAL", "No longer approved; the source list notes conversion to GENTPJ with KIS=0."),
            row("GENTPJ", "gentpj", "GENTPJU1, GENTPJ1", "GENTPJ", UNAPPROVED,
                    "2022-01-27", "", "WECC directs transition to GENQEC."),
            row("GENCC", "gencc", "GENROU/IEEEVC", "GENCC", TRANSITIONAL, "", "",
                    "Cross-compound model; the source list directs transition to GENQEC."),
            row("GENQEC", "genqec", "GENQEC", "GENQEC", APPROVED, "2020-12-03",
                    "GENQEC", "Saturation applied to mutual inductances."),
            row("GENQEJ", "genqej", "GENQEJ", "GENQEJ", APPROVED, "2026-01-30",
                    "GENQEJ", "GENQEC dynamics with KIS current-dependent saturation input."),
            row("GENCLS", "gencls", "PLBVFU1, GENCLS", "GENCLS", NEVER_APPROVED, "",
                    "GENCLS", "Classical generator or playback representation."));

    private static final List<WeccModelApproval> STABILIZERS = List.of(
            stabilizer("WSCCST", "wsccst", "ST2CUT", "WSCCST and ST2CUT", APPROVED,
                    "2006-08-11", "ST2CUT", "Legacy dual-input WSCC stabilizer."),
            stabilizer("PSS2A", "pss2a", "PSS2A", "PSS2A, PSS3B", APPROVED,
                    "2006-08-11", "PSS2A", "Dual-input delta-P/omega stabilizer."),
            stabilizer("PSLF-PSS2C", "pss2c", "PSS2C", "PSS2C", APPROVED,
                    "2020-04-22", "", "Source-list PSLF cross-software row."),
            stabilizer("IEEEST", "ieeest", "IEEEST", "IEEEST", APPROVED,
                    "2006-08-11", "IEEEST", "Single-input dual-lead-lag stabilizer."),
            stabilizer("PSSSB", "psssb", "PSS2A", "PSSSB", APPROVED,
                    "2006-08-11", "", "PSS2A plus transient stabilizer."),
            stabilizer("PSS1A", "pss1a", "PSS1A", "PSS1A", APPROVED,
                    "2016-11-17", "PSS1A", "Generic single-input stabilizer."),
            stabilizer("PSS2B", "pss2b", "PSS2B", "PSS2B", APPROVED,
                    "2006-08-11", "PSS2B",
                    "Dual-input stabilizer with an additional lead/lag block."),
            stabilizer("PSS2C", "pss2c", "PSS2C", "PSS2C", APPROVED,
                    "2020-04-22", "PSS2C", "IEEE 421.5-2016 PSS2C."),
            stabilizer("PSS3B", "pss3b", "PSS3B", "PSS3B", APPROVED,
                    "2006-08-11", "PSS3B", "Thyripol/Unitrol stabilizer."),
            stabilizer("PSS4B", "pss4b", "PSS4B", "PSS4B", APPROVED,
                    "2006-08-11", "PSS4B", "ABB multiband stabilizer."),
            stabilizer("PSS3C", "pss3c", "PSS3C", "PSS3C", APPROVED,
                    "2020-04-22", "PSS3C", "IEEE 421.5-2016 PSS3C."),
            stabilizer("PSS4C", "pss4c", "PSS4C", "PSS4C", APPROVED,
                    "2020-04-22", "PSS4C", "IEEE 421.5-2016 PSS4C."),
            stabilizer("PSS5C", "pss5c", "PSS5C", "PSS5C", APPROVED,
                    "2020-04-22", "PSS5C", "IEEE 421.5-2016 PSS5C."),
            stabilizer("PSS6C", "pss6c", "PSS6C", "PSS6C", APPROVED,
                    "2020-04-22", "PSS6C", "IEEE 421.5-2016 PSS6C."),
            stabilizer("PSS7C", "pss7c", "PSS7C", "PSS7C", APPROVED,
                    "2020-04-22", "PSS7C", "IEEE 421.5-2016 PSS7C."),
            stabilizer("PSSSH", "psssh", "", "PSSSH", NEVER_APPROVED,
                    "", "", "Siemens H-infinity stabilizer."));

    private static final List<WeccModelApproval> GOVERNORS = List.of(
            governor("G2WSCC", "g2wscc", "WSHYDD", "G2WSCC, WSHYDD", RETIRED, "2021-12-02", ""),
            governor("GAST", "gast", "URGS3T", "GAST_GE, URGS3T", RETIRED, "2018-05-11", ""),
            governor("GGOV1", "ggov1", "GGOV1", "GGOV1", APPROVED, "2006-08-11", "GGOV1"),
            governor("GPWSCC", "gpwscc", "WSHYGP", "GPWSCC, WSHYGP", RETIRED, "2021-12-02", ""),
            governor("H6B", "h6b", "", "H6B", RETIRED, "2016-06-15", ""),
            governor("H6E", "h6e", "H6EU1", "H6E", APPROVED, "2018-05-11", "H6E"),
            governor("HYG3", "hyg3", "HYG3U1", "HYG3", APPROVED, "2006-08-11", "HYG3"),
            governor("HYGOV", "hygov", "HYGOV", "HYGOV", APPROVED, "2006-08-11", "HYGOV"),
            governor("HYGOV4", "hygov4", "IEEEG3", "HYGOV4", APPROVED, "2006-08-11", "IEEEG3"),
            governor("HYGOVR", "hygovr", "HYGOVR1, HYGOVRU", "HYGOVR", APPROVED,
                    "2008", "HYGOVR"),
            governor("IEEEG1", "ieeeg1", "WSIEG1", "IEEEG1, WSIEG1", APPROVED, "2006-08-11", "IEEEG1"),
            governor("IEEEG3", "ieeeg3", "IEEEG3", "IEEEG3", RETIRED, "2021-12-02", "IEEEG3"),
            governor("LCFB1", "lcfb1", "LCFB1", "LCFB1, LCFB1_PTI", APPROVED,
                    "2006-08-11", "LCFB1"),
            governor("PIDGOV", "pidgov", "PIDGOV", "PIDGOV", RETIRED, "2021-12-02", ""),
            governor("TGOV1", "tgov1", "TGOV1", "TGOV1", APPROVED, "2006-08-11", "TGOV1"),
            governor("GGOV2", "ggov2", "GGOV2", "GGOV2", NEVER_APPROVED, "", ""),
            // The May 2026 table has no PTI PSS/E entry for GGOV3.  It is a
            // GE PSLF model implemented by PowerWorld, not a PSS/E DYR model.
            governor("GGOV3", "ggov3", "", "GGOV3", APPROVED, "2010", ""),
            governor("GGOV1D", "", "GGOV1DU, GGOV1D", "GGOV1D", APPROVED, "2019-11", "GGOV1D"),
            governor("IEEEG1D", "", "IEEEG1SDU, IEEEG1CDU, IEEEG1D", "IEEEG1D", APPROVED, "2019-11", "IEEEG1D"),
            governor("IEESGOD", "", "IEESGODU, IEESGOD", "IEESGOD", APPROVED, "2019-11", "IEESGOD"),
            governor("WESGOVD", "", "WESGOVDU, WESGOVD", "WESGOVD", APPROVED, "2019-11", "WESGOVD"),
            governor("WPIDHYD", "", "WPIDHYDU, WPIDHYD", "WPIDHYD", APPROVED, "2019-11", "WPIDHYD"),
            governor("GASTWDD", "", "GASTWDDU, GASTWDD", "GASTWDD", APPROVED, "2019-11", "GASTWDD"),
            governor("GAST2AD", "", "GAST2ADU, GAST2AD", "GAST2AD", APPROVED, "2019-11", "GAST2AD"),
            governor("GASTD", "", "GASTDU, GASTD", "GASTD", APPROVED, "2019-11", "GASTD"),
            governor("HYGOVD", "", "HYGOVDU, HYGOVD", "HYGOVD", APPROVED, "2019-11", "HYGOVD"),
            governor("TGOV1D", "", "TGOV1DU, TGOV1D", "TGOV1D", APPROVED, "2019-11", "TGOV1D"),
            governor("IEEEG3D", "", "IEEEG3DU, IEEEG3D", "IEEEG3D", APPROVED, "2019-11", "IEEEG3D"),
            governor("DEGOV1D", "DEGOV1", "DEGOV1DU, DEGOV1D", "DEGOV1D", APPROVED, "2019-11", "DEGOV1D"),
            governor("PIDGOVD", "", "PIDGOVDU, PIDGOVD", "PIDGOVD", APPROVED, "2019-11", "PIDGOVD"),
            governor("TGOV3D", "", "TGOV3DU, TGOV3D", "TGOV3D", APPROVED, "2019-11", "TGOV3D"),
            governor("HYGOV2D", "", "HYGOV2DU, HYGOV2D", "HYGOV2D", APPROVED, "2019-11", "HYGOV2D"));

    private WeccApprovedDynamicModelCatalog() {
    }

    public static List<WeccModelApproval> generators() {
        return GENERATORS;
    }

    public static List<WeccModelApproval> exciters() {
        return EXCITERS;
    }

    public static List<WeccModelApproval> stabilizers() {
        return STABILIZERS;
    }

    public static List<WeccModelApproval> governors() {
        return GOVERNORS;
    }

    public static Optional<WeccModelApproval> findGenerator(String catalogName) {
        if (catalogName == null || catalogName.isBlank()) return Optional.empty();
        String normalized = catalogName.trim().toUpperCase(Locale.ROOT);
        return GENERATORS.stream().filter(row -> row.catalogName().equals(normalized)).findFirst();
    }

    public static Optional<WeccModelApproval> findExciter(String catalogName) {
        if (catalogName == null || catalogName.isBlank()) return Optional.empty();
        String normalized = catalogName.trim().toUpperCase(Locale.ROOT);
        return EXCITERS.stream().filter(row -> row.catalogName().equals(normalized)).findFirst();
    }

    private static WeccModelApproval row(String name, String pslf, String psse,
            String powerWorld, WeccModelApprovalStatus status, String effective,
            String interpss, String comments) {
        return new WeccModelApproval(name, SYNCHRONOUS_MACHINE, pslf, psse, powerWorld,
                status, effective, interpss, comments);
    }

    private static WeccModelApproval stabilizer(String name, String pslf, String psse,
            String powerWorld, WeccModelApprovalStatus status, String effective,
            String interpss, String comments) {
        return new WeccModelApproval(name, STABILIZER, pslf, psse, powerWorld,
                status, effective, interpss, comments);
    }

    private static WeccModelApproval governor(String name, String pslf, String psse,
            String powerWorld, WeccModelApprovalStatus status, String effective,
            String interpss) {
        return new WeccModelApproval(name, GOVERNOR, pslf, psse, powerWorld,
                status, effective, interpss, "");
    }

    private static WeccModelApproval exciter(String name, String pslf, String psse,
            String powerWorld, WeccModelApprovalStatus status, String effective,
            String interpss) {
        return new WeccModelApproval(name, EXCITER, pslf, psse, powerWorld,
                status, effective, interpss, "");
    }
}
