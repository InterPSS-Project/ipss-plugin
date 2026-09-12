package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st1c.St1cData;
import org.interpss.dstab.control.oel.psse.oel2c.Oel2cData;
import org.interpss.dstab.control.oel.psse.oel2c.Oel2cOverExcitationLimiter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Exact-schema, equation, boundary, and named-state tests for OEL2C. */
public class Oel2cLimiterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesFlatAndExactWrapperAllocations(@TempDir Path dir)throws Exception{
        String payload=" 1 1 .015 .08 .02 .10 .35 .8 .01 .08 0 -1 0 -1 0 -1 0 .12 .4 .04 .7 .02 1.02 .9 1.45 1.10 .05 1.8 .2 2 .05 .5 -.5 1 -1 .2 1 0 0 -2 2 .1 1 /\n";
        for(boolean wrapper:new boolean[]{false,true}){
            Path dyr=dir.resolve(wrapper?"wrapper.dyr":"flat.dyr");
            String head=wrapper?"1 'USRMDL' '1' 'OEL2CU1' 10 0 2 41 8 8":"1 'OEL2C' '1'";
            Files.writeString(dyr,head+payload);
            DStabNetworkBuilder b=fixtureBuilder();PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);
            p.parseDynFile(dyr.toString());
            assertTrue(p.getLastImportReport().isStrictlyComplete());
            assertEquals(1,b.getDStabNetwork().getBus("Bus1").getDynamicBusDeviceList().stream()
                    .filter(Oel2cOverExcitationLimiter.class::isInstance).count());
        }
        Path invalid=dir.resolve("invalid.dyr");
        Files.writeString(invalid,"1 'USRMDL' '1' 'OEL2CU1' 10 0 2 41 7 8"+payload);
        PSSEDStabDirectParser bad=new PSSEDStabDirectParser(fixtureBuilder()).setStrictImport(true);
        assertThrows(InterpssException.class,()->bad.parseDynFile(invalid.toString()));
        var descriptor=DynamicModelCatalog.find("OEL2CU1").orElseThrow();
        assertEquals("OEL2C",descriptor.canonicalName());assertEquals(Set.of(43,49),descriptor.recordSchema().acceptedParameterCounts());
    }

    @Test void exposesAllEightPublishedStatesAndHonorsLimits()throws Exception{
        DStabNetworkBuilder b=fixtureBuilder();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        Oel2cOverExcitationLimiter o=b.addOel2c("Bus1","1",data(1));assertNotNull(o);assertTrue(o.initStates());
        assertEquals(Set.of("PID integrator","PID derivative","First lead-lag","Second lead-lag",
                "Reference filter","Current reference","Sensed input","Timer signal"),o.getNamedStates().keySet());
        m.setEfd(2.0);
        for(int i=0;i<500;i++){assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));
            assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));assertTrue(o.afterStep(.001));}
        double[] state=o.getStateSnapshot();
        assertTrue(state[5]>=1.10-TOL&&state[5]<=1.45+TOL);
        assertTrue(state[7]>=-TOL&&state[7]<=1+TOL);
        assertTrue(o.getOutput()>=-1-TOL&&o.getOutput()<=TOL);
    }

    @Test void fixedAndInverseRampModesProduceDifferentReferenceTrajectories()throws Exception{
        DStabNetworkBuilder fixedBuilder=fixtureBuilder(),inverseBuilder=fixtureBuilder();
        Oel2cOverExcitationLimiter fixed=fixedBuilder.addOel2c("Bus1","1",data(1));
        Oel2cOverExcitationLimiter inverse=inverseBuilder.addOel2c("Bus1","1",data(2));
        assertTrue(fixed.initStates());assertTrue(inverse.initStates());
        fixedBuilder.getDStabNetwork().getMachine("Bus1-mach1").setEfd(.5);
        inverseBuilder.getDStabNetwork().getMachine("Bus1-mach1").setEfd(.5);
        for(int i=0;i<500;i++){step(fixed,.001);step(inverse,.001);}
        assertNotEquals(fixed.getStateSnapshot()[5],inverse.getStateSnapshot()[5],1e-6);
    }

    private static void step(Oel2cOverExcitationLimiter o,double dt){assertTrue(o.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(o.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1));assertTrue(o.afterStep(dt));}
    private static DStabNetworkBuilder fixtureBuilder()throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St1cData d=new St1cData();
        d.setUel(0);d.setVos(1);d.setOel(1);d.setTr(0);d.setVimax(9);d.setVimin(-9);d.setTc(0);d.setTb(0);
        d.setTc1(0);d.setTb1(0);d.setKa(1);d.setTa(0);d.setVamax(9);d.setVamin(-9);d.setVrmax(9);d.setVrmin(-9);
        d.setKc(0);d.setKf(0);d.setTf(0);d.setKlr(0);d.setIlr(99);assertNotNull(b.addExcSt1c("Bus1","1",d));return b;
    }
    private static Oel2cData data(int rampMode){return new Oel2cData(1,rampMode,
            .015,.08,.02,.10,.35,.8,.01,.08,0,-1,0,-1,0,-1,0,.12,.4,.04,
            .7,.02,1.02,.9,1.45,1.10,.05,1.8,.2,2,.05,.5,-.5,1,-1,.2,1,0,0,-2,2,.1,1);}
}
