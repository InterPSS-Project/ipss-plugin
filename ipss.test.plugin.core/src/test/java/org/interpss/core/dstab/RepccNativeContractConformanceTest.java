package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.RepccNativeReference;
import org.interpss.dstab.renewable.RepccInitialization;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Test;

/** Native contract and algebraic initialization only; no time-domain runtime claim. */
public class RepccNativeContractConformanceTest {
    private static List<Map<String,Double>> rows(String csv) {
        var lines=csv.lines().toList();String[] header=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())) {
            String[] fields=line.split(",");assertEquals(header.length,fields.length);var row=new HashMap<String,Double>();
            for(int i=0;i<header.length;i++){double value=Double.parseDouble(fields[i]);assertTrue(Double.isFinite(value));row.put(header[i],value);}
            result.add(row);
        }return result;
    }
    @Test void exactStandardSchemaAndAllConstantsAreRetained() throws Exception {
        assertEquals(8,RepccNativeReference.profiles().size());
        for(var p:RepccNativeReference.profiles()) {
            var r=PsseDyrRecordReader.read(new StringReader(p.input()),p.name()).stream().filter(v->v.sourceModelName().equals("REPCC")).findFirst().orElseThrow();
            assertEquals(3,r.parameterOffset());assertEquals(92,r.parameterCount());
            var d=p.parameters();assertEquals(r.intParameter(0),d.remoteBus());
            assertEquals(r.intParameter(1),d.measurementBranch().from());assertEquals("1",d.measurementBranch().circuit());
            assertEquals(10,d.shunts().size());for(var s:d.shunts()){assertEquals(0,s.bus());assertEquals("0",s.id());}
            for(int i=0;i<57;i++)assertEquals(r.doubleParameter(35+i),d.constants()[i]);
        }
    }
    @Test void allHistoryEventSidesAndNativeChannelsArePreserved() {
        for(var p:RepccNativeReference.profiles()) {
            var samples=rows(p.body());assertEquals(p.samples(),samples.size());assertEquals(60,samples.getFirst().size());
            for(int i=0;i<13;i++)assertTrue(samples.getFirst().containsKey("PLANT_STATE"+i));
            for(int i=0;i<36;i++)assertTrue(samples.getFirst().containsKey("PLANT_VAR"+i));
            assertEquals(2,samples.stream().filter(r->r.get("time_s")<0).count());
            int duplicates=0;for(int i=1;i<samples.size();i++) {
                double previous=samples.get(i-1).get("time_s"),now=samples.get(i).get("time_s");assertTrue(now>=previous);if(now==previous)duplicates++;
            }
            assertEquals(2,duplicates);assertEquals(.3,samples.getLast().get("time_s"),1e-5);
            double initial=samples.getFirst().get("V1");
            assertTrue(samples.stream().allMatch(r->Math.abs(r.get("V1")-initial)<1e-6));
        }
    }
    private static Complex polar(Map<String,Double> row,int bus) {
        double angle=Math.toRadians(row.get("A"+bus+"_DEG"));return new Complex(Math.cos(angle),Math.sin(angle)).multiply(row.get("V"+bus));
    }
    @Test void allThirteenEquilibriumStatesAndReferencesMatchNative() {
        for(var p:RepccNativeReference.profiles()) {
            var row=rows(p.body()).stream().filter(r->r.get("time_s")==0).findFirst().orElseThrow();var d=p.parameters();
            boolean fallback=p.name().startsWith("fallback");
            // Unresolved line-flow plot VARs remain zero; physical fallback
            // inputs are generator power, not those inactive plot variables.
            double active=fallback?2*row.get("P_PU"):row.get("PLANT_VAR4")/50;
            double reactive=fallback?2*row.get("Q_PU"):row.get("PLANT_VAR5")/50;
            Complex at=polar(row,fallback?1:2);
            Complex current=new Complex(active,reactive).divide(at).conjugate();
            var init=RepccInitialization.initialize(d,new RepccInitialization.Measurements(polar(row,3),current,active,reactive,
                    2*row.get("P_PU"),2*row.get("Q_PU")));
            double[] errors=new double[13];double[] expected=init.states();
            for(int i=0;i<13;i++)errors[i]=Math.abs(expected[i]-row.get("PLANT_STATE"+i));
            System.out.println("REPCC initialization "+p.name()+" state errors="+Arrays.toString(errors));
            // Declared double constants and raw float export retain their own precision.
            double[] bounds={5.5e-8,3e-9,1e-12,1e-12,6e-9,6e-9,2e-12,1e-12,4e-10,4e-13,6e-9,3e-9,2e-8};
            for(int i=0;i<13;i++)assertTrue(errors[i]<bounds[i],p.name()+" state "+i+" error="+errors[i]);
            assertEquals(init.voltageReference(),row.get("PLANT_VAR0"),5.5e-8);
            assertEquals(init.reactiveReference(),row.get("PLANT_VAR1"),3e-9);
            assertEquals(init.plantReference(),row.get("PLANT_VAR3"),6e-9);
            assertEquals(init.powerFactorReference(),row.get("PLANT_VAR9"),6e-8);
            assertEquals(13,init.namedStates().size());
        }
    }
    @Test void typedFoundationDoesNotRaiseRuntimeCoverage() {
        assertTrue(DynamicModelCatalog.find("REPCC").isEmpty());assertTrue(DynamicModelCatalog.find("REPCCU").isEmpty());
    }
}
