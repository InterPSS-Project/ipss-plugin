package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.junit.jupiter.api.Test;

/** Native input ports, canonical coordinates and actual evaluation/history observations. */
public class WtptaNativeObservationConformanceTest {
    private static Wtpta1Data data(){return new Wtpta1Data(2,1,4,3,.5,1,20,0,100,-100);}
    private static WindControlStack stack() {
        var s=new WindControlStack();s.setDriveTrain(Wtdta1Model.fromNativeRecord(new Wtdta1Data(5,.1,.8,1.5,.2),60));
        s.setTorqueController(new Wttqa1Model(new Wttqa1Data(0,2,1,.02,.1,2,0,.1,.8,.2,.9,.4,1,.8,1.1,100)));
        s.setAerodynamics(new Wtara1Model(new Wtara1Data(.007,2)));s.setPitchController(Wtpta1Model.fromNativeRecord(data()));s.initialize(.24);return s;
    }

    @Test void nativeStorageAndLegacyNamedKeysRemainPureAndReinitializable() {
        var model=Wtpta1Model.fromNativeRecord(data());model.initialize(2,.92);
        assertEquals(Set.of("Pitch","Speed Integral","Compensation Integral"),model.getNamedStates().keySet());
        assertArrayEquals(new double[]{2,2,0},model.getNativeStates(),0);var before=model.getNativeStates();var copy=model.getNativeStates();copy[0]=99;
        assertArrayEquals(before,model.getNativeStates(),0);assertThrows(UnsupportedOperationException.class,()->model.getNamedStates().clear());
        model.stepWithReference(.001,.3,.24,.93,.92);model.initialize(2,.92);
        assertArrayEquals(before,model.getNativeStates(),0);assertEquals(Stage.NOT_EVALUATED,model.getStateDerivativeSnapshot().stage());
        var legacy=new Wtpta1Model(data());assertFalse(legacy.isNativeRecordConvention());assertThrows(IllegalStateException.class,legacy::getNativeStates);
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->model.initialize(2,invalid));
            assertArrayEquals(before,model.getNativeStates(),0);
        }
    }

    @Test void integralStorageIsBoundedButRawPiOutputsAreNotClipped() {
        var model=Wtpta1Model.fromNativeRecord(new Wtpta1Data(2,100,4,3,0,1,1,0,100,-100));model.initialize(.5,1);
        model.stepWithReference(.001,.2,0,1.1,1,0);var snapshot=model.getStateDerivativeSnapshot();
        assertEquals(Stage.PREDICTOR,snapshot.stage());assertEquals(.2,snapshot.rates().get("Speed Integral"),1e-14);
        assertEquals(.8,snapshot.rates().get("Compensation Integral"),1e-14);assertEquals(10.6,snapshot.rates().get("Pitch"),1e-12);
        assertEquals(.5002,model.getSpeedIntegral(),1e-14);assertEquals(.5106,model.getPitch(),1e-14);
        assertEquals(.5,snapshot.evaluationStates().get("Pitch"),0);
    }

    @Test void pitchAngleCrossAntiWindupFreezesOnlyOutwardMotionAndPermitsRelease() {
        var model=Wtpta1Model.fromNativeRecord(new Wtpta1Data(2,1,4,3,0,1,1,0,100,-100));model.initialize(1,1);
        model.stepWithReference(.001,.2,0,1.1,1,0);assertTrue(model.getStateDerivativeSnapshot().rates().values().stream().allMatch(v->v==0));
        model.initialize(1,1);model.stepWithReference(.001,-.2,0,.9,1,0);
        assertEquals(-.2,model.getStateDerivativeSnapshot().rates().get("Speed Integral"),1e-14);
        assertTrue(model.getStateDerivativeSnapshot().rates().get("Pitch")<0);assertTrue(model.getPitch()<1);
    }

    @Test void zeroTimeIsAnAlgebraicOutputNotARateLimitedIntegratedCoordinate() {
        var model=Wtpta1Model.fromNativeRecord(new Wtpta1Data(0,0,0,4,0,0,1,0,.01,-.01));model.initialize(.5,1);
        model.stepWithReference(0,.2,0,1,1,0);assertEquals(1,model.getPitch(),0);
        assertEquals(2,model.getIntegrationStates().size());assertEquals(3,model.getNamedStates().size());
        assertEquals(Stage.NOT_EVALUATED,model.getStateDerivativeSnapshot().stage());
        model.stepWithReference(.001,.2,0,1,1,0);assertEquals(2,model.getStateDerivativeSnapshot().rates().size());
    }

    @Test void stackPassesIndependentPlantAndMovingSpeedReferencesAtBothEvaluationEndpoints() {
        var s=stack();var independent=Wtpta1Model.fromNativeRecord(data());independent.initialize(2,s.getTorqueController().getSpeedReference());
        double oldWt=s.getTurbineSpeed(),oldWr=s.getTorqueController().getSpeedReference();
        s.step(.001,.3,.28,.22,false,0);independent.stepWithReference(.001,.28,.22,oldWt,oldWr,0);
        assertArrayEquals(independent.getNativeStates(),s.getPitchController().getNativeStates(),1e-15);
        assertEquals(.24,s.getPitchController().getStateDerivativeSnapshot().rates().get("Compensation Integral"),1e-14);
        double predictedWt=s.getTurbineSpeed(),predictedWr=s.getTorqueController().getSpeedReference();
        s.step(.001,.31,.27,.23,false,1);independent.stepWithReference(.001,.27,.23,predictedWt,predictedWr,1);
        assertArrayEquals(independent.getNativeStates(),s.getPitchController().getNativeStates(),1e-15);
        assertEquals(predictedWr,s.getPitchController().getSpeedReference(),0);
        var complete=stack();var staged=stack();complete.step(.001,.3,.28,.22,false);
        staged.step(.001,.3,.28,.22,false,0);staged.step(.001,.3,.28,.22,false,1);
        assertArrayEquals(staged.getPitchController().getNativeStates(),complete.getPitchController().getNativeStates(),0);
    }

    @Test void eventHistoryObservationReportsAppliedRatesAndZeroDurationDoesNotConsumeIt() {
        var model=Wtpta1Model.fromNativeRecord(data());model.initialize(2,1);
        model.stepTwoStepWithReference(.001,.28,.24,1,1);var first=model.getStateDerivativeSnapshot();double before=model.getCompensationIntegral();
        assertEquals(Stage.TWO_STEP,first.stage());model.rebaseNextTwoStep();model.stepTwoStepWithReference(0,.3,.24,1,1);assertEquals(first,model.getStateDerivativeSnapshot());
        model.stepTwoStepWithReference(.001,.3,.24,1,1);var event=model.getStateDerivativeSnapshot();assertEquals(Stage.TWO_STEP_EVENT,event.stage());
        assertEquals(before+.001*(1.5*event.rates().get("Compensation Integral")-.5*first.rates().get("Compensation Integral")),model.getCompensationIntegral(),1e-15);
        model.rebaseNextTwoStep();model.initialize(2,1);model.stepTwoStepWithReference(.001,.28,.24,1,1);
        assertEquals(Stage.TWO_STEP,model.getStateDerivativeSnapshot().stage());
    }

    @Test void nativeAngleCrossFreezeRetainsTheIntegratorHistoryRatherThanAnchoringAtState() {
        var model=Wtpta1Model.fromNativeRecord(new Wtpta1Data(2,1,4,3,.5,1,1,0,100,-100));model.initialize(.999,1);
        model.stepTwoStepWithReference(.001,1,0,1,1);assertEquals(1,model.getPitch(),0);assertEquals(.006,model.getCompensationIntegral(),1e-15);
        model.stepTwoStepWithReference(.001,1,0,1,1);assertEquals(.004,model.getCompensationIntegral(),1e-15);
        assertEquals(0,model.getStateDerivativeSnapshot().rates().get("Compensation Integral"),0);
        assertEquals(.006,model.getStateDerivativeSnapshot().evaluationStates().get("Compensation Integral"),1e-15);
    }
}
