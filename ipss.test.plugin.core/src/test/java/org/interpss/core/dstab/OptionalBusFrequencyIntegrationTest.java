package org.interpss.core.dstab;

import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.DStabObjectFactory;
import org.apache.commons.math3.complex.Complex;

/** Required measurements preserve coupled fault trajectories and can be restored on reinitialization. */
class OptionalBusFrequencyIntegrationTest {
    @Test void suppliedCaseRetainsLocalRemoteAndOutputConsumers() throws Exception {
        var root=java.nio.file.Path.of("testData/private/texas7k");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(root.resolve("Texas7k_20210804.dyr")));
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new PSSEMultiFileLoader().loadDStab(root.resolve("Texas7k_20210804.RAW").toString(),root.resolve("Texas7k_20210804.dyr").toString());
        var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();var lf=algorithm.getAclfAlgorithm();
        lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);lf.getDataCheckConfig().setTurnOffIslandBus(true);
        lf.setNonDivergent(true);lf.setMaxIterations(50);lf.setTolerance(.001);assertTrue(lf.loadflow());
        network.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I);
        network.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        network.setStaticLoadIncludedInYMatrix(false);network.setStaticLoadZPartInYMatrix(true);
        algorithm.setSimuStepSec(1./240);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var report=((DefaultDynamicSimulationSolver)algorithm.getSolver()).getBusFrequencyReport();
        assertEquals(6717,report.activeBuses());assertEquals(704,report.measuredBuses(),report.reason());
    }
    private List<double[]> run(DefaultDynamicSimulationSolver.BusFrequencyMode mode) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        String root="testData/adpter/psse/v33/renewable/";
        var context=new PSSEMultiFileLoader().loadDStab(root+"regca_zero_q_lossless.raw",root+"regca_iolim_native.dyr");
        var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();
        var solver=assertInstanceOf(DefaultDynamicSimulationSolver.class,algorithm.getSolver());solver.setBusFrequencyMode(mode);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.00025);algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        var monitor=new StateMonitor();monitor.addBusStdMonitor(new String[]{"Bus1"});algorithm.setSimuOutputHandler(monitor);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,.05,.05),"fault");
        assertTrue(algorithm.initialization());
        assertEquals(mode==DefaultDynamicSimulationSolver.BusFrequencyMode.ALL?3:2,solver.getBusFrequencyReport().measuredBuses());
        var converter=(org.interpss.dstab.renewable.Regca1Model)((com.interpss.dstab.DStabGen)
                network.getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var result=new ArrayList<double[]>();
        for(int i=0;i<2000;i++) {
            assertTrue(algorithm.solveDEqnStep(true));
            result.add(new double[]{network.getDStabBus("Bus1").getVoltageMag(),network.getDStabBus("Bus2").getVoltageMag(),
                    network.getDStabBus("Bus3").getVoltageMag(),network.getDStabBus("Bus1").getFreq(),
                    network.getDStabBus("Bus3").getFreq(),converter.getIp(),converter.getIq()});
        }
        if(mode==DefaultDynamicSimulationSolver.BusFrequencyMode.REQUIRED) {
            assertNull(network.getDStabBus("Bus2").getBusFreqMeasureBlock());
            monitor.addBusStdMonitor(new String[]{"Bus2"});
            assertThrows(com.interpss.dstab.common.DStabSimuException.class,
                    ()->solver.beforeStep(algorithm.getSimuTime()));
            solver.setBusFrequencyMode(DefaultDynamicSimulationSolver.BusFrequencyMode.ALL);
            assertTrue(algorithm.initialization());
            assertNotNull(network.getDStabBus("Bus2").getBusFreqMeasureBlock());
            assertEquals(3,solver.getBusFrequencyReport().measuredBuses());
        }
        return result;
    }
    @Test void converterFaultAndRequiredFrequencyHistoriesMatchEveryStepExactly() throws Exception {
        var all=run(DefaultDynamicSimulationSolver.BusFrequencyMode.ALL);var required=run(DefaultDynamicSimulationSolver.BusFrequencyMode.REQUIRED);
        assertEquals(all.size(),required.size());
        for(int i=0;i<all.size();i++)assertArrayEquals(all.get(i),required.get(i),0.,"step "+i);
    }

    private List<double[]> runDualPss(DefaultDynamicSimulationSolver.BusFrequencyMode mode,boolean remote,
            java.nio.file.Path folder,boolean pss2b) throws Exception {
        java.nio.file.Files.createDirectory(folder);
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var base=java.nio.file.Path.of("testData/adpter/psse/v33/SMIB");
        String raw=java.nio.file.Files.readString(base.resolve("SMIB_v33.raw"));
        raw=raw.replace("0 / END OF BUS DATA","3,'PASSIVE',100.0000,1,1,1,1,1.0000,0.0000,1.1000,0.9000,1.1000,0.9000\n0 / END OF BUS DATA");
        raw=raw.replace("0 / END OF LOAD DATA","3,'1',1,1,1,20.000,10.000,0.000,0.000,0.000,0.000,1,1,0\n0 / END OF LOAD DATA");
        raw=raw.replace("0 / END OF BRANCH DATA","2,3,'1',0.01000,0.10000,0.00000,0.00,0.00,0.00,0.00000,0.00000,0.00000,0.00000,1,1,0.00,1,1.0000\n0 / END OF BRANCH DATA");
        var file=folder.resolve("three-bus.raw");java.nio.file.Files.writeString(file,raw);
        var dynamic=base.resolve("SMIB_v33_genrou_esst1a_pss2a.dyr");
        if(pss2b) {
            String record=java.nio.file.Files.readAllLines(java.nio.file.Path.of("testData/adpter/psse/v33/HspModels/pss2b.dyr"))
                    .stream().filter(line->line.contains("'PSS2B'")).findFirst().orElseThrow();
            String text=java.nio.file.Files.readString(dynamic).replaceAll("(?m)^.*'PSS2A'.*$",java.util.regex.Matcher.quoteReplacement(record));
            dynamic=folder.resolve("pss2b.dyr");java.nio.file.Files.writeString(dynamic,text);
        }
        var context=new PSSEMultiFileLoader().loadDStab(file.toString(),dynamic.toString());
        var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();
        var solver=assertInstanceOf(DefaultDynamicSimulationSolver.class,algorithm.getSolver());solver.setBusFrequencyMode(mode);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var machine=network.getMachine("Bus1-mach1");
        var pss=assertInstanceOf(com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer.class,machine.getStabilizer());
        org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizerData data;
        if(pss instanceof org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizer model) {
            model.setInputSignalBuses(network.getDStabBus(remote?"Bus3":"Bus1"),network.getDStabBus("Bus1"));data=model.getData();
        } else {
            var model=(org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer)pss;
            model.setInputSignalBuses(network.getDStabBus(remote?"Bus3":"Bus1"),network.getDStabBus("Bus1"));data=model.getData();
        }
        data.setIcs1(5);data.setIcs2(1);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(1./240);
        var monitor=new StateMonitor();monitor.addBusStdMonitor(new String[]{"Bus1"});algorithm.setSimuOutputHandler(monitor);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3",network,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,.1,.05),"fault");
        assertTrue(algorithm.initialization());
        assertEquals(mode==DefaultDynamicSimulationSolver.BusFrequencyMode.ALL||remote?3:2,solver.getBusFrequencyReport().measuredBuses(),solver.getBusFrequencyReport().reason());
        assertEquals(mode==DefaultDynamicSimulationSolver.BusFrequencyMode.REQUIRED&&!remote,network.getDStabBus("Bus3").getBusFreqMeasureBlock()==null);
        var result=new ArrayList<double[]>();
        for(int tick=0;tick<120;tick++) {
            if(tick==10)data.setIcs1(2);
            assertTrue(algorithm.solveDEqnStep(true));
            var values=new ArrayList<Double>();
            for(var bus:network.getBusList())values.add(bus.getVoltageMag());
            values.add(network.getDStabBus("Bus1").getFreq());values.add(network.getDStabBus("Bus2").getFreq());
            if(remote)values.add(network.getDStabBus("Bus3").getFreq());
            values.add(machine.getAngle());values.add(machine.getSpeed());values.add(machine.getPe());values.add(machine.getPm());values.add(machine.getEfd());
            values.addAll(new TreeMap<>(pss.getNamedStates()).values());
            result.add(values.stream().mapToDouble(Double::doubleValue).toArray());
        }
        return result;
    }

    @Test void pss2aFaultAndLiveFrequencySelectorsRetainExactStatesAndHistories(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        for(boolean pss2b:new boolean[]{false,true})for(boolean remote:new boolean[]{false,true}) {
            var all=runDualPss(DefaultDynamicSimulationSolver.BusFrequencyMode.ALL,remote,folder.resolve("all-"+remote+"-"+pss2b),pss2b);
            var required=runDualPss(DefaultDynamicSimulationSolver.BusFrequencyMode.REQUIRED,remote,folder.resolve("required-"+remote+"-"+pss2b),pss2b);
            for(int tick=0;tick<all.size();tick++)assertArrayEquals(all.get(tick),required.get(tick),0.,"remote="+remote+" step="+tick);
        }
    }
}
