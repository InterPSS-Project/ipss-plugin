package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.hvdc.*;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Independent constant-gamma controls, including rectifier minimum-angle limitation. */
public class Cdc6tConstantGammaTest extends CorePluginTestSetup {
    @TempDir Path folder;
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("fixed-inverter","fixed-rectifier","limited-inverter","limited-rectifier","order-current","order-inverter-power","order-rectifier-power","order-half-inverter-power","low","high","rectifier","schedule495","schedule505","rectifier-power")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @ValueSource(strings={"low","high","rectifier"})
    void measuredInputsVerifyPowerControlledGammaAlgebra(String profile)throws Exception{
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-constant-gamma-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent constant-gamma captures");
        assertEquals("b5398197ab827c04b1d955482fa99acedc6e5227c56ea5a0395b68e82b2b41cf",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var zip=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";
            var equipment=new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry(prefix+"solved-equipment.json")));
            var r=equipment.get("7");var i=equipment.get("9");
            var link=new Cdc6tConductingLink(
                    new Cdc6tConverterBoundary(true,2,r.get("EBASE").doubleValue()*1000,r.get("TR").doubleValue(),r.get("TAP").doubleValue(),r.get("XC").doubleValue(),1e8),
                    new Cdc6tConverterBoundary(false,2,i.get("EBASE").doubleValue()*1000,i.get("TR").doubleValue(),i.get("TAP").doubleValue(),i.get("XC").doubleValue(),1e8),
                    5,Math.toRadians(5),Math.toRadians(i.get("ANGMIN").doubleValue()),Cdc6tConductingLink.InverterControl.CONSTANT_GAMMA);
            var command=new Cdc6tCurrentOrder(Cdc6tCurrentOrder.Mode.POWER,-500,500,0);
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(prefix+"native.csv")).readAllBytes());
            String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
            double[] output=new double[8],maximum=new double[8];int rectifierBound=0;
            Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-constant-gamma")),"replay-"+profile+"-");
            try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("trajectory.csv"),2*1024*1024)){
            writer.write("time_s,"+String.join(",",keys)+"\n");
            for(var row:rows){
                double current=command.amperes(row.get("STATE0"),row.get("STATE2"),0);
                link.evaluate(row.get("V7"),row.get("V9"),current,command.inverterVoltage(current),output,0);
                for(int k=0;k<keys.length;k++)maximum[k]=Math.max(maximum[k],Math.abs((k==2||k==5?Math.toDegrees(output[k]):output[k])-row.get(keys[k])));
                assertEquals(5*current*current/1e8,output[0]+output[3],1e-12);
                if(Math.abs(Math.toDegrees(output[2])-5)<1e-7)rectifierBound++;
                writer.write(Double.toString(row.get("time_s")));
                for(int k=0;k<keys.length;k++)writer.write(","+(k==2||k==5?Math.toDegrees(output[k]):output[k]));
                writer.write("\n");
            }
            }
            System.out.println("CDC6T gamma measured-input "+profile+" "+Arrays.toString(maximum));
            double[] tolerance={3.5e-6,5e-6,5e-5,3e-6,4.5e-6,4e-5,.3,.3};
            for(int k=0;k<keys.length;k++)assertTrue(maximum[k]<tolerance[k],keys[k]+" error "+maximum[k]);
            if(profile.equals("rectifier"))assertTrue(rectifierBound>0,"Rectifier limitation must actually be excited");
        }
    }
    @ParameterizedTest @ValueSource(strings={"low","high","rectifier"})
    void inconsistentStaticPowerCannotSilentlyInitializeDynamics(String profile)throws Exception{
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-constant-gamma-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent constant-gamma captures");
        try(var zip=new ZipFile(archive.toFile())){
            var raw=new ArrayList<>(new String(zip.getInputStream(zip.getEntry(profile+"/a/network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry(profile+"/a/dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
            var fixture=Cdc6tCoupledNetworkTest.loadWithDcTapControl(folder,raw,parameters);
            var owner=(com.interpss.core.aclf.hvdc.HvdcLine2TLCC<?>)fixture.context().getDStabilityNet().getSpecialBranchList().get(0);
            owner.setPowerDemand(owner.getPowerDemand()+.01);
            var failure=assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(fixture.context().getDStabilityNet(),parameters));
            assertTrue(failure.getMessage().contains("power must match the DC power schedule"));
        }
    }
    @ParameterizedTest @MethodSource("profiles")
    void constantGammaUsesStaticControlAndBothCoupledIntegrators(String profile,boolean staggered)throws Exception{
        boolean order=profile.startsWith("order-");
        boolean limited=profile.startsWith("limited-")||order;
        boolean fixed=profile.startsWith("fixed-")||limited;
        boolean schedule=profile.startsWith("schedule")||profile.equals("rectifier-power");
        Path archive=Path.of("testData/private/verification-artifacts/"+(schedule?"cdc6t-gamma-power-preparation":"cdc6t-constant-gamma"+(limited?"-fixed":fixed?"-fixed-v2":""))+"-20261101/"+(schedule?"captures-v2.zip":"captures.zip"));
        if(order)archive=Path.of("testData/private/verification-artifacts/cdc6t-transfer-orders-20261101/captures-v2.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent constant-gamma captures");
        assertEquals(order?"22d3a04e2db17d59cc8dfac64c2b3e472029a7e7c1b0d29f2d36346ccde5a543":schedule?"002bef9babdc7f44cd5afc708d16e04d7b6f675d5dfb485feffd31265a61fd3e":limited?"447984396d6920f266a9b0640a2424b4c7d39d9a81d8df6d352eb4e9530c0d1f":fixed?"1fe186a87d766575bfa7bdda495cf85d6f9b88f760a00014cdf0102cc381df68":"b5398197ab827c04b1d955482fa99acedc6e5227c56ea5a0395b68e82b2b41cf",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        com.fasterxml.jackson.databind.JsonNode solvedEquipment;
        String faultBus;double step;
        try(var zip=new ZipFile(archive.toFile())){
            String key=order?profile.substring(6):limited?profile.substring(8):fixed?profile.substring(6):profile;
            String prefix=key+"/a/";
            byte[] capture=zip.getInputStream(zip.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(capture,zip.getInputStream(zip.getEntry(key+"/b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(capture);
            raw=new ArrayList<>(new String(zip.getInputStream(zip.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
            var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry(prefix+"manifest.json")));
            step=manifest.get("settings").get("step").doubleValue();
            solvedEquipment=new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry(prefix+"solved-equipment.json")));
            faultBus=manifest.get("settings").get("fault_terminal").asText().equals("rectifier")?"Bus7":"Bus9";
            assertEquals(parameters.copySourceConstants()[1],manifest.get("initialized_constants").get(1).doubleValue(),1e-9,
                    "The ignored dynamic gamma constant is retained in source data");
        }
        var f=fixed?Cdc6tCoupledNetworkTest.load(folder,raw,parameters):Cdc6tCoupledNetworkTest.loadWithDcTapControl(folder,raw,parameters);
        var owner=(com.interpss.core.aclf.hvdc.HvdcLine2TLCC<?>)f.context().getDStabilityNet().getSpecialBranchList().get(0);
        assertEquals(limited?com.interpss.core.aclf.hvdc.HvdcControlMode.DC_CURRENT:com.interpss.core.aclf.hvdc.HvdcControlMode.FIRING_ANGLE,owner.getInverterControlMode());
        if(fixed){
            assertEquals(.94375,owner.getInverter().getXformerTapSetting(),1e-12);
            if(!order)assertEquals(1000,owner.getInverter().getIdc(),1e-10);
        }
        for(var entry:Map.of("7",owner.getRectifier(),"9",owner.getInverter()).entrySet()){
            var expected=solvedEquipment.get(entry.getKey());var converter=entry.getValue();
            assertEquals(expected.get("TAP").doubleValue(),converter.getXformerTapSetting(),1e-7);
            assertEquals(expected.get("KVDC").doubleValue()*1000,converter.getDcVoltage(),.5);
            assertEquals(expected.get("DCCUR").doubleValue(),converter.getIdc(),.001);
        }
        System.out.println("CDC6T gamma solved "+profile+" tap/V/I="+owner.getInverter().getXformerTapSetting()+"/"+owner.getInverter().getDcVoltage()+"/"+owner.getInverter().getIdc());
        var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f);
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(step);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(faultBus,n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());
        if(order){
            double[] initialized=new double[3],afterRead=new double[3],boundary=new double[8];
            f.runtime().readStaggeredState(initialized,0);
            f.ports().rectifierPort().getOutputObject();f.runtime().readBoundary(boundary,0);
            f.runtime().readStaggeredState(afterRead,0);
            assertArrayEquals(initialized,afterRead,0,"Reading the initial boundary cannot settle or integrate states");
            assertTrue(Math.abs(initialized[1]-owner.getInverter().getIdc())>1,
                    "Dynamic startup must replace the stale static current after control transfer");
            assertEquals(boundary[7],initialized[0],.5,"Settled inverter measurement");
            assertEquals(-boundary[3]*1e8/boundary[7],initialized[1],.005,"Settled current measurement");
            assertEquals(boundary[6],initialized[2],.5,"Settled rectifier measurement");
        }
        a.setSimuTime(-2*step);recorder.enabled=true;
        while(a.getSimuTime()<.5-step/2)assertTrue(a.solveDEqnStep(true),"Coupled constant-gamma solve at "+a.getSimuTime());
        recorder.capture(a.getSimuTime());assertEquals(reference.size(),recorder.rows.size());
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-constant-gamma")),profile+"-"+(staggered?"staggered-":"euler-"));
        double[] maxima=Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,step);
        // All reference rows are retained. Heun and staggered integration have
        // distinct event-side transducer envelopes; neither gate shifts time.
        double stateLimit=staggered?3.6:190;
        double[] tolerance={9e-6,.00065,2.8e-6,.00013,stateLimit,1e-8,stateLimit,
                .000055,.00011,.0015,.000055,.000055,.00085,5.5,5.5};
        if(!fixed||order)tolerance=staggered
                ?new double[]{1e-5,.00095,3.5e-6,.00018,4,.004,4,.000055,.00011,.0012,.000055,.00011,.0015,6,6}
                :new double[]{.000085,.0052,7.5e-6,.0047,180,.017,180,.0021,.00065,.0115,.0021,.0012,.0035,16,16};
        // This source operating point retains an initial bus-angle residual.
        // Gate the absolute trajectory without subtracting or fitting an offset.
        if(limited&&!order)tolerance=new double[]{1e-6,.0004,2.3e-6,.00095,staggered?2.4:190,1e-8,
                staggered?2.4:190,.000012,.000027,.00045,.000012,.000013,.00014,1.2,1.2};
        if(order)tolerance=profile.equals("order-current")
                ?new double[]{1.4e-6,.00025,4e-6,.0015,staggered?2.7:190,1e-8,staggered?2.7:190,.000022,.000045,.0007,.000022,.0000052,1e-8,2.1,2.1}
                :staggered?new double[]{2e-6,.00007,2.7e-6,.00105,2.6,.0026,2.6,.000017,.000029,.00043,.000017,.000011,1e-8,1.5,1.5}
                :new double[]{.000065,.0046,9.5e-6,.0052,190,.014,190,.0021,.00056,.016,.0021,.0011,1e-8,6.6,8.2};
        // The independent half-step capture retains its own absolute clock and
        // angle residual. Tighten the finite-stage errors against that capture.
        if(profile.equals("order-half-inverter-power"))tolerance=staggered
                ?new double[]{1.5e-6,.000072,2.4e-6,.0012,1.55,.0022,1.55,.000011,.00003,.0004,.000011,.0000065,1e-8,1.35,1.35}
                :new double[]{.000033,.0025,6e-6,.0032,101,.0048,101,.0011,.00028,.0082,.0011,.00057,1e-8,3.3,4.1};
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("maxima.csv"),16384)){
            writer.write("channel,max_absolute,limit\n");
            for(int k=0;k<tolerance.length;k++)writer.write(Cdc6tCoupledReferenceTest.KEYS[k]+","+maxima[k]+","+tolerance[k]+"\n");
        }
        for(int k=0;k<Cdc6tCoupledReferenceTest.KEYS.length;k++)
            assertTrue(maxima[k]<tolerance[k],Cdc6tCoupledReferenceTest.KEYS[k]+" error "+maxima[k]);
        int duplicates=0;
        for(int k=0;k<reference.size();k++){
            double referenceTime=reference.get(k).get("time_s"),actualTime=recorder.rows.get(k)[0];
            assertEquals(Math.round(referenceTime/step),Math.round(actualTime/step),"Same integration interval");
            // Binary32 accumulation reaches 9.36 microseconds at the half step.
            // Preserve both times and the original stricter coarse-step bound.
            assertEquals(referenceTime,actualTime,step<.0005?1e-5:5e-6,"Same grid and scheduled event side");
            if(k>0&&recorder.rows.get(k)[0]==recorder.rows.get(k-1)[0])duplicates++;
        }
        assertEquals(2,duplicates);
    }
}
