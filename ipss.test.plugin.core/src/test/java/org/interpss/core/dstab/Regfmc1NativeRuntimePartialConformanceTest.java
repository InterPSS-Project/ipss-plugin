package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.Regfmc1NativeContractReference;
import org.interpss.core.dstab.reference.Regfmc1SelectorNativeReference;
import org.interpss.dstab.renewable.Regfmc1Data;
import org.interpss.dstab.renewable.Regfmc1Kernel;
import org.junit.jupiter.api.Test;

/** Recorded voltage is the only replay input; native states are never injected. */
public class Regfmc1NativeRuntimePartialConformanceTest {
    private static List<Map<String,Double>> rows(String csv) {
        var lines=csv.lines().toList();String[] names=lines.getFirst().split(",");List<Map<String,Double>> rows=new ArrayList<>();
        for(String line:lines.subList(1,lines.size())){String[] values=line.split(",");Map<String,Double> row=new HashMap<>();for(int i=0;i<names.length;i++)row.put(names[i],Double.parseDouble(values[i]));rows.add(row);}return rows;
    }
    private static Complex terminal(Map<String,Double> r){double a=Math.toRadians(r.get("A1_DEG"));return new Complex(Math.cos(a),Math.sin(a)).multiply(r.get("V1"));}
    private record Profile(String name,double step,String body,Regfmc1Data data){}
    private static void references(Regfmc1Kernel model,Map<String,Double> row) {
        // These are independently declared manual inputs, not controller STATEs or fitted play-in.
        model.setReferenceSteps(0,0,row.get("VAR1"),row.get("VAR3"),row.get("VAR4"),row.get("VAR2"));
    }
    private static double[] stateBounds(Profile p) {
        boolean coarse=p.step()==.0005;
        if(p.name().startsWith("flat"))return new double[]{1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1.8e-8};
        if(p.name().startsWith("fault"))return coarse
                ?new double[]{5.2e-4,1e-12,.00427,1.72e-5,5.66e-6,6.92e-6,1e-12,1.52e-4,1.04e-6,2.46e-5,3.75e-5}
                :new double[]{2.67e-4,1e-12,.002254,4.29e-6,1.61e-6,6.92e-6,1e-12,1.52e-4,1.04e-6,2.46e-5,3.75e-5};
        if(p.name().startsWith("limit"))return coarse
                ?new double[]{.00795,1e-12,.06510,.000239,.00007984,3.42e-6,1e-12,.001143,5.56e-7,8.18e-6,1.038e-5}
                :new double[]{.004075,1e-12,.03436,.00006176,.00002061,8.69e-6,1e-12,.0004253,9.51e-7,.00001415,.00002168};
        if(p.name().startsWith("bounded"))return coarse
                ?new double[]{.008028,.0001205,.06563,.000400,4.56e-6,6.43e-6,.00002385,.0001523,5.12e-6,2.71e-6,3.43e-6}
                :new double[]{.004118,.0000613,.03465,.0001615,2.68e-6,.00002376,.00001127,.00007838,2.59e-6,2.48e-6,3.11e-6};
        return coarse?new double[]{.0005224,.0001205,.004291,.000472,.0001393,1e-12,1e-12,.000043,.00001005,.000006,.0000075}
                :new double[]{.0002679,.0000613,.002269,.0002352,.0000692,1e-12,1e-12,.00002144,.000005116,.000001494,.000001915};
    }
    private static double[] boundaryBounds(Profile p) {
        boolean coarse=p.step()==.0005;
        if(p.name().startsWith("flat"))return new double[]{1e-12,1e-12,1e-12};
        if(p.name().startsWith("fault"))return coarse?new double[]{.000247,.000228,.000109}:new double[]{.000165,.000168,.0000585};
        if(p.name().startsWith("limit"))return coarse?new double[]{.00432,.00464,.000741}:new double[]{.004355,.004675,.000194};
        if(p.name().startsWith("bounded"))return coarse?new double[]{.000779,.0000155,.000844}:new double[]{.000352,.0000146,.000200};
        return coarse?new double[]{.000605,.000233,.000622}:new double[]{.000300,.000120,.000309};
    }
    @Test void replayEveryStateFromRecordedTerminalVoltageOnly() throws Exception {
        List<Profile> profiles=new ArrayList<>();
        for(var p:Regfmc1NativeContractReference.profiles())profiles.add(new Profile(p.name(),p.step(),p.body(),Regfmc1NativeContractReference.parameters()));
        for(var p:Regfmc1SelectorNativeReference.profiles())profiles.add(new Profile(p.name(),p.step(),p.body(),p.parameters()));
        Map<String,double[]> coarseByMode=new HashMap<>();
        for(var profile:profiles) {
            var rows=rows(profile.body()).stream().filter(r->r.get("time_s")>=0).toList();var initial=rows.getFirst();
            var m=new Regfmc1Kernel(profile.data(),60);
            m.initialize(terminal(initial),new Complex(2*initial.get("P_PU"),2*initial.get("Q_PU")),profile.step());
            double[] maximum=new double[11],at=new double[11],boundaryMax=new double[3],boundaryAt=new double[3];
            StringBuilder trace=new StringBuilder("time_s");for(int i=0;i<11;i++)trace.append(",IPSS_STATE").append(i);for(int i=0;i<11;i++)trace.append(",NATIVE_STATE").append(i);trace.append(",IPSS_P,IPSS_Q,NATIVE_P,NATIVE_Q\n");
            for(int i=0;i<rows.size();i++) {
                var r=rows.get(i);
                if(i==0||r.get("time_s").equals(rows.get(i-1).get("time_s"))){references(m,r);m.step(0,terminal(r));}
                else {references(m,rows.get(i-1));m.step(profile.step(),terminal(rows.get(i-1)),0);references(m,r);m.step(profile.step(),terminal(r),1);}
                double[] states=m.stateSnapshot();for(int c=0;c<11;c++){double err=Math.abs(states[c]-r.get("STATE"+c));if(err>maximum[c]){maximum[c]=err;at[c]=r.get("time_s");}}
                Complex v=terminal(r),current=m.boundary(v).totalCurrent(),nativeCurrent=new Complex(2*r.get("P_PU"),2*r.get("Q_PU")).divide(v).conjugate();
                Complex power=v.multiply(current.conjugate());double[] error={current.subtract(nativeCurrent).abs(),Math.abs(power.getReal()-2*r.get("P_PU")),Math.abs(power.getImaginary()-2*r.get("Q_PU"))};
                for(int j=0;j<3;j++)if(error[j]>boundaryMax[j]){boundaryMax[j]=error[j];boundaryAt[j]=r.get("time_s");}
                trace.append(r.get("time_s"));for(double state:states)trace.append(',').append(state);for(int j=0;j<11;j++)trace.append(',').append(r.get("STATE"+j));
                trace.append(',').append(power.getReal()).append(',').append(power.getImaginary()).append(',').append(2*r.get("P_PU")).append(',').append(2*r.get("Q_PU")).append('\n');
            }
            System.out.println("REGFMC1U kernel replay "+profile.name()+" errors="+Arrays.toString(maximum)+" times="+Arrays.toString(at)+" current/P/Q="+Arrays.toString(boundaryMax)+" at="+Arrays.toString(boundaryAt));
            Path directory=Path.of("target","regfmc-kernel-replay");Files.createDirectories(directory);Files.writeString(directory.resolve(profile.name()+".csv"),trace.toString());
            double[] bounds=stateBounds(profile),outputBounds=boundaryBounds(profile);
            for(int i=0;i<11;i++)assertTrue(maximum[i]<bounds[i],profile.name()+" STATE"+i+" error="+maximum[i]+" at="+at[i]);
            for(int i=0;i<3;i++)assertTrue(boundaryMax[i]<outputBounds[i],profile.name()+" physical channel "+i+" error="+boundaryMax[i]+" at="+boundaryAt[i]);
            // Step convergence is asserted only where truncation dominates.
            // Absolute-frequency float locking and current-circle tangency are
            // independently diagnosed, not hidden by claiming uniform convergence.
            if(!profile.name().startsWith("flat")) {
                String mode=profile.name().replace("-coarse","").replace("-fine","");
                if(profile.step()==.0005)coarseByMode.put(mode,maximum);
                else {
                    int[] states=mode.equals("bounded")?new int[]{0,1,2,3,4,6,7,8}
                            :mode.contains("off")?new int[]{0,1,2,3,4,7,8,9,10}:new int[]{0,2,3,4};
                    for(int state:states)assertTrue(maximum[state]<.65*coarseByMode.get(mode)[state],profile.name()+" step-halving STATE"+state);
                }
            }
        }
    }
    @Test void nativeFrequencyOffDiscriminatorLeavesUnusedStatesZeroWhileManualReferencesChange() {
        for(var profile:Regfmc1SelectorNativeReference.profiles())if(!profile.parameters().enabled(Regfmc1Data.Flag.FFLAG)) {
            var rows=rows(profile.body());int excited=0;
            for(var r:rows){assertEquals(0,r.get("STATE5"));assertEquals(0,r.get("STATE6"));if(r.get("VAR3")!=0)excited++;}
            assertTrue(excited>100);
            if(profile.mode().equals("frequency-off")) {
                assertArrayEquals(new int[]{0,0,1},profile.parameters().toNativeIcons());
                assertArrayEquals(Regfmc1NativeContractReference.parameters().constants(),profile.parameters().constants());
            }
        }
    }
    @Test void nativeAbsoluteSpeedMeasurementExposesAFloatRoundingFloor() {
        for(var profile:Regfmc1NativeContractReference.profiles())if(profile.name().startsWith("fault")) {
            double doubleState=1,maxDeparture=0;var rows=rows(profile.body()).stream().filter(r->r.get("time_s")>=0).toList();
            for(int i=0;i<rows.size();i++) {
                var r=rows.get(i);assertEquals(1,r.get("STATE5"));
                double inputDeviation=r.get("STATE8"),increment=profile.step()*inputDeviation/.3;
                double halfSpacing=inputDeviation>=0?.5*Math.ulp(1.0f):.5*(1.0-(double)Math.nextDown(1.0f));
                assertTrue(Math.abs(increment)<halfSpacing,"Each native float increment rounds back to 1.0");
                if(i>0&&!r.get("time_s").equals(rows.get(i-1).get("time_s")))doubleState+=profile.step()*(1+inputDeviation-doubleState)/.3;
                maxDeparture=Math.max(maxDeparture,Math.abs(doubleState-1));
            }
            assertTrue(maxDeparture>5e-6,"The ideal filter does not remain exactly one; do not fit the double-precision model to native rounding");
        }
    }
    @Test void nativeReferenceDiscontinuityDiscriminatesStoredHistoryFromModifiedEuler() {
        for(var profile:Regfmc1SelectorNativeReference.profiles()) {
            var rows=rows(profile.body());int post=-1;
            for(int i=1;i<rows.size();i++)if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))){post=i;break;}
            assertTrue(post>0);var start=rows.get(post);var next=rows.get(post+1);
            assertEquals(.5,start.get("VAR2"));assertEquals(0,start.get("STATE3"));
            double euler=profile.step()*profile.parameters().value(Regfmc1Data.Parameter.KIE)*.5;
            assertEquals(.75,(next.get("STATE3")-start.get("STATE3"))/euler,2e-6);
        }
    }
    @Test void nativeSelectorReferencesAndBothActiveBoundsAreRetained() {
        for(var profile:Regfmc1SelectorNativeReference.profiles()) {
            var samples=rows(profile.body());assertEquals(profile.samples(),samples.size());assertEquals(49,samples.getFirst().size());
            int duplicates=0,upperPi=0,lowerPi=0,upperSpeed=0,lowerSpeed=0,activePriority=0;
            for(int i=0;i<samples.size();i++) {
                var r=samples.get(i);if(i>0&&r.get("time_s").equals(samples.get(i-1).get("time_s")))duplicates++;
                double sign=Math.signum(r.get("VAR4"));assertEquals((double)(float)(.01*sign),r.get("VAR1"),1e-12);
                assertEquals(.5*sign,r.get("VAR2"),1e-12);assertEquals((double)(float)(.002*sign),r.get("VAR3"),1e-12);
                if(profile.mode().equals("bounded")) {
                    if(Math.abs(r.get("STATE3")-.002)<1e-10)upperPi++;if(Math.abs(r.get("STATE3")+.002)<1e-10)lowerPi++;
                    if(Math.abs(r.get("STATE8")-.00005)<1e-11)upperSpeed++;if(Math.abs(r.get("STATE8")+.00005)<1e-11)lowerSpeed++;
                    if(Math.abs(r.get("VAR14")-.42)<3e-8)activePriority++;
                }
            }
            assertEquals(5,duplicates);assertEquals(2,samples.stream().filter(r->r.get("time_s")<0).count());
            if(profile.mode().equals("bounded")){assertTrue(upperPi>20);assertTrue(lowerPi>20);assertTrue(upperSpeed>20);assertTrue(lowerSpeed>20);assertTrue(activePriority>20);}
        }
    }
}
