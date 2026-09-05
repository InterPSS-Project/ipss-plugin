package org.interpss.fadapter.psse.dyr;

import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.SYNCHRONOUS_MACHINE;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.STABILIZER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.GOVERNOR;
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
                    "2006-08-11", "", "Dual-input stabilizer with an additional lead/lag block."),
            stabilizer("PSS2C", "pss2c", "PSS2C", "PSS2C", APPROVED,
                    "2020-04-22", "", "IEEE 421.5-2016 PSS2C."),
            stabilizer("PSS3B", "pss3b", "PSS3B", "PSS3B", APPROVED,
                    "2006-08-11", "", "Thyripol/Unitrol stabilizer."),
            stabilizer("PSS4B", "pss4b", "PSS4B", "PSS4B", APPROVED,
                    "2006-08-11", "", "ABB multiband stabilizer."),
            stabilizer("PSS3C", "pss3c", "PSS3C", "PSS3C", APPROVED,
                    "2020-04-22", "", "IEEE 421.5-2016 PSS3C."),
            stabilizer("PSS4C", "pss4c", "PSS4C", "PSS4C", APPROVED,
                    "2020-04-22", "", "IEEE 421.5-2016 PSS4C."),
            stabilizer("PSS5C", "pss5c", "PSS5C", "PSS5C", APPROVED,
                    "2020-04-22", "", "IEEE 421.5-2016 PSS5C."),
            stabilizer("PSS6C", "pss6c", "PSS6C", "PSS6C", APPROVED,
                    "2020-04-22", "", "IEEE 421.5-2016 PSS6C."),
            stabilizer("PSS7C", "pss7c", "PSS7C", "PSS7C", APPROVED,
                    "2020-04-22", "", "IEEE 421.5-2016 PSS7C."),
            stabilizer("PSSSH", "psssh", "", "PSSSH", NEVER_APPROVED,
                    "", "", "Siemens H-infinity stabilizer."));

    private static final List<WeccModelApproval> GOVERNORS = List.of(
            governor("G2WSCC", "g2wscc", "WSHYDD", "G2WSCC, WSHYDD", RETIRED, "2021-12-02", ""),
            governor("GAST", "gast", "URGS3T", "GAST_GE, URGS3T", RETIRED, "2018-05-11", ""),
            governor("GGOV1", "ggov1", "GGOV1", "GGOV1", APPROVED, "2006-08-11", "GGOV1"),
            governor("GPWSCC", "gpwscc", "WSHYGP", "GPWSCC, WSHYGP", RETIRED, "2021-12-02", ""),
            governor("H6B", "h6b", "", "H6B", RETIRED, "2016-06-15", ""),
            governor("H6E", "h6e", "H6EU1", "H6E", APPROVED, "2018-05-11", ""),
            governor("HYG3", "hyg3", "HYG3U1", "HYG3", APPROVED, "2006-08-11", ""),
            governor("HYGOV", "hygov", "HYGOV", "HYGOV", APPROVED, "2006-08-11", "HYGOV"),
            governor("HYGOV4", "hygov4", "IEEEG3", "HYGOV4", APPROVED, "2006-08-11", ""),
            governor("HYGOVR", "hygovr", "HYGOVR", "HYGOVR", APPROVED, "2008", ""),
            governor("IEEEG1", "ieeeg1", "WSIEG1", "IEEEG1, WSIEG1", APPROVED, "2006-08-11", "IEEEG1"),
            governor("IEEEG3", "ieeeg3", "IEEEG3", "IEEEG3", RETIRED, "2021-12-02", ""),
            governor("LCFB1", "lcfb1", "LCFB1", "LCFB1, LCFB1_PTI", APPROVED, "2006-08-11", ""),
            governor("PIDGOV", "pidgov", "PIDGOV", "PIDGOV", RETIRED, "2021-12-02", ""),
            governor("TGOV1", "tgov1", "TGOV1", "TGOV1", APPROVED, "2006-08-11", "TGOV1"),
            governor("GGOV2", "ggov2", "GGOV2", "GGOV2", NEVER_APPROVED, "", ""),
            governor("GGOV3", "ggov3", "GGOV3", "GGOV3", APPROVED, "2010", ""),
            governor("GGOV1D", "", "GGOV1DU, GGOV1D", "GGOV1D", APPROVED, "2019-11", "GGOV1D"),
            governor("IEEEG1D", "", "IEEEG1SDU, IEEEG1CDU, IEEEG1D", "IEEEG1D", APPROVED, "2019-11", ""),
            governor("IEESGOD", "", "IEESGODU, IEESGOD", "IEESGOD", APPROVED, "2019-11", ""),
            governor("WESGOVD", "", "WESGOVDU, WESGOVD", "WESGOVD", APPROVED, "2019-11", ""),
            governor("WPIDHYD", "", "WPIDHYDU, WPIDHYD", "WPIDHYD", APPROVED, "2019-11", ""),
            governor("GASTWDD", "", "GASTWDDU, GASTWDD", "GASTWDD", APPROVED, "2019-11", ""),
            governor("GAST2AD", "", "GAST2ADU, GAST2AD", "GAST2AD", APPROVED, "2019-11", ""),
            governor("GASTD", "", "GASTDU, GASTD", "GASTD", APPROVED, "2019-11", ""),
            governor("HYGOVD", "", "HYGOVDU, HYGOVD", "HYGOVD", APPROVED, "2019-11", "HYGOVD"),
            governor("TGOV1D", "", "TGOV1DU, TGOV1D", "TGOV1D", APPROVED, "2019-11", "TGOV1D"),
            governor("IEEEG3D", "", "IEEEG3DU, IEEEG3D", "IEEEG3D", APPROVED, "2019-11", ""),
            governor("DEGOV1D", "DEGOV1", "DEGOV1DU, DEGOV1D", "DEGOV1D", APPROVED, "2019-11", ""),
            governor("PIDGOVD", "", "PIDGOVDU, PIDGOVD", "PIDGOVD", APPROVED, "2019-11", ""),
            governor("TGOV3D", "", "TGOV3DU, TGOV3D", "TGOV3D", APPROVED, "2019-11", ""),
            governor("HYGOV2D", "", "HYGOV2DU, HYGOV2D", "HYGOV2D", APPROVED, "2019-11", ""));

    private WeccApprovedDynamicModelCatalog() {
    }

    public static List<WeccModelApproval> generators() {
        return GENERATORS;
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
}
