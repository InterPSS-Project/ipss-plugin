package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.interpss.core.dstab.reference.ReeceLocalPiNativeReference;
import org.interpss.core.dstab.reference.WtgibffraNativeReference;
import org.interpss.dstab.renewable.Reece1LocalPi;
import org.interpss.dstab.renewable.Reece1LocalPi.Input;
import static org.interpss.dstab.renewable.Reece1Data.Parameter.*;
import org.junit.jupiter.api.Test;

/** Prepared-input local PI checks only; not full REECE import or SMIB acceptance. */
public class ReeceLocalPiNativePartialConformanceTest {
    @Test void nativeLocalReactiveAndActivePiProfilesRetainBothReferenceEventSides() {
        double[] coarse=null;
        for(var p:ReeceLocalPiNativeReference.profiles()) {
            var data=p.parameters();var m=new Reece1LocalPi(data);
            var rows=p.inputs().lines().map(ReeceLocalPiNativePartialConformanceTest::numbers).toList();assertEquals(p.samples(),rows.size());
            m.initialize(p.initialQ()/p.initialV(),p.initialP(),input(p,rows.getFirst()));
            var actual=new ArrayList<double[]>();double maxQRate=0,maxPRate=0;
            for(int i=0;i<rows.size();i++) {
                Input now=input(p,rows.get(i));
                if(i==0||i==p.postRow())m.step(0,now);
                else {m.step(p.step(),input(p,rows.get(i-1)),0);m.step(p.step(),now,1);}
                double[] state=m.getIntegralContributions();actual.add(new double[]{state[0],state[1],m.reactiveOutput()});
                assertTrue(m.reactiveOutput()<now.iqMaximum()-.1&&m.reactiveOutput()>now.iqMinimum()+.1,
                        "Recorded current limits must be inactive, not fitted PI inputs");
                maxQRate=Math.max(maxQRate,Math.abs(data.value(KQI)*(now.reactiveReference()-now.measuredReactivePower())));
                maxPRate=Math.max(maxPRate,Math.abs(data.value(KPI)*(now.activeReference()-now.measuredPower())));
            }
            double[] maximum=new double[3],at=new double[3];int count=0,posts=0;
            for(String line:p.states().lines().toList()) {
                double[] expected=numbers(line);int row=(int)expected[0];if(row==p.postRow())posts++;
                for(int c=0;c<3;c++) {
                    double error=Math.abs(actual.get(row)[c]-expected[c+2]);
                    if(error>maximum[c]){maximum[c]=error;at[c]=expected[1];}
                }
                count++;
            }
            assertEquals(1,posts);assertEquals(p.checkpoints(),count);assertTrue(count>200);
            // Native startup/event STORE differs from modified Euler. Keep
            // the two-step-size comparison, do not replace the solver to fit.
            double precision=rows.size()*Math.ulp((float).3);
            double[] bounds={2*p.step()*maxQRate+precision,2*p.step()*maxPRate+precision,2*p.step()*maxQRate+precision};
            double[] measuredEnvelope=p.step()==.0005?new double[]{1.6e-5,4.8e-5,1.6e-5}:new double[]{8e-6,2.85e-5,8e-6};
            for(int c=0;c<3;c++)bounds[c]=Math.min(bounds[c],measuredEnvelope[c]);
            for(int c=0;c<3;c++)assertTrue(maximum[c]<=bounds[c],p.name()+" PI channel "+c+": "+maximum[c]);
            if(coarse==null)coarse=maximum;
            else for(int c=0;c<3;c++)assertTrue(maximum[c]<.8*coarse[c],"PI channel "+c+" must improve under step halving");
            System.out.println("REECE local-PI partial "+p.name()+" max="+Arrays.toString(maximum)+" times="+Arrays.toString(at));
        }
    }
    @Test void frozenBodiesAndPhysicalInitializationAreNotNativeStateFitting() {
        var bundle=new StringBuilder();
        for(var p:ReeceLocalPiNativeReference.profiles()) {
            p.inputs();p.states();bundle.append(p.rawSha256()).append('|').append(p.inputsSha256()).append('|').append(p.statesSha256()).append('\n');
            double[] constants=p.constants();constants[77]=99;assertEquals(2,p.parameters().value(KPP));
            double[] first=numbers(p.states().lines().findFirst().orElseThrow());
            assertEquals(p.initialQ()/p.initialV(),first[2],2e-9);assertEquals(p.initialP(),first[3],2e-9);
        }
        assertEquals(2,ReeceLocalPiNativeReference.profiles().size());
        assertEquals("1f4500e9593ca544a24e7b7f3e5a4dd6b65e9e6b1987b041cc8269b7fff6c917",WtgibffraNativeReference.digest(bundle.toString()));
    }
    private static Input input(ReeceLocalPiNativeReference.Profile p,double[] row) {
        var data=p.parameters();return new Input(row[2],row[0],p.initialQ()+row[3],row[1],row[4],row[5],data.value(PMIN),data.value(PMAX),row[6]!=0);
    }
    private static double[] numbers(String line){return Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();}
}
