package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.core.dstab.reference.Wtptb1NativeExtendedReference;
import org.interpss.core.dstab.reference.Wtptb1NativeReference.Profile;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Complete solver and lifecycle coverage; independent network parity is scored separately. */
public class Wtptb1SmibIntegrationTest extends CorePluginTestSetup {
    @Test void flatRunAtBothStepsRetainsTheInitializedPitchAndVoltage() throws Exception {
        for(double step:new double[]{.0005,.00025})run(step,false,false);
    }
    @Test void threeCycleFaultExcitesPitchAndSpeedAtBothSteps() throws Exception {
        double[] coarse=run(.0005,true,false),fine=run(.00025,true,false);
        for(int channel:new int[]{0,1,2,4})assertTrue(fine[channel]<.65*coarse[channel],
                "Pitch/PI/generator-speed channel "+channel+" must improve under step halving");
    }
    @Test void generatorTripFreezesAllPitchStatesAtBothSteps() throws Exception {
        for(double step:new double[]{.0005,.00025})run(step,true,true);
    }
    private static double[] run(double step,boolean fault,boolean trip) throws Exception {
        Path base=Path.of("testData/adpter/psse/v33/renewable");
        var context=new PSSEMultiFileLoader().loadDStab(base.resolve("regca_reeca_repca_bus1062.raw").toString(),
                base.resolve("regca_reeca_wtptb1_bus1062.dyr").toString());
        var net=context.getDStabilityNet();net.setAllowGenWithoutMach(true);
        var algorithm=context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(step);
        algorithm.setTotalSimuTimeSec(1);algorithm.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.05,.05),"WTPTB1-fault");
        if(trip)net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1","1",net,.15),"WTPTB1-trip");
        assertTrue(algorithm.initialization());
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");
        var converter=(Regca1Model)gen.getDynamicGenDevice();
        var stack=converter.getReeca1Controller().getWindControlStack();
        var pitch=stack.getPitchControllerB();assertNotNull(pitch);assertTrue(stack.isComplete());
        assertEquals(pitch.getPitch(),converter.getNamedStates().get("WTPTB1.Pitch Angle"));
        double[] initial=pitch.getNativeStates(),held=null;
        double initialV=net.getBus("Bus1").getVoltageMag(),minV=initialV,maxPitch=0,maxSpeed=0;
        var rows=new ArrayList<String>();
        var observed=new ArrayList<double[]>();
        rows.add("time_s,pitch,speed_integral,comp_integral,shaft,wt_dev,wg_dev,angle_dev,v1,v2,v3,p_pu,q_pu,pord,torque_pref,wref");
        int lastTick=(int)Math.round(1/step)+(fault&&!trip&&step==.00025?1:0);
        for(int tick=0;tick<=lastTick;tick++) {
            if(tick>0)assertTrue(algorithm.solveDEqnStep(true),"Step "+tick);
            double[] states=pitch.getNativeStates(),mechanical=stack.getTorsionalEmulation().getNativeStates();
            for(double state:states)assertTrue(Double.isFinite(state));
            double voltage=net.getBus("Bus1").getVoltageMag();
            if(!fault){assertArrayEquals(initial,states,1e-9);assertEquals(initialV,voltage,1e-9);}
            if(!trip || algorithm.getSimuTime()<.15){
                minV=Math.min(minV,voltage);maxPitch=Math.max(maxPitch,Math.abs(states[0]-initial[0]));
                maxSpeed=Math.max(maxSpeed,Math.abs(mechanical[2]));
            }
            if(trip && algorithm.getSimuTime()>=.16){
                if(held==null)held=states;else assertArrayEquals(held,states,0);
            }
            var signals=converter.getNamedStates();
            observed.add(new double[]{states[0],states[1],states[2],mechanical[1],mechanical[2],stack.getTorqueController().getSpeedReference(),
                    converter.getReeca1Controller().getNamedStates().get("Active Power Order"),voltage,
                    net.getBus("Bus2").getVoltageMag(),net.getBus("Bus3").getVoltageMag(),signals.get("Active Power"),signals.get("Reactive Power")});
            rows.add(algorithm.getSimuTime()+","+states[0]+","+states[1]+","+states[2]+","+mechanical[0]+","+mechanical[1]
                    +","+mechanical[2]+","+mechanical[3]+","+voltage+","+net.getBus("Bus2").getVoltageMag()
                    +","+net.getBus("Bus3").getVoltageMag()+","+signals.get("Active Power")+","+signals.get("Reactive Power")
                    +","+converter.getReeca1Controller().getNamedStates().get("Active Power Order")+","+stack.getPref()
                    +","+stack.getTorqueController().getSpeedReference());
        }
        if(fault){assertTrue(minV<initialV-.001);assertTrue(maxPitch>1e-4);assertTrue(maxSpeed>1e-6);}
        if(trip){assertNotNull(held);assertFalse(gen.isActive());assertFalse(converter.isActive());}
        Path output=Path.of("target","wtptb1-smib");Files.createDirectories(output);
        Files.write(output.resolve((trip?"trip":fault?"fault":"flat")+"-"+step+".csv"),rows);
        return fault&&!trip?assertNativeEnvelope(step,observed):null;
    }
    private static double[] assertNativeEnvelope(double step,List<double[]> observed) {
        Profile reference=Wtptb1NativeExtendedReference.profiles().stream()
                .filter(p->p.name().equals(step==.0005?"fault-coarse":"fault-fine")).findFirst().orElseThrow();
        reference.verify();
        int[] columns={2,3,4,7,14,8,6,9,10,11,12,13};
        double[] maximum=new double[12],at=new double[12],unacceptedNetworkPost=new double[5];int posts=0;
        for(String line:reference.states().lines().toList()) {
            double[] expected=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
            int row=(int)expected[0],tick=row-(row>=reference.firstPost()?1:0)-(row>=reference.secondPost()?1:0);
            double[] actual=observed.get(tick);boolean post=reference.isPost(row);if(post)posts++;
            for(int channel=0;channel<12;channel++) {
                double error=Math.abs(actual[channel]-expected[columns[channel]]);
                if(post && channel>=7)unacceptedNetworkPost[channel-7]=Math.max(unacceptedNetworkPost[channel-7],error);
                else if(error>maximum[channel]){maximum[channel]=error;at[channel]=expected[1];}
            }
        }
        assertEquals(2,posts);
        assertEquals(observed.size()-1,reference.samples()-3,"Keep the native extra fine terminal tick");
        double[] limits=step==.0005
                ?new double[]{1.5e-3,7.2e-5,2.4e-4,3e-7,2.8e-6,1e-10,5e-4,6e-5,2.3e-5,4.3e-6,3.8e-4,2.5e-6}
                :new double[]{7.5e-4,3.6e-5,1.2e-4,1.5e-7,1.4e-6,1e-10,2.5e-4,6e-5,2.3e-5,4.3e-6,1.9e-4,2.5e-6};
        for(int channel=0;channel<12;channel++)assertTrue(maximum[channel]<=limits[channel],
                reference.name()+" channel "+channel+": "+maximum[channel]+" > "+limits[channel]);
        System.out.println("WTPTB1 coupled "+reference.name()+" max="+Arrays.toString(maximum)+" at="+Arrays.toString(at)
                +"; instantaneous network-post sides remain unaccepted="+Arrays.toString(unacceptedNetworkPost));
        return maximum;
    }
}
