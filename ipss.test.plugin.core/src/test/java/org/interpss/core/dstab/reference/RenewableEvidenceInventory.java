package org.interpss.core.dstab.reference;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.WeccModelApprovalStatus;

/** Approved native renewable coverage, including absent implementations, not VERIFIED labels. */
public final class RenewableEvidenceInventory {
    private RenewableEvidenceInventory() {}
    public enum Scope { MISSING_IMPLEMENTATION, FOCUSED_ONLY, BOUNDED_DIALECT_COMPARISON,
        NATIVE_PARTIAL_PROFILE, NATIVE_RECORDED_PROFILE }
    public enum CheckpointKind { NONE, NATIVE_CSV, REGFMA_NATIVE_MAP, WTDTB_NATIVE_MAP, WTPTB_NATIVE_MAP, WTGWGO_NATIVE_MAP, REECD_PAUX_NATIVE_MAP,
        WTGIBFFR_NATIVE_MAP, REECE_NATIVE_MAP, REGFMB_NATIVE_MAP, INLINE_REGCA_CURRENT_LIMIT, ANDES_CSV }
    public record Entry(String model,String focusedTest,String trajectoryTest,Scope scope,
                        CheckpointKind storage,String checkpoint,String fixture,String limitation) {
        public Class<?> focusedClass() throws ClassNotFoundException {
            return Class.forName("org.interpss.core.adapter.builder.dstab."+focusedTest,false,
                    RenewableEvidenceInventory.class.getClassLoader());
        }
        public Class<?> trajectoryClass() throws ClassNotFoundException {
            return Class.forName("org.interpss.core.dstab."+trajectoryTest,false,
                    RenewableEvidenceInventory.class.getClassLoader());
        }
        /** Readability/integrity guard only, not cold-provenance or complete-model acceptance. */
        public void requireReadableCheckpoint() throws Exception {
            switch(storage) {
                case NONE -> { if(!checkpoint.isEmpty())throw new IllegalStateException("Unexpected checkpoint"); }
                case NATIVE_CSV -> {
                    Path path=Path.of("testData/reference/psse").resolve(checkpoint);
                    EmbeddedNativeTrajectoryValues.verifyCheckpoint(path,EmbeddedNativeTrajectoryValues.bytes(path));
                }
                case REGFMA_NATIVE_MAP -> {
                    var references=Regfma1NativeReferenceCheckpoints.references();
                    if(references.isEmpty())throw new IllegalStateException("Empty native REGFMA1 reference map");
                    for(var reference:references.values())reference.verify(reference.body());
                }
                case WTDTB_NATIVE_MAP -> {
                    var references=Wtdtb1NativeReference.profiles();
                    if(references.size()!=14)throw new IllegalStateException("Incomplete native WTDTB1 profile matrix");
                    for(var reference:references)reference.verify();
                }
                case WTPTB_NATIVE_MAP -> {
                    var references=Wtptb1NativeExtendedReference.profiles();
                    if(references.size()!=10)throw new IllegalStateException("Incomplete WTPTB1 native branch matrix");
                    for(var reference:references)reference.verify();
                }
                case WTGWGO_NATIVE_MAP -> {
                    var references=WtgwgoaNativeReference.profiles();
                    if(references.size()!=10)throw new IllegalStateException("Incomplete WTGWGOA native branch matrix");
                    for(var reference:references)reference.verify();
                    if(!WtgwgoaNetworkCoarse.BODY_SHA256.equals(WtgwgoaNativeReference.digest(WtgwgoaNetworkCoarse.body()))
                            ||!WtgwgoaNetworkFine.BODY_SHA256.equals(WtgwgoaNativeReference.digest(WtgwgoaNetworkFine.body())))
                        throw new IllegalStateException("Changed WTGWGOA native network reference");
                }
                case REECD_PAUX_NATIVE_MAP -> {
                    var references=ReecdAuxiliaryPowerReference.profiles();
                    if(references.size()!=4)throw new IllegalStateException("Incomplete REECD Paux profile matrix");
                    for(var reference:references)reference.verify();
                }
                case WTGIBFFR_NATIVE_MAP -> {
                    var references=WtgibffraNativeReference.profiles();
                    if(references.size()!=7)throw new IllegalStateException("Incomplete WTGIBFFRA kernel references");
                    for(var reference:references){reference.inputs();reference.states();}
                    if(WtgibffraCoupledReference.profiles().size()!=2)throw new IllegalStateException("Incomplete coupled WTGIBFFRA references");
                    for(var reference:WtgibffraCoupledReference.profiles())reference.body();
                }
                case REGFMB_NATIVE_MAP -> {
                    if(Regfmb1NativeContractReference.profiles().size()!=4||Regfmb1SelectorNativeReference.profiles().size()!=11)throw new IllegalStateException("Incomplete REGFMB1U reference matrix");
                    for(var p:Regfmb1NativeContractReference.profiles())p.body();
                    for(var p:Regfmb1SelectorNativeReference.profiles())p.body();
                }
                case REECE_NATIVE_MAP -> {
                    if(ReeceLocalPiNativeReference.profiles().size()!=2||ReeceBranchNativeReference.profiles().size()!=10)
                        throw new IllegalStateException("Incomplete native REECE replay matrix");
                    for(var p:ReeceLocalPiNativeReference.profiles()){p.inputs();p.states();}
                    for(var p:ReeceBranchNativeReference.profiles())p.body();
                    if(ReeceRegcbNativeBoundaryReference.profiles().size()!=2)
                        throw new IllegalStateException("Incomplete REGCB blocking boundary references");
                    for(var p:ReeceRegcbNativeBoundaryReference.profiles())p.body();
                }
                case INLINE_REGCA_CURRENT_LIMIT -> {
                    for(String name:List.of("QUARTER","EIGHTH")) {
                        var field=trajectoryClass().getDeclaredField(name);field.setAccessible(true);
                        String text=(String)field.get(null);
                        if(text.lines().count()<2)throw new IllegalStateException("Empty inline "+name);
                        for(String row:text.strip().split("\\R")) {
                            String[] values=row.split(",");
                            if(values.length!=6)throw new IllegalStateException("Invalid REGCA1 checkpoint width");
                            for(String value:values)if(!Double.isFinite(Double.parseDouble(value)))
                                throw new IllegalStateException("Nonfinite REGCA1 checkpoint");
                        }
                    }
                }
                case ANDES_CSV -> {
                    if(EmbeddedCsvTrajectoryValues.lines(checkpoint).size()<2)
                        throw new IllegalStateException("Empty ANDES reference "+checkpoint);
                }
            }
        }
    }

