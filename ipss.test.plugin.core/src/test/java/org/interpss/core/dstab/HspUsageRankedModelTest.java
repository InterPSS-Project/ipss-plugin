package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import com.interpss.dstab.controller.gov.weccwsieg1.Wsieg1Governor;
import org.interpss.dstab.relay.Tiocr1Data;
import org.interpss.dstab.relay.Tiocr1RelayModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Exact source layouts and complete small-network integration of the usage-ranked additions. */
public class HspUsageRankedModelTest {
    private static final Path CASE=Path.of("testData/adpter/psse/v33/HspModels");
    @BeforeAll static void setup() { IpssCorePlugin.init(); }

    @ParameterizedTest
    @ValueSource(strings={"pss2b","pss2b-remote","hyg3u1-0","hyg3u1-1","hyg3u1-deadband","hyg3u1-deadband-zero","hyg3u1-asymmetric","hyg3u1-partial","wsieg1","wsieg1-curve","wsieg1-dual","wsieg1-unused-lp","tiocr1-0","tiocr1-1","tiocr1-current","cmldblu1","cmldblu1-electronic"})
    void exactLayoutRunsFaultOnSmallNetwork(String fixture) throws Exception {
        run(fixture,false);
    }
    @ParameterizedTest
    @ValueSource(strings={"pss2b","hyg3u1-0","hyg3u1-1","hyg3u1-deadband","hyg3u1-deadband-zero","hyg3u1-asymmetric","hyg3u1-partial","wsieg1","wsieg1-curve","wsieg1-dual","wsieg1-unused-lp","cmldblu1","cmldblu1-electronic"})
    void noEventEquilibriumRemainsStationary(String fixture) throws Exception {
        run(fixture,true);
    }
    static void run(String fixture,boolean flat) throws Exception {
        run(fixture,flat,false);
    }
    static void run(String fixture,boolean flat,boolean staggered) throws Exception {
        run(fixture,flat,staggered,false);
    }
    static void run(String fixture,boolean flat,boolean staggered,boolean resetProfile) throws Exception {
        run(fixture,flat,staggered,resetProfile,
                CASE.resolve(fixture.startsWith("cmldblu1")?"load-network.raw":fixture.equals("wsieg1-dual")?"dual-network.raw":"network.raw"),
                CASE.resolve(fixture+".dyr"),Path.of("target/hsp-model-actual-20261030"));
    }
    static void run(String fixture,boolean flat,boolean staggered,boolean resetProfile,
            Path rawFile,Path dynamicFile,Path out) throws Exception {
        run(fixture,flat,staggered,resetProfile,rawFile,dynamicFile,out,.2,"Bus1");
    }
    static void run(String fixture,boolean flat,boolean staggered,boolean resetProfile,
            Path rawFile,Path dynamicFile,Path out,double faultReactance,
            String faultBus) throws Exception {
        run(fixture,flat,staggered,resetProfile,rawFile,dynamicFile,out,faultReactance,faultBus,.0005);
    }
    static void run(String fixture,boolean flat,boolean staggered,boolean resetProfile,
            Path rawFile,Path dynamicFile,Path out,double faultReactance,
            String faultBus,double dt) throws Exception {
        assertTrue(Double.isFinite(dt)&&dt>0);
        assertFalse(resetProfile && (flat || !fixture.startsWith("tiocr1-")));
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(rawFile.toString(),dynamicFile.toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        if(fixture.equals("tiocr1-current")||fixture.equals("tiocr1-load-only")) {net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I);}
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(dt);algorithm.setTotalSimuTimeSec(.5);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        var recording=new SmallNetworkRecorder(algorithm,flat,resetProfile);
        algorithm.setSolver(staggered?new StaggeredNetworkRecorder(algorithm,recording):recording);
        if(!flat)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(fixture.startsWith("cmldblu1")?"Bus2":faultBus,net,SimpleFaultCode.GROUND_3P,new Complex(0,fixture.equals("cmldblu1-electronic")?.01:faultReactance),null,.05,resetProfile?.01:.05),"Fault");
        if(resetProfile)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,
                new Complex(0,.2),null,.2,.05),"Second fault");
        assertTrue(algorithm.initialization(),fixture+" initialization");
        assertEquals(com.interpss.dstab.ConstantCurrentLoadVoltageCurve.ELLIPTIC,net.getConstantCurrentLoadVoltageCurve());
        var machine=net.getMachine("Bus1-mach1");
        double initialV=net.getDStabBus("Bus1").getVoltageMag(),initialAngle=machine.getAngle();
        var initial=capture(net);var columns=new ArrayList<>(initial.keySet());
        recording.enabled=true;
        var rows=new ArrayList<String>();rows.add("time_s,"+String.join(",",columns)+",sample_kind,solver_time_s");
        double maxDrift=0;
        while(algorithm.getSimuTime()<.5-dt/2) {
            assertTrue(algorithm.solveDEqnStep(true),fixture+" integration at "+algorithm.getSimuTime());
            double v=net.getDStabBus("Bus1").getVoltageMag();
            assertTrue(Double.isFinite(v)&&Double.isFinite(machine.getAngle())&&Double.isFinite(machine.getPm()));
            maxDrift=Math.max(maxDrift,Math.abs(v-initialV));

        }
        if(flat) {
            assertTrue(maxDrift<1e-5,fixture+" flat voltage drift "+maxDrift);
            assertEquals(initialAngle,machine.getAngle(),1e-5,fixture+" flat angle");
        }
        recording.rows.add(new Captured(algorithm.getSimuTime(),"FINAL",capture(net)));
        if(fixture.equals("tiocr1-load-only")) {
            assertTrue(net.getBranchList().stream().allMatch(b->b.isActive()),"Load-only protection preserves lines");
            var relay=(Tiocr1RelayModel)net.getDStabBus("Bus1").getDynamicBusDeviceList().stream()
                    .filter(Tiocr1RelayModel.class::isInstance).findFirst().orElseThrow();
            assertTrue(relay.isOperated());assertEquals(.1,relay.getShedFraction(),1e-12);
            assertEquals(1,relay.getNetworkActionVersion());
        }
        for(var frame:recording.rows) {
            rows.add(Double.toString(frame.time())+","+frame.values().values().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","))
                    +","+frame.kind()+","+frame.time());
        }
        Files.createDirectories(out);
        Files.write(out.resolve(fixture+(resetProfile?"-reset":"")+(staggered?"-staggered":"")+(flat?"-flat":"-fault")+".csv"),rows);
    }

    private record Captured(double time,String kind,SortedMap<String,Double> values) { }
    /** Observe the solved algebraic boundary at each accepted grid time, before advancing states. */
    private static final class SmallNetworkRecorder extends org.interpss.dstab.algo.DefaultDynamicSimulationSolver {
        final List<Captured> rows=new ArrayList<>();final boolean flat,resetProfile;boolean enabled;
        private Map<String,Double> protectionLeft;
        private long protectionRevision;
        SmallNetworkRecorder(com.interpss.dstab.algo.DynamicSimuAlgorithm algorithm,boolean flat,boolean resetProfile) {
            super(algorithm);this.flat=flat;this.resetProfile=resetProfile;
        }
        @Override public void beforeStep(double t) throws com.interpss.dstab.common.DStabSimuException {
            captureBeforeStep(t);
            super.beforeStep(t);
        }
        void captureBeforeStep(double t) {
            boolean scheduled=!flat&&(Math.abs(t-.05)<1e-9||Math.abs(t-(resetProfile?.06:.1))<1e-9
                    ||resetProfile&&(Math.abs(t-.2)<1e-9||Math.abs(t-.25)<1e-9));
            if(enabled&&(scheduled||(t>1e-9&&dstabAlgo.getNetwork().isYMatrixDirty()))) {
                var values=capture(dstabAlgo.getNetwork());
                if(protectionLeft!=null&&relayRevision()!=protectionRevision) {
                    values.putAll(protectionLeft);
                    for(var bus:dstabAlgo.getNetwork().getBusList())for(var device:bus.getDynamicBusDeviceList())
                        if(device instanceof Tiocr1RelayModel relay&&relay.getLastActionSnapshot()!=null) {
                            var action=relay.getLastActionSnapshot();
                            values.put("RELAY.Current",action.current());values.put("RELAY.Position",action.position());
                            values.put("RELAY.Breaker",action.breakerTimer());
                            values.put("RELAY.Operated",action.operatedLatch()?1.:0.);
                        }
                }
                rows.add(new Captured(t,"EVENT_PRE",values));
            }
            protectionLeft=null;
        }
        private long relayRevision() {
            long revision=0;
            for(var bus:dstabAlgo.getNetwork().getBusList())for(var device:bus.getDynamicBusDeviceList())
                if(device instanceof Tiocr1RelayModel relay)revision+=relay.getNetworkActionVersion();
            return revision;
        }
        void captureProtectionLeft() {
            if(!enabled)return;
            protectionRevision=relayRevision();
            for(var bus:dstabAlgo.getNetwork().getBusList())for(var device:bus.getDynamicBusDeviceList())
                if(device instanceof Tiocr1RelayModel relay) {
                    protectionLeft=capture(dstabAlgo.getNetwork());
                }
        }
        @Override public void afterStep(double t) throws com.interpss.dstab.common.DStabSimuException {
            captureProtectionLeft();super.afterStep(t);
        }
        @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
            boolean solved=super.networkSolutionStep();
            if(solved&&enabled&&"INITIAL".equals(networkSolutionStage))
                rows.add(new Captured(dstabAlgo.getSimuTime(),"GRID",capture(dstabAlgo.getNetwork())));
            return solved;
        }
    }

    /** Same observation boundary for the staggered integration path. */
    private static final class StaggeredNetworkRecorder extends org.interpss.dstab.algo.StaggeredEulerSolver {
        private final SmallNetworkRecorder recording;
        StaggeredNetworkRecorder(com.interpss.dstab.algo.DynamicSimuAlgorithm algorithm,SmallNetworkRecorder recording) {
            super(algorithm);this.recording=recording;setReuseNetworkEndpoint(false);
        }
        @Override public void beforeStep(double t) throws com.interpss.dstab.common.DStabSimuException {
            recording.captureBeforeStep(t);super.beforeStep(t);
        }
        @Override public void afterStep(double t) throws com.interpss.dstab.common.DStabSimuException {
            recording.captureProtectionLeft();super.afterStep(t);
        }
        @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
            boolean solved=super.networkSolutionStep();
            if(solved&&recording.enabled&&"INITIAL".equals(networkSolutionStage))
                recording.rows.add(new Captured(dstabAlgo.getSimuTime(),"GRID",capture(dstabAlgo.getNetwork())));
            return solved;
        }
    }

    private static SortedMap<String,Double> capture(BaseDStabNetwork<?,?> net) {
        var values=new TreeMap<String,Double>();var machine=net.getMachine("Bus1-mach1");
        var voltage=net.getDStabBus("Bus1").getVoltage();
        var power=voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate());
        values.put("V1",voltage.abs());values.put("V2",net.getDStabBus("Bus2").getVoltageMag());
        values.put("ANGLE_DEG",net.getDStabBus("Bus1").isActive()?Math.toDegrees(machine.getAngle()):0.);
        values.put("P_PU",power.getReal());values.put("Q_PU",power.getImaginary());
        values.put("PMECH",machine.getPm());values.put("SPEED",machine.getSpeed()-1);
        if(net.getMachine("Bus2-mach1")!=null && net.getMachine("Bus3-mach1")!=null) {
            values.put("PMECH_LP",net.getMachine("Bus2-mach1").getPm());values.put("SPEED_LP",net.getMachine("Bus2-mach1").getSpeed()-1);
        }
        values.put("STABILIZER",machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine));
        if(machine.getGovernor() instanceof ICMLStateProvider provider)
            provider.getNamedStates().forEach((k,v)->values.put("GOV."+k,v));
        for(var bus:net.getBusList())for(var device:bus.getDynamicBusDeviceList())
            if(device instanceof Tiocr1RelayModel relay) {
                var side=net.getDStabBus("Bus3");
                if(side!=null)values.put("V3",side.getVoltageMag());
                values.put("RELAY.Current",relay.currentMagnitude());values.put("RELAY.Position",relay.getPosition());values.put("RELAY.Breaker",relay.getBreakerTimer());
                values.put("RELAY.Operated",relay.isOperated()?1.:0.);values.put("RELAY.ShedFraction",relay.getShedFraction());
                values.put("RELAY.Revision",(double)relay.getNetworkActionVersion());
                captureBranchStatus(net,values);
            }
        return values;
    }
    private static void captureBranchStatus(BaseDStabNetwork<?,?> net,Map<String,Double> values) {
        for(var branch:net.getBranchList())values.put("BRANCH."+branch.getId(),branch.isActive()?1.:0.);
    }

    @Test void inverseTimeCurveAndResetUsePhysicalCurrentBase() {
        var data=new Tiocr1Data(new double[]{.2,.1,1.1,.04,1.5,.03,2,.02,3,.01,5,.005,.01,.1});
        assertEquals(3600,data.timeToClose(.2),0);
        assertEquals(.035,data.timeToClose(.26),1e-12);
        assertEquals(.005,data.timeToClose(2),0);
        assertEquals(-10,data.rate(0),0);
        assertEquals(-7.5,data.rate(.1),1e-12);
        assertThrows(IllegalArgumentException.class,()->data.rate(Double.NaN));
    }

    @Test void steamEventRefreshCannotAdvanceStateOrCommitHistory() throws Exception {
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("network.raw").toString(),CASE.resolve("wsieg1-curve.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        context.getDynSimuAlgorithm().setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(context.getDynSimuAlgorithm().initialization());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var governor=(Wsieg1Governor)machine.getGovernor();var initial=governor.getNamedStates();
        machine.setSpeed(1.001);
        assertTrue(governor.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertEquals(initial,governor.getNamedStates());
        assertFalse(governor.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        assertEquals(initial,governor.getNamedStates());
    }

    @Test void voltageDerivativeInputIsFiniteAtZeroDurationEventRefresh() throws Exception {
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("network.raw").toString(),CASE.resolve("pss2b-remote.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        context.getDynSimuAlgorithm().setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(context.getDynSimuAlgorithm().initialization());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var pss=(com.interpss.dstab.controller.pss.ieee1992pss2b.Ieee1992PSS2BStabilizer)machine.getStabilizer();
        pss.getData().setIcs1(6);
        assertTrue(pss.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(Double.isFinite(pss.input1Signal));
        context.getDStabilityNet().getDStabBus("Bus2").setVoltage(new Complex(.9,0));
        assertTrue(pss.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(Double.isFinite(pss.input1Signal));
    }

    @Test void relayShedAdmittanceSurvivesRebuildAndIsAppliedExactlyOnce() throws Exception {
        var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("network.raw").toString(),CASE.resolve("tiocr1-1.dyr").toString());
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        var bus=net.getDStabBus("Bus2");
        var relay=(Tiocr1RelayModel)net.getDStabBus("Bus1").getDynamicBusDeviceList().stream().filter(Tiocr1RelayModel.class::isInstance).findFirst().orElseThrow();
        net.getDStabBus("Bus1").setVoltage(new Complex(.8,0));
        for(int i=0;i<5;i++)assertTrue(relay.afterStep(.01));
        assertTrue(relay.isOperated());assertEquals(.1,relay.getShedFraction(),1e-12);
        assertEquals(1,relay.getNetworkActionVersion());
        assertTrue(relay.afterStep(.01));assertEquals(1,relay.getNetworkActionVersion(),"One revision per accepted physical action");
        int index=bus.getSortNumber();var expected=net.getYMatrix().getA(index,index);
        net.formYMatrix4DStab();
        org.interpss.dstab.relay.AbstractStagedLoadSheddingRelayModel.reapplyAfterNetworkRebuild(net);
        assertEquals(0,expected.subtract(net.getYMatrix().getA(index,index)).abs(),1e-12);
        org.interpss.dstab.relay.AbstractStagedLoadSheddingRelayModel.reapplyAfterNetworkRebuild(net);
        assertEquals(0,expected.subtract(net.getYMatrix().getA(index,index)).abs(),1e-12);
    }

    @Test void splitShaftAttachmentIsIndependentOfRecordOrder(@TempDir Path temporary) throws Exception {
        var lines=Files.readAllLines(CASE.resolve("wsieg1-dual.dyr"));
        Collections.rotate(lines,2);Path dynamic=temporary.resolve("dual.dyr");Files.write(dynamic,lines);
        var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(CASE.resolve("dual-network.raw").toString(),dynamic.toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        context.getDynSimuAlgorithm().setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(context.getDynSimuAlgorithm().initialization());
        var machine=context.getDStabilityNet().getMachine("Bus2-mach1");
        assertEquals(machine.getPm(),machine.getGovernor().getOutput(machine),1e-7);
    }

    @Test void sourceSchemaRejectsInvalidRemoteAndAllocation(@TempDir Path temporary) throws Exception {
        for(String fixture:List.of("pss2b-remote","hyg3u1-1")) {
            var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("network.raw").toString());
            String text=Files.readString(CASE.resolve(fixture+".dyr"));
            text=fixture.startsWith("pss")?text.replace("5 2 3 0 5 1","5 9999 3 0 5 1")
                    :text.replace("5 0 2 36 9 5","5 0 2 35 9 5");
            var path=temporary.resolve(fixture+".dyr");Files.writeString(path,text);
            var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
            assertThrows(Exception.class,()->parser.parseDynFile(path.toString()));
        }
    }
}
