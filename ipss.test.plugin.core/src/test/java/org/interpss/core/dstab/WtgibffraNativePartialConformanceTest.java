package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.interpss.core.dstab.reference.WtgibffraNativeReference;
import org.interpss.core.dstab.reference.WtgibffraNativeReference.Profile;
import org.interpss.core.dstab.reference.WtgibffraNativeReference.Transition;
import org.interpss.dstab.renewable.WtgibffraModel;
import org.junit.jupiter.api.Test;

/** Kernel replay only: no assertion of native import, network coupling or callback parity. */
public class WtgibffraNativePartialConformanceTest {
    private record Observed(double[] state,double[] vars,int mode) {}

    @Test void coldNativeProfilesCheckEveryPublishedStateAndVariableInSustainedModes() {
        for (Profile profile : WtgibffraNativeReference.profiles()) replay(profile);
    }

    @Test void immutableInputsAndCheckpointsHaveIndependentlyFrozenDigests() {
        StringBuilder bundle=new StringBuilder();
        for (Profile p:WtgibffraNativeReference.profiles()) {
            assertEquals(p.inputsSha256(),WtgibffraNativeReference.digest(p.inputs()));
            assertEquals(p.checkpointsSha256(),WtgibffraNativeReference.digest(p.states()));
            bundle.append(p.rawSha256()).append('|').append(p.inputsSha256()).append('|').append(p.checkpointsSha256()).append('\n');
            double[] copy=p.constants();copy[0]=99;assertEquals(.001,p.parameters().deadband());
            assertThrows(UnsupportedOperationException.class,()->p.transitions().clear());
        }
        assertEquals(7,WtgibffraNativeReference.profiles().size());
        assertEquals("1cc1cbb37532a2e576f166c551b7d556ba05be861bcfcaae2e20b4b8c8ebf8f6",WtgibffraNativeReference.digest(bundle.toString()));
    }

    @Test void automaticRecoveryUsesPublishedEnergyBalanceNotTheObservedNativeShortRecovery() {
        var constants=WtgibffraNativeReference.profiles().getFirst().constants();
        for(int i=37;i<43;i++)constants[i]=0;
        var data=org.interpss.dstab.renewable.WtgibffraData.fromNativeParameters(3,constants);
        assertEquals(.7,data.bands().getFirst().effectiveRecoveryTime(),1e-15);
        // Native 36.7 discriminator reports ~.52 and completes recovery in a
        // single sample. Preserve that observation as a discrepancy, not an oracle.
        assertNotEquals(.519999980927,data.bands().getFirst().effectiveRecoveryTime(),.1);
    }

    private static void replay(Profile p) {
        List<double[]> inputs=p.inputs().lines().map(WtgibffraNativePartialConformanceTest::numbers).toList();
        assertEquals(p.samples(),inputs.size());
        var model=new WtgibffraModel(p.parameters());model.initialize(1);
        var observed=new ArrayList<Observed>();var transitions=new ArrayList<Transition>();
        int previous=Integer.MIN_VALUE;
        for(int row=0;row<inputs.size();row++) {
            double[] input=inputs.get(row);
            if(row==0)model.step(0,input[0],1+input[1]);
            else {double[] old=inputs.get(row-1);model.step(p.step(),old[0],1+old[1],0);model.step(p.step(),input[0],1+input[1],1);}
            int mode=model.getMode().code();
            if(mode!=previous){transitions.add(new Transition(row,row*p.step(),mode));previous=mode;}
            observed.add(new Observed(model.getNativeStates(),model.getVariableSnapshot(),mode));
        }
        assertEquals(p.transitions().stream().map(Transition::mode).toList(),transitions.stream().map(Transition::mode).toList(),p.name());
        // Native float constants/timers can add one sampled tick at each
        // completed handoff. This accumulates on the persistent second cycle;
        // it is not a claim of pointwise callback equivalence.
        double lag=0;
        for(int i=0;i<transitions.size();i++) {
            double delta=Math.abs(p.transitions().get(i).rawTime()-transitions.get(i).rawTime());
            assertTrue(delta<=(i+1)*p.step()+1e-4,p.name()+" cumulative phase "+i+": "+delta);
            lag=Math.max(lag,delta);
        }
        double window=lag+p.step()+1e-4;
        double[] raw=new double[21],overlap=new double[21];int sustained=0,checkpoints=0;
        for(String line:p.states().lines().toList()) {
            double[] values=numbers(line);assertEquals(23,values.length);
            int row=(int)values[0];Observed actual=observed.get(row);double time=row*p.step();
            boolean callback=p.transitions().stream().anyMatch(t->Math.abs(t.rawTime()-time)<=window)
                    ||transitions.stream().anyMatch(t->Math.abs(t.rawTime()-time)<=window);
            boolean same=actual.mode()==(int)values[22];
            for(int c=0;c<21;c++) {
                double value=c<4?actual.state()[c]:actual.vars()[c-4];
                double error=Math.abs(value-values[c+2]);raw[c]=Math.max(raw[c],error);
                // The filter is continuous: never exclude it at callbacks.
                if(c==3)assertTrue(error<=(p.step()==.0005?4.9e-6:2.6e-6),p.name()+" frequency filter "+error);
                if(same&&!callback)overlap[c]=Math.max(overlap[c],error);
            }
            if(same&&!callback)sustained++;
            checkpoints++;
        }
        assertEquals(p.checkpoints(),checkpoints);assertTrue(sustained>400,p.name());
        assertEquals(p.samples()-1,(int)numbers(p.states().lines().toList().getLast())[0]);
        double h=p.step(),power=inputs.stream().mapToDouble(x->x[0]).max().orElseThrow();
        var band=p.parameters().bands().getFirst();
        double roundoff=p.samples()*Math.ulp((float)((band.boost()+band.deficit())*power));
        double[] limits=new double[21];Arrays.fill(limits,1e-7);
        // Analytic ramp error due to the separately bounded phase-start lag;
        // no time shifting or gain/initial-state fitting is performed.
        limits[0]=band.boost()*power/band.riseTime()*(lag+h)+roundoff;
        limits[1]=(band.boost()+band.deficit())*power/band.fallTime()*(lag+h)+roundoff;
        limits[2]=band.deficit()*power/1.2*(lag+h)+roundoff;
        limits[3]=limits[4]=h==.0005?4.9e-6:2.6e-6;
        limits[5]=limits[16]=limits[17]=1e-6; // captured power and derived Pmax/Pmin
        limits[18]=limits[19]=window; // absolute peak/cooldown clocks
        for(int c=0;c<21;c++)assertTrue(overlap[c]<=limits[c],p.name()+" sustained channel "+c+": "+overlap[c]+" > "+limits[c]);
        System.out.println("WTGIBFFRA partial kernel "+p.name()+" raw-max="+Arrays.toString(raw)+" sustained="+Arrays.toString(overlap)
                +"; instantaneous callbacks and native automatic recovery are not accepted");
    }
    private static double[] numbers(String line){return Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();}
}
