package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.Regfmb1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.*;
import com.interpss.dstab.DStabGen;
import com.interpss.common.exp.InterpssException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseRegfmb1ModelTest {
    private static String record() throws Exception {return Files.readAllLines(Path.of("testData/adpter/psse/v33/renewable/regfmb1u_native.dyr")).getFirst()+"\n";}
    private static DStabGen gen(DStabNetworkBuilder b){return (DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1");}
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder b,Path dir,String text,boolean strict) throws Exception {
        Path file=dir.resolve("regfmb.dyr");Files.writeString(file,text);var parser=new PSSEDStabDirectParser(b).setStrictImport(strict);parser.parseDynFile(file.toString());return parser;
    }
    @Test void exactWrapperAttachesWithoutReorderingAndObservationsArePure(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();var parser=parse(b,dir,record(),true);
        var m=assertInstanceOf(Regfmb1Model.class,gen(b).getDynamicGenDevice());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertArrayEquals(new int[]{0,0,0,0,1,0},m.getData().toNativeIcons());assertEquals(.05,m.getData().constants()[0]);assertEquals(3,m.getData().constants()[11]);
        assertEquals(gen(b).getSourceZ(),m.getData().sourceImpedance());assertTrue(m.initStates(m.getDStabBus()));var state=m.getNamedStates();
        assertEquals(14,state.size());m.getOutputObject();assertNotNull(m.getStates(null).get(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID));assertEquals(state,m.getNamedStates());
    }
    @Test void everyAllocationFieldAndCardinalityAreStrict(@TempDir Path dir) throws Exception {
        var tokens=new ArrayList<>(PsseDyrRecordReader.tokenize(record().replace("/","")));
        for(int i=4;i<10;i++){var bad=new ArrayList<>(tokens);bad.set(i,"999");assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,String.join(" ",bad)+" /",true));}
        for(String text:List.of(record().replace("1 0.05 /","1 /"),record().replace(" /"," 0 /"),record().replace("'USRMDL' '1' 'REGFMB1U' 101 1 6 25 14 17","'REGFMB1U' '1'")))
            assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,text,true));
    }
    @Test void aliasesAndMalformedNumbersAreNotGuessed(@TempDir Path dir) throws Exception {
        for(String text:List.of(record().replace("REGFMB1U","REGFMB1"),record().replace(" 5.86 "," NaN "),record().replace(" 0.1 -0.1 "," bad -0.1 ")))
            assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,text,true));
    }
    @Test void inactiveMissingAndAlreadyAttachedHostsAreRejected(@TempDir Path dir) throws Exception {
        var inactive=DStabBuilderTestFixture.createBuilder();gen(inactive).setStatus(false);assertThrows(InterpssException.class,()->parse(inactive,dir,record(),true));
        var machine=DStabBuilderTestFixture.createWithMachine();assertThrows(InterpssException.class,()->parse(machine,dir,record(),true));
        assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,record().replace("'1' 'REGFMB1U'","'9' 'REGFMB1U'"),true));
        var b=DStabBuilderTestFixture.createBuilder();parse(b,dir,record(),true);var original=gen(b).getDynamicGenDevice();
        assertThrows(InterpssException.class,()->parse(b,dir,record(),true));assertSame(original,gen(b).getDynamicGenDevice());
    }
    @Test void machineBaseConversionAndNortonBoundaryPreserveSolvedPower(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();gen(b).setMvaBase(50);gen(b).setGen(new Complex(.2,.1));parse(b,dir,record(),true);
        var m=assertInstanceOf(Regfmb1Model.class,gen(b).getDynamicGenDevice());assertTrue(m.initStates(m.getDStabBus()));
        assertEquals(.4,m.getNamedStates().get("Pmeas"),1e-12);assertEquals(.2,m.getNamedStates().get("Qmeas"),1e-12);
        Complex v=m.getDStabBus().getVoltage(),physical=((Complex)m.getOutputObject()).subtract(v.divide(gen(b).getPosGenZ().multiply(gen(b).getZMultiFactor())));
        assertEquals(0,v.multiply(physical.conjugate()).subtract(new Complex(.2,.1)).abs(),1e-12);
    }
    @Test void implicitTransformerAndRemovedGeneratorsAreNotSilentlyAttached(@TempDir Path dir) throws Exception {
        var transformer=DStabBuilderTestFixture.createBuilder();gen(transformer).setXfrZ(new Complex(0,.01));assertThrows(InterpssException.class,()->parse(transformer,dir,record(),true));
        for(boolean gnet:new boolean[]{false,true}) {
            var b=DStabBuilderTestFixture.createBuilder();var p=new PSSEDStabDirectParser(b).setStrictImport(true);
            var keys=Set.of(new org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey("Bus1","1"));
            if(gnet)p.setGnetRemovedGenerators(keys);else p.setModelRemovedGenerators(keys);
            Path file=dir.resolve("removed.dyr");Files.writeString(file,record());p.parseDynFile(file.toString());assertNull(gen(b).getDynamicGenDevice());
        }
    }
    @Test void deferredPlantAttachesInBothSourceOrders(@TempDir Path dir) throws Exception {
        String plant=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr")).lines().filter(s->s.contains("'REPCA1'")).findFirst().orElseThrow()+"\n";
        for(String text:List.of(plant+record(),record()+plant)) {
            var b=DStabBuilderTestFixture.createBuilder();parse(b,dir,text,true);
            var model=assertInstanceOf(Regfmb1Model.class,gen(b).getDynamicGenDevice());assertNotNull(model.getPlantController());
            assertTrue(model.initStates(model.getDStabBus()));assertEquals(0,model.getPlantController().getPref(),0);
        }
    }
    @Test void publicFixtureRetainsNativeBytesApartFromDeclaredLfNormalization() throws Exception {
        String text=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regfmb1u_native.dyr")).replace("\r\n","\n");
        assertEquals("200d305771956adb36e2dd04034f706ae2c02e64e04ff8fdf640c553185535a8",org.interpss.core.dstab.reference.WtgibffraNativeReference.digest(text));
        assertEquals("4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4",org.interpss.core.dstab.reference.WtgibffraNativeReference.digest(text.replace("\n","\r\n")));
    }
}
