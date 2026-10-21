package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

class Texas2kReferenceRunnerReportingTest {
    @Test void numericalPolicyIsExplicitAndReportedWithoutSourceRewriting() {
        var plan = JsonParser.parseString("""
                {"network_voltage_tolerance":0.0001,"network_convergence_norm":"COMPLEX_MAGNITUDE",
                 "minimum_network_iterations":1,"reuse_network_endpoint":true,
                 "static_load_z_part_in_y_matrix":true,"converter_network_voltage_memory":true,
                 "machine_measurement_stage":"LAST_NETWORK_INJECTION"}
                """).getAsJsonObject();
        var options = Texas2kNumericalOptions.read(plan);
        var status = new java.util.LinkedHashMap<String,Object>();
        options.report(status);
        assertEquals(1.e-4, status.get("network_voltage_tolerance"));
        assertEquals("LAST_NETWORK_INJECTION", status.get("machine_measurement_stage"));
        assertEquals("COMPLEX_MAGNITUDE", status.get("network_convergence_norm"));
        assertEquals(1, status.get("minimum_network_iterations"));
        assertTrue(options.reuseEndpoint() && options.loadZInMatrix() && options.converterVoltageMemory());
        var defaults = Texas2kNumericalOptions.read(new JsonObject());
        assertEquals(1.e-8, defaults.networkTolerance());
        assertEquals(2, defaults.minimumIterations());
        assertEquals(org.interpss.dstab.algo.PsseStaggeredEulerSolver.MachineMeasurementStage.LAST_NETWORK_INJECTION, defaults.machineStage());
        assertFalse(defaults.reuseEndpoint() || defaults.loadZInMatrix() || defaults.converterVoltageMemory());
    }

    @Test void invalidNumericalPoliciesFailBeforeAReferenceRun() {
        for (double tolerance : new double[]{0., -1., Double.NaN, Double.POSITIVE_INFINITY}) {
            var plan = new JsonObject(); plan.addProperty("network_voltage_tolerance", tolerance);
            assertThrows(IllegalArgumentException.class, () -> Texas2kNumericalOptions.read(plan));
        }
        for (int iterations : new int[]{0, -1, 501}) {
            var plan = new JsonObject(); plan.addProperty("minimum_network_iterations", iterations);
            assertThrows(IllegalArgumentException.class, () -> Texas2kNumericalOptions.read(plan));
        }
        for (String key : new String[]{"machine_measurement_stage", "network_convergence_norm"}) {
            var plan = new JsonObject(); plan.addProperty(key, "UNKNOWN");
            assertThrows(IllegalArgumentException.class, () -> Texas2kNumericalOptions.read(plan));
        }
    }

