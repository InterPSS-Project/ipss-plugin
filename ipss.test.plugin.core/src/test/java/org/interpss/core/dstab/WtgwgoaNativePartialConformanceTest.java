package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.interpss.core.dstab.reference.WtgwgoaNativeReference;
import org.interpss.core.dstab.reference.WtgwgoaNativeReference.Profile;
import org.interpss.core.dstab.reference.WtgwgoaNativeReference.Transition;
import com.interpss.dstab.renewable.WtgwgoaModel;
import org.junit.jupiter.api.Test;

/** Hybrid timing/overlap evidence, explicitly not pointwise acceptance of callback handoffs. */
public class WtgwgoaNativePartialConformanceTest {
    private record Observed(double time,double[] state,double[] vars,String mode){}
    @Test void nativeProfilesRetainAllInputsAndCheckFilteredVoltageAndHybridPhaseTiming(){
        for(Profile p:WtgwgoaNativeReference.profiles())replay(p,true);
    }
    @Test void publishedRestartAndObservedNativeContinuationAreNotSilentlyAliased(){
        int checked=0;
        for(Profile p:WtgwgoaNativeReference.profiles())if(p.name().startsWith("repeat-")){
            var actual=replay(p,false);assertTrue(actual.size()>p.transitions().size(),p.name()+" must distinguish the published second-fault restart");checked++;
        }
        assertEquals(3,checked);
    }
    @Test void nativeDisabledAndAboveReferenceBranchesDoNotInventAnActiveProfile(){
        for(Profile p:WtgwgoaNativeReference.profiles())if(p.name().startsWith("disabled")||p.name().startsWith("above")){
            assertEquals(List.of("BYPASS"),p.transitions().stream().map(Transition::mode).toList());replay(p,true);
        }
    }
    @Test void frozenBodiesAndIndependentBundleRejectMutation(){
        StringBuilder bundle=new StringBuilder();
        for(Profile p:WtgwgoaNativeReference.profiles()){
            p.verify();bundle.append(p.rawSha256()).append('|').append(p.inputsSha256()).append('|').append(p.statesSha256()).append('\n');
            assertThrows(IllegalArgumentException.class,()->p.verify("9"+p.inputs().substring(1),p.states()));
            assertThrows(IllegalArgumentException.class,()->p.verify(p.inputs(),"9"+p.states().substring(1)));
            assertThrows(UnsupportedOperationException.class,()->p.postRows().add(99));
        }
        assertEquals(10,WtgwgoaNativeReference.profiles().size());
        assertEquals("b64d4bf29365f705fa92e9bac53a5b42b7586b0fd20d4b08a9f1a4e52770c235",WtgwgoaNativeReference.digest(bundle.toString()));
    }
    private static List<Transition> replay(Profile p,boolean nativeConvention){
        p.verify();var inputs=p.inputs().lines().map(WtgwgoaNativePartialConformanceTest::numbers).toList();
        assertEquals(p.samples(),inputs.size());
        var model=nativeConvention?WtgwgoaModel.forNativePsse367(p.parameters()):new WtgwgoaModel(p.parameters());
        model.initialize(p.initialVoltage(),p.initialReference());
        var observed=new ArrayList<Observed>();var transitions=new ArrayList<Transition>();String previousMode="";
        for(int row=0;row<inputs.size();row++){
            double[] input=inputs.get(row);
            if(row==0||p.isPost(row))model.step(0,input[0],input[1]);
            else {double[] old=inputs.get(row-1);model.step(p.step(),old[0],old[1],0);model.step(p.step(),input[0],input[1],1);}
            double time=p.tick(row)*p.step();String mode=model.getMode().name();
            if(!mode.equals(previousMode)){transitions.add(new Transition(mode,time));previousMode=mode;}
            observed.add(new Observed(time,model.getNativeStates(),model.getNativeVars(),mode));
        }
        if(!nativeConvention)return transitions;
        assertEquals(p.transitions().stream().map(Transition::mode).toList(),transitions.stream().map(Transition::mode).toList(),p.name());
        double eventBound=Math.max(3.1*p.step(),1e-4);
        for(int i=0;i<transitions.size();i++)assertEquals(p.transitions().get(i).rawTime(),transitions.get(i).rawTime(),eventBound,p.name()+" hybrid transition "+i);
        double[] all=new double[6],overlap=new double[6];int windows=0,overlapRows=0,checkpointCount=0;
        for(String line:p.states().lines().toList()){
            String[] columns=line.split(",");int row=Integer.parseInt(columns[0]);Observed actual=observed.get(row);
            boolean callbackWindow=p.transitions().stream().anyMatch(t->Math.abs(t.rawTime()-actual.time())<=eventBound)
                    ||transitions.stream().anyMatch(t->Math.abs(t.rawTime()-actual.time())<=eventBound);
            boolean sameMode=actual.mode().equals(columns[8]);
            if(callbackWindow)windows++;
            for(int channel=0;channel<6;channel++){
                double value=channel<3?actual.state()[channel]:actual.vars()[channel-3];
                double error=Math.abs(value-Double.parseDouble(columns[channel+2]));all[channel]=Math.max(all[channel],error);
                // Filter STATE is continuous and checked at every checkpoint,
                // including callback windows. Ramp/reset and VAR discontinuities
                // retain raw maxima below, but are not blessed by broad bounds.
                if(channel==0)assertTrue(error<=(p.step()==.0005?4.4e-4:2.3e-4),p.name()+" voltage filter: "+error);
                if(sameMode&&!callbackWindow)overlap[channel]=Math.max(overlap[channel],error);
            }
            if(sameMode&&!callbackWindow)overlapRows++;
            checkpointCount++;
        }
        assertEquals(p.checkpoints(),checkpointCount);assertTrue(overlapRows>100,p.name()+" must retain sustained mode-overlap evidence");
        assertEquals(inputs.size()-1,Integer.parseInt(p.states().strip().lines().toList().getLast().split(",")[0]));
        // A native ramp performs float STORE/STATE additions. Bound their
        // accumulated rounding by one maximum-range float ULP per nominal
        // step, separately from the mode/startup sampling error. This is a
        // precision budget, not a fitted parameter or callback-amplitude bound.
        double span=Math.max(Math.abs(p.parameters().holdPower()-p.parameters().faultPower()),
                Math.abs(p.initialReference()-p.parameters().holdPower()));
        double roundoff=p.tick(p.samples()-1)*Math.ulp((float)span);
        double h=p.step();double[] bounds={h==.0005?4.4e-4:2.3e-4,1.2*p.parameters().firstRampRate()*h+roundoff,
                1.5*p.parameters().secondRampRate()*h+roundoff,5e-5,Math.max(3*h,1e-4),1.5*p.parameters().firstRampRate()*h+roundoff};
        for(int channel=1;channel<6;channel++)assertTrue(overlap[channel]<=bounds[channel],p.name()+" sustained overlap channel "+channel+": "+overlap[channel]);
        System.out.println("WGO hybrid diagnostic "+p.name()+" raw-max="+Arrays.toString(all)+" sustained-overlap="+Arrays.toString(overlap)
                +" callback-window checkpoints="+windows+"; callback/handoff pointwise parity remains unaccepted");
        return transitions;
    }
    private static double[] numbers(String line){return Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();}
}
