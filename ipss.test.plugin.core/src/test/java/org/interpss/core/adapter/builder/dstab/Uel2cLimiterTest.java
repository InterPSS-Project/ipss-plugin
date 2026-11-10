package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st1c.St1cData;
import org.interpss.dstab.control.uel.psse.uel2c.Uel2cData;
import org.interpss.dstab.control.uel.psse.uel2c.Uel2cUnderExcitationLimiter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Schema, equation, limiter, and named-state tests for UEL2C. */
public class Uel2cLimiterTest extends CorePluginTestSetup {
    @Test void initialPowerFiltersUseGeneratorMvaBase() throws Exception {
        var b=fixtureBuilder();var bus=b.getDStabNetwork().getDStabBus("Bus1");
        var gen=(com.interpss.dstab.DStabGen)bus.getContributeGen("1");
        gen.setMvaBase(200);gen.setGen(new org.apache.commons.math3.complex.Complex(.8,.2));
        gen.getMach().setRating(200,org.interpss.numeric.datatype.Unit.UnitType.mVA,bus.getNetwork().getBaseKva());
        bus.setVoltage(org.apache.commons.math3.complex.Complex.ONE);
        var u=b.addUel2c("Bus1","1",data());assertTrue(u.initStates(bus));
        assertEquals(.4,u.getNamedStates().get("Real power filter"),1e-12);
        assertEquals(.1,u.getNamedStates().get("Reactive power filter"),1e-12);
        assertTrue(u.nextStepWithSignals(.001,DynamicSimuMethod.MODIFIED_EULER,0,1,.4,.1,0,0));
        assertTrue(u.nextStepWithSignals(.001,DynamicSimuMethod.MODIFIED_EULER,1,1,.4,.1,0,0));
        assertEquals(.4,u.getNamedStates().get("Real power filter"),1e-12);
        assertEquals(.1,u.getNamedStates().get("Reactive power filter"),1e-12);
    }
    @Test void parsesFlatAndExactWrapperAllocations(@TempDir Path dir)throws Exception{
        String payload=" 2 2 1 2 .02 .03 .025 .4 .8 .5 -.2 0 0 .08 .02 .07 .015 .06"
                +" -.2 .0125 .2 .0125 .6 .0125 1 .0125 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
                +" .6 -.3 .95 .9 .1 .04 .55 -.25 .5 -.2 .6 /\n";
        for(boolean wrapper:new boolean[]{false,true}){
            Path dyr=dir.resolve(wrapper?"wrapper.dyr":"flat.dyr");
            String head=wrapper?"1 'USRMDL' '1' 'UEL2CU1' 9 0 4 47 9 2":"1 'UEL2C' '1'";
            Files.writeString(dyr,head+payload);DStabNetworkBuilder b=fixtureBuilder();
            PSSEDStabDirectParser parser=new PSSEDStabDirectParser(b).setStrictImport(true);parser.parseDynFile(dyr.toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());
            assertEquals(1,b.getDStabNetwork().getBus("Bus1").getDynamicBusDeviceList().stream()
                    .filter(Uel2cUnderExcitationLimiter.class::isInstance).count());
        }
        Path invalid=dir.resolve("invalid.dyr");
        Files.writeString(invalid,"1 'USRMDL' '1' 'UEL2CU1' 9 0 4 47 8 2"+payload);
        assertThrows(InterpssException.class,()->new PSSEDStabDirectParser(fixtureBuilder()).setStrictImport(true).parseDynFile(invalid.toString()));
        var descriptor=DynamicModelCatalog.find("UEL2CU1").orElseThrow();assertEquals("UEL2C",descriptor.canonicalName());
        assertEquals(Set.of(51,57),descriptor.recordSchema().acceptedParameterCounts());
    }

