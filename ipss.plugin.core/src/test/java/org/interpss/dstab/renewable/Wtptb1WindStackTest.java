package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Wtptb1WindStackTest {
    private static WindControlStack stack() {
        var stack=new WindControlStack();
        stack.setTorsionalEmulation(Wtdtb1Model.forNativePsse367(new Wtdtb1Data(4.5,1.5,.6,2,.4,.1),60));
        stack.setAerodynamics(new Wtara1Model(new Wtara1Data(.007,2)));
        stack.setTorqueController(new Wttqa1Model(new Wttqa1Data(1,2,1,.02,.1,2,0,.1,.8,.2,.9,.24,1,.8,1.1,100)));
        stack.setPitchControllerB(new Wtptb1Model(new Wtptb1Data(29,177,23.72,2.93,.25,.3,27,0,10,-10,5,-5,25,0)));
        return stack;
    }
    @Test void stagedPitchUsesPlantReferenceAndAtomicPredictedTorqueSpeedReference() {
        var stack=stack();stack.initialize(.24);
        var expected=new Wtptb1Model(stack.getPitchControllerB().getData());expected.initialize(2);
        expected.step(.001,.35,.31,stack.getTurbineSpeed(),stack.getTorqueController().getSpeedReference(),0);
        stack.step(.001,.12,.35,.31,false,0);
        assertArrayEquals(expected.getNativeStates(),stack.getPitchControllerB().getNativeStates(),1e-14);
        double speed=stack.getTurbineSpeed(),reference=stack.getTorqueController().getSpeedReference();
        expected.step(.001,.33,.29,speed,reference,1);
        stack.step(.001,.13,.33,.29,false,1);
        assertArrayEquals(expected.getNativeStates(),stack.getPitchControllerB().getNativeStates(),1e-14);
        assertEquals(.24-.007*stack.getPitchControllerB().getPitch()*(stack.getPitchControllerB().getPitch()-2),
                stack.getAerodynamics().getMechanicalPower(),1e-14);
    }
    @Test void zeroStepTwoStepRefreshDoesNotAdvanceDifferentialStates() {
        var stack=stack();stack.initialize(.24);assertTrue(stack.isComplete());
        double[] initial=stack.getPitchControllerB().getNativeStates();
        stack.stepTwoStep(0,.12,.35,.31,false);
        assertArrayEquals(initial,stack.getPitchControllerB().getNativeStates(),0);
        stack.stepTwoStep(.001,.12,.35,.31,false);
        assertTrue(stack.getPitchControllerB().getPitch()>2);
    }
    @Test void typedAttachmentAndInitializationRejectIncompleteOrMixedPitchStacks() {
        var stack=stack();
        assertThrows(IllegalStateException.class,()->stack.setPitchControllerB(stack.getPitchControllerB()));
        assertThrows(IllegalStateException.class,()->stack.setPitchController(new Wtpta1Model(new Wtpta1Data(1,1,1,1,0,.3,27,0,10,-10))));
        var missing=new WindControlStack();missing.setPitchControllerB(stack.getPitchControllerB());
        assertThrows(IllegalStateException.class,()->missing.initialize(.24));
    }
}
