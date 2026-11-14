package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;

/** Complete synthetic grid/event recordings exercise actual shared and ordinary dispatch. */
public class Wsieg1SharedSmibTest {
    static final Path CASE=Path.of("testData/adpter/psse/v33/HspModels");
    static final String[] NAMES={"Speed lag","Valve","Turbine 1","Turbine 2","Turbine 3","Turbine 4"};
    static final double TOLERANCE=1e-6;
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}
    static Stream<Arguments> methods(){return Stream.of("standard","curve","dual","unused-lp")
            .flatMap(profile->Stream.of(Arguments.of(profile,false),Arguments.of(profile,true)));}

    @ParameterizedTest @MethodSource("methods")
    void flatAndFaultRunThroughTheActualSolverWithoutDoubleAdvancing(String fixture,boolean staggered) throws Exception {
        String name=fixture.equals("standard")?"wsieg1":"wsieg1-"+fixture;
        Path raw=CASE.resolve(fixture.equals("dual")?"dual-network.raw":"network.raw"),dyr=CASE.resolve(name+".dyr");
        Path output=Files.createTempDirectory(Path.of("target"),"wsieg1-shared-"+fixture+"-"+(staggered?"staggered":"euler")+"-");
        System.out.println("WSIEG1 flat/fault recording "+output.getFileName());
        for(String event:List.of("flat","fault")) {
            var recording=simulate(raw,dyr,staggered,event,.0005,.5,List.of());
            write(output.resolve(fixture+"-"+event+"-"+(staggered?"staggered":"euler")+".csv.gz"),recording);
            if(event.equals("flat")) for(var row:recording.frames) {
                assertEquals(recording.initial.values.get("V1"),row.values.get("V1"),1e-5);
                assertEquals(recording.initial.values.get("ANGLE_DEG"),row.values.get("ANGLE_DEG"),1e-5);
                for(int i=0;i<6;i++)assertEquals(recording.initial.values.get("STATE"+i),row.values.get("STATE"+i),1e-12);
            }
        }
    }

    record ReferenceEvent(double time,double increment){}
    record Frame(double time,String kind,Map<String,Double> values){}
    static final class Recording {
        final DynamicSimuAlgorithm algorithm;final String event;final List<ReferenceEvent> references;
        final List<Frame> frames=new ArrayList<>();boolean enabled,recordMachineStates;int nextReference;Frame initial;
        final double[] published=new double[6];
        Recording(DynamicSimuAlgorithm a,String event,List<ReferenceEvent> references){algorithm=a;this.event=event;this.references=List.copyOf(references);}
        boolean before(double time) {
            if(!enabled)return false;
            if(nextReference<references.size()) {
                var point=references.get(nextReference);
                if(Math.abs(point.time-time)<1e-9) {
                    frames.add(capture(time,"REFERENCE_PRE"));var g=(Wsieg1Governor)algorithm.getNetwork().getMachine("Bus1-mach1").getGovernor();
                    g.applyReferenceEvent(g.getRefPoint()+point.increment);nextReference++;return true;
                }
                if(time>point.time+1e-9)throw new IllegalStateException("Reference event missed its physical grid point");
            }
            if((!event.equals("flat")&&!event.equals("reference-reversals")&&Math.abs(time-.05)<1e-9)
                    ||(event.equals("fault")||event.endsWith("-trip"))&&Math.abs(time-.1)<1e-9)frames.add(capture(time,"EVENT_PRE"));
            return false;
        }
        Frame capture(double time,String kind) {
            var net=algorithm.getNetwork();var machine=net.getMachine("Bus1-mach1");var governor=(Wsieg1Governor)machine.getGovernor();
            var values=new LinkedHashMap<String,Double>();
            for(int bus=1;bus<=net.getBusList().size();bus++)if(net.getDStabBus("Bus"+bus)!=null) {
                var voltage=net.getDStabBus("Bus"+bus).getVoltage();values.put("V"+bus,voltage.abs());values.put("A"+bus,Math.toDegrees(voltage.getArgument()));
            }
            for(int bus=1;bus<=2;bus++) {
                var m=net.getMachine("Bus"+bus+"-mach1");if(m==null||bus==2&&!Wsieg1Governor.isSecondaryShaft(m.getGovernor()))continue;
                String suffix=bus==1?"":"_LP";boolean active=m.isActive()&&m.getDStabBus().isActive()&&m.getDStabBus().getContributeGen("1").isActive();var voltage=m.getDStabBus().getVoltage();
                var power=active?voltage.multiply(m.getIgen().subtract(voltage.multiply(m.getYgen())).conjugate()):Complex.ZERO;
                values.put("ANGLE_DEG"+suffix,active?Math.toDegrees(m.getAngle()):0.);
                values.put("P_PU"+suffix,active?power.getReal():0.);values.put("Q_PU"+suffix,active?power.getImaginary():0.);
                values.put("PMECH"+suffix,active?m.getPm():0.);values.put("SPEED"+suffix,active?m.getSpeed()-1.:0.);
                values.put("ACTIVE"+suffix,active?1.:0.);
                if(recordMachineStates) {
                    var round=(com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine)m;
                    values.put("GEN_EP_Q"+suffix,round.getEq1());values.put("GEN_EP_D"+suffix,round.getEd1());
                    values.put("GEN_PSIK_D"+suffix,round.getPsikd());values.put("GEN_PSIK_Q"+suffix,round.getPsikq());
                    values.put("GEN_SPEED_DEV"+suffix,m.getSpeed()-1.);values.put("GEN_ANGLE"+suffix,m.getAngle());
                    values.put("EFD"+suffix,m.getEfd());
                }
            }
            var states=governor.getNamedStates();for(int i=0;i<NAMES.length;i++)values.put("STATE"+i,states.get(NAMES[i]));
            governor.readPublishedState(published,0);
            for(int i=0;i<6;i++)values.put("PUBLISHED"+i,published[i]);
            values.put("REFERENCE_INPUT",governor.getRefPoint());values.put("HISTORY.Valve",states.get("Valve deadband memory"));
            values.put("HISTORY.SpeedMode",states.get("Speed deadband mode"));
            values.put("VAR0",governor.getInitialReference());values.put("VAR1",governor.getSecondaryBaseScale());
            values.put("VAR3",governor.getSpeedDeadbandOutput());values.put("VAR4",governor.getEffectiveMaximum());values.put("VAR5",governor.getEffectiveMinimum());
            values.put("VAR6",governor.getBacklashInputHistory());values.put("VAR7",governor.getValveOutput());
            return new Frame(time,kind,values);
        }
    }

    static Recording simulate(Path raw,Path dynamic,boolean staggered,String event,double h,double duration,List<ReferenceEvent> references) throws Exception {
        return simulate(raw,dynamic,staggered,event,h,duration,references,algorithm->{});
    }
    static Recording simulate(Path raw,Path dynamic,boolean staggered,String event,double h,double duration,List<ReferenceEvent> references,
            java.util.function.Consumer<DynamicSimuAlgorithm> configure) throws Exception {
        return simulate(raw,dynamic,staggered,event,h,duration,references,configure,0);
    }
    static Recording simulate(Path raw,Path dynamic,boolean staggered,String event,double h,double duration,List<ReferenceEvent> references,
            java.util.function.Consumer<DynamicSimuAlgorithm> configure,int startupSteps) throws Exception {
        return simulate(raw,dynamic,staggered,event,h,duration,references,configure,startupSteps,false);
    }
    static Recording simulate(Path raw,Path dynamic,boolean staggered,String event,double h,double duration,List<ReferenceEvent> references,
            java.util.function.Consumer<DynamicSimuAlgorithm> configure,int startupSteps,boolean recordMachineStates) throws Exception {
        if(startupSteps<0)throw new IllegalArgumentException("Negative startup-step count");
        var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(raw.toString(),dynamic.toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());var a=context.getDynSimuAlgorithm();var net=context.getDStabilityNet();
        assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(duration);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());var recording=new Recording(a,event,references);
        recording.recordMachineStates=recordMachineStates;
        a.setSolver(staggered?new SharedRecorder(a,recording):new OrdinaryRecorder(a,recording));
        if(event.equals("fault"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.2*net.getBaseKva()/100000.),null,.05,.05),"fault");
        if(event.endsWith("-trip"))net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent(event.equals("primary-trip")?"Bus1":"Bus2","1",net,.05),"shaft-trip");
        configure.accept(a);
        assertTrue(a.initialization());recording.initial=recording.capture(0.,"INITIAL");a.setSimuTime(-startupSteps*h);recording.enabled=true;
        while(a.getSimuTime()<duration-h/2.)assertTrue(a.solveDEqnStep(true));
        recording.frames.add(recording.capture(a.getSimuTime(),"FINAL"));assertEquals(references.size(),recording.nextReference);
        var governor=(Wsieg1Governor)net.getMachine("Bus1-mach1").getGovernor();
        if(staggered)assertTrue(governor.getStaggeredEvaluationCount()>0,"Actual shared-state dispatch");else assertEquals(0,governor.getStaggeredEvaluationCount());
        return recording;
    }

    static class OrdinaryRecorder extends DefaultDynamicSimulationSolver {
        final Recording recording;
        OrdinaryRecorder(DynamicSimuAlgorithm a,Recording r){super(a);recording=r;converge_tol=TOLERANCE;}
        @Override public void beforeStep(double time)throws DStabSimuException{recording.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException {
            boolean solved=super.networkSolutionStep();if(solved&&recording.enabled&&"INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(),"GRID"));return solved;
        }
    }
    static class SharedRecorder extends StaggeredEulerSolver {
        final Recording recording;
        SharedRecorder(DynamicSimuAlgorithm a,Recording r){super(a);recording=r;setNetworkSolutionTolerance(TOLERANCE);setReuseNetworkEndpoint(false);}
        @Override public void beforeStep(double time)throws DStabSimuException{recording.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException {
            boolean solved=super.networkSolutionStep();if(solved&&recording.enabled&&"INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(),"GRID"));return solved;
        }
    }
    static void write(Path path,Recording recording)throws Exception {
        try(var raw=BenchmarkOutputGuard.newOutputStream(path,BenchmarkOutputGuard.estimateCsvBytes(recording.frames.size(),recording.initial.values.size()+2));
                var zip=new GZIPOutputStream(raw);var writer=new BufferedWriter(new OutputStreamWriter(zip,StandardCharsets.UTF_8))) {
            writer.write("time_s,sample_kind,"+String.join(",",recording.initial.values.keySet())+"\n");
            for(var row:recording.frames){writer.write(row.time+","+row.kind);for(double value:row.values.values()){assertTrue(Double.isFinite(value));writer.write(","+value);}writer.write("\n");}
        }
    }
}