    @Test
    void reusableCsvRowPreservesDoubleFormattingAndRejectsNonfiniteValues() throws Exception {
        var sample=new Texas2kSelectedRecorder.Sample(new String[]{"zero","small","large"});
        sample.values()[0]=-0.;
        sample.values()[1]=Double.MIN_VALUE;
        sample.values()[2]=Double.MAX_VALUE;
        var text=new java.io.StringWriter();
        try(var writer=new java.io.BufferedWriter(text)) {
            sample.write(writer,1.,"STEP_END");
            sample.values()[0]=.01;
            sample.values()[1]=-1.e-4;
            sample.values()[2]=1.e20;
            sample.write(writer,1.,"EVENT_POST");
            for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
                sample.values()[1]=invalid;
                assertThrows(IllegalStateException.class,()->sample.write(writer,2.,"STEP_END"));
            }
        }
        assertEquals("1.0,STEP_END,-0.0,4.9E-324,1.7976931348623157E308"+System.lineSeparator()
                +"1.0,EVENT_POST,0.01,-1.0E-4,1.0E20"+System.lineSeparator(),text.toString());
    }

    @Test
    void connectedReportingPreservesElectricalCachesAndRetainedPower() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(
                base+"SMIB_v33.raw",base+"SMIB_v33_genrou_esst4b.dyr");
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.01);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var net=context.getDStabilityNet();
        var machine=(com.interpss.dstab.mach.impl.RoundRotorMachineImpl)net.getMachine("Bus1-mach1");
        var electrical=machine.captureRoundElectricalState();
        double retainedPower=machine.getPe();
        var bus=machine.getDStabBus();
        bus.setVoltage(bus.getVoltage().multiply(new org.apache.commons.math3.complex.Complex(1.01,.002)));
        var terminal=bus.getVoltage().multiply(machine.getIxy().conjugate());
        assertNotEquals(electrical,machine.captureRoundElectricalState());
        assertNotEquals(retainedPower,machine.getPe());
        machine.restoreRoundElectricalState(electrical);
        machine.setPe(retainedPower);
        var event=JsonParser.parseString("""
                {"nearby_buses":[1],"nearby_generators":[{"bus":1,"id":"1"}]}
                """).getAsJsonObject();
        var recorder=new Texas2kSelectedRecorder(net,event);
        var firstSample=recorder.sample();
        var observed=values(firstSample);
        assertEquals(electrical,machine.captureRoundElectricalState());
        assertEquals(retainedPower,machine.getPe(),0.);
        assertEquals(retainedPower,observed.get("machine_AIRGAP_pu_mbase__bus_1__id_1"),0.);
        assertEquals(terminal.getReal(),observed.get("machine_PELEC_pu_sbase__bus_1__id_1"),0.);
        assertEquals(terminal.getImaginary(),observed.get("machine_QELEC_pu__bus_1__id_1"),0.);
        assertSame(firstSample,recorder.sample(),"Sample storage is reused within a case");
        assertEquals(observed,values(firstSample));
        assertEquals(electrical,machine.captureRoundElectricalState());
        bus.setVoltage(bus.getVoltage().multiply(new org.apache.commons.math3.complex.Complex(.98,-.003)));
        var changed=Texas2kFullNetworkRecorder.terminalPowerWithoutCacheChanges(machine);
        var refreshed=values(recorder.sample());
        assertNotEquals(observed.get("machine_PELEC_pu_sbase__bus_1__id_1"),refreshed.get("machine_PELEC_pu_sbase__bus_1__id_1"));
        assertEquals(changed.getReal(),refreshed.get("machine_PELEC_pu_sbase__bus_1__id_1"),0.);
        assertEquals(changed.getImaginary(),refreshed.get("machine_QELEC_pu__bus_1__id_1"),0.);
        assertEquals(electrical,machine.captureRoundElectricalState());
        assertEquals(retainedPower,machine.getPe(),0.);
    }

    @Test
    void disconnectedChannelsAreZeroWithoutChangingRetainedMachineStates() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(
                base+"SMIB_v33.raw",base+"SMIB_v33_genrou_esst4b.dyr");
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.01);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        var net=context.getDStabilityNet();
        var machine=net.getMachine("Bus1-mach1");
        var event=JsonParser.parseString("""
                {"nearby_buses":[1],"nearby_generators":[{"bus":1,"id":"1"}]}
                """).getAsJsonObject();
        var generators=event.getAsJsonArray("nearby_generators");
        event.add("diagnostic_generators",generators);
        var recorder=new Texas2kSelectedRecorder(net,event);
        Map<String,Double> connected=values(recorder.sample());
        assertEquals(Math.toDegrees(machine.getAngle()),connected.get("machine_ANGLE_deg__bus_1__id_1"));
        assertNotEquals(0.,machine.getEfd());

        event.add("nearby_generators",JsonParser.parseString("[]").getAsJsonArray());
        assertEquals(connected,record(net,event),"diagnostic observations override the scoring subset");
        event.add("nearby_buses",JsonParser.parseString("[1,1]").getAsJsonArray());
        event.add("diagnostic_generators",JsonParser.parseString("[{\"bus\":1,\"id\":\"1\"},{\"bus\":1,\"id\":\"1\"}]").getAsJsonArray());
        var duplicateSelection=new Texas2kSelectedRecorder(net,event).sample();
        assertEquals(10,duplicateSelection.names().length,"Duplicate selections retain the original unique column order");
        assertEquals(connected,values(duplicateSelection));
        double angle=machine.getAngle(),speed=machine.getSpeed(),efd=machine.getEfd(),pm=machine.getPm();
        machine.setStatus(false);
        var disconnected=values(recorder.sample());
        assertEquals(connected.get("bus_voltage_pu__bus_1"),disconnected.get("bus_voltage_pu__bus_1"));
        assertEquals(connected.get("bus_angle_deg__bus_1"),disconnected.get("bus_angle_deg__bus_1"));
        disconnected.forEach((name,value)-> {
            if(name.startsWith("machine_"))assertEquals(0.,value,0.,name);
        });
        assertEquals(angle,machine.getAngle(),0.);
        assertEquals(speed,machine.getSpeed(),0.);
        assertEquals(efd,machine.getEfd(),0.);
        assertEquals(pm,machine.getPm(),0.);
        machine.setStatus(true);
        assertEquals(connected,values(recorder.sample()));
        event.remove("diagnostic_generators");
        event.add("nearby_generators",generators);
        assertEquals(connected,record(net,event),"the original scoring-only observation plan remains valid");
    }

    private static Map<String,Double> record(BaseDStabNetwork<?,?> net,JsonObject event) throws Exception {
        return values(new Texas2kSelectedRecorder(net,event).sample());
    }

    private static Map<String,Double> values(Texas2kSelectedRecorder.Sample sample) {
        var result=new java.util.LinkedHashMap<String,Double>();
        for(int i=0;i<sample.names().length;i++)result.put(sample.names()[i],sample.values()[i]);
        return result;
    }
}
