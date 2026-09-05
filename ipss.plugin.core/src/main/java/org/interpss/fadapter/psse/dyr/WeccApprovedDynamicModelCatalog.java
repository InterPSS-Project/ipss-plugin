package org.interpss.fadapter.psse.dyr;

import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.SYNCHRONOUS_MACHINE;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.STABILIZER;
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
                    "2016-11-17", "", "Generic single-input stabilizer."),
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

    private WeccApprovedDynamicModelCatalog() {
    }

    public static List<WeccModelApproval> generators() {
        return GENERATORS;
    }

    public static List<WeccModelApproval> stabilizers() {
        return STABILIZERS;
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
}
