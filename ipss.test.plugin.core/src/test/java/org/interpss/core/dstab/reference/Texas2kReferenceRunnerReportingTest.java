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
        Map<String,Double> connected=record(net,event);
        assertEquals(Math.toDegrees(machine.getAngle()),connected.get("machine_ANGLE_deg__bus_1__id_1"));
        assertNotEquals(0.,machine.getEfd());

        event.add("nearby_generators",JsonParser.parseString("[]").getAsJsonArray());
        assertEquals(connected,record(net,event),"diagnostic observations override the scoring subset");
        double angle=machine.getAngle(),speed=machine.getSpeed(),efd=machine.getEfd(),pm=machine.getPm();
        machine.setStatus(false);
        var disconnected=record(net,event);
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
        assertEquals(connected,record(net,event));
        event.remove("diagnostic_generators");
        event.add("nearby_generators",generators);
        assertEquals(connected,record(net,event),"the original scoring-only observation plan remains valid");
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Double> record(BaseDStabNetwork<?,?> net,JsonObject event) throws Exception {
        var method=Texas2kReferenceRunner.class.getDeclaredMethod("record",BaseDStabNetwork.class,JsonObject.class);
        method.setAccessible(true);
        return (Map<String,Double>)method.invoke(null,net,event);
    }
}
