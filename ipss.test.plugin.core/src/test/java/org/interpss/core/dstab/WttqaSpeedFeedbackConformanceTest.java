package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.dstab.renewable.Wttqa1Model;
import org.junit.jupiter.api.Test;

/** Native speed feedback is generator speed minus reference; power feedback is a separate selector. */
public class WttqaSpeedFeedbackConformanceTest extends CorePluginTestSetup {
    private static Wttqa1Model model(int selector,double kp,double ki) {
        return new Wttqa1Model(new Wttqa1Data(selector,kp,ki,.02,.1,2,0,0,0,0,0,0,0,0,0,100));
    }

    @Test void completeStepIncreasesElectricalTorqueWhenTheShaftOverspeeds() {
        for(double speed:new double[]{.9,1.1}) {
            var model=model(0,2,0);model.initialize(.5,1);
            model.step(.01,.5,speed,.5,false);
            assertEquals(.5+2*(speed-1),model.getTorque(),1e-14);
            assertEquals(model.getTorque()*speed,model.getPref(),1e-14);
        }
    }

    @Test void bothExplicitStagesUseThePublishedSpeedErrorSign() {
        var model=model(0,3,2);model.initialize(.5,1);
        model.step(.01,.5,1.02,.5,false,0);
        assertEquals(.5004,model.getTorqueIntegral(),1e-14);
        model.step(.01,.5,.99,.5,false,1);
        assertEquals(.5001,model.getTorqueIntegral(),1e-14);
        assertEquals(.4701,model.getTorque(),1e-14);
    }

    @Test void nonunitInitializationBacksolvesTheSameSpeedFeedbackJunction() {
        var model=model(0,2,1);model.initialize(.5,.9);
        assertEquals(.5/.9-2*(.9-1),model.getTorqueIntegral(),1e-14);
        model.step(0,.5,.9,.5,false,0);
        assertEquals(.5,model.getPref(),1e-14,"Pure algebraic refresh must preserve the initialized boundary");
    }

    @Test void powerModeRetainsReferenceMinusMeasuredPowerOverSpeed() {
        var model=model(1,2,1);model.initialize(.5,1);
        model.step(.01,.5,.9,.6,false,0);
        assertEquals(.5+.01*(.1/.9),model.getTorqueIntegral(),1e-14);
        assertEquals(model.getTorqueIntegral()+2*(.1/.9),model.getTorque(),1e-14);
    }

    @Test void voltageDipFreezesTheIntegralNotTheProportionalTorqueOrPowerFilter() {
        var model=model(0,3,2);model.initialize(.5,1);
        model.step(.01,.7,1.02,.5,true,0);assertEquals(.5,model.getTorqueIntegral(),0);assertEquals(.56,model.getTorque(),1e-14);
        model.step(.01,.7,1.03,.5,true,1);assertEquals(.5,model.getTorqueIntegral(),0);assertEquals(.59,model.getTorque(),1e-14);
        assertTrue(model.getFilteredPower()>.5);
    }
}
