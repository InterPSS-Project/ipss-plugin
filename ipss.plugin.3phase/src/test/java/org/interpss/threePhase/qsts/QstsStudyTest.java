package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.INetwork3Phase;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PBranch;
import com.interpss.core.threephase.Static3PGen;
import com.interpss.core.threephase.Static3PLoad;
import com.interpss.core.threephase.Static3PNetwork;
import com.interpss.core.threephase.Static3PhaseFactory;

public class QstsStudyTest {
	@Test
	void runnerAppliesLoadAndGeneratorSchedulesSequentially() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		QstsScheduleData schedule = schedule(3);
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, schedule)
				.setPowerFlowAlgorithm(powerFlow)
				.setNumberOfSteps(3)
				.run();

		assertTrue(result.isConverged());
		assertEquals(3, result.getStepResults().size());
		assertEquals(3, powerFlow.solveCount);
		assertTrue(powerFlow.fixedPointYMatrixCacheEnabled);

		QstsStepResult step0 = result.getStep(0);
		assertEquals(3, result.getStep(0).getLoadPowers().stream()
				.filter(sample -> sample.getDeviceId().equals("load1"))
				.count());
		assertEquals(0.05, power(step0.getLoadPowers(), "load1", "A").getP(), 1.0e-12);
		assertEquals(0.01, power(step0.getLoadPowers(), "load1", "A").getQ(), 1.0e-12);
		assertEquals(0.03, power(step0.getGeneratorPowers(), "pv1", "A").getP(), 1.0e-12);

		QstsStepResult step2 = result.getStep(2);
		assertEquals(0.15, power(step2.getLoadPowers(), "load1", "A").getP(), 1.0e-12);
		assertEquals(0.03, power(step2.getLoadPowers(), "load1", "A").getQ(), 1.0e-12);
		assertEquals(0.0, power(step2.getGeneratorPowers(), "pv1", "A").getP(), 1.0e-12);
		assertNull(step2.getFailureReason());
	}

	@Test
	void branchPowerSamplingSkipsOpenDssInternalVsourceBranch() throws InterpssException {
		Static3PNetwork network = twoBusNetworkWithOpenDssSourceImpedance();
		QstsScheduleData schedule = schedule(1);
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, schedule)
				.setPowerFlowAlgorithm(powerFlow)
				.setNumberOfSteps(1)
				.run();

		assertTrue(result.isConverged());
		assertEquals(6, result.getStep(0).getBranchPowers().size());
		assertTrue(result.getStep(0).getBranchPowers().stream()
				.noneMatch(sample -> sample.getElementId().equals("vsource_source")));
	}

	@Test
	void qstsSamplingSkipsInactivePhasesForTwoPhaseBusesAndDevices()
			throws InterpssException {
		Static3PNetwork network = twoPhaseNetwork();
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, staticSchedule())
				.setPowerFlowAlgorithm(powerFlow)
				.setNumberOfSteps(1)
				.run();

		QstsStepResult step = result.getStep(0);
		assertTrue(result.isConverged());
		assertTrue(step.getBusVoltages().stream()
				.anyMatch(sample -> sample.getBusId().equals("load") && sample.getPhase().equals("A")));
		assertTrue(step.getBusVoltages().stream()
				.anyMatch(sample -> sample.getBusId().equals("load") && sample.getPhase().equals("B")));
		assertFalse(step.getBusVoltages().stream()
				.anyMatch(sample -> sample.getBusId().equals("load") && sample.getPhase().equals("C")));
		assertEquals(2, step.getLoadPowers().stream()
				.filter(sample -> sample.getDeviceId().equals("load1"))
				.count());
		assertFalse(step.getLoadPowers().stream()
				.anyMatch(sample -> sample.getDeviceId().equals("load1") && sample.getPhase().equals("C")));
		assertEquals(4, step.getBranchPowers().size());
		assertFalse(step.getBranchPowers().stream()
				.anyMatch(sample -> sample.getPhase().equals("C")));
	}

	@Test
	void staticScheduleReusesSolvedStateAfterFirstQstsStep() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, staticSchedule())
				.setPowerFlowAlgorithm(powerFlow)
				.setNumberOfSteps(4)
				.run();

		assertTrue(result.isConverged());
		assertEquals(4, result.getStepResults().size());
		assertEquals(1, powerFlow.solveCount);
		assertEquals(3, result.getStep(0).getIterationCount());
		assertEquals(0, result.getStep(1).getIterationCount());
		assertEquals(0, result.getStep(2).getIterationCount());
		assertEquals(0, result.getStep(3).getIterationCount());
	}

	@Test
	void repeatedProfileValuesReuseSolvedStateUntilInjectionChanges() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, repeatedValueSchedule())
				.setPowerFlowAlgorithm(powerFlow)
				.setNumberOfSteps(4)
				.run();

		assertTrue(result.isConverged());
		assertEquals(4, result.getStepResults().size());
		assertEquals(2, powerFlow.solveCount);
		assertEquals(3, result.getStep(0).getIterationCount());
		assertEquals(0, result.getStep(1).getIterationCount());
		assertEquals(3, result.getStep(2).getIterationCount());
		assertEquals(0, result.getStep(3).getIterationCount());
		assertEquals(0.05, power(result.getStep(1).getLoadPowers(), "load1", "A").getP(), 1.0e-12);
		assertEquals(0.10, power(result.getStep(2).getLoadPowers(), "load1", "A").getP(), 1.0e-12);
	}

	@Test
	void controlledQstsDoesNotReuseRepeatedProfileSolvedState() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();
		RegulatorControlData control = new RegulatorControlData("reg1", "regBranch", 2,
				PhaseCode.A, 1.0, 0.01, 0.00625, -16, 16);

		QstsResult result = QstsStudy.from(network, repeatedValueSchedule())
				.setPowerFlowAlgorithm(powerFlow)
				.setRegulatorControls(List.of(control))
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(1)
				.setNumberOfSteps(4)
				.run();

		assertTrue(result.isConverged());
		assertEquals(4, powerFlow.solveCount);
		assertEquals(3, result.getStep(0).getIterationCount());
		assertEquals(3, result.getStep(1).getIterationCount());
		assertEquals(3, result.getStep(2).getIterationCount());
		assertEquals(3, result.getStep(3).getIterationCount());
	}

	@Test
	void failedStepIncludesStepModeHourAndReason() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();
		powerFlow.failOnSolve = 2;

		QstsResult result = QstsStudy.from(network, schedule(4))
				.setPowerFlowAlgorithm(powerFlow)
				.setNumberOfSteps(4)
				.setStepSizeHours(0.25)
				.run();

		assertFalse(result.isConverged());
		assertEquals(2, result.getStepResults().size());
		QstsStepResult failed = result.getStep(1);
		assertFalse(failed.isConverged());
		assertEquals(QstsMode.DAILY, failed.getMode());
		assertEquals(0.25, failed.getHour(), 1.0e-12);
		assertTrue(failed.getFailureReason().contains("step 1"));
		assertTrue(failed.getFailureReason().contains("DAILY"));
		assertTrue(failed.getFailureReason().contains("0.25"));
	}

	@Test
	void staticBusExposesPhaseDeviceViewsWithoutDStabCasts() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		IBus3Phase bus = network.getBus("load");

		AclfLoad3Phase load = bus.getPhaseLoadList().get(0);
		com.interpss.core.threephase.AclfGen3Phase generator = bus.getPhaseGenList().get(0);

		assertFalse(network.getClass().getName().contains(".dstab."));
		assertFalse(bus.getClass().getName().contains(".dstab."));
		assertFalse(load.getClass().getName().contains(".dstab."));
		assertFalse(generator.getClass().getName().contains(".dstab."));
		assertEquals("load1", load.getId());
		assertEquals("pv1", generator.getId());
		assertEquals(0.1, load.getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
		assertEquals(0.1, generator.getPower3Phase(UnitType.PU).a_0.getReal(), 1.0e-12);
	}

	@Test
	void qstsDefaultPowerFlowRunsOnStaticNetworkWithoutDStabModels() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setNumberOfSteps(1)
				.setMaxPowerFlowIterations(50)
				.setTolerance(1.0e-6)
				.run();

		assertTrue(result.isConverged(), result.getStep(0).getFailureReason());
		assertEquals(1, result.getStepResults().size());
		assertFalse(network.getClass().getName().contains(".dstab."));
		assertTrue(network.getBusList().stream()
				.noneMatch(bus -> bus.getClass().getName().contains(".dstab.")));
		assertTrue(network.getBranchList().stream()
				.noneMatch(branch -> branch.getClass().getName().contains(".dstab.")));
	}

	@Test
	void delayedCapacitorControlOperationCountIsCapturedPerQstsStep() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		Static3PBus loadBus = network.getBus("load");
		Static3PLoad capacitor = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		capacitor.setId("cap1");
		capacitor.setCode(AclfLoadCode.CONST_Z);
		capacitor.set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.01),
				new Complex(0.0, -0.01), new Complex(0.0, -0.01)));
		loadBus.getContributeLoadList().add(capacitor);
		CapacitorControlData control = new CapacitorControlData("capctrl1", "cap1", "", 1,
				CapacitorControlData.ControlType.VOLTAGE, 100.0, 200.0, 1.0, 1.0,
				false, 0.0, 0.0, 0.0, 1800.0, null, null);

		QstsResult result = QstsStudy.from(network, schedule(3))
				.setPowerFlowAlgorithm(new FakePowerFlowAlgorithm())
				.setCapacitorControls(List.of(control))
				.setControlMode(QstsControlMode.TIME)
				.setMaxControlIterations(1)
				.setNumberOfSteps(3)
				.setStepSizeHours(1.0)
				.run();

		assertTrue(result.isConverged());
		assertEquals(0, result.getStep(0).getActionCount());
		assertEquals(1, result.getStep(1).getActionCount());
		assertEquals(0, result.getStep(2).getActionCount());
		assertEquals(0, result.getStep(0).getCapacitorStates().get(0).getOperationCount());
		assertEquals(1, result.getStep(1).getCapacitorStates().get(0).getOperationCount());
		assertEquals(1, result.getStep(2).getCapacitorStates().get(0).getOperationCount());
		assertTrue(result.getStep(0).getCapacitorStates().get(0).isClosed());
		assertFalse(result.getStep(1).getCapacitorStates().get(0).isClosed());
		assertEquals(-0.03, result.getStep(0).getCapacitorStates().get(0).getTotalReactivePowerPu(), 1.0e-12);
		assertEquals(0.0, result.getStep(1).getCapacitorStates().get(0).getTotalReactivePowerPu(), 1.0e-12);
	}

	@Test
	void qstsExcludesControlledCapacitorsFromLoadPowerSamples() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		Static3PBus loadBus = network.getBus("load");
		Static3PLoad capacitor = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		capacitor.setId("cap1");
		capacitor.setCode(AclfLoadCode.CONST_Z);
		capacitor.set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.01),
				new Complex(0.0, -0.01), new Complex(0.0, -0.01)));
		loadBus.getContributeLoadList().add(capacitor);
		Static3PLoad fixedCapacitor = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		fixedCapacitor.setId("cap2");
		fixedCapacitor.setCode(AclfLoadCode.CONST_Z);
		fixedCapacitor.set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.02),
				new Complex(0.0, -0.02), new Complex(0.0, -0.02)));
		loadBus.getContributeLoadList().add(fixedCapacitor);
		CapacitorControlData control = new CapacitorControlData("capctrl1", "cap1", "", 1,
				CapacitorControlData.ControlType.VOLTAGE, 100.0, 200.0, 1.0, 1.0,
				false, 0.0, 0.0, 0.0, 0.0, null, null);
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();
		powerFlow.loadVoltageScale = 1.1;

		QstsResult result = QstsStudy.from(network, staticSchedule())
				.setPowerFlowAlgorithm(powerFlow)
				.setCapacitorControls(List.of(control))
				.setNumberOfSteps(1)
				.run();

		assertTrue(result.isConverged());
		assertTrue(result.getStep(0).getLoadPowers().stream()
				.noneMatch(sample -> sample.getDeviceId().equals("cap1")));
		assertTrue(result.getStep(0).getLoadPowers().stream()
				.noneMatch(sample -> sample.getDeviceId().equals("cap2")));
		assertEquals(-0.0363, result.getStep(0).getCapacitorStates().get(0)
				.getTotalReactivePowerPu(), 1.0e-12);
	}

	@Test
	void staticCapacitorControlsUseInvalidationAwareFixedPointYMatrixCache() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		Static3PBus loadBus = network.getBus("load");
		Static3PLoad capacitor = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		capacitor.setId("cap1");
		capacitor.setCode(AclfLoadCode.CONST_Z);
		capacitor.set3PhaseLoad(new Complex3x1(new Complex(0.0, -0.01),
				new Complex(0.0, -0.01), new Complex(0.0, -0.01)));
		loadBus.getContributeLoadList().add(capacitor);
		CapacitorControlData control = new CapacitorControlData("capctrl1", "cap1", "", 1,
				CapacitorControlData.ControlType.VOLTAGE, 100.0, 200.0, 1.0, 1.0,
				false, 0.0, 0.0, 0.0, 0.0, null, null);
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setPowerFlowAlgorithm(powerFlow)
				.setCapacitorControls(List.of(control))
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(1)
				.run();

		assertTrue(result.isConverged());
		assertTrue(powerFlow.fixedPointYMatrixCacheEnabled);
	}

	@Test
	void staticRegulatorControlsUseInvalidationAwareFixedPointYMatrixCache()
			throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		RegulatorControlData control = new RegulatorControlData("reg1", "regBranch", 2,
				PhaseCode.A, 1.0, 0.01, 0.00625, -16, 16);
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setPowerFlowAlgorithm(powerFlow)
				.setRegulatorControls(List.of(control))
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(1)
				.run();

		assertTrue(result.isConverged());
		assertTrue(powerFlow.fixedPointYMatrixCacheEnabled);
	}

	@Test
	void qstsControlIterationLimitIsPropagatedToPowerFlowAlgorithm()
			throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setPowerFlowAlgorithm(powerFlow)
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(37)
				.run();

		assertTrue(result.isConverged());
		assertEquals(37, powerFlow.maxControlIterations);
	}

	@Test
	void inverterControlSetpointIsAppliedAfterQstsPowerFlowThroughStaticPhaseGen() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.VOLTVAR, "", 40000.0,
				-5000.0, 5000.0, 0.0, Double.NaN, 6000.0, Double.NaN, true);

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setPowerFlowAlgorithm(new FakePowerFlowAlgorithm())
				.setInverterControls(List.of(control))
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(1)
				.run();

		QstsInverterControlSample sample = result.getStep(0).getInverterControls().get(0);
		assertTrue(sample.isApplied());
		assertTrue(sample.isLimited());
		assertEquals(5000.0, sample.getReactivePowerKvar(), 1.0e-12);
		assertEquals(0.05, power(result.getStep(0).getGeneratorPowers(), "pv1", "A")
				.getQ() + power(result.getStep(0).getGeneratorPowers(), "pv1", "B").getQ()
				+ power(result.getStep(0).getGeneratorPowers(), "pv1", "C").getQ(), 1.0e-12);
		assertFalse(network.getBus("load").getPhaseGenList().get(0).getClass().getName().contains(".dstab."));
	}

	@Test
	void inverterControlUsesAdapterStoreInsteadOfExtendingPhaseGen() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		CountingInverterAdapter adapter = new CountingInverterAdapter(network.getBus("load").getPhaseGenList().get(0));
		QstsInverterAdapterStore store = new QstsInverterAdapterStore();
		store.register(adapter);
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.WATTVAR, "", 40000.0,
				-5000.0, 5000.0, 0.0, Double.NaN, 4000.0, Double.NaN, true);

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setPowerFlowAlgorithm(new FakePowerFlowAlgorithm())
				.setInverterAdapterStore(store)
				.setInverterControls(List.of(control))
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(1)
				.run();

		assertEquals(1, adapter.applyCount);
		assertTrue(result.getStep(0).getInverterControls().get(0).isApplied());
		assertEquals(4000.0, result.getStep(0).getInverterControls().get(0).getReactivePowerKvar(), 1.0e-12);
		assertTrue(adapter.getGenerator() instanceof com.interpss.core.threephase.AclfGen3Phase);
	}

	@Test
	void changedInverterSetpointTriggersPowerFlowControlIteration() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		FakePowerFlowAlgorithm powerFlow = new FakePowerFlowAlgorithm();
		CountingInverterAdapter adapter = new CountingInverterAdapter(network.getBus("load").getPhaseGenList().get(0));
		QstsInverterAdapterStore store = new QstsInverterAdapterStore();
		store.register(adapter);
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.WATTVAR, "", 40000.0,
				-5000.0, 5000.0, 0.0, Double.NaN, 4000.0, Double.NaN, true);

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setPowerFlowAlgorithm(powerFlow)
				.setInverterAdapterStore(store)
				.setInverterControls(List.of(control))
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(3)
				.run();

		assertTrue(result.isConverged());
		assertEquals(2, powerFlow.solveCount);
		assertEquals(2, adapter.applyCount);
		assertEquals(4000.0, result.getStep(0).getInverterControls().get(0).getReactivePowerKvar(), 1.0e-12);
	}

	@Test
	void inverterAdapterUsesSolvedBusVoltageToResolveVoltVarCurve() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		InverterGenAdapter adapter = new InverterGenAdapter(network.getBus("load").getPhaseGenList().get(0))
				.addCurve(new QstsControlCurve("vv1", new double[] {0.95, 1.05},
						new double[] {2000.0, -2000.0}));
		QstsInverterAdapterStore store = new QstsInverterAdapterStore();
		store.register(adapter);
		InverterControlData control = new InverterControlData("inv1", "pv1",
				InverterControlData.ControlMode.VOLTVAR, "vv1", 40000.0,
				-5000.0, 5000.0, 0.0, true);

		QstsResult result = QstsStudy.from(network, schedule(1))
				.setPowerFlowAlgorithm(new FakePowerFlowAlgorithm())
				.setInverterAdapterStore(store)
				.setInverterControls(List.of(control))
				.setControlMode(QstsControlMode.STATIC)
				.setMaxControlIterations(1)
				.run();

		assertTrue(result.getStep(0).getInverterControls().get(0).isApplied());
		assertEquals(0.0, result.getStep(0).getInverterControls().get(0).getReactivePowerKvar(), 1.0e-12);
	}

	private static QstsDevicePowerSample power(List<QstsDevicePowerSample> samples, String deviceId, String phase) {
		return samples.stream()
				.filter(sample -> sample.getDeviceId().equals(deviceId) && sample.getPhase().equals(phase))
				.findFirst()
				.orElseThrow();
	}

	private static QstsScheduleData schedule(int steps) {
		QstsProfileRegistry registry = new QstsProfileRegistry();
		registry.add(new QstsProfile("load_day", null, new double[] {0.5, 1.0, 1.5, 2.0},
				null, null));
		registry.add(new QstsProfile("pv_day", null, new double[] {0.3, 0.8, 0.0, 0.2},
				null, null));
		return new QstsScheduleData(registry, List.of(
				new QstsProfileBinding("load", "load1", Map.of("daily", "load_day"),
						QstsDeviceStatus.VARIABLE),
				new QstsProfileBinding("generator", "pv1", Map.of("daily", "pv_day"),
						QstsDeviceStatus.VARIABLE)),
				new QstsGlobalOptions("daily", steps, 1.0, 1.0, "off", 0, 0.0));
	}

	private static QstsScheduleData staticSchedule() {
		return new QstsScheduleData(new QstsProfileRegistry(), null,
				new QstsGlobalOptions("snapshot", 1, 1.0, 1.0, "off", 0, 0.0));
	}

	private static QstsScheduleData repeatedValueSchedule() {
		QstsProfileRegistry registry = new QstsProfileRegistry();
		registry.add(new QstsProfile("load_day", null, new double[] {0.5, 0.5, 1.0, 1.0},
				null, null));
		registry.add(new QstsProfile("pv_day", null, new double[] {0.3, 0.3, 0.8, 0.8},
				null, null));
		return new QstsScheduleData(registry, List.of(
				new QstsProfileBinding("load", "load1", Map.of("daily", "load_day"),
						QstsDeviceStatus.VARIABLE),
				new QstsProfileBinding("generator", "pv1", Map.of("daily", "pv_day"),
						QstsDeviceStatus.VARIABLE)),
				new QstsGlobalOptions("daily", 4, 1.0, 1.0, "off", 0, 0.0));
	}

	private static Static3PNetwork twoBusNetwork() throws InterpssException {
		Static3PNetwork network = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
		network.setBaseKva(100000.0);
		Static3PBus source = Static3PhaseFactory.eINSTANCE.createStatic3PBus();
		source.setId("source");
		network.addBus(source);
		source.setBaseVoltage(12470.0);
		source.setGenCode(AclfGenCode.SWING);
		Static3PBus loadBus = Static3PhaseFactory.eINSTANCE.createStatic3PBus();
		loadBus.setId("load");
		network.addBus(loadBus);
		loadBus.setBaseVoltage(12470.0);
		loadBus.setGenCode(AclfGenCode.GEN_PQ);

		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setId("load1");
		load.setCode(AclfLoadCode.CONST_P);
		load.set3PhaseLoad(new Complex3x1(new Complex(0.1, 0.02),
				new Complex(0.1, 0.02), new Complex(0.1, 0.02)));
		loadBus.getContributeLoadList().add(load);

		Static3PGen generator = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		generator.setId("pv1");
		generator.setCode(AclfGenCode.GEN_PQ);
		generator.setMvaBase(100.0);
		generator.setGen(new Complex(0.1, 0.0));
		generator.setPower3Phase(new Complex3x1(new Complex(0.1, 0.0),
				new Complex(0.1, 0.0), new Complex(0.1, 0.0)), UnitType.PU);
		loadBus.getContributeGenList().add(generator);

		Static3PBranch branch = ThreePhaseObjectFactory.createStatic3PBranch("source", "load", "1", network);
		branch.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex(0.01, 0.04)));
		return network;
	}

	private static Static3PNetwork twoPhaseNetwork() throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		Static3PBus loadBus = network.getBus("load");
		loadBus.getPhaseLoadList().get(0).setPhaseCode(PhaseCode.AB);
		loadBus.getContributeGenList().clear();
		Static3PBranch branch = network.getBranch("source->load(1)");
		branch.setPhaseCode(PhaseCode.AB);
		return network;
	}

	private static Static3PNetwork twoBusNetworkWithOpenDssSourceImpedance()
			throws InterpssException {
		Static3PNetwork network = twoBusNetwork();
		Static3PBus idealSource = Static3PhaseFactory.eINSTANCE.createStatic3PBus();
		idealSource.setId("source_vsource");
		network.addBus(idealSource);
		idealSource.setBaseVoltage(12470.0);
		idealSource.setGenCode(AclfGenCode.SWING);
		Static3PBranch sourceBranch = ThreePhaseObjectFactory.createStatic3PBranch(
				"source_vsource", "source", "vsource", network);
		sourceBranch.setName("vsource_source");
		sourceBranch.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex(0.001, 0.002)));
		return network;
	}

	private static class FakePowerFlowAlgorithm implements DistributionPowerFlowAlgorithm {
		private INetwork3Phase network;
		private int solveCount;
		private int failOnSolve = -1;
		private int maxIteration = 100;
		private int maxControlIterations = 20;
		private double tolerance = 1.0e-6;
		private DistributionPFMethod method = DistributionPFMethod.Fixed_Point;
		private boolean fixedPointYMatrixCacheEnabled;
		private double loadVoltageScale = 1.0;

		@Override
		public INetwork3Phase getNetwork() {
			return network;
		}

		@Override
		public void setNetwork(INetwork3Phase net) {
			this.network = net;
		}

		@Override
		public boolean orderDistributionBuses(boolean radialOnly) {
			return true;
		}

		@Override
		public boolean initBusVoltages() {
			return true;
		}

		@Override
		public boolean powerflow() {
			solveCount++;
			for(IBus3Phase bus : network.getThreePhaseBusList()) {
				double scale = "load".equals(bus.getId()) ? loadVoltageScale : 1.0;
				bus.set3PhaseVotlages(new Complex3x1(new Complex(scale, 0.0),
						new Complex(-0.5 * scale, -0.8660254037844386 * scale),
						new Complex(-0.5 * scale, 0.8660254037844386 * scale)));
			}
			return solveCount != failOnSolve;
		}

		@Override
		public void setRegulatorControls(List<RegulatorControlData> controls) {
		}

		@Override
		public List<RegulatorControlData> getRegulatorControls() {
			return List.of();
		}

		@Override
		public void setRegulatorControlEnabled(boolean enabled) {
		}

		@Override
		public boolean isRegulatorControlEnabled() {
			return false;
		}

		@Override
		public void setCapacitorControls(List<CapacitorControlData> controls) {
		}

		@Override
		public List<CapacitorControlData> getCapacitorControls() {
			return List.of();
		}

		@Override
		public void setCapacitorControlEnabled(boolean enabled) {
		}

		@Override
		public boolean isCapacitorControlEnabled() {
			return false;
		}

		@Override
		public DistributionPFMethod getPFMethod() {
			return method;
		}

		@Override
		public void setPFMethod(DistributionPFMethod method) {
			this.method = method;
		}

		@Override
		public void setTolerance(double tolerance) {
			this.tolerance = tolerance;
		}

		@Override
		public double getTolerance() {
			return tolerance;
		}

		@Override
		public void setMaxIteration(int maxIterNum) {
			this.maxIteration = maxIterNum;
		}

		@Override
		public int getMaxIteration() {
			return maxIteration;
		}

		@Override
		public void setMaxControlIterations(int maxControlIterations) {
			this.maxControlIterations = maxControlIterations;
		}

		@Override
		public int getMaxControlIterations() {
			return maxControlIterations;
		}

		@Override
		public int getIterationCount() {
			return 3;
		}

		@Override
		public boolean isFixedPointFallbackUsed() {
			return false;
		}

		@Override
		public void setFixedPointYMatrixCacheEnabled(boolean enabled) {
			this.fixedPointYMatrixCacheEnabled = enabled;
		}

		@Override
		public boolean isFixedPointYMatrixCacheEnabled() {
			return fixedPointYMatrixCacheEnabled;
		}

		@Override
		public void clearFixedPointYMatrixCache() {
		}

		@Override
		public void setInitBusVoltageEnabled(boolean enableInitBus3PhaseVolts) {
		}

		@Override
		public boolean isInitBusVoltageEnabled() {
			return true;
		}
	}

	private static class CountingInverterAdapter extends InverterGenAdapter {
		private int applyCount;

		private CountingInverterAdapter(com.interpss.core.threephase.AclfGen3Phase generator) {
			super(generator);
		}

		@Override
		public org.interpss.threePhase.powerflow.control.InverterControlModel.InverterControlResult apply(
				InverterControlData control, QstsStepContext context, double networkBaseKva) {
			applyCount++;
			return super.apply(control, context, networkBaseKva);
		}
	}
}
