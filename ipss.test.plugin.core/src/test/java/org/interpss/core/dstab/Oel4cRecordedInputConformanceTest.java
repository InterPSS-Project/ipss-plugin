package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.interpss.core.dstab.Oel4cPowerWorldArtifactConformanceTest.Profile;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.control.oel.psse.oel4c.Oel4cKernel;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Controlled-error conformance plus honest all-row output/timer diagnostics; not full acceptance. */
public class Oel4cRecordedInputConformanceTest {
    @TempDir Path directory;
    private static Path output;
    private static synchronized Path outputDirectory()throws Exception {
        if(output==null){
            Path classes=Path.of(Oel4cRecordedInputConformanceTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            output=Files.createTempDirectory(classes.getParent(),"oel4c-recorded-input-");
        }
        return output;
    }
    private static final class Metric {
        int samples;double sum,maximum,peakTime;String peakSide="regular";
        void add(double error,double time,String side){
            assertTrue(Double.isFinite(error));samples++;sum+=error*error;
            if(error>maximum){maximum=error;peakTime=time;peakSide=side;}
        }
        double rmse(){return samples==0?0:Math.sqrt(sum/samples);}
        Map<String,Object> report(){return Map.of("samples",samples,"rmse",rmse(),"maximum",maximum,
            "peak_time_s",peakTime,"peak_event_side",peakSide,"passes_rmse",samples>0&&rmse()<.001);}
    }
    @ParameterizedTest @MethodSource("org.interpss.core.dstab.Oel4cPowerWorldArtifactConformanceTest#profiles")
    void verifiesControlledErrorAndRetainsFailedOutputTimerComparisons(Profile profile)throws Exception {
        var reference=Oel4cPowerWorldArtifactConformanceTest.load(profile,directory);
        var kernel=new Oel4cKernel(Oel4cPowerWorldArtifactConformanceTest.data(profile));
        kernel.initialize(Oel4cPowerWorldArtifactConformanceTest.index().get("reactive_maximum_pu").getAsDouble());
        int q=Oel4cPowerWorldArtifactConformanceTest.field(reference,"TSMvar"),
            outputField=Oel4cPowerWorldArtifactConformanceTest.field(reference,"TSVOEL"),
            controlled=Oel4cPowerWorldArtifactConformanceTest.field(reference,"TSOELOther:3"),
            timer=Oel4cPowerWorldArtifactConformanceTest.field(reference,"TSOELOther:2");
        String[] names={"output_pu","controlled_error_pu","timer_s"};
        Map<String,Map<String,Metric>> metrics=new LinkedHashMap<>();
        for(String name:names){var groups=new LinkedHashMap<String,Metric>();for(String side:new String[]{"all","regular","pre","post"})groups.put(side,new Metric());metrics.put(name,groups);}
        double previousTime=0,previousQ=0;int count=0,duplicates=0;
        for(var sample:reference.samples()){
            double time=sample.time(),reactive=sample.value(q)/100,dt=time-previousTime;
            assertEquals(Math.rint(time/profile.step()),time/profile.step(),1e-8,"Off-grid reference clock");
            if(count>0&&dt>0){
                assertEquals(profile.step(),dt,1e-12);assertTrue(kernel.step(profile.step(),0,previousQ));
                assertTrue(kernel.step(profile.step(),1,reactive));
            }else if(count>0){assertEquals(1,sample.occurrence());duplicates++;}
            boolean event=!profile.profile().equals("flat")&&(time==.05||time==.1);
            String side=event?(sample.occurrence()==0?"pre":"post"):"regular";
            var s=kernel.signals(reactive);double[] actual={s.output(),s.controlledError(),s.elapsed()};
            double[] expected={sample.value(outputField),sample.value(controlled),sample.value(timer)};
            for(int i=0;i<names.length;i++){
                double error=Math.abs(actual[i]-expected[i]);metrics.get(names[i]).get("all").add(error,time,side);
                metrics.get(names[i]).get(side).add(error,time,side);
            }
            previousTime=time;previousQ=reactive;count++;
        }
        assertEquals(profile.samples(),count);assertEquals(profile.profile().equals("flat")?0:2,duplicates);
        assertTrue(metrics.get("controlled_error_pu").get("all").rmse()<1e-6,"Independent controlled-error serialization gate");
        boolean numericalPass=true;var reports=new LinkedHashMap<String,Object>();
        for(String name:names){
            numericalPass&=metrics.get(name).get("all").rmse()<.001;var groups=new LinkedHashMap<String,Object>();
            for(var group:metrics.get(name).entrySet())groups.put(group.getKey(),group.getValue().report());reports.put(name,groups);
        }
        var report=new LinkedHashMap<String,Object>();report.put("profile",profile.name());report.put("step_s",profile.step());
        report.put("rows",count);report.put("rows_dropped",0);report.put("event_duplicates",duplicates);
        report.put("reference_raw_sha256",profile.rawSha());report.put("metrics",reports);report.put("numerical_rmse_pass",numericalPass);
        report.put("controlled_error_conformance",true);report.put("coupled_trajectory_acceptance",false);report.put("parameters_fitted",false);
        String json=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report);
        try(var writer=BenchmarkOutputGuard.newWriter(outputDirectory().resolve(profile.name()+".json"),json.length()*4L+1024)){writer.write(json);}
        System.out.println("OEL4C recorded-input "+profile.name()+" all-row numerical RMSE pass="+numericalPass+"; coupled acceptance=false");
    }
}
