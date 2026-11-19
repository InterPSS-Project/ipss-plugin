package org.interpss.core.dstab;

import org.interpss.core.dstab.reference.EventLeftLimits;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.algo.dynamic.*;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Complete coupled fault discriminator with explicit event-side observations. */
public class HplntdFaultNativeSmibTest extends CorePluginTestSetup {
    public static List<Profile> reviewedProfiles(){return HplntdKernelNativeConformanceTest.reviewedProfiles().stream()
            .filter(p->p.name().startsWith("plant-low-window")).toList();}
    private static double[] snapshot(DynamicSimuAlgorithm algorithm){
        var net=algorithm.getNetwork();double[] row=new double[26];
        var first=(Regca1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var group=first.getReeca1Controller().getHybridController().owner();
        for(int i=0;i<4;i++)row[i]=net.getBus("Bus"+(i+1)).getVoltageMag();
        for(int i=0;i<2;i++){
            var converter=group.ports().get(i).converter();double v=converter.getDStabBus().getVoltageMag(),base=converter.getParentGen().getMvaBase()/100;
            row[4+2*i]=v*converter.getIp()*base;row[5+2*i]=v*converter.getIq()*base;
            System.arraycopy(group.ports().get(i).states(),0,row,21+2*i,2);
        }
        System.arraycopy(group.kernel().states(),0,row,8,13);row[25]=net.getBus("Bus2").getFreq()-1;
        return row;
    }
    private static final class ObservedSolver extends PsseDStabSolver {
        final DynamicSimuAlgorithm algorithm;final double step;final Map<Integer,double[]> post=new HashMap<>();
        double maximumEventPhaseError,maximumEventFrequencyChange,maximumReferenceShift;int eventEndpoints;
        ObservedSolver(DynamicSimuAlgorithm algorithm,double step){super(algorithm);this.algorithm=algorithm;this.step=step;}
        @Override public boolean networkSolutionStep()throws DStabSimuException {
            var before=new HashMap<String,double[]>();
            if(eventBoundaryThisStep&&"INITIAL".equals(networkSolutionStage))
                for(var bus:algorithm.getNetwork().getBusList())if(bus.getBusFreqMeasureBlock() instanceof
                        com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl frequency)
                    before.put(bus.getId(),new double[]{frequency.getY(),frequency.captureModelState().angle0()});
            boolean solved=super.networkSolutionStep();
            if(solved&&eventBoundaryThisStep&&"INITIAL".equals(networkSolutionStage)){
                eventEndpoints++;
                for(var bus:algorithm.getNetwork().getBusList())if(bus.getBusFreqMeasureBlock() instanceof
                        com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl frequency){
                    var state=frequency.captureModelState();
                    maximumEventFrequencyChange=Math.max(maximumEventFrequencyChange,Math.abs(frequency.getY()-before.get(bus.getId())[0]));
                    maximumReferenceShift=Math.max(maximumReferenceShift,Math.abs(state.angle0()-before.get(bus.getId())[1]));
                    maximumEventPhaseError=Math.max(maximumEventPhaseError,Math.abs(Math.IEEEremainder(
                            bus.getVoltageAng()-state.angle0()-state.lastInput(),2*Math.PI)));
                }
            }
            return solved;
        }
        @Override public void diffEqnIntegrationStep(double time,double dt,DynamicSimuMethod method,int flag)throws DStabSimuException {
            if(dt>0&&flag==0)post.put((int)Math.round(time/step),snapshot(algorithm));
            super.diffEqnIntegrationStep(time,dt,method,flag);
        }
    }
    private record Result(List<double[]> actual,ObservedSolver solver,HplntdCoordinator group) {}
    private Result simulate(Profile profile,Path caseDir)throws Exception {
        return simulate(profile,caseDir,false);
    }
    private Result simulate(Profile profile,Path caseDir,boolean disabled)throws Exception {
        Path raw=HplntdTypedRuntimeSmibTest.writeNetwork(caseDir);
        Path dyr=caseDir.resolve("dynamic.dyr");Files.writeString(dyr,profile.input());
        var ctx=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);parser.parseDynFile(dyr.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());net.setAllowGenWithoutMach(true);
        algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);algo.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algo.getAclfAlgorithm().loadflow());
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(profile.step());algo.setTotalSimuTimeSec(.2);algo.setSimuOutputHandler(new StateMonitor());
        algo.setDisableDynamicEvent(disabled);
        var solver=new ObservedSolver(algo,profile.step());algo.setSolver(solver);
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,new Complex(0,.1),null,.05,.05),"three-cycle");
        assertTrue(algo.initialization());
        var converter=(Regca1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();var group=converter.getReeca1Controller().getHybridController().owner();
        // Samples after each step hold the pre-event side at an event tick.
        var left=EventLeftLimits.capture(algo,profile.step(),()->snapshot(algo));
        var actual=new ArrayList<double[]>();int steps=(int)Math.round(.2/profile.step()),event=(int)Math.round(.05/profile.step());
        for(int tick=0;tick<=steps;tick++){
            if(tick==event){var r=group.kernel().references();group.kernel().setReferences(new HplntdKernel.References(r.voltage(),r.reactive()+.02,r.frequency(),r.active()+.05,r.powerFactor(),r.voltageAux(),r.activeAux()));}
            actual.add(left.at(algo.getSimuTime(),()->snapshot(algo)));if(tick<steps)assertTrue(algo.solveDEqnStep(true));
        }
        return new Result(actual,solver,group);
    }
    @Test void convergedEventNetworkIsTheFrequencyReferenceBeforeAnyIntegration(@TempDir Path dir)throws Exception {
        for(var profile:HplntdKernelNativeConformanceTest.reviewedProfiles())if(profile.name().startsWith("plant-low-window")){
            var result=simulate(profile,dir.resolve(profile.name()));
            assertEquals(2,result.solver().eventEndpoints);
            assertEquals(0,result.solver().maximumEventPhaseError,1e-12,
                    "Frequency must be rebased to the converged, not the first linear event endpoint");
            assertEquals(0,result.solver().maximumEventFrequencyChange,1e-15,"Zero-time rebase must preserve frequency output");
            assertEquals((int)Math.round(.2/profile.step()),result.group().predictorCount());
            assertEquals(result.group().predictorCount(),result.group().correctorCount());
        }
    }
    @Test void disabledEventsDoNotRebaseContinuousFrequencyInputs(@TempDir Path dir)throws Exception {
        for(var profile:HplntdKernelNativeConformanceTest.reviewedProfiles())if(profile.name().startsWith("plant-low-window")){
            var result=simulate(profile,dir.resolve(profile.name()),true);
            // With events disabled, exact event stepping has no event boundary at all.
            assertEquals(0,result.solver().eventEndpoints);
            assertEquals(0,result.solver().maximumReferenceShift,0,"Disabled events must not shift the frequency reference");
            assertEquals(0,result.solver().maximumEventFrequencyChange,1e-15);
        }
    }
    @Test void boundedFaultDiagnosticRetainsUnacceptedInstantaneousBoundary(@TempDir Path dir)throws Exception {
        double[] coarse=null;int profiles=0;
        for(var profile:HplntdKernelNativeConformanceTest.reviewedProfiles()){
            if(!profile.name().startsWith("plant-low-window"))continue;
            var result=simulate(profile,dir.resolve(profile.name()));var actual=result.actual();var solver=result.solver();
            var channels=new ArrayList<>(List.of("V1","V2","V3","V4","UNIT1_P_PU","UNIT1_Q_PU","UNIT4_P_PU","UNIT4_Q_PU"));
            for(int i=0;i<13;i++)channels.add("PLANT_STATE"+i);
            for(int bus:new int[]{1,4})for(int i=0;i<2;i++)channels.add("UNIT"+bus+"_STATE"+i);channels.add("FREQ2");
            var lines=profile.body().lines().toList();assertEquals(profile.samples(),lines.size()-1);var headers=Arrays.asList(lines.getFirst().split(","));
            double[] maxima=new double[26],at=new double[26],postMax=new double[26];String previous="";int duplicates=0,history=0;
            var trace=new StringBuilder("time_s,side");for(String c:channels)trace.append(",interpss_").append(c).append(",native_").append(c);trace.append('\n');
            for(String line:lines.subList(1,lines.size())){
                var fields=line.split(",");double time=Double.parseDouble(fields[0]);int tick=Math.max(0,(int)Math.round(time/profile.step()));
                boolean duplicate=previous.equals(fields[0]);previous=fields[0];if(duplicate)duplicates++;if(time<0)history++;
                double[] own=duplicate?solver.post.get(tick):actual.get(tick);assertNotNull(own,"Missing event-post boundary");trace.append(time).append(',').append(time<0?-1:duplicate?1:0);
                for(int i=0;i<26;i++){
                    double expected=Double.parseDouble(fields[headers.indexOf(channels.get(i))]),error=Math.abs(own[i]-expected);assertTrue(Double.isFinite(own[i]));
                    if(error>maxima[i]){maxima[i]=error;at[i]=time;}if(duplicate)postMax[i]=Math.max(postMax[i],error);
                    trace.append(',').append(own[i]).append(',').append(expected);
                }trace.append('\n');
            }
            Path out=Path.of("target/hplntd-fault-coupled",profile.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace.toString());
            System.out.println("Hybrid fault diagnostic (event parity unaccepted) "+profile.name()+" maxima="+Arrays.toString(maxima)+" at="+Arrays.toString(at)+" post="+Arrays.toString(postMax));
            assertEquals(2,history);assertEquals(2,duplicates);
            double[] limits=profile.name().endsWith("coarse")
                    ?new double[]{5.20e-4,4.36e-4,5.29e-5,6.22e-4,.02787,4.11e-5,.04175,4.92e-5,1e-12,.01054,.001326,.004044,.003084,6.57e-6,5.77e-4,.003478,1e-12,6.30e-6,.0056,.0023,.004234,3.03e-4,.004455,6.04e-4,.002230,8.23e-4}
                    :new double[]{5.20e-4,4.36e-4,5.29e-5,6.22e-4,.02787,4.11e-5,.04175,4.92e-5,1e-12,.005404,6.57e-4,.001972,.001506,8.78e-6,7.42e-4,.001785,1e-12,6.15e-6,.0056,.0023,.001972,3.65e-4,.002220,7.30e-4,.001112,8.29e-4};
            // These are bounded diagnostic envelopes, not exact trajectory
            // acceptance. Every raw row, including both event sides, is retained.
            for(int i=0;i<26;i++)assertTrue(maxima[i]<limits[i],profile.name()+" "+channels.get(i)+"="+maxima[i]);
            assertTrue(postMax[4]>.02&&postMax[6]>.03,"Keep the instantaneous-current counterexample explicit");
            if(coarse==null)coarse=maxima.clone();else
                for(int i:new int[]{9,10,11,12,15,20,22,24})assertTrue(maxima[i]<.6*coarse[i],"Half-step reduction: "+channels.get(i));
            profiles++;
        }
        assertEquals(2,profiles);
    }
}