    private static final List<Entry> ENTRIES=java.util.stream.Stream.concat(
            List.of(
                nativeRow("DERA1","Dera1ModelTest","Dera1NativeSmibConformanceTest","smib-dera1/psse.csv","SMIB/SMIB_v33_dera1.dyr"),
                new Entry("REGCA1","PsseRegca1ConverterTest","RegcaCurrentLimitNativeTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.INLINE_REGCA_CURRENT_LIMIT,"QUARTER,EIGHTH","renewable/regca_iolim_native.dyr",
                        "Native Iolim direction/selected endpoints only; high-voltage boundary, complete fault and instantaneous event-post parity remain open. Inline checkpoint provenance/digests require separate review."),
                nativeRow("REGCB1","Regcb1ModelTest","Regcb1NativeSmibConformanceTest","smib-regcb1/psse.csv","SMIB/SMIB_v33_regcb1.dyr"),
                new Entry("REGFMA1","PsseRegfma1NativeSchemaTest","Regfma1NativeTrajectoryTest",Scope.NATIVE_RECORDED_PROFILE,
                        CheckpointKind.REGFMA_NATIVE_MAP,"Regfma1NativeReferenceCheckpoints","",
                        "Fifteen native checkpoint profiles construct DYR in the test; plant-input/lifecycle, lower integral limit and exact event-post gaps remain. Typed/PowerWorld dialect is separate."),
                nativeRow("WT1G1","PsseWt1g1ModelTest","mach.Wt1g1PsseSmibConformanceTest","smib-wt1g1/psse.csv","SMIB/SMIB_v33_wt1g1.dyr"),
                nativeRow("WT2G1","PsseWt2g1ModelTest","mach.Wt2g1PsseSmibConformanceTest","smib-wt2g1/psse.csv","SMIB/SMIB_v33_wt2g1.dyr"),
                nativeRow("WT2E1","PsseWt2e1ModelTest","mach.Wt2e1PsseSmibConformanceTest","smib-wt2e1/psse.csv","SMIB/SMIB_v33_wt2e1.dyr"),
                nativeRow("REECC1","PsseReecc1ControllerTest","Reecc1NativeConformanceTest","reecc1-bus1062/psse.csv","renewable/regca_reecc1_bus1062.dyr"),
                nativeRow("WT12T1","PsseWt12t1ModelTest","mach.Wt1g1PsseSmibConformanceTest","smib-wt12t1/psse.csv","SMIB/SMIB_v33_wt12t1.dyr"),
                new Entry("WTDTBU1","PsseWtdtb1ModelTest","Wtdtb1NativePartialConformanceTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.WTDTB_NATIVE_MAP,"Wtdtb1NativeReference","renewable/regca_reeca_wtdtb1_bus1062.dyr",
                        "Native 36.7 standard/exact legacy wrapper; 14 cold-repeated profiles. Unit-speed five-state playback is bounded; non-unit absolute integrated-coordinate precision and instantaneous coupled-network event-post parity remain open. Published PowerWorld convention is separate."),
                new Entry("REECD","PsseReecd1ControllerTest","ReecdAuxiliaryPowerNativeConformanceTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.REECD_PAUX_NATIVE_MAP,"ReecdAuxiliaryPowerReference","renewable/regca_reecd_paux_bus1062.dyr",
                        "Native 36.7 Paux power-before-voltage-divider and algebraic-update profile only: four cold-paired recorded-input/coupled SMIB profiles at two steps. Both reference-update sides retained. Inactive PI/bypass storage differs; other selectors, limits, lifecycle and complete REECD conformance remain open. This is not WTGIBFFRA implementation evidence."),
                new Entry("WTPTBU1","PsseWtptb1ModelTest","Wtptb1NativeExtendedConformanceTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.WTPTB_NATIVE_MAP,"Wtptb1NativeExtendedReference","renewable/regca_reeca_wtptb1_bus1062.dyr",
                        "Native 36.7 strict standard/legacy import, zero-time bypass and recorded-input branch matrix. Coupled two-step flat/fault/trip checks; instantaneous network-post, additional lifecycle/base profiles and complete cross-tool acceptance remain open. Typed PowerWorld cold-repeat artifacts are separate evidence."),
                focused("WT12A1B","PsseWt12a1bModelTest","No independent WT12A1U_B trajectory link found; the older WT12A1 reference is a different model."),
                new Entry("WTGIBFFRA","PsseWtgibffraModelTest","WtgibffraNativePartialConformanceTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.WTGIBFFR_NATIVE_MAP,"WtgibffraNativeReference","renewable/regca_reecd_wtgibffra_bus1062.dyr",
                        "Strict native one-ICON/45-CON import and REGCA/REGCB + REECD Paux attachment; seven cold-paired kernel replay profiles, two cold-paired governed-SMIB trajectories and two-step flat/frequency-step/fault/trip tests. Published automatic recovery differs from native 36.7. Callback parity, nonconvergent frequency/voltage floors, alternate hosts/bases and complete PowerWorld verification remain open."),
                new Entry("WTGWGOAU","PsseWtgwgoaModelTest","WtgwgoaNativePartialConformanceTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.WTGWGO_NATIVE_MAP,"WtgwgoaNativeReference","renewable/regca_reeca_wtgwgoa_bus1062.dyr",
                        "Native 36.7 strict standard/legacy import and REEC reference routing; ten recorded-input profiles plus coupled flat/fault/no-plant/trip checks at two steps. Callback/handoff and instantaneous network-post parity remain open; published second-fault restart is a separate explicit convention. PowerWorld export diagnostic is not accepted evidence."),
                new Entry("REECE","PsseReeceModelTest","ReeceRuntimeNativePartialConformanceTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.REECE_NATIVE_MAP,"ReeceBranchNativeReference + ReeceLocalPiNativeReference","renewable/regca_reece_bus1062.dyr",
                        "Native 36.7 standard REECE eight-ICON/79-CON import and deferred active REGCA/REGCB attachment; twelve recorded-input replay datasets, two cold-paired REGCB hardware-blocking boundary profiles and two-step flat/fault/trip checks. Source-listed REECE1/REECEU1 names/wrappers remain unsupported. Prepared-reference/current-limit callbacks, remaining active-limit branches and independent coupled native/PowerWorld trajectories remain open; not complete conformance."),
                new Entry("REGFMB1U","PsseRegfmb1ModelTest","Regfmb1NativeRuntimePartialConformanceTest",Scope.NATIVE_PARTIAL_PROFILE,
                        CheckpointKind.REGFMB_NATIVE_MAP,"Regfmb1NativeContractReference + Regfmb1SelectorNativeReference","renewable/regfmb1u_native.dyr",
                        "Native 36.7 exact USRMDL 101 1 6 25 14 17 import and network adapter; thirteen fourteen-state replays and two-step flat/fault/trip checks. Source REGFMB1 is not an inferred alias. Modified-/staggered-Euler, active limits, further selectors/lifecycle and complete coupled/PowerWorld acceptance remain open."),
                bounded("REECA1","PsseReeca1ControllerTest"),
                bounded("REPCA1","PsseRepca1PlantControllerTest"),
                bounded("WTDTA1","PsseType3WindControllerTest"),
                bounded("WTARA1","PsseType3WindControllerTest"),
                bounded("WTPTA1","PsseType3WindControllerTest"),
                bounded("WTTQA1","PsseType3WindControllerTest")
            ).stream(),
            Set.of("REGFMC1",
                    "REPCCU","HPLNTD","REPCGFMC1").stream().map(model->
                new Entry(model,"","",Scope.MISSING_IMPLEMENTATION,CheckpointKind.NONE,"","",
                        model.equals("REGFMC1")
                        ?"Native 36.7 REGFMC1U data, hybrid boundary and eleven-state kernel exist, with twelve bounded native replays. No parser or network device yet; retain missing-runtime classification. Import, coupled SMIB, precision/callback, lifecycle and PowerWorld acceptance remain open."
                        :"No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit."))
    ).sorted(Comparator.comparing(Entry::model)).toList();

