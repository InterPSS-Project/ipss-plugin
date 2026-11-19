package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import com.interpss.dstab.hvdc.model.*;
import com.interpss.dstab.hvdc.model.Cdc6tNormalRuntime.OperatingMode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Recorded-regime electrical checks; mode decisions and full protected trajectories remain separate. */
public class Cdc6tOperatingBoundaryTest {
    static Cdc6tNormalRuntime runtime(){
        return runtime(0);
    }
    static Cdc6tNormalRuntime runtime(double minimumCurrent){
        var r=new Cdc6tNormalRuntime(new Cdc6tMeasurements(.05,.05,.05),
                new Cdc6tCurrentOrder(Cdc6tCurrentOrder.Mode.POWER,-500,500,0),
                Cdc6tVoltageCurrentLimit.fromSourcePoints(250,450,500,2000,425,2000),
                new Cdc6tConductingLink(new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
                        new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),5,Math.toRadians(5),Math.toRadians(20)),minimumCurrent);
        r.initialize(500000,1000,505000);return r;
    }
    @ParameterizedTest @CsvSource({"blocking,instant","blocking,delayed","inverter-blocking,short","inverter-blocking,long","bypass,dc","bypass,ac"})
    void recordedRegimesAndRecoveryFactorsReproduceEveryElectricalBoundary(String group,String profile)throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-"+group+"-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent protection captures");
        String hash=switch(group){
            case "blocking" -> "9a39f37ab6519a3a11f7cc1992a38d1b66c3b301d08cec8635899bce55b286e5";
            case "inverter-blocking" -> "0626f2f1f0a173678d0836a9b3411a3f0f059fbfc6ad9b1258f6c3f3cd66f787";
            default -> "4af03eff4e7d3699b4eba5bce4088e796625179c81a460ab40362d9eaae2fda7";
        };
        assertEquals(hash,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var model=runtime();double[] state=new double[3],current=new double[4],boundary=new double[8],max=new double[8];
        int nonconducting=0,recovering=0,limited=0,duplicates=0;double previous=Double.NEGATIVE_INFINITY;
        String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
        try(var zip=new ZipFile(archive.toFile())){
            byte[] raw=zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry(profile+"/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(raw);assertTrue(rows.size()>1005);
            for(var row:rows){
                double t=row.get("time_s");assertTrue(t>=previous);if(t==previous)duplicates++;previous=t;
                for(int j=0;j<3;j++)state[j]=row.get("STATE"+j);model.applyStaggeredState(state,0,0);
                double vf=row.get("VAR2"),cf=row.get("VAR3");
                // Modes are supplied by the independent electrical status. This test does not infer timer correctness.
                OperatingMode mode=row.get("VAR11")==0?OperatingMode.BLOCKED:row.get("VAR9")==0?OperatingMode.BYPASSED
                        :vf<1||cf<1?OperatingMode.RECOVERING:OperatingMode.NORMAL;
                if(mode==OperatingMode.BLOCKED||mode==OperatingMode.BYPASSED)nonconducting++;
                if(mode==OperatingMode.RECOVERING){recovering++;if(row.get("VAR11")<1000*cf-.001)limited++;}
                if(mode!=OperatingMode.RECOVERING){vf=1;cf=1;}
                double ar=Math.toRadians(row.get("A7")),ai=Math.toRadians(row.get("A9")),vr=row.get("V7"),vi=row.get("V9");
                model.evaluateBoundary(vr*Math.cos(ar),vr*Math.sin(ar),vi*Math.cos(ai),vi*Math.sin(ai),0,mode,vf,cf,current,0);
                model.readBoundary(boundary,0);
                for(int j=0;j<8;j++)max[j]=Math.max(max[j],Math.abs((j==2||j==5?Math.toDegrees(boundary[j]):boundary[j])-row.get(keys[j])));
                double[] after=new double[3];model.readStaggeredState(after,0);assertArrayEquals(state,after,"Electrical reads cannot integrate states");
                assertEquals(-boundary[0],vr*(Math.cos(ar)*current[0]+Math.sin(ar)*current[1]),2e-14);
                assertEquals(-boundary[3],vi*(Math.cos(ai)*current[2]+Math.sin(ai)*current[3]),2e-14);
            }
        }
        assertTrue(duplicates>2);assertTrue(nonconducting>0);assertTrue(recovering>0);assertTrue(limited>0);
        System.out.println("CDC6T operating boundary "+group+"/"+profile+" "+java.util.Arrays.toString(max));
        double[] tolerance={3e-6,2.8e-5,.00007,2.6e-6,1e-5,.00007,.21,.21};
        for(int j=0;j<8;j++)assertTrue(max[j]<tolerance[j],keys[j]+" error "+max[j]);
    }
    @Test void sourcePlateauMustNotBeSortedIntoADifferentSlope(){
        var c=Cdc6tVoltageCurrentLimit.fromSourcePoints(250,450,500,2000,425,2000);
        assertEquals(450,c.ceilingAmperes(0),0);assertEquals(760,c.ceilingAmperes(300000),1e-12);
        assertEquals(1535,c.ceilingAmperes(425000),1e-12);assertEquals(2000,c.ceilingAmperes(500000),0);
        assertEquals(2000,c.ceilingAmperes(600000),0);
        assertThrows(IllegalArgumentException.class,()->new Cdc6tVoltageCurrentLimit(250,450,500,2000,425,2000));
        assertThrows(IllegalArgumentException.class,()->Cdc6tVoltageCurrentLimit.fromSourcePoints(250,450,500,2000,425,1800));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void blockedMeasurementsDecayUnderEitherIntegrationMethod(boolean staggered){
        var r=runtime();double h=.0005;double[] current=new double[4],state=new double[3],rates=new double[3],prior=new double[3];
        for(int step=0;step<100;step++){
            r.evaluateBoundary(0,0,0,0,0,OperatingMode.BLOCKED,1,1,current,0);assertArrayEquals(new double[4],current);
            if(staggered){
                r.readStaggeredState(state,0);r.evaluateStaggered(h,rates,0);
                for(int j=0;j<3;j++){state[j]+=h*(step==0?rates[j]:1.5*rates[j]-.5*prior[j]);prior[j]=rates[j];}
                r.applyStaggeredState(state,0,h);
            } else {
                r.step(h,0);r.evaluateBoundary(0,0,0,0,0,OperatingMode.BLOCKED,1,1,current,0);r.step(h,1);
            }
        }
        r.readStaggeredState(state,0);
        double[] initial={500000,1000,505000};
        for(int j=0;j<3;j++)assertEquals(initial[j]*Math.exp(-1),state[j],initial[j]*1e-5);
    }
    @Test void bypassAndSwitchedCurrentAvoidPowerDivisionAndRejectInvalidTrials(){
        var r=runtime();double[] out=new double[4],boundary=new double[8];
        r.applyStaggeredState(new double[]{0,1000,5000},0,0);
        r.evaluateBoundary(1,0,0,0,0,OperatingMode.BYPASSED,1,1,out,0);r.readBoundary(boundary,0);
        assertEquals(.05,boundary[0],1e-15);assertEquals(5000,boundary[6],0);assertEquals(0,boundary[7],0);
        r.evaluateBoundary(1,0,1,0,0,OperatingMode.POWER_TO_CURRENT,1,1,out,0);r.readBoundary(boundary,0);
        assertEquals(2.25,boundary[3]*-1,1e-14,"Scheduled current is still ceiling-limited");
        double[] before=out.clone();
        assertThrows(IllegalArgumentException.class,()->r.evaluateBoundary(1,0,1,0,1,OperatingMode.RECOVERING,.5,.5,out,0));
        assertArrayEquals(before,out);
        assertThrows(IllegalStateException.class,()->r.readBoundary(boundary,0));
        assertThrows(IllegalArgumentException.class,()->r.evaluateBoundary(1,0,1,0,0,OperatingMode.NORMAL,.5,1,out,0));
    }
    @Test void warmedElectricalModesReuseNumericalBuffers(){
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        var r=runtime();var modes=OperatingMode.values();double[] out=new double[4];
        for(int i=0;i<30000;i++)for(var mode:modes)r.evaluateBoundary(1,0,1,0,0,mode,
                mode==OperatingMode.RECOVERING?.7:1,mode==OperatingMode.RECOVERING?.6:1,out,0);
        long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<100000;i++)for(var mode:modes)r.evaluateBoundary(1,0,1,0,0,mode,
                mode==OperatingMode.RECOVERING?.7:1,mode==OperatingMode.RECOVERING?.6:1,out,0);
        long bytes=bean.getThreadAllocatedBytes(thread)-before;
        System.out.println("CDC6T warmed electrical-mode allocation bytes="+bytes);
        // JIT and runtime bookkeeping can add about 1.5 kB to a warmed thread; one object per call would add megabytes.
        assertTrue(bytes<=16*1024,"Electrical kernels must reuse numerical buffers: "+bytes);
    }
}
