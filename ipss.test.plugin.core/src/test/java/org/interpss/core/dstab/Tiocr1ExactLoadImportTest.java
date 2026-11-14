package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Native records select exact identities; typed wildcards must not leak into import. */
public class Tiocr1ExactLoadImportTest {
    @TempDir Path directory;
    private static final Path ROOT=Path.of("testData/adpter/psse/v33/HspModels");
    @ParameterizedTest @CsvSource({"*,false","*,true","#,false","#,true"})
    void symbolicTargetsRequireAnExistingExactIdentity(String id,boolean literalExists)throws Exception {
        var context=base();var net=context.getDStabilityNet();
        if(literalExists)net.getDStabBus("Bus2").getContributeLoad("1").setId(id);
        int before=net.getBusList().stream().mapToInt(b->b.getDynamicBusDeviceList().size()).sum();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        Path input=directory.resolve("relay.dyr");Files.writeString(input,record(2,id));
        if(literalExists) {
            parser.parseDynFile(input.toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());
            assertEquals(before+2,net.getBusList().stream().mapToInt(b->b.getDynamicBusDeviceList().size()).sum());
            return;
        }
        assertThrows(Exception.class,()->parser.parseDynFile(input.toString()));
        assertFalse(parser.getLastImportReport().isStrictlyComplete());
        assertTrue(parser.getLastImportReport().failures().getFirst().message().contains("does not expand wildcard load IDs"));
        assertEquals(before,net.getBusList().stream().mapToInt(b->b.getDynamicBusDeviceList().size()).sum());
    }
    @ParameterizedTest @CsvSource({"2,1","0,*","0,#"})
    void exactAndNoLoadActionsRemainSupported(int bus,String id)throws Exception {
        var context=base();var net=context.getDStabilityNet();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        Path input=directory.resolve("relay.dyr");Files.writeString(input,record(bus,id));parser.parseDynFile(input.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1,net.getBusList().stream().flatMap(b->b.getDynamicBusDeviceList().stream()).filter(com.interpss.dstab.relay.model.Tiocr1RelayModel.class::isInstance).count());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"*","#"})
    void typedWildcardRemainsAnExplicitApplicationChoice(String id)throws Exception {
        var context=base();var net=context.getDStabilityNet();assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var host=net.getDStabBus("Bus2");var branch=net.getBranchList().getFirst();
        var curve=new com.interpss.dstab.relay.model.Tiocr1Data(new double[]{.4,.1,1.1,.04,1.5,.03,2,.02,3,.01,5,.005,.01,.1});
        var relay=new com.interpss.dstab.relay.model.Tiocr1RelayModel(branch,true,1,true,host,id,new com.interpss.core.aclf.AclfBranch[3],curve);
        assertTrue(relay.initStates(relay.getDStabBus()));
    }
    private com.interpss.simu.SimuContext base()throws Exception {
        IpssCorePlugin.init();Path dynamic=directory.resolve("base.dyr");
        Files.writeString(dynamic,Files.readString(ROOT.resolve("tiocr1-1.dyr")).replaceAll("(?m)^.*TIOCR1.*$",""));
        return new PSSEMultiFileLoader().loadDStab(ROOT.resolve("network.raw").toString(),dynamic.toString());
    }
    private static String record(int bus,String id) {
        return "1 'TIOCR1' 2 '1' 1 1 "+bus+" '"+id+"' 1 2 '1' 0 0 '0' 0 0 '0' .40 .1 1.1 .04 1.5 .03 2 .02 3 .01 5 .005 .01 .1 /\n";
    }
}

