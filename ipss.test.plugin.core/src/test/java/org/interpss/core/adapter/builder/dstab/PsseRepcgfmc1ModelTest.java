package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.Repcgfmc1NativeContractReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.*;
import com.interpss.dstab.DStabGen;
import com.interpss.common.exp.InterpssException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseRepcgfmc1ModelTest {
    private static String input(){return Repcgfmc1NativeContractReference.profiles().getFirst().input().lines().limit(2).reduce("",(a,s)->a+s+"\n");}
    private static DStabGen gen(DStabNetworkBuilder b){return (DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1");}
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder b,Path dir,String input) throws Exception {
        Path file=dir.resolve("plant.dyr");Files.writeString(file,input);var p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(file.toString());return p;
    }
    @Test void exactAllocationCharacterCircuitAndSourceOrderArePreserved(@TempDir Path dir) throws Exception {
        String[] lines=input().lines().toArray(String[]::new);
        for(String text:List.of(input(),lines[1]+"\n"+lines[0]+"\n")){
            var b=DStabBuilderTestFixture.createBuilder();var p=parse(b,dir,text);
            assertTrue(p.getLastImportReport().isStrictlyComplete());var m=assertInstanceOf(Regfmc1Model.class,gen(b).getDynamicGenDevice());
            assertNotNull(m.getPlantController());var d=m.getPlantController().getData();assertEquals(54,d.constants().length);
            assertEquals(3,d.frequencyBus());assertEquals(2,d.branchFrom());assertEquals(3,d.branchTo());assertEquals("1",d.circuit());
            assertEquals(.006,d.value(Repcgfmc1Data.Parameter.RLOSS));assertEquals(0,d.value(Repcgfmc1Data.Parameter.PLNTMVA));
            assertTrue(m.initStates(m.getDStabBus()));assertEquals(26,m.getNamedStates().size());
            assertTrue(m.getNamedStates().containsKey("REPCGFMC1U.FrequencyMeasured"));
        }
    }
    @Test void allAllocationFieldsCardinalityAndCharacterTypingAreStrict(@TempDir Path dir) throws Exception {
        String row=input().lines().filter(s->s.contains("'REPCGFMC1U'")).findFirst().orElseThrow();
        String genRow=input().lines().findFirst().orElseThrow()+"\n";
        for(int i=0;i<6;i++){
            var fields=new ArrayList<>(PsseDyrRecordReader.tokenize(row));fields.removeLast();fields.set(4+i,"999");fields.set(13,"'1'");
            assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,genRow+String.join(" ",fields)+" /"));
        }
        for(String bad:List.of(row.replace(" 107 0 7 54 15 20 "," "),row.replace(" '1' 1 1 0 "," 1 1 1 0 "),
                row.replace("0.006","NaN"),row.replace("REPCGFMC1U","REPCGFMC1"),row.replace(" 3 2 3 "," 3.5 2 3 "),row.replace(" /"," 0 /"),row.replace(" 10 0 /"," 10 /")))
            assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,genRow+bad));
    }
    @Test void duplicateWrongHostAndRemovalDoNotOverwrite(@TempDir Path dir) throws Exception {
        String plant=input().lines().filter(s->s.contains("'REPCGFMC1U'")).findFirst().orElseThrow();
        assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,plant));
        assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createWithMachine(),dir,plant));
        var b=DStabBuilderTestFixture.createBuilder();parse(b,dir,input());var owner=(Regfmc1Model)gen(b).getDynamicGenDevice();var original=owner.getPlantController();
        assertThrows(InterpssException.class,()->parse(b,dir,plant));assertSame(original,owner.getPlantController());
        var left=DStabBuilderTestFixture.createBuilder();var right=DStabBuilderTestFixture.createBuilder();String converterRow=input().lines().findFirst().orElseThrow();
        parse(left,dir,converterRow);parse(right,dir,converterRow);var l=(Regfmc1Model)gen(left).getDynamicGenDevice();var r=(Regfmc1Model)gen(right).getDynamicGenDevice();
        assertThrows(IllegalArgumentException.class,()->l.setPlantController(new Repcgfmc1Model(original.getData(),r)));assertNull(l.getPlantController());
        for(boolean gnet:new boolean[]{true,false}){
            var removed=DStabBuilderTestFixture.createBuilder();var parser=new PSSEDStabDirectParser(removed).setStrictImport(true);
            var keys=Set.of(new org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey("Bus1","1"));
            if(gnet)parser.setGnetRemovedGenerators(keys);else parser.setModelRemovedGenerators(keys);
            Path file=dir.resolve("removed.dyr");Files.writeString(file,input());parser.parseDynFile(file.toString());assertNull(gen(removed).getDynamicGenDevice());
        }
    }
    @Test void branchDirectionFrequencyFallbackAndPlantBaseAreExplicit(@TempDir Path dir) throws Exception {
        for(String profile:List.of("branch-flat","base-flat","fallback-flat")){
            var reference=Repcgfmc1NativeContractReference.profiles().stream().filter(p->p.name().equals(profile)).findFirst().orElseThrow();
            Path file=dir.resolve(profile+".dyr");Files.writeString(file,reference.input());
            var ctx=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw",file.toString());var net=ctx.getDStabilityNet();net.setAllowGenWithoutMach(true);
            var algo=ctx.getDynSimuAlgorithm();algo.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(algo.getAclfAlgorithm().loadflow());assertTrue(algo.initialization());
            var owner=assertInstanceOf(Regfmc1Model.class,((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());var m=owner.getPlantController();
            assertEquals(profile.equals("fallback-flat"),m.isBranchFallback());assertEquals(profile.equals("fallback-flat")?"Bus1":"Bus2",m.getRemoteBusId());assertEquals("Bus3",m.getFrequencyBusId());
            var commands=m.commands();assertEquals(0,commands.active(),1e-12);assertEquals(0,commands.reactive(),1e-12);assertEquals(0,commands.voltage(),1e-12);assertEquals(0,commands.speedDeviation(),1e-12);
            var before=owner.getNamedStates();for(int i=0;i<20;i++){owner.getOutputObject();m.commands();}assertEquals(before,owner.getNamedStates());
            if(profile.equals("branch-flat")){
                var refs=m.getKernel().references();m.getKernel().setReferences(new Repcgfmc1Kernel.References(refs.frequency(),refs.activeBranch(),refs.reactiveBranch(),refs.voltage(),0,.01));
                double oldVoltage=owner.getKernel().getNamedStates().get("GfmVrefMeas");double h=.0005;
                assertTrue(owner.nextStep(h,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
                double predictedPlant=m.getKernel().states()[5];assertTrue(predictedPlant>oldVoltage);
                assertEquals(oldVoltage,owner.getKernel().getNamedStates().get("GfmVrefMeas"),1e-14);assertEquals(0,m.getKernel().time());
                assertTrue(owner.nextStep(h,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,1));
                assertEquals(oldVoltage+.5*h*(predictedPlant-oldVoltage)/.025,owner.getKernel().getNamedStates().get("GfmVrefMeas"),1e-14);
                assertEquals(h,m.getKernel().time());
            }
            if(profile.equals("base-flat")){
                var refs=m.getKernel().references();double h=.0005,pref=refs.activeBranch();
                m.getKernel().setReferences(new Repcgfmc1Kernel.References(refs.frequency(),pref,refs.reactiveBranch(),refs.voltage(),.02,0));
                assertTrue(owner.nextStep(h,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
                double expected=2*h*(.02+.006*(Math.pow(pref+.02,2)-pref*pref))/.05;
                assertEquals(expected,m.commands().active(),1e-12,"Plant 100 MVA to converter 50 MVA, once");
                assertTrue(owner.nextStep(h,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,1));
            }
        }
    }
    @Test void receivingEndAndMissingFrequencyBusResolveWithoutDoubleBaseConversion(@TempDir Path dir) throws Exception {
        for(String frequency:List.of("0","9999")){
            String text=Repcgfmc1NativeContractReference.profiles().getFirst().input().replace("20 3 2 3 '1'","20 "+frequency+" -2 1 '1'");
            Path file=dir.resolve("direction.dyr");Files.writeString(file,text);
            var ctx=new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw",file.toString());
            var net=ctx.getDStabilityNet();net.setAllowGenWithoutMach(true);var algo=ctx.getDynSimuAlgorithm();algo.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(algo.getAclfAlgorithm().loadflow());assertTrue(algo.initialization());
            var owner=(Regfmc1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();var plant=owner.getPlantController();
            assertTrue(plant.getData().arrivingFlow());assertEquals("Bus2",plant.getRemoteBusId());assertEquals("Bus2",plant.getFrequencyBusId());
            var branch=(com.interpss.core.aclf.AclfBranch)net.getBranch("Bus1","Bus2","1");
            Complex expected=branch.powerTo2From(org.interpss.numeric.datatype.Unit.UnitType.PU).negate();
            assertEquals(expected.getReal(),plant.measurement().branchActive(),1e-12);assertEquals(expected.getImaginary(),plant.measurement().branchReactive(),1e-12);
            net.getBus("Bus2").setFreq(.998);assertEquals(.998,plant.measurement().frequency());
            branch.setBranchCode(com.interpss.core.aclf.AclfBranchCode.XFORMER);branch.setFromTurnRatio(1.1);branch.setToTurnRatio(1);
            Complex tapped=branch.powerTo2From(org.interpss.numeric.datatype.Unit.UnitType.PU).negate();
            assertTrue(tapped.subtract(expected).abs()>1e-5);assertEquals(tapped.getReal(),plant.measurement().branchActive(),1e-12);assertEquals(tapped.getImaginary(),plant.measurement().branchReactive(),1e-12);
        }
    }
}
