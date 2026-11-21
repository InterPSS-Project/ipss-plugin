package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Source-defined accepted protection history and subsequent physical application. */
public class CompositeMotorProtectionTimingTest {
    static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cmldblu1-motor-protection-timing-20261104/captures.zip");
    @ParameterizedTest @CsvSource({"false,zero-flat","true,zero-flat","false,zero-fault","true,zero-fault","false,cancelled","true,cancelled"})
    void zeroDelayAndCancelledPickup(boolean staggered,String scenario)throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals("049a8562c7501471918cb23008bc1c586a42a1812478ee85936a2cc7535a9d23",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        boolean flat=scenario.equals("zero-flat");String profile=flat?"flat":"repeated-faults";
        String prefix="target/cmld-motor-uv-enabled-"+scenario+"-cleared-20261104/cmldblu1-mixed-"+profile+"-h0.0005-";
        var temporary=Files.createTempDirectory(Path.of("target"),"motor-timing-");Path dynamic=temporary.resolve("dynamic.dyr");
        List<Map<String,Double>> rows;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            var raw=CompositeMotorRecloseTest.read(zip,prefix+"a-part1/native.csv");
            assertArrayEquals(raw,CompositeMotorRecloseTest.read(zip,prefix+"b-part1/native.csv"));
            rows=CompositeMotorRecloseTest.csv(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).stream().filter(r->r.get("time_s")>=0).toList();
            Files.write(dynamic,CompositeMotorRecloseTest.read(zip,prefix+"a-part1/dynamic.dyr"));
        }
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var actual=Cmldblu1SharedRecording.simulateControl(dynamic,staggered,flat,.0005,.2,.2,.5);
        if(flat) {
            // The reference resumes RUN at .05/.10 even without an event. Retain both
            // observations of that identical accepted state without advancing history.
            var expanded=new ArrayList<Cmldblu1SharedRecording.Frame>();int next=0;
            for(int i=0;i<rows.size();i++) {
                if(i>0&&rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))) {
                    var prior=expanded.getLast();expanded.add(new Cmldblu1SharedRecording.Frame(prior.time(),"RUN_CONTINUATION",prior.values()));
                } else expanded.add(actual.frames.get(next++));
            }
            assertEquals(actual.frames.size(),next);actual.frames.clear();actual.frames.addAll(expanded);
        }
        actual.write(temporary.resolve("complete.csv.gz"));
        assertEquals(rows.size(),actual.frames.size());int actions=0;var peaks=new TreeMap<String,Double>();var squares=new TreeMap<String,Double>();
        for(String k:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG","SPEED")){peaks.put(k,0.);squares.put(k,0.);}
        for(int i=0;i<rows.size();i++) {
            var r=rows.get(i);var a=actual.frames.get(i);assertEquals(r.get("time_s"),a.time(),5e-6);
            assertEquals(r.get("VAR83"),a.values().get("MotorA.UvFraction"),0,"Exact unshifted fraction row "+i);
            if(i>0&&!r.get("VAR83").equals(rows.get(i-1).get("VAR83")))actions++;
            for(String k:peaks.keySet()){double e=a.values().get(k)-r.get(k);peaks.put(k,Math.max(peaks.get(k),Math.abs(e)));squares.put(k,squares.get(k)+e*e);}
        }
        assertEquals(scenario.equals("zero-fault")?4:0,actions);
        var normalized=new TreeMap<String,Double>();
        for(String k:peaks.keySet()) {
            double span=rows.stream().mapToDouble(r->r.get(k)).max().orElseThrow()-rows.stream().mapToDouble(r->r.get(k)).min().orElseThrow();
            if(span>1e-12)normalized.put(k,Math.sqrt(squares.get(k)/rows.size())/span);
        }
        System.out.println("MOTOR TIMING staggered="+staggered+" scenario="+scenario+" peaks="+peaks+" NRMSE="+normalized+" recording="+temporary.getFileName());
        if(flat) {
            for(String k:List.of("V1","V2","V3"))assertTrue(peaks.get(k)<1e-5,k);
            for(String k:List.of("P_PU","Q_PU"))assertTrue(peaks.get(k)<1e-4,k);
        } else for(String k:List.of("V1","V2","V3","P_PU","Q_PU","SPEED"))assertTrue(normalized.get(k)<2e-4,k+"="+normalized.get(k));
        assertTrue(peaks.get("ANGLE_DEG")<2e-4,"Rotor peak");
    }
    @org.junit.jupiter.api.Test
    void independentHistoryPrecedesNetworkThenElectricalState()throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(CompositeMotorRecloseTest.ARCHIVE));
        try(var zip=new ZipFile(CompositeMotorRecloseTest.ARCHIVE.toFile())) {
            var a=CompositeMotorRecloseTest.csv(new String(CompositeMotorRecloseTest.read(zip,CompositeMotorRecloseTest.prefix("enabled","a",1)+"native.csv"),java.nio.charset.StandardCharsets.UTF_8));
            var b=CompositeMotorRecloseTest.csv(new String(CompositeMotorRecloseTest.read(zip,CompositeMotorRecloseTest.prefix("disabled-cleared","a",1)+"native.csv"),java.nio.charset.StandardCharsets.UTF_8));
            int history=firstChange(a,b,"VAR83");assertEquals(.055,a.get(history).get("time_s"),5e-6);
            for(String key:List.of("V1","V2","V3","A1","A2","A3"))assertEquals(history+1,firstChange(a,b,key),key+" physical network stage");
            for(String key:List.of("STATE0","STATE1","STATE2","STATE3","STATE4"))assertEquals(history+2,firstChange(a,b,key),key+" electrical response stage");
            assertEquals(0,boundaryCurrent(a.get(history)).subtract(boundaryCurrent(b.get(history))).abs(),0);
            assertTrue(boundaryCurrent(a.get(history+1)).subtract(boundaryCurrent(b.get(history+1))).abs()>1e-3,"Physical KCL boundary changes at the network stage");
        }
    }
    @org.junit.jupiter.api.Test
    void capacityActionPreservesPendingProtectionAndWarmCopy()throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(CompositeMotorRecloseTest.ARCHIVE));
        var directory=Files.createTempDirectory(Path.of("target"),"motor-capacity-");var dynamic=directory.resolve("dynamic.dyr");
        try(var zip=new ZipFile(CompositeMotorRecloseTest.ARCHIVE.toFile())) {Files.write(dynamic,CompositeMotorRecloseTest.read(zip,CompositeMotorRecloseTest.prefix("enabled","a",1)+"dynamic.dyr"));}
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),dynamic.toString());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        var owner=(org.interpss.dstab.dynLoad.impl.Cmldblu1Model)algorithm.getNetwork().getDStabBus("Bus2").getInfoOnlyDynModel();
        var motor=(org.interpss.dstab.dynLoad.impl.InductionMotorImpl)owner.getInductionMotorA();
        assertThrows(IllegalStateException.class,()->motor.setLegacyCompositeProtectionScheduling(false));
        motor.getDStabBus().setVoltage(new org.apache.commons.math3.complex.Complex(.7,0));
        for(int i=0;i<10;i++)motor.afterStep(.0005);
        assertEquals(.75,motor.getFuv(),0);assertEquals(1,motor.getFonline(),0);
        var pending=motor.getIntegrationHistories();
        var external=new org.interpss.dstab.dynLoad.impl.MotorProtectionControlImpl();motor.getProtectionControlList().add(external);
        assertThrows(IllegalStateException.class,()->motor.afterStep(.0005));assertEquals(pending,motor.getIntegrationHistories());
        motor.getProtectionControlList().remove(external);motor.afterStep(0);assertEquals(pending,motor.getIntegrationHistories());
        assertThrows(IllegalArgumentException.class,()->motor.afterStep(-.001));assertEquals(pending,motor.getIntegrationHistories());
        assertTrue(motor.changeLoad(-.2));assertEquals(.8,motor.getFonline(),1e-14,"Capacity scales applied rather than pending protection");
        var bus=(com.interpss.dstab.DStabBus)com.interpss.dstab.DStabObjectFactory.createDStabBus("copy",com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork()).get();
        bus.setVoltage(motor.getDStabBus().getVoltage());
        var copy=new org.interpss.dstab.dynLoad.impl.InductionMotorImpl.ModelState(motor).create(bus);
        assertEquals(motor.getIntegrationHistories(),copy.getIntegrationHistories());
        motor.afterStep(.0005);copy.afterStep(.0005);
        assertEquals(.6,motor.getFonline(),1e-14);assertEquals(motor.getIntegrationHistories(),copy.getIntegrationHistories());
        assertTrue(motor.changeLoad(-.8));var fullyShed=motor.getIntegrationHistories();
        assertFalse(motor.changeLoad(.1));assertEquals(fullyShed,motor.getIntegrationHistories(),"Unqualified full-capacity restoration is rejected");
    }

    private static org.apache.commons.math3.complex.Complex boundaryCurrent(Map<String,Double> row) {
        var v=new org.apache.commons.math3.complex.Complex[4];
        for(int i=1;i<=3;i++)v[i]=org.apache.commons.math3.complex.ComplexUtils.polar2Complex(row.get("V"+i),Math.toRadians(row.get("A"+i)));
        var current=v[1].subtract(v[2]).divide(new org.apache.commons.math3.complex.Complex(.005,.1))
                .add(v[3].subtract(v[2]).divide(new org.apache.commons.math3.complex.Complex(.01,.05)));
        double t=row.get("time_s");if(t>.05&&t<.1||t>.2&&t<.25)current=current.subtract(v[2].divide(new org.apache.commons.math3.complex.Complex(0,.2)));
        return current;
    }
    private static int firstChange(List<Map<String,Double>> a,List<Map<String,Double>> b,String key) {
        assertEquals(a.size(),b.size());for(int i=0;i<a.size();i++)if(!a.get(i).get(key).equals(b.get(i).get(key)))return i;return -1;
    }
}
