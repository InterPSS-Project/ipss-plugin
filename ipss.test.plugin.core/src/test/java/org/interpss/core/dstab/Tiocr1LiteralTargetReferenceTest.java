package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Exact symbolic identities preserve unrelated load through complete SMIB trajectories. */
public class Tiocr1LiteralTargetReferenceTest {
    @ParameterizedTest @CsvSource({"star,false","star,true","hash,false","hash,true"})
    void exactSymbolsPreservePeerLoad(String label,boolean staggered,@TempDir Path folder)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/tiocr-literal-targets-20261104/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent literal-target captures");
        assertEquals("01876cab0f0c9a14ae2b9c688953d19d5868136c8e80b6e12ec793df8f9f8c11",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<Map<String,Double>> reference;
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] expected=zip.getInputStream(zip.getEntry("peer/ordinary/a/native.csv")).readAllBytes();
            for(String name:List.of("ordinary",label)) {
                for(String repeat:List.of("a","b"))
                    assertArrayEquals(expected,zip.getInputStream(zip.getEntry("peer/"+name+"/"+repeat+"/native.csv")).readAllBytes());
                Path input=Files.createDirectories(folder.resolve(name));
                for(String file:List.of("network.raw","dynamic.dyr"))
                    Files.write(input.resolve(file),zip.getInputStream(zip.getEntry("peer/"+name+"/a/"+file)).readAllBytes());
                HspUsageRankedModelTest.run("tiocr1-load-only",false,staggered,false,
                        input.resolve("network.raw"),input.resolve("dynamic.dyr"),input);
            }
            var complete=Tiocr1ResetReferenceTest.csv(expected);
            assertEquals(1006,complete.size());
            reference=complete.stream().filter(r->r.get("time_s")>=0).toList();
        }
        String file="tiocr1-load-only"+(staggered?"-staggered":"")+"-fault.csv";
        byte[] ordinary=Files.readAllBytes(folder.resolve("ordinary").resolve(file));
        byte[] symbolic=Files.readAllBytes(folder.resolve(label).resolve(file));
        assertArrayEquals(ordinary,symbolic,"Exact ID must not shed the colocated peer load");
        var actual=Tiocr1ResetReferenceTest.csv(symbolic);
        assertEquals(reference.size(),actual.size(),"Preserve complete nonnegative grid and event sides");
        for(String key:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG")) {
            double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<reference.size();i++) {
                assertEquals(reference.get(i).get("time_s"),actual.get(i).get("time_s"),5e-6);
                double value=reference.get(i).get(key),error=actual.get(i).get(key)-value;
                assertTrue(Double.isFinite(error));sum+=error*error;min=Math.min(min,value);max=Math.max(max,value);
            }
            double rmse=Math.sqrt(sum/reference.size());
            if(key.equals("ANGLE_DEG"))assertTrue(rmse<.01,"Rotor angle RMSE "+rmse);
            else {assertTrue(max>min);assertTrue(rmse/(max-min)<.001,key+" NRMSE "+rmse/(max-min));}
        }
        var end=reference.getLast();
        // Constant-current P and impedance Q expose the physical retained capacities.
        assertEquals(.9,end.get("LOAD_P")/end.get("V2"),2e-7);
        assertEquals(.1,end.get("PEER_P")/end.get("V2"),2e-7);
        assertEquals(.45,end.get("LOAD_Q")/(end.get("V2")*end.get("V2")),2e-7);
        assertEquals(.03,end.get("PEER_Q")/(end.get("V2")*end.get("V2")),2e-7);
    }
}
