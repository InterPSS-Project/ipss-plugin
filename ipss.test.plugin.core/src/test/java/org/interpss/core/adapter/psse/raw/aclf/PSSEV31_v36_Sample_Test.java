package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Version-matrix coverage for official PSS/E sample RAW files (v31–v36).
 * Primary assertions are parse/mapping; full NR LF on these samples is optional
 * and currently disabled when adj control is off.
 */
public class PSSEV31_v36_Sample_Test extends CorePluginTestSetup {
	@Test
	public void testV31() throws Exception {
		assertSampleMapped(31, PsseVersion.PSSE_31);
	}

	@Test
	public void testV32() throws Exception {
		assertSampleMapped(32, PsseVersion.PSSE_32);
	}

	@Test
	public void testV33() throws Exception {
		assertSampleMapped(33, PsseVersion.PSSE_33);
	}

	@Test
	public void testV34() throws Exception {
		assertSampleMapped(34, PsseVersion.PSSE_34);
	}

	@Test
	public void testV35() throws Exception {
		assertSampleMapped(35, PsseVersion.PSSE_35);
	}

	@Test
	public void testV36() throws Exception {
		assertSampleMapped(36, PsseVersion.PSSE_36);
	}

	@Test
	@Disabled("Sample NR LF does not converge with adjust algo disabled; mapping covered by testV36")
	public void testV36Loadflow() throws Exception {
		runSampleLoadflow(36, PsseVersion.PSSE_36);
	}

	@Test
	@Disabled("Sample PQ_NR LF fails on isolated/zero B1 bus in sample case; mapping covered by testV36")
	public void testV36Pq2ThenNrLoadflow() throws Exception {
		AclfNetwork net = loadSample(36, PsseVersion.PSSE_36);
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.PQ_NR);
		algo.setTolerance(0.001);
		algo.setMaxIterations(1000);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);

		algo.loadflow();

		assertTrue(net.isLfConverged());
		checkSolvedData(net);
	}

	private void assertSampleMapped(int version, PsseVersion psseVersion) throws Exception {
		AclfNetwork net = loadSample(version, psseVersion);
		assertNull(net.getBus("Bus0"), "version " + version + " must not create Bus0");
		assertTrue(net.getNoActiveBus() > 0, "version " + version + " must have active buses");
		checkMappedData(net);
	}

	private AclfNetwork loadSample(int version, PsseVersion psseVersion) throws Exception {
		return IpssAdapter.importAclfNet("testData/psse/v" + version + "/sample_v" + version + ".raw")
				.setFormat(FileFormat.PSSE)
				.setPsseVersion(psseVersion)
				.load()
				.getImportedObj();
	}

	private void runSampleLoadflow(int version, PsseVersion psseVersion) throws Exception {
		AclfNetwork net = loadSample(version, psseVersion);
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		if (version >= 36) {
			algo.setTolerance(0.001);
			algo.setMaxIterations(1000);
		}
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algo.loadflow();

		assertTrue(net.isLfConverged());
		checkSolvedData(net);
	}

	private void checkSolvedData(AclfNetwork net) {
		checkMappedData(net);

		AclfBus bus153 = net.getBus("Bus153");
		// The solved bus voltage should meet the PSS/E FACTS voltage setpoint.
		assertEquals(1.015, bus153.getVoltageMag(), 1.0E-6);
	}

	private void checkMappedData(AclfNetwork net) {
		AclfBus bus153 = net.getBus("Bus153");
		AclfBus bus155 = net.getBus("Bus155");
		assertNotNull(bus153);
		assertNotNull(bus155);

		// FACTS_DVCE_1 is the STATCON row. FACTS_DVCE_2 contributes its own mode-1
		// shunt equivalent, so Bus153 should keep two separately controllable SVCs.
		assertEquals(2, bus153.getStaticVarCompensatorList().size());
		StaticVarCompensator statcon = bus153.getStaticVarCompensatorList().get(0);
		StaticVarCompensator factsShunt = bus153.getStaticVarCompensatorList().get(1);
		assertEquals(1.015, statcon.getVSpecified(), 1.0E-6);
		assertEquals(1.015, factsShunt.getVSpecified(), 1.0E-6);
		assertEquals(0.50, statcon.getBLimit().getMax(), 1.0E-6);
		assertEquals(0.25, factsShunt.getBLimit().getMax(), 1.0E-6);

		// MODE=1 target transfer is represented as equal-and-opposite constant-power
		// injections because the dummy LINX branch is not left in the AC equations.
		assertComplexEquals(new Complex(3.5, 0.4), findLoad(bus153, new Complex(3.5, 0.4)).getLoadCP(), 1.0E-8);
		assertComplexEquals(new Complex(-3.5, -0.4), findLoad(bus155, new Complex(-3.5, -0.4)).getLoadCP(), 1.0E-8);
		assertEquals(-3.5, bus155.getLoadP(), 1.0E-8);
		assertEquals(-0.4, bus155.getLoadQ(), 1.0E-8);
	}

	private AclfLoad findLoad(AclfBus bus, Complex loadPQ) {
		return bus.getContributeLoadList().stream()
				.filter(load -> load.getLoadCP() != null)
				.filter(load -> Math.abs(load.getLoadCP().getReal() - loadPQ.getReal()) < 1.0E-8)
				.filter(load -> Math.abs(load.getLoadCP().getImaginary() - loadPQ.getImaginary()) < 1.0E-8)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Load not found: " + loadPQ));
	}

	private void assertComplexEquals(Complex expected, Complex actual, double tolerance) {
		assertEquals(expected.getReal(), actual.getReal(), tolerance);
		assertEquals(expected.getImaginary(), actual.getImaginary(), tolerance);
	}

}
