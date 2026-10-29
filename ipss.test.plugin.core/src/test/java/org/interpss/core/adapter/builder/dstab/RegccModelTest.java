package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.ConverterCompensationPort;

/** Native schema, physical interface, state equations, integration and active boundaries. */
class RegccModelTest extends CorePluginTestSetup {
    private static final String RECORD="1 'REGCC' '1' 0 0 9999 -9999 .01 100 1.2 .01 .5 5 20 100 100 -100 .09 /\n";
    private static RegccData data(int rate,int pq,double freeze){return new RegccData(rate,pq,.1,-.1,.01,.1,1.2,.0001,.5,5.,20.,100.,.1,-.1,freeze);}
    private RegccModel model(RegccData data,double p,double q)throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();var m=b.addRegcc("Bus1","1",data);
        m.getParentGen().setPosGenZ(new Complex(.01,.1));m.getParentGen().setGen(new Complex(p,q));
        m.getDStabBus().setVoltage(new Complex(1.,0.));m.configureIntegrationStep(.0005);assertTrue(m.initStates(m.getDStabBus()));return m;
    }
    @Test void exactCatalogAndParserKeepPhysicalSource(@TempDir Path dir)throws Exception {
        var b=DStabBuilderTestFixture.createBuilder();var g=(DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1");
        var source=g.getPosGenZ();var file=dir.resolve("model.dyr");Files.writeString(file,RECORD);
        assertNotNull(new PSSEDStabDirectParser(b).setStrictImport(true).parseDynFile(file.toString()));
        var m=assertInstanceOf(RegccModel.class,g.getDynamicGenDevice());assertEquals(source,g.getPosGenZ());
        assertEquals(15,DynamicModelCatalog.find("REGCC").orElseThrow().parameterCount());
        assertTrue(DynamicModelCatalog.find("REGCCU").isEmpty());assertFalse(ConverterCompensationPort.class.isInstance(m));
    }
    @Test void malformedOrForeignSchemasAreRejectedWithoutAttachment(@TempDir Path dir)throws Exception {
        for(String text:new String[]{RECORD.replace("0 0 9999","2 0 9999"),RECORD.replace(" 100 100 -100 .09"," 100 100 -100"),
                RECORD.replace(".09 /",".09 .2 /"),RECORD.replace(" .5 5 "," NaN 5 "),RECORD.replace("'REGCC'","'REGCC1'")}) {
            var b=DStabBuilderTestFixture.createBuilder();var f=dir.resolve("invalid.dyr");Files.writeString(f,text);
            assertThrows(Exception.class,()->new PSSEDStabDirectParser(b).setStrictImport(true).parseDynFile(f.toString()));
            assertNull(((DStabGen)b.getDStabNetwork().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());
        }
    }
    @Test void initializationIsEquilibriumAndNortonPairRetainsImpedance()throws Exception {
        var m=model(data(0,0,.09),.7,.2);var x=m.getStateSnapshot();
        assertEquals(9,m.getNamedStates().size());assertEquals(m.getNamedStates(),m.getIntegrationStates());
        for(double rate:m.getStateDerivativeSnapshot().rates().values())assertEquals(0.,rate,1e-12);
        assertEquals(.7,x[1],1e-12);assertEquals(.2,x[0],1e-12);assertEquals(.001,m.getEffectiveTe());
        Complex physical=((Complex)m.getOutputObject()).subtract(Complex.ONE.divide(m.getParentGen().getPosGenZ()));
        assertEquals(.7,physical.getReal(),1e-12);assertEquals(-.2,physical.getImaginary(),1e-12);
        assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));
        assertArrayEquals(x,m.getStateSnapshot(),1e-12);
        assertThrows(IllegalArgumentException.class,()->m.nextStep(.0005,DynamicSimuMethod.RUNGE_KUTTA,0));
    }
    @Test void rateLimitsAndVoltageFreezeAreAppliedToCorrectCoordinates()throws Exception {
        for(double q:new double[]{.2,-.2}) {
            var m=model(data(0,0,.99),.7,q);var x=m.getStateSnapshot();
            var dx=m.evaluateDerivatives(x,new Complex(.5,.2),new Complex(.3,-.1),2.,Math.copySign(2.,q));
            assertEquals(Math.copySign(.1,q),dx[3],1e-12);assertEquals(.1,dx[4],1e-12);
            // Above the freeze threshold the integral advances in per-unit frequency.
            assertEquals(100.*.2/(2.*Math.PI*m.getDStabBus().getNetwork().getFrequency()),dx[7],1e-12);
            x[2]=.5;dx=m.evaluateDerivatives(x,new Complex(.5,.2),Complex.ZERO,2.,q);
            assertEquals(0.,dx[7]);assertEquals(.1,dx[8]);
        }
        var charging=model(data(0,0,.09),-.7,-.2);
        var dx=charging.evaluateDerivatives(charging.getStateSnapshot(),Complex.ONE,new Complex(-.7,.2),-2.,-2.);
        assertEquals(-.1,dx[4]);
    }
    @Test void currentPriorityBlockingAndBaseConversionRemainPhysical()throws Exception {
        for(int priority:new int[]{0,1}) {
            var m=model(data(0,priority,.09),.7,.2);var before=m.getStateSnapshot();
            m.getDStabBus().setVoltage(new Complex(.1,0.));
            Complex z=m.getParentGen().getPosGenZ(),v=m.getDStabBus().getVoltage();
            Complex i=((Complex)m.getOutputObject()).subtract(v.divide(z));
            assertEquals(1.2,i.abs(),1e-12);
            if(priority==0)assertEquals(-1.2,i.getImaginary(),1e-12);else assertEquals(1.2,i.getReal(),1e-12);
            m.setActiveElectricalController(new RenewableElectricalController(){
                public void initialize(double p,double q,double v){}
                public void step(double dt,double p,double q,double v,double f){}
                public double getIpcmd(){return .7;}
                public double getIqcmd(){return -.2;}
                public boolean isConverterBlocked(){return true;}
                public Repca1Model getPlantController(){return null;}
                public void setPlantController(Repca1Model plant){}
            });
            assertEquals(v.divide(z),m.getOutputObject());assertArrayEquals(before,m.getStateSnapshot());
        }
        var b=DStabBuilderTestFixture.createBuilder();var m=b.addRegcc("Bus1","1",data(0,0,.09));
        m.getParentGen().setMvaBase(50.);m.getParentGen().setGen(new Complex(.3,.1));assertTrue(m.initStates(m.getDStabBus()));
        assertEquals(.6/m.getDStabBus().getVoltageMag(),m.getStateSnapshot()[1],1e-12);
    }
    @Test void inactiveGeneratorRetainsHistoryAndCanResume()throws Exception {
        var m=model(data(0,0,.09),.7,.2);var state=m.getStateSnapshot();
        m.getParentGen().setStatus(false);
        assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
        assertArrayEquals(state,m.getStateSnapshot());assertEquals(Complex.ZERO,m.getOutputObject());
        assertTrue(m.updateAttributes(false));assertEquals(0.,m.getP());assertEquals(0.,m.getQ());
        m.getParentGen().setStatus(true);assertNotEquals(Complex.ZERO,m.getOutputObject());assertArrayEquals(state,m.getStateSnapshot());
    }
}
