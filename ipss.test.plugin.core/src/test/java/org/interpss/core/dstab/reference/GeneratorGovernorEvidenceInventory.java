package org.interpss.core.dstab.reference;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Reviewed links to model-specific public evidence, not a VERIFIED status.
 * Simulator scripts, versions, hashes and full traces remain in the private archive.
 */
public final class GeneratorGovernorEvidenceInventory {
    private GeneratorGovernorEvidenceInventory() {}

    public enum Oracle { POWERWORLD, PSSE, ANDES, PSSE_TGOV3D }

    public record Evidence(String model, String focusedTest, String trajectoryTest,
                           Oracle oracle, String checkpoint, String dyr) {
        public Path fixture() {
            return Path.of("testData", "adpter", "psse", "v33", "SMIB", dyr);
        }

        public Class<?> focusedTestClass() throws ClassNotFoundException {
            return Class.forName("org.interpss.core.adapter.builder.dstab." + focusedTest,
                    false, GeneratorGovernorEvidenceInventory.class.getClassLoader());
        }

        public Class<?> trajectoryTestClass() throws ClassNotFoundException {
            return Class.forName("org.interpss.core.dstab." + trajectoryTest,
                    false, GeneratorGovernorEvidenceInventory.class.getClassLoader());
        }

        public void requireReadableCheckpoint() {
            switch (oracle) {
                case POWERWORLD -> {
                    if (EmbeddedTrajectoryReference.embedded(checkpoint).samples().isEmpty())
                        throw new IllegalStateException("Empty " + checkpoint);
                }
                case PSSE -> {
                    EmbeddedNativeTrajectoryValues.verifyCheckpoint(
                            Path.of(checkpoint), EmbeddedNativeTrajectoryValues.bytes(Path.of(checkpoint)));
                    if(model.equals("GENQEC")){
                        var profiles=GenqecSchemaNativeReference.profiles();
                        if(profiles.size()!=10)throw new IllegalStateException("Incomplete native generator selector matrix");
                        for(var profile:profiles){profile.body();profile.input();profile.network();}
                        var compensation=GenqecKwNativeReference.profiles();
                        if(compensation.size()!=6)throw new IllegalStateException("Incomplete field-compensation reference matrix");
                        for(var profile:compensation){profile.body();profile.input();profile.network();if(profile.replayBounds().length!=7)throw new IllegalStateException("Missing field-compensation channel bounds");}
                    }
                    if(model.equals("GENQEC")||model.equals("GENQEJ")){
                        var profiles=GenqecSalientNativeReference.profiles();
                        if(profiles.size()!=8)throw new IllegalStateException("Incomplete zero-q-transient evidence");
                        for(var profile:profiles){profile.body();profile.input();profile.network();if(profile.replayBounds().length!=7)throw new IllegalStateException("Missing electrical replay bounds");}
                    }
                }
                case ANDES -> {
                    if (EmbeddedCsvTrajectoryValues.lines(checkpoint).isEmpty())
                        throw new IllegalStateException("Empty " + checkpoint);
                    if(model.equals("GENROU")){
                        var profiles=GenrouNativeReference.profiles();
                        if(profiles.size()!=20)throw new IllegalStateException("Incomplete native round-rotor matrix");
                        for(var profile:profiles){profile.body();profile.input();profile.network();}
                        // Native initialization and fault diagnostics supplement,
                        // rather than replace, the accepted ANDES trajectory gate.
                    }
                }
                case PSSE_TGOV3D -> {
                    if (Tgov3dPsseReference.lines().isEmpty())
                        throw new IllegalStateException("Empty TGOV3D checkpoints");
                }
            }
        }
    }

