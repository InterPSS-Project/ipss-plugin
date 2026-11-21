package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.hvdc.model.*;
import org.interpss.fadapter.psse.dyr.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Complete independent ten-second terminal/state comparisons through repeated blocking and recovery. */
public class Cdc6tLongRunTest extends CorePluginTestSetup {
    @TempDir Path folder;
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("current","inverter-power")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void quarterCycleRunCompletesRecoveryWithoutDroppingReferenceSamples(String profile,boolean staggered)throws Exception{
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-long-power-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent long-run captures");
        assertEquals("5d39961f2d005a44d41ce075c8c5b440260d3feff108b90a7734692f9e1e42f3",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        double step,duration,reactance;double[] events=new double[4];
        try(var z=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var settings=mapper.readTree(z.getInputStream(z.getEntry(prefix+"manifest.json"))).get("settings");
            step=settings.get("step").doubleValue();duration=settings.get("duration").doubleValue();
            reactance=settings.get("second_fault_reactance").doubleValue();assertEquals(10,duration,0);assertEquals(1.0/240,step,1e-17);
            String[] names={"before-first-fault","before-first-clearing","before-second-fault","before-second-clearing"};
            var snapshots=mapper.readTree(z.getInputStream(z.getEntry(prefix+"snapshots.json")));
            for(int i=0;i<names.length;i++){
                boolean found=false;
                for(var item:snapshots)if(item.get("stage").asText().equals(names[i])){
                    double time=item.get("time").doubleValue();events[i]=Math.rint(time/step)*step;
                    assertEquals(time,events[i],1e-5);found=true;break;
                }
                assertTrue(found);
            }
            byte[] data=z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(data,z.getInputStream(z.getEntry(profile+"/b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(data);
            raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
        }
        // Preserve the reference's final complete interval beyond its requested stop time.
        double referenceEnd=reference.getLast().get("time_s"),end=Math.rint(referenceEnd/step)*step;
        assertTrue(referenceEnd>=duration&&end-duration<=step+1e-12);
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f,events);recorder.recordInternalTransitions();
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(step);a.setTotalSimuTimeSec(end);a.setSimuOutputHandler(new StateMonitor());
        var solver=staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder);a.setSolver(solver);
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,events[0],events[1]-events[0]),"FirstFault");
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,reactance),null,events[2],events[3]-events[2]),"SecondFault");
        assertTrue(a.initialization());a.setSimuTime(-2*step);recorder.enabled=true;
        var machines=new com.interpss.dstab.mach.Machine[4];
        for(int i=0;i<machines.length;i++)machines[i]=n.getMachine("Bus"+(i+1)+"-mach1");
        double[] initialMechanical=new double[4];
        for(int i=0;i<4;i++){
            var m=(com.interpss.dstab.mach.EConstMachine)machines[i];initialMechanical[i]=m.getPm();
            double networkPowerBound=2e-6*((com.interpss.dstab.mach.EConstMachine)m).getE()/Math.hypot(m.getRa(),m.getXd1());
            assertEquals(m.getPe(),m.getPm(),networkPowerBound,"Settled balance within network voltage-correction tolerance");
            assertEquals(reference.getFirst().get("M"+(i+1)+"_PMECH"),m.getPm(),6e-5,"Independent settled mechanical power");
        }
        Map<Long,double[]> machineRows=new LinkedHashMap<>();
        recordMachines(machineRows,machines,a.getSimuTime(),step);
        double[] history=new double[23];int recovered=0;
        while(a.getSimuTime()<end-step/2){
            assertTrue(a.solveDEqnStep(true),"Long-run network at "+a.getSimuTime());
            recordMachines(machineRows,machines,a.getSimuTime(),step);
            for(int i=0;i<4;i++)assertEquals(initialMechanical[i],machines[i].getPm(),0,"Faults must not rebase mechanical power");
            assertTrue(solver.getLastNetworkSolutionDiagnostics().iterations()<=50);
            f.protection().readAcceptedHistory(history,0);assertEquals(a.getSimuTime(),history[0],1e-11);
            if(a.getSimuTime()>1){
                assertEquals(Cdc6tNormalRuntime.OperatingMode.NORMAL,f.protection().operatingMode());
                assertEquals(1,f.protection().voltageFactor(),0);assertEquals(1,f.protection().currentFactor(),0);recovered++;
            }
        }
        assertTrue(recovered>2100);recorder.capture(a.getSimuTime());
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-long-run")),profile+"-"+(staggered?"staggered-":"euler-"));
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("machine-local-power.csv"),2*1024*1024)){
            writer.write("time_s,M1_PELEC,M1_PMECH,M2_PELEC,M2_PMECH,M3_PELEC,M3_PMECH,M4_PELEC,M4_PMECH\n");
            for(var values:machineRows.values()){
                writer.write(Double.toString(values[0]));
                for(int i=9;i<17;i++)writer.write(","+values[i]);
                writer.write("\n");
            }
        }
        // Each single-precision addition contributes at most half an ulp at the maximum clock magnitude.
        double clockBound=.5*(Math.ceil(end/step)+2)*Math.ulp((float)end)+2*Math.ulp((float)step);
        Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,step,clockBound);
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("machine-comparison.csv"),2*1024*1024)){
            writer.write("reference_time_s,local_time_s");
            for(int i=1;i<=4;i++)for(String key:new String[]{"ANGLE","SPEED","PELEC","PMECH"})
                writer.write(",M"+i+"_"+key+"_reference,M"+i+"_"+key+"_local");
            writer.write("\n");
            for(var row:reference){
                double time=row.get("time_s");var local=machineRows.get(Math.round(time/step));
                assertNotNull(local,"Machine endpoint at "+time);assertEquals(time,local[0],clockBound);
                writer.write(time+","+local[0]);
                for(int i=1;i<=4;i++){
                    writer.write(","+row.get("M"+i+"_ANGLE")+","+local[2*i-1]);
                    writer.write(","+row.get("M"+i+"_SPEED")+","+local[2*i]);
                    writer.write(","+row.get("M"+i+"_PELEC")+","+local[7+2*i]);
                    writer.write(","+row.get("M"+i+"_PMECH")+","+local[8+2*i]);
                }
                writer.write("\n");
            }
        }
        var errors=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("reference-row-errors.csv")));
        assertEquals(reference.size(),errors.size());assertEquals(2410,errors.size());
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("metrics.csv"),64*1024)){
            writer.write("channel,max_absolute,rmse,reference_range,nrmse\n");
            for(String key:Cdc6tCoupledReferenceTest.KEYS){
                double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,sum=0,peak=0;
                for(int i=0;i<reference.size();i++){
                    double value=reference.get(i).get(key),error=errors.get(i).get(key);
                    min=Math.min(min,value);max=Math.max(max,value);sum+=error*error;peak=Math.max(peak,Math.abs(error));
                }
                assertTrue(max>min);double rmse=Math.sqrt(sum/reference.size()),nrmse=rmse/(max-min);
                writer.write(key+","+peak+","+rmse+","+(max-min)+","+nrmse+"\n");
                if(key.equals("V7")||key.equals("V9"))assertTrue(peak<2.2e-5&&nrmse<.0005,"Complete terminal-voltage accuracy: "+key);
                if(key.equals("A7")||key.equals("A9"))assertTrue(peak<.006&&rmse<.003,"Complete raw terminal-angle accuracy: "+key);
                if(key.equals("VAR5")||key.equals("VAR6")||key.equals("VAR7")||key.equals("VAR8"))
                    assertTrue(peak<.0008&&nrmse<.0002,"Complete converter P/Q accuracy: "+key);
            }
        }
        // Recorded speed replay diagnoses source-angle accumulation; it never
        // replaces the raw angle channels or rounds the production integrator.
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("source-angle-replay.csv"),16384)){
            writer.write("machine,unrounded_peak_deg,rounded_peak_deg\n");
            for(int m=1;m<=4;m++){
                double angle=Math.toRadians(reference.getFirst().get("M"+m+"_ANGLE"));
                double rounded=(float)angle,unroundedPeak=0,roundedPeak=0;
                for(int i=1;i<reference.size();i++){
                    var left=reference.get(i-1);var right=reference.get(i);
                    double h=right.get("time_s")-left.get("time_s");assertTrue(h>=0);
                    double increment=Math.PI*60*h*(left.get("M"+m+"_SPEED")+right.get("M"+m+"_SPEED"));
                    angle+=increment;rounded=(float)(rounded+increment);
                    unroundedPeak=Math.max(unroundedPeak,Math.abs(Math.toDegrees(angle)-right.get("M"+m+"_ANGLE")));
                    roundedPeak=Math.max(roundedPeak,Math.abs(Math.toDegrees(rounded)-right.get("M"+m+"_ANGLE")));
                }
                writer.write(m+","+unroundedPeak+","+roundedPeak+"\n");
                assertTrue(roundedPeak<2e-5&&roundedPeak<unroundedPeak/10,"Independent source-angle precision discriminator");
            }
        }
    }
    private static void recordMachines(Map<Long,double[]> rows,com.interpss.dstab.mach.Machine[] machines,double time,double step){
        double[] values=new double[1+4*machines.length];values[0]=time;
        for(int i=0;i<machines.length;i++){
            values[2*i+1]=Math.toDegrees(machines[i].getAngle());values[2*i+2]=machines[i].getSpeed()-1;
            var m=(com.interpss.dstab.mach.EConstMachine)machines[i];
            var emf=new Complex(m.getE()*Math.cos(m.getAngle()),m.getE()*Math.sin(m.getAngle())).multiply(m.getVMultiFactor());
            var voltage=m.getDStabBus().getVoltage();
            var current=emf.subtract(voltage).divide(new Complex(m.getRa(),m.getXd1()).multiply(m.getZMultiFactor()));
            values[9+2*i]=voltage.multiply(current.conjugate()).getReal();values[10+2*i]=m.getPm();
        }
        assertNull(rows.put(Math.round(time/step),values),"One accepted machine state per interval");
    }
}
