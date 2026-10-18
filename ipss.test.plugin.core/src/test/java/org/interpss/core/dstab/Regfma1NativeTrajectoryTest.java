package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.Regfma1NativeReferenceCheckpoints;
import org.interpss.core.dstab.reference.Regfma1NativeReferenceCheckpoints.Reference;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Native 36.7 nine-state/24-channel nominal-grid checkpoints, not full model acceptance.
 * Instantaneous event-post and plant-input excursions remain separate profiles.
 */
public class Regfma1NativeTrajectoryTest {
    private static final double[] FLAT={6e-7,1e-12,1e-12,3.5e-7,3e-6,3.5e-7,1e-12,1e-12,1e-6,
            1e-6,1e-6,1e-12,1e-12,1e-12,1e-12,8e-7,1e-6,3.5e-7,1.2e-7,8e-8,3e-7,1.5e-6,3.5e-7,1e-12};
    private static final double[] DIRECT={3e-4,1e-12,1e-12,5e-6,.003,.00065,1e-12,1e-12,1e-12,
            9e-5,9e-5,1e-12,1e-12,1e-12,1e-12,.00023,1e-6,3e-5,1.5e-5,1.4e-6,2e-5,.00014,3e-5,6e-6};
    private static final double[] TERMINAL={3e-4,1e-12,1e-12,5e-6,.003,.00065,1e-12,1e-12,7e-6,
            9e-5,.00015,1e-12,1e-12,1e-12,1e-12,.0004,1e-6,4.5e-5,2.4e-5,2.1e-6,4.2e-5,.00023,4.5e-5,6e-6};
    private static final double[] CURRENT={.0018,1e-12,1e-12,2.4e-5,.0017,.0008,1e-12,1e-12,1e-12,
            5e-5,5e-5,1e-12,1e-12,1e-12,1e-12,1.3e-5,1e-6,1.7e-5,8.5e-6,8e-7,2.8e-5,7e-5,1.7e-5,3.7e-5};
    private static final double[] CURRENT_HALF={.00095,1e-12,1e-12,5.8e-6,.0009,.00042,1e-12,1e-12,1e-12,
            2.7e-5,2.7e-5,1e-12,1e-12,1e-12,1e-12,6e-6,1e-6,1.3e-5,6.5e-6,5.6e-7,1.3e-5,3.6e-5,1.3e-5,2e-5};
    private static final double[] CURRENT_PI={.0017,1e-12,1e-12,2.4e-5,.0017,.00085,1e-12,1e-12,9e-6,
            5.3e-5,.00021,1e-12,1e-12,1e-12,1e-12,1.2e-5,1e-12,3.5e-5,1.9e-5,1.8e-6,9e-5,.00017,3.5e-5,3.6e-5};
    private static final double[] CURRENT_PI_HALF={.0009,1e-12,1e-12,6e-6,.0009,.00044,1e-12,1e-12,3e-6,
            2.8e-5,.00011,1e-12,1e-12,1e-12,1e-12,1e-5,1e-12,2e-5,1.1e-5,9e-7,4.1e-5,8.1e-5,2e-5,1.9e-5};
    private static final double[] PQ={.00031,4.7e-8,7e-7,9e-6,.003,.0007,.00014,2.3e-5,1e-12,
            .00047,.00047,4.5e-7,3.2e-6,.00038,1.8e-5,.00027,1e-6,.00015,7.7e-5,7e-6,.00012,.0007,.00015,9.5e-6};
    private static final double[] PQ_HALF={.00016,1.2e-8,2.7e-7,2.4e-6,.0016,.00036,3e-5,6e-6,1e-12,
            .00023,.00023,1.2e-7,1.6e-6,.00018,4e-6,.00014,1e-6,7.2e-5,3.8e-5,3.5e-6,5.9e-5,.00035,7.2e-5,4.9e-6};

    @Test void allVoltageAndPlantSelectorsHoldTheirNativeInitialPoint(@TempDir Path directory) throws Exception {
        for(String key:List.of("flat00","flat01","flat10","flat11")) {
            Reference reference=Regfma1NativeReferenceCheckpoints.get(key);
            List<double[]> actual=run(reference,directory.resolve(key+".dyr"));
            double[] initial=reference.rows().get(0);
            for(int tick=0;tick<actual.size();tick++) {
                compare(initial,actual.get(tick),FLAT,key+" tick="+tick);
                assertArrayEquals(actual.get(0),actual.get(tick),1e-10,"No-event equilibrium must not drift");
            }
        }
    }