    private static final List<Evidence> ENTRIES = List.of(
            new Evidence("GENROU", "DStabNetworkBuilderMachineTest",
                    "mach.GenrouAndesSmibConformanceTest", Oracle.ANDES,
                    "genrou-smib-line-trip.csv", "SMIB_v33_genrou.dyr"),
            new Evidence("GENQEC", "GenqecNativeSchemaTest",
                    "mach.GenqecuPsseSmibConformanceTest", Oracle.PSSE,
                    "smib-genqecu/psse.csv", "SMIB_v33_genqecu.dyr"),
            new Evidence("GENQEJ", "GenqecNativeSchemaTest",
                    "mach.GenqejPsseSmibConformanceTest", Oracle.PSSE,
                    "smib-genqej/psse.csv", "SMIB_v33_genqej.dyr"),
            new Evidence("GGOV1", "PsseGgov1GovernorTest",
                    "Ggov1IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-ggov1", "SMIB_v33_genrou_ggov1.dyr"),
            new Evidence("H6E", "PsseH6eGovernorTest",
                    "H6eIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-h6e", "SMIB_v33_genrou_h6e_independent.dyr"),
            new Evidence("HYG3", "PsseHyg3GovernorTest",
                    "Hyg3IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-hyg3", "SMIB_v33_genrou_hyg3_independent.dyr"),
            new Evidence("HYGOV", "PsseHygovGovernorTest",
                    "HygovIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-hygov", "SMIB_v33_genrou_hygov.dyr"),
            new Evidence("IEEEG3", "DStabNetworkBuilderIeeeg3Test",
                    "Ieeeg3dIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-ieeeg3", "SMIB_v33_genrou_ieeeg3.dyr"),
            new Evidence("HYGOVR", "PsseHygovrGovernorTest",
                    "Hygovr1IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-hygovr1", "SMIB_v33_genrou_hygovr1_independent.dyr"),
            new Evidence("IEEEG1", "DStabNetworkBuilderGovernorTest",
                    "Ieeeg1IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-ieeeg1", "SMIB_v33_genrou_ieeeg1.dyr"),
            new Evidence("LCFB1", "Lcfb1PrefControllerTest",
                    "Lcfb1IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-tgov1-lcfb1", "SMIB_v33_genrou_tgov1_lcfb1.dyr"),
            new Evidence("TGOV1", "DStabNetworkBuilderGovernorTest",
                    "Tgov1IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-tgov1", "SMIB_v33_genrou_tgov1.dyr"),
            new Evidence("GGOV1D", "PsseGgov1GovernorTest",
                    "Ggov1IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-ggov1d", "SMIB_v33_genrou_ggov1d.dyr"),
            new Evidence("IEEEG1D", "DStabNetworkBuilderIeeeg1dTest",
                    "Ieeeg1dPsseSmibConformanceTest", Oracle.PSSE,
                    "smib-genrou-ieeeg1d/psse.csv", "SMIB_v33_genrou_ieeeg1d.dyr"),
            new Evidence("IEESGOD", "DStabNetworkBuilderIeesgodTest",
                    "IeesgodIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-ieesgod", "SMIB_v33_genrou_ieesgod.dyr"),
            new Evidence("WESGOVD", "DStabNetworkBuilderWesgovdTest",
                    "WesgovdIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-wesgovd", "SMIB_v33_genrou_wesgovd.dyr"),
            new Evidence("WPIDHYD", "DStabNetworkBuilderWpidhydTest",
                    "WpidhydIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-wpidhyd", "SMIB_v33_genrou_wpidhyd.dyr"),
            new Evidence("GASTWDD", "DStabNetworkBuilderGastwddTest",
                    "GastwddIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-gastwdd", "SMIB_v33_genrou_gastwdd.dyr"),
            new Evidence("GAST2AD", "DStabNetworkBuilderGast2adTest",
                    "Gast2adIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-gast2ad", "SMIB_v33_genrou_gast2ad.dyr"),
            new Evidence("GASTD", "DStabNetworkBuilderGastdTest",
                    "GastdIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-gastd", "SMIB_v33_genrou_gastd.dyr"),
            new Evidence("HYGOVD", "PsseHygovGovernorTest",
                    "HygovdIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-hygovd", "SMIB_v33_genrou_hygovd_independent.dyr"),
            new Evidence("TGOV1D", "DStabNetworkBuilderTgov1dTest",
                    "Tgov1IndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-tgov1d", "SMIB_v33_genrou_tgov1d.dyr"),
            new Evidence("IEEEG3D", "DStabNetworkBuilderIeeeg3dTest",
                    "Ieeeg3dIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-ieeeg3d", "SMIB_v33_genrou_ieeeg3d.dyr"),
            new Evidence("DEGOV1D", "DStabNetworkBuilderDegov1dTest",
                    "Degov1dIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-degov1d", "SMIB_v33_genrou_degov1d.dyr"),
            new Evidence("PIDGOVD", "DStabNetworkBuilderPidgovdTest",
                    "PidgovdIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-pidgovd", "SMIB_v33_genrou_pidgovd.dyr"),
            new Evidence("TGOV3D", "DStabNetworkBuilderTgov3dTest",
                    "Tgov3dIndependentSmibConformanceTest", Oracle.PSSE_TGOV3D,
                    "TGOV3D native 0.1 ms checkpoints", "SMIB_v33_genrou_tgov3d.dyr"),
            new Evidence("HYGOV2D", "DStabNetworkBuilderHygov2dTest",
                    "Hygov2dIndependentSmibConformanceTest", Oracle.POWERWORLD,
                    "smib-genrou-hygov2d", "SMIB_v33_genrou_hygov2d_independent.dyr")
    ).stream().sorted(Comparator.comparing(Evidence::model)).toList();

