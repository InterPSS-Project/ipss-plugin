package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.Repcgfmc1NativeContractReference;
import com.interpss.dstab.renewable.Repcgfmc1Initialization;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Test;

/** Native initialization/algebraic evidence only, not a time-domain kernel replay. */
public class Repcgfmc1NativeContractConformanceTest {
    private static List<Map<String,Double>> rows(String csv){
        var lines=csv.lines().toList();String[] header=lines.getFirst().split(",");var rows=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){
            String[] values=line.split(",");assertEquals(header.length,values.length);Map<String,Double> row=new HashMap<>();
            for(int i=0;i<values.length;i++){double v=Double.parseDouble(values[i]);assertTrue(Double.isFinite(v));row.put(header[i],v);}rows.add(row);
        }return rows;
    }
    @Test void exactMixedTypeInputContractAndEveryConstantAreRetained() throws Exception {
        for(var p:Repcgfmc1NativeContractReference.profiles()){
            var r=PsseDyrRecordReader.read(new StringReader(p.input()),p.name()).stream().filter(v->v.sourceModelName().equals("REPCGFMC1U")).findFirst().orElseThrow();
            assertEquals("USRMDL",r.fields().get(1));assertEquals(4,r.parameterOffset());assertEquals(67,r.parameterCount());
            int[] allocation={107,0,7,54,15,20};for(int i=0;i<6;i++)assertEquals(allocation[i],r.intParameter(i));
            var d=p.parameters();assertEquals("1",d.circuit());assertEquals(r.intParameter(6),d.frequencyBus());
            assertEquals(r.intParameter(7),d.branchFrom());assertEquals(r.intParameter(8),d.branchTo());
            for(int i=0;i<54;i++)assertEquals(r.doubleParameter(13+i),d.constants()[i]);
        }
    }
    @Test void fullNativeChannelsHistoryAndEventSidesArePreserved(){
        assertEquals(8,Repcgfmc1NativeContractReference.profiles().size());
        for(var p:Repcgfmc1NativeContractReference.profiles()){
            var samples=rows(p.body());assertEquals(p.samples(),samples.size());assertEquals(84,samples.getFirst().size());
            for(int i=0;i<15;i++)assertTrue(samples.getFirst().containsKey("PLANT_STATE"+i));
            for(int i=0;i<20;i++)assertTrue(samples.getFirst().containsKey("PLANT_VAR"+i));
            assertEquals(2,samples.stream().filter(r->r.get("time_s")<0).count());int duplicates=0;
            for(int i=1;i<samples.size();i++){double previous=samples.get(i-1).get("time_s"),current=samples.get(i).get("time_s");assertTrue(current>=previous);if(current==previous)duplicates++;}
            assertEquals(2,duplicates);assertEquals(.3,samples.getLast().get("time_s"),1e-5);
            double initial=samples.getFirst().get("V1"),change=samples.stream().mapToDouble(r->Math.abs(r.get("V1")-initial)).max().orElseThrow();
            if(p.name().startsWith("branch-fault"))assertTrue(change>.01);else assertEquals(0,change,1e-6);
        }
    }
    @Test void initialTargetsLossesBiasesAndVoltageReferencesMatchNative(){
        for(var p:Repcgfmc1NativeContractReference.profiles()){
            var first=rows(p.body()).stream().filter(r->r.get("time_s")==0).findFirst().orElseThrow();
            var d=p.parameters();boolean found=!p.name().startsWith("fallback");
            var m=new Repcgfmc1Initialization.Measurements(100,50,first.get("V1"),first.get("V2"),
                    first.get("P_PU"),first.get("Q_PU"),first.get("PLANT_VAR12")/100,first.get("PLANT_VAR13")/100,found);
            var initial=Repcgfmc1Initialization.initialize(d,m);
            double[] actual={initial.pTarget(),initial.qTarget(),initial.resistance(),initial.reactance(),initial.activeLoss(),initial.reactiveLoss(),
                    initial.compensatedVoltage(),initial.voltageCorrection(),initial.remoteVoltageReference(),initial.activeIntegral(),initial.reactiveIntegral(),
                    initial.voltageIntegral(),initial.activeReference(),initial.reactiveReference()};
            // Initialization targets equal the measurement-filter states. With
            // Kivc=0 the later algebraic VAR2 refresh quantizes voltage error
            // before multiplying by Kp, so it is not the original Q target.
            String[] channels={"PLANT_STATE6","PLANT_STATE7","PLANT_VAR8","PLANT_VAR9","PLANT_VAR10","PLANT_VAR11","PLANT_VAR4","PLANT_VAR3",
                    "PLANT_VAR14","PLANT_STATE10","PLANT_STATE12","PLANT_STATE8","PLANT_STATE11","PLANT_STATE13"};
            double[] errors=new double[actual.length];
            double[] bounds={6.1e-9,3.8e-9,3e-8,1.8e-8,6e-9,2.1e-8,1.45e-7,1.1e-8,3.1e-8,6e-9,2e-9,3.8e-9,2e-12,2e-12};
            for(int i=0;i<actual.length;i++)errors[i]=Math.abs(actual[i]-first.get(channels[i]));
            System.out.println("REPCGFMC1U initialization "+p.name()+" channels="+Arrays.toString(channels)+" errors="+Arrays.toString(errors));
            for(int i=0;i<actual.length;i++)assertTrue(errors[i]<bounds[i],p.name()+" "+channels[i]+" error="+errors[i]);
            assertEquals(initial.qTarget(),first.get("PLANT_VAR2"),p.name().equals("kp-only-flat")?2.5e-7:3.8e-9,
                    "Retain proportional-only post-initialization float-voltage quantization separately");
        }
    }
    @Test void lossAndCompensatedVoltageAlgebraReplaysEveryRawSample(){
        for(var p:Repcgfmc1NativeContractReference.profiles()){
            double[] max=new double[3];double[] times=new double[3];var data=p.parameters();
            for(var r:rows(p.body())){
                double active=r.get("PLANT_VAR1"),reactive=r.get("PLANT_VAR2"),resistance=r.get("PLANT_VAR8"),reactance=r.get("PLANT_VAR9");
                double squared=active*active+reactive*reactive;
                double voltage=data.voltageReferenceFlag()==1?new Complex(r.get("PLANT_STATE2"),0)
                        .add(new Complex(resistance,reactance).multiply(new Complex(active,-reactive))).abs():0;
                double[] expected={resistance*squared,reactance*squared,voltage};String[] channels={"PLANT_VAR10","PLANT_VAR11","PLANT_VAR4"};
                for(int i=0;i<3;i++){double error=Math.abs(expected[i]-r.get(channels[i]));if(error>max[i]){max[i]=error;times[i]=r.get("time_s");}}
            }
            System.out.println("REPCGFMC1U algebraic "+p.name()+" errors="+Arrays.toString(max)+" times="+Arrays.toString(times));
            double[] bounds={1.9e-10,6.2e-9,1.45e-7};
            for(int i=0;i<3;i++)assertTrue(max[i]<bounds[i],"Native float algebraic residual "+i+"="+max[i]);
        }
    }
    @Test void subsequentExactImportDoesNotCreateAnUnprovedSourceNameAlias(){
        assertEquals("com.interpss.dstab.renewable.Repcgfmc1Model",DynamicModelCatalog.find("REPCGFMC1U").orElseThrow().runtimeClassName());
        assertTrue(DynamicModelCatalog.find("REPCGFMC1").isEmpty());
    }
}
