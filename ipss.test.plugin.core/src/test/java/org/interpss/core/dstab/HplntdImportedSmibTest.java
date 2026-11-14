package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import org.interpss.fadapter.psse.dyr.*;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full-file wrapper import; rejection never masquerades as a loaded group. */
public class HplntdImportedSmibTest extends CorePluginTestSetup {
    record Imported(DynamicSimuAlgorithm algorithm,PSSEDStabDirectParser parser) {
        Regca1Model unit(int bus){return (Regca1Model)((DStabGen)algorithm.getNetwork().getBus("Bus"+bus).getContributeGen("1")).getDynamicGenDevice();}
    }
    private Imported load(Path dir,String text,boolean strict,Set<PsseGnetIdvProcessor.GeneratorKey> removed)throws Exception {
        return load(dir,text,strict,removed,Set.of());
    }
    private Imported load(Path dir,String text,boolean strict,Set<PsseGnetIdvProcessor.GeneratorKey> removed,
            Set<PsseGnetIdvProcessor.GeneratorKey> stacks)throws Exception {
        Path raw=HplntdTypedRuntimeSmibTest.writeNetwork(dir),dyr=dir.resolve("input.dyr");Files.writeString(dyr,text);
        var ctx=new PSSEMultiFileLoader().loadDStab(raw.toString());
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(ctx.getDStabilityNet())).setStrictImport(strict).setGnetRemovedGenerators(removed).setModelRemovedGenerators(stacks);
        parser.parseDynFile(dyr.toString());return new Imported(ctx.getDynSimuAlgorithm(),parser);
    }
    private static String input(){return HplntdAggregationNativeReference.profiles().getFirst().input();}

    @Test void strictImportAndReversedSourceOrderMatchTypedRuntimeAtBothSteps(@TempDir Path dir)throws Exception {
        var factory=new HplntdTypedRuntimeSmibTest();
        for(double h:new double[]{.0005,.00025}){
            var typed=factory.fixture(dir.resolve("typed"+h),false,h,false);
            var imported=factory.fixture(dir.resolve("imported"+h),true,h,true);
            var g=imported.group();assertSame(g,imported.second().getReeca1Controller().getHybridController().owner());
            var named=imported.first().getNamedStates();
            assertEquals(13,named.keySet().stream().filter(n->n.startsWith("HPLNTDU.")).count());
            assertEquals(2,named.keySet().stream().filter(n->n.startsWith("REAXDU.")).count());
            for(var group:List.of(typed.group(),g)){
                var r=group.kernel().references();group.kernel().setReferences(new HplntdKernel.References(
                        r.voltage(),r.reactive()+.02,r.frequency(),r.active()+.05,r.powerFactor(),r.voltageAux(),r.activeAux()));
            }
            for(int tick=0;tick<200;tick++){
                assertTrue(typed.algorithm().solveDEqnStep(true));assertTrue(imported.algorithm().solveDEqnStep(true));
                assertArrayEquals(typed.group().kernel().states(),g.kernel().states(),1e-10);
                for(int i=0;i<2;i++)assertArrayEquals(typed.group().ports().get(i).states(),g.ports().get(i).states(),1e-10);
            }
            assertEquals(200,g.predictorCount());assertEquals(17,g.getNamedStates().size());
        }
    }

    @Test void oneRemovedUnitDoesNotRemoveTheBusPlant(@TempDir Path dir)throws Exception {
        var f=load(dir,input(),true,Set.of(new PsseGnetIdvProcessor.GeneratorKey("Bus4","1")));
        assertNotNull(f.unit(1).getReeca1Controller().getHybridController());
        assertEquals(1,f.unit(1).getReeca1Controller().getHybridController().owner().ports().size());
        var report=f.parser().getLastImportReport();assertTrue(report.isStrictlyComplete());assertEquals(3,report.count(DynamicModelImportStatus.SKIPPED_GNET));
    }

    @Test void allRemovedUnitsMakeTheirBusPlantExplicitlyInapplicable(@TempDir Path dir)throws Exception {
        var f=load(dir,input(),true,Set.of(new PsseGnetIdvProcessor.GeneratorKey("Bus1","1"),new PsseGnetIdvProcessor.GeneratorKey("Bus4","1")));
        var report=f.parser().getLastImportReport();assertTrue(report.isStrictlyComplete());assertEquals(7,report.count(DynamicModelImportStatus.SKIPPED_GNET));
        assertEquals(1,report.count(DynamicModelImportStatus.ATTACHED));
    }

    @Test void duplicateAndMalformedBatchesDoNotPartiallyAttach(@TempDir Path dir)throws Exception {
        String plant=input().lines().filter(s->s.contains("USRBUS")).findFirst().orElseThrow();
        for(String text:List.of(input()+plant+"\n",input().replace("504 0 35 61 13 45","504 0 35 60 13 45"))){
            var f=load(dir.resolve("case"+Math.abs(text.hashCode())),text,false,Set.of());
            assertFalse(f.parser().getLastImportReport().isStrictlyComplete());
            assertNull(f.unit(1).getReeca1Controller().getHybridController());assertNull(f.unit(4).getReeca1Controller().getHybridController());
            assertTrue(f.parser().getLastImportReport().count(DynamicModelImportStatus.REJECTED)>=3);
        }
    }

    @Test void missingTargetRejectsTheWholeGroupAndStrictImportFails(@TempDir Path dir)throws Exception {
        String text=input().replace("4 'USRMDL'","9 'USRMDL'");
        var f=load(dir.resolve("report"),text,false,Set.of());
        assertEquals(3,f.parser().getLastImportReport().count(DynamicModelImportStatus.MISSING_TARGET));
        assertNull(f.unit(1).getReeca1Controller().getHybridController());
        assertThrows(com.interpss.common.exp.InterpssException.class,()->load(dir.resolve("strict"),text,true,Set.of()));
    }

    @Test void unsupportedLinksNeverBecomeSilentCommands(@TempDir Path dir)throws Exception {
        for(String text:List.of(input().replace("2 'HPLNTDU' 37","2 'NOTPLANT' 37"),
                input().replace("37 38 39 42 43 44 45","36 38 39 42 43 44 45"))){
            var f=load(dir.resolve("case"+Math.abs(text.hashCode())),text,false,Set.of());
            assertFalse(f.parser().getLastImportReport().isStrictlyComplete());
            assertNull(f.unit(1).getReeca1Controller().getHybridController());assertNull(f.unit(4).getReeca1Controller().getHybridController());
            assertEquals(3,f.parser().getLastImportReport().entries().stream()
                    .filter(e->e.status()==DynamicModelImportStatus.UNSUPPORTED||e.status()==DynamicModelImportStatus.REJECTED).count());
        }
    }

    @Test void anOccupiedSecondPortCannotPartiallyAttachTheFirst(@TempDir Path dir)throws Exception {
        String legacy=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr"))
                .lines().filter(s->s.contains("'REPCA1'")).findFirst().orElseThrow();
        var f=load(dir,input()+legacy.replaceFirst("^1 ","4 ")+"\n",false,Set.of());
        assertNotNull(f.unit(4).getReeca1Controller().getPlantController());
        assertNull(f.unit(1).getReeca1Controller().getHybridController());assertNull(f.unit(4).getReeca1Controller().getHybridController());
        assertEquals(3,f.parser().getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    @Test void mixedIntentionalRemovalsAreStrictlyAccountedWithoutAPhantomPlant(@TempDir Path dir)throws Exception {
        var f=load(dir,input(),true,Set.of(new PsseGnetIdvProcessor.GeneratorKey("Bus1","1")),
                Set.of(new PsseGnetIdvProcessor.GeneratorKey("Bus4","1")));
        var report=f.parser().getLastImportReport();assertTrue(report.isStrictlyComplete());
        assertEquals(3,report.count(DynamicModelImportStatus.SKIPPED_GNET));
        assertEquals(4,report.count(DynamicModelImportStatus.SKIPPED_MODEL_REMOVE));
        assertEquals(8,report.totalRecordCount());
    }

    @Test void importedThreeCycleFaultMatchesTheTypedCompositionAtBothSteps(@TempDir Path dir)throws Exception {
        var factory=new HplntdTypedRuntimeSmibTest();
        for(double h:new double[]{.0005,.00025}){
            var typed=factory.fixture(dir.resolve("typed"+h),false,h,false);
            var imported=factory.fixture(dir.resolve("imported"+h),true,h,true);
            for(var f:List.of(typed,imported))f.algorithm().getNetwork().addDynamicEvent(
                    DStabObjectFactory.createBusFaultEvent("Bus2",f.algorithm().getNetwork(),
                            com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,
                            new org.apache.commons.math3.complex.Complex(0,.1),null,.05,.05),"three-cycle");
            for(int tick=0;tick<(int)Math.round(.2/h);tick++){
                assertTrue(typed.algorithm().solveDEqnStep(true));assertTrue(imported.algorithm().solveDEqnStep(true));
                assertArrayEquals(typed.group().kernel().states(),imported.group().kernel().states(),1e-10);
                for(int i=1;i<=4;i++)assertEquals(typed.algorithm().getNetwork().getBus("Bus"+i).getVoltageMag(),
                        imported.algorithm().getNetwork().getBus("Bus"+i).getVoltageMag(),1e-10);
            }
        }
    }

    @Test void reusedElectricalObjectRejectsBeforeAnyHybridPortIsMutated(@TempDir Path dir)throws Exception {
        String base=input().lines().filter(s->!s.contains("HPLNTDU")&&!s.contains("REAXDU"))
                .reduce("",(a,b)->a+b+"\n");
        for(boolean includeFirst:new boolean[]{true,false}){
            Path caseDir=dir.resolve("first"+includeFirst);var f=load(caseDir,base,true,Set.of());
            f.unit(4).setReeca1Controller(f.unit(1).getReeca1Controller());
            String hybrid=input().lines().filter(s->s.contains("USRBUS")||s.contains("REAXDU")&&(includeFirst||s.startsWith("4 ")))
                    .reduce("",(a,b)->a+b+"\n");
            Path file=caseDir.resolve("hybrid.dyr");Files.writeString(file,hybrid);
            f.parser().setStrictImport(false).parseDynFile(file.toString());
            assertFalse(f.parser().getLastImportReport().isStrictlyComplete());
            assertNull(f.unit(1).getReeca1Controller().getHybridController(),"Invalid ownership must not leave a partial first port");
            assertNull(f.unit(4).getReeca1Controller().getHybridController());
        }
    }
}
