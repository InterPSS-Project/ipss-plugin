package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;

public class PsseWtptb1ModelTest extends CorePluginTestSetup {
    private static final String STANDARD="1 'WTPTB1' 1 29 177 23.72 2.93 .25 .3 27 0 10 -10 5 -5 25 0 /\n";
    private static final String LEGACY=STANDARD.replace("'WTPTB1' 1","'USRMDL' 1 'WTPTBU1' 104 0 0 14 3 1");
    @Test void standardMayPrecedeItsCompleteMechanicalHost(@TempDir Path directory) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var parser=parse(builder,directory,STANDARD+host());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(6,parser.getLastImportReport().totalRecordCount());
        assertNotNull(model(builder));
        assertEquals(23.72,model(builder).getData().kic(),0);
        assertEquals("WTPTB1",DynamicModelCatalog.canonicalName("WTPTBU1"));
        assertEquals(Set.of(14,20),DynamicModelCatalog.find("WTPTBU1").orElseThrow().recordSchema().acceptedParameterCounts());
    }
    @Test void exactLegacyAllocationPreservesAllFourteenConstants(@TempDir Path directory) throws Exception {
        var first=DStabBuilderTestFixture.createBuilder();var second=DStabBuilderTestFixture.createBuilder();
        parse(first,directory,host()+STANDARD);parse(second,directory,host()+LEGACY);
        assertEquals(model(first).getData(),model(second).getData());
    }
    @Test void malformedCountsWrappersMetadataAndParametersAreRejected(@TempDir Path directory) throws Exception {
        for(String invalid:List.of(STANDARD.replace("25 0 /","25 /"),STANDARD.replace("25 0 /","25 0 99 /"),
                STANDARD.replace("WTPTB1","WTPTBU1"),LEGACY.replace("104 0 0 14 3 1","103 0 0 14 3 1"),
                LEGACY.replace("104 0 0 14 3 1","104 1 0 14 3 1"),LEGACY.replace("104 0 0 14 3 1","104 0 1 14 3 1"),
                LEGACY.replace("104 0 0 14 3 1","104 0 0 13 3 1"),LEGACY.replace("104 0 0 14 3 1","104 0 0 14 4 1"),
                LEGACY.replace("104 0 0 14 3 1","104 0 0 14 3 2"),LEGACY.replace("WTPTBU1","WTPTB1"),
                LEGACY.replace("USRMDL","USRLOD"),STANDARD.replace("29 177","NaN 177"),STANDARD.replace(".25 .3",".25 -1"))) {
            var builder=DStabBuilderTestFixture.createBuilder();
            assertThrows(InterpssException.class,()->parse(builder,directory,host()+invalid));
            assertNull(model(builder));
        }
    }
    @Test void duplicatesAndMixedPitchModelsCannotReplaceAnAttachedController(@TempDir Path directory) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        assertThrows(InterpssException.class,()->parse(builder,directory,host()+STANDARD+STANDARD.replace("29 177","99 177")));
        assertEquals(29,model(builder).getData().kiw(),0);
        assertNull(builder.addWtpta1("Bus1","1",new Wtpta1Data(1,1,1,1,0,.3,27,0,10,-10)));
        var other=DStabBuilderTestFixture.createBuilder();
        parse(other,directory,host()+"1 'WTPTA1' 1 1 1 1 1 0 .3 27 0 10 -10 /\n");
        assertNull(other.addWtptb1("Bus1","1",model(builder).getData()));
    }
    @Test void allRequiredMechanicalModelsAndTargetMustExist(@TempDir Path directory) throws Exception {
        for(String missing:List.of("WTARA1","WTTQA1","WTDTB1")) {
            String partial=host().lines().filter(line->!line.contains(missing)).reduce("",(a,b)->a+b+"\n");
            var builder=DStabBuilderTestFixture.createBuilder();
            assertThrows(InterpssException.class,()->parse(builder,directory,partial+STANDARD));
            assertNull(model(builder));
        }
        var builder=DStabBuilderTestFixture.createBuilder();
        assertThrows(InterpssException.class,()->parse(builder,directory,host()+STANDARD.replace("1 'WTPTB1'","9 'WTPTB1'")));
        assertNull(model(builder));
    }
    @Test void intentionalWholeStackRemovalIsNotMissingModelSupport(@TempDir Path directory) throws Exception {
        for(boolean gnet:new boolean[]{false,true}) {
            var builder=DStabBuilderTestFixture.createBuilder();
            var parser=new PSSEDStabDirectParser(builder).setIgnoreMissingHosts(false).setStrictImport(true);
            var keys=Set.of(new GeneratorKey("Bus1","1"));
            if(gnet)parser.setGnetRemovedGenerators(keys);else parser.setModelRemovedGenerators(keys);
            Path file=directory.resolve("removed.dyr");Files.writeString(file,host()+STANDARD);parser.parseDynFile(file.toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());assertNull(model(builder));
        }
    }
    private static String host() throws Exception {
        return Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_wtptb1_bus1062.dyr"))
                .lines().filter(line->!line.contains("WTPTB1")&&!line.startsWith("3 "))
                .reduce("",(a,b)->a+b+"\n");
    }
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder builder,Path directory,String text) throws Exception {
        Path file=directory.resolve("model.dyr");Files.writeString(file,text);
        var parser=new PSSEDStabDirectParser(builder).setIgnoreMissingHosts(false).setStrictImport(true);parser.parseDynFile(file.toString());return parser;
    }
    private static Wtptb1Model model(DStabNetworkBuilder builder) {
        var gen=(DStabGen)builder.getDStabNetwork().getDStabBus("Bus1").getContributeGen("1");
        if(!(gen.getDynamicGenDevice() instanceof Regca1Model converter) || converter.getReeca1Controller()==null
                || converter.getReeca1Controller().getWindControlStack()==null)return null;
        return converter.getReeca1Controller().getWindControlStack().getPitchControllerB();
    }
}
