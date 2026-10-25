package org.interpss.core.dstab;

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
        var report=((PsseDStabSolver)algorithm.getSolver()).getBusFrequencyReport();
        assertEquals(6717,report.activeBuses());assertEquals(704,report.measuredBuses(),report.reason());
    }
    private List<double[]> run(PsseDStabSolver.BusFrequencyMode mode) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        String root="testData/adpter/psse/v33/renewable/";
        var context=new PSSEMultiFileLoader().loadDStab(root+"regca_zero_q_lossless.raw",root+"regca_iolim_native.dyr");
        var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();
        var solver=assertInstanceOf(PsseDStabSolver.class,algorithm.getSolver());solver.setBusFrequencyMode(mode);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.00025);algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        var monitor=new StateMonitor();monitor.addBusStdMonitor(new String[]{"Bus1"});algorithm.setSimuOutputHandler(monitor);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,.05,.05),"fault");
        assertTrue(algorithm.initialization());
        assertEquals(mode==PsseDStabSolver.BusFrequencyMode.ALL?3:2,solver.getBusFrequencyReport().measuredBuses());
        var converter=(org.interpss.dstab.renewable.Regca1Model)((com.interpss.dstab.DStabGen)
                network.getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var result=new ArrayList<double[]>();
        for(int i=0;i<2000;i++) {
            assertTrue(algorithm.solveDEqnStep(true));
            result.add(new double[]{network.getDStabBus("Bus1").getVoltageMag(),network.getDStabBus("Bus2").getVoltageMag(),
                    network.getDStabBus("Bus3").getVoltageMag(),network.getDStabBus("Bus1").getFreq(),
                    network.getDStabBus("Bus3").getFreq(),converter.getIp(),converter.getIq()});
        }
        if(mode==PsseDStabSolver.BusFrequencyMode.REQUIRED) {
            assertNull(network.getDStabBus("Bus2").getBusFreqMeasureBlock());
            monitor.addBusStdMonitor(new String[]{"Bus2"});
            assertThrows(com.interpss.dstab.common.DStabSimuException.class,
                    ()->solver.beforeStep(algorithm.getSimuTime()));
            solver.setBusFrequencyMode(PsseDStabSolver.BusFrequencyMode.ALL);
            assertTrue(algorithm.initialization());
            assertNotNull(network.getDStabBus("Bus2").getBusFreqMeasureBlock());
            assertEquals(3,solver.getBusFrequencyReport().measuredBuses());
        }
        return result;
    }
    @Test void converterFaultAndRequiredFrequencyHistoriesMatchEveryStepExactly() throws Exception {
        var all=run(PsseDStabSolver.BusFrequencyMode.ALL);var required=run(PsseDStabSolver.BusFrequencyMode.REQUIRED);
        assertEquals(all.size(),required.size());
        for(int i=0;i<all.size();i++)assertArrayEquals(all.get(i),required.get(i),0.,"step "+i);
    }
}
