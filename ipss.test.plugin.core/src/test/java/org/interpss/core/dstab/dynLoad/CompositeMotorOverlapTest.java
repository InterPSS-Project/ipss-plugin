package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Retained complete trajectory gates; oversubscribed source behavior remains unqualified. */
public class CompositeMotorOverlapTest {
    static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cmldblu1-motor-overlap-20261104/controls.zip");
    @ParameterizedTest @CsvSource({"false,overlap","true,overlap","false,overlap-zero","true,overlap-zero"})
    void overlappingTripFractions(boolean staggered,String scenario)throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals("85107e36eaafd1939df3e46cee2b917fec95a5b096975c018aff9cc2c9b1c9c3",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        boolean flat=scenario.equals("zero-flat");String profile=flat?"flat":"repeated-faults";
        String prefix="target/cmld-motor-uv-enabled-"+scenario+"-cleared-20261104/cmldblu1-mixed-"+profile+"-h0.0005-";
        var temporary=Files.createTempDirectory(Path.of("target"),"motor-overlap-");Path dynamic=temporary.resolve("dynamic.dyr");
        List<Map<String,Double>> rows;
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            var raw=CompositeMotorRecloseTest.read(zip,prefix+"a-part1/native.csv");
            assertArrayEquals(raw,CompositeMotorRecloseTest.read(zip,prefix+"b-part1/native.csv"));
            rows=CompositeMotorRecloseTest.csv(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).stream().filter(r->r.get("time_s")>=0).toList();
            Files.write(dynamic,CompositeMotorRecloseTest.read(zip,prefix+"a-part1/dynamic.dyr"));
        }
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var actual=Cmldblu1SharedRecording.simulateControl(dynamic,staggered,flat,.0005,.2,.2,.5);
        actual.write(temporary.resolve("complete.csv.gz"));
        assertEquals(rows.size(),actual.frames.size());int actions=0;var peaks=new TreeMap<String,Double>();var squares=new TreeMap<String,Double>();
        for(String k:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG","SPEED")){peaks.put(k,0.);squares.put(k,0.);}
        for(int i=0;i<rows.size();i++) {
            var r=rows.get(i);var a=actual.frames.get(i);assertEquals(r.get("time_s"),a.time(),5e-6);
            assertEquals(r.get("VAR83"),a.values().get("MotorA.UvFraction"),5e-8,"Unshifted raw fraction row "+i);
            if(i>0&&!r.get("VAR83").equals(rows.get(i-1).get("VAR83")))actions++;
            for(String k:peaks.keySet()){double e=a.values().get(k)-r.get(k);peaks.put(k,Math.max(peaks.get(k),Math.abs(e)));squares.put(k,squares.get(k)+e*e);}
        }
        assertEquals(2,actions);
        var normalized=new TreeMap<String,Double>();
        for(String k:peaks.keySet()) {
            double span=rows.stream().mapToDouble(r->r.get(k)).max().orElseThrow()-rows.stream().mapToDouble(r->r.get(k)).min().orElseThrow();
            if(span>1e-12)normalized.put(k,Math.sqrt(squares.get(k)/rows.size())/span);
        }
        System.out.println("MOTOR OVERLAP staggered="+staggered+" scenario="+scenario+" peaks="+peaks+" NRMSE="+normalized+" recording="+temporary.getFileName());
        for(String k:List.of("V1","V2","V3","P_PU","Q_PU","SPEED"))assertTrue(normalized.get(k)<2e-4,k+"="+normalized.get(k));
        // The independent rotor-history control shows that the reference uses
        // prior-speed history, whereas modified Euler integrates the current
        // interval's predictor/corrector speed. Keep separate measured gates.
        double rotorPeakLimit=staggered?2e-4:6e-4;
        assertTrue(peaks.get("ANGLE_DEG")<rotorPeakLimit,
                "Rotor peak="+peaks.get("ANGLE_DEG")+" limit="+rotorPeakLimit);
    }

    @org.junit.jupiter.api.Test
    void independentPhysicalOverlapDiscriminator()throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            var controls=new ArrayList<List<Map<String,Double>>>();
            for(String profile:List.of("overlap","overlap-zero")) {
                String prefix="target/cmld-motor-uv-enabled-"+profile+"-cleared-20261104/cmldblu1-mixed-repeated-faults-h0.0005-";
                byte[] bytes=CompositeMotorRecloseTest.read(zip,prefix+"a-part1/native.csv");
                assertArrayEquals(bytes,CompositeMotorRecloseTest.read(zip,prefix+"b-part1/native.csv"));
                controls.add(CompositeMotorRecloseTest.csv(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
            }
            var negative=controls.get(0);var zero=controls.get(1);assertEquals(1007,negative.size());assertEquals(negative.size(),zero.size());
            int history=-1,physical=-1,state=-1;
            for(int i=0;i<negative.size();i++) {
                var a=negative.get(i);var b=zero.get(i);assertEquals(a.get("time_s"),b.get("time_s"));
                if(history<0&&!a.get("VAR83").equals(b.get("VAR83")))history=i;
                if(physical<0&&!a.get("V2").equals(b.get("V2")))physical=i;
                if(state<0&&!a.get("STATE0").equals(b.get("STATE0")))state=i;
            }
            assertEquals(.060,negative.get(history).get("time_s"),5e-6);
            assertEquals(history+1,physical);assertEquals(history+2,state);
            assertEquals(-.1,negative.get(history).get("VAR83"),5e-8);assertEquals(0,zero.get(history).get("VAR83"),0);
            int sample=physical;
            assertTrue(residualConductance(negative.get(sample),negative.get(0).get("V2"))<-.01,
                    "KCL proves a negative active residual, not just a stale protection channel");
            assertEquals(0,residualConductance(zero.get(sample),zero.get(0).get("V2")),1e-5);
        }
    }
    private static double residualConductance(Map<String,Double> row,double initialVoltage) {
        var v=new org.apache.commons.math3.complex.Complex[4];
        for(int i=1;i<=3;i++)v[i]=org.apache.commons.math3.complex.ComplexUtils.polar2Complex(row.get("V"+i),Math.toRadians(row.get("A"+i)));
        var current=v[1].subtract(v[2]).divide(new org.apache.commons.math3.complex.Complex(.005,.1))
                .add(v[3].subtract(v[2]).divide(new org.apache.commons.math3.complex.Complex(.01,.05)));
        double t=row.get("time_s");if(t>.05&&t<.1||t>.2&&t<.25)current=current.subtract(v[2].divide(new org.apache.commons.math3.complex.Complex(0,.2)));
        double ratio=v[2].abs()/initialVoltage;
        double staticP=.32*(.23*Math.pow(ratio,1.7)+.51*Math.pow(ratio,.8)+.26);
        return (v[2].multiply(current.conjugate()).getReal()-staticP)/v[2].abs()/v[2].abs();
    }

}
