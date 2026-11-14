package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.RepccNativeReference;
import org.interpss.core.dstab.reference.RepccShuntNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.FixedShuntAdmittance;
import org.interpss.fadapter.psse.*;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseRepccModelTest extends CorePluginTestSetup {
    private static String input(){return RepccNativeReference.profiles().getFirst().input();}
    private static DStabNetworkBuilder builder() throws Exception {
        return new DStabNetworkBuilder(new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw").getDStabilityNet());
    }
    private static Regca1Model owner(DStabNetworkBuilder b){return (Regca1Model)((DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();}
    private static String plant(){return input().lines().filter(s->s.contains("'REPCC'")).findFirst().orElseThrow();}
    private static String withoutPlant(){return input().lines().filter(s->!s.contains("'REPCC'")).reduce("",(a,s)->a+s+"\n");}
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder b,Path dir,String input) throws Exception {
        Path file=dir.resolve("plant.dyr");Files.writeString(file,input);var parser=new PSSEDStabDirectParser(b).setStrictImport(true);parser.parseDynFile(file.toString());return parser;
    }
    private static String changed(int parameter,String value){
        var matcher=Pattern.compile("'(?:[^']|'')*'|[^\\s,]+").matcher(plant());var fields=new ArrayList<String>();while(matcher.find())fields.add(matcher.group());
        fields.set(3+parameter,value);return String.join(" ",fields);
    }
    @Test void exactSchemaAndPlantBeforeConverterOrderingAreAccepted(@TempDir Path dir) throws Exception {
        for(String text:List.of(input(),plant()+"\n"+withoutPlant())){
            var b=builder();assertTrue(parse(b,dir,text).getLastImportReport().isStrictlyComplete());
            var plant=owner(b).getActiveElectricalController().getRepccController();assertNotNull(plant);
            assertSame(owner(b),plant.getConverter());assertArrayEquals(RepccNativeReference.profiles().getFirst().parameters().constants(),plant.getData().constants());
            assertEquals(10,plant.getData().shunts().size());assertEquals(3,plant.getData().remoteBus());
        }
        var b=builder();assertTrue(parse(b,dir,withoutPlant()+changed(14,"0")).getLastImportReport().isStrictlyComplete(),"Unused shunt ID zero may be unquoted");
    }
    @Test void cardinalityTypingAndNativeNamesAreStrict(@TempDir Path dir) throws Exception {
        for(String bad:List.of(plant().replace(" /"," 0 /"),plant().replace("0.015 -0.015 /","0.015 /"),
                changed(0,"3.5"),changed(3,"1"),changed(35,"NaN"),changed(35,"-.01"),changed(4,"2"),
                plant().replace("'REPCC'","'REPCCU'")))
            assertThrows(InterpssException.class,()->parse(builder(),dir,withoutPlant()+bad),bad);
    }
    @Test void unsupportedInitializationModesAreReportedInsteadOfSilentlySubstituted(@TempDir Path dir) throws Exception {
        for(String bad:List.of(changed(6,"0"),changed(34,"0"),changed(35+2,"0"),changed(35+16,"0")))
            assertThrows(InterpssException.class,()->parse(builder(),dir,withoutPlant()+bad));
    }
    @Test void enabledSwitchingWithoutAMeasurementBranchUsesDocumentedDisablePath(@TempDir Path dir)throws Exception {
        var b=builder();assertTrue(parse(b,dir,withoutPlant()+changed(9,"1")).getLastImportReport().isStrictlyComplete());
        assertEquals(1,owner(b).getActiveElectricalController().getRepccController().getData().switchingFlag());
    }
    @Test void missingOrModifiedDeviceProvenanceCannotOccupyThePlantPort(@TempDir Path dir)throws Exception {
        var profile=RepccShuntNativeReference.profiles().getFirst();
        for(int bad=0;bad<4;bad++) {
            var b=builder();parse(b,dir,withoutPlant());var topology=new AclfNetworkBuilder(b.getDStabNetwork());
            for(var key:profile.parameters().shunts())topology.addFixedShunt("Bus2",key.id(),true,.001,.01,null);
            var bus=b.getDStabNetwork().getBus("Bus2");var device=bus.getCompensator("C1");
            if(bad==0)device.eAdapters().removeIf(a->a instanceof FixedShuntAdmittance);
            if(bad==1)device.setB(.02);
            if(bad==2)topology.addFixedShunt("Bus2","C1",true,0,.01,null);
            if(bad==3)bus.getCompensatorList().remove(device);
            assertThrows(IllegalArgumentException.class,()->b.addRepcc("Bus1","1",profile.parameters()));
            assertNull(owner(b).getActiveElectricalController().getRepccController());
        }
    }
    @Test void duplicatesAndLegacyPlantDoNotOverwritePorts(@TempDir Path dir) throws Exception {
        var b=builder();parse(b,dir,input());var original=owner(b).getActiveElectricalController().getRepccController();
        assertThrows(InterpssException.class,()->parse(b,dir,plant()));assertSame(original,owner(b).getActiveElectricalController().getRepccController());
        var legacy=builder();String row=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr")).lines().filter(s->s.contains("'REPCA1'")).findFirst().orElseThrow();
        assertThrows(InterpssException.class,()->parse(legacy,dir,withoutPlant()+row+"\n"+plant()));
        assertNotNull(owner(legacy).getActiveElectricalController().getPlantController());assertNull(owner(legacy).getActiveElectricalController().getRepccController());
    }
    @Test void missingConverterAndTypedHostMismatchCannotAttach(@TempDir Path dir) throws Exception {
        var b=builder();assertThrows(InterpssException.class,()->parse(b,dir,plant()));
        assertNull(b.addRepcc("Bus1","1",RepccNativeReference.profiles().getFirst().parameters()));
        var left=builder();var right=builder();parse(left,dir,withoutPlant());parse(right,dir,withoutPlant());
        assertThrows(IllegalArgumentException.class,()->owner(left).getActiveElectricalController().setRepccController(new RepccModel(RepccNativeReference.profiles().getFirst().parameters(),owner(right))));
    }
    @Test void enabledSwitchingImportsFullAdmittanceForInitiallyOpenAndClosedDevices(@TempDir Path dir)throws Exception {
        var profile=RepccShuntNativeReference.profiles().getFirst();
        var lines=profile.status().lines().toList();var names=lines.getFirst().split(",");var values=lines.get(1).split(",");
        var initial=new HashMap<String,Double>();for(int i=0;i<names.length;i++)initial.put(names[i],Double.parseDouble(values[i]));
        StringBuilder records=new StringBuilder();var nominal=new HashMap<String,Complex>();
        for(var key:profile.parameters().shunts()) {
            // Distinct nonzero G, including open devices; aggregate bus G cannot recover it.
            double g=key.id().equals("C1")?.003:.001,b=initial.get(key.id()+"_B_NOM")/100;
            nominal.put(key.id(),new Complex(g,b));
            records.append("2,'").append(key.id()).append("',").append(initial.get(key.id()+"_STATUS").intValue())
                    .append(',').append(g*100).append(',').append(b*100).append('\n');
        }
        String raw=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regfma1_native.raw"));
        Path source=dir.resolve("switches.raw");Files.writeString(source,raw.replace("0 / END OF FIXED SHUNT DATA",records+"0 / END OF FIXED SHUNT DATA"));
        for(double h:new double[]{.0005,.00025}) {
            var ctx=new PSSEMultiFileLoader().loadDStab(source.toString());var b=new DStabNetworkBuilder(ctx.getDStabilityNet());
            assertTrue(parse(b,dir,profile.input()).getLastImportReport().isStrictlyComplete());
            var plant=owner(b).getActiveElectricalController().getRepccController();assertNotNull(plant);
            var net=b.getDStabNetwork();var algo=ctx.getDynSimuAlgorithm();net.setAllowGenWithoutMach(true);
            algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);algo.getAclfAlgorithm().setTolerance(1e-10);
            assertTrue(algo.getAclfAlgorithm().loadflow());algo.setSimuStepSec(h);algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algo.setSimuOutputHandler(new StateMonitor());assertTrue(algo.initialization());assertTrue(plant.getBoundary().shuntController().isEnabled());
            var bus=net.getBus("Bus2");int index=bus.getSortNumber();Complex original=net.getYMatrix().getA(index,index),originalG=bus.getShuntY();
            for(int tick=0;tick<(int)Math.round(.03/h);tick++)assertTrue(algo.solveDEqnStep(true));
            Complex delta=Complex.ZERO;int changed=0,opened=0,closed=0;
            for(var key:profile.parameters().shunts()) {
                double sign=(bus.getCompensator(key.id()).isActive()?1:0)-initial.get(key.id()+"_STATUS");
                if(sign!=0)changed++;if(sign<0)opened++;if(sign>0)closed++;
                delta=delta.add(nominal.get(key.id()).multiply(sign));
            }
            assertTrue(changed>0);assertTrue(opened>0&&closed>0,"Exercise both initially closed and initially open devices");
            assertTrue(Math.abs(delta.getReal())>.0001,"Activate the retained conductance path");
            assertTrue(bus.getShuntY().subtract(originalG.add(new Complex(delta.getReal(),0))).abs()<1e-12);
            assertTrue(net.getYMatrix().getA(index,index).subtract(original.add(delta)).abs()<1e-11);
            net.formYMatrix4DStab();assertTrue(net.getYMatrix().getA(index,index).subtract(original.add(delta)).abs()<1e-11);
        }
    }
}
