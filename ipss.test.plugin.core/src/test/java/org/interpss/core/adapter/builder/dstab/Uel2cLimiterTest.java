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
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Schema, equation, limiter, and named-state tests for UEL2C. */
public class Uel2cLimiterTest extends CorePluginTestSetup {
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

    private static DStabNetworkBuilder fixtureBuilder()throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St1cData d=new St1cData();
        d.setUel(1);d.setVos(1);d.setOel(0);d.setTr(0);d.setVimax(9);d.setVimin(-9);d.setTc(0);d.setTb(0);
        d.setTc1(0);d.setTb1(0);d.setKa(1);d.setTa(0);d.setVamax(9);d.setVamin(-9);d.setVrmax(9);d.setVrmin(-9);
        d.setKc(0);d.setKf(0);d.setTf(0);d.setKlr(0);d.setIlr(99);assertNotNull(b.addExcSt1c("Bus1","1",d));return b;
    }
    private static Uel2cData data(){return new Uel2cData(2,2,1,2,.02,.03,.025,.4,.8,.5,-.2,0,0,.08,.02,.07,.015,.06,
            -.2,.0125,.2,.0125,.6,.0125,1,.0125,0,0,0,0,0,0,0,0,0,0,0,0,0,0,.6,-.3,.95,.9,.1,.04,.55,-.25,.5,-.2,.6);}
}
