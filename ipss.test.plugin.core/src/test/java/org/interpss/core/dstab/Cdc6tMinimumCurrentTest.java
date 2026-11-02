package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent conducting current-floor and electrical boundary checks, including active recovery. */
public class Cdc6tMinimumCurrentTest {
    @ParameterizedTest @ValueSource(ints={200,400})
    void allRecordedRegimesRetainMinimumDemandAndCeiling(int minimum)throws Exception{
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-minimum-current-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent minimum-current captures");
        assertEquals("9634c30bc479897165d89e1bf97a9fba3c11881614dd7f6d3035eec6258c0639",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var runtime=Cdc6tOperatingBoundaryTest.runtime(minimum);
        double[] state=new double[3],injections=new double[4],boundary=new double[8],max=new double[8];
        String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
        int floorRows=0,aboveFloorRows=0,blocked=0;double maxCurrent=0;
        try(var z=new ZipFile(archive.toFile())){
            String prefix="floor"+minimum+"/";byte[] raw=z.getInputStream(z.getEntry(prefix+"a/native.csv")).readAllBytes();
            assertArrayEquals(raw,z.getInputStream(z.getEntry(prefix+"b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(raw);assertTrue(rows.size()>1005);
            for(var row:rows){
                for(int j=0;j<3;j++)state[j]=row.get("STATE"+j);runtime.applyStaggeredState(state,0,0);
                double vf=row.get("VAR2"),cf=row.get("VAR3"),vr=row.get("V7"),vi=row.get("V9"),ar=Math.toRadians(row.get("A7")),ai=Math.toRadians(row.get("A9"));
                var mode=row.get("VAR11")==0?OperatingMode.BLOCKED:row.get("VAR9")==0?OperatingMode.BYPASSED:
                    vf<1||cf<1?OperatingMode.RECOVERING:OperatingMode.NORMAL;
                runtime.evaluateBoundary(vr*Math.cos(ar),vr*Math.sin(ar),vi*Math.cos(ai),vi*Math.sin(ai),0,mode,vf,cf,injections,0);
                runtime.readBoundary(boundary,0);
                double current=boundary[6]==0?0:boundary[0]*1e8/boundary[6];
                maxCurrent=Math.max(maxCurrent,Math.abs(current-row.get("VAR11")));
                for(int j=0;j<8;j++)max[j]=Math.max(max[j],Math.abs((j==2||j==5?Math.toDegrees(boundary[j]):boundary[j])-row.get(keys[j])));
                if(mode==OperatingMode.BLOCKED){blocked++;assertEquals(0,current,0);}
                if(mode==OperatingMode.RECOVERING){
                    if(cf*1000<minimum){floorRows++;assertEquals(minimum,current,1e-9);}
                    else aboveFloorRows++;
                }
            }
        }
        assertTrue(floorRows>50);assertTrue(aboveFloorRows>50);assertTrue(blocked>0);
        System.out.println("CDC6T minimum="+minimum+" current error="+maxCurrent+" boundary="+java.util.Arrays.toString(max));
        assertTrue(maxCurrent<.000034,"Independent current error in amperes: "+maxCurrent);
        double[] limits={7e-7,2.4e-5,.000037,7e-7,6.7e-6,.000059,.034,.018};
        for(int j=0;j<8;j++)assertTrue(max[j]<limits[j],keys[j]+" error "+max[j]);
    }
    @Test void normalFloorCeilingAndBlockedOverrideRemainDistinct(){
        var r=Cdc6tOperatingBoundaryTest.runtime(400);double[] out=new double[4],b=new double[8];
        r.evaluateBoundary(1,0,1,0,-450,out,0);r.readBoundary(b,0);
        assertEquals(400,b[0]*1e8/b[6],1e-12,"Minimum demand also limits normal orders");
        r.applyStaggeredState(new double[]{200000,1000,505000},0,0);
        r.evaluateBoundary(1,0,1,0,0,OperatingMode.RECOVERING,.5,.9,out,0);r.readBoundary(b,0);
        assertEquals(450,b[0]*1e8/b[6],1e-12,"Voltage-dependent ceiling still applies");
        r.evaluateBoundary(0,0,0,0,0,OperatingMode.BLOCKED,1,1,out,0);assertArrayEquals(new double[4],out);
        for(double invalid:new double[]{-1,451,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->Cdc6tOperatingBoundaryTest.runtime(invalid));
    }
}
