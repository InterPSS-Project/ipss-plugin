package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.dstab.common.DStabSimuException;
import org.interpss.core.dstab.reference.RelativeRmse;

/** Complete independent hot-reactivation and permanently-offline controls. */
public class Urgs3tReactivationReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-reactivation-contract-20261104/captures.zip");
    @BeforeAll static void setup()throws Exception {
        Urgs3tReferenceTest.setup();Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals("e2508035a2f7d912f6596cdc71849d986b79e1189efe7bf5956402ffdda24e18",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
    }
    @ParameterizedTest @CsvSource({"false,false","false,true","true,false","true,true"})
    void completeReactivationTrajectory(boolean shared,boolean control)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-reactivation-");
        String tag=(control?"offline-control-":"reenable-")+"standard-h0p0005-t0p5-trip1-machines-a";
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String file:List.of("manifest.json","native.csv","network.raw","dynamic.dyr"))
                try(var in=z.getInputStream(z.getEntry(tag+"/"+file))){Files.copy(in,out.resolve(file));}
        }
        var simulation=Urgs3tReferenceTest.simulate(out,shared,1e-6,(algorithm,recording)-> {
            if(shared)return new Urgs3tReferenceTest.Shared(algorithm,recording,1e-6) {
                {setHotReactivationMachineIds(Set.of("Bus1-mach1"));}
                @Override public void beforeStep(double t)throws DStabSimuException {
                    super.beforeStep(t);
                    if(Math.abs(t-.2)<1e-9) {
                        recording.frames.add(recording.capture(t));
                        if(!control) {
                            // Record the public event sides, not each internal algebraic solve.
                            recording.enabled=false;
                            try{reactivateMachineAtAcceptedEndpoint("Bus1-mach1",t);}finally{recording.enabled=true;}
                        }
                    }
                }
            };
            return new Urgs3tReferenceTest.Ordinary(algorithm,recording,1e-6) {
                {setHotReactivationMachineIds(Set.of("Bus1-mach1"));}
                @Override public void beforeStep(double t)throws DStabSimuException {
                    super.beforeStep(t);
                    if(Math.abs(t-.2)<1e-9) {
                        recording.frames.add(recording.capture(t));
                        if(!control) {
                            // Record the public event sides, not each internal algebraic solve.
                            recording.enabled=false;
                            try{reactivateMachineAtAcceptedEndpoint("Bus1-mach1",t);}finally{recording.enabled=true;}
                        }
                    }
                }
            };
        });
        var reference=Urgs3tReferenceTest.rows(out.resolve("native.csv"));
        assertEquals(reference.size(),simulation.frames().size()+2);
        var maximum=new TreeMap<String,Double>();var square=new TreeMap<String,Double>();var relative=new TreeMap<String,RelativeRmse>();
        var lo=new TreeMap<String,Double>();var hi=new TreeMap<String,Double>();
        var keys=new ArrayList<>(simulation.initial().keySet());keys.remove("time_s");keys.remove("HOST_ACTIVE");
        var definedReferenceKeys=new HashSet<>(reference.getFirst().keySet());
        definedReferenceKeys.remove("time_s");definedReferenceKeys.remove("VREF"); // No regulator owns VREF in this fixture.
        assertEquals(definedReferenceKeys,new HashSet<>(keys));
        int index=0;
        try(var writer=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"),4*1024*1024)) {
            writer.write("source_time_s,actual_time_s,"+String.join(",",keys)+"\n");
            for(var r:reference) {
                var a=r.get("time_s")<0?simulation.initial():simulation.frames().get(index++);
                writer.write(r.get("time_s")+","+a.get("time_s"));
                for(String key:keys) {
                    double d=a.get(key)-r.get(key);maximum.merge(key,Math.abs(d),Math::max);square.merge(key,d*d,Double::sum);
                    relative.computeIfAbsent(key,ignored->new RelativeRmse()).add(a.get(key),r.get(key));
                    lo.merge(key,r.get(key),Math::min);hi.merge(key,r.get(key),Math::max);writer.write(","+a.get(key));
                }
                writer.write("\n");
            }
        }
        var nrmse=new TreeMap<String,Double>();var relativeValues=new TreeMap<String,Double>();
        for(String key:List.of("V1","V2","P_PU","Q_PU")) {
            double rmse=Math.sqrt(square.get(key)/reference.size()),range=hi.get(key)-lo.get(key);
            nrmse.put(key,range>1e-7?rmse/range:rmse);
            relativeValues.put(key,relative.get(key).value());
        }
        double angle=Math.sqrt(square.get("ANGLE_DEG")/reference.size());relativeValues.put("ANGLE_DEG",relative.get("ANGLE_DEG").value());
        for(int k=0;k<5;k++){String key="STATE"+k;relativeValues.put(key,relative.get(key).value());}
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out.resolve("comparison.json").toFile(),
                Map.of("shared",shared,"control",control,"maximum",maximum,"range_nrmse",nrmse,"relative_rmse",relativeValues,"angle_rmse",angle));
        System.out.println("Reactivation "+shared+" "+control+" "+out+" "+maximum+" "+nrmse);
        var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
        for(String key:relativeValues.keySet())checks.add(()->assertTrue(relativeValues.get(key)<RelativeRmse.ONE_PERCENT,
                key+" relative RMSE "+relativeValues.get(key)));
        assertAll(checks);
    }
}
