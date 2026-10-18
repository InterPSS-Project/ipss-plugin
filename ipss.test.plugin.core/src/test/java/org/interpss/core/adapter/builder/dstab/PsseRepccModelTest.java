package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.RepccNativeReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;
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
        for(String bad:List.of(changed(9,"1"),changed(6,"0"),changed(34,"0"),changed(35+2,"0"),changed(35+16,"0")))
            assertThrows(InterpssException.class,()->parse(builder(),dir,withoutPlant()+bad));
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
}
