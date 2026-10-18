package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class WtgwgoaControllerIntegrationTest {
    private static WtgwgoaModel weak(){return WtgwgoaModel.forNativePsse367(new WtgwgoaData(.95,.1,.2,.3,.1,.1,.01,0));}
    private static Reeca1Model electricalA(){return new Reeca1Model(new Reeca1Data(
            0,0,0,0,0,0,.7,1.3,0,0,0,0,1,-1,1,0,0,0,0,2,-2,2,-2,0,0,0,0,0,0,
            100,-100,2,0,2,.01,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),null);}
    private static List<RenewableElectricalController> controllers(){
        var b=new Reecb1Model(new Reecb1Data(0,0,0,0,0,.7,1.3,0,0,0,0,1,-1,1,0,
                2,-2,2,-2,0,0,0,0,0,100,-100,2,0,2,.01));
        var c=new Reecc1Model(new Reecc1Data(0,0,0,0,0,.7,1.3,0,0,0,0,1,-1,1,0,
                2,-2,2,-2,0,0,0,0,0,100,-100,2,0,2,.01,
                0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10000,.5,1,0));
        double[] table=new double[10];
        var d=new Reecd1Model(new Reecd1Data(0,0,0,0,0,0,
                .7,1.3,0,0,0,0,1,-1,1,0,0,0,0,2,-2,2,-2,0,0,0,0,0,0,100,-100,
                2,0,2,.01,0,0,0,0,0,0,2,0,table,table,table,table));
        return List.of(electricalA(),b,c,d);
    }
    @Test void allFourElectricalFamiliesConsumeTheOptionalAbsoluteWeakGridReference(){
        for(var controller:controllers()){
            controller.setWeakGridController(weak());controller.initialize(.5,0,1);
            assertEquals(.5,controller.getSelectedPowerReference(.5));
            for(int i=0;i<150;i++)controller.step(.001,.5,0,.9,1);
            assertEquals(.1,controller.getSelectedPowerReference(.5));assertTrue(controller.getIpcmd()<.12,controller.getClass().getSimpleName());
            controller.initialize(.5,0,1);assertEquals(.5,controller.getSelectedPowerReference(.5));
            assertEquals(WtgwgoaModel.Mode.BYPASS,controller.getWeakGridController().getMode());
        }
    }
    @Test void absenceLeavesTheOriginalPlantReferencePathUnchanged(){
        for(var controller:controllers()){
            controller.initialize(.5,0,1);for(int i=0;i<20;i++)controller.step(.001,.5,0,.9,1);
            assertNull(controller.getWeakGridController());assertEquals(.5,controller.getSelectedPowerReference(.5));
            assertEquals(.5/.9,controller.getIpcmd(),1e-9);
        }
    }
    private static WindControlStack wind(){
        var stack=new WindControlStack();
        stack.setTorsionalEmulation(Wtdtb1Model.forNativePsse367(new Wtdtb1Data(4.5,1.5,.6,2,.4,.1),60));
        stack.setAerodynamics(new Wtara1Model(new Wtara1Data(.007,2)));
        stack.setTorqueController(new Wttqa1Model(new Wttqa1Data(1,2,1,.02,.1,2,0,.1,.8,.2,.9,.5,1,.8,1.1,100)));
        stack.setPitchControllerB(new Wtptb1Model(new Wtptb1Data(29,177,23.72,2.93,.25,.3,27,0,10,-10,5,-5,25,0)));
        return stack;
    }
    @Test void typeThreeTorqueAndPitchReceiveTheSameLimitedReferenceAtBothStages(){
        var actual=electricalA();actual.setWeakGridController(weak());var stack=wind();actual.setWindControlStack(stack);actual.initialize(.5,0,1);
        var expected=wind();expected.initialize(.5);
        for(int flag=0;flag<2;flag++){
            double order=actual.getNamedStates().get("Active Power Order");
            expected.step(.001,.5,order,.1,false,flag);actual.step(.001,.5,0,.9,1,flag);
            assertArrayEquals(expected.getPitchControllerB().getNativeStates(),stack.getPitchControllerB().getNativeStates(),1e-14);
            assertEquals(expected.getTorqueController().getPref(),stack.getTorqueController().getPref(),1e-14);
        }
    }
    @Test void twoStepAlgebraicRefreshesDoNotConsumeWeakGridHistory(){
        var a=electricalA();var b=electricalA();a.setWeakGridController(weak());b.setWeakGridController(weak());
        a.initialize(.5,0,1);b.initialize(.5,0,1);
        a.stepTwoStep(.001,.5,0,.9,1);b.stepTwoStep(.001,.5,0,.9,1);
        for(int i=0;i<10;i++)a.refreshTwoStep(.5,0,1,1);
        b.refreshTwoStep(.5,0,1,1);
        a.stepTwoStep(.001,.5,0,1,1);b.stepTwoStep(.001,.5,0,1,1);
        assertArrayEquals(a.getWeakGridController().getNativeStates(),b.getWeakGridController().getNativeStates(),0);
        assertArrayEquals(a.getWeakGridController().getNativeVars(),b.getWeakGridController().getNativeVars(),0);
    }
}
