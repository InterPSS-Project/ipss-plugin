package org.interpss.core.algo.cpf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.cpf.ContinuationPowerFlowAlgorithm;
import com.interpss.core.algo.cpf.CpfConfig;
import com.interpss.core.algo.cpf.CpfParameterization;
import com.interpss.core.algo.cpf.CpfPoint;
import com.interpss.core.algo.cpf.CpfResult;
import com.interpss.core.algo.cpf.CpfTerminationStatus;
import com.interpss.core.algo.cpf.QvResult;

public class ContinuationPowerFlowPsseTest extends CorePluginTestSetup {
	private static final String IEEE39_RAW =
			"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw";
	private static final String IEEE300_RAW =
			"../ipss.plugin.3phase/testData/IEEE300/IEEE300Bus_noHVDC_addXfr_v30.raw";
	private static final String WECC179_RAW =
			"testData/adpter/psse/v32/wecc179/wecc_179_v32.raw";
	private static final String WECC179_ANDES_REFERENCE =
			"testData/adpter/psse/v32/wecc179/andes_cpf_reference.properties";

	@Test
	void tracesIeee39PsseCaseToTargetLoading() throws Exception {
		assertTargetTrace(loadPsse(IEEE39_RAW), 39, "Bus12", 0.20);
	}

	@Test
	void arcLengthMethodsFindSameIeee39Nose() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		LoadflowAlgorithm loadflow = configuredBaseLoadflow(network);
		assertTrue(loadflow.loadflow(), "base-case load flow must converge");
		double baseVoltage = network.getBus("Bus12").getVoltageMag();
		double minLambda = Double.POSITIVE_INFINITY;
		double maxLambda = Double.NEGATIVE_INFINITY;

		for (CpfParameterization parameterization : new CpfParameterization[] {
				CpfParameterization.ARC_LENGTH, CpfParameterization.PSEUDO_ARC_LENGTH }) {
			CpfConfig config = new CpfConfig()
					.setParameterization(parameterization)
					.setLoadScale(2.0)
					.setStep(0.05)
					.setMaxStep(0.20)
					.setTolerance(1.0e-6)
					.setMaxSteps(500);
			CpfResult result = new ContinuationPowerFlowAlgorithm(network, config).runPV();

			System.out.printf("IEEE39 %s CPF: maxLambda=%.8f, points=%d, reason=%s, cause=%s%n",
					parameterization, result.maxLambda(), result.points().size(), result.terminationReason(),
					result.failureCause());
			assertTrue(result.converged(), parameterization + ": " + result.terminationReason());
			assertEquals(CpfTerminationStatus.NOSE_REACHED, result.status());
			assertTrue(result.maxLambda() > 0.20);
			assertTrue(result.points().size() > 3);
			assertTrue(result.points().stream().allMatch(point -> point.maxMismatch() < 1.0e-5));
			CpfPoint nose = result.points().stream()
					.max((left, right) -> Double.compare(left.lambda(), right.lambda()))
					.orElseThrow();
			assertTrue(nose.busStates().get("Bus12").voltageMagnitude() < baseVoltage);
			assertEquals(baseVoltage, network.getBus("Bus12").getVoltageMag(), 1.0e-12);
			minLambda = Math.min(minLambda, result.maxLambda());
			maxLambda = Math.max(maxLambda, result.maxLambda());
		}

