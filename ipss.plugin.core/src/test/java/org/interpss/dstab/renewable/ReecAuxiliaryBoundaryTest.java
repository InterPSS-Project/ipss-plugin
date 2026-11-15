package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.mach.Reaxbu1Data;
import org.interpss.dstab.mach.Reaxbu1Model;
import org.junit.jupiter.api.Test;

/** Absolute auxiliary references and shaft inputs remain distinct from Paux. */
class ReecAuxiliaryBoundaryTest {
    private static Reecd1Model reecd() {
        double[] table=new double[10];
        return new Reecd1Model(new Reecd1Data(0,0,0,0,1,0,
                .2,2,0,0,0,0,1,-1,1,0,0,0,0,2,-2,2,-2,0,0,0,0,0,0,100,-100,
                2,-2,2,.02,0,0,0,0,0,0,3,0,table,table,table,table));
    }
    private static Reecb1Model reecb() {
        return new Reecb1Model(new Reecb1Data(0,0,1,0,0,
                .2,2,0,0,0,0,1,-1,1,0,2,-2,2,-2,0,0,0,0,0,100,-100,2,-2,2,.02));
    }
    private static Reaxbu1Model auxiliary() {
        return new Reaxbu1Model("REAX4BU1",new Reaxbu1Data(1,.02,.8,.7,9,-9,9,-9));
    }
    @Test void electricalHostsPreserveNonzeroInitialOutputsAndTransmitBothAuxiliaryAxes() {
        for(RenewableElectricalController controller:new RenewableElectricalController[]{reecb(),reecd()}) {
            var auxiliary=auxiliary();controller.setAuxiliaryController(auxiliary);controller.initialize(.4,.1,1);
            for(int i=0;i<20;i++)for(int flag=0;flag<2;flag++)controller.step(.0005,.4,.1,1,1,flag);
            assertEquals(.4,auxiliary.getActiveOutput(),1e-12);assertEquals(.1,auxiliary.getReactiveOutput(),1e-12);
            assertEquals(.4,controller.getIpcmd(),1e-12);assertEquals(-.1,controller.getIqcmd(),1e-12);
            auxiliary.setExternalSignals(.03,.02);
            for(int i=0;i<400;i++)for(int flag=0;flag<2;flag++)controller.step(.0005,.4,.1,1,1,flag);
            assertTrue(controller.getIpcmd()>.41);assertTrue(controller.getIqcmd()<-.12);
            assertThrows(IllegalStateException.class,()->controller.setAuxiliaryController(auxiliary()));
            controller.initialize(.5,.2,1);
            assertEquals(.5,auxiliary.getActiveOutput(),1e-12);assertEquals(.2,auxiliary.getReactiveOutput(),1e-12);
        }
    }
    @Test void windDriveTrainAdvancesOnBothStagesAndReinitializesAtTheElectricalPower() {
        var controller=reecd();var stack=new WindControlStack();
        stack.setDriveTrain(new Wtdta1Model(new Wtdta1Data(3,0,0,0,0)));
        controller.setWindControlStack(stack);controller.initialize(.4,.1,1);
        for(int i=0;i<30;i++)for(int flag=0;flag<2;flag++)controller.step(.0005,.4,.1,1,1,flag);
        assertEquals(1,stack.getGeneratorSpeed(),1e-12);
        for(int i=0;i<30;i++)for(int flag=0;flag<2;flag++)controller.step(.0005,.2,.1,1,1,flag);
        assertTrue(stack.getGeneratorSpeed()>1);
        controller.initialize(.3,.1,1);assertEquals(1,stack.getGeneratorSpeed(),1e-12);
    }
}
