package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ActivePowerResponseLimitsTest {
    @Test void electricalControllerRespondsInBothDirectionsAccordingToItsOperatingPolicy() {
        for(var policy:ActivePowerResponseLimits.values())for(double increment:new double[]{.1,-.1}) {
            var controller=new Reecb1Model(new Reecb1Data(0,0,1,0,0,
                    .85,1.15,.02,-.01,.01,2,1.1,-1.1,0,.02,.6,-.6,1.2,.8,1,5,1,10,.02,99,-99,1,0,1.3,.02));
            var plant=new Repca1Model(new Repca1Data(0,0,0,"1",0,0,1,
                    .02,.5,1,.03,.1,.7,0,0,0,.2,-.2,0,0,1,-1,.5,1,.02,-.0006,.0006,.2,-.2,1,-1,.1,20,20,1));
            controller.setPlantController(plant);controller.setActivePowerResponseLimits(policy);
            controller.initialize(.3,.04,1);plant.setActivePowerReference(.3+increment);
            for(int i=0;i<500;i++)controller.step(.002,.3,.04,1,1);
            double order=controller.getIpcmd();
            if(policy==ActivePowerResponseLimits.FIXED||policy==ActivePowerResponseLimits.DOWN_ONLY&&increment>0)
                assertEquals(.3,order,1e-12);
            else assertTrue(increment>0?order>.31:order<.29,policy+" increment="+increment+" order="+order);
            if(policy==ActivePowerResponseLimits.FIXED) {
                controller.initialize(.5,.06,1);
                assertEquals(.5,controller.getEffectiveActivePowerMinimum(),0);
                assertEquals(.5,controller.getEffectiveActivePowerMaximum(),0);
                assertEquals(0,plant.getPref(),0);
            }
        }
    }
}
