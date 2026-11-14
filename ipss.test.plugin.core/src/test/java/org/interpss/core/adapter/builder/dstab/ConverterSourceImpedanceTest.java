package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.ConverterCompensationPort;

/** Current-source and effective-reactance variants retain distinct physical contracts. */
class ConverterSourceImpedanceTest extends CorePluginTestSetup {
    private static Regca1Data controls() {
        return new Regca1Data(0,.02,999.,.9,.4,1.22,10.,.8,.4,-99.,.02,0.,999.,-999.,.8);
    }

    @Test void exactWrapperRetainsSeparateXeAndRawSource(@TempDir Path directory) throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var generator=(DStabGen)builder.getDStabNetwork().getBus("Bus1").getContributeGen("1");
        Complex original=generator.getPosGenZ();
        Path file=directory.resolve("variant.dyr");
        Files.writeString(file,"1 'USRMDL' '1' 'REGCAU2' 101 1 1 15 3 4 "
                +"0 .02 999 .9 .4 1.22 10 .8 .4 -99 .02 0 999 -999 .8 .2 /\n");
        assertNotNull(new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(file.toString()));
        var model=assertInstanceOf(Regca1Model.class,generator.getDynamicGenDevice());
        assertEquals("REGCAU2",model.getModelName());
        assertEquals(.2,model.getEffectiveReactance());
        assertEquals(original,generator.getPosGenZ());
        assertNotNull(model.getActiveElectricalController());
        assertEquals("REGCAU2",DynamicModelCatalog.canonicalName("REGCAU2"));
        assertEquals(22,DynamicModelCatalog.find("REGCAU2").orElseThrow().recordSchema().primaryParameterCount());
    }

    @Test void rejectsWrongAllocationAndForeignFlatExtension(@TempDir Path directory) throws Exception {
        for(String record:new String[]{
                "1 'USRMDL' '1' 'REGCAU2' 101 1 1 14 3 4 0 .02 999 .9 .4 1.22 10 .8 .4 -99 .02 0 999 -999 .8 .2 /",
                "1 'REGCA1' '1' 0 .02 999 .9 .4 1.22 10 .8 .4 -99 .02 0 999 -999 .8 .2 /",
                "1 'REGCAU2' '1' 0 .02 999 .9 .4 1.22 10 .8 .4 -99 .02 0 999 -999 .8 .2 /"}) {
            var builder=DStabBuilderTestFixture.createBuilder();Path file=directory.resolve("invalid.dyr");
            Files.writeString(file,record+"\n");
            assertThrows(Exception.class,()->new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(file.toString()));
            assertNull(((DStabGen)builder.getDStabNetwork().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());
        }
    }

    @Test void physicalXeSurvivesVoltageCollapseAndPreservesInitialEquilibrium() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        var model=builder.addRegcau2("Bus1","1",new Regcau2Data(controls(),.2));
        var bus=model.getDStabBus();bus.setVoltage(new Complex(1.04,0.));
        model.getParentGen().setGen(new Complex(.7,.2));
        model.setActiveElectricalController(new ConverterCurrentCommands());
        assertTrue(model.initStates(bus));
        double initialQ=.2/1.04,voltageState=1.04+.2*initialQ;
        assertEquals(voltageState,model.getIqRegulatorState(),1e-12);
        assertEquals(initialQ,model.getIq(),1e-12);
        assertTrue(model.getIntegrationStates().containsKey("Reactive Voltage Regulator"));
        assertTrue(model.getNamedStates().containsKey("Reactive Voltage Regulator"));
        assertFalse(model.supportsUncompensatedNetworkSolve());
        assertFalse(model.supportsDeferredCurrentPublication());
        assertThrows(IllegalArgumentException.class,()->model.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP));
        bus.setVoltage(new Complex(.6,0.));
        assertEquals((voltageState-.6)/.2,model.getIq(),1e-12);
        double before=model.getIqRegulatorState();
        assertTrue(model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
        assertEquals(before,model.getIqRegulatorState(),1e-12,"Fixed input retains the initialized voltage command");
        assertTrue(model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));
        assertTrue(model.getStateDerivativeSnapshot().rates().containsKey("Reactive Voltage Regulator"));
    }

    @Test void zeroXeUsesExistingCurrentKernelWithoutChangingSourceData() throws Exception {
        var one=DStabBuilderTestFixture.createBuilder().addRegca1("Bus1","1",controls());
        var two=DStabBuilderTestFixture.createBuilder().addRegcau2("Bus1","1",new Regcau2Data(controls(),0.));
        for(var model:new Regca1Model[]{one,two}) {
            model.getParentGen().setGen(new Complex(.7,.2));
            model.setActiveElectricalController(new ConverterCurrentCommands());assertTrue(model.initStates(model.getDStabBus()));
        }
        for(double v:new double[]{1.04,.6,1.1,0.,.9}) {
            for(var model:new Regca1Model[]{one,two}) {
                model.getDStabBus().setVoltage(new Complex(v,0.));
                assertTrue(model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
                assertTrue(model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));
            }
            assertEquals(one.getIntegrationStates(),two.getIntegrationStates());
            assertEquals(one.getOutputObject(),two.getOutputObject());
        }
        assertTrue(one.supportsUncompensatedNetworkSolve());assertFalse(two.supportsUncompensatedNetworkSolve());
    }

    @Test void voltageSourceModelRetainsImpedanceAndHasNoCancellationPort() throws Exception {
        var model=DStabBuilderTestFixture.createBuilder().addRegcb1("Bus1","1",
                new Regcb1Data(0,1,.02,.02,999.,-999.,999.,.01,1.3));
        assertFalse(ConverterCompensationPort.class.isInstance(model));
        var source=new Complex(0.,1.);model.getParentGen().setPosGenZ(source);
        model.getParentGen().setGen(new Complex(.7,.2));assertTrue(model.initStates(model.getDStabBus()));
        model.getDStabBus().setVoltage(new Complex(.6,0.));
        assertEquals(source,model.getParentGen().getPosGenZ());
        assertTrue(((Complex)model.getOutputObject()).abs()>0.);
        assertTrue(DynamicModelCatalog.find("REGCCU").isEmpty(),"An unsupported voltage-source model must not alias REGCA1");
    }

    @Test void invalidExplicitReactanceIsRejected() {
        for(double value:new double[]{-1.,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new Regcau2Data(controls(),value));
    }

    @Test void currentCoordinateEigenanalysisRejectsEffectiveReactance() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();
        builder.addRegcau2("Bus1","1",new Regcau2Data(controls(),.2));
        var error=assertThrows(IllegalArgumentException.class,()->
                org.interpss.dstab.analysis.LocalRenewableQvEigenAnalyzer.analyze(
                        builder.getDStabNetwork(),java.util.List.of("Bus1")));
        assertTrue(error.getMessage().contains("REGCAU2 Xe"));
    }

    @Test void electricalCurrentCommandAndFixedVoltageCommandHaveDistinctBridges() {
        var fixed=new ConverterCurrentCommands();fixed.initialize(.7,.2,1.04);
        assertEquals(1.04+.2*(.2/1.04),fixed.getReactiveVoltageCommand(.6,.2,-1.),1e-12);
        RenewableElectricalController electrical=new RenewableElectricalController() {
            @Override public void initialize(double p,double q,double v) { }
            @Override public void step(double dt,double p,double q,double v,double f) { }
            @Override public double getIpcmd(){return .7;}
            @Override public double getIqcmd(){throw new AssertionError("The captured command must be used");}
            @Override public Repca1Model getPlantController(){return null;}
            @Override public void setPlantController(Repca1Model plant){ }
        };
        assertEquals(.8,electrical.getReactiveVoltageCommand(.6,.2,-1.),1e-12);
    }
}
