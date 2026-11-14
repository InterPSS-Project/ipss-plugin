package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabGen;

/** PSS/E 36.7 table/syntax, not the distinct Texas2k 19-value exchange. */
public class PsseRegfma1NativeSchemaTest extends CorePluginTestSetup {
    private static final String CONSTANTS =
            ".01 .012 .014 1.5 1.5 .5 .9 -.9 .8 -.8 .02 .03 .01 .1 .1 20 .25 5.86";

    @Test void twentyValuesMapBothFlagsAndRawMachineBaseImpedance(@TempDir Path directory)
            throws Exception {
        for (int vflag : new int[]{0,1}) for (int qvflag : new int[]{0,1}) {
            var builder=fixture(new Complex(.015,.23));
            var parser=parse(builder,directory,"native-"+vflag+qvflag,vflag+" "+qvflag+" "+CONSTANTS);
            var gen=generator(builder);
            var model=assertInstanceOf(Regfma1Model.class,gen.getDynamicGenDevice());
            assertEquals(vflag,model.getData().vflag());
            assertEquals(qvflag,model.getData().qvflag());
            assertEquals(.015,model.getData().re(),0);
            assertEquals(.23,model.getData().xe(),0);
            assertEquals(new Complex(.015,.23),gen.getPosGenZ());
            assertEquals(new Complex(.015,.23),gen.getSourceZ());
            assertEquals(.01,model.getData().tpf(),0);
            assertEquals(.012,model.getData().tqf(),0);
            assertEquals(.014,model.getData().tvf(),0);
            assertEquals(.25,model.getData().kpv(),0);
            assertEquals(5.86,model.getData().kiv(),0);
            var d=model.getData();
            assertArrayEquals(new double[]{.01,.012,.014,1.5,1.5,.5,.9,-.9,.8,-.8,.02,.03,.01,.1,.1,20,.25,5.86},
                    new double[]{d.tpf(),d.tqf(),d.tvf(),d.imax(),d.emax(),d.emin(),d.pmax(),d.pmin(),
                            d.qmax(),d.qmin(),d.mp(),d.mq(),d.kppmax(),d.kipmax(),d.kpqmax(),d.kiqmax(),d.kpv(),d.kiv()},0);
            assertEquals(50,gen.getMvaBase(),0);
            assertTrue(parser.getLastImportReport().entries().get(0).message().contains("PSS/E 36.7"));
            assertTrue(model.initStates(model.getDStabBus()));
            var unchanged=model.getNamedStates();
            var nativeStates=model.getPsseStateSnapshot();
            assertEquals(9,nativeStates.size());
            assertEquals(.4,nativeStates.get("Active power measurement transducer"),1e-12);
            assertEquals(.2,nativeStates.get("Reactive power measurement transducer"),1e-12);
            assertEquals(vflag==0 ? 0.0 : model.getVoltageIntegral(),
                    nativeStates.get("Terminal voltage PI integrator"),0);
            var nativeVars=model.getPsseVariableSnapshot();
            assertEquals(8,nativeVars.size());
            assertEquals(qvflag==0 ? (vflag==0 ? model.getInternalVoltage() : model.getMeasuredVoltage()) : 0.0,
                    nativeVars.get("Fixed voltage reference"),1e-12);
            assertEquals(unchanged,model.getNamedStates(),"Native observation cannot change integration states");
            assertThrows(UnsupportedOperationException.class,()->nativeStates.put("other",1.0));
            assertThrows(UnsupportedOperationException.class,()->nativeVars.put("other",1.0));
        }
    }

    @Test void callersCanPinNativeOrLegacyCardinalityWithoutAmbiguousFallback(@TempDir Path directory)
            throws Exception {
        Path legacy=directory.resolve("legacy-pinned.dyr");
        Files.writeString(legacy,"1 'REGFMA1' '1' 0 "+CONSTANTS+" /\n");
        var nativeOnly=new PSSEDStabDirectParser(fixture(new Complex(.015,.23))).setStrictImport(true)
                .setRegfma1RecordFormat(PSSEDStabDirectParser.Regfma1RecordFormat.NATIVE_PSSE_36_7);
        assertThrows(InterpssException.class,()->nativeOnly.parseDynFile(legacy.toString()));
        assertTrue(nativeOnly.getLastImportReport().entries().get(0).message().contains("expected 20"));
        Path nativeDyr=directory.resolve("native-pinned.dyr");
        Files.writeString(nativeDyr,"1 'REGFMA1' '1' 0 1 "+CONSTANTS+" /\n");
        var legacyOnly=new PSSEDStabDirectParser(fixture(new Complex(.015,.23))).setStrictImport(true)
                .setRegfma1RecordFormat(PSSEDStabDirectParser.Regfma1RecordFormat.TEXAS2K_LEGACY);
        assertThrows(InterpssException.class,()->legacyOnly.parseDynFile(nativeDyr.toString()));
        assertTrue(legacyOnly.getLastImportReport().entries().get(0).message().contains("expected 19"));
    }