		assertTrue((maxLambda - minLambda) / ((maxLambda + minLambda) / 2.0) < 0.01,
				"parameterizations must agree on the nose within 1%");
	}

	@Test
	void tracesIeee300PsseCaseToTargetLoading() throws Exception {
		// PSS/E three-winding transformers are expanded to auxiliary buses by the mapper.
		assertTargetTrace(loadPsse(IEEE300_RAW), 369, "Bus1", 0.05);
	}

	@Test
	void tracesWecc179PsseCaseToTargetLoading() throws Exception {
		Properties reference = loadProperties(WECC179_ANDES_REFERENCE);
		assertEquals(reference.getProperty("fixture.sha256"), sha256(Path.of(WECC179_RAW)),
				"the ANDES reference is valid only for the pinned RAW fixture");
		assertEquals("2.0.0", reference.getProperty("andes.version"));
		assertEquals("eda5163c9ee8d19945a1dd5d1771fec5da608c27",
				reference.getProperty("andes.commit"));
		AclfNetwork network = loadPsse(WECC179_RAW, IpssAdapter.PsseVersion.PSSE_32);
		assertEquals(179, network.getNoBus());
		assertTrue(configuredBaseLoadflow(network).loadflow());
		String[] buses = reference.getProperty("comparison.buses").split(",");

		for (String lambdaText : reference.getProperty("target.lambdas").split(",")) {
			double targetLambda = Double.parseDouble(lambdaText);
			CpfResult result = new ContinuationPowerFlowAlgorithm(network, new CpfConfig()
					.setLoadScale(2.0)
					.setTargetLambda(targetLambda)
					.setStep(0.02)
					.setMaxStep(0.05))
					.runPV();
			assertEquals(CpfTerminationStatus.TARGET_REACHED, result.status(), result.failureCause());
			CpfPoint endpoint = result.points().getLast();
			double maxVoltageDifference = 0.0;
			for (String busId : buses) {
				double andesVoltage = Double.parseDouble(reference.getProperty(
						"point." + lambdaText + ".bus." + busId + ".voltage"));
				double interpssVoltage = endpoint.busStates().get("Bus" + busId).voltageMagnitude();
				maxVoltageDifference = Math.max(maxVoltageDifference,
						Math.abs(interpssVoltage - andesVoltage));
				assertEquals(andesVoltage, interpssVoltage, 1.0e-5,
						"lambda=" + lambdaText + ", Bus" + busId);
			}
			System.out.printf(
					"WECC179 full-vector ANDES parity: lambda=%.3f, buses=%d, maxDifference=%.3e pu%n",
					targetLambda, buses.length, maxVoltageDifference);
		}
	}

	@Test
	void allParameterizationsReachSameIeee39Target() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		double referenceVoltage = Double.NaN;

		for (CpfParameterization parameterization : CpfParameterization.values()) {
			CpfConfig config = new CpfConfig()
					.setParameterization(parameterization)
					.setLoadScale(2.0)
					.setTargetLambda(0.10)
					.setStep(0.02)
					.setMaxStep(0.05)
					.setTolerance(1.0e-6);
			CpfResult result = new ContinuationPowerFlowAlgorithm(network, config).runPV();

			assertTrue(result.converged(), parameterization + ": " + result.terminationReason());
			assertEquals(0.10, result.points().getLast().lambda(), 2.0e-5);
			double voltage = result.points().getLast().busStates().get("Bus12").voltageMagnitude();
			if (Double.isNaN(referenceVoltage))
				referenceVoltage = voltage;
			else
				assertEquals(referenceVoltage, voltage, 2.0e-5, parameterization.name());
		}
	}

	@Test
	void repeatedIeee39TraceIsDeterministic() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		CpfConfig config = new CpfConfig().setTargetLambda(0.10).setTolerance(1.0e-6);
		ContinuationPowerFlowAlgorithm cpf = new ContinuationPowerFlowAlgorithm(network, config);

		CpfResult first = cpf.runPV();
		CpfResult second = cpf.runPV();

		assertEquals(first.maxLambda(), second.maxLambda(), 1.0e-10);
		assertEquals(first.points().size(), second.points().size());
		assertEquals(first.points().getLast().busStates().get("Bus12").voltageMagnitude(),
				second.points().getLast().busStates().get("Bus12").voltageMagnitude(), 1.0e-10);
	}

	@Test
	void reactiveLimitsReduceIeee39LoadabilityAndRestoreBusTypes() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		Map<String, AclfGenCode> baseCodes = new LinkedHashMap<>();
		network.getBusList().forEach(bus -> baseCodes.put(bus.getId(), bus.getGenCode()));
		// The RAW fixture uses placeholder +/-99 pu limits. Tighten each PV bus
		// around its solved base output so this test exercises PV-to-PQ switching.
		network.getBusList().stream().filter(bus -> bus.isGenPV()).forEach(bus -> {
			double baseQ = bus.toPVBus().getGenResults().getImaginary();
			bus.setQGenLimit(new LimitType(baseQ + 0.50, baseQ - 0.50));
		});

		CpfConfig unlimitedConfig = noseConfig().setEnforceReactiveLimits(false);
		CpfResult unlimited = new ContinuationPowerFlowAlgorithm(network, unlimitedConfig).runPV();
		CpfConfig limitedConfig = noseConfig().setEnforceReactiveLimits(true);
		CpfResult limited = new ContinuationPowerFlowAlgorithm(network, limitedConfig).runPV();

		System.out.printf("IEEE39 Q-limit CPF: unlimited=%.8f, limited=%.8f, limitedBuses=%s%n",
				unlimited.maxLambda(), limited.maxLambda(),
				limited.points().getLast().reactiveLimitedBuses());
		assertTrue(unlimited.converged(), unlimited.terminationReason());
		assertTrue(limited.converged(), limited.terminationReason());
		assertEquals(CpfTerminationStatus.NOSE_REACHED, unlimited.status());
		assertEquals(CpfTerminationStatus.NOSE_REACHED, limited.status());
		assertTrue(limited.points().stream()
				.anyMatch(point -> !point.reactiveLimitedBuses().isEmpty()));
		assertTrue(limited.maxLambda() < unlimited.maxLambda(),
				"reactive limits should reduce the loadability margin");
		assertTrue(limited.points().stream().allMatch(point -> point.maxMismatch() < 1.0e-5));
		network.getBusList().forEach(bus ->
				assertEquals(baseCodes.get(bus.getId()), bus.getGenCode(), bus.getId()));
	}

	@Test
	void reconcilesBaseCaseUpperQViolationAndRestoresGeneratorState() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		var bus = network.getBus("Bus32");
		AclfGenCode baseCode = bus.getGenCode();
		double baseScheduledQ = bus.getGenQ();
		double baseVoltage = bus.getVoltageMag();
		double requiredQ = bus.toPVBus().getGenResults().getImaginary();
		double upperLimit = requiredQ - 0.01;
		bus.setQGenLimit(new LimitType(upperLimit, requiredQ - 1.0));

		CpfResult result = new ContinuationPowerFlowAlgorithm(network, new CpfConfig()
				.setTargetLambda(0.01)
				.setEnforceReactiveLimits(true))
				.runPV();

		assertEquals(CpfTerminationStatus.TARGET_REACHED, result.status(), result.failureCause());
		assertTrue(result.points().getFirst().reactiveLimitedBuses().contains("Bus32"));
		assertEquals(upperLimit,
				result.points().getFirst().reactiveLimitedGeneration().get("Bus32"), 1.0e-12);
		assertEquals(baseCode, bus.getGenCode());
		assertEquals(baseScheduledQ, bus.getGenQ(), 1.0e-12);
		assertEquals(baseVoltage, bus.getVoltageMag(), 1.0e-12);
	}

	@Test
	void reconcilesBaseCaseLowerQViolation() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		var bus = network.getBus("Bus32");
		double requiredQ = bus.toPVBus().getGenResults().getImaginary();
		double lowerLimit = requiredQ + 0.01;
		bus.setQGenLimit(new LimitType(requiredQ + 1.0, lowerLimit));

		CpfResult result = new ContinuationPowerFlowAlgorithm(network, new CpfConfig()
				.setTargetLambda(0.01)
				.setEnforceReactiveLimits(true))
				.runPV();

		assertEquals(CpfTerminationStatus.TARGET_REACHED, result.status(), result.failureCause());
		assertEquals(lowerLimit,
				result.points().getFirst().reactiveLimitedGeneration().get("Bus32"), 1.0e-12);
	}

	@Test
	void forcedCorrectorFailureRestoresCompleteNetworkState() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		Map<String, List<Object>> before = snapshotNetworkState(network);

		CpfResult result = new ContinuationPowerFlowAlgorithm(network, new CpfConfig()
				.setLoadScale(10.0)
				.setTargetLambda(2.0)
				.setMinStep(0.5)
				.setStep(0.5)
				.setMaxStep(0.5)
				.setAdaptiveStep(false)
				.setMaxCorrectorIterations(1))
				.runPV();

		assertEquals(CpfTerminationStatus.CORRECTOR_FAILED, result.status());
		assertFalse(result.converged());
		assertTrue(result.failureCause().contains("iteration limit"));
		assertEquals(before, snapshotNetworkState(network));
		assertTrue(network.isLfConverged());
	}

	@Test
	void pointListenerExceptionRestoresCompleteNetworkState() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		Map<String, List<Object>> before = snapshotNetworkState(network);

		assertThrows(IllegalStateException.class, () ->
				new ContinuationPowerFlowAlgorithm(network,
						new CpfConfig().setTargetLambda(0.10))
						.setPointListener(point -> {
							if (point.lambda() > 0.0)
								throw new IllegalStateException("forced listener failure");
						})
						.runPV());

		assertEquals(before, snapshotNetworkState(network));
		assertTrue(network.isLfConverged());
	}

	@Test
	void tracesIeee39QvCurveAndRestoresBaseState() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		double baseVoltage = network.getBus("Bus12").getVoltageMag();
		double baseReactiveLoad = network.getBus("Bus12").getLoadQ();
		CpfConfig config = new CpfConfig()
				.setLoadScale(1.0)
				.setTargetLambda(0.50)
				.setStep(0.05)
				.setMaxStep(0.20)
				.setTolerance(1.0e-6)
				.setMaxSteps(1500);

		QvResult result = new ContinuationPowerFlowAlgorithm(network, config)
				.runQV("Bus12", 0.50);

		System.out.printf("IEEE39 Bus12 QV: converged=%s, maxLambda=%.8f, points=%d, lastV=%.6f, reason=%s, cause=%s%n",
				result.continuation().converged(), result.continuation().maxLambda(),
				result.points().size(), result.points().getLast().voltageMagnitude(),
				result.continuation().terminationReason(), result.continuation().failureCause());
		assertTrue(result.continuation().converged(), result.continuation().terminationReason());
		assertEquals(CpfTerminationStatus.TARGET_REACHED, result.continuation().status());
		assertTrue(result.points().size() > 2);
		assertEquals(baseReactiveLoad, result.points().getFirst().reactiveLoad(), 1.0e-12);
		assertTrue(result.points().stream()
				.mapToDouble(point -> point.reactiveLoad()).max().orElseThrow()
				> baseReactiveLoad + 0.10);
		assertTrue(result.points().getLast().voltageMagnitude()
				< result.points().getFirst().voltageMagnitude());
		assertTrue(result.points().stream().allMatch(point -> point.maxMismatch() < 1.0e-5));
		assertEquals(baseVoltage, network.getBus("Bus12").getVoltageMag(), 1.0e-12);
		assertEquals(baseReactiveLoad, network.getBus("Bus12").getLoadQ(), 1.0e-12);
	}

	@Test
	void qvCurveAppliesReactiveLimits() throws Exception {
		AclfNetwork network = loadPsse(IEEE39_RAW);
		assertTrue(configuredBaseLoadflow(network).loadflow());
		network.getBusList().stream().filter(bus -> bus.isGenPV()).forEach(bus -> {
			double baseQ = bus.toPVBus().getGenResults().getImaginary();
			bus.setQGenLimit(new LimitType(baseQ + 0.005, baseQ - 1.0));
		});
		CpfConfig config = new CpfConfig()
				.setLoadScale(1.0)
				.setTargetLambda(0.50)
				.setStep(0.05)
				.setMaxStep(0.20)
				.setEnforceReactiveLimits(true);

		QvResult result = new ContinuationPowerFlowAlgorithm(network, config)
				.runQV("Bus12", 0.50);

		assertEquals(CpfTerminationStatus.TARGET_REACHED, result.continuation().status(),
				result.continuation().failureCause());
		assertTrue(result.continuation().points().stream()
				.anyMatch(point -> !point.reactiveLimitedGeneration().isEmpty()));
	}

	private static CpfConfig noseConfig() {
		return new CpfConfig()
				.setLoadScale(2.0)
				.setStep(0.05)
				.setMaxStep(0.20)
				.setTolerance(1.0e-6)
				.setMaxSteps(500);
	}

	private static CpfResult assertTargetTrace(
			AclfNetwork network, int expectedBusCount, String observedBusId, double targetLambda)
			throws Exception {
		assertEquals(expectedBusCount, network.getNoBus());
		LoadflowAlgorithm loadflow = configuredBaseLoadflow(network);
		assertTrue(loadflow.loadflow(), "base-case load flow must converge");

		double baseVoltage = network.getBus(observedBusId).getVoltageMag();
		double baseLoadP = network.getBus(observedBusId).getLoadP();
		CpfConfig config = new CpfConfig()
				.setLoadScale(2.0)
				.setTargetLambda(targetLambda)
				.setStep(0.05)
				.setMaxStep(0.10)
				.setTolerance(1.0e-6);

		CpfResult result = new ContinuationPowerFlowAlgorithm(network, config).runPV();

		assertTrue(result.converged(), result.terminationReason());
		assertEquals(CpfTerminationStatus.TARGET_REACHED, result.status());
		assertTrue(result.points().size() > 1);
		assertEquals(targetLambda, result.points().getLast().lambda(), 2.0e-5);
		assertTrue(result.points().stream().allMatch(point -> point.maxMismatch() < 1.0e-5));
		assertNotEquals(baseVoltage,
				result.points().getLast().busStates().get(observedBusId).voltageMagnitude(), 1.0e-5);

		assertEquals(baseVoltage, network.getBus(observedBusId).getVoltageMag(), 1.0e-12);
		assertEquals(baseLoadP, network.getBus(observedBusId).getLoadP(), 1.0e-12);
		return result;
	}

	private static LoadflowAlgorithm configuredBaseLoadflow(AclfNetwork network) {
		LoadflowAlgorithm loadflow = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		loadflow.setLfMethod(AclfMethodType.PQ);
		loadflow.setTolerance(1.0e-6);
		loadflow.getLfAdjAlgo().setApplyAdjustAlgo(false);
		return loadflow;
	}

	private static AclfNetwork loadPsse(String path) throws Exception {
		return loadPsse(path, IpssAdapter.PsseVersion.PSSE_30);
	}

	private static AclfNetwork loadPsse(
			String path, IpssAdapter.PsseVersion version) throws Exception {
		return IpssAdapter.importAclfNet(path)
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(version)
				.load()
				.getImportedObj();
	}

	private static Properties loadProperties(String path) throws Exception {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(Path.of(path))) {
			properties.load(input);
		}
		return properties;
	}

	private static String sha256(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = Files.newInputStream(path)) {
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0; )
				digest.update(buffer, 0, read);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static Map<String, List<Object>> snapshotNetworkState(AclfNetwork network) {
		Map<String, List<Object>> snapshot = new LinkedHashMap<>();
		network.getBusList().forEach(bus -> {
			List<Object> state = new ArrayList<>();
			state.add(bus.getVoltageMag());
			state.add(bus.getVoltageAng());
			state.add(bus.getLoadP());
			state.add(bus.getLoadQ());
			state.add(bus.getGenCode());
			state.add(bus.getGenP());
			state.add(bus.getGenQ());
			for (Object object : bus.getContributeLoadList()) {
				AclfLoad load = (AclfLoad) object;
				state.add(load.getLoadCP());
				state.add(load.getLoadCI());
				state.add(load.getLoadCZ());
			}
			snapshot.put(bus.getId(), state);
		});
		network.getBranchList().forEach(branch -> snapshot.put("branch:" + branch.getId(), List.of(
				branch.getFromTurnRatio(),
				branch.getToTurnRatio(),
				branch.getFromPSXfrAngle(),
				branch.getToPSXfrAngle())));
		return snapshot;
	}
}
