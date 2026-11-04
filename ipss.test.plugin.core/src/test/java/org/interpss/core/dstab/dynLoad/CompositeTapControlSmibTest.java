package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.NetworkIterationAware;

/** Independent target controls and owned tap-history rollback under both methods. */
public class CompositeTapControlSmibTest {
    private static final org.interpss.test.TestConfig CONFIG=org.interpss.test.TestConfig.load(
            "/test-config/ipss.test.plugin.core.json","CompositeTapControlSmibTest");
    static final Path ARCHIVE=Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/cmldblu1-tap-discriminators-20261101/captures.zip"));
    static byte[] entry(String name) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent tap-control captures");
        assertEquals("46df94e9454567a72351a1339d4cef93f5d7d93e4aa39972cfc8fdcb6caff680",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) { return zip.getInputStream(zip.getEntry(name)).readAllBytes(); }
    }
    static com.interpss.dstab.algo.DynamicSimuAlgorithm setup(String profile,boolean staggered) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path dyr=Files.createTempFile(Path.of("target"),"tap-control-",".dyr");
        Files.write(dyr,entry(profile+"/a/dynamic.dyr"));
        var loader=new org.interpss.fadapter.psse.PSSEMultiFileLoader();
        var context=loader.loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),dyr.toString());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.0005);
        algorithm.setTotalSimuTimeSec(.5);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        algorithm.setSolver(staggered ? new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm)
                : new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algorithm) {{ converge_tol=1e-8; }});
        if(staggered) {
            var solver=(org.interpss.dstab.algo.StaggeredEulerSolver)algorithm.getSolver();
            solver.setNetworkSolutionTolerance(1e-8);
            solver.setElectricalEndpointPreparationEnabled(true);
        }
        assertTrue(algorithm.initialization());return algorithm;
    }
    static Cmldblu1Model model(com.interpss.dstab.algo.DynamicSimuAlgorithm a) {
        return (Cmldblu1Model)a.getNetwork().getDStabBus("Bus2").getInfoOnlyDynModel();
    }
    @ParameterizedTest @CsvSource({"false,up","true,up","false,down","true,down","false,reversal","true,reversal","false,deadband","true,deadband"})
    void targetTapStatesAndDeadband(boolean staggered,String profile) throws Exception {
        var a=setup(profile,staggered);var m=model(a);
        var feeder=a.getNetwork().getBranchList().stream().filter(b->b.getFromBus()==m.getLowVoltBus()
                && b.getToBus()==m.getLoadBus()).findFirst().orElseThrow();
        var sourceBranch=a.getNetwork().getBranchList().stream()
                .filter(b->b.getFromBus().getId().equals("Bus1") && b.getToBus()==m.getDStabBus())
                .findFirst().orElseThrow();
        String[] lines=new String(entry(profile+"/a/native.csv"),StandardCharsets.UTF_8).strip().split("\\R");
        String[] keys=lines[0].split(",");Map<String,Integer> index=new HashMap<>();
        for(int j=0;j<keys.length;j++)index.put(keys[j],j);
        int current=0;boolean first=false,second=false;double maxTap=0,maxV=0,maxAngle=0,old=m.getDistributionTransformer().getToTurnRatio();
        var metrics=new TreeMap<String,double[]>();
        var diagnostic=new ArrayList<String>();
        diagnostic.add("time,tap,low_v,load_v,parent_v,parent_a,static_p,static_q,e_p,e_q,machine_p,machine_q,angle");
        for(String name:List.of("V1","V2","V3","P_PU","Q_PU","SPEED","ANGLE_DEG"))
            metrics.put(name,new double[]{0,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0});
        for(int j=1;j<lines.length;j++) {
            String[] values=lines[j].split(",");double t=Double.parseDouble(values[0]);if(t<0)continue;
            int step=(int)Math.round(t/.0005);
            while(current<step) {
                if(current==100 && !first) { double shift=profile.equals("down") ? -.1 : .1;
                    m.setTapVoltageBand(1.015+shift,1.045+shift);first=true; }
                if(current==200 && !second) {
                    if(profile.equals("reversal"))m.setTapVoltageBand(.915,.945);
                    if(profile.equals("deadband"))m.setTapVoltageBand(1.030,1.060);
                    second=true;
                }
                assertTrue(a.solveDEqnStep(true));current++;
                double tap=m.getDistributionTransformer().getToTurnRatio();assertTrue(Math.abs(tap-old)<.00500001,"Incremental physical tap");old=tap;
            }
            double tap=m.getDistributionTransformer().getToTurnRatio();
            var low=m.getLowVoltBus().getVoltage();var load=m.getLoadBus().getVoltage();var parent=m.getDStabBus().getVoltage();
            var incoming=parent.subtract(low.divide(tap)).divide(m.getDistributionTransformer().getZ());
            var downstream=low.subtract(load).divide(feeder.getZ());var states=m.getNamedStates();
            var demand=new org.apache.commons.math3.complex.Complex(states.get("STATIC.P")+states.get("ELECTRONIC.P"),
                    states.get("STATIC.Q")+states.get("ELECTRONIC.Q"));
            assertTrue(incoming.divide(tap).subtract(downstream).subtract(m.getLowVoltBus().getShuntY().multiply(low)).abs()<2e-6,"Low-side KCL at every endpoint");
            assertTrue(downstream.subtract(demand.divide(load).conjugate()).subtract(m.getLoadBus().getShuntY().multiply(load)).abs()<2e-6,"Load KCL at every endpoint");
            assertTrue(Math.abs(parent.multiply(incoming.conjugate()).getReal()-demand.getReal()-downstream.abs()*downstream.abs()*feeder.getZ().getReal())<2e-6,"Passive power balance at every endpoint");
            var physicalDemand=demand.add(feeder.getZ().multiply(downstream.abs()*downstream.abs()))
                    .add(m.getDistributionTransformer().getZ().multiply(incoming.abs()*incoming.abs()))
                    .add(low.multiply(m.getLowVoltBus().getShuntY().multiply(low).conjugate()))
                    .add(load.multiply(m.getLoadBus().getShuntY().multiply(load).conjugate()));
            assertTrue(parent.multiply(incoming.conjugate()).subtract(physicalDemand).abs()<2e-6,
                    "Complex power includes feeder/transformer losses and both physical shunts");
            maxTap=Math.max(maxTap,Math.abs(tap-Double.parseDouble(values[index.get("VAR12")])));
            maxV=Math.max(maxV,Math.abs(m.getLowVoltBus().getVoltageMag()-Double.parseDouble(values[index.get("VAR30")])));
            maxAngle=Math.max(maxAngle,Math.abs(Math.toDegrees(a.getNetwork().getMachine("Bus1-mach1").getAngle())-Double.parseDouble(values[index.get("ANGLE_DEG")])));
            var machine=a.getNetwork().getMachine("Bus1-mach1");var voltage=machine.getDStabBus().getVoltage();
            var power=voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate());
            var sourceCurrent=sourceBranch.yff().multiply(voltage).add(sourceBranch.yft().multiply(parent));
            var sourcePower=voltage.multiply(sourceCurrent.conjugate());
            assertTrue(power.subtract(sourcePower).abs()<1e-6,
                    "Recorded machine power and independently solved branch power share an endpoint");
            if(CONFIG.bool("recording.enabled")) {
                var s=m.getNamedStates();
                diagnostic.add(String.join(",",Double.toString(t),Double.toString(tap),Double.toString(m.getLowVoltBus().getVoltageMag()),
                        Double.toString(m.getLoadBus().getVoltageMag()),Double.toString(m.getDStabBus().getVoltageMag()),Double.toString(Math.toDegrees(m.getDStabBus().getVoltageAng())),
                        s.get("STATIC.P").toString(),s.get("STATIC.Q").toString(),s.get("ELECTRONIC.P").toString(),s.get("ELECTRONIC.Q").toString(),
                        Double.toString(power.getReal()),Double.toString(power.getImaginary()),Double.toString(Math.toDegrees(machine.getAngle()))));
            }
            for(var e:metrics.entrySet()) {
                String name=e.getKey();double reference=Double.parseDouble(values[index.get(name)]);
                double actual=switch(name) {
                    case "P_PU" -> power.getReal(); case "Q_PU" -> power.getImaginary();
                    case "SPEED" -> machine.getSpeed()-1;case "ANGLE_DEG" -> Math.toDegrees(machine.getAngle());
                    default -> a.getNetwork().getDStabBus("Bus"+name.substring(1)).getVoltageMag();
                };
                var metric=e.getValue();metric[0]+=(actual-reference)*(actual-reference);
                metric[1]=Math.min(metric[1],reference);metric[2]=Math.max(metric[2],reference);metric[3]++;
            }
            assertEquals(tap,m.getNamedStates().get("LTC.Tap"),1e-14,"Only committed history is recorded");
        }
        System.out.printf("TAP_DIAGNOSTIC %s %s maxTap=%.12g maxLowV=%.12g maxAngle=%.12g%n",staggered,profile,maxTap,maxV,maxAngle);
        if(CONFIG.bool("recording.enabled")) {
            Path output=Path.of(CONFIG.string("recording.directory","target"),"tap-runtime-"+profile+"-"+staggered+".csv");
            byte[] bytes=String.join("\n",diagnostic).getBytes(StandardCharsets.UTF_8);
            try(var stream=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newOutputStream(output,bytes.length)){stream.write(bytes);}
        }
        assertTrue(first && second); assertTrue(current>=1000);
        if(staggered)assertEquals(0,((org.interpss.dstab.algo.StaggeredEulerSolver)a.getSolver()).getElectricalEndpointPreparationCount(),
                "A discrete-only tap owner must preserve the existing machine integration path");
        if(profile.equals("deadband")) assertEquals(1.0651980198,old,2e-7,"Hold tap after return to deadband");
        else {
            assertTrue(maxTap<1.2e-7,"Same tap at every reference sample");assertTrue(maxV<6e-5,"Low-side voltage");
            boolean trajectoryQualified=true;
            for(var e:metrics.entrySet()) {
                var metric=e.getValue();double rmse=Math.sqrt(metric[0]/metric[3]);double range=metric[2]-metric[1];
                System.out.printf("TAP_ERROR %s %s %s RMSE=%.12g NRMSE=%.12g%n",staggered,profile,e.getKey(),rmse,rmse/range);
                // Retain strict full-trajectory assessment separately from tap-state qualification.
                // At a tap action, the reference publishes pre-action power with the new tap.
                trajectoryQualified &= e.getKey().equals("ANGLE_DEG") ? rmse<.0002 : range>1e-10 ? rmse/range<.001 : rmse<1e-7;
            }
            System.out.printf("TAP_FULL_TRAJECTORY_QUALIFIED %s %s %s%n",staggered,profile,trajectoryQualified);
        }
    }
    @Test
    void stageOwnershipRejectsInvalidCallsWithoutChangingHistory() throws Exception {
        var a=setup("up",false);var m=model(a);
        var port=m.getDStabBus().getDynLoadModelList().stream().filter(v->v.getId().endsWith("_Tap")).findFirst().orElseThrow();
        var participant=(org.interpss.dstab.control.util.StaggeredEulerParticipant)port;
        var aware=(NetworkIterationAware)port;
        var observed=(com.interpss.dstab.controller.cml.ICMLStateProvider)port;
        var initial=observed.getNamedStates();
        assertThrows(IllegalStateException.class,()->port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));
        assertThrows(IllegalArgumentException.class,()->port.nextStep(Double.NaN,DynamicSimuMethod.MODIFIED_EULER,0));
        port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0);
        assertThrows(IllegalStateException.class,()->port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
        assertThrows(IllegalStateException.class,()->port.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        assertThrows(IllegalStateException.class,()->participant.completeStaggeredStep(.0005,.0005));
        assertThrows(IllegalStateException.class,()->m.setTapVoltageBand(1.115,1.145));
        // Event refresh is read-only, including between predictor and corrector.
        port.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0);
        port.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,1);
        participant.completeStaggeredStep(0,0);
        assertEquals(initial,observed.getNamedStates());
        port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1);
        assertThrows(IllegalStateException.class,()->m.setTapVoltageBand(1.115,1.145));
        assertEquals(initial,observed.getNamedStates());
        aware.beginNetworkSolution(new NetworkIterationAware.Context("CORRECTOR",0,.0005,50,1e-5));
        aware.endNetworkSolution(true);
        assertEquals(.0005,observed.getNamedStates().get("LTC.Time"),0);
        m.setTapVoltageBand(1.115,1.145);
        port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0);
        aware.beginNetworkSolution(new NetworkIterationAware.Context("PREDICTOR",.0005,.001,50,1e-5));
        aware.endNetworkSolution(false);
        // Failed prediction can be retried without consuming the timer or sampling history.
        assertEquals(.0005,observed.getNamedStates().get("LTC.Time"),0);
        port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0);
        port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1);
        aware.beginNetworkSolution(new NetworkIterationAware.Context("CORRECTOR",.0005,.001,50,1e-5));
        aware.endNetworkSolution(true);
        assertEquals(.001,observed.getNamedStates().get("LTC.Time"),0);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void failureRestoresTapMatrixAndHistories(boolean staggered) throws Exception {
        var a=setup("up",staggered);var m=model(a);var net=a.getNetwork();
        var port=m.getDStabBus().getDynLoadModelList().stream().filter(v->v.getId().endsWith("_Tap")).findFirst().orElseThrow();
        var participant=(org.interpss.dstab.control.util.StaggeredEulerParticipant)port;
        var aware=(NetworkIterationAware)port;var observed=(com.interpss.dstab.controller.cml.ICMLStateProvider)port;
        assertEquals(port,m.getIntegrationStateOwners().get("TapControl"));
        assertTrue(m.getIntegrationStateOwners().get("TapControl").getIntegrationStates().isEmpty());
        assertEquals(org.apache.commons.math3.complex.Complex.ZERO,port.getInitLoadPQ());
        assertEquals(0,port.getEquivY().abs(),"No numerical shunt contribution, including signed zero");
        m.setTapVoltageBand(1.115,1.145);
        double tap=m.getDistributionTransformer().getToTurnRatio();var history=observed.getNamedStates();
        int f=m.getDStabBus().getSortNumber(),t=m.getLowVoltBus().getSortNumber();
        var matrix=new org.apache.commons.math3.complex.Complex[]{net.getYMatrix().getA(f,f),net.getYMatrix().getA(f,t),net.getYMatrix().getA(t,f),net.getYMatrix().getA(t,t)};
        for(int n=0;n<40;n++) {
            if(staggered)participant.completeStaggeredStep(.0005,(n+1)*.0005);
            else {port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0);port.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1);}
            aware.beginNetworkSolution(new NetworkIterationAware.Context("CORRECTOR",n*.0005,(n+1)*.0005,50,1e-5));
            if(n<39) {aware.endNetworkSolution(true);history=observed.getNamedStates();}
        }
        assertEquals(tap+.005,m.getDistributionTransformer().getToTurnRatio(),1e-13);
        assertEquals(history,observed.getNamedStates(),"Trial history is not published");
        aware.endNetworkSolution(false);
        assertEquals(tap,m.getDistributionTransformer().getToTurnRatio(),1e-13);assertEquals(history,observed.getNamedStates());
        var actual=new org.apache.commons.math3.complex.Complex[]{net.getYMatrix().getA(f,f),net.getYMatrix().getA(f,t),net.getYMatrix().getA(t,f),net.getYMatrix().getA(t,t)};
        for(int n=0;n<4;n++)assertTrue(matrix[n].subtract(actual[n]).abs()<1e-13);
        assertTrue(net.isYMatrixDirty());
        int count=m.getDStabBus().getDynLoadModelList().size();assertTrue(port.initStates());
        assertEquals(0,observed.getNamedStates().get("LTC.Time"));
        assertEquals(tap,observed.getNamedStates().get("LTC.Tap"));
        assertEquals(count,m.getDStabBus().getDynLoadModelList().size());
        net.formYMatrix4DStab();
        assertTrue(matrix[1].subtract(net.getYMatrix().getA(f,t)).abs()<1e-13,"Rebuilt transformer stamp exactly once");
        m.getDistributionTransformer().setStatus(false);
        var inactiveHistory=observed.getNamedStates();
        participant.completeStaggeredStep(.1,.1);
        assertEquals(inactiveHistory,observed.getNamedStates(),"Disconnected transformer cannot advance tap history");
        m.getDistributionTransformer().setStatus(true);
    }
}
