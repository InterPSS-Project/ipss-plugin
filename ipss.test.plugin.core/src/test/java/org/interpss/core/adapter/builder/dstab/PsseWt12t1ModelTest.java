package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt1g1Machine;
import org.interpss.dstab.mach.Wt12t1Data;
import org.interpss.dstab.mach.Wt12t1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact parser, initialization, and state-coordinate checks for WT12T1. */
public class PsseWt12t1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void nativeTwoMassRecordAttachesToWt1g1() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt12t1.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt1g1Machine machine = (Wt1g1Machine) context.getDStabilityNet()
                .getMachine("Bus1-mach1");
        var model = machine.getDriveTrain();
        assertNotNull(model);
        assertEquals(5, DynamicModelCatalog.find("WT12T1").orElseThrow().parameterCount());
        assertEquals(4, model.getNamedStates().size());
        assertEquals(0.13515957, model.getShaftAngle(), 2.0e-5);
        assertEquals(0.00356328,
                model.getNamedStates().get("Generator speed deviation"), 2.0e-5);
        assertEquals(0.0, model.getGeneratorAngleDeviation(), 1.0e-12);
        assertEquals(0.500179, model.getAerodynamicPower(), 2.0e-5);
    }

    @Test
    void twoMassGeneratorNormalizesShaftTransferAtUnequalSpeeds() {
        // The equilibrium test cannot distinguish the generator/turbine speed
        // ratio: it is exactly one there. Excite the turbine, then check the
        // next mechanical predictor at unequal speeds. Independent reference
        // state perturbations and the existing coupled fault regression bind
        // this equation; no reference gain or model constant is fitted.
        Wt12t1Model model = new Wt12t1Model(new Wt12t1Data(5.4, .05, .82, 4.7, .9));
        model.initialize(.5, 1.00356, 60);
        model.setAerodynamicPower(model.getAerodynamicPower() + .2);
        model.step(.02, .5, 0);
        model.step(.02, .5, 1);
        double wt = model.getTurbineSpeed(), wg = model.getGeneratorSpeed();
        assertTrue(Math.abs(wt - wg) > 1e-5);
        double phi = model.getShaftAngle(), dt = .001;
        double shaft = model.shaftStiffness() * phi + .9 * (wt - wg);
        double generatorRhs = (shaft * wg / wt - .52 / wg - .05 * (wg - 1))
                / (2 * model.generatorInertia());
        double turbineRhs = (model.getAerodynamicPower() / wt - shaft)
                / (2 * model.turbineInertia());
        model.step(dt, .52, 0);
        assertEquals(wg + dt * generatorRhs, model.getGeneratorSpeed(), 1e-12);
        assertEquals(wt + dt * turbineRhs, model.getTurbineSpeed(), 1e-12);
        assertEquals(phi + dt * 2 * Math.PI * 60 * (wt - wg), model.getShaftAngle(), 1e-12);
    }

    @Test
    void oneMassModeInitializesAtExactDampedEquilibrium() {
        Wt12t1Model model = new Wt12t1Model(new Wt12t1Data(4.8, .12, 0, 0, 0));
        model.initialize(.6, 1.01, 60.0);
        double initializedSpeed = model.getGeneratorSpeed();
        model.step(.01, .6, 0);
        model.step(.01, .6, 1);
        assertEquals(initializedSpeed, model.getGeneratorSpeed(), 1.0e-12);
        assertEquals(initializedSpeed, model.getTurbineSpeed(), 1.0e-12);
    }

    @Test
    void absentAerodynamicControllerHoldsTorqueAndReportsGeneratorShaftPower() {
        Wt12t1Model model = new Wt12t1Model(new Wt12t1Data(5.4, .05, .82, 4.7, .9));
        model.initialize(.5, 1.00356, 60.0);
        model.initializeAerodynamicController(.5, 1.0);
        double initialTorque = model.getAerodynamicPower() / model.getTurbineSpeed();
        for (int i = 0; i < 25; i++) {
            model.stepCoupled(.002, .3, 0);
            model.stepCoupled(.002, .3, 1);
        }
        double wt = model.getTurbineSpeed(), wg = model.getGeneratorSpeed();
        assertTrue(Math.abs(wt - 1.00356) > 1.0e-5);
        assertTrue(Math.abs(wt - wg) > 1.0e-5);
        assertEquals(initialTorque, model.getAerodynamicPower() / wt, 1.0e-12);
        double shaftTorque = model.shaftStiffness() * model.getShaftAngle() + .9 * (wt - wg);
        assertEquals(shaftTorque * wg, model.getGeneratorMechanicalPower(), 1.0e-12);

        double dt = .001;
        model.stepCoupled(dt, .3, 0);
        assertEquals(wt + dt * (initialTorque - shaftTorque) / (2 * model.turbineInertia()),
                model.getTurbineSpeed(), 1.0e-12);
    }

    @Test
    void oneMassReportsInputPowerAndPreservesAnExplicitPowerCommand() {
        Wt12t1Model model = new Wt12t1Model(new Wt12t1Data(4.8, .12, 0, 0, 0));
        model.initialize(.6, 1.01, 60.0);
        model.initializeAerodynamicController(.6, 1.0);
        double initialTorque = model.getAerodynamicPower() / model.getTurbineSpeed();
        model.stepCoupled(.01, .4, 0);
        model.stepCoupled(.01, .4, 1);
        assertEquals(initialTorque * model.getTurbineSpeed(), model.getGeneratorMechanicalPower(), 1.0e-12);
        model.setAerodynamicPower(.65);
        model.step(.01, .4, 0);
        model.step(.01, .4, 1);
        assertEquals(.65, model.getGeneratorMechanicalPower(), 1.0e-12);
    }

    @Test
    void oneMassUsesInitializedSpeedForAerodynamicTorqueAtCorrector() {
        double inertia = 3.46, damping = .12, initialSpeed = 1.05, dt = .02;
        var model = new Wt12t1Model(new Wt12t1Data(inertia, damping, 0, 0, 0));
        model.initialize(.8, initialSpeed, 60);

        double predictorPower = .9, predictorElectricalPower = .7;
        model.setAerodynamicPower(predictorPower);
        double predictor = (predictorPower / initialSpeed - predictorElectricalPower / initialSpeed
                - damping * (initialSpeed - 1)) / (2 * inertia);
        model.step(dt, predictorElectricalPower, 0);
        double stageSpeed = initialSpeed + dt * predictor;
        assertEquals(stageSpeed, model.getGeneratorSpeed(), 1e-12);

        double correctorPower = .85, correctorElectricalPower = .75;
        model.setAerodynamicPower(correctorPower);
        double corrector = (correctorPower / initialSpeed - correctorElectricalPower / stageSpeed
                - damping * (stageSpeed - 1)) / (2 * inertia);
        model.step(dt, correctorElectricalPower, 1);
        assertEquals(initialSpeed + .5 * dt * (predictor + corrector), model.getGeneratorSpeed(), 1e-12);
        assertEquals(correctorPower, model.getGeneratorMechanicalPower(), 1e-12);
    }

}
