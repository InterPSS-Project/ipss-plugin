package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Counter history at a tap command that crosses into the voltage deadband. */
public class CompositeTapMeasurementCoordinateTest {
    @ParameterizedTest @CsvSource({"false,down","true,down","false,reversal","true,reversal"})
    void acceptedCommandMeasurementControlsFollowingCounter(boolean staggered,String profile) throws Exception {
        var a=CompositeTapControlSmibTest.setup(profile,staggered);
        var m=CompositeTapControlSmibTest.model(a);
        String[] rows=new String(CompositeTapControlSmibTest.entry(profile+"/a/native.csv"),StandardCharsets.UTF_8).strip().split("\\R");
        var keys=Arrays.asList(rows[0].split(","));int current=0,checked=0;double peak=0,previousCommand=Double.NaN;
        int action=profile.equals("down")?497:659;
        for(int j=1;j<rows.length;j++) {
            String[] v=rows[j].split(",");double time=Double.parseDouble(v[0]);if(time<0)continue;
            int step=(int)Math.round(time/.0005);
            while(current<step) {
                if(current==100)m.setTapVoltageBand(profile.equals("down")?.915:1.115,profile.equals("down")?.945:1.145);
                if(current==200 && profile.equals("reversal"))m.setTapVoltageBand(.915,.945);
                assertTrue(a.solveDEqnStep(true));current++;
            }
            double command=Double.parseDouble(v[keys.indexOf("VAR12")]);
            if(step==action) {
                var low=new org.apache.commons.math3.complex.Complex(Double.parseDouble(v[keys.indexOf("VAR13")]),Double.parseDouble(v[keys.indexOf("VAR14")]));
                double physicalTap=previousCommand;
                var physicalLow=low.multiply(physicalTap/command);
                double angle=Math.toRadians(Double.parseDouble(v[keys.indexOf("A2")]));
                var parent=new org.apache.commons.math3.complex.Complex(Math.cos(angle),Math.sin(angle)).multiply(Double.parseDouble(v[keys.indexOf("V2")]));
                var primaryCurrent=parent.subtract(physicalLow.divide(physicalTap)).divide(new org.apache.commons.math3.complex.Complex(0,Double.parseDouble(v[keys.indexOf("VAR24")])));
                var compensation=new org.apache.commons.math3.complex.Complex(.004,.009).multiply(100/Double.parseDouble(v[keys.indexOf("VAR0")]));
                double prepared=low.subtract(compensation.multiply(primaryCurrent).divide(command)).abs();
                double physical=physicalLow.subtract(compensation.multiply(primaryCurrent).divide(physicalTap)).abs();
                assertEquals(Double.parseDouble(v[keys.indexOf("VAR39")]),prepared,5e-8,"Independent prepared control-voltage reconstruction");
                assertTrue(prepared>.915 && prepared<.945 && physical>.945,"The two coordinates predict opposite counter updates");
                assertEquals(.01,m.getData().value(14),1e-12,"Source control delay gives a 0.05 counter decrement per interval");
            }
            previousCommand=command;
            if(step>=action-1 && step<=action+22) {
                double expected=Double.parseDouble(v[keys.indexOf("VAR45")]);
                double actual=Math.abs(m.getNamedStates().get("LTC.Control"));
                System.out.printf("TAP_COUNTER %s %s %d %.12g %.12g%n",profile,staggered,step,expected,actual);
                peak=Math.max(peak,Math.abs(expected-actual));checked++;
            }
        }
        assertEquals(24,checked);assertTrue(peak<2e-6,"Accepted counter maximum error="+peak);
    }

    @ParameterizedTest @CsvSource({"false,up","true,up","false,no-compensation","true,no-compensation"})
    void measurementIsPureWithAndWithoutCompensation(boolean staggered,String profile) throws Exception {
        var a=CompositeTapControlSmibTest.setup(profile,staggered);var m=CompositeTapControlSmibTest.model(a);
        var port=m.getDStabBus().getDynLoadModelList().stream().filter(p->p.getId().endsWith("_Tap")).findFirst().orElseThrow();
        var measurement=port.getClass().getDeclaredMethod("controlledVoltage");measurement.setAccessible(true);
        m.setTapVoltageBand(1.115,1.145);
        for(int phase=0;phase<2;phase++) {
            var branch=m.getDistributionTransformer();double tap=branch.getToTurnRatio();double command=m.getNamedStates().get("LTC.Tap");
            assertEquals(phase==0,command==tap,"Exercise identity and accepted-but-unapplied command");
            var low=m.getLowVoltBus().getVoltage();var high=m.getDStabBus().getVoltage();
            var primary=high.subtract(low.divide(tap)).divide(branch.getZ());
            var compensation=new org.apache.commons.math3.complex.Complex(m.getData().value(16),m.getData().value(17)).multiply(a.getNetwork().getBaseMva()/m.getDistEquivalent().getMva());
            double expected=low.multiply(command/tap).subtract(compensation.multiply(primary).divide(command)).abs();
            var states=m.getNamedStates();var ff=branch.yff();var ft=branch.yft();var tf=branch.ytf();var tt=branch.ytt();
            for(int n=0;n<10;n++) assertEquals(expected,(double)measurement.invoke(port),2e-15);
            assertEquals(states,m.getNamedStates());assertEquals(low,m.getLowVoltBus().getVoltage());assertEquals(high,m.getDStabBus().getVoltage());
            assertEquals(tap,branch.getToTurnRatio());assertEquals(ff,branch.yff());assertEquals(ft,branch.yft());assertEquals(tf,branch.ytf());assertEquals(tt,branch.ytt());
            if(phase==0) {
                for(int n=0;n<50 && m.getNamedStates().get("LTC.Tap")==branch.getToTurnRatio();n++)assertTrue(a.solveDEqnStep(true));
            }
        }
    }
}
