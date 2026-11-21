package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.core.dstab.reference.ReeceLocalPiNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;

/** Native 36.7 standard REECE only; no assumed REECE1/REECEU1 layouts. */
public class PsseReeceModelTest {
    private static final String A="1 'REGCA1' 1 1 .02 10 .9 .5 1.22 1.2 .8 .4 -1.3 .02 .7 0 0 .8 /\n";
    private static final String B="1 'REGCB1' 1 0 1 .02 .01 10 -10 10 .01 1.2 /\n";
    private static Reece1Data data(){return ReeceLocalPiNativeReference.profiles().getFirst().parameters();}
    private static String parameters(Reece1Data d) {
        return String.join(" ",Arrays.stream(d.toNativeIcons()).mapToObj(Integer::toString).toList())+" "+
                String.join(" ",Arrays.stream(d.constants()).mapToObj(Double::toString).toList());
    }
    private static String record(){return "1 'REECE' 1 "+parameters(data())+" /\n";}
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder b,Path dir,String text,boolean strict) throws Exception {
        var p=new PSSEDStabDirectParser(b).setIgnoreMissingHosts(false).setStrictImport(strict);Path file=dir.resolve("reece.dyr");Files.writeString(file,text);p.parseDynFile(file.toString());return p;
    }
    private static DStabGen gen(DStabNetworkBuilder b){return (DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1");}
    private static RenewableElectricalController controller(DStabNetworkBuilder b) {
        Object host=gen(b).getDynamicGenDevice();return host instanceof Regca1Model a?a.getActiveElectricalController():host instanceof Regcb1Model v?v.getActiveElectricalController():null;
    }
    @Test void standardSchemaAttachesRegardlessOfConverterRecordOrder(@TempDir Path dir) throws Exception {
        for(String host:List.of(A,B))for(boolean first:new boolean[]{false,true}) {
            var b=DStabBuilderTestFixture.createBuilder();var p=parse(b,dir,first?record()+host:host+record(),true);
            assertTrue(p.getLastImportReport().isStrictlyComplete());assertEquals(data(),assertInstanceOf(Reece1Model.class,controller(b)).getData());
            var result=p.getLastImportReport().entries().stream().filter(e->e.canonicalModelName().equals("REECE")).findFirst().orElseThrow();
            assertEquals(87,result.parameterCount());assertFalse(result.aliasConverted());assertTrue(result.message().contains("36.7"));
        }
    }
    @Test void allNativeSelectorsAndConstantsRoundTripWithoutReordering(@TempDir Path dir) throws Exception {
        double[] c=data().constants();c[10]=-.06;c[11]=.08;c[17]=.4;c[18]=3.1;c[19]=.6;c[20]=4.2;
        for(int i=0;i<10;i++){c[29+2*i]=c[49+2*i]=.2+i*.1;c[30+2*i]=.3+i*.05;c[50+2*i]=.25+i*.05;}
        c[69]=.01;c[70]=.07;c[71]=.03;c[72]=.04;c[73]=.5;c[77]=2.7;c[78]=4.9;
        var d=Reece1Data.fromNativeParameters(new int[]{1,1,2,1,0,1,1,1},c);var b=DStabBuilderTestFixture.createBuilder();
        parse(b,dir,A+"1 'reece' 1 "+parameters(d).replace(" ",",")+" /\n",true);
        var actual=assertInstanceOf(Reece1Model.class,controller(b)).getData();assertArrayEquals(d.toNativeIcons(),actual.toNativeIcons());assertArrayEquals(c,actual.constants());
    }
    @Test void descriptorAndSourceListPreserveTheVersionedNameBoundary() {
        var d=DynamicModelCatalog.find("REECE").orElseThrow();assertEquals(Set.of(87),d.recordSchema().acceptedParameterCounts());assertTrue(d.aliases().isEmpty());
        for(String name:List.of("REECE1","REECEU1","REEC_E"))assertTrue(DynamicModelCatalog.find(name).isEmpty(),name);
        var row=WeccApprovedDynamicModelCatalog.renewables().stream().filter(r->r.catalogName().equals("REEC_E")).findFirst().orElseThrow();
        assertEquals("REECEU1, REECE1",row.psseModel());assertEquals("REECE",row.interpssModel());assertTrue(row.isImplementedExactly());
    }
    @Test void rejectsExtraMissingAndGuessedWrapperFields(@TempDir Path dir) throws Exception {
        String values=parameters(data());var bad=List.of("1 'REECE' 1 "+values+" 0 /\n", "1 'REECE' 1 "+values.substring(0,values.lastIndexOf(' '))+" /\n",
                "1 'USRMDL' 1 'REECE' "+values+" /\n", "1 'USRMDL' 1 'REECE' 102 0 8 79 8 20 "+values+" /\n");
        for(String text:bad){var b=DStabBuilderTestFixture.createBuilder();var p=parse(b,dir,A+text,false);assertNull(controller(b));assertFalse(p.getLastImportReport().isStrictlyComplete());}
    }
    @Test void rejectsUnsupportedReleaseNamesRatherThanConvertingThem(@TempDir Path dir) throws Exception {
        for(String name:List.of("REECE1","REECEU1","REEC_E")) {
            var b=DStabBuilderTestFixture.createBuilder();var p=parse(b,dir,A+record().replace("'REECE'","'"+name+"'"),false);
            assertNull(controller(b));assertEquals(1,p.getLastImportReport().count(DynamicModelImportStatus.UNSUPPORTED));
        }
    }
    @Test void malformedSelectorsConstantsAndEnabledZeroIntegralGainNeverAttach(@TempDir Path dir) throws Exception {
        String values=parameters(data());var bad=new ArrayList<String>();
        bad.add(values.replaceFirst("0 0 2","0 0 3"));bad.add(values.replaceFirst("0 0 2","0 0 2.0"));
        bad.add(values.replaceFirst("0.7","NaN"));bad.add(values.replaceFirst("0.7","garbage"));
        bad.add(values.substring(0,values.lastIndexOf(' '))+" 0");
        for(String text:bad){var b=DStabBuilderTestFixture.createBuilder();var p=parse(b,dir,A+"1 'REECE' 1 "+text+" /\n",false);assertNull(controller(b));assertEquals(1,p.getLastImportReport().count(DynamicModelImportStatus.REJECTED));}
    }
    @Test void duplicateRecordsDoNotReplaceTheFirstController(@TempDir Path dir) throws Exception {
        double[] constants=data().constants();constants[77]=3;
        var second=Reece1Data.fromNativeParameters(data().toNativeIcons(),constants);
        var b=DStabBuilderTestFixture.createBuilder();var p=parse(b,dir,A+record()+"1 'REECE' 1 "+parameters(second)+" /\n",false);
        assertEquals(data(),assertInstanceOf(Reece1Model.class,controller(b)).getData());assertEquals(1,p.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }
    @Test void existingElectricalControllerIsNeverSilentlyReplaced(@TempDir Path dir) throws Exception {
        String existing=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reecd_paux_bus1062.dyr"))
                .lines().filter(line->line.startsWith("1 ")).reduce("",(a,b)->a+b+"\n");
        var b=DStabBuilderTestFixture.createBuilder();parse(b,dir,existing,true);Object old=assertInstanceOf(Reecd1Model.class,controller(b));
        assertThrows(InterpssException.class,()->parse(b,dir,record(),true));assertSame(old,controller(b));
    }
    @Test void missingInactiveAndSynchronousHostsAreRejected(@TempDir Path dir) throws Exception {
        for(int scenario=0;scenario<4;scenario++) {
            var b=scenario==3?DStabBuilderTestFixture.createWithMachine():DStabBuilderTestFixture.createBuilder();
            if(scenario==1)gen(b).setStatus(false);if(scenario==2)b.getDStabNetwork().getBus("Bus1").setStatus(false);
            String host=scenario==1||scenario==2?A:"";var p=parse(b,dir,host+record(),false);
            assertNull(controller(b));assertEquals(1,p.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        }
    }
    @Test void missingBusOrGeneratorIsReportedBeforeDeferredAttachment(@TempDir Path dir) throws Exception {
        for(String text:List.of(record().replaceFirst("1 ","99 "),record().replace("'REECE' 1","'REECE' 2"))) {
            var b=DStabBuilderTestFixture.createBuilder();var p=parse(b,dir,A+text,false);assertInstanceOf(ConverterCurrentCommands.class,controller(b));assertEquals(1,p.getLastImportReport().count(DynamicModelImportStatus.MISSING_TARGET));
        }
    }
    @Test void gnetAndModelRemoveSkipTheWholeStackWithoutPendingAttachments(@TempDir Path dir) throws Exception {
        for(boolean gnet:new boolean[]{false,true}) {
            var b=DStabBuilderTestFixture.createBuilder();var p=new PSSEDStabDirectParser(b).setIgnoreMissingHosts(false).setStrictImport(true);var keys=Set.of(new GeneratorKey("Bus1","1"));
            if(gnet)p.setGnetRemovedGenerators(keys);else p.setModelRemovedGenerators(keys);
            Path file=dir.resolve("removed.dyr");Files.writeString(file,record()+A);p.parseDynFile(file.toString());assertNull(gen(b).getDynamicGenDevice());
            assertEquals(2,p.getLastImportReport().count(gnet?DynamicModelImportStatus.SKIPPED_GNET:DynamicModelImportStatus.SKIPPED_MODEL_REMOVE));
        }
    }
    @Test void repeatedParserUseDoesNotRetainRejectedPendingRecords(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();var p=new PSSEDStabDirectParser(b).setIgnoreMissingHosts(false);Path file=dir.resolve("repeat.dyr");
        Files.writeString(file,record());p.parseDynFile(file.toString());Files.writeString(file,A);p.parseDynFile(file.toString());assertInstanceOf(ConverterCurrentCommands.class,controller(b));assertEquals(1,p.getLastImportReport().totalRecordCount());
    }
    @Test void converterInitializationConvertsSystemPowerToMachineBaseOnce(@TempDir Path dir) throws Exception {
        for(String host:List.of(A,B)) {
        var b=DStabBuilderTestFixture.createBuilder();gen(b).setMvaBase(50);gen(b).setGen(new Complex(.1,.025));parse(b,dir,host+record(),true);
        assertTrue(gen(b).getDynamicGenDevice().initStates(b.getDStabNetwork().getBus("Bus1")));
        var c=assertInstanceOf(Reece1Model.class,controller(b));assertEquals(.2,c.getNamedStates().get("PIP"),1e-15);assertEquals(.05/1.04,-c.getIqcmd(),1e-15);
        assertEquals(.2/1.04,c.getIpcmd(),1e-15);
        }
    }
    @Test void deferredPlantAndElectricalControlsAttachInAnySourceOrder(@TempDir Path dir) throws Exception {
        String plant=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr"))
                .lines().filter(line->line.contains("'REPCA1'")).findFirst().orElseThrow()+"\n";
        for(String host:List.of(A,B))for(String text:List.of(plant+record()+host,host+plant+record(),record()+host+plant)) {
            var b=DStabBuilderTestFixture.createBuilder();assertTrue(parse(b,dir,text,true).getLastImportReport().isStrictlyComplete());
            assertNotNull(assertInstanceOf(Reece1Model.class,controller(b)).getPlantController());
        }
    }
    @Test void behindImpedanceNetworkInjectionStopsDuringExplicitBlockingWithoutResettingStates(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();gen(b).setGen(new Complex(.2,.1));double[] constants=data().constants();constants[2]=0;
        var d=Reece1Data.fromNativeParameters(data().toNativeIcons(),constants);
        parse(b,dir,B+"1 'REECE' 1 "+parameters(d)+" /\n",true);
        var converter=assertInstanceOf(Regcb1Model.class,gen(b).getDynamicGenDevice());var bus=b.getDStabNetwork().getBus("Bus1");
        assertTrue(converter.initStates(bus));var c=assertInstanceOf(Reece1Model.class,controller(b));double[] saved=converter.getStateSnapshot();
        c.step(.001,.2,.1,.1,1);assertTrue(c.isBlocked());bus.setVoltage(new Complex(.1,0));
        Complex yVoltage=bus.getVoltage().divide(gen(b).getPosGenZ().multiply(gen(b).getZMultiFactor()));
        Complex physical=((Complex)converter.getOutputObject()).subtract(yVoltage);
        assertEquals(0,physical.abs(),1e-12,"Blocking must stop the actual network current, not just the commands");
        assertArrayEquals(saved,converter.getStateSnapshot(),0,"The hardware gate must not erase retained lag states");
        assertTrue(converter.updateAttributes(false));assertEquals(0,converter.getP(),1e-12);assertEquals(0,converter.getQ(),1e-12);
        c.initialize(.2,.1,1.04);converter.setCommands(0,0);bus.setVoltage(new Complex(.5,0));
        yVoltage=bus.getVoltage().divide(gen(b).getPosGenZ().multiply(gen(b).getZMultiFactor()));
        assertTrue(((Complex)converter.getOutputObject()).subtract(yVoltage).abs()>.01,
                "Zero commands alone are not a hardware-block signal");
    }
}