    public static List<Evidence> entries() { return ENTRIES; }

    public static String markdown() {
        StringBuilder text = new StringBuilder("# Generator and governor verification evidence\n\n")
                .append("Generated from GeneratorGovernorEvidenceInventory. Covers the three approved ")
                .append("generator models and 24 approved native reference simulator governor mappings in the May 2026 catalog.\n\n")
                .append("Each row links a DYR fixture, focused tests, readable checkpoints and a ")
                .append("registered trajectory comparison. It does not establish coverage of all ")
                .append("selectors, limits, lifecycle events or Texas2k system acceptance. ")
                .append("Simulator versions, cold regeneration and raw provenance require the private archive.\n\n")
                .append("POWERWORLD identifies archived PowerWorld exports; PSSE and PSSE_TGOV3D ")
                .append("identify native reference simulator exports; ANDES identifies the separate open-source oracle. ")
                .append("A reference's presence alone does not prove its comparison passes.\n\n")
                .append("GENQEC's exact standard input is one selector plus sixteen constants; GENQECU and GENQEJU use the allocation wrapper. ")
                .append("The former flat eighteen-field interchange is not a native input. Native nonzero selectors normalize to scaled quadratic, separately from the typed three-way setting. ")
                .append("GenqecSchemaNativeReference adds ten complete raw histories from twenty cold runs and GenqecNativeSelectorConformanceTest checks imported three-cycle fault equivalence. ")
                .append("These establish the schema/selector boundary, not complete machine or cross-tool trajectory parity. See [the scoped source and evidence report](benchmarks/genqec-native-schema-2026-10-18.md).\n\n")
                .append("GenqecSalientNativeReference adds eight exact Tqop=0/Xqp=Xq profiles for GENQEC/GENQEJ and both saturation modes. ")
                .append("The four electrical states, saturation and terminal power are replayed from native phasor/speed/field-voltage inputs; forced mechanical inputs are not predicted-state acceptance. ")
                .append("The existing zero-state/general q-coordinate implementation is retained. First-fault callback residuals decrease under step halving, but complete coupled parity remains open. See [the salient-branch evidence](benchmarks/genqec-salient-branch-2026-10-19.md).\n\n")
                .append("GENQEC field compensation now accepts finite Kw>=0 without the former artificial Kw<1 cap; the runtime still clips Kw*Id to +/-0.4. ")
                .append("GenqecKwNativeReference adds six exact-input profiles for Kw=0/1/1.5 with native limiter entry, both signs and release. ")
                .append("Native field initialization and electrical replay are separate from complete coupled acceptance. See [the compensation evidence](benchmarks/genqec-field-compensation-2026-10-19.md).\n\n")
                .append("GENROU/GENROE now reuse the core round-rotor equations with distinct quadratic/exponential saturation. ")
                .append("GenrouNativeReference adds twenty six-state profiles from forty raw-exact cold runs; GenrouNativeSmibTest checks native initialization, immutable named states and reconstruction. ")
                .append("Native coupled fault differences remain diagnostics, and the existing ANDES gate remains unchanged. See [the saturation evidence and TODO](benchmarks/genrou-native-saturation-2026-10-19.md).\n\n")
                .append("Supplemental salient-pole evidence does not change the approved-row counts above. GENSAL retains the core d-axis-only solver; GENSAE adds the native three-coordinate both-axis saturation adapter while reusing core mechanics and Norton admittance. ")
                .append("GensalNativeReference retains thirty-two five-state/phasor profiles from sixty-four cold runs. GensalNativeSmibTest separates bounded native state comparisons from remaining event-side and checkpoint gaps. See [the salient-pole evidence and TODO](benchmarks/gensal-gensae-native-2026-10-19.md).\n\n")
                .append("| Model | Oracle | DYR fixture | Focused tests | Trajectory comparison | Checkpoints |\n")
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
