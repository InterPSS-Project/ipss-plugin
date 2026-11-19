package com.interpss.dstab.controller.gov.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.IpssCorePlugin;
import org.interpss.test.TestConfig;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.common.DStabSimuException;

/** Mechanical boundary cut localizes the coupled power-filter-bypass discrepancy. */
public class Hyg3MechanicalBoundaryReplayTest {
    @Test void prescribedMechanicalPowerSeparatesHydroAndMachineBoundaries(@TempDir Path staged)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var config=TestConfig.load("/test-config/ipss.test.plugin.core.json","Hyg3BypassConformanceTest");
        var fixture=new Hyg3ReferenceFixture(staged,config);
        var output=Files.createTempDirectory(Path.of("target"),"hyg3-mechanical-boundary-");
        var diagnostics=new LinkedHashMap<String,Object>();int profiles=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a")||!entry.path("profile").asText().equals("power-filter-bypass"))continue;
            String tag=entry.path("tag").asText();var root=fixture.stage(tag);double h=entry.path("step").asDouble();assertTrue(h>0.);
            var reference=Hyg3StateConformanceTest.rows(root.resolve("native.csv"));
            var constants=fixture.captureManifest(tag).path("initialized_constants");
            assertEquals(1.,constants.get(12).asDouble(),0.);
            for(int point=0;point<6;point++)assertEquals(constants.get(15+2*point).asDouble(),
                    constants.get(16+2*point).asDouble(),0.,"This discriminator uses the identity gate curve");
            assertEquals(100.,constants.get(32).asDouble(),0.);
            double formulaError=0.,omittedHeadError=0.;
            for(var row:reference){
                double flow=row.get("STATE6"),gate=row.get("VAR3"),head=Math.pow(flow/gate,2);
                double turbine=constants.get(30).asDouble()*(flow-constants.get(28).asDouble());
                double damping=constants.get(31).asDouble()*row.get("SPEED")*gate;
                formulaError=Math.max(formulaError,Math.abs(turbine*head-damping-row.get("PMECH")));
                omittedHeadError=Math.max(omittedHeadError,Math.abs(turbine-damping-row.get("PMECH")));
            }
            assertTrue(formulaError<2e-7,"Recorded states reproduce the declared mechanical equation");
            assertTrue(omittedHeadError>1e-3,"Disturbance distinguishes nonlinear head from constant head");
            var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            var a=context.getDynSimuAlgorithm();var net=context.getDStabilityNet();assertTrue(a.getAclfAlgorithm().loadflow());
            a.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(.5);
            a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            net.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus1",net,
                    com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0.,.2),null,.05,.05),"fault");
            var recording=new Hyg3SharedSmibTest.Recording(a,false,List.of());
            var solver=new PrescribedPowerSolver(a,recording,reference);a.setSolver(solver);assertTrue(a.initialization());
            recording.initial=recording.capture(0.,"INITIAL");recording.enabled=true;
            while(a.getSimuTime()<.5-h/2.)assertTrue(a.solveDEqnStep(true));
            recording.frames.add(recording.capture(a.getSimuTime(),"FINAL"));
            assertEquals(1e-6,solver.getLastNetworkSolutionDiagnostics().voltageTolerance(),0.);
            var maximum=new LinkedHashMap<String,Double>();var times=new LinkedHashMap<String,Double>();
            for(String key:List.of("V1","P_PU","Q_PU","SPEED","ANGLE_DEG","PMECH")){maximum.put(key,0.);times.put(key,0.);}
            int index=0,startups=0;
            try(var raw=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),16L*1024*1024);
                    var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(raw),java.nio.charset.StandardCharsets.UTF_8))){
                writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",recording.initial.values().keySet()));
                for(String key:reference.getFirst().keySet())writer.write(",REFERENCE."+key);writer.write("\n");
                for(var row:reference){
                    double time=row.get("time_s");var actual=time<0?recording.initial:recording.frames.get(index++);if(time<0)startups++;
                    writer.write(time+","+actual.time()+","+actual.kind());for(double value:actual.values().values())writer.write(","+value);for(double value:row.values())writer.write(","+value);writer.write("\n");
                    for(String key:maximum.keySet()){
                        double error=Math.abs(actual.values().get(key)-row.get(key));if(error>maximum.get(key)){maximum.put(key,error);times.put(key,time);}
                    }
                    if(time>=0)assertEquals(row.get("PMECH"),actual.values().get("PMECH"),0.,"Unmodified mechanical boundary reaches the machine");
                }
            }
            assertEquals(reference.size(),recording.frames.size()+startups);
            assertTrue(maximum.get("P_PU")<3.3e-6,
                    "Mechanical boundary cut passes the unchanged fine-step bypass power-coordinate envelope");
            diagnostics.put(tag,Map.of("maximum_error",maximum,"time_of_maximum",times,"samples",reference.size(),
                    "recorded_state_mechanical_formula_error",formulaError,"omitted_head_negative_control_error",omittedHeadError,
                    "scope","Prescribed machine power; inactive governor states are not trajectory-qualified"));profiles++;
        }
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),diagnostics);
        System.out.println("HYG3 mechanical boundary "+output.getFileName()+" "+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(diagnostics));
        assertEquals(4,profiles);
    }

    private static final class PrescribedPowerSolver extends StaggeredEulerSolver {
        final Hyg3SharedSmibTest.Recording recording;final double[] power;
        PrescribedPowerSolver(DynamicSimuAlgorithm a,Hyg3SharedSmibTest.Recording recording,List<Map<String,Double>> reference){
            super(a);this.recording=recording;setReuseNetworkEndpoint(false);setNetworkSolutionTolerance(1e-6);
            int ticks=(int)Math.round(reference.getLast().get("time_s")/a.getSimuStepSec());power=new double[ticks+1];Arrays.fill(power,Double.NaN);
            for(var row:reference)if(row.get("time_s")>=0){int tick=(int)Math.round(row.get("time_s")/a.getSimuStepSec());
                if(Double.isFinite(power[tick]))assertEquals(power[tick],row.get("PMECH"),0.,"Continuous boundary agrees across event sides");power[tick]=row.get("PMECH");}
            for(double value:power)assertTrue(Double.isFinite(value));
        }
        @Override public boolean initialization(){boolean result=super.initialization();if(result){
            var machine=dstabAlgo.getNetwork().getMachine("Bus1-mach1");machine.getGovernor().setStatus(false);machine.setPm(power[0]);}return result;}
        @Override public void beforeStep(double time)throws DStabSimuException{recording.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{
            if(recording.enabled){int tick=(int)Math.round(getParticipantEndpointTime()/dstabAlgo.getSimuStepSec());assertTrue(tick>=0&&tick<power.length);
                dstabAlgo.getNetwork().getMachine("Bus1-mach1").setPm(power[tick]);}
            boolean solved=super.networkSolutionStep();
            if(solved&&recording.enabled&&"INITIAL".equals(networkSolutionStage))recording.frames.add(recording.capture(dstabAlgo.getSimuTime(),"GRID"));
            return solved;
        }
    }
}
