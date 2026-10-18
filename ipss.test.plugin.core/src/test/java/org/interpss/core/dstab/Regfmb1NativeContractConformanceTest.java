package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.Regfmb1NativeContractReference;
import org.interpss.dstab.renewable.Regfmb1NetworkBoundary;
import org.junit.jupiter.api.Test;

/** Native schema and boundary evidence only; REGFMB1U dynamics are not loadable. */
public class Regfmb1NativeContractConformanceTest {
    private static List<Map<String,Double>> rows(String csv) {
        var lines=csv.lines().toList();String[] names=lines.getFirst().split(",");List<Map<String,Double>> rows=new ArrayList<>();
        for(String line:lines.subList(1,lines.size())){String[] values=line.split(",");assertEquals(names.length,values.length);
            Map<String,Double> row=new HashMap<>();for(int i=0;i<names.length;i++){double v=Double.parseDouble(values[i]);assertTrue(Double.isFinite(v));row.put(names[i],v);}rows.add(row);}
        return rows;
    }
    @Test void frozenReferencesRetainAllStatesChannelsAndEventSides() {
        for(var profile:Regfmb1NativeContractReference.profiles()) {
            var rows=rows(profile.body());assertEquals(profile.samples(),rows.size());assertEquals(42,rows.getFirst().size());
            assertEquals(2,rows.stream().filter(r->r.get("time_s")<0).count());int duplicates=0;
            for(int i=1;i<rows.size();i++){assertTrue(rows.get(i).get("time_s")>=rows.get(i-1).get("time_s"));if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s")))duplicates++;}
            assertEquals(2,duplicates);assertEquals(.3,rows.getLast().get("time_s"),1e-5);
            if(profile.name().startsWith("fault"))assertTrue(rows.stream().mapToDouble(r->r.get("V2")).min().orElseThrow()<rows.getFirst().get("V2")-.02);
            else for(var row:rows)assertEquals(rows.getFirst().get("V1"),row.get("V1"),1e-6,"Native flat reference must remain stationary");
        }
    }
    @Test void nativeInitialPhasorUsesRawSourceAndMachineMvaBase() {
        for(var profile:Regfmb1NativeContractReference.profiles()) {
            var r=rows(profile.body()).stream().filter(v->v.get("time_s")==0).findFirst().orElseThrow();
            double angle=Math.toRadians(r.get("A1_DEG"));Complex terminal=new Complex(Math.cos(angle),Math.sin(angle)).multiply(r.get("V1"));
            Complex e=Regfmb1NetworkBoundary.initialInternalVoltage(Regfmb1NativeContractReference.parameters(),terminal,new Complex(r.get("P_PU"),r.get("Q_PU")).multiply(2));
            assertEquals(r.get("VAR10"),e.abs(),5e-7);
            assertEquals(r.get("ANGLE_DEG"),Math.toDegrees(e.getArgument()),3e-5);
            assertEquals(angle,r.get("STATE6"),1e-7);assertEquals(r.get("STATE3")+r.get("STATE6"),e.getArgument(),5e-7);
        }
    }
    @Test void nativeMovingCurrentAndVoltageBoundsUseFilteredMeasurements() {
        for(var profile:Regfmb1NativeContractReference.profiles()) {
            double[] maximum=new double[4];
            for(var r:rows(profile.body())) {
                var limits=Regfmb1NetworkBoundary.limits(Regfmb1NativeContractReference.parameters(),r.get("STATE7"),r.get("STATE10"),r.get("STATE9"));
                double[] calculated={limits.idmax(),limits.iqmax(),limits.emax(),limits.emin()},nativeValues={r.get("VAR6"),r.get("VAR7"),r.get("VAR4"),r.get("VAR5")};
                for(int i=0;i<4;i++)maximum[i]=Math.max(maximum[i],Math.abs(calculated[i]-nativeValues[i]));
            }
            System.out.println("REGFMB1U native bounds "+profile.name()+" errors="+Arrays.toString(maximum));
            double[] bounds={1.5e-7,5.6e-8,1.7e-7,1.25e-7};
            for(int i=0;i<4;i++)assertTrue(maximum[i]<bounds[i],"Boundary channel "+i+" discrepancy "+maximum[i]);
        }
    }
    @Test void foundationDoesNotPretendThatADynamicRuntimeOrAliasExists() {
        assertTrue(org.interpss.fadapter.psse.dyr.DynamicModelCatalog.find("REGFMB1").isEmpty());
        assertTrue(org.interpss.fadapter.psse.dyr.DynamicModelCatalog.find("REGFMB1U").isEmpty());
    }
}