    @Test void advancesAllPublishedStatesAndHonorsOutputBounds()throws Exception{
        DStabNetworkBuilder b=fixtureBuilder();Uel2cUnderExcitationLimiter u=b.addUel2c("Bus1","1",data());
        assertNotNull(u);u.initializeWithSignals(1,.5,.0125,0,0);
        assertEquals(Set.of("Voltage filter","Real power filter","Reactive power filter","Integrator",
                "Reference feedback","First lead-lag","Second lead-lag","Reactive power reference","Adjustable gain"),u.getNamedStates().keySet());
        for(int i=0;i<1000;i++){
            double t=i*.001,vt=.92+.05*Math.sin(3*t),p=.45+.1*Math.cos(2*t),q=-.05+.03*Math.sin(5*t);
            assertTrue(u.nextStepWithSignals(.001,DynamicSimuMethod.MODIFIED_EULER,0,vt,p,q,.01,.02));
            assertTrue(u.nextStepWithSignals(.001,DynamicSimuMethod.MODIFIED_EULER,1,vt,p,q,.01,.02));
        }
        assertEquals(9,u.getStateSnapshot().length);assertTrue(u.getOutput()>=-.3&&u.getOutput()<=.6);
        assertTrue(u.getStateSnapshot()[3]>=-.2-1e-12&&u.getStateSnapshot()[3]<=.5+1e-12);
    }

    @Test void implementsVoltageBiasCurveModesAndDirectionalAntiWindup()throws Exception{
        DStabNetworkBuilder b=fixtureBuilder();Uel2cUnderExcitationLimiter u=b.addUel2c("Bus1","1",data());
        u.initializeWithSignals(.8,.5,-.1,0,0);double initial=u.getStateSnapshot()[3];
        for(int i=0;i<20;i++){
            assertTrue(u.nextStepWithSignals(.01,DynamicSimuMethod.MODIFIED_EULER,0,.8,.5,-.1,0,0));
            assertTrue(u.nextStepWithSignals(.01,DynamicSimuMethod.MODIFIED_EULER,1,.8,.5,-.1,0,0));
        }
        assertTrue(u.getStateSnapshot()[3]>initial,"integrator must be allowed to move inward from its lower boundary");
        assertTrue(Double.isFinite(u.getNormalizedReactiveReference()));
    }

    private static DStabNetworkBuilder fixtureBuilder()throws Exception{return fixtureBuilder(false);}
    private static DStabNetworkBuilder fixtureBuilder(boolean typeJ)throws Exception{
        DStabNetworkBuilder b=typeJ?DStabBuilderTestFixture.createBuilder():DStabBuilderTestFixture.createWithMachine();
        if(typeJ)b.addGentpj1("Bus1","1",100,16.5,new org.interpss.dstab.mach.Gentpj1Data(
                6.8,.042,.65,.083,4.7,.18,2.12,1.38,.34,.52,.21,.19,.13,.085,.34,0));
        St1cData d=new St1cData();
        d.setUel(1);d.setVos(1);d.setOel(0);d.setTr(0);d.setVimax(9);d.setVimin(-9);d.setTc(0);d.setTb(0);
        d.setTc1(0);d.setTb1(0);d.setKa(1);d.setTa(0);d.setVamax(9);d.setVamin(-9);d.setVrmax(9);d.setVrmin(-9);
        d.setKc(0);d.setKf(0);d.setTf(0);d.setKlr(0);d.setIlr(99);assertNotNull(b.addExcSt1c("Bus1","1",d));return b;
    }
    private static Uel2cData data(){return new Uel2cData(2,2,1,2,.02,.03,.025,.4,.8,.5,-.2,0,0,.08,.02,.07,.015,.06,
            -.2,.0125,.2,.0125,.6,.0125,1,.0125,0,0,0,0,0,0,0,0,0,0,0,0,0,0,.6,-.3,.95,.9,.1,.04,.55,-.25,.5,-.2,.6);}
    @Test void zeroGainSelectorUsesFixedGainAndDoesNotAdvanceTheAdjustableGainState() throws Exception {
        var original=data();var components=Uel2cData.class.getRecordComponents();
        Object[] values=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
        for(int i=0;i<components.length;i++){values[i]=components[i].getAccessor().invoke(original);types[i]=components[i].getType();}
        values[3]=0;var zero=Uel2cData.class.getConstructor(types).newInstance(values);
        values[3]=1;var one=Uel2cData.class.getConstructor(types).newInstance(values);
        var a=fixtureBuilder().addUel2c("Bus1","1",zero);
        var b=fixtureBuilder().addUel2c("Bus1","1",one);
        a.initializeWithSignals(.9,.5,-.1,0,0);b.initializeWithSignals(.9,.5,-.1,0,0);
        for(int step=0;step<100;step++)for(int stage=0;stage<2;stage++) {
            assertTrue(a.nextStepWithSignals(.001,DynamicSimuMethod.MODIFIED_EULER,stage,.9,.5,-.2,0,0));
            assertTrue(b.nextStepWithSignals(.001,DynamicSimuMethod.MODIFIED_EULER,stage,.9,.5,-.2,0,0));
            assertArrayEquals(a.getStateSnapshot(),b.getStateSnapshot(),1e-12);
            assertEquals(0,a.getStateSnapshot()[8],0);
            assertEquals(a.getOutput(),b.getOutput(),1e-12);
        }
        assertEquals(0,a.getData().gainMode());
    }


