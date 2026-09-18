package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Public hydro-generator SMIB initialization and three-cycle-fault tests. */
public class GensalHydroSmibTest {
    private static final Path CASE = Path.of(
            "testData", "adpter", "psse", "v33", "SMIB");
    private static final String MACHINE_ID = "Bus1-mach1";

    @Test
    void initializesAndHoldsSteadyWithoutDisturbance() throws Exception {
        Simulation result = run(false, 1.0);

        assertTrue(result.machine instanceof SalientPoleMachine,
                "GENSAL must attach the shared core salient-pole machine");
        assertEquals(1.0, first(result.speed), 1.0e-10, "initial speed");
        assertTrue(span(result.speed) < 1.0e-7, "one-second speed drift");
        assertTrue(span(result.bus1Voltage) < 1.0e-6, "one-second Bus1 voltage drift");
        assertTrue(span(result.bus2Voltage) < 1.0e-6, "one-second Bus2 voltage drift");
        assertFinite(result);
    }

    @Test
    void remainsStableThroughThreeCycleTerminalFault() throws Exception {
        Simulation result = run(true, 5.0);

        assertFinite(result);
        assertTrue(min(result.bus1Voltage) < 0.05,
                "a bolted terminal fault must collapse terminal voltage");
        assertTrue(max(result.speed) - min(result.speed) > 1.0e-4,
                "the GENSAL rotor must respond to the fault");
        assertTrue(Math.abs(last(result.speed) - 1.0) < 5.0e-3,
                "machine remains synchronously stable after clearing");
        assertTrue(last(result.bus1Voltage) > 0.90,
                "terminal voltage recovers after clearing");
    }

    private static Simulation run(boolean fault, double endTime) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_gensal.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "SMIB load flow");

        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.001);
        algorithm.setTotalSimuTimeSec(endTime);
        algorithm.setOutPutPerSteps(1);
        if (fault) {
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                    "Bus1", network, SimpleFaultCode.GROUND_3P,
                    new Complex(0.0, 1.0e-4), null, 1.0, 3.0 / 60.0),
                    "ThreeCycleFault@Bus1");
        }

        StateMonitor monitor = new StateMonitor();
        monitor.addGeneratorStdMonitor(new String[] {MACHINE_ID});
        monitor.addBusStdMonitor(new String[] {"Bus1", "Bus2"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(), "GENSAL SMIB initialization");
        assertTrue(algorithm.performSimulation(), "GENSAL SMIB simulation");

        var machine = (SalientPoleMachine) network.getMachine(MACHINE_ID);
        assertEquals(100.0, machine.getRating(UnitType.mVA, network.getBaseKva()),
                1.0e-12, "machine MVA base");
        assertEquals(0.089, machine.getXd11(), 1.0e-12, "GENSAL Xd''");
        return new Simulation(machine,
                monitor.getMachSpeedTable().get(MACHINE_ID),
                monitor.getBusVoltTable().get("Bus1"),
                monitor.getBusVoltTable().get("Bus2"));
    }

    private static void assertFinite(Simulation result) {
        assertTrue(result.speed.values().stream().allMatch(record -> Double.isFinite(record.value)));
        assertTrue(result.bus1Voltage.values().stream().allMatch(record -> Double.isFinite(record.value)));
        assertTrue(result.bus2Voltage.values().stream().allMatch(record -> Double.isFinite(record.value)));
    }

    private static double first(Hashtable<Integer, MonitorRecord> records) {
        return records.get(0).value;
    }

    private static double last(Hashtable<Integer, MonitorRecord> records) {
        return records.get(records.size() - 1).value;
    }

    private static double min(Hashtable<Integer, MonitorRecord> records) {
        return records.values().stream().mapToDouble(record -> record.value).min().orElseThrow();
    }

    private static double max(Hashtable<Integer, MonitorRecord> records) {
        return records.values().stream().mapToDouble(record -> record.value).max().orElseThrow();
    }

    private static double span(Hashtable<Integer, MonitorRecord> records) {
        return max(records) - min(records);
    }

    private record Simulation(
            SalientPoleMachine machine,
            Hashtable<Integer, MonitorRecord> speed,
            Hashtable<Integer, MonitorRecord> bus1Voltage,
            Hashtable<Integer, MonitorRecord> bus2Voltage) {
    }
}
