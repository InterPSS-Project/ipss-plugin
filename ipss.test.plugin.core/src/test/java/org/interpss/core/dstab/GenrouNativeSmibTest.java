package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GenrouNativeReference;
import com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine;
import com.interpss.dstab.mach.ieeevc.IeeeVcData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.state.dstab.MachineState;

/** Core reuse and native six-state SMIB bounds, not complete boundary/lifecycle acceptance. */
public class GenrouNativeSmibTest extends CorePluginTestSetup {
    private record Fixture(DynamicSimuAlgorithm algorithm,IeeeVcRoundRotorMachine machine,Machine reference) {}
    private static Fixture load(GenrouNativeReference.Profile p,Path directory)throws Exception {
        return load(p,directory,false);
    }
    private static Fixture load(GenrouNativeReference.Profile p,Path directory,boolean exponentialOverride)throws Exception {
        Files.createDirectories(directory);
        Path raw=directory.resolve("network.raw"),dyr=directory.resolve("dynamic.dyr");
        Files.writeString(raw,p.network());Files.writeString(dyr,exponentialOverride?p.input().replace("GENROU","GENROE"):p.input());
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=context.getDStabilityNet();
        // The native probe converts zero percent to current/admittance: retain
        // constant MVA for both components, outside the all-loads-in-Y path.
        net.setStaticLoadIncludedInYMatrix(false);
        net.setStaticLoadModel(StaticLoadModel.CONST_P);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_P);
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(p.step());algorithm.setTotalSimuTimeSec(.5);algorithm.setSimuOutputHandler(new StateMonitor());
        if(p.fault())net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"round-rotor-three-cycle");
        assertTrue(algorithm.initialization());
        return new Fixture(algorithm,assertInstanceOf(IeeeVcRoundRotorMachine.class,net.getMachine("Bus1-mach1")),net.getMachine("Bus2-mach1"));
    }
    private static final String HEADER="time_s,EP_Q,EP_D,PSIK_D,PSIK_Q,SPEED_DEV,ANGLE,V_BUS1,V_BUS2,MACH_ANGLE,P_PU,Q_PU,EFD,PMECH,MACH_SPEED,REF_ANGLE,REF_SPEED\n";
    private static double[] snapshot(Fixture f,double time) {
        var m=f.machine();var s=m.getNamedStates();var v=m.getDStabBus().getVoltage();
        var current=m.getIgen().subtract(v.multiply(m.getYgen()));var power=v.multiply(current.conjugate());
        return new double[]{time,s.get("E'q"),s.get("E'd"),s.get("Psi'd"),s.get("Psi'q"),s.get("Speed deviation"),s.get("Angle"),
            v.abs(),f.algorithm().getNetwork().getBus("Bus2").getVoltageMag(),Math.toDegrees(m.getAngle()),
            power.getReal(),power.getImaginary(),m.getEfd(),m.getPm(),m.getSpeed()-1,Math.toDegrees(f.reference().getAngle()),f.reference().getSpeed()-1};
    }
    @Test void allImportedProfilesReuseCoreAndMatchEveryNativeStateRow(@TempDir Path dir)throws Exception {
        for(var p:GenrouNativeReference.profiles()) {
            var f=load(p,dir.resolve(p.name()));var m=f.machine();
            assertEquals(com.interpss.dstab.mach.impl.RoundRotorMachineImpl.class,m.getClass().getSuperclass());
            var states=m.getNamedStates();
            assertEquals(List.of("E'q","E'd","Psi'd","Psi'q","Speed deviation","Angle"),new ArrayList<>(states.keySet()));
            assertThrows(UnsupportedOperationException.class,()->states.put("Angle",99.));
            assertEquals(m.getEq1(),states.get("E'q"));assertEquals(m.getEd1(),states.get("E'd"));
            assertEquals(m.getPsikd(),states.get("Psi'd"));assertEquals(m.getPsikq(),states.get("Psi'q"));
            assertEquals(m.getSpeed()-1,states.get("Speed deviation"));assertEquals(m.getAngle(),states.get("Angle"));
            var trace=new StringBuilder(HEADER);int ticks=(int)Math.round(.5/p.step());
            var points=new ArrayList<double[]>();
            for(int tick=0;tick<=ticks;tick++) {
                double[] row=snapshot(f,tick*p.step());points.add(row);for(int i=0;i<row.length;i++) {
                    assertTrue(Double.isFinite(row[i]),p.name()+" column "+i+" tick "+tick);
                    if(i>0)trace.append(',');trace.append(row[i]);
                }trace.append('\n');
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            assertEquals(0,states.get("Speed deviation"));
            Path output=Path.of("target/genrou-native-coupled",p.name()+".csv");
            Files.createDirectories(output.getParent());Files.writeString(output,trace);
            assertEveryNativeStateRow(p,points);
        }
    }
    private static void assertEveryNativeStateRow(GenrouNativeReference.Profile p,List<double[]> points) {
        String[] names={"EP_Q","EP_D","PSIK_D","PSIK_Q","SPEED_DEV","ANGLE"};
        var rows=HplntdSwitchingNativeSmibTest.rows(p.body());double[] maximum=new double[6],at=new double[6];
        assertEquals(6,p.stateBounds().length);
        for(var expected:rows) {
            double time=expected.get("time_s"),position=Math.max(0,Math.min(points.size()-1,time/p.step()));
            int lower=(int)Math.floor(position),upper=Math.min(points.size()-1,lower+1);double fraction=position-lower;
            for(int j=0;j<6;j++) {
                double actual=points.get(lower)[j+1]+fraction*(points.get(upper)[j+1]-points.get(lower)[j+1]);
                double error=Math.abs(actual-expected.get(names[j]));
                if(error>maximum[j]){maximum[j]=error;at[j]=time;}
                assertTrue(error<=p.stateBounds()[j],p.name()+" "+names[j]+" raw row t="+time+" error="+error);
            }
        }
        System.out.println("GENROU native states "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
    }
    @Test void rawHistoriesRetainEveryNativeSampleAndState() {
        assertEquals(20,GenrouNativeReference.profiles().size());
        for(var p:GenrouNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            assertEquals(17,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            assertTrue(rows.stream().flatMap(r->r.values().stream()).allMatch(Double::isFinite));
            assertFalse(p.input().isBlank());assertFalse(p.network().isBlank());
        }
    }
    @Test void allNativeInitialStatesAndBoundariesMatchWithoutStateForcing(@TempDir Path dir)throws Exception {
        double[] bounds={2.3e-7,2.3e-7,2.5e-7,2.5e-7,1e-11,2e-7,1.5e-7,1.5e-7,
                2.1e-5,2.5e-7,1.5e-6,5.1e-7,3.5e-8,1e-11,7e-7,1e-11};
        for(var p:GenrouNativeReference.profiles()) {
            var f=load(p,dir.resolve(p.name()));var expected=HplntdSwitchingNativeSmibTest.rows(p.body()).getFirst();
            double[] actual=snapshot(f,0);String[] names=HEADER.strip().split(",");
            for(int column=1;column<actual.length;column++) {
                assertEquals(expected.get(names[column]),actual[column],bounds[column-1],p.name()+" initial "+names[column]);
            }
            assertEquals(p.name().startsWith("exponential-"),f.machine().isExponentialSaturation());
            assertEquals(p.name().startsWith("exponential-")?"GENROE":"GENROU",f.machine().getName());
        }
    }
    @Test void saturationUsesSpecifiedPointsAndPreservesQuadraticDefault() {
        var m=new IeeeVcRoundRotorMachine();m.setMachData(DStabObjectFactory.createMachineData());
        m.setSe100(10);m.setSe120(40);assertFalse(m.isExponentialSaturation());
        double quadratic=m.getSatruationFactor(1.1);
        m.setExponentialSaturation(true);
        assertEquals(.1,m.getSatruationFactor(1),1e-15);assertEquals(.4,m.getSatruationFactor(1.2),1e-15);
        assertEquals(.1*Math.pow(1.1,Math.log(4)/Math.log(1.2)),m.getSatruationFactor(1.1),1e-15);
        assertTrue(Math.abs(quadratic-m.getSatruationFactor(1.1))>1e-4);
        assertEquals(0,m.getSatruationFactor(0));assertEquals(0,m.getSatruationFactor(-1));
        m.setSe100(0);assertEquals(0,m.getSatruationFactor(1.1));
        m.setSe100(10);m.setSe120(0);assertEquals(0,m.getSatruationFactor(1.1));
        m.setSe120(40);m.setExponentialSaturation(false);assertEquals(quadratic,m.getSatruationFactor(1.1));
    }
    @Test void nativeBenchmarkLoadRemainsConstantMvaAtReducedVoltage(@TempDir Path dir)throws Exception {
        var p=GenrouNativeReference.profiles().getFirst();var f=load(p,dir);
        var net=f.algorithm().getNetwork();var bus=net.getDStabBus("Bus2");
        assertFalse(net.isStaticLoadIncludedInYMatrix());
        assertEquals(StaticLoadModel.CONST_P,net.getStaticLoadModel());
        assertEquals(StaticLoadModel.CONST_P,net.getReactiveStaticLoadModel());
        var power=bus.calStaticLoad();assertEquals(new Complex(1,.5),power);
        bus.setVoltage(bus.getVoltage().multiply(.9));
        assertEquals(power,bus.calStaticLoad());
    }
    @Test void stateReconstructionPreservesSaturationAndOptionalSensing(@TempDir Path dir)throws Exception {
        var p=GenrouNativeReference.profiles().stream().filter(v->v.name().equals("saturated-flat-coarse")).findFirst().orElseThrow();
        for(boolean exponential:List.of(false,true))for(boolean compensated:List.of(false,true)) {
            var f=load(p,dir.resolve("source-"+exponential+"-"+compensated),exponential);
            if(compensated)f.machine().setIeeeVcData(new IeeeVcData(.01,.02));
            var captured=new MachineState(f.machine());
            var target=load(p,dir.resolve("target-"+exponential+"-"+compensated));
            var copy=assertInstanceOf(IeeeVcRoundRotorMachine.class,captured.create(target.algorithm().getNetwork(),"Bus1","1"));
            assertEquals(exponential,copy.isExponentialSaturation());
            assertEquals(f.machine().getIeeeVcData(),copy.getIeeeVcData());
            assertEquals(f.machine().getNamedStates(),copy.getNamedStates());
            assertEquals(f.machine().getSatruationFactor(1.1),copy.getSatruationFactor(1.1));
        }
    }
    @Test void exponentialIdentifierMustNotSilentlyUseQuadraticInitialization(@TempDir Path dir)throws Exception {
        var p=GenrouNativeReference.profiles().stream().filter(v->v.name().equals("saturated-flat-coarse")).findFirst().orElseThrow();
        var quadratic=load(p,dir.resolve("quadratic"));var exponential=load(p,dir.resolve("exponential"),true);
        assertTrue(Math.abs(quadratic.machine().getAngle()-exponential.machine().getAngle())>1e-5,
                "The independently recorded saturation discriminator has distinct equilibrium rotor angles");
        assertTrue(Math.abs(quadratic.machine().getEfd()-exponential.machine().getEfd())>1e-4,
                "Different saturation laws must not initialize with identical field voltage");
    }
}
