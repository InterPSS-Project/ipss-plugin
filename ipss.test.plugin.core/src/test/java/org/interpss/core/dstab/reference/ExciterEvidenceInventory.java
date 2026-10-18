package org.interpss.core.dstab.reference;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.interpss.core.dstab.reference.GeneratorGovernorEvidenceInventory.Evidence;
import org.interpss.core.dstab.reference.GeneratorGovernorEvidenceInventory.Oracle;

/** Explicit native-model links; neither model aliases nor reference presence imply verification. */
public final class ExciterEvidenceInventory {
    private ExciterEvidenceInventory() {}

    private static final List<Evidence> ENTRIES = List.of(
            pw("AC11C", "DStabNetworkBuilderAc11cTest", "Ac11cIndependentSmibConformanceTest"),
            pw("AC1C", "DStabNetworkBuilderAc1cTest", "Ac1cIndependentSmibConformanceTest"),
            pw("AC2C", "DStabNetworkBuilderAc2cTest", "Ac2cIndependentSmibConformanceTest"),
            pw("AC3C", "DStabNetworkBuilderAc3cTest", "Ac3cIndependentSmibConformanceTest"),
            pw("AC4C", "DStabNetworkBuilderAc4cTest", "Ac4cIndependentSmibConformanceTest"),
            pw("AC5C", "DStabNetworkBuilderAc5cTest", "Ac5cIndependentSmibConformanceTest"),
            pw("AC6C", "DStabNetworkBuilderAc6cTest", "Ac6cIndependentSmibConformanceTest"),
            pw("AC7B", "Ac7bImportTest", "Ac7bIndependentSmibConformanceTest", "ac7b_independent"),
            pw("AC7C", "DStabNetworkBuilderAc7cTest", "Ac7cIndependentSmibConformanceTest"),
            pw("AC8B", "DStabNetworkBuilderExciterTest", "Ac8bIndependentSmibConformanceTest",
                    "ac8b_independent"),
            pw("AC8C", "DStabNetworkBuilderAc8cTest", "Ac8cIndependentSmibConformanceTest"),
            pw("AC9C", "DStabNetworkBuilderAc9cTest", "Ac9cIndependentSmibConformanceTest"),
            psse("BBSEX1", "DStabNetworkBuilderBbsex1Test", "Bbsex1PsseSmibConformanceTest"),
            pw("DC1C", "DStabNetworkBuilderDc1c2cTest", "Dc1cIndependentSmibConformanceTest"),
            pw("DC2C", "DStabNetworkBuilderDc1c2cTest", "Dc2cIndependentSmibConformanceTest"),
            pw("DC3A", "Dc3aExciterTest", "Dc3aIndependentSmibConformanceTest"),
            pw("DC4B", "Dc4bExciterTest", "Dc4bIndependentSmibConformanceTest"),
            pw("DC4C", "DStabNetworkBuilderDc4cTest", "Dc4cIndependentSmibConformanceTest"),
            pw("ESAC1A", "DStabNetworkBuilderEsac1aTest", "Esac1aIndependentSmibConformanceTest",
                    "esac1a_kf01"),
            pw("ESAC2A", "DStabNetworkBuilderEsac2aTest", "Esac2aIndependentSmibConformanceTest"),
            pw("ESAC3A", "DStabNetworkBuilderEsac3aTest", "Esac3aIndependentSmibConformanceTest"),
            pw("ESAC4A", "Esac4aExciterTest", "Esac4aIndependentSmibConformanceTest",
                    "esac4a_independent"),
            pw("ESAC5A", "DStabNetworkBuilderEsac5aTest", "Esac5aIndependentSmibConformanceTest"),
            pw("ESAC6A", "Esac6aExciterTest", "Esac6aIndependentSmibConformanceTest",
                    "esac6a_independent"),
            pw("ESAC8B", "DStabNetworkBuilderEsac8bTest", "Esac8bIndependentSmibConformanceTest",
                    "esac8b_independent"),
            pw("ESDC1A", "DStabNetworkBuilderEsdc1a2aTest", "Esdc1aIndependentSmibConformanceTest"),
            pw("ESDC2A", "DStabNetworkBuilderEsdc1a2aTest", "Esdc1aIndependentSmibConformanceTest"),
            pw("ESST1A", "PsseEsst1aExciterTest", "Esst1aIndependentSmibConformanceTest"),
            pw("ESST2A", "Esst2aExciterTest", "Esst2aIndependentSmibConformanceTest"),
            pw("ESST3A", "Esst3aExciterTest", "Esst3aIndependentSmibConformanceTest"),
            pw("ESST4B", "PsseEsst4bExciterTest", "Esst4bIndependentSmibConformanceTest"),
            pw("ESURRY", "DStabNetworkBuilderEsurryTest", "EsurryIndependentSmibConformanceTest"),
            pw("EXAC1", "DStabNetworkBuilderExac1Test", "Exac1IndependentSmibConformanceTest"),
            pw("EXAC1A", "DStabNetworkBuilderExac1aTest", "Exac1aIndependentSmibConformanceTest",
                    "exac1a_independent"),
            pw("EXAC2", "DStabNetworkBuilderExac2Test", "Exac2IndependentSmibConformanceTest"),
            pw("EXAC4", "Exac4ExciterTest", "Exac4IndependentSmibConformanceTest",
                    "exac4_independent"),
            psse("EXDC2", "DStabNetworkBuilderExciterTest", "Exdc2PsseSmibConformanceTest"),
            psse("EXELI", "ExeliExciterTest", "ExeliPsseSmibConformanceTest"),
            pw("EXST1", "PsseLegacyControllerMappingTest", "Esst1aIndependentSmibConformanceTest"),
            psse("EXST2", "Exst2ExciterTest", "Exst2PsseSmibConformanceTest"),
            pw("EXST3", "Exst3ExciterTest", "Exst3IndependentSmibConformanceTest"),
            pw("IEEET1", "PsseLegacyControllerMappingTest", "Ieeet1IndependentSmibConformanceTest"),
            psse("IEEET4", "Ieeet4OpenSourceEquationConformanceTest", "Ieeet4PsseSmibConformanceTest"),
            psse("IEEEX1", "DStabNetworkBuilderExciterTest", "Ieeex1PsseSmibConformanceTest"),
            pw("REXSYS", "RexsysExciterTest", "RexsysIndependentSmibConformanceTest",
                    "rexsys_independent"),
            psse("SCRX", "DStabNetworkBuilderScrxTest", "ScrxPsseSmibConformanceTest"),
            psse("ST10C", "St10cExciterTest", "St10cPsseSmibConformanceTest"),
            pw("ST1C", "St1cExciterTest", "St1cIndependentSmibConformanceTest"),
            pw("ST2C", "St2cExciterTest", "St2cIndependentSmibConformanceTest"),
            pw("ST3C", "St3cExciterTest", "St3cIndependentSmibConformanceTest"),
            pw("ST4C", "St4cExciterTest", "St4cIndependentSmibConformanceTest"),
            pw("ST5B", "St5bExciterTest", "St5bIndependentSmibConformanceTest"),
            pw("ST5C", "St5cExciterTest", "St5cIndependentSmibConformanceTest"),
            pw("ST6B", "St6bExciterTest", "St6bIndependentSmibConformanceTest"),
            psse("ST6C", "St6cExciterTest", "St6cPsseSmibConformanceTest"),
            psse("ST7B", "St7bExciterTest", "St7bPsseSmibConformanceTest"),
            psse("ST7C", "St7cExciterTest", "St7cPsseSmibConformanceTest"),
            psse("ST8C", "St8cExciterTest", "St8cPsseSmibConformanceTest"),
            psse("ST9C", "St9cExciterTest", "St9cPsseSmibConformanceTest")
    ).stream().sorted(Comparator.comparing(Evidence::model)).toList();

