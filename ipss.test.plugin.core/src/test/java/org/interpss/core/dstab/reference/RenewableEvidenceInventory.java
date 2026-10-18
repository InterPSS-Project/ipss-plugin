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
    public enum CheckpointKind { NONE, NATIVE_CSV, REGFMA_NATIVE_MAP, INLINE_REGCA_CURRENT_LIMIT, ANDES_CSV }
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
                focused("REECD","PsseReecd1ControllerTest","No independent REECD-specific trajectory link found; unit equations/schema are not simulator parity."),
                focused("WT12A1B","PsseWt12a1bModelTest","No independent WT12A1U_B trajectory link found; the older WT12A1 reference is a different model."),
                bounded("REECA1","PsseReeca1ControllerTest"),
                bounded("REPCA1","PsseRepca1PlantControllerTest"),
                bounded("WTDTA1","PsseType3WindControllerTest"),
                bounded("WTARA1","PsseType3WindControllerTest"),
                bounded("WTPTA1","PsseType3WindControllerTest"),
                bounded("WTTQA1","PsseType3WindControllerTest")
            ).stream(),
            Set.of("REGFMB1","REGFMC1","REECE1","WTGWGOAU","WTGIBFFRA","WTPTBU1",
                    "WTDTBU1","REPCCU","HPLNTD","REPCGFMC1").stream().map(model->
                new Entry(model,"","",Scope.MISSING_IMPLEMENTATION,CheckpointKind.NONE,"","",
                        "No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit."))
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
                .append("Only 17 families currently have loadable runtime paths; ten implementations are genuinely missing. ")
                .append("PVD1 has a blank PTI cell and is excluded from native scope. Approval, source mapping, loadability, ")
                .append("recorded profile evidence and complete verification are separate claims. No row below is VERIFIED.\n\n")
                .append("Eight families have native recorded-profile links, one has a partial native Iolim profile, ")
                .append("six have only the explicitly bounded ANDES coupled comparison, and two have focused tests only. ")
                .append("None of the latter eight is established as native simulator conformance. Legacy/retired and ")
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
