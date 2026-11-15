package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt2e1Model;
import org.interpss.dstab.mach.Wt2g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Parser, initialization, and named-state checks for WT2E1. */
public class PsseWt2e1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "-.0101, -.0101", "-.0099, 0", ".0099, 0", ".0101, .0101"
    })
    void nativePowerDeadbandGatesBothPiTerms(double error, double effectiveError) {
        var generator = new org.interpss.dstab.mach.Wt2g1Data(.14, 6.5, .2, .005, .12,
                1.02, 0, 1.22, 0, new double[]{0, .25, .5, .75, 1},
                new double[]{0, .01, .02, .03, .04});
        double initial = .2, speed = .02, dt = .001, ti = .25, kp = 5;
        var controller = new Wt2e1Model(new org.interpss.dstab.mach.Wt2e1Data(0, 0, ti, kp, .37, 0),
                generator);
        controller.initialize(speed, .5, initial);

        controller.step(dt, speed, .5 + error, 0);
        controller.step(dt, speed, .5 + error, 1);
        double integrator = initial + dt * effectiveError / ti;
        assertEquals(integrator, controller.getNamedStates().get("PI integrator"), 1e-12);
        assertEquals(integrator + kp * effectiveError, controller.getOutput(), 1e-12);
        assertEquals(.5 + error, controller.getNamedStates().get("Power filter"), 1e-12);
    }

    @Test void initializationLimitCorrectionIsEffectiveReportedAndReset() {
        var generatorData=new org.interpss.dstab.mach.Wt2g1Data(.14,6.5,.2,.005,.12,1.02,0,1.22,0,
                new double[]{0,.15,.45,.75,.95},new double[]{0,.006,.025,.055,.11});
        var entered=new org.interpss.dstab.mach.Wt2e1Data(.05,.05,2,.1,.15,.05);
        var control=new Wt2e1Model(entered,generatorData);
        for(double initial:new double[]{0,.2}) {
            control.initialize(.03,.5,initial);
            assertTrue(control.isInitializationLimitCorrected());
            assertEquals(initial,control.getOutput(),0);
            assertEquals(Math.min(.05,initial),control.getEffectiveOutputMin(),0);
            assertEquals(Math.max(.15,initial),control.getEffectiveOutputMax(),0);
            for(int i=0;i<100;i++)for(int stage=0;stage<2;stage++)control.step(.001,.03,.5,stage);
            assertEquals(initial,control.getOutput(),1e-12,"Zero error preserves the physical initialization command");
            assertEquals(.15,entered.outputMax(),0);assertEquals(.05,entered.outputMin(),0);
            control.initialize(.03,.5,.1);
            org.junit.jupiter.api.Assertions.assertFalse(control.isInitializationLimitCorrected());
            assertEquals(.15,control.getEffectiveOutputMax(),0);assertEquals(.05,control.getEffectiveOutputMin(),0);
        }
        control.initialize(.03,.5,.2);
        for(int stage=0;stage<2;stage++)control.step(.01,.03,.8,stage);
        assertEquals(.2,control.getNamedStates().get("PI integrator"),1e-12,"Anti-windup uses the effective upper bound");
        assertEquals(.2,control.getOutput(),1e-12);
    }

    @Test
    void exactPublishedRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt2e1.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Wt2g1Machine machine = assertInstanceOf(Wt2g1Machine.class,
                context.getDStabilityNet().getMachine("Bus1-mach1"));
        Wt2e1Model controller = machine.getRotorResistanceController();
        assertInstanceOf(Wt2e1Model.class, controller);
        assertEquals(6, DynamicModelCatalog.find("WT2E1").orElseThrow().parameterCount());
        assertEquals(3, controller.getNamedStates().size());
        assertEquals(0.03, controller.getNamedStates().get("Rotor speed filter"), 1e-12);
        assertEquals(0.5, controller.getNamedStates().get("Power filter"), 2e-4);
        assertEquals(machine.getRotorControlVoltage(),
                controller.getNamedStates().get("PI integrator"), 1e-12);
    }
}
