package org.interpss.core.dstab.reference;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;

import org.interpss.core.dstab.reference.GeneratorGovernorEvidenceInventory.Evidence;
import org.interpss.core.dstab.reference.GeneratorGovernorEvidenceInventory.Oracle;

/** Evidence links and explicit equivalence gaps, never a model acceptance label. */
public final class StabilizerEvidenceInventory {
    private StabilizerEvidenceInventory() {}

    public enum ReferenceScope { MODEL_SPECIFIC_COMPARISON, EQUIVALENT_SIGNAL_PLAYBACK }

    public record Entry(Evidence evidence, ReferenceScope scope, String limitation) {
        public String model() { return evidence.model(); }
        public String sourcePsseNames() {
            return WeccApprovedDynamicModelCatalog.stabilizers().stream()
                    .filter(row -> row.interpssModel().equals(model())).map(row -> row.psseModel())
                    .distinct().sorted().collect(Collectors.joining(", "));
        }
    }

    private static final List<Entry> ENTRIES=List.of(
            nativeReference("IEEEST","IeeestPsseSmibConformanceTest"),
            new Entry(new Evidence("PSS1A","DStabNetworkBuilderStabilizerTest",
                    "IeeestPsseSmibConformanceTest",Oracle.PSSE,
                    "smib-genrou-esst1a-ieeest/psse.csv","SMIB_v33_genrou_esst1a_pss1a.dyr"),
                    ReferenceScope.EQUIVALENT_SIGNAL_PLAYBACK,
                    "Primary link is equivalent IEEEST playback; supplementary exact typed PowerWorld playback is registered, but complete commercial SMIB agreement remains open."),
            powerWorld("PSS2A","Pss2aIndependentSmibConformanceTest"),
            powerWorld("PSS2B","Pss2bIndependentSmibConformanceTest"),
            nativeReference("PSS2C","Pss2cPsseSmibConformanceTest"),
            nativeReference("PSS3B","Pss3bPsseSmibConformanceTest"),
            nativeReference("PSS3C","Pss3cPsseSmibConformanceTest"),
            nativeReference("PSS4B","Pss4bPsseSmibConformanceTest"),
            powerWorld("PSS4C","Pss4cIndependentSmibConformanceTest"),
            powerWorld("PSS5C","Pss5cIndependentSmibConformanceTest"),
            nativeReference("PSS6C","Pss6cPsseSmibConformanceTest"),
            nativeReference("PSS7C","Pss7cPsseSmibConformanceTest"),
            powerWorld("PSSSB","PsssbIndependentSmibConformanceTest"),
            nativeReference("ST2CUT","St2cutPsseSmibConformanceTest")
    ).stream().sorted(Comparator.comparing(Entry::model)).toList();

    private static Entry nativeReference(String model,String trajectory) {
        return entry(model,trajectory,Oracle.PSSE,"smib-genrou-esst1a-"
                +model.toLowerCase(Locale.ROOT)+"/psse.csv");
    }

    private static Entry powerWorld(String model,String trajectory) {
        return entry(model,trajectory,Oracle.POWERWORLD,"smib-genrou-esst1a-"
                +model.toLowerCase(Locale.ROOT));
    }

    private static Entry entry(String model,String trajectory,Oracle oracle,String checkpoint) {
        return new Entry(new Evidence(model,"DStabNetworkBuilderStabilizerTest",trajectory,
                oracle,checkpoint,"SMIB_v33_genrou_esst1a_"+model.toLowerCase(Locale.ROOT)+".dyr"),
                ReferenceScope.MODEL_SPECIFIC_COMPARISON,
                "Profile-specific comparison only; selectors, active limits, lifecycle and cold provenance need individual review.");
    }

    public static List<Entry> entries() { return ENTRIES; }

    public static void main(String[] args) { System.out.print(markdown()); }

    public static String markdown() {
        StringBuilder text=new StringBuilder("# Stabilizer verification evidence\n\n")
                .append("Generated from StabilizerEvidenceInventory. The fifteen approved source-list rows ")
                .append("in the May 2026 catalog resolve to fourteen distinct InterPSS stabilizer mappings; ")
                .append("the PSLF PSS2C row is not a second verified native model.\n\n")
                .append("The supplied PDF's twelve filled PTI cells contain nine distinct reference simulator names. ")
                .append("PSS1A maps to IEEEST and PSSSB maps to PSS2A in that column; PSS3C, PSS4C and ")
                .append("PSS5C have blank PTI cells. These source facts are not inferred DYR aliases or ")
                .append("claims that later installed releases lack those models. Existing implementation ")
                .append("and benchmark links are retained separately, including supplemental models.\n\n")
                .append("Thirteen mappings have model-specific comparison links: eight native reference simulator ")
                .append("and five PowerWorld. PSS1A's primary row below retains its equivalent IEEEST recorded-signal comparison. ")
                .append("Supplementary exact typed PowerWorld playback is registered in Pss1aPowerWorldPlaybackConformanceTest ")
                .append("at 0.5 and 0.25 ms, with all six published stabilizer states and Vstab scored at 5 ms checkpoints; ")
                .append("Additional Pss1aPowerWorldActiveConformanceTest profiles independently exercise both output limits ")
                .append("and upper/lower voltage cutouts at both steps; full-network SMIB differences, other selectors ")
                .append("and lifecycle coverage remain open. ")
                .append("This typed reference is not proof of a native PTI PSS1A DYR schema. None of these inventory rows is a ")
                .append("VERIFIED label or proof that every selector, limiter, state coordinate and lifecycle ")
                .append("profile is independently accepted. Current raw versions, repeatability and provenance ")
                .append("must be audited separately; no new simulator reference is generated here.\n\n")
                .append("PSS6C/PSS7C native import admits exactly 34/38 values. The PowerWorld-only ")
                .append("Tpgfilt extension remains available through the builder, not inferred by the native reader. ")
                .append("PssCanonicalNativeSchemaTest guards rejection, attachment and compensation-tail mapping; ")
                .append("four new cold runs reproduce every raw value of the existing two native references. ")
                .append("See [schema separation evidence](benchmarks/pss-canonical-native-schema-2026-10-19.md).\n\n")
                .append("All other focused checks below use DStabNetworkBuilderStabilizerTest.\n\n")
                .append("| InterPSS mapping | Source PTI cell | Oracle | Scope | DYR fixture | Comparison | Checkpoint | Limitation |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for(Entry row:ENTRIES) {
            Evidence e=row.evidence();
            text.append("| ").append(e.model()).append(" | ")
                    .append(row.sourcePsseNames().isEmpty()?"(blank)":row.sourcePsseNames()).append(" | ")
                    .append(e.oracle()).append(" | ")
                    .append(row.scope()).append(" | ").append(e.dyr()).append(" | ")
                    .append(e.trajectoryTest()).append(" | ")
                    .append(e.checkpoint()).append(" | ").append(row.limitation()).append(" |\n");
        }
        return text.toString();
    }
}
