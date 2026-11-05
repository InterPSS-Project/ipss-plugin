package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.DStabObjectFactory;

class PsseDStabSolverTest {
    @Test
    void preparesBusMotorOnceAcrossItsTwoMembershipLists() {
        var network = DStabObjectFactory.createDStabilityNetwork();
        var bus = DStabObjectFactory.createDStabBus("Bus1", network).get();
        bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);
        class Motor extends org.interpss.dstab.dynLoad.impl.LD1PACImpl {
            int preparations;
            @Override public void configureIntegrationStep(double interval) {
                preparations++;
                super.configureIntegrationStep(interval);
            }
        }
        var motor = new Motor();
        motor.setDStabBus(bus);
        motor.setCharacteristicPolicy(org.interpss.dstab.dynLoad.impl.LD1PACImpl.CharacteristicPolicy.LEGACY_COMPOSITE);
        motor.setTv(.004); motor.setTf(.004); motor.setTth(.004);
        bus.addDynamicLoadModel(motor);
        if (!bus.getDynamicBusDeviceList().contains(motor)) bus.getDynamicBusDeviceList().add(motor);
        PsseDStabSolver.configureIntegrationStepAwareModels(network, 1.0 / 240);
        assertEquals(1, motor.preparations);
        assertEquals(0, motor.getEffectiveTv()); assertEquals(0, motor.getEffectiveTf()); assertEquals(0, motor.getEffectiveTth());
        assertEquals(.004, motor.getTv()); assertEquals(.004, motor.getTf()); assertEquals(.004, motor.getTth());
        PsseDStabSolver.configureIntegrationStepAwareModels(network, .0005);
        assertEquals(2, motor.preparations);
        assertEquals(.004, motor.getEffectiveTv()); assertEquals(.004, motor.getEffectiveTf()); assertEquals(.004, motor.getEffectiveTth());
    }

    @Test
    void retainsSubThresholdInitialConditionCompensation() {
        Hashtable<String, Complex> compensation = new Hashtable<>();
        PsseDStabSolver.retainNonzeroCompensation(compensation, "Bus1",
                new Complex(5.0e-11, -4.0e-11));
        PsseDStabSolver.retainNonzeroCompensation(compensation, "Bus2", Complex.ZERO);

        assertEquals(new Complex(5.0e-11, -4.0e-11), compensation.get("Bus1"));
        assertTrue(!compensation.containsKey("Bus2"));
    }

    @Test
    void suppliesIntegrationStepBeforeGgov1Initialization() throws Exception {
        var network = DStabObjectFactory.createDStabilityNetwork();
        AclfNetworkBuilder topology = new AclfNetworkBuilder(network);
        topology.setNetworkInfo("step-aware", "step-aware", 100000.0,
                OriginalDataFormat.PSSE);
        topology.addBus("Bus1", "Generator", 1L, 16500.0,
                1.0, 0.0, null, null, null);
        topology.setSwingBus("Bus1", 1.0, 0.0);
        topology.addContributeGen("Bus1", "1", true, 0.4, 0.0, 100.0, 1.0,
                1.0, -1.0, 1.0, 0.0, new Complex(0.0, 0.2), null,
                0.0, null, 0.0, 0.0);
        DStabNetworkBuilder builder = new DStabNetworkBuilder(network);
        var machine = builder.addGenrou("Bus1", "1", 100.0, 16.5,
                5.0, 0.0, 0.4, 0.05, 5.0, 0.0,
                1.8, 1.7, 0.3, 0.55, 0.25, 0.15, 0.0, 0.0);
        machine.setPm(0.4);
        machine.setPe(0.4);
        PsseGgov1GovernorData data = new PsseGgov1GovernorData();
        data.setTpelec(0.004);
        var governor = builder.addGovGgov1("Bus1", "1", data);

        PsseDStabSolver.configureIntegrationStepAwareModels(network, 0.01);
        assertTrue(governor.initStates(network.getDStabBus("Bus1"), machine));
        assertEquals(0.0, governor.getEffectiveTpelec(), 1.0e-12);
        assertEquals(0.004, governor.getData().getTpelec(), 1.0e-12);
    }

	@Test
	void suppliesIntegrationStepBeforePss2aInitialization() throws Exception {
		var network = DStabObjectFactory.createDStabilityNetwork();
		AclfNetworkBuilder topology = new AclfNetworkBuilder(network);
		topology.setNetworkInfo("step-aware", "step-aware", 100000.0,
				OriginalDataFormat.PSSE);
		topology.addBus("Bus1", "Generator", 1L, 16500.0,
				1.0, 0.0, null, null, null);
		topology.setSwingBus("Bus1", 1.0, 0.0);
		topology.addContributeGen("Bus1", "1", true, 0.4, 0.0, 100.0, 1.0,
				1.0, -1.0, 1.0, 0.0, new Complex(0.0, 0.2), null,
				0.0, null, 0.0, 0.0);
		DStabNetworkBuilder builder = new DStabNetworkBuilder(network);
		var machine = builder.addGenrou("Bus1", "1", 100.0, 16.5,
				5.0, 0.0, 0.4, 0.05, 5.0, 0.0,
				1.8, 1.7, 0.3, 0.55, 0.25, 0.15, 0.0, 0.0);
		machine.setPm(0.4);
		machine.setPe(0.4);
		var stabilizer = builder.addPss2a("Bus1", "1", 1, 0, 3, 0, 0, 0,
				0.004, 0.0, 0.0, 0.2, 0.0, 0.0,
				1.0, 1.0, 0.0, 0.0, 1.0,
				0.0, 0.0, 0.0, 0.0, 0.1, -0.1);

		PsseDStabSolver.configureIntegrationStepAwareModels(network, 0.01);
		assertTrue(stabilizer.initStates(network.getDStabBus("Bus1"), machine));
		assertEquals(0.01, stabilizer.tw1, 1.0e-12);
		assertEquals(0.004, stabilizer.getData().getTw1(), 1.0e-12);
	}
}
