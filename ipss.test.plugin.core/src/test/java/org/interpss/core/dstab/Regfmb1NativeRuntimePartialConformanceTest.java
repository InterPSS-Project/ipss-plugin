package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.Regfmb1NativeContractReference;
import org.interpss.core.dstab.reference.Regfmb1SelectorNativeReference;
import org.interpss.dstab.renewable.Regfmb1Data;
import org.interpss.dstab.renewable.Regfmb1Kernel;
import org.junit.jupiter.api.Test;

/** Recorded terminal-input replay only; no native states are model inputs. */
public class Regfmb1NativeRuntimePartialConformanceTest {
    private static List<Map<String,Double>> rows(String csv) {
        var lines=csv.lines().toList();String[] names=lines.getFirst().split(",");List<Map<String,Double>> rows=new ArrayList<>();
        for(String line:lines.subList(1,lines.size())){String[] values=line.split(",");Map<String,Double> row=new HashMap<>();for(int i=0;i<names.length;i++)row.put(names[i],Double.parseDouble(values[i]));rows.add(row);}return rows;
    }
    private static Regfmb1Kernel.Terminal terminal(Map<String,Double> row) {
        double angle=Math.toRadians(row.get("A1_DEG"));Complex v=new Complex(Math.cos(angle),Math.sin(angle)).multiply(row.get("V1"));
        return Regfmb1Kernel.Terminal.fromPower(v,new Complex(row.get("P_PU"),row.get("Q_PU")).multiply(2));
    }
    private record Profile(String name,double step,String body,Regfmb1Data parameters){}
    @Test void nativeFlatAndFaultReplayEveryStateAtBothSteps() {
        List<Profile> profiles=new ArrayList<>();
        for(var p:Regfmb1NativeContractReference.profiles())profiles.add(new Profile(p.name(),p.step(),p.body(),Regfmb1NativeContractReference.parameters()));
        for(var p:Regfmb1SelectorNativeReference.profiles())if(!p.excitedFreeze())profiles.add(new Profile(p.name(),p.step(),p.body(),p.parameters()));
        Map<String,double[]> coarseByMode=new HashMap<>();
        for(var profile:profiles) {
            var rows=rows(profile.body()).stream().filter(r->r.get("time_s")>=0).toList();var m=new Regfmb1Kernel(profile.parameters(),60);m.initialize(terminal(rows.getFirst()));
            double[] maximum=new double[14],at=new double[14];
            for(int i=0;i<rows.size();i++) {
                var now=rows.get(i);
                if(i==0||now.get("time_s").equals(rows.get(i-1).get("time_s")))m.step(0,terminal(now));
                else {m.step(profile.step(),terminal(rows.get(i-1)),0);m.step(profile.step(),terminal(now),1);}
                double[] states=m.stateSnapshot();for(int c=0;c<14;c++){double error=Math.abs(states[c]-now.get("STATE"+c));if(error>maximum[c]){maximum[c]=error;at[c]=now.get("time_s");}}
            }
            System.out.println("REGFMB1U replay "+profile.name()+" errors="+Arrays.toString(maximum)+" times="+Arrays.toString(at));
            // Native STORE/event-history versus modified Euler: retain all
            // event sides and tighten each state to its measured two-step envelope.
            boolean flat=profile.name().contains("flat"),coarse=profile.step()==.0005;
            double[] bounds=flat?new double[]{1e-12,1e-12,1e-12,6e-9,2e-12,1e-12,1.9e-8,1.1e-9,1e-12,1e-12,5.2e-9,2.1e-8,2.2e-8,2.2e-8}
                    :coarse?new double[]{5.45e-4,1.19e-7,2.31e-6,4.37e-5,1.70e-4,1.15e-6,4.36e-5,6e-5,.00213,.000541,.00235,4.86e-6,2.2e-8,2.2e-8}
                    :new double[]{2.95e-4,3.12e-8,5.7e-7,2.30e-5,8.68e-5,6.04e-7,2.29e-5,3.11e-5,.001089,.0002774,.001205,3.57e-6,2.2e-8,2.2e-8};
            if(!profile.parameters().enabled(Regfmb1Data.Flag.ESFLAG))bounds[13]=1e-12;
            for(int i=0;i<14;i++)assertTrue(maximum[i]<bounds[i],profile.name()+" state "+i+": "+maximum[i]);
            if(!flat) {
                String mode=profile.name().replace("-coarse","").replace("-fine","");
                if(coarse)coarseByMode.put(mode,maximum);
                else for(int i=0;i<11;i++)assertTrue(maximum[i]<.65*coarseByMode.get(mode)[i],profile.name()+" step-halving state "+i);
            }
        }
    }
    @Test void nativeWashoutStoresFilteredD2TimesSpeedInThisDiscriminator() {
        for(var profile:Regfmb1NativeContractReference.profiles())for(var row:rows(profile.body()))
            assertEquals(-row.get("STATE0"),row.get("STATE2"),5e-8,"Equal pole and inverse-droop/D2 gains expose the native scaled coordinate");
    }
    @Test void nativeFaultFilterFirstStepDiscriminatesTheIntegrationMethod() {
        for(var profile:Regfmb1NativeContractReference.profiles())if(profile.name().startsWith("fault")) {
            var rows=rows(profile.body());int post=-1;
            for(int i=1;i<rows.size();i++)if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))){post=i;break;}
            assertTrue(post>0);var start=rows.get(post);var next=rows.get(post+1);
            double fullEuler=profile.step()/.02*(2*start.get("Q_PU")-start.get("STATE8"));
            assertEquals(.75,(next.get("STATE8")-start.get("STATE8"))/fullEuler,5e-5);
            // An ordinary modified-Euler lag would advance by almost one full
            // Euler increment, not the native discontinuity-history 3/4 increment.
            assertTrue(1-.5*profile.step()/.02>.98);
        }
    }
    @Test void nativeCurrentDroopVoltageReferenceUsesTheNegativeOfDqReactiveCurrent() {
        for(var profile:Regfmb1SelectorNativeReference.profiles())if(profile.name().startsWith("current-voltage-flat")) {
            var r=rows(profile.body()).getFirst();assertTrue(r.get("STATE10")<-.1);
            assertEquals(r.get("STATE9")-.05*r.get("STATE10"),r.get("VAR11"),2e-7);
            assertTrue(Math.abs(r.get("VAR11")-(r.get("STATE9")+.05*r.get("STATE10")))>.02);
        }
    }
    @Test void nativeNonzeroPllOutputDropsItsProportionalPathWhileFrozen() {
        for(var profile:Regfmb1SelectorNativeReference.profiles())if(profile.excitedFreeze()) {
            var rows=rows(profile.body());assertEquals(profile.samples(),rows.size());int frozen=0;boolean nonzeroBefore=false;
            for(int i=0;i<rows.size();i++) {
                var r=rows.get(i);
                if(r.get("V1")<.05) {
                    assertEquals(r.get("STATE5"),r.get("VAR2"),0,"Frozen PI output equals retained integral, not prior total output");
                    if(frozen>1){assertEquals(rows.get(i-1).get("STATE5"),r.get("STATE5"),0);assertEquals(rows.get(i-1).get("STATE6"),r.get("STATE6"),0);}
                    frozen++;
                } else if(r.get("time_s")<.05&&Math.abs(r.get("VAR2")-r.get("STATE5"))>1e-5)nonzeroBefore=true;
            }
            assertTrue(nonzeroBefore);assertTrue(frozen>50);
        }
    }
}
