package org.interpss.fadapter.psse.dyr;

import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.SYNCHRONOUS_MACHINE;
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

    private WeccApprovedDynamicModelCatalog() {
    }

    public static List<WeccModelApproval> generators() {
        return GENERATORS;
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
}
