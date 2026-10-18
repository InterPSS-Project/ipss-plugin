package org.interpss.core.dstab;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.interpss.dstab.renewable.*;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile;
import static org.junit.jupiter.api.Assertions.*;

/** Recorded-input lag comparisons; not full hybrid-plant runtime acceptance. */
public class ReaxdLagNativeConformanceTest {
    private static List<Profile> profiles(){var p=new ArrayList<Profile>();
        for(var existing:HplntdAggregationNativeReference.profiles())if(existing.name().startsWith("commands")||existing.name().equals("flat-auto-coarse")||existing.name().equals("fixed-continuous-coarse"))p.add(existing);
        p.addAll(List.of(
            new Profile("reaxd-limits-coarse",.0005,405,"7d568fd3db0c8c3588c488877ae736e0856fd2cc8439cef65daf9957d5ee5ca6","4249ac02f1899f43d35bd5e5b9d18137d3104fe9391bd54eb6dce2b6cb86ae1b"),
            new Profile("reaxd-limits-fine",.00025,805,"f5dbb990f527a40b057fe19eb14af6292ff6d3c160082f468e23ad7a082e9220","4249ac02f1899f43d35bd5e5b9d18137d3104fe9391bd54eb6dce2b6cb86ae1b"),
            new Profile("reaxd-minimum-time-coarse",.0005,405,"694beff207876f209828b0ab01288037cfb7c9df8c8c51563a3f0618452ce759","7bc6e12ad81aba31be9f7675cc331f4352cc5f4f36de1b298990eed5773cfc35"),
            new Profile("reaxd-minimum-time-fine",.00025,805,"83ed4df81a9f8cb276bff02a0805847e1eb314fba59350a8bb7ae1fd0f7c92bd","7bc6e12ad81aba31be9f7675cc331f4352cc5f4f36de1b298990eed5773cfc35")
        ));return p;
    }
    private static double value(double[] row,Map<String,Integer> columns,String name){return row[columns.get(name)];}
    private static ReaxdKernel.Input input(double[] row,Map<String,Integer> columns,String prefix){return new ReaxdKernel.Input(value(row,columns,prefix+"VAR2")*value(row,columns,prefix+"VAR4"),value(row,columns,prefix+"VAR3")*value(row,columns,prefix+"VAR5"));}
    @Test void replayAllFourStatesAtBothSteps()throws Exception {
        var maxima=new LinkedHashMap<String,double[]>();
        for(var profile:profiles()) {
            String[] lines=profile.body().strip().split("\\R"),names=lines[0].split(",");
            var columns=new HashMap<String,Integer>();for(int i=0;i<names.length;i++)columns.put(names[i],i);
            var rows=new ArrayList<double[]>();for(int i=1;i<lines.length;i++)rows.add(Arrays.stream(lines[i].split(",")).mapToDouble(Double::parseDouble).toArray());
            assertEquals(profile.samples(),rows.size());assertEquals(89,names.length);
            var kernels=new ArrayList<ReaxdKernel>();
            for(int bus:new int[]{1,4}){var k=new ReaxdKernel(profile.downstream(bus));k.initialize(value(rows.get(0),columns,"UNIT"+bus+"_VAR0"),value(rows.get(0),columns,"UNIT"+bus+"_VAR1"));kernels.add(k);}
            double[] maximum=new double[4],at=new double[4];boolean[] limitsActivated=new boolean[4];
            var trace=new StringBuilder("time_s,unit1_active_native,unit1_active_interpss,unit1_reactive_native,unit1_reactive_interpss,unit4_active_native,unit4_active_interpss,unit4_reactive_native,unit4_reactive_interpss\n");
            double[] previous=rows.get(0);
            for(var row:rows) {
                if(previous[0]>=0&&row[0]-previous[0]>profile.step()*.5)
                    for(int unit=0;unit<2;unit++){String prefix="UNIT"+(unit==0?1:4)+"_";var k=kernels.get(unit);k.step(profile.step(),input(previous,columns,prefix),0);k.step(profile.step(),input(row,columns,prefix),1);}
                trace.append(row[0]);
                for(int unit=0;unit<2;unit++)for(int axis=0;axis<2;axis++){
                    String prefix="UNIT"+(unit==0?1:4)+"_";int index=2*unit+axis;
                    double actual=kernels.get(unit).states()[axis],expected=value(row,columns,prefix+"STATE"+axis),error=Math.abs(actual-expected);
                    if(error>maximum[index]){maximum[index]=error;at[index]=row[0];}
                    trace.append(',').append(expected).append(',').append(actual);
                    if(profile.name().contains("limits")){double output=axis==0?kernels.get(unit).output().active():kernels.get(unit).output().reactive();if(Math.abs(actual-output)>1e-4)limitsActivated[index]=true;}
                }
                trace.append('\n');previous=row;
            }
            boolean fine=profile.step()<.0005,minimum=profile.name().contains("minimum");
            double[] bounds=minimum?(fine?new double[]{2.30e-5,1.75e-5,4.60e-5,8.75e-6}:new double[]{4.65e-5,3.50e-5,9.30e-5,1.75e-5})
                    :(fine?new double[]{9.30e-7,6.10e-7,1.83e-6,3.10e-7}:new double[]{3.55e-6,2.20e-6,7.10e-6,1.10e-6});
            if(profile.name().startsWith("flat")||profile.name().startsWith("fixed"))Arrays.fill(bounds,1e-10);
            for(int i=0;i<4;i++)assertTrue(maximum[i]<=bounds[i],profile.name()+" state "+i+" max="+maximum[i]+" at="+at[i]+" bound="+bounds[i]);
            if(profile.name().contains("limits"))for(int i=0;i<4;i++)assertTrue(limitsActivated[i],"Output limit "+i+" must actually activate while storage continues");
            maxima.put(profile.name(),maximum);
            var out=Path.of("target/reaxd-lag-comparisons",profile.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace);
            System.out.println(profile.name()+" max="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        }
        for(String coarse:new String[]{"commands-coarse","reaxd-limits-coarse"}){
            var a=maxima.get(coarse);var b=maxima.get(coarse.replace("coarse","fine"));for(int i=0;i<4;i++)assertTrue(b[i]<a[i]*.35,"Ordinary lag discrepancy must reduce at the second-order step-halving rate");
        }
        // Minimum-time profiles change the effective lag itself when dt changes;
        // they are NOT a convergence proof for a fixed physical time constant.
    }
}
