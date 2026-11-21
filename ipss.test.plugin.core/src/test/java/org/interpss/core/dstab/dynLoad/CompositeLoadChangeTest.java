package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Original-load actions must reach the composite's expanded consuming leaves. */
public class CompositeLoadChangeTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sourceOwnerChangesExpandedLoad(boolean staggered)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var path=Cmldblu1SharedSmibTest.CASE;
        var context=new PSSEMultiFileLoader().loadDStab(path.resolve("load-network.raw").toString(),path.resolve("cmldblu1-mixed.dyr").toString());
        var a=context.getDynSimuAlgorithm();
        var unrelatedShunt=new org.apache.commons.math3.complex.Complex(.002,.003);
        a.getNetwork().getDStabBus("Bus2").setShuntY(unrelatedShunt);
        var unrelatedLoad=com.interpss.dstab.DStabObjectFactory.createDStabLoad("outside");
        unrelatedLoad.setCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);
        unrelatedLoad.setLoadCP(new org.apache.commons.math3.complex.Complex(.2,.04));unrelatedLoad.setStatus(true);
        a.getNetwork().getDStabBus("Bus2").getContributeLoadList().add(unrelatedLoad);
        assertTrue(a.getNetwork().getDStabBus("Bus2").initContributeLoad(false));
        assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.1);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(staggered)a.setSolver(new StaggeredEulerSolver(a));
        assertTrue(a.initialization());
        var model=(Cmldblu1Model)a.getNetwork().getDStabBus("Bus2").getInfoOnlyDynModel();
        assertEquals(java.util.Set.of("1"),model.getTargetLoadIds());
        assertTrue(model.ownsCapacityLoad(model.getInductionMotorA()));
        assertTrue(model.ownsCapacityLoad(model.get1PhaseACMotor()));
        assertFalse(model.ownsCapacityLoad(null));
        assertFalse(model.ownsCapacityLoad(new org.interpss.dstab.dynLoad.impl.LD1PACImpl()));
        for(var leaf:model.getLoadBus().getDynLoadModelList())assertTrue(model.ownsCapacityLoad(leaf));
        var feeder=a.getNetwork().getBranchList().stream().filter(b->b.getFromBus()==model.getLowVoltBus()
                && b.getToBus()==model.getLoadBus()).findFirst().orElseThrow();
        var originalZ=feeder.getZ();var transformerZ=model.getDistributionTransformer().getZ();
        var lowB=model.getLowVoltBus().getShuntY();var farB=model.getLoadBus().getShuntY();
        double bss=model.getData().value(1)*.4/Math.abs(model.getData().value(0));
        var parent=model.getDStabBus();
        model.setDStabBus(a.getNetwork().getDStabBus("Bus1"));
        assertFalse(model.canChangeLoad("1",-.25));assertFalse(model.changeLoad(-.25));
        assertEquals(originalZ,feeder.getZ());model.setDStabBus(parent);
        assertTrue(model.canPrepareLoadChange("1",-.25));
        assertTrue(model.canPrepareLoadChange("1",-1));
        var foreign=com.interpss.dstab.DStabObjectFactory.createDStabBranch(
                model.getLoadBus().getId(),"Bus1",a.getNetwork());
        foreign.setZ(new org.apache.commons.math3.complex.Complex(.01,.1));foreign.setStatus(true);
        assertFalse(model.canPrepareLoadChange("1",-1),"Live foreign adjacency prevents disconnecting an internal bus");
        foreign.setStatus(false);
        assertTrue(model.canPrepareLoadChange("1",-1),"An inactive foreign branch does not block removal");
        foreign.setStatus(true);
        assertFalse(model.canPrepareLoadChange("1",-1),"Eligibility observes reactivation without a cached result");
        foreign.setStatus(false);
        var preparedMatrix=a.getNetwork().getYMatrix();
        a.getNetwork().setYMatrix(null);
        try {
            assertTrue(model.canPrepareLoadChange("1",-1));
            assertFalse(model.canChangeLoad("1",-1));assertFalse(model.changeLoad(-1));
        } finally { a.getNetwork().setYMatrix(preparedMatrix); }
        assertFalse(model.canPrepareLoadChange("other",-.25));
        assertTrue(model.canChangeLoad("1",-.25));assertTrue(model.canChangeLoad("*",-.25));
        assertFalse(model.canChangeLoad("other",-.25));
        assertFalse(model.canChangeLoad(-1.1));assertFalse(model.changeLoad(-1.1));
        assertFalse(model.changeLoad(Double.NaN));assertFalse(model.changeLoad(.1));
        assertEquals(originalZ,feeder.getZ());assertEquals(1,model.getRemainingCapacity(),0);
        assertTrue(model.changeLoad(-.25),"Source owner must accept and delegate physical load change");
        assertEquals(.75,model.getInductionMotorA().getFonline(),1e-12);
        assertEquals(.75,model.getInductionMotorB().getFonline(),1e-12);
        assertEquals(.75,model.getInductionMotorC().getFonline(),1e-12);
        assertEquals(0,feeder.getZ().subtract(originalZ.divide(.75)).abs(),1e-14);
        assertEquals(transformerZ,model.getDistributionTransformer().getZ());
        assertEquals(bss+.75*(lowB.getImaginary()-bss),model.getLowVoltBus().getShuntY().getImaginary(),1e-12);
        assertEquals(0,model.getLoadBus().getShuntY().subtract(farB.multiply(.75)).abs(),1e-14);
        assertTrue(model.changeLoad(-.25));assertEquals(.5,model.getRemainingCapacity(),1e-14);
        assertEquals(0,feeder.getZ().subtract(originalZ.divide(.5)).abs(),1e-14);
        assertEquals(.5,model.getInductionMotorA().getFonline(),1e-12);
        assertFalse(model.changeLoad(-.6));assertEquals(.5,model.getRemainingCapacity(),1e-14);
        var matrix=a.getNetwork().getYMatrix();
        int lowRow=model.getLowVoltBus().getSortNumber(), loadRow=model.getLoadBus().getSortNumber();
        var lowDiagonal=matrix.getA(lowRow,lowRow);var loadDiagonal=matrix.getA(loadRow,loadRow);
        a.getNetwork().formYMatrix4DStab();
        assertEquals(0,a.getNetwork().getYMatrix().getA(lowRow,lowRow).subtract(lowDiagonal).abs(),1e-12,"Rebuild preserves low-side capacity stamp");
        assertEquals(0,a.getNetwork().getYMatrix().getA(loadRow,loadRow).subtract(loadDiagonal).abs(),1e-12,"Rebuild preserves load-side capacity stamp");
        for(int i=0;i<20;i++)assertTrue(a.solveDEqnStep(true));
        var load=model.getLoadBus();var voltage=load.getVoltage();
        var current=org.apache.commons.math3.complex.Complex.ZERO;
        for(var leaf:load.getDynLoadModelList())if(leaf.isActive())
            current=current.add(leaf.getPosSeqEquivY().multiply(voltage).subtract(leaf.getNortonCurInj()));
        current=current.add(load.getShuntY().multiply(voltage));
        var incoming=model.getLowVoltBus().getVoltage().subtract(voltage).divide(feeder.getZ());
        assertTrue(incoming.subtract(current).abs()<1e-4,"Expanded leaf and physical shunt current balance");
        var parentVoltage=parent.getVoltage();var frozenLow=model.getLowVoltBus().getVoltage();var frozenLoad=load.getVoltage();
        var motorState=model.getInductionMotorA().getEp();
        assertTrue(model.canChangeLoad("1",-.5));assertTrue(model.changeLoad(-.5));
        assertEquals(0,model.getRemainingCapacity(),0);assertTrue(parent.isActive());
        assertEquals(unrelatedShunt,parent.getShuntY(),"Unrelated parent shunt survives full composite disconnection");
        assertSame(unrelatedLoad,parent.getContributeLoad("outside"));assertTrue(unrelatedLoad.isActive());
        assertEquals(new org.apache.commons.math3.complex.Complex(.2,.04),unrelatedLoad.getLoadCP());
        assertFalse(model.getLowVoltBus().isActive());assertFalse(load.isActive());
        assertFalse(feeder.isActive());assertFalse(model.getDistributionTransformer().isActive());
        assertFalse(model.changeLoad(.1));assertFalse(model.changeLoad(-.1));
        var parentDiagonal=a.getNetwork().getYMatrix().getA(parent.getSortNumber(),parent.getSortNumber());
        assertEquals(org.apache.commons.math3.complex.Complex.ONE,a.getNetwork().getYMatrix().getA(lowRow,lowRow));
        a.getNetwork().formYMatrix4DStab();
        assertEquals(0,a.getNetwork().getYMatrix().getA(parent.getSortNumber(),parent.getSortNumber()).subtract(parentDiagonal).abs(),1e-12);
        for(int i=0;i<40;i++)assertTrue(a.solveDEqnStep(true));
        assertEquals(frozenLow,model.getLowVoltBus().getVoltage());assertEquals(frozenLoad,load.getVoltage());
        assertEquals(motorState,model.getInductionMotorA().getEp(),"Disconnected differential state remains frozen");
        assertTrue(parent.getVoltage().subtract(parentVoltage).abs()>1e-5,"External network keeps solving");
    }
    @org.junit.jupiter.api.Test
    void independentCaptureIdentifiesWhichDistributionElementsScale()throws Exception {
        var archive=java.nio.file.Path.of("testData/private/verification-artifacts/cmldblu1-shedding-20261104/captures.zip");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(archive),"Retained independent shedding captures required");
        assertEquals("09a82dd77febe6dceb050ef837b24da9f82c3def9a80d5ac726d947a22feadf9",java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(archive))));
        try(var zip=new java.util.zip.ZipFile(archive.toFile())) {
            for(String fraction:new String[]{"0.25","0.0"}) {
                String prefix="target/cmld-shedding-"+fraction+"-20261104/cmldblu1-restart-fault-h0.0005-";
                byte[] first=zip.getInputStream(zip.getEntry(prefix+"a-part0/native.csv")).readAllBytes();
                assertArrayEquals(first,zip.getInputStream(zip.getEntry(prefix+"b-part0/native.csv")).readAllBytes(),"Cold complete traces");
                String[] lines=new String(first,java.nio.charset.StandardCharsets.UTF_8).strip().split("\\R");
                var columns=new java.util.HashMap<String,Integer>();String[] keys=lines[0].split(",");
                for(int k=0;k<keys.length;k++)columns.put(keys[k],k);
                double[] initial=java.util.Arrays.stream(lines[1].split(",")).mapToDouble(Double::parseDouble).toArray();
                int scaled=0;
                for(int j=1;j<lines.length;j++) {
                    double[] row=java.util.Arrays.stream(lines[j].split(",")).mapToDouble(Double::parseDouble).toArray();
                    double ratio=row[columns.get("VAR7")]/initial[columns.get("VAR7")];
                    assertEquals(ratio> .9?1: .75,ratio,2e-7);
                    for(String k:new String[]{"VAR8","VAR22","VAR23","VAR55","VAR56"})
                        assertEquals(initial[columns.get(k)]*ratio,row[columns.get(k)],2e-7,k+" proportional capacity");
                    for(String k:new String[]{"VAR18","VAR19"})assertEquals(initial[columns.get(k)]/ratio,row[columns.get(k)],2e-7,k+" inverse capacity");
                    for(String k:new String[]{"VAR12","VAR21","VAR24","VAR40","VAR41"})assertEquals(initial[columns.get(k)],row[columns.get(k)],0,k+" unchanged");
                    if(ratio<.9)scaled++;
                }
                assertEquals(1007,lines.length,"All startup and event-side rows retained");
                if(fraction.equals("0.25"))assertTrue(scaled>800);else assertEquals(0,scaled);
            }
        }
    }

}

