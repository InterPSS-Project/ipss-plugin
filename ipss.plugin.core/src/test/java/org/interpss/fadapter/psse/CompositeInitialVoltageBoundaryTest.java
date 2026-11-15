package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A solved low source voltage is not an impossible composite-load operating point. */
class CompositeInitialVoltageBoundaryTest {
    @TempDir Path temp;
    private PSSEMultiFileLoader loader;
    private com.interpss.simu.SimuContext load(boolean extraLoad) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path fixtures=Path.of("..","ipss.test.plugin.core","testData","adpter","psse","v33","HspModels");
        var lines=new ArrayList<>(Files.readAllLines(fixtures.resolve("load-network.raw")));boolean generators=false;
        for(int i=0;i<lines.size();i++) {
            String line=lines.get(i);
            if(line.contains("BEGIN GENERATOR DATA")){generators=true;continue;}
            if(generators&&line.startsWith("0 /"))generators=false;
            if(generators){String[] fields=line.split(",");fields[6]=".94";lines.set(i,String.join(",",fields));}
        }
        if(extraLoad) {
            int end=java.util.stream.IntStream.range(0,lines.size()).filter(i->lines.get(i).contains("END OF LOAD DATA")).findFirst().orElseThrow();
            lines.add(end,"2,'2',1,1,1,1,.25,0,0,0,0,1,1,0");
        }
        Path raw=temp.resolve("low.raw"),dyr=temp.resolve("low.dyr");Files.write(raw,lines);
        // Deliberately precede the explicit model: scope priority is order-independent.
        var source=new StringBuilder("1 'IEELAR' '*' 0 1 0 1 0 0 0 0 2 1 0 2 1 0 /\n");
        for(var record:PsseDyrRecordReader.read(fixtures.resolve("cmldblu1-restart.dyr"))) {
            var fields=new ArrayList<>(record.fields());
            if(record.canonicalModelName().equals("CMLDBLU1")) {
                fields.set(13,".005");fields.set(14,".01");fields.set(16,"0");
                fields.set(17,"1");fields.set(18,"1");fields.set(19,"-1");
            }
            source.append(String.join(" ",fields)).append(" /\n");
        }
        Files.writeString(dyr,source);loader=new PSSEMultiFileLoader();
        return loader.loadDStab(raw.toString(),dyr.toString());
    }
    @Test void omittedTransformerUsesFiniteFeederFloorAndPreservesTheLowVoltageBoundary() throws Exception {
        var context=load(true);
        var parentBefore=context.getDStabilityNet().getDStabBus("Bus2");
        assertEquals(1,parentBefore.getDynLoadModelList().stream().filter(org.interpss.dstab.dynLoad.impl.IeelLoadModel.class::isInstance).count());
        var characteristic=(org.interpss.dstab.dynLoad.impl.IeelLoadModel)parentBefore.getDynLoadModelList().stream()
                .filter(org.interpss.dstab.dynLoad.impl.IeelLoadModel.class::isInstance).findFirst().orElseThrow();
        assertEquals("2",characteristic.getParentLoad().getId());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var parent=context.getDStabilityNet().getDStabBus("Bus2");
        var model=assertInstanceOf(Cmldblu1Model.class,parent.getInfoOnlyDynModel());assertTrue(parent.getVoltageMag()<.95);
        algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.initialization());assertSame(parent,model.getLowVoltBus());
        var feeder=context.getDStabilityNet().getBranchList().stream().filter(b->b.getToBus()==model.getLoadBus()).findFirst().orElseThrow();
        assertEquals(0,feeder.getZ().subtract(new Complex(1e-7,1e-5)).abs(),0);
        assertEquals(.005,model.getData().value(2),0);assertEquals(.01,model.getData().value(3),0);
        double initial=model.getLoadBus().getVoltageMag();assertTrue(initial<.95);
        for(int i=0;i<20;i++)assertTrue(algorithm.solveDEqnStep(true));
        assertEquals(initial,model.getLoadBus().getVoltageMag(),1e-4);
    }
    @Test void fullyShadowedAreaDefaultIsReportedWithoutASecondModelOnTheExplicitLoad() throws Exception {
        var context=load(false);
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        assertEquals(1,loader.getLastDynamicImportReport().count(org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.SKIPPED_LOAD_PRIORITY));
        assertTrue(context.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().stream()
                .noneMatch(org.interpss.dstab.dynLoad.impl.IeelLoadModel.class::isInstance));
    }

    @Test void stagedRelayFollowsZoneCompositeSourceIntoItsExpandedDistribution() throws Exception {
        var context=load(false);var parent=context.getDStabilityNet().getDStabBus("Bus2");
        var legacy=(Cmldblu1Model)parent.getInfoOnlyDynModel();
        var constants=Arrays.copyOf(legacy.getLegacyConstants(),133);constants[132]=1;
        var zone=new org.interpss.dstab.dynLoad.impl.Cmldznu2Model("zone",parent,Set.of("1"),
                parent.getContributeLoad("1").getLoad(parent.getVoltageMag()),new org.interpss.dstab.dynLoad.Cmldznu2Data(constants));
        parent.setInfoOnlyDynModel(zone);
        var stages=new ArrayList<org.interpss.dstab.relay.LoadSheddingStage>();
        stages.add(new org.interpss.dstab.relay.LoadSheddingStage(1.1,0,0,.25));
        for(int i=0;i<4;i++)stages.add(new org.interpss.dstab.relay.LoadSheddingStage(0,0,0,0));
        var relay=new org.interpss.dstab.relay.Lvs3blRelayModel(parent,"1",
                new org.interpss.dstab.relay.StagedLoadSheddingRelayData(stages,false),null,0,null,0);
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.initialization());assertTrue(context.getDStabilityNet().isDStabNetInitialized());
        assertNull(parent.getContributeLoad("1"));assertNotSame(parent,zone.getLoadBus());
        assertEquals(1,zone.getRemainingCapacity(),0);assertTrue(relay.afterStep(.001));
        assertEquals(.75,zone.getRemainingCapacity(),1e-12);assertEquals(.25,relay.getShedFraction(),1e-12);
        assertTrue(relay.afterStep(.001));assertEquals(.75,zone.getRemainingCapacity(),1e-12);
    }

    @Test void zoneDefaultsKeepIndependentSourceStatesAndSelectiveRelayActions() throws Exception {
        for(boolean wildcard:new boolean[]{false,true}) {
            var staged=load(true);var original=(Cmldblu1Model)staged.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel();
            var source=new StringBuilder();
            for(var record:PsseDyrRecordReader.read(temp.resolve("low.dyr")))
                if(!record.canonicalModelName().equals("CMLDBLU1"))source.append(String.join(" ",record.fields())).append(" /\n");
            source.append("1 'USRLOD' '*' 'CMLDZNU2' 12 3 2 133 27 146 48 0 0 ");
            for(double constant:original.getLegacyConstants())source.append(constant).append(' ');
            source.append("1 /\n");Files.writeString(temp.resolve("zone.dyr"),source);
            var context=new PSSEMultiFileLoader().loadDStab(temp.resolve("low.raw").toString(),temp.resolve("zone.dyr").toString());
            var parent=context.getDStabilityNet().getDStabBus("Bus2");
            var first=(org.interpss.dstab.dynLoad.impl.Cmldznu2Model)parent.getInfoOnlyDynModel();
            assertEquals(2,first.getSourceModels().size());
            var one=first.getSourceModels().stream().filter(m->m.getTargetLoadIds().contains("1")).findFirst().orElseThrow();
            var two=first.getSourceModels().stream().filter(m->m.getTargetLoadIds().contains("2")).findFirst().orElseThrow();
            var stages=new ArrayList<org.interpss.dstab.relay.LoadSheddingStage>();
            stages.add(new org.interpss.dstab.relay.LoadSheddingStage(1.1,0,0,.25));
            for(int i=0;i<4;i++)stages.add(new org.interpss.dstab.relay.LoadSheddingStage(0,0,0,0));
            var relay=new org.interpss.dstab.relay.Lvs3blRelayModel(parent,wildcard?"#":"1",
                    new org.interpss.dstab.relay.StagedLoadSheddingRelayData(stages,false),null,0,null,0);
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());assertTrue(context.getDStabilityNet().isDStabNetInitialized());
            assertNotSame(one.getLoadBus(),two.getLoadBus());assertNotEquals(one.getLoadBus().getId(),two.getLoadBus().getId());
            assertNotSame(one.get1PhaseACMotor(),two.get1PhaseACMotor());
            assertTrue(parent.getContributeLoadList().isEmpty());assertTrue(relay.afterStep(.001));
            assertEquals(.75,one.getRemainingCapacity(),1e-12);assertEquals(wildcard?.75:1,two.getRemainingCapacity(),1e-12);
            assertTrue(relay.afterStep(.001));assertEquals(wildcard?.75:1,two.getRemainingCapacity(),1e-12);
        }
    }

    @Test void inactiveLoadProtectionIsReportedAfterParameterValidation() throws Exception {
        load(false);Path raw=temp.resolve("low.raw");
        var lines=new ArrayList<>(Files.readAllLines(raw));boolean loads=false;
        for(int i=0;i<lines.size();i++) {
            if(lines.get(i).contains("BEGIN LOAD DATA")){loads=true;continue;}
            if(loads&&lines.get(i).startsWith("0 /"))loads=false;
            if(loads){var fields=lines.get(i).split(",");fields[2]="0";lines.set(i,String.join(",",fields));}
        }
        Files.write(raw,lines);var machines=new StringBuilder();
        for(var record:PsseDyrRecordReader.read(temp.resolve("low.dyr")))
            if(record.canonicalModelName().equals("GENROU")||record.canonicalModelName().equals("GENCLS"))
                machines.append(String.join(" ",record.fields())).append(" /\n");
        for(boolean malformed:new boolean[]{false,true}) {
            Path dyr=temp.resolve("inactive.dyr");Files.writeString(dyr,machines+
                    "2 'LDS3BL' '1' 0 '0' 0 57 .1 0 "+(malformed?"-.25":".25")+" 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 /\n");
            var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(raw.toString(),dyr.toString());
            assertEquals(malformed?0:1,loader.getLastDynamicImportReport().count(org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.SKIPPED_INACTIVE_TARGET));
            assertEquals(!malformed,loader.getLastDynamicImportReport().isStrictlyComplete());
            assertTrue(context.getDStabilityNet().getDStabBus("Bus2").getDynamicBusDeviceList().stream()
                    .noneMatch(org.interpss.dstab.relay.Lds3blRelayModel.class::isInstance));
        }
    }

}