    @Test void nativeSourceIsNotSilentlyClampedToThePowerWorldReactanceFloor(@TempDir Path directory)
            throws Exception {
        var builder=fixture(new Complex(.015,.00001));
        parse(builder,directory,"small-source","0 0 "+CONSTANTS);
        var model=assertInstanceOf(Regfma1Model.class,generator(builder).getDynamicGenDevice());
        assertEquals(.00001,model.getData().xe(),0);
        assertEquals(new Complex(.015,.00001),generator(builder).getPosGenZ());
    }

    @Test void initializationDoesNotDependOnTheLaterNetworkImpedanceBaseSetup(@TempDir Path directory)
            throws Exception {
        for(double rating:new double[]{50,100,200}) {
            var builder=fixture(new Complex(.015,.23));
            var gen=generator(builder);
            gen.setMvaBase(rating);
            gen.setZMultiFactor(1.0); // untouched core default before the dynamic matrix is formed
            parse(builder,directory,"base-order-"+rating,"0 0 "+CONSTANTS);
            var model=(Regfma1Model)gen.getDynamicGenDevice();
            assertTrue(model.initStates(model.getDStabBus()));
            gen.setZMultiFactor(builder.getDStabNetwork().getBaseMva()/rating);
            Complex voltage=model.getDStabBus().getVoltage();
            Complex current=((Complex)model.getOutputObject()).subtract(
                    voltage.divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
            assertEquals(0.0,voltage.multiply(current.conjugate()).subtract(gen.getGen()).abs(),1e-12,
                    "Initialization must use the final network-base source impedance at MVA="+rating);
        }
    }

    @Test void legacyNineteenValuesKeepTheirDeclaredCompatibilityInterface(@TempDir Path directory)
            throws Exception {
        var builder=fixture(new Complex(.015,.23));
        var parser=parse(builder,directory,"legacy","0 "+CONSTANTS);
        var model=assertInstanceOf(Regfma1Model.class,generator(builder).getDynamicGenDevice());
        assertEquals(0,model.getData().qvflag());
        assertEquals(0,model.getData().re(),0);
        assertEquals(.15,model.getData().xe(),0);
        assertTrue(parser.getLastImportReport().entries().get(0).message().contains("legacy 19-value"));
    }

    @Test void nativeInternalVoltageVarRemainsBeforeTheActiveCurrentLimit(@TempDir Path directory)
            throws Exception {
        var builder=fixture(new Complex(.015,.23));
        parse(builder,directory,"active-limit-observation","0 0 "+CONSTANTS.replace("1.5 1.5 .5",".5 1.5 .5"));
        var model=(Regfma1Model)generator(builder).getDynamicGenDevice();
        assertTrue(model.initStates(model.getDStabBus()));
        model.getDStabBus().setVoltage(new Complex(.85,0));
        model.getOutputObject();
        assertTrue(model.isCurrentLimited(),"Discriminator must activate Imax");
        var before=model.getNamedStates();
        var variables=model.getPsseVariableSnapshot();
        assertEquals(model.getInternalVoltage(),variables.values().stream().skip(1).findFirst().orElseThrow(),1e-12,
                "Native L+1 retains pre-limit Edroop, not the equivalent limited Norton voltage");
        assertEquals(.5,variables.get("Terminal current magnitude on machine base"),1e-12);
        assertEquals(before,model.getNamedStates());
    }

    @Test void nativeVoltagePiContinuesIntegratingWhenOnlyTheOutputIsLimited(@TempDir Path directory)
            throws Exception {
        var model=nativeNarrowVoltageModel(directory,"voltage-upper");
        double initial=model.getVoltageIntegral();
        model.setReferenceOffsets(0,.1);
        assertTrue(model.nextStep(.001,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
        assertEquals(initial+.001*5.86*.1,model.getVoltageIntegral(),1e-12,
                "Native s8 is limited independently of Kpv*error+s8");
        assertEquals(1.10,model.getInternalVoltage(),1e-12);
    }

    @Test void nativeVoltagePiClampsAndUnwindsAtBothIntegralBounds(@TempDir Path directory)
            throws Exception {
        var model=nativeNarrowVoltageModel(directory,"voltage-bounds");
        model.setReferenceOffsets(0,.1);
        for(int step=0;step<100;step++) {
            assertTrue(model.nextStep(.01,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
            assertTrue(model.nextStep(.01,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,1));
            assertTrue(model.getVoltageIntegral()<=1.10);
        }
        assertEquals(1.10,model.getVoltageIntegral(),1e-12);
        model.setReferenceOffsets(0,-.1);
        assertTrue(model.nextStep(.001,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(model.getVoltageIntegral()<1.10,"Upper integral limit must allow inward motion");
        assertEquals(1.085,model.getInternalVoltage(),1e-12);
        assertTrue(model.nextStep(.001,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,1));
        for(int step=0;step<100;step++) {
            assertTrue(model.nextStep(.01,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
            assertTrue(model.nextStep(.01,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,1));
            assertTrue(model.getVoltageIntegral()>=1.085);
        }
        assertEquals(1.085,model.getVoltageIntegral(),1e-12);
        model.setReferenceOffsets(0,.1);
        assertTrue(model.nextStep(.001,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(model.getVoltageIntegral()>1.085,"Lower integral limit must allow inward motion");
    }

    private static Regfma1Model nativeNarrowVoltageModel(Path directory,String name) throws Exception {
        var builder=fixture(new Complex(.015,.23));
        parse(builder,directory,name,"1 1 "+CONSTANTS.replace("1.5 .5","1.10 1.085"));
        var model=(Regfma1Model)generator(builder).getDynamicGenDevice();
        assertTrue(model.initStates(model.getDStabBus()));
        assertTrue(model.getVoltageIntegral()>1.085 && model.getVoltageIntegral()<1.10);
        return model;
    }

    @Test void nativeInvalidFieldsDoNotDefaultOrPartiallyAttach(@TempDir Path directory) throws Exception {
        String[] invalid={"0 0 "+CONSTANTS+" 999", "0 0 "+CONSTANTS.replace("5.86","NaN"),
                "0 0 "+CONSTANTS.replace(".012","bad"),"0.5 0 "+CONSTANTS,
                "0 2 "+CONSTANTS,"0 0 "+CONSTANTS.replace(".9 -.9","-.9 .9")};
        for(int i=0;i<invalid.length;i++) {
            var builder=fixture(new Complex(.015,.23));
            final int profile=i;
            assertThrows(InterpssException.class,()->parse(builder,directory,"invalid-"+profile,invalid[profile]));
            assertNull(generator(builder).getDynamicGenDevice());
        }
    }

    @Test void nativeRequiresAUsableRawSourceAndNoImplicitTransformer(@TempDir Path directory) throws Exception {
        var missing=fixture(Complex.ZERO);
        assertThrows(InterpssException.class,()->parse(missing,directory,"zero-source","0 0 "+CONSTANTS));
        assertNull(generator(missing).getDynamicGenDevice());
        var transformer=fixture(new Complex(.015,.23));
        generator(transformer).setXfrZ(new Complex(0,.1));
        assertThrows(InterpssException.class,()->parse(transformer,directory,"implicit-transformer","0 0 "+CONSTANTS));
        assertNull(generator(transformer).getDynamicGenDevice());
    }

    private static DStabNetworkBuilder fixture(Complex source) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var gen=generator(builder);
        gen.setSourceZ(source);gen.setPosGenZ(source);
        gen.setMvaBase(50);gen.setZMultiFactor(builder.getDStabNetwork().getBaseMva()/50);
        gen.setGen(new Complex(.2,.1));
        return builder;
    }
    private static DStabGen generator(DStabNetworkBuilder builder) {
        return (DStabGen)builder.getDStabNetwork().getDStabBus("Bus1").getContributeGen("1");
    }
    private static PSSEDStabDirectParser parse(DStabNetworkBuilder builder,Path directory,String name,String values)
            throws Exception {
        Path path=directory.resolve(name+".dyr");
        Files.writeString(path,"1 'REGFMA1' '1' "+values+" /\n");
        var parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(path.toString());
        return parser;
    }
}
