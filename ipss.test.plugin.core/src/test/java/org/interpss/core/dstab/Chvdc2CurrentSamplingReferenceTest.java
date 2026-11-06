package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.core.dstab.reference.RelativeRmse;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent recorded-current input replay through production measurement equations.
 * This isolates the transducer; it does not qualify the complete coupled trajectory. */
public class Chvdc2CurrentSamplingReferenceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    static Stream<Arguments> profiles() {
        var p=new ArrayList<String[]>();
        for(String h:List.of("0p0005","0p00025"))p.add(new String[]{"chvdc2-controller-20261102/captures.zip",
                "75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511",
                "stiff-legacy-inverter-fault-flags001-h"+h+"-a"});
        p.add(new String[]{"chvdc2-protection-20261102/captures.zip",
                "32079591003689230139fa893b5c3e07a315dc4d411442372838e10da3b658ea",
                "chvdc2-protection-short-hold-20261102/stiff-legacy-inverter-fault-flags001-h0p0005-gamma12-faultx0p02-netiter50-cfhold0p005-a"});
        return p.stream().flatMap(a->Stream.of(false,true).map(s->Arguments.of(a[0],a[1],a[2],s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void sampledCurrentThroughProductionTransducers(String archive,String digest,String tag,boolean staggered)throws Exception {
        Path path=Path.of("testData/private/verification-artifacts").resolve(archive);
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent current-measurement captures");
        assertEquals(digest,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        try(var z=new ZipFile(path.toFile())) {
            var manifest=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
            var equipment=JSON.readTree(z.getInputStream(z.getEntry(tag+"/converted-equipment.json"))).path("9");
            assertEquals("complete",manifest.path("status").asText());
            assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            var rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes());
            double H=manifest.path("settings").path("step").asDouble();
            double[] constants=JSON.convertValue(manifest.path("entered_constants"),double[].class);
            var parameters=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,
                    JSON.convertValue(manifest.path("settings").path("flags"),int[].class),constants);
            double[] maximum=new double[2];RelativeRmse[] relative={new RelativeRmse(),new RelativeRmse()};
            Path output=Files.createTempDirectory(Files.createDirectories(Path.of("../target/chvdc2-current-sampling-reference")),staggered?"staggered-":"euler-");
            for(int run=0;run<2;run++) {
                int localSteps=run==0?1:10;double h=H/localSteps;
                var model=new Chvdc2OrderControls(parameters,equipment.path("TR").asDouble(),equipment.path("TAP").asDouble());
                var first=rows.getFirst();input(model,first,constants[28]);model.initialize(first.get("STATE7"));
                double[] state=new double[10],rates=new double[10],old=new double[10];boolean started=false;int duplicates=0;
                try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("local-"+localSteps+".csv"),2*1024*1024)) {
                    writer.write("time_s,rectifier_pu,reference_rectifier_pu,inverter_pu,reference_inverter_pu\n");
                    for(int k=0;k<rows.size();k++) {
                        var row=rows.get(k);
                        if(k>0&&row.get("time_s").equals(rows.get(k-1).get("time_s")))duplicates++;
                        else if(k>0) {
                            input(model,rows.get(k-1),constants[28]);
                            for(int local=0;local<localSteps;local++) {
                                if(staggered) {
                                    model.readStaggeredState(state,0);model.evaluateStaggered(h,rates,0);
                                    for(int c=0;c<10;c++)state[c]+=h*(started?1.5*rates[c]-.5*old[c]:rates[c]);
                                    model.applyStaggeredState(state,0,h);System.arraycopy(rates,0,old,0,10);started=true;
                                }else{model.step(h,0);model.step(h,1);}
                            }
                        }
                        model.readStaggeredState(state,0);
                        for(int c=0;c<2;c++){double expected=row.get("STATE"+c);
                            maximum[run]=Math.max(maximum[run],Math.abs(state[c]-expected));relative[run].add(state[c],expected);}
                        writer.write(row.get("time_s")+","+state[0]+","+row.get("STATE0")+","+state[1]+","+row.get("STATE1")+"\n");
                    }
                }
                assertEquals(2,duplicates);
            }
            try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("comparison.json"),20000)) {
                var report=new LinkedHashMap<String,Object>(Map.of("profile",tag,"staggered",staggered,
                        "maximum_one_step_error_pu",maximum[0],"maximum_ten_step_error_pu",maximum[1],
                        "samples",rows.size(),"event_duplicates",2,"production_precision","double",
                        "state_resets_after_initialization",0,"complete_model_qualified",false));
                report.put("relative_rmse_one_step",relative[0].value());report.put("relative_rmse_ten_step",relative[1].value());
                JSON.writerWithDefaultPrettyPrinter().writeValue(writer,report);
            }
            assertTrue(relative[1].value()<RelativeRmse.ONE_PERCENT,"Sampled-current transducer relative RMSE "+relative[1].value());
        }
    }
    private static void input(Chvdc2OrderControls model,Map<String,Double> row,double rated) {
        model.setInput(row.get("VAR5"),row.get("VAR7"),row.get("VAR6"),row.get("VAR8"),
                row.get("V7"),row.get("V9"),row.get("VAR15")*rated,row.get("VAR0"));
    }
}
