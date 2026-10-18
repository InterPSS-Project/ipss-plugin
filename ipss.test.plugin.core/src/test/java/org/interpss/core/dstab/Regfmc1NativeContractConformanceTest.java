package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.Regfmc1NativeContractReference;
import org.interpss.dstab.renewable.Regfmc1NetworkBoundary;
import org.junit.jupiter.api.Test;

/** Boundary replay of recorded native states, not dynamic-kernel trajectory acceptance. */
public class Regfmc1NativeContractConformanceTest {
    @Test void nativeInputFixtureMatchesEveryFrozenReferenceAndTypedParameter() throws Exception {
        String input=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regfmc1u_native.dyr")).replace("\r\n","\n");
        for(var profile:Regfmc1NativeContractReference.profiles())assertEquals(profile.inputSha256(),
                org.interpss.core.dstab.reference.WtgibffraNativeReference.digest(input.replace("\n","\r\n")));
        String[] fields=input.lines().findFirst().orElseThrow().split("\\s+");assertEquals(48,fields.length);
        assertEquals("'REGFMC1U'",fields[3]);int[] allocation={101,1,3,34,11,27};
        for(int i=0;i<6;i++)assertEquals(allocation[i],Integer.parseInt(fields[4+i]));
        var d=Regfmc1NativeContractReference.parameters();
        for(int i=0;i<3;i++)assertEquals(d.toNativeIcons()[i],Integer.parseInt(fields[10+i]));
        for(int i=0;i<34;i++)assertEquals(d.constants()[i],Double.parseDouble(fields[13+i]));
    }
    private static List<Map<String,Double>> rows(String csv) {
        var lines=csv.lines().toList();String[] names=lines.getFirst().split(",");List<Map<String,Double>> rows=new ArrayList<>();
        for(String line:lines.subList(1,lines.size())){String[] values=line.split(",");assertEquals(names.length,values.length);
            Map<String,Double> row=new HashMap<>();for(int i=0;i<names.length;i++){double v=Double.parseDouble(values[i]);assertTrue(Double.isFinite(v));row.put(names[i],v);}rows.add(row);}
        return rows;
    }
    private static Complex polar(double magnitude,double angle){return new Complex(Math.cos(angle),Math.sin(angle)).multiply(magnitude);}
    private static Complex terminal(Map<String,Double> r){return polar(r.get("V1"),Math.toRadians(r.get("A1_DEG")));}
    private static Regfmc1NetworkBoundary.Hybrid boundary(Map<String,Double> r) {
        Complex gfl=new Complex(r.get("VAR14"),-r.get("VAR15")).multiply(polar(1,Math.toRadians(r.get("A1_DEG"))));
        return Regfmc1NetworkBoundary.hybrid(Regfmc1NativeContractReference.parameters(),polar(r.get("STATE4"),r.get("STATE10")),terminal(r),gfl);
    }
    @Test void rawReferencesRetainAllStatesChannelsAndEventSides() {
        for(var profile:Regfmc1NativeContractReference.profiles()) {
            var rows=rows(profile.body());assertEquals(profile.samples(),rows.size());assertEquals(49,rows.getFirst().size());
            assertEquals(2,rows.stream().filter(r->r.get("time_s")<0).count());int duplicates=0;
            for(int i=1;i<rows.size();i++){assertTrue(rows.get(i).get("time_s")>=rows.get(i-1).get("time_s"));if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s")))duplicates++;}
            assertEquals(2,duplicates);assertEquals(.3,rows.getLast().get("time_s"),1e-5);
            if(profile.name().startsWith("flat"))for(var row:rows)assertEquals(rows.getFirst().get("V1"),row.get("V1"),1e-6);
            else assertTrue(rows.stream().mapToDouble(r->r.get("V1")).min().orElseThrow()<rows.getFirst().get("V1")-.02);
        }
    }
    @Test void initializationHasZeroGfmAndGflCarriesTheMachineBasePower() {
        for(var profile:Regfmc1NativeContractReference.profiles()) {
            var r=rows(profile.body()).stream().filter(v->v.get("time_s")==0).findFirst().orElseThrow();
            assertEquals(r.get("V1"),r.get("STATE4"),0);assertEquals(r.get("V1"),r.get("STATE1"),0);
            assertEquals(Math.toRadians(r.get("A1_DEG")),r.get("STATE10"),3e-8);
            for(int state:new int[]{2,3,7,8,9})assertEquals(0,r.get("STATE"+state));
            assertEquals(1,r.get("STATE5"));assertEquals(1,r.get("STATE6"));
            assertEquals(0,r.get("VAR24"));assertEquals(0,r.get("VAR5"));assertEquals(0,r.get("VAR6"));
            assertEquals(2*r.get("P_PU"),r.get("VAR7"),2e-12);assertEquals(2*r.get("Q_PU"),r.get("VAR8"),2e-12);
        }
    }
    @Test void gflPriorityDeadbandAndCommandsReplayNativeAlgebraicOutputs() {
        for(var profile:Regfmc1NativeContractReference.profiles()) {
            var samples=rows(profile.body());var initial=samples.stream().filter(r->r.get("time_s")==0).findFirst().orElseThrow();
            double p=2*initial.get("P_PU"),q=2*initial.get("Q_PU");double[] maximum=new double[5];
            for(var r:samples) {
                var g=Regfmc1NetworkBoundary.gridFollowing(Regfmc1NativeContractReference.parameters(),p,q,r.get("STATE0"),r.get("VAR9"),Math.toRadians(r.get("A1_DEG")));
                double[] computed={g.voltageInjection(),g.ipmax(),g.iqmax(),g.ip(),g.iq()};String[] channels={"VAR0","VAR10","VAR12","VAR14","VAR15"};
                for(int i=0;i<5;i++)maximum[i]=Math.max(maximum[i],Math.abs(computed[i]-r.get(channels[i])));
                assertEquals(-r.get("VAR10"),r.get("VAR11"),0);assertEquals(-r.get("VAR12"),r.get("VAR13"),0);
            }
            System.out.println("REGFMC1U native GFL "+profile.name()+" errors="+Arrays.toString(maximum));
            // Near the circle tangency, float-rounded native references amplify sqrt sensitivity.
            double[] bounds={1.5e-7,3.1e-6,5.6e-8,3.1e-6,1.1e-7};
            for(int i=0;i<5;i++)assertTrue(maximum[i]<bounds[i],"GFL channel "+i+" error="+maximum[i]);
        }
    }
    @Test void summedCurrentLimitReplaysNativeSourceVoltageAngleAndBranchCurrents() {
        for(var profile:Regfmc1NativeContractReference.profiles()) {
            double[] maximum=new double[8];int active=0;double rawLimitedSeparation=0;
            for(var r:rows(profile.body())) {
                var b=boundary(r);if(b.currentRatio()>1)active++;
                rawLimitedSeparation=Math.max(rawLimitedSeparation,b.rawGfmCurrent().subtract(b.gfmCurrent()).abs());
                double[] computed={b.currentRatio(),b.internalVoltage().abs(),Math.toDegrees(b.internalVoltage().getArgument()),
                        b.gflCurrent().abs(),Math.toDegrees(b.gflCurrent().getArgument()),b.totalCurrent().abs(),
                        b.rawGfmCurrent().abs(),terminal(r).multiply(b.rawGfmCurrent().conjugate()).getReal()};
                String[] channels={"VAR16","VAR17","VAR18","VAR19","VAR20","VAR21","VAR24","VAR5"};
                for(int i=0;i<8;i++)maximum[i]=Math.max(maximum[i],Math.abs(computed[i]-r.get(channels[i])));
                assertTrue(b.totalCurrent().abs()<=1.2+1e-13);
                assertEquals(0,b.internalVoltage().subtract(terminal(r)).divide(Regfmc1NativeContractReference.parameters().sourceImpedance()).subtract(b.gfmCurrent()).abs(),1e-14);
            }
            System.out.println("REGFMC1U native hybrid "+profile.name()+" errors="+Arrays.toString(maximum)+" active="+active);
            double[] bounds={1.4e-6,1.6e-7,5.3e-6,1.6e-7,1.7e-5,6.9e-7,9.5e-7,1.5e-7};
            for(int i=0;i<8;i++)assertTrue(maximum[i]<bounds[i],"Hybrid channel "+i+" error="+maximum[i]);
            if(profile.name().startsWith("limit")){assertEquals(profile.step()==.0005?101:201,active);assertTrue(rawLimitedSeparation>3);}
            else {assertEquals(0,active);assertEquals(0,rawLimitedSeparation);}
        }
    }
    @Test void nativeRuntimeDoesNotInferTheSourceListAlias() {
        assertTrue(org.interpss.fadapter.psse.dyr.DynamicModelCatalog.find("REGFMC1").isEmpty());
        assertEquals("org.interpss.dstab.renewable.Regfmc1Model",org.interpss.fadapter.psse.dyr.DynamicModelCatalog.find("REGFMC1U").orElseThrow().runtimeClassName());
    }
}
