package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Repeated motor undervoltage actions retain all fault sides and complete recordings. */
public class CompositeMotorRecloseTest {
    static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cmldblu1-motor-reclose-20261104/captures.zip");
    static String prefix(String profile,String repeat,int part){return "target/cmld-motor-uv-"+profile+"-20261104/cmldblu1-mixed-repeated-faults-h0.0005-"+repeat+"-part"+part+"/";}
    static byte[] read(java.util.zip.ZipFile z,String name)throws Exception {return z.getInputStream(z.getEntry(name)).readAllBytes();}
    @ParameterizedTest @CsvSource({"false,enabled","true,enabled","false,disabled","true,disabled"})
    void repeatedProtection(boolean staggered,String protection)throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        String profile=protection.equals("disabled")?"disabled-cleared":protection;
        var temporary=Files.createTempDirectory(Path.of("target"),"motor-protection-input-");
        Path dynamic=temporary.resolve("dynamic.dyr");List<Map<String,Double>> rows;
        try(var zip=new java.util.zip.ZipFile(ARCHIVE.toFile())) {
            rows=csv(new String(read(zip,prefix(profile,"a",1)+"native.csv"),java.nio.charset.StandardCharsets.UTF_8)).stream().filter(r->r.get("time_s")>=0).toList();
            Files.write(dynamic,read(zip,prefix(profile,"a",1)+"dynamic.dyr"));
        }
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var recording=Cmldblu1SharedRecording.simulateStaticControl(dynamic,staggered,.0005,.2,.2);
        var out=Files.createTempDirectory(Path.of("target"),"motor-reclose-");recording.write(out.resolve("complete.csv.gz"));
        assertEquals(rows.size(),recording.frames.size());
        var nativeActions=new ArrayList<Double>();var actualActions=new ArrayList<Double>();
        var nativeFractions=new ArrayList<Double>();var actualFractions=new ArrayList<Double>();
        var squares=new TreeMap<String,Double>();var peaks=new TreeMap<String,Double>();
        for(String k:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG","SPEED")){squares.put(k,0.);peaks.put(k,0.);}
        for(int i=0;i<rows.size();i++) {
            assertEquals(rows.get(i).get("time_s"),recording.frames.get(i).time(),5e-6);
            if(i>0 && !rows.get(i).get("VAR83").equals(rows.get(i-1).get("VAR83"))){nativeActions.add(rows.get(i).get("time_s"));nativeFractions.add(rows.get(i).get("VAR83"));}
            if(i>0 && !recording.frames.get(i).values().get("MotorA.UvFraction").equals(recording.frames.get(i-1).values().get("MotorA.UvFraction"))){actualActions.add(recording.frames.get(i).time());actualFractions.add(recording.frames.get(i).values().get("MotorA.UvFraction"));}
            for(String k:squares.keySet()) {double e=recording.frames.get(i).values().get(k)-rows.get(i).get(k);squares.put(k,squares.get(k)+e*e);peaks.put(k,Math.max(peaks.get(k),Math.abs(e)));}
            assertEquals(rows.get(i).get("VAR83"),recording.frames.get(i).values().get("MotorA.UvFraction"),0,"Unshifted fraction row "+i);
        }
        System.out.println("MOTOR RECLOSE staggered="+staggered+" protection="+protection+" native="+nativeActions+" actual="+actualActions+" record="+out.getFileName());
        assertEquals(protection.equals("enabled")?4:0,nativeActions.size());
        assertEquals(nativeActions.size(),actualActions.size(),"Every trip and reclose must recur");
        assertEquals(nativeFractions,actualFractions,"Exact per-action shedding and recovery fractions");
        if(protection.equals("enabled"))assertEquals(List.of(.75,1.,.75,1.),actualFractions);
        var normalized=new TreeMap<String,Double>();
        for(String k:squares.keySet()) {double range=rows.stream().mapToDouble(r->r.get(k)).max().orElseThrow()-rows.stream().mapToDouble(r->r.get(k)).min().orElseThrow();normalized.put(k,Math.sqrt(squares.get(k)/rows.size())/range);}
        System.out.println("MOTOR BOUNDARIES staggered="+staggered+" protection="+protection+" NRMSE="+normalized+" peaks="+peaks);
        for(String k:List.of("V1","V2","V3","P_PU","Q_PU","SPEED"))assertTrue(normalized.get(k)<2e-4,k+" NRMSE="+normalized.get(k));
        assertTrue(peaks.get("ANGLE_DEG")<2e-4,"Rotor peak error");
        for(int i=0;i<nativeActions.size();i++)assertEquals(nativeActions.get(i),actualActions.get(i),.000505,"Accepted action "+i);
        assertEquals(1.,recording.model.getInductionMotorA().getFuv(),0,"Second recovery completes");
    }
    @org.junit.jupiter.api.Test
    void independentCaptureIntegrityAndDiagnosticClearingIsolation()throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals("2c3f16467dfa0a11b315897ba04bd8030cf7cd3bea348fabc2f1f83b456da7ca",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new java.util.zip.ZipFile(ARCHIVE.toFile())) {
            for(String profile:List.of("enabled","disabled-cleared"))for(int part=0;part<2;part++) {
                assertArrayEquals(Files.readAllBytes(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw")),read(zip,prefix(profile,"a",part)+"network.raw"));
                var raw=read(zip,prefix(profile,"a",part)+"native.csv");
                assertArrayEquals(raw,read(zip,prefix(profile,"b",part)+"native.csv"));
                var rows=csv(new String(raw,java.nio.charset.StandardCharsets.UTF_8));assertEquals(1007,rows.size());
                if(profile.equals("disabled-cleared"))for(String repeat:List.of("a","b")) {
                    var original=csv(new String(read(zip,prefix("disabled",repeat,part)+"native.csv"),java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals(rows.size(),original.size());
                    for(int i=0;i<rows.size();i++)for(String key:rows.get(i).keySet()) {
                        // These inactive/unassigned diagnostic slots remain raw in the archive.
                        int var=key.startsWith("VAR")?Integer.parseInt(key.substring(3)):-1;
                        boolean inactive=var==77||var==78||var==81||var==82||var>=85;
                        if(!inactive)assertEquals(original.get(i).get(key),rows.get(i).get(key),0,"Clearing must preserve "+key+" row "+i);
                    }
                }
            }
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={1,2})
    void repeatedHistorySurvivesWarmCopyAndReadOnlyObservation(int stage)throws Exception {
        var bus=bus("source");var motor=new org.interpss.dstab.dynLoad.impl.InductionMotorImpl(bus,"M");
        motor.setXm(3);motor.setXl(.07);motor.setRa(.032);motor.setXr1(.2);motor.setRr1(.01);
        motor.setMvaBase(50);motor.setH(1);motor.setLoadPercent(50);motor.setRatedVoltage(1000);
        motor.setInitLoadPQ(new org.apache.commons.math3.complex.Complex(.3,.12));
        motor.setVtr1(stage==1?.8:0);motor.setTtr1(.005);motor.setFtr1(stage==1?.25:0);motor.setVrc1(.9);motor.setTrc1(.01);
        motor.setVtr2(stage==2?.8:0);motor.setTtr2(.005);motor.setFtr2(stage==2?.25:0);motor.setVrc2(.9);motor.setTrc2(.01);
        motor.setLegacyCompositeProtectionScheduling(true);
        assertTrue(motor.initStates());
        org.interpss.dstab.dynLoad.impl.InductionMotorImpl copy=null;
        int changes=0;double prior=1;
        for(int i=0;i<500;i++) {
            double v=i>=50&&i<100||i>=200&&i<250?.7:1;
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(v,0));
            motor.afterStep(.001);
            var histories=motor.getIntegrationHistories();
            for(int read=0;read<3;read++){motor.getNamedStates();motor.getStates(null);motor.getIntegrationStates();}
            assertEquals(histories,motor.getIntegrationHistories(),"Observation is read-only");
            if(copy!=null){copy.getDStabBus().setVoltage(bus.getVoltage());copy.afterStep(.001);assertEquals(histories,copy.getIntegrationHistories());}
            if(i==75||i==150)copy=new org.interpss.dstab.dynLoad.impl.InductionMotorImpl.ModelState(motor).create(bus("copy"+i));
            if(prior!=motor.getFuv()){changes++;prior=motor.getFuv();}
        }
        assertEquals(4,changes);assertEquals(1,motor.getFuv(),0);
    }
    private static com.interpss.dstab.DStabBus bus(String id)throws Exception {
        var net=com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);net.setFrequency(60);
        var bus=(com.interpss.dstab.DStabBus)com.interpss.dstab.DStabObjectFactory.createDStabBus(id,net).get();
        bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);bus.setBaseVoltage(1000);
        bus.setVoltage(org.apache.commons.math3.complex.Complex.ONE);bus.setInitLoad(new org.apache.commons.math3.complex.Complex(.6,.2));return bus;
    }

    static List<Map<String,Double>> csv(String text) {
        var lines=text.strip().split("\\R");var keys=lines[0].split(",");var rows=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++){var fields=lines[i].split(",");var row=new LinkedHashMap<String,Double>();
            for(int k=0;k<keys.length;k++){double value=Double.parseDouble(fields[k]);assertTrue(Double.isFinite(value));row.put(keys[k],value);}rows.add(row);}
        return rows;
    }
}