    @ParameterizedTest @ValueSource(strings={"bus","generator","machine","exciter","limiter"})
    void inactiveHostNeverReadsUninitializedMachineOrAdvancesLimiter(String disabled)throws Exception{
        var b=fixtureBuilder(true);var bus=b.getDStabNetwork().getDStabBus("Bus1");
        var machine=b.getDStabNetwork().getMachine("Bus1-mach1");
        // This machine deliberately has no initialized electrical owner. A
        // current read is an error; an inactive auxiliary must not attempt it.
        assertThrows(IllegalStateException.class,machine::getIxy);
        var limiter=b.addUel2c("Bus1","1",data());
        limiter.initializeWithSignals(1,.5,.0125,0,0);
        double[] before=limiter.getStateSnapshot();double output=limiter.getOutput();
        switch(disabled){
            case "bus"->bus.setStatus(false);
            case "generator"->machine.getParentGen().setStatus(false);
            case "machine"->machine.setStatus(false);
            case "exciter"->machine.getExciter().setStatus(false);
            case "limiter"->limiter.setStatus(false);
            default->throw new AssertionError(disabled);
        }
        assertTrue(limiter.initStates(bus));
        assertTrue(limiter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(limiter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,1));
        assertTrue(limiter.afterStep(.01));
        assertDoesNotThrow(()->limiter.getStates(null));
        assertArrayEquals(before,limiter.getStateSnapshot(),0);
        assertEquals(output,limiter.getOutput(),0);
    }


    @ParameterizedTest @ValueSource(strings={"bus","generator","machine","exciter","limiter"})
    void inactiveUel1HostDoesNotReadUninitializedElectricalState(String disabled)throws Exception{
        var b=fixtureBuilder(true);var bus=b.getDStabNetwork().getDStabBus("Bus1");
        var machine=b.getDStabNetwork().getMachine("Bus1-mach1");
        assertThrows(IllegalStateException.class,machine::getIxy);
        var limiter=b.addUel1("Bus1","1",new org.interpss.dstab.control.uel.psse.uel1.Uel1Data(
                1,.8,.1,2,2,.5,.4,1,-1,.02,.1,.03,.12,1,-1));
        limiter.initializeWithError(.2);var before=limiter.getNamedStates();double output=limiter.getOutput();
        switch(disabled){
            case "bus"->bus.setStatus(false);
            case "generator"->machine.getParentGen().setStatus(false);
            case "machine"->machine.setStatus(false);
            case "exciter"->machine.getExciter().setStatus(false);
            case "limiter"->limiter.setStatus(false);
            default->throw new AssertionError(disabled);
        }
        assertTrue(limiter.initStates(bus));
        assertTrue(limiter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(limiter.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,1));
        assertTrue(limiter.afterStep(.01));assertDoesNotThrow(()->limiter.getStates(null));
        assertEquals(before,limiter.getNamedStates());assertEquals(output,limiter.getOutput(),0);
        assertEquals(0,limiter.getError(),0);
    }

}
