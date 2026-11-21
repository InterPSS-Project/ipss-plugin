package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.renewable.Regfmc1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.*;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.common.exp.InterpssException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseRegfmc1ModelTest {
    private static String record() throws Exception{return Files.readAllLines(Path.of("testData/adpter/psse/v33/renewable/regfmc1u_native.dyr")).getFirst()+"\n";}
    private static DStabGen gen(DStabNetworkBuilder b){return (DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1");}
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder b,Path dir,String text) throws Exception {
        Path file=dir.resolve("regfmc.dyr");Files.writeString(file,text);var parser=new PSSEDStabDirectParser(b).setIgnoreMissingHosts(false).setStrictImport(true);parser.parseDynFile(file.toString());return parser;
    }
    @Test void exactWrapperRetainsAllFieldsAndSemanticMonitoring(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();var parser=parse(b,dir,record());var m=assertInstanceOf(Regfmc1Model.class,gen(b).getDynamicGenDevice());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());assertArrayEquals(new int[]{1,0,1},m.getData().toNativeIcons());
        assertEquals(.4,m.getData().constants()[0]);assertEquals(1.5,m.getData().constants()[10]);assertEquals(1.2,m.getData().constants()[33]);
        assertEquals(gen(b).getSourceZ(),m.getData().sourceImpedance());assertTrue(m.initStates(m.getDStabBus()));var state=m.getNamedStates();
        assertEquals(11,state.size());for(int i=0;i<10;i++)m.getOutputObject();assertEquals(state,m.getNamedStates());
        assertNotNull(m.getStates(null).get(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID));
    }
    @Test void everyAllocationFieldAndCardinalityAreStrict(@TempDir Path dir) throws Exception {
        var tokens=new ArrayList<>(PsseDyrRecordReader.tokenize(record().replace("/","")));
        for(int i=4;i<10;i++){var bad=new ArrayList<>(tokens);bad.set(i,"999");assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,String.join(" ",bad)+" /"));}
        var missing=new ArrayList<>(tokens);missing.removeLast();var extra=new ArrayList<>(tokens);extra.add("0");
        for(String text:List.of(String.join(" ",missing)+" /",String.join(" ",extra)+" /",record().replace("'USRMDL' '1' 'REGFMC1U' 101 1 3 34 11 27","'REGFMC1U' '1'")))
            assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,text));
    }
    @Test void sourceNameAndMalformedNumericFieldsAreNotGuessed(@TempDir Path dir) throws Exception {
        for(String text:List.of(record().replace("REGFMC1U","REGFMC1"),record().replace(" 0.35 "," NaN "),record().replace(" 0.025 "," bad "),record().replace("27 1 0 1","27 1.5 0 1")))
            assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,text));
    }
    @Test void inactiveMissingAndExistingHostsAreNotOverwritten(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();gen(b).setStatus(false);assertThrows(InterpssException.class,()->parse(b,dir,record()));
        assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createWithMachine(),dir,record()));
        assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,record().replace("'1' 'REGFMC1U'","'9' 'REGFMC1U'")));
        var duplicate=DStabBuilderTestFixture.createBuilder();parse(duplicate,dir,record());var original=gen(duplicate).getDynamicGenDevice();
        assertThrows(InterpssException.class,()->parse(duplicate,dir,record()));assertSame(original,gen(duplicate).getDynamicGenDevice());
    }
    @Test void nonunitMvaNortonBoundaryPreservesPowerAndJointCurrentCap(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();gen(b).setMvaBase(50);gen(b).setGen(new Complex(.2,.1));parse(b,dir,record());
        var m=assertInstanceOf(Regfmc1Model.class,gen(b).getDynamicGenDevice());assertTrue(m.initStates(m.getDStabBus()));
        Complex v=m.getDStabBus().getVoltage(),ySource=v.divide(gen(b).getPosGenZ().multiply(gen(b).getZMultiFactor()));
        Complex physical=((Complex)m.getOutputObject()).subtract(ySource);assertEquals(0,v.multiply(physical.conjugate()).subtract(new Complex(.2,.1)).abs(),1e-12);
        assertEquals(0,m.getNamedStates().get("GfmPmeas"));assertEquals(.4,m.getActivePower());assertEquals(.2,m.getReactivePower());
        m.getDStabBus().setVoltage(new Complex(.01,.01));v=m.getDStabBus().getVoltage();var states=m.getNamedStates();
        physical=((Complex)m.getOutputObject()).subtract(v.divide(gen(b).getPosGenZ().multiply(gen(b).getZMultiFactor())));
        assertEquals(.6,physical.abs(),1e-12);assertEquals(states,m.getNamedStates());
    }
    @Test void transformersAndExplicitRemovalAreRejectedOrSkipped(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();gen(b).setXfrZ(new Complex(0,.01));assertThrows(InterpssException.class,()->parse(b,dir,record()));
        for(boolean gnet:new boolean[]{false,true}) {
            var removed=DStabBuilderTestFixture.createBuilder();var p=new PSSEDStabDirectParser(removed).setIgnoreMissingHosts(false).setStrictImport(true);
            var keys=Set.of(new org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey("Bus1","1"));
            if(gnet)p.setGnetRemovedGenerators(keys);else p.setModelRemovedGenerators(keys);
            Path file=dir.resolve("removed.dyr");Files.writeString(file,record());p.parseDynFile(file.toString());assertNull(gen(removed).getDynamicGenDevice());
        }
    }
    @Test void unsupportedPlantIsNotSilentlyAliasedToRepca(@TempDir Path dir) throws Exception {
        String plant=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr")).lines().filter(s->s.contains("'REPCA1'")).findFirst().orElseThrow()+"\n";
        for(String text:List.of(plant+record(),record()+plant))assertThrows(InterpssException.class,()->parse(DStabBuilderTestFixture.createBuilder(),dir,text));
    }
    @Test void tripFreezesStatesAndInvalidBasesAndMethodsAreRejected(@TempDir Path dir) throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();parse(b,dir,record());var m=assertInstanceOf(Regfmc1Model.class,gen(b).getDynamicGenDevice());assertTrue(m.initStates(m.getDStabBus()));
        assertFalse(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,3));assertThrows(IllegalArgumentException.class,()->m.setReferenceOffsets(Double.NaN,0,0,0,0,0));
        assertFalse(m.nextStep(.0005,null,0));m.setReferenceOffsets(0,0,0,0,.1,.1);
        var state=m.getNamedStates();gen(b).setStatus(false);assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));assertEquals(state,m.getNamedStates());
        assertEquals(Complex.ZERO,m.getOutputObject());assertEquals(0,m.getActivePower());assertEquals(0,m.getReactivePower());
        for(double base:new double[]{0,Double.NaN,Double.POSITIVE_INFINITY}){var bad=DStabBuilderTestFixture.createBuilder();gen(bad).setMvaBase(base);parse(bad,dir,record());assertFalse(gen(bad).getDynamicGenDevice().initStates(bad.getDStabNetwork().getBus("Bus1")));}
    }
}
