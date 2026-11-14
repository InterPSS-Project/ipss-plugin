package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import com.interpss.dstab.hvdc.model.Cdc6tConverterBoundary;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Measured-input electrical boundary qualification, distinct from coupled controller dynamics. */
public class Cdc6tConverterBoundaryTest {
    @ParameterizedTest
    @CsvSource({"current-order,inverter-power","current-order,rectifier-power","current-order,current",
        "vdcl,flat","vdcl,equal-slope-fault","vdcl,two-slope-fault"})
    void bothConverterPowersMatchEveryReferenceSample(String group,String profile) throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-"+group+"-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent converter captures");
        String expected=group.equals("vdcl")?"0afeb5bb1968ce9048b86daa4d3061a9a54ef2505424a8824acc89c7b916416d":
                "6d2fe324463db46be0fa0f6fd3f69aa928cb687a527b80ff8236ea1ea307a400";
        assertEquals(expected,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var rectifier=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8);
        var inverter=new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8);
        double[] output=new double[6],max=new double[6];
        try(var zip=new ZipFile(archive.toFile())) {
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes());
            assertEquals(1005,rows.size());
            for(var row:rows) {
                rectifier.evaluate(row.get("V7"),row.get("VAR10"),row.get("VAR11"),output,0);
                inverter.evaluate(row.get("V9"),row.get("VAR9"),row.get("VAR11"),output,3);
                String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13"};
                for(int i=0;i<6;i++)max[i]=Math.max(max[i],Math.abs((i%3==2?Math.toDegrees(output[i]):output[i])-row.get(keys[i])));
                assertEquals(5*row.get("VAR11")*row.get("VAR11")/1e8,output[0]+output[3],2e-6,"DC resistive loss");
            }
        }
        System.out.println("CDC6T converter "+group+" "+profile+" "+java.util.Arrays.toString(max));
        for(int i=0;i<6;i++)assertTrue(max[i]<(i%3==2?.0003:i%3==1?1e-5:1.2e-6),"Boundary coordinate "+i+" error "+max[i]);
    }
    @Test void invalidTrialsAreAtomicAndIdleCurrentHasZeroPower() {
        var converter=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8);
        double[] out={7,8,9,10,11};
        converter.evaluate(1,500000,0,out,1);
        assertEquals(0,out[1],0);assertEquals(0,out[2],0);assertEquals(7,out[0]);assertEquals(11,out[4]);
        var before=out.clone();
        assertThrows(IllegalArgumentException.class,()->converter.evaluate(.1,500000,1000,out,1));
        assertArrayEquals(before,out);
        assertThrows(IllegalArgumentException.class,()->converter.evaluate(Double.MAX_VALUE,500000,1000,out,1));
        assertArrayEquals(before,out);
        assertThrows(IllegalArgumentException.class,()->converter.evaluate(1,500000,-1,out,1));
        assertArrayEquals(before,out);
        converter.evaluate(1,0,1000,out,1);
        assertEquals(0,out[1],0);assertTrue(out[2]>0,"Commutating current consumes reactive power even at zero DC power");
        double zeroVoltageQ=out[2];converter.evaluate(1,.001,1000,out,1);
        assertEquals(zeroVoltageQ,out[2],1e-8,"Reactive power is continuous at zero DC voltage");
    }
}
