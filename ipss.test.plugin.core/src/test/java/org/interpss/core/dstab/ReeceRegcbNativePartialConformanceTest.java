package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.interpss.core.dstab.reference.ReeceLocalPiNativeReference;
import org.interpss.core.dstab.reference.ReeceRegcbNativeBoundaryReference;
import org.interpss.dstab.renewable.Reece1Model;
import org.junit.jupiter.api.Test;

/** Native 36.7 discriminator; the printed freeze arrow differs from the executable. */
public class ReeceRegcbNativePartialConformanceTest {
    private static List<Map<String,Double>> rows(String csv) {
        var lines=csv.lines().toList();String[] names=lines.getFirst().split(",");
        List<Map<String,Double>> result=new ArrayList<>();
        for(String line:lines.subList(1,lines.size())) {
            String[] values=line.split(",");Map<String,Double> row=new HashMap<>();
            for(int i=0;i<names.length;i++)row.put(names[i],Double.parseDouble(values[i]));
            result.add(row);
        }
        return result;
    }
    @Test void nativeActivePiIntegratesWhilePowerOrderLagIsFrozen() {
        for(var profile:ReeceRegcbNativeBoundaryReference.profiles()) {
            var rows=rows(profile.body());int witnesses=0;double maxRateError=0,growth=0;
            for(int i=1;i<rows.size();i++) {
                var a=rows.get(i-1);var b=rows.get(i);
                if(b.get("time_s")<=a.get("time_s")||a.get("EC_STATE0")>=.65||b.get("EC_STATE0")>=.65)continue;
                double delta=b.get("EC_STATE7")-a.get("EC_STATE7");
                if(delta<=0)continue;
                witnesses++;
                growth+=delta;
                assertEquals(a.get("EC_STATE5"),b.get("EC_STATE5"),0,"Pord must remain frozen");
                double rate=10*(.24-.5*(a.get("EC_STATE1")+b.get("EC_STATE1")));
                assertTrue(rate>0,"Active PI advances in the power-error direction");
                maxRateError=Math.max(maxRateError,Math.abs(delta/profile.step()-rate));
            }
            assertTrue(witnesses>30);System.out.println("REECE native dip PI "+profile.name()+" witnesses="+witnesses+" rate residual="+maxRateError);
            // Exported measurements straddle network callbacks; the slope residual
            // is diagnostic, not same-row equation parity. State growth disproves freeze.
            assertTrue(growth>.05,"Native active PI must not freeze during this dip");
        }
    }
    @Test void terminalInputReplayRetainsActivePiDuringDipAtBothSteps() {
        double coarse=Double.NaN,coarseReactive=Double.NaN;
        for(var profile:ReeceRegcbNativeBoundaryReference.profiles()) {
            var rows=rows(profile.body()).stream().filter(r->r.get("time_s")>=0).toList();var initial=rows.getFirst();
            var model=new Reece1Model(ReeceLocalPiNativeReference.profiles().getFirst().parameters());
            model.initialize(initial.get("P_PU"),initial.get("Q_PU"),initial.get("V1"));
            double maximum=0,at=0,reactiveMaximum=0,reactiveAt=0;
            for(int i=1;i<rows.size();i++) {
                var a=rows.get(i-1);var b=rows.get(i);
                if(b.get("time_s").equals(a.get("time_s")))model.step(0,b.get("P_PU"),b.get("Q_PU"),b.get("V1"),1);
                else {
                    model.step(profile.step(),a.get("P_PU"),a.get("Q_PU"),a.get("V1"),1,0);
                    model.step(profile.step(),b.get("P_PU"),b.get("Q_PU"),b.get("V1"),1,1);
                }
                double error=Math.abs(model.getNamedStates().get("PIP")-b.get("EC_STATE7"));
                if(error>maximum){maximum=error;at=b.get("time_s");}
                double reactiveError=Math.abs(model.getNamedStates().get("PIQ")-b.get("EC_STATE2"));
                if(reactiveError>reactiveMaximum){reactiveMaximum=reactiveError;reactiveAt=b.get("time_s");}
            }
            System.out.println("REECE terminal replay "+profile.name()+" PIP max="+maximum+" at="+at);
            System.out.println("REECE terminal replay "+profile.name()+" PIQ max="+reactiveMaximum+" at="+reactiveAt);
            assertTrue(maximum<(profile.step()==.0005?5.2e-5:1.4e-5),"Native PIP mismatch "+maximum);
            assertTrue(reactiveMaximum<(profile.step()==.0005?9.4e-4:4.75e-4),"Native PIQ mismatch "+reactiveMaximum);
            if(Double.isNaN(coarse)){coarse=maximum;coarseReactive=reactiveMaximum;}
            else {
                assertTrue(maximum<.35*coarse,"Active PI replay error must decrease at half step");
                assertTrue(reactiveMaximum<.6*coarseReactive,"Reactive PI replay error must decrease at half step");
            }
        }
    }
    @Test void nativeBlockingProjectsFrozenReactivePiIntoCollapsedBounds() {
        for(var profile:ReeceRegcbNativeBoundaryReference.profiles()) {
            int blocked=0;boolean retainedBeforeBlock=false;
            for(var row:rows(profile.body())) {
                if(row.get("EC_STATE0")<.2)
                    for(String name:new String[]{"EC_VAR9","EC_VAR10","EC_VAR11","EC_VAR12"})
                        assertEquals(0,row.get(name),0,"Native bounds collapse at the threshold endpoint");
                if(row.get("EC_STATE0")<.7&&Math.abs(row.get("EC_STATE2"))>.04)retainedBeforeBlock=true;
                if(row.get("EC_VAR9")==0&&row.get("EC_VAR10")==0
                        &&row.get("EC_VAR11")==0&&row.get("EC_VAR12")==0) {
                    blocked++;assertTrue(row.get("EC_STATE0")<.7);
                    assertEquals(0,row.get("EC_STATE2"),0,"Native frozen PI state follows collapsed bounds");
                }
            }
            assertTrue(retainedBeforeBlock);assertTrue(blocked>10,profile.name());
        }
    }
}
