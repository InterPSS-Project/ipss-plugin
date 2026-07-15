package org.interpss.core.opf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.opf.matpower.OpfMatpowerAdapter;
import org.ieee.odm.model.opf.OpfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.odm.mapper.ODMOpfParserMapper;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.net.Branch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfGen;
import com.interpss.opf.OpfGenOperatingMode;
import com.interpss.opf.OpfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class MatpowerOpfMapperTest extends CorePluginTestSetup {
	@Test
	public void rtsStyleExtensionsMapToOpfNetwork() throws InterpssException {
		OpfNetwork net = mapMatpower("testData/matpower/case3bus_rts_extensions.m");

		assertEquals(3, net.getBusList().size());
		assertEquals(1, net.getBranchList().size());

		OpfBus bus2 = net.getBus("Bus2");
		assertNotNull(bus2);
		assertEquals(2, bus2.getContributeGenList().size());

		OpfGen gen2a = bus2.getContributeGenList().get(0);
		assertEquals("GEN_2A", gen2a.getName());
		assertEquals(OpfGenOperatingMode.PQ_GENERATOR, gen2a.getOperatingMode());
		assertEquals(0.30, gen2a.getGen().getReal(), 1.0e-8);
		assertEquals(0.60, gen2a.getOpfLimits().getPLimit().getMax(), 1.0e-8);
		assertEquals(0.05, gen2a.getOpfLimits().getPLimit().getMin(), 1.0e-8);
		assertEquals(2000.0, gen2a.getIncCost().getQuadraticCurve().getB(), 1.0e-8);

		OpfGen gen2b = bus2.getContributeGenList().get(1);
		assertEquals("GEN_2B", gen2b.getName());
		assertEquals(0.20, gen2b.getGen().getReal(), 1.0e-8);
		assertEquals(0.40, gen2b.getOpfLimits().getPLimit().getMax(), 1.0e-8);
		assertEquals(0.04, gen2b.getOpfLimits().getPLimit().getMin(), 1.0e-8);
		assertEquals(3000.0, gen2b.getIncCost().getQuadraticCurve().getB(), 1.0e-8);

		Branch branch = net.getBranch("Bus1->Bus2(1)");
		assertNotNull(branch);
		assertEquals("LINE_1_2", branch.getName());
		assertEquals(1, net.getSpecialBranchList().size());
		assertTrue(net.getSpecialBranchList().get(0) instanceof HvdcLine2TLCC);

		AclfLoad dcFromLoad = net.getBus("Bus1").getContributeLoadList().get(0);
		assertEquals(new Complex(0.10, 0.01), dcFromLoad.getLoadCP());
		assertEquals("MATPOWER DC line 1 from terminal", dcFromLoad.getName());

		AclfLoad dcToLoad = net.getBus("Bus3").getContributeLoadList().get(0);
		assertEquals(new Complex(-0.09, -0.01), dcToLoad.getLoadCP());
		assertEquals("MATPOWER DC line 1 to terminal", dcToLoad.getName());
	}

	@Test
	public void optionalRtsGmlcMatpowerMapsToOpfNetwork() throws InterpssException {
		String file = System.getProperty("rts.gmlc.matpower");
		assumeTrue(file != null && Files.isRegularFile(Path.of(file)));

		OpfNetwork net = mapMatpower(file);

		assertEquals(73, net.getBusList().size());
		assertEquals(120, net.getBranchList().size());
		assertTrue(net.getSpecialBranchList().size() > 0);
		assertEquals(158, countContributedGenerators(net));
		assertTrue(net.getBus("Bus101").getContributeGenList().size() > 1);
		assertTrue(net.getBus("Bus113").getContributeLoadList().stream()
				.anyMatch(load -> "MATPOWER DC line 1 from terminal".equals(load.getName())));
		assertTrue(net.getBus("Bus316").getContributeLoadList().stream()
				.anyMatch(load -> "MATPOWER DC line 1 to terminal".equals(load.getName())));
	}

	private OpfNetwork mapMatpower(String file) throws InterpssException {
		IODMAdapter adapter = new OpfMatpowerAdapter();
		assertTrue(adapter.parseInputFile(file));

		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
		assertTrue(new ODMOpfParserMapper().map2Model((OpfModelParser) adapter.getModel(), simuCtx));

		OpfNetwork net = simuCtx.getOpfNet();
		assertNotNull(net);
		return net;
	}

	private int countContributedGenerators(OpfNetwork net) {
		int count = 0;
		for (OpfBus bus : net.getBusList()) {
			count += bus.getContributeGenList().size();
		}
		return count;
	}
}
