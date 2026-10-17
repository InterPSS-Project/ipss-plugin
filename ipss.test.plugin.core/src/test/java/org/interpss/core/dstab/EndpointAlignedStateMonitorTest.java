package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Hashtable;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EndpointAlignedStateMonitor;
import org.interpss.core.dstab.reference.EndpointAlignedStateMonitor.SampleKind;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.datatype.DStabSimuEvent;

public class EndpointAlignedStateMonitorTest {
    private static EndpointAlignedStateMonitor monitor() {
        var monitor = new EndpointAlignedStateMonitor("Bus1");
        monitor.addBusStdMonitor(new String[] {"Bus1"});
        emit(monitor, "Bus1", 0, 1);
        return monitor;
    }

    private static void emit(EndpointAlignedStateMonitor monitor, String bus, double time, double voltage) {
        Hashtable<String,Object> data = new Hashtable<>();
        data.put(DStabOutSymbol.OUT_SYMBOL_BUS_ID, bus);
        data.put(DStabOutSymbol.OUT_SYMBOL_TIME, time);
        data.put(DStabOutSymbol.OUT_SYMBOL_BUS_VMAG, voltage);
        data.put(DStabOutSymbol.OUT_SYMBOL_BUS_VANG, 0.0);
        data.put(DStabOutSymbol.OUT_SYMBOL_BUS_FREQ, 1.0);
        data.put(DStabOutSymbol.OUT_SYMBOL_BUS_PLOAD, 0.0);
        data.put(DStabOutSymbol.OUT_SYMBOL_BUS_QLOAD, 0.0);
        assertTrue(monitor.onSimuEvent(new DStabSimuEvent(DStabSimuEvent.PlotStepBusStates, data)));
    }

    @Test void endpointUsesEndTimeAndCallbackStateRatherThanADeferredTimestampJoin() {
        var monitor = monitor();
        double[] live = {1};
        monitor.initializeStateCapture(0, () -> live);
        monitor.beginStep(0, .01);
        live[0] = 2;
        emit(monitor, "Bus1", 0, .9);
        live[0] = 99; // changing live storage after output cannot change the snapshot
        monitor.endStep(.01);
        var frame = monitor.frames().get(1);
        assertEquals(.01, frame.time());
        assertEquals(0, frame.solverTime());
        assertEquals(SampleKind.STEP_END, frame.kind());
        assertArrayEquals(new double[]{2}, frame.states());
        frame.states()[0] = 101;
        assertArrayEquals(new double[]{2}, frame.states());
    }

    @Test void eventPreAndIntegratedEndpointAreDistinctDespiteIdenticalRawLabels() {
        var monitor = monitor();
        double[] live = {3};
        monitor.initializeStateCapture(0, () -> live);
        monitor.beginStep(0, .01);
        emit(monitor, "Bus1", 0, 1);
        live[0] = 4;
        emit(monitor, "Bus1", 0, .01);
        monitor.endStep(.01);
        var frames = monitor.frames();
        assertEquals(3, frames.size());
        assertEquals(SampleKind.EVENT_PRE, frames.get(1).kind());
        assertEquals(0, frames.get(1).time());
        assertArrayEquals(new double[]{3}, frames.get(1).states());
        assertEquals(.01, frames.get(2).time());
        assertArrayEquals(new double[]{4}, frames.get(2).states());
        assertEquals(frames.get(1).solverTime(), frames.get(2).solverTime());
    }

