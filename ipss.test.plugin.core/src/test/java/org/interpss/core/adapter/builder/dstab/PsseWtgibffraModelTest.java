package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.*;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;

public class PsseWtgibffraModelTest extends CorePluginTestSetup {
    private static double[] constants() {
        double[] con=new double[45];con[0]=.001;
        for(int i=0;i<6;i++){con[1+i]=(i+1)*.1;con[7+i]=.1;con[13+i]=.05;
            con[19+i]=con[25+i]=con[31+i]=.1;con[37+i]=1.2;}
        con[43]=0;con[44]=.2;return con;
    }
    private static String record(int bus,double[] con) {
        return "1 'WTGIBFFRA' 1 "+bus+" "+String.join(" ",Arrays.stream(con).mapToObj(Double::toString).toList())+" /\n";
    }
    @Test void strictNativeRecordAttachesAfterHostRegardlessOfRecordPosition(@TempDir Path directory) throws Exception {
        var first=DStabBuilderTestFixture.createBuilder();var second=DStabBuilderTestFixture.createBuilder();
        String record=record(0,constants());
        assertTrue(parse(first,directory,record+host()).getLastImportReport().isStrictlyComplete());
        assertTrue(parse(second,directory,host()+record).getLastImportReport().isStrictlyComplete());
        assertEquals(control(first).getInertialResponseController().getData(),control(second).getInertialResponseController().getData());
        assertEquals(Set.of(46),DynamicModelCatalog.find("WTGIBFFRA").orElseThrow().recordSchema().acceptedParameterCounts());
    }
    @Test void rejectsGuessedWrapperAliasesExtraIconsAndMalformedFields(@TempDir Path directory) throws Exception {
        String record=record(0,constants());var bad=new ArrayList<String>();
        bad.add(record.replace("WTGIBFFRA","WTGIBFFR_A"));bad.add(record.replace("'WTGIBFFRA' 1","'USRMDL' 1 'WTGIBFFRA'"));
        bad.add(record.replace(" /"," 0 /"));bad.add(record.replace(" 0.2 /"," /"));
        bad.add(record.replace("1 'WTGIBFFRA' 1 0 ","1 'WTGIBFFRA' 1 0.0 "));
        bad.add(record.replace("0.001","NaN"));bad.add(record.replace("0.001","Infinity"));
        for(String text:bad){var b=DStabBuilderTestFixture.createBuilder();
            assertThrows(InterpssException.class,()->parse(b,directory,host()+text),text);
            assertNull(control(b).getInertialResponseController());}
    }
    @Test void duplicateDoesNotReplaceFirstAndDisabledOrderingRemainsExplicit(@TempDir Path directory) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();String record=record(0,constants());
        assertThrows(InterpssException.class,()->parse(b,directory,host()+record+record.replace("0.001","0.002")));
        assertEquals(.001,control(b).getInertialResponseController().getData().deadband());
        var disabled=DStabBuilderTestFixture.createBuilder();double[] con=constants();con[2]=con[1];
        parse(disabled,directory,host()+record(0,con));assertFalse(control(disabled).getInertialResponseController().getData().enabled());
    }
    @Test void remoteLocalMissingAndInactiveFrequencyBusAreResolvedWithoutChangingPowerBase(@TempDir Path directory) throws Exception {
        for(int frequencyBus:new int[]{0,2,9999}) {
            var b=DStabBuilderTestFixture.createBuilder();var net=b.getDStabNetwork();
            new AclfNetworkBuilder(net).addBus("Bus2","Remote",2L,16500,1,0,null,null,null);
            parse(b,directory,host()+record(frequencyBus,constants()));var c=control(b);
            net.getBus("Bus1").setFreq(1);net.getBus("Bus2").setFreq(1);c.initialize(.7164,.2705,1.04);
            if(frequencyBus==2)net.getBus("Bus2").setFreq(.98);else net.getBus("Bus1").setFreq(.98);
            c.step(0,.7164,.2705,1.04,1,0);assertEquals(WtgibffraModel.Mode.RISE,c.getInertialResponseController().getMode());
            assertEquals(.7164,c.getInertialResponseController().getCapturedPower(),0);
            c.initialize(.7164,.2705,1.04);net.getBus("Bus2").setStatus(false);net.getBus("Bus1").setFreq(.98);
            c.step(0,.7164,.2705,1.04,1,0);assertEquals(WtgibffraModel.Mode.RISE,c.getInertialResponseController().getMode());
        }
    }
    @Test void wrongElectricalControllerAndMissingHostCannotClaimLoadability(@TempDir Path directory) throws Exception {
        var missing=DStabBuilderTestFixture.createBuilder();assertThrows(InterpssException.class,()->parse(missing,directory,record(0,constants())));
        var wrong=DStabBuilderTestFixture.createBuilder();String other=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr"));
        assertThrows(InterpssException.class,()->parse(wrong,directory,other.lines().filter(s->!s.startsWith("3 ")).reduce("",(a,b)->a+b+"\n")+record(0,constants())));
        var gen=(DStabGen)wrong.getDStabNetwork().getBus("Bus1").getContributeGen("1");
        assertNull(((Regca1Model)gen.getDynamicGenDevice()).getActiveElectricalController().getInertialResponseController());
    }
    @Test void regcbReceivesTheSameReecdPauxAttachment(@TempDir Path directory) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();String converted=host().lines().filter(s->!s.contains("REGCA1")).reduce("",(a,c)->a+c+"\n");
        parse(b,directory,"1 'REGCB1' 1 0 1 .02 .02 70 -70 .4 .012 1.25 /\n"+converted+record(0,constants()));
        assertNotNull(control(b).getInertialResponseController());
    }
    @Test void weakGridPrefAndInertialPauxCoexistWithoutAliasingTheirSignals(@TempDir Path directory) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();
        parse(b,directory,record(0,constants())+host()+"1 'WTGWGOA' 1 .99 .08 .4 .16 .2 .1 .005 .02 /\n");
        var c=control(b);assertNotNull(c.getWeakGridController());assertNotNull(c.getInertialResponseController());
        b.getDStabNetwork().getBus("Bus1").setFreq(1);c.initialize(.7164,.2705,1.04);
        b.getDStabNetwork().getBus("Bus1").setFreq(.98);
        for(int i=0;i<40;i++){c.step(.001,.7164,.2705,.8,1,0);c.step(.001,.7164,.2705,.8,1,1);}
        assertEquals(.08,c.getSelectedPowerReference(.7164),1e-12);assertTrue(c.getAuxiliaryPower()>0);
        assertEquals(.7164,c.getInertialResponseController().getCapturedPower(),0);
    }
    @Test void converterConvertsIncidentPowerToMachineBaseBeforeBandSelection(@TempDir Path directory) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();parse(b,directory,host()+record(0,constants()));
        var bus=b.getDStabNetwork().getDStabBus("Bus1");var gen=(DStabGen)bus.getContributeGen("1");gen.setMvaBase(50);
        var converter=(Regca1Model)gen.getDynamicGenDevice();assertTrue(converter.initStates(bus));
        double incident=converter.getNamedStates().get("Active Power");assertEquals(2*.7164,incident,1e-12);
        bus.setFreq(.98);var c=control(b);c.step(0,incident,0,1.04,1,0);
        assertEquals(incident,c.getInertialResponseController().getCapturedPower(),0);assertEquals(5,c.getInertialResponseController().getSelectedBand());
    }
    @Test void gnetAndModelRemovalKeepStrictImportAccounting(@TempDir Path directory) throws Exception {
        for(boolean gnet:new boolean[]{true,false}) {
            var b=DStabBuilderTestFixture.createBuilder();var p=new PSSEDStabDirectParser(b).setStrictImport(true);
            var keys=Set.of(new GeneratorKey("Bus1","1"));if(gnet)p.setGnetRemovedGenerators(keys);else p.setModelRemovedGenerators(keys);
            Path file=directory.resolve("removed.dyr");Files.writeString(file,host()+record(0,constants()));p.parseDynFile(file.toString());
            assertTrue(p.getLastImportReport().isStrictlyComplete());
            var gen=(DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1");assertNull(gen.getDynamicGenDevice());
        }
    }
    private static String host() throws Exception {
        return Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reecd_paux_bus1062.dyr"))
                .lines().filter(s->!s.startsWith("3 ")).reduce("",(a,b)->a+b+"\n");
    }
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder b,Path directory,String text) throws Exception {
        Path file=directory.resolve("model.dyr");Files.writeString(file,text);var p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(file.toString());return p;
    }
    private static Reecd1Model control(DStabNetworkBuilder b) {
        var gen=(DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1");
        var device=gen.getDynamicGenDevice();return (Reecd1Model)(device instanceof Regca1Model a?a.getActiveElectricalController():((Regcb1Model)device).getActiveElectricalController());
    }
}
