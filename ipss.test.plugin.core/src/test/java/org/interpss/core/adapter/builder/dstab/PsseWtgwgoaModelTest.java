package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;

public class PsseWtgwgoaModelTest extends CorePluginTestSetup {
    private static final String STANDARD="1 'WTGWGOA' 1 .99 .08 .4 .16 .2 .1 .005 .02 /\n";
    private static final String LEGACY=STANDARD.replace("'WTGWGOA' 1","'USRMDL' 1 'WTGWGOAU' 109 0 0 8 3 3");
    @Test void exactSchemasAttachAfterTheirHostRegardlessOfRecordPosition(@TempDir Path directory) throws Exception {
        var first=DStabBuilderTestFixture.createBuilder();var second=DStabBuilderTestFixture.createBuilder();
        assertTrue(parse(first,directory,STANDARD+host()).getLastImportReport().isStrictlyComplete());
        parse(second,directory,host()+LEGACY);
        assertEquals(model(first).getData(),model(second).getData());
        assertEquals(WtgwgoaModel.EquationConvention.PSSE_36_7,model(first).getEquationConvention());
        assertEquals("WTGWGOA",DynamicModelCatalog.canonicalName("WTGWGOAU"));
        assertEquals(Set.of(8,14),DynamicModelCatalog.find("WTGWGOAU").orElseThrow().recordSchema().acceptedParameterCounts());
    }
    @Test void optionalPlantControllerDoesNotPreventNativeAttachment(@TempDir Path directory) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        parse(builder,directory,host().lines().filter(s->!s.contains("REPCA1")).reduce("",(a,b)->a+b+"\n")+STANDARD);
        assertNotNull(model(builder));assertNull(converter(builder).getActiveElectricalController().getPlantController());
    }
    @Test void behindImpedanceRegcbHostReceivesTheSameWeakGridAttachment(@TempDir Path directory) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        parse(builder,directory,"1 'REGCB1' 1 0 1 .02 .02 70 -70 .4 .012 1.25 /\n");
        double[] table=new double[10];
        // REECD is the existing REGCB electrical-controller import path.
        assertNotNull(builder.addReecd1("Bus1","1",new Reecd1Data(0,0,0,0,0,0,
                .7,1.3,0,0,0,0,1,-1,1,0,0,0,0,2,-2,2,-2,0,0,0,0,0,0,100,-100,
                2,0,2,.01,0,0,0,0,0,0,2,0,table,table,table,table)));
        assertTrue(parse(builder,directory,STANDARD).getLastImportReport().isStrictlyComplete());
        var gen=(DStabGen)builder.getDStabNetwork().getDStabBus("Bus1").getContributeGen("1");
        var converter=assertInstanceOf(Regcb1Model.class,gen.getDynamicGenDevice());
        assertNotNull(converter.getActiveElectricalController().getWeakGridController());
    }
    @Test void strictLayoutsAndInvalidConstantsAreRejectedWithoutPartialAttachment(@TempDir Path directory) throws Exception {
        var invalid=new ArrayList<>(List.of(STANDARD.replace(".005 .02 /",".005 /"),STANDARD.replace(".005 .02 /",".005 .02 9 /"),
                STANDARD.replace("WTGWGOA","WTGWGOAU"),LEGACY.replace("WTGWGOAU","WTGWGOA"),LEGACY.replace("USRMDL","USRLOD"),
                STANDARD.replace(".08 .4","NaN .4"),STANDARD.replace(".08 .4",".08 0"),STANDARD.replace(".005 .02",".005 -1")));
        String[] tokens={"109","0","0","8","3","3"};
        for(int i=0;i<6;i++){String[] changed=tokens.clone();changed[i]="99";invalid.add(LEGACY.replace("109 0 0 8 3 3",String.join(" ",changed)));}
        for(String record:invalid){var builder=DStabBuilderTestFixture.createBuilder();
            assertThrows(InterpssException.class,()->parse(builder,directory,host()+record));assertNull(model(builder));}
    }
    @Test void duplicateDoesNotReplaceFirstModelAndDisabledOrderIsAccepted(@TempDir Path directory) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        assertThrows(InterpssException.class,()->parse(builder,directory,host()+STANDARD+STANDARD.replace(".99 .08",".9 .08")));
        assertEquals(.99,model(builder).getData().voltageThreshold());
        var disabled=DStabBuilderTestFixture.createBuilder();parse(disabled,directory,host()+STANDARD.replace(".4 .16",".4 .08"));
        assertFalse(model(disabled).getData().enabled());
    }
    @Test void missingHostAndMissingTargetDoNotClaimAttachment(@TempDir Path directory) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        assertThrows(InterpssException.class,()->parse(builder,directory,STANDARD));assertNull(model(builder));
        assertThrows(InterpssException.class,()->parse(builder,directory,host()+STANDARD.replace("1 'WTGWGOA'","9 'WTGWGOA'")));
        assertNull(model(builder));
    }
    @Test void intentionalRemovalPreservesStrictImportAccounting(@TempDir Path directory) throws Exception {
        for(boolean gnet:new boolean[]{false,true}){
            var builder=DStabBuilderTestFixture.createBuilder();var parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
            var keys=Set.of(new GeneratorKey("Bus1","1"));
            if(gnet)parser.setGnetRemovedGenerators(keys);else parser.setModelRemovedGenerators(keys);
            Path file=directory.resolve("removed.dyr");Files.writeString(file,host()+STANDARD);parser.parseDynFile(file.toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());assertNull(model(builder));
        }
    }
    private static String host() throws Exception {
        return Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_wtgwgoa_bus1062.dyr"))
                .lines().filter(s->!s.contains("WTGWGOA")&&!s.startsWith("3 ")).reduce("",(a,b)->a+b+"\n");
    }
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder builder,Path directory,String text) throws Exception {
        Path file=directory.resolve("model.dyr");Files.writeString(file,text);
        var parser=new PSSEDStabDirectParser(builder).setStrictImport(true);parser.parseDynFile(file.toString());return parser;
    }
    private static Regca1Model converter(DStabNetworkBuilder builder){
        var gen=(DStabGen)builder.getDStabNetwork().getDStabBus("Bus1").getContributeGen("1");
        return gen.getDynamicGenDevice() instanceof Regca1Model device?device:null;
    }
    private static WtgwgoaModel model(DStabNetworkBuilder builder){
        var converter=converter(builder);return converter==null?null:converter.getActiveElectricalController().getWeakGridController();
    }
}