    @Test void rejectsMissingInitializationNoncontiguousStepsAndIncompleteExport() {
        var monitor = monitor();
        assertThrows(IllegalStateException.class, () -> monitor.beginStep(0,.01));
        monitor.initializeStateCapture(0, () -> new double[]{1});
        assertThrows(IllegalStateException.class, () -> monitor.initializeStateCapture(0, () -> new double[0]));
        assertThrows(IllegalStateException.class, () -> monitor.beginStep(.01,.01));
        for (double bad : new double[] {0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalStateException.class, () -> monitor.beginStep(0,bad));
        monitor.beginStep(0,.01);
        assertThrows(IllegalStateException.class, monitor::frames);
        assertThrows(IllegalStateException.class, () -> monitor.endStep(.01));
    }

    @Test void rejectsChangedOutputContractInsteadOfSilentlyShiftingOrDroppingSamples() {
        var monitor = monitor();
        monitor.initializeStateCapture(0, () -> new double[]{1});
        assertThrows(IllegalStateException.class, () -> emit(monitor,"Bus1",0,1));
        monitor.beginStep(0,.01);
        assertThrows(IllegalStateException.class, () -> emit(monitor,"Bus1",.01,1));
        emit(monitor,"Bus1",0,1);
        emit(monitor,"Bus1",0,1);
        assertThrows(IllegalStateException.class, () -> emit(monitor,"Bus1",0,1));
    }

    @Test void rejectsNonfiniteAndChangingStateVectors() {
        var initial = monitor();
        assertThrows(IllegalStateException.class,
                () -> initial.initializeStateCapture(0, () -> new double[]{Double.NaN}));
        var monitor = monitor();
        double[][] live = {{1}};
        monitor.initializeStateCapture(0, () -> live[0]);
        monitor.beginStep(0,.01);
        live[0] = new double[]{1,2};
        assertThrows(IllegalStateException.class, () -> emit(monitor,"Bus1",0,1));
    }

    @Test void fullSolverVoltageAndMachineSpeedMatchSynchronousStateSnapshotsAtFaultEdges() throws Exception {
        IpssCorePlugin.init();
        String root = "testData/adpter/psse/v33/renewable/";
        var context = new PSSEMultiFileLoader().loadDStab(
                root + "regca_reeca_repca_bus1062_weak.raw", root + "regca_reeca_repca_bus1062.dyr");
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var monitor = new EndpointAlignedStateMonitor("Bus2");
        monitor.addBusStdMonitor(new String[]{"Bus2"});
        monitor.addGeneratorStdMonitor(new String[]{"Bus3-mach1"});
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.01);
        algorithm.setTotalSimuTimeSec(.15);
        algorithm.setOutPutPerSteps(1);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",network,
                SimpleFaultCode.GROUND_3P,Complex.ZERO,null,.05,.05),"TimingFault");
        assertTrue(algorithm.initialization());
        monitor.initializeStateCapture(algorithm.getSimuTime(), () -> new double[]{
                network.getBus("Bus2").getVoltageMag(),network.getMachine("Bus3-mach1").getSpeed()});
        for (int step=0; step<15; step++) {
            monitor.beginStep(algorithm.getSimuTime(),.01);
            assertTrue(algorithm.solveDEqnStep(true));
            monitor.endStep(algorithm.getSimuTime());
        }
        var frames = monitor.frames();
        assertEquals(18,frames.size()); // initial + 15 integrated endpoints + 2 event-pre samples
        assertEquals(.15,frames.get(frames.size()-1).time(),1.e-12);
        for (int row=0; row<frames.size(); row++) {
            assertEquals(monitor.getBusVoltTable().get("Bus2").get(row).value,frames.get(row).states()[0],0);
            assertEquals(monitor.getMachSpeedTable().get("Bus3-mach1").get(row).value,frames.get(row).states()[1],0);
        }
        for (double boundary : new double[]{.05,.1}) {
            int before = java.util.stream.IntStream.range(0,frames.size()).filter(i ->
                    frames.get(i).kind()==SampleKind.EVENT_PRE && Math.abs(frames.get(i).time()-boundary)<1.e-9)
                    .findFirst().orElseThrow();
            assertEquals(boundary,frames.get(before).solverTime(),1.e-12);
            assertEquals(boundary+.01,frames.get(before+1).time(),1.e-12);
        }
    }
}
