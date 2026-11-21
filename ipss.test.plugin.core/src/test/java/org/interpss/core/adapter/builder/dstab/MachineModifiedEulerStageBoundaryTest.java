package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.mach.ieeevc.*;
import com.interpss.dstab.mach.model.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.apache.commons.math3.complex.Complex;

/** Electrical derivatives in a coupled stage use the same stage origin as mechanics. */
class MachineModifiedEulerStageBoundaryTest {
    private Gentpj1Machine typeJ()throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();builder.getDStabNetwork().setFrequency(60);
        var bus=builder.getDStabNetwork().getDStabBus("Bus1");bus.setVoltage(1.,0.);
        ((com.interpss.dstab.DStabGen)bus.getContributeGen("1")).setGen(new Complex(.5,.1));
        var m=builder.addGentpj1("Bus1","1",100.,16.5,
                new Gentpj1Data(5.,.04,.5,.03,3.,.1,1.8,1.,.6,.5,.3,.3,.1,.1,.3,0.));
        assertTrue(m.initStates(bus));m.setSpeed(1.01);return m;
    }
    private void sameElectrical(Gentpj1Machine a,Gentpj1Machine b) {
        for(String state:new String[]{"E''q","E''d","E'q","E'd"})
            assertEquals(a.getNativeNamedStates().get(state),b.getNativeNamedStates().get(state),1e-12,state);
    }
    @Test void typeJElectricalStagesUseTheirOwnPredictorAndCorrectorOrigins()throws Exception {
        var coupled=typeJ();var electrical=typeJ();double dt=.0033333;
        coupled.getIgen();electrical.getIgen();
        assertTrue(electrical.nextStepElectrical(dt,DynamicSimuMethod.MODIFIED_EULER,electrical.getDStabBus().getNetwork(),0));
        assertTrue(coupled.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));
        sameElectrical(electrical,coupled);
        electrical.setAngle(coupled.getAngle());electrical.setSpeed(coupled.getSpeed());
        coupled.getIgen();electrical.getIgen();
        assertTrue(electrical.nextStepElectrical(dt,DynamicSimuMethod.MODIFIED_EULER,electrical.getDStabBus().getNetwork(),1));
        assertTrue(coupled.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1));sameElectrical(electrical,coupled);
    }
    private Wt1g1Machine wind()throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();builder.getDStabNetwork().setFrequency(60);
        var bus=builder.getDStabNetwork().getDStabBus("Bus1");bus.setVoltage(1.,0.);
        ((com.interpss.dstab.DStabGen)bus.getContributeGen("1")).setGen(new Complex(.5,.1));
        var m=builder.addWt1g1("Bus1","1",100.,16.5,new Wt1g1Data(.81,.035,3.6,.21,.15,.1,1,.025,1.2,.14));
        m.setDriveTrain(new Wt12t1Model(new Wt12t1Data(1.,2.,.92,5.,1.)));assertTrue(m.initStates(bus));
        bus.setVoltage(.8,.1);return m;
    }
    private void sameFlux(Wt1g1Machine a,Wt1g1Machine b) {
        for(String key:a.getNamedStates().keySet())
            if(!key.toLowerCase().contains("slip")&&!key.toLowerCase().contains("speed"))
                assertEquals(a.getNamedStates().get(key),b.getNamedStates().get(key),1e-12,key);
    }
    @Test void windElectricalStagesUseEntrySpeedRatherThanAdvancedMechanics()throws Exception {
        var coupled=wind();var electrical=wind();double dt=.0033333;
        assertTrue(electrical.nextStepElectrical(dt,DynamicSimuMethod.MODIFIED_EULER,electrical.getDStabBus().getNetwork(),0));
        assertTrue(coupled.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));sameFlux(electrical,coupled);
        electrical.setDriveTrainSpeed(coupled.getSpeed());
        assertTrue(electrical.nextStepElectrical(dt,DynamicSimuMethod.MODIFIED_EULER,electrical.getDStabBus().getNetwork(),1));
        assertTrue(coupled.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1));sameFlux(electrical,coupled);
    }
}