    @Test void directVoltageThreeCycleFaultMatchesNativeAtHalfMillisecond(@TempDir Path directory) throws Exception {
        compareFault("fault00",DIRECT,directory);
    }
    @Test void directVoltageThreeCycleFaultMatchesNativeAtQuarterMillisecond(@TempDir Path directory) throws Exception {
        double[] half=new double[DIRECT.length];
        for(int i=0;i<half.length;i++)half[i]=Math.max(FLAT[i],DIRECT[i]*.5);
        compareFault("fault00h2",half,directory);
    }
    @Test void terminalVoltagePiThreeCycleFaultMatchesNativeStatesAndOutputs(@TempDir Path directory) throws Exception {
        compareFault("fault11",TERMINAL,directory);
    }
    @Test void activeCurrentLimitMatchesNativeAndReleasesAfterClearing(@TempDir Path directory) throws Exception {
        compareFault("current00",CURRENT,directory);
    }
    @Test void activeCurrentLimitAtHalfStepReducesNativeStateErrors(@TempDir Path directory) throws Exception {
        compareFault("current00h2",CURRENT_HALF,directory);
    }
    @Test void terminalVoltagePiCurrentLimitMatchesNativeAtBothSteps(@TempDir Path directory) throws Exception {
        compareFault("current11",CURRENT_PI,directory);
        compareFault("current11h2",CURRENT_PI_HALF,directory);
    }
    @Test void allFourPowerLimitPathsMatchNativeAtBothSteps(@TempDir Path directory) throws Exception {
        compareFault("pq00",PQ,directory);
        compareFault("pq00h2",PQ_HALF,directory);
    }
    @Test void independentlyBoundedVoltageIntegralMatchesNativeAtBothSteps(@TempDir Path directory) throws Exception {
        compareFault("voltage11",TERMINAL,directory);
        double[] half=TERMINAL.clone();
        for(int i=0;i<half.length;i++)half[i]=Math.max(FLAT[i],half[i]*.5);
        // Native float-clock/limit-release residual does not halve for s8.
        half[8]=6e-6;
        compareFault("voltage11h2",half,directory);
    }
    @Test void fixedCheckpointDigestsRejectMutation() {
        assertEquals(15,Regfma1NativeReferenceCheckpoints.references().size());
        for(var reference:Regfma1NativeReferenceCheckpoints.references().values()) {
            assertDoesNotThrow(()->reference.verify(reference.body()));
            assertThrows(IllegalArgumentException.class,()->reference.verify(reference.body()+" "));
            assertEquals(64,reference.nativeCsvSha256().length());
            assertEquals(18,reference.constants().size());
            assertThrows(UnsupportedOperationException.class,()->reference.constants().set(0,999.0));
        }
        assertThrows(IllegalArgumentException.class,()->Regfma1NativeReferenceCheckpoints.get("unknown"));
    }

    private static void compareFault(String key,double[] bounds,Path directory) throws Exception {
        Reference reference=Regfma1NativeReferenceCheckpoints.get(key);
        List<double[]> actual=run(reference,directory.resolve(key+".dyr"));
        double[] maximum=new double[bounds.length];
        for(double[] expected:reference.rows()) {
            int tick=(int)expected[0];
            assertEquals(tick,expected[0],0);
            assertEquals(tick*reference.step(),expected[1],reference.step()/2,
                    "Preserved native float clock must map to exactly one nominal endpoint");
            compare(expected,actual.get(tick),bounds,key+" tick="+tick);
            for(int i=0;i<maximum.length;i++)maximum[i]=Math.max(maximum[i],Math.abs(expected[i+2]-actual.get(tick)[i]));
        }
        compare(reference.rows().get(0),actual.get(0),FLAT,key+" initialization");
        assertTrue(actual.stream().mapToDouble(row->row[17]).min().orElseThrow()<1.015,
                "The three-cycle fault must exercise the voltage response");
        if(reference.vflag()==1)assertTrue(actual.stream().mapToDouble(row->Math.abs(row[8]-actual.get(0)[8])).max().orElseThrow()>1e-4,
                "The terminal-voltage profile must exercise the voltage PI integrator");
        System.out.println("Native REGFMA1 "+key+" channel maxima="+Arrays.toString(maximum));
    }

