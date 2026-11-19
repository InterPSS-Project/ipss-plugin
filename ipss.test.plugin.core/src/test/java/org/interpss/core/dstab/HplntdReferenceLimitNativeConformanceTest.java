package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.interpss.core.dstab.reference.HplntdReferenceLimitNativeReference;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import static com.interpss.dstab.renewable.HplntdData.Parameter.*;

/** Published-limit checks and explicit native discrepancies, not voltage parity. */
public class HplntdReferenceLimitNativeConformanceTest {
    private static List<Map<String,Double>> rows(Profile p){
        var lines=p.body().lines().toList();String[] header=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){String[] values=line.split(",");var row=new LinkedHashMap<String,Double>();for(int i=0;i<header.length;i++)row.put(header[i],Double.parseDouble(values[i]));result.add(row);}
        assertEquals(p.samples(),result.size());return result;
    }
    private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static double error(HplntdData d,double value){double dead=value>d.value(DBD2)?value-d.value(DBD2):value<d.value(DBD1)?value-d.value(DBD1):0;return clip(dead,d.value(EMIN),d.value(EMAX));}
    private static HplntdKernel.Input input(Map<String,Double> r){
        double angle=Math.toRadians(r.get("A2_DEG"));var voltage=new Complex(r.get("V2")*Math.cos(angle),r.get("V2")*Math.sin(angle));
        var power=new Complex(r.get("PLANT_VAR4")/r.get("PLANT_VAR6"),r.get("PLANT_VAR5")/r.get("PLANT_VAR6"));
        return new HplntdKernel.Input(voltage,power.divide(voltage).conjugate(),power.getReal(),power.getImaginary(),r.get("FREQ2"),r.get("V2"));
    }
    @Test void nativeVoltageClampDiscrepancyIsExplicitAcrossAllSamples(){
        int profiles=0;
        for(var p:HplntdReferenceLimitNativeReference.profiles())if(p.plant().referenceFlag()==1){
            var d=p.plant();boolean outside=false,disagrees=false;double maximum=0,difference=0;
            for(var r:rows(p)){
                double measured=d.compensationFlag()==1?r.get("PLANT_STATE0"):r.get("PLANT_STATE7")+r.get("PLANT_STATE8");
                double reference=r.get("PLANT_VAR0"),observed=r.get("PLANT_VAR7");
                double raw=error(d,reference+r.get("PLANT_VAR10")-measured);
                double limited=error(d,clip(reference,d.value(VREFMIN),d.value(VREFMAX))+r.get("PLANT_VAR10")-measured);
                maximum=Math.max(maximum,Math.abs(observed-raw));difference=Math.max(difference,Math.abs(observed-limited));
                outside|=reference<d.value(VREFMIN)||reference>d.value(VREFMAX);disagrees|=Math.abs(observed-limited)>.005;
            }
            System.out.println("Hybrid native voltage-limit discriminator "+p.name()+" rawError="+maximum+" publishedDifference="+difference);
            assertTrue(outside&&disagrees,p.name()+" must distinguish limiting from bypass");
            assertTrue(maximum<1e-10,p.name()+" observed raw voltage error="+maximum);profiles++;
        }
        assertEquals(12,profiles);
    }
    @Test void nativeReactiveAndPowerFactorLimitsRemainActive(){
        int profiles=0;
        for(var p:HplntdReferenceLimitNativeReference.acceptedProfiles()){
            var d=p.plant();boolean activated=false;double maximum=0;
            for(var r:rows(p)){
                double demand;
                if(d.referenceFlag()==0){
                    assertTrue(r.get("PLANT_STATE11")<=d.value(QREFMAX)+1e-7&&r.get("PLANT_STATE11")>=d.value(QREFMIN)-1e-7);
                    activated|=r.get("PLANT_VAR1")>d.value(QREFMAX)&&Math.abs(r.get("PLANT_STATE11")-d.value(QREFMAX))<1e-7;
                    demand=r.get("PLANT_STATE11");
                }else{
                    double pf=r.get("PLANT_VAR9"),magnitude=clip(Math.abs(pf),pf>=0?d.value(PFMAX):-d.value(PFMIN),1);
                    demand=Math.copySign(Math.tan(Math.acos(magnitude))*Math.abs(r.get("PLANT_STATE4")),pf);
                    activated|=Math.abs(pf)<magnitude;
                }
                maximum=Math.max(maximum,Math.abs(error(d,demand-r.get("PLANT_STATE1"))-r.get("PLANT_VAR7")));
            }
            System.out.println("Hybrid native Q/PF algebraic discriminator "+p.name()+" error="+maximum);
            assertTrue(activated,p.name()+" limiter must activate");assertTrue(maximum<1e-7,p.name()+" algebraic error="+maximum);profiles++;
        }
        assertEquals(4,profiles);
    }
    @Test void publishedKernelKeepsVoltageClampAndStrictInitializationBounds(){
        int rejected=0,checked=0;
        for(var p:HplntdReferenceLimitNativeReference.profiles())if(p.plant().referenceFlag()==1){
            var d=p.plant();var samples=rows(p);var first=samples.getFirst();var in=input(first);var k=new HplntdKernel(d);
            var initial=new HplntdInitialization.Measurements(in.regulatedVoltage(),in.branchCurrent(),in.active(),in.reactive(),first.get("PLANT_VAR41"),first.get("PLANT_VAR42"));
            if(p.name().contains("outside")||p.name().contains("narrow")){
                assertTrue(assertThrows(IllegalArgumentException.class,()->k.initialize(initial)).getMessage().contains("voltage reference"));rejected++;continue;
            }
            k.initialize(initial);
            for(var r:samples){
                var refs=k.references();k.setReferences(new HplntdKernel.References(r.get("PLANT_VAR0"),refs.reactive(),refs.frequency(),refs.active(),refs.powerFactor(),0,0));
                double measured=d.compensationFlag()==1?k.states()[0]:k.states()[7]+k.states()[8];
                assertEquals(error(d,clip(r.get("PLANT_VAR0"),d.value(VREFMIN),d.value(VREFMAX))-measured),k.output(in).reactiveError(),1e-14);
            }checked++;
        }
        assertEquals(6,rejected);assertEquals(6,checked);
    }
    @Test void replayEveryPlantStateAndCommandForTheConformingQAndPfProfiles()throws Exception {
        HplntdKernelNativeConformanceTest.compareProfiles(HplntdReferenceLimitNativeReference.acceptedProfiles(),p->HplntdReferenceLimitNativeReference.replayBounds(p.name()));
    }
}
