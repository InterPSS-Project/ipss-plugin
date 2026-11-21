package org.interpss.core.dstab;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
/** Complete pulse trajectories with the existing strict voltage acceptance gate. */
@Tag("extended")
public class Svsmo3t2DeadbandCoupledTest extends CorePluginTestSetup {
    private static final double H=.0005;
    static java.util.stream.Stream<Arguments> profiles() {
        return java.util.stream.Stream.of("enabled","disabled","middle","unequal","bypass")
                .flatMap(p->java.util.stream.Stream.of(Arguments.of(p,false),Arguments.of(p,true)));
    }
    @ParameterizedTest(name="{0}, staggered={1}") @MethodSource("profiles")
    void completePulseTrajectoriesUnderBothMethods(String profile,boolean staggered) throws Exception {
        Path root=Svsmo3t2DeadbandRelockTest.reference();
        Path output=Files.createTempDirectory(Path.of("target"),"svs-deadband-coupled-");
        var summary=new LinkedHashMap<String,Object>();
        var gates=new ArrayList<org.junit.jupiter.api.function.Executable>();
        {
            Path p=root.resolve(profile+"-a");String key=profile+(staggered?"-staggered":"-modified");
            var c=new PSSEMultiFileLoader().loadDStab(p.resolve("smib.raw").toString(),p.resolve("smib.dyr").toString());
            var a=c.getDynSimuAlgorithm();var net=c.getDStabilityNet();
            net.setStaticLoadIncludedInYMatrix(true);net.setStaticLoadModel(StaticLoadModel.CONST_Z);net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
            a.getAclfAlgorithm().setTolerance(1e-10);a.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
            a.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);assertTrue(a.getAclfAlgorithm().loadflow());
            a.setSimuStepSec(H);a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuOutputHandler(new StateMonitor());
            var solver=staggered?new StaggeredEulerSolver(a):new DefaultDynamicSimulationSolver(a) {{ converge_tol=1e-6; }};
            if(staggered)((StaggeredEulerSolver)solver).setNetworkSolutionTolerance(1e-6);
            if(staggered)((StaggeredEulerSolver)solver).setEndpointTimePrecision(StaggeredEulerSolver.EndpointTimePrecision.SINGLE);
            a.setSolver(solver);assertTrue(a.initialization());
            var m=(Svsmo3t2Model)net.getDStabBus("Bus3").getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
            var frames=new ArrayList<Map<String,Double>>();var eventFrames=new HashMap<Integer,Map<String,Double>>();double schedule=m.getScheduledVoltage();
            for(int tick=0;tick<=2000;tick++) {
                frames.add(frame(a,m));if(tick==2000)break;
                if(tick==100||tick==200){m.setScheduledVoltage(schedule+(tick==100?(profile.equals("enabled")||profile.equals("disabled")?.02:.007):0));
                    assertTrue(m.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
                    if(staggered)((StaggeredEulerSolver)solver).notifyDiscontinuity();
                    assertTrue(solver.networkSolutionStep());eventFrames.put(tick,frame(a,m));}
                assertTrue(a.solveDEqnStep(true));
            }
            var lines=Files.readAllLines(p.resolve("reference.csv"));assertEquals(2006,lines.size());var names=Arrays.asList(lines.getFirst().split(","));
            var stats=new LinkedHashMap<String,double[]>();for(String name:frames.getFirst().keySet())stats.put(name,new double[]{0,0,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY});
            double previous=Double.NaN;
            try(var w=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(output.resolve(key+".csv"),4000000)) {
                w.write("time_s");for(String name:stats.keySet())w.write(",actual_"+name+",reference_"+name);w.write('\n');
                for(String line:lines.subList(1,lines.size())){double[] r=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();int tick=Math.max(0,(int)Math.round(r[0]/H));
                    var actual=r[0]==previous?eventFrames.getOrDefault(tick,frames.get(tick)):frames.get(tick);previous=r[0];w.write(Double.toString(r[0]));
                    for(var e:stats.entrySet()){String name=e.getKey();double ref=r[names.indexOf(name)],v=actual.get(name),err=v-ref;assertTrue(Double.isFinite(err));var q=e.getValue();q[0]=Math.max(q[0],Math.abs(err));q[1]+=err*err;q[2]=Math.min(q[2],ref);q[3]=Math.max(q[3],ref);w.write(","+v+","+ref);}w.write('\n');}
            }
            var metrics=new LinkedHashMap<String,Object>();for(var e:stats.entrySet()){var q=e.getValue();double rmse=Math.sqrt(q[1]/2005);metrics.put(e.getKey(),Map.of("max",q[0],"rmse",rmse,"range_nrmse",q[3]>q[2]?rmse/(q[3]-q[2]):0));}
            boolean voltagePass=true;
            for(int bus=1;bus<=3;bus++) {String channel="V_"+bus;var q=stats.get(channel);double error=Math.sqrt(q[1]/2005);double nrmse=q[3]>q[2]?error/(q[3]-q[2]):0;
                voltagePass &= nrmse<.001;gates.add(()->assertTrue(nrmse<.001,key+" "+channel+" range NRMSE="+nrmse+"; limit=.001"));}
            summary.put(key,Map.of("status",voltagePass?"voltage-gate-passed-other-channels-unqualified":"voltage-gate-failed","rows",2005,"channels",metrics));
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(),summary);
        }
        System.out.println("SVS_DEADBAND_COUPLED "+output);
        assertAll("Full pulse voltage accuracy",gates);
    }
    private static Map<String,Double> frame(DynamicSimuAlgorithm a,Svsmo3t2Model m){
        var r=new LinkedHashMap<String,Double>();for(int i=1;i<=3;i++){var b=a.getNetwork().getBus("Bus"+i);r.put("V_"+i,b.getVoltageMag());r.put("ANG_"+i,Math.toDegrees(b.getVoltageAng()));}
        r.put("A_VAR_0",m.getCurrent());r.put("A_VAR_1",m.getMvarOutput()/m.getData().statcomBaseMva());r.put("A_VAR_6",m.getVoltageReference());
        int i=0;for(double x:m.getNamedStates().values())r.put("A_STATE_"+i++,x);return r;
    }
}