    private static List<double[]> run(Reference reference,Path dyr) throws Exception {
        IpssCorePlugin.init();
        Files.writeString(dyr,"1 'REGFMA1' '1' "+reference.vflag()+" "+reference.qvflag()
                +" "+String.join(" ",reference.constants().stream().map(Object::toString).toList())+" /\n"
                +"3 'GENCLS' '1' 9999999 0 /\n");
        var context=new PSSEMultiFileLoader().loadDStab(
                "testData/adpter/psse/v33/renewable/regfma1_native.raw",dyr.toString());
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        net.setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var generator=(DStabGen)net.getDStabBus("Bus1").getContributeGen("1");
        var model=assertInstanceOf(Regfma1Model.class,generator.getDynamicGenDevice());
        assertEquals(new Complex(.015,.23),generator.getPosGenZ());
        assertEquals(50,generator.getMvaBase(),0);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(reference.step());algorithm.setTotalSimuTimeSec(.3);
        algorithm.setSimuOutputHandler(new StateMonitor());
        if(reference.fault())net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.05,.05),"NativeThreeCycleFault");
        assertTrue(algorithm.initialization());
        int steps=(int)Math.round(.3/reference.step());
        List<double[]> rows=new ArrayList<>();
        boolean sawCurrentLimit=false;
        boolean narrowPowerLimits=reference.constants().get(6)<.9;
        boolean[] sawPowerLimit=new boolean[4];
        int[] powerIntegralIndices={1,2,6,7};
        for(int tick=0;tick<=steps;tick++) {
            assertEquals(tick*reference.step(),algorithm.getSimuTime(),1e-10);
            model.getOutputObject();
            var before=model.getNamedStates();
            var values=new ArrayList<Double>(24);
            values.addAll(model.getPsseStateSnapshot().values());
            values.addAll(model.getPsseVariableSnapshot().values());
            assertEquals(before,model.getNamedStates(),"Observation must not change model state");
            for(String bus:List.of("Bus1","Bus2","Bus3"))values.add(net.getBus(bus).getVoltageMag());
            double powerScale=generator.getMvaBase()/net.getBaseMva();
            values.add(model.getActivePower()*powerScale);values.add(model.getReactivePower()*powerScale);
            values.add(net.getBus("Bus1").getVoltageMag());values.add(model.getSpeed()-1.0);
            double[] row=values.stream().mapToDouble(Double::doubleValue).toArray();
            assertEquals(24,row.length);
            assertTrue(Arrays.stream(row).allMatch(Double::isFinite));
            sawCurrentLimit|=model.isCurrentLimited();
            if(reference.imax()==1.5)
                assertFalse(model.isCurrentLimited(),"The original mild profiles keep current limiting inactive");
            else if(tick*reference.step()>.05 && tick*reference.step()<.1) {
                assertTrue(model.isCurrentLimited(),"The new native profile must exercise Imax in-fault");
                assertEquals(.5,row[15],1e-10,"Current is limited on the device MVA base");
                assertEquals(model.getInternalVoltage(),row[10],0,"Native VAR L+1 remains before Imax");
            }
            for(int i=0;i<powerIntegralIndices.length;i++) {
                int index=powerIntegralIndices[i];
                sawPowerLimit[i]|=Math.abs(row[index])>1e-10;
                if(!narrowPowerLimits)assertEquals(0,row[index],1e-12,"This profile keeps PQ limits inactive");
                else assertTrue(i%2==0 ? row[index]<=0 : row[index]>=0,"Power-limit integrals retain their one-sided bounds");
            }
            if(reference.constants().get(4)==1.08) {
                assertTrue(row[8]>=1.07 && row[8]<=1.08,"Native voltage integral has independent hard bounds");
            }
            rows.add(row);
            if(tick<steps)assertTrue(algorithm.solveDEqnStep(true),"Native REGFMA1 step="+tick);
        }
        assertEquals(reference.imax()==.5,sawCurrentLimit);
        if(reference.imax()==.5)assertTrue(rows.get(rows.size()-1)[15]<.49,"Imax must release after clearing");
        if(narrowPowerLimits) {
            for(boolean active:sawPowerLimit)assertTrue(active,"Every P/Q limit path must be exercised");
            for(int index:new int[]{11,12,13,14})assertEquals(0,rows.get(rows.size()-1)[index],1e-12,"Power-limit outputs release by the end");
        }
        if(reference.constants().get(4)==1.08)
            assertEquals(1.08,rows.stream().mapToDouble(row->row[8]).max().orElseThrow(),1e-12);
        return rows;
    }
    private static void compare(double[] expected,double[] actual,double[] bounds,String context) {
        for(int i=0;i<bounds.length;i++)assertEquals(expected[i+2],actual[i],bounds[i],
                context+" "+Regfma1NativeReferenceCheckpoints.CHANNELS.get(i));
    }
}