    private static Entry nativeRow(String model,String focused,String trajectory,String checkpoint,String fixture) {
        return new Entry(model,focused,trajectory,Scope.NATIVE_RECORDED_PROFILE,CheckpointKind.NATIVE_CSV,
                checkpoint,fixture,"Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review.");
    }
    private static Entry focused(String model,String focused,String limitation) {
        return new Entry(model,focused,"",Scope.FOCUSED_ONLY,CheckpointKind.NONE,"","",limitation);
    }
    private static Entry bounded(String model,String focused) {
        return new Entry(model,focused,"Type3WindAndesTrajectoryTest",Scope.BOUNDED_DIALECT_COMPARISON,
                CheckpointKind.ANDES_CSV,"type3-wind-bus1062-fault.csv","renewable/type3_wind_bus1062.dyr",
                "ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native PSS/E conformance. PowerWorld Type-3 test is finite-error diagnostic only.");
    }
    public static List<Entry> entries() { return ENTRIES; }
    public static Set<String> requiredModels() {
        return WeccApprovedDynamicModelCatalog.renewables().stream()
                .filter(row->row.approvalStatus()==WeccModelApprovalStatus.APPROVED&&row.hasPsseModel())
                .map(row->row.interpssModel()).collect(Collectors.toUnmodifiableSet());
    }
    public static String markdown() {
        var source=WeccApprovedDynamicModelCatalog.renewables();
        StringBuilder text=new StringBuilder("# Approved native renewable-model coverage\n\n")
                .append("Generated from RenewableEvidenceInventory and the visually reviewed May 2026 PDF, pages 3-4. ")
                .append("Source SHA-256: `").append(WeccApprovedDynamicModelCatalog.SOURCE_PDF_SHA256).append("`.\n\n")
                .append("The source renewable section has 45 rows: 29 approved, 28 with a native PTI mapping, ")
                .append("and 27 distinct planned native families because WT1T/WT2T share WT12T1. ")
                .append("23 families currently have loadable runtime paths; four implementations are genuinely missing. ")
                .append("REEC_E uses the proved native 36.7 standard name REECE; the source-listed REECE1/REECEU1 names and wrappers remain unsupported. ")
                .append("PVD1 has a blank PTI cell and is excluded from native scope. Approval, source mapping, loadability, ")
                .append("recorded profile evidence and complete verification are separate claims. No row below is VERIFIED.\n\n")
                .append("Eight families have native recorded-profile links, eight have partial native profiles (REGCA1 Iolim, WTDTB1, WTPTB1, WTGWGOA, REECD Paux, WTGIBFFRA kernel, REECE and REGFMB1U), ")
                .append("six have only the explicitly bounded ANDES coupled comparison, and one has focused tests only. ")
                .append("None of the latter seven is established as native simulator conformance. Legacy/retired and ")
                .append("manufacturer-package supplemental models need a separate evidence expansion; their source rows ")
                .append("are retained below and must not be counted as approved implementations. No new simulator run ")
                .append("or complete raw-provenance acceptance is claimed by this inventory.\n\n")
                .append("| Native family | Evidence scope | Focused test | Comparison | Checkpoint | Limitation |\n")
                .append("|---|---|---|---|---|---|\n");
        for(Entry row:ENTRIES)text.append("| ").append(row.model()).append(" | ").append(row.scope())
                .append(" | ").append(row.focusedTest().isEmpty()?"missing":row.focusedTest())
                .append(" | ").append(row.trajectoryTest().isEmpty()?"none":row.trajectoryTest())
                .append(" | ").append(row.checkpoint().isEmpty()?"none":row.checkpoint())
                .append(" | ").append(row.limitation()).append(" |\n");
        text.append("\n## Source columns and implementation presence\n\n")
                .append("These are frozen source-list statuses, not a claim about today's WECC approval. ")
                .append("Planned mappings for absent models do not add parser aliases, guessed field counts or dummy runtimes. ")
                .append("Descriptive vendor-package cells are retained as comments rather than invented native model identifiers.\n\n")
                .append("| Source row | PSLF | PTI PSS/E | PowerWorld | Source status | Runtime/planned mapping | Loadable primary mapping |\n")
                .append("|---|---|---|---|---|---|---|\n");
        for(var row:source)text.append("| ").append(row.catalogName()).append(" | ").append(row.pslfModel())
                .append(" | ").append(row.psseModel().isEmpty()?"(blank/no concrete identifier)":row.psseModel())
                .append(" | ").append(row.powerWorldModel()).append(" | ").append(row.approvalStatus())
                .append(" | ").append(row.interpssModel().isEmpty()?"none":row.interpssModel())
                .append(" | ").append(row.isImplementedExactly()?"yes (not verified)":"no").append(" |\n");
        return text.toString();
    }
    public static void main(String[] args) { System.out.print(markdown()); }
}