    private static Evidence pw(String model, String focused, String trajectory) {
        return pw(model, focused, trajectory, model.toLowerCase(Locale.ROOT));
    }

    private static Evidence pw(String model, String focused, String trajectory, String fixtureSuffix) {
        return new Evidence(model, focused, trajectory, Oracle.POWERWORLD,
                "smib-genrou-" + model.toLowerCase(Locale.ROOT),
                "SMIB_v33_genrou_" + fixtureSuffix + ".dyr");
    }

    private static Evidence psse(String model, String focused, String trajectory) {
        String lower = model.toLowerCase(Locale.ROOT);
        return new Evidence(model, focused, trajectory, Oracle.PSSE,
                "smib-genrou-" + lower + "/psse.csv", "SMIB_v33_genrou_" + lower + ".dyr");
    }

    public static List<Evidence> entries() { return ENTRIES; }

    public static void main(String[] args) { System.out.print(markdown()); }

    public static String markdown() {
        StringBuilder text = new StringBuilder("# Exciter verification evidence\n\n")
                .append("Generated from ExciterEvidenceInventory. The 64 approved source-list rows ")
                .append("with native reference simulator mappings in the May 2026 catalog resolve to 59 distinct ")
                .append("native models. Vendor rows sharing a native mapping are not separate models.\n\n")
                .append("Each row links a native DYR fixture, focused tests, readable embedded ")
                .append("checkpoints and a registered trajectory comparison. POWERWORLD identifies ")
                .append("archived PowerWorld exports; PSSE identifies native reference simulator exports. ")
                .append("These are reviewed evidence links, not VERIFIED labels or proof of all ")
                .append("selectors, active limits, zero-time branches, lifecycle events, or Texas2k acceptance. ")
                .append("Raw simulator provenance, exact versions and cold regeneration remain ")
                .append("subject to review in the private archive. No new reference is generated by this inventory.\n\n")
                .append("| Native model | Oracle | DYR fixture | Focused tests | Trajectory comparison | Checkpoints |\n")
                .append("|---|---|---|---|---|---|\n");
        for (Evidence entry : ENTRIES) {
            text.append("| ").append(entry.model()).append(" | ").append(entry.oracle())
                    .append(" | ").append(entry.dyr()).append(" | ").append(entry.focusedTest())
                    .append(" | ").append(entry.trajectoryTest()).append(" | ")
                    .append(entry.checkpoint()).append(" |\n");
        }
        return text.toString();
    }
}
