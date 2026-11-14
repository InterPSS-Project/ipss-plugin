package com.interpss.dstab.controller.gov.weccurgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.mach.impl.MachineImpl;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Prescribed mechanical power localizes downstream error; it does not qualify the governor. */
public class Urgs3tPowerBoundaryTest {
    @BeforeAll static void setup()throws Exception{Urgs3tReferenceTest.setup();Urgs3tRunBoundaryTest.verifyArchive();}
    static Stream<Arguments> profiles(){return Stream.of("temperature-limits","rate-limits").flatMap(p->Stream.of(false,true).flatMap(s->Stream.of(false,true).map(m->Arguments.of(p,s,m))));}
    @ParameterizedTest @MethodSource("profiles")
    void exactReferencePowerReachesTheMechanicalEquationAtEachStage(String profile,boolean shared,boolean machineStates)throws Exception {
        Path input=Urgs3tRunBoundaryTest.stage(profile+"-h0p0005-t0p5-command-machines-a");
        var reference=Urgs3tReferenceTest.rows(input.resolve("native.csv"));var tape=new Tape(reference,machineStates);
        var simulation=Urgs3tReferenceTest.simulate(input,shared,1e-6,(a,r)->shared?new Shared(a,r,tape):new Ordinary(a,r,tape));
        assertEquals(1000,tape.predictors);assertEquals(shared?0:1000,tape.correctors);
        assertFalse(simulation.governor().isActive());
        int frame=0;
        for(var row:reference)if(row.get("time_s")>=0)assertEquals(row.get("PMECH"),simulation.frames().get(frame++).get("PMECH"),0.);
        Path output=Files.createTempDirectory(Path.of("target"),"urgs3t-command-power-");
        var result=new LinkedHashMap<String,Object>();result.put("profile",profile);result.put("method",shared?"staggered":"modified-euler");
        result.put("diagnostic_only",true);result.put("governor_states_held",true);result.put("machine_states_prescribed",machineStates);result.put("exact_power_stage_checks",tape.predictors+tape.correctors);result.put("prescribed_network_checks",tape.networkChecks);
        result.putAll(Urgs3tCommandBoundaryTest.compare(output,"prescribed-power",simulation,reference));
        try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("diagnostic.json"),100000)){new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(writer,result);}
        if(machineStates)assertTrue(tape.networkChecks>=2000,"Both endpoint network stages consume the prescribed machine states");
        System.out.println("URGS3T power boundary "+profile+" shared="+shared+" machineStates="+machineStates+" output="+output.getFileName());
    }
    private static final class Tape {
        final double[] before,after;final List<Map<String,Double>> states=new ArrayList<>();final boolean machineStates;int predictors,correctors,networkChecks;
        Tape(List<Map<String,Double>> reference,boolean machineStates) {
            this.machineStates=machineStates;
            var first=new TreeMap<Integer,Double>();var last=new TreeMap<Integer,Double>();
            var coordinates=new TreeMap<Integer,Map<String,Double>>();
            for(var row:reference)if(row.get("time_s")>=0) {
                int tick=(int)Math.round(row.get("time_s")/.0005);
                first.putIfAbsent(tick,row.get("PMECH"));last.put(tick,row.get("PMECH"));
                coordinates.put(tick,row);
            }
            before=new double[first.size()];after=new double[first.size()];
            for(int i=0;i<before.length;i++){assertNotNull(first.get(i));before[i]=first.get(i);after[i]=last.get(i);states.add(coordinates.get(i));}
        }
        double at(double time,boolean right){int tick=(int)Math.round(time/.0005);assertTrue(tick>=0&&tick<before.length,"No boundary extrapolation");return (right?after:before)[tick];}
        void prescribe(DynamicSimuAlgorithm a,double time) {
            if(!machineStates)return;
            at(time,false);var row=states.get((int)Math.round(time/.0005));
            var m=(com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine)machine(a);
            m.setEq1(row.get("GEN1_STATE0"));m.setEd1(row.get("GEN1_STATE1"));m.setPsikd(row.get("GEN1_STATE2"));m.setPsikq(row.get("GEN1_STATE3"));
            m.setSpeed(1+row.get("GEN1_STATE4"));m.setAngle(row.get("GEN1_STATE5"));m.setEfd(row.get("EFD"));
            var second=(com.interpss.dstab.mach.EConstMachine)a.getNetwork().getMachine("Bus2-mach1");
            second.setSpeed(1+row.get("GEN2_STATE0"));second.setAngle(row.get("GEN2_STATE1"));second.setE(row.get("EFD_2"));
        }
        void verify(DynamicSimuAlgorithm a,double time) {
            if(!machineStates)return;
            var row=states.get((int)Math.round(time/.0005));var m=(com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine)machine(a);
            assertArrayEquals(new double[]{row.get("GEN1_STATE0"),row.get("GEN1_STATE1"),row.get("GEN1_STATE2"),row.get("GEN1_STATE3"),1+row.get("GEN1_STATE4"),row.get("GEN1_STATE5")},new double[]{m.getEq1(),m.getEd1(),m.getPsikd(),m.getPsikq(),m.getSpeed(),m.getAngle()},0.,"Network solve preserves prescribed machine coordinates");networkChecks++;
        }
    }
    private static MachineImpl machine(DynamicSimuAlgorithm a) {
        var m=(MachineImpl)a.getNetwork().getMachine("Bus1-mach1");
        assertInstanceOf(com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine.class,m);assertEquals(0.,m.getD(),0.);return m;
    }
    private static double rate(MachineImpl m,double power,double speed) {
        return (power/speed-m.getPe()-m.getPe_NegSeq())/(2*m.getH());
    }
    private static final class Ordinary extends Urgs3tReferenceTest.Ordinary {
        final Tape tape;
        Ordinary(DynamicSimuAlgorithm a,Urgs3tReferenceTest.Recording r,Tape tape){super(a,r,1e-6);this.tape=tape;}
        @Override public boolean initialization(){boolean ok=super.initialization();if(ok)machine(dstabAlgo).getGovernor().setStatus(false);return ok;}
        @Override public void beforeStep(double time)throws DStabSimuException {
            if(r.enabled)machine(dstabAlgo).setPm(tape.at(time,false));super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep()throws DStabSimuException {
            if(r.enabled)machine(dstabAlgo).setPm(tape.at(networkEvaluationTime,"INITIAL".equals(networkSolutionStage)));
            if(r.enabled)tape.prescribe(dstabAlgo,networkEvaluationTime);
            boolean ok=super.networkSolutionStep();if(r.enabled)tape.verify(dstabAlgo,networkEvaluationTime);return ok;
        }
        @Override public void diffEqnIntegrationStep(double time,double h,DynamicSimuMethod method,int flag)throws DStabSimuException {
            assertTrue(flag==0||flag==1);var m=machine(dstabAlgo);var history=m.captureMechanicalIntegrationState();
            double power=tape.at(time+(flag==1?h:0),flag==0),speed=flag==0?m.getSpeed():history.predictedSpeed();
            double derivative=rate(m,power,speed);
            double expected=m.getSpeed()+h*(flag==0?derivative:.5*(derivative-history.speedDerivative()));
            m.setPm(power);super.diffEqnIntegrationStep(time,h,method,flag);
            assertEquals(power,m.getPm(),0.);assertEquals(expected,m.getSpeed(),1e-14,"Mechanical integration consumed the prescribed stage power");
            if(h>0){if(flag==0)tape.predictors++;else tape.correctors++;}
        }
    }
    private static final class Shared extends Urgs3tReferenceTest.Shared {
        final Tape tape;double endpointTime,step,previousRate;boolean advancing;
        Shared(DynamicSimuAlgorithm a,Urgs3tReferenceTest.Recording r,Tape tape){super(a,r,1e-6);this.tape=tape;}
        @Override public boolean initialization(){boolean ok=super.initialization();if(ok)machine(dstabAlgo).getGovernor().setStatus(false);return ok;}
        @Override public void beforeStep(double time)throws DStabSimuException {
            endpointTime=time;if(r.enabled)machine(dstabAlgo).setPm(tape.at(time,false));super.beforeStep(time);
        }
        @Override public void nextStep(double time,double h,DynamicSimuMethod method)throws DStabSimuException {
            var m=machine(dstabAlgo);double power=tape.at(time,true);m.setPm(power);
            double derivative=rate(m,power,m.getSpeed()),expected=m.getSpeed()+h*(1.5*derivative-.5*previousRate);
            advancing=true;step=h;try{super.nextStep(time,h,method);}finally{advancing=false;}
            if(!tape.machineStates)assertEquals(expected,m.getSpeed(),1e-14,"Two-step mechanical integration consumed the prescribed left power");
            previousRate=derivative;endpointTime=time+h;tape.predictors++;
        }
        @Override public boolean networkSolutionStep()throws DStabSimuException {
            double time=endpointTime+(advancing&&!"INITIAL".equals(networkSolutionStage)?step:0.);
            if(r.enabled){machine(dstabAlgo).setPm(tape.at(time,"INITIAL".equals(networkSolutionStage)));tape.prescribe(dstabAlgo,time);}
            boolean ok=super.networkSolutionStep();if(r.enabled)tape.verify(dstabAlgo,time);return ok;
        }
    }
}
