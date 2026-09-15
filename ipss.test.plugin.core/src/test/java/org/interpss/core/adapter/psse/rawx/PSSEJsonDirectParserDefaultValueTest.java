package org.interpss.core.adapter.psse.rawx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings.ApplicationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;

/** Regression coverage for PSS/E defaults allowed by sparse RAWX tables. */
public class PSSEJsonDirectParserDefaultValueTest extends CorePluginTestSetup {

	@TempDir
	Path tempDir;

	@Test
	public void sparseMinimumCaseUsesPsseDefaultsAndSolves() throws Exception {
		AclfNetwork net = parse("minimum.rawx", """
				{
				  "general": {"version": "36.0"},
				  "network": {
				    "caseid": {
				      "fields": ["rev", "title1"],
				      "data": [36, "PSS/E MINIMUM RAWX CASE"]
				    },
				    "bus": {
				      "fields": ["ibus", "name", "ide"],
				      "data": [[101, "Source", 3], [102, "Sink", 1]]
				    },
				    "load": {
				      "fields": ["ibus", "loadid", "pl", "ql"],
				      "data": [[102, "1", 500.0, 200.0]]
				    },
				    "generator": {
				      "fields": ["ibus", "machid"],
				      "data": [[101, "1"]]
				    },
				    "acline": {
				      "fields": ["ibus", "jbus", "ckt", "xpu"],
				      "data": [[101, 102, "1", 0.01]]
				    }
				  }
				}
				""");

		assertEquals(1_000.0, net.getBus("Bus101").getBaseVoltage());
		assertEquals(1_000.0, net.getBus("Bus102").getBaseVoltage());
		assertConverged(net);
	}

	@Test
	public void explicitZeroBaseKvSupportsPvVoltageControl() throws Exception {
		AclfNetwork net = parse("zero-base-pv.rawx", """
				{
				  "network": {
				    "caseid": {
				      "fields": ["sbase", "rev"],
				      "data": [100.0, 36]
				    },
				    "bus": {
				      "fields": ["ibus", "name", "baskv", "ide", "vm", "va"],
				      "data": [
				        [1, "Swing", 0.0, 3, 1.0, 0.0],
				        [2, "PV", 0.0, 2, 1.01, 0.0],
				        [3, "Load", 0.0, 1, 1.0, 0.0]
				      ]
				    },
				    "load": {
				      "fields": ["ibus", "loadid", "pl", "ql"],
				      "data": [[3, "1", 90.0, 30.0]]
				    },
				    "generator": {
				      "fields": ["ibus", "machid", "pg", "qg", "qt", "qb", "vs"],
				      "data": [
				        [1, "1", 40.0, 0.0, 9999.0, -9999.0, 1.0],
				        [2, "1", 50.0, 0.0, 100.0, -100.0, 1.01]
				      ]
				    },
				    "acline": {
				      "fields": ["ibus", "jbus", "ckt", "rpu", "xpu", "bpu"],
				      "data": [[1, 2, "1", 0.01, 0.10, 0.0], [2, 3, "1", 0.01, 0.10, 0.0]]
				    }
				  }
				}
				""");

		AclfBus pvBus = net.getBus("Bus2");
		assertEquals(1_000.0, pvBus.getBaseVoltage());
		assertTrue(pvBus.isGenPV());
		assertEquals(1.01, pvBus.getDesiredVoltMag(), 1.0E-12);
		assertConverged(net);
	}

	@Test
	public void standardRawxSolutionSettingsAreAvailableForReplay()
			throws Exception {
		AclfNetwork net = parse("solution-settings.rawx", """
				{
				  "general": {"version": "36.0"},
				  "network": {
				    "caseid": {
				      "fields": ["sbase", "rev"],
				      "data": [100.0, 36]
				    },
				    "general": {
				      "fields": ["thrshz", "pqbrak"],
				      "data": [0.0002, 0.65]
				    },
				    "newton": {
				      "fields": ["itmxn", "toln", "dvlim"],
				      "data": [25, 0.1, 0.2]
				    },
				    "solver": {
				      "fields": ["method", "actaps", "areain", "phshft", "dctaps", "swshnt", "nondiv"],
				      "data": ["FDNS", 0, 0, 0, 0, 0, 1]
				    },
				    "bus": {
				      "fields": ["ibus", "baskv", "ide", "vm", "va"],
				      "data": [[1, 230.0, 3, 1.0, 0.0]]
				    },
				    "generator": {
				      "fields": ["ibus", "machid"],
				      "data": [[1, "1"]]
				    }
				  }
				}
				""");

		Object initializer = net.getExtraInfo().get(
				LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY);
		assertNotNull(initializer);
		assertTrue(initializer instanceof PsseLoadflowSolutionSettings);
		PsseLoadflowSolutionSettings settings =
				(PsseLoadflowSolutionSettings) initializer;
		assertEquals(36, settings.sourceVersion());
		assertEquals(25, settings.newton().itmxn());
		assertEquals(0, settings.solver().actaps());

		LoadflowAlgorithm algorithm = LoadflowAlgoObjectFactory
				.createLoadflowAlgorithm(net);
		assertEquals(25, algorithm.getMaxIterations());
		assertEquals(0.001, algorithm.getTolerance(), 1.0E-12);
		settings.applyTo(algorithm, net,
				ApplicationPolicy.SAVED_SOLUTION_REPLAY);
		assertFalse(algorithm.getLfAdjAlgo().getVoltAdjConfig()
				.isXfrTapControl());
		assertFalse(algorithm.getLfAdjAlgo().getPowerAdjConfig()
				.isPsXfrPControl());
		assertFalse(algorithm.getLfAdjAlgo().getVoltAdjConfig()
				.isSwitchedShuntAdjust());
		assertTrue(algorithm.getNrMethodConfig().isNonDivergent());
	}

	private AclfNetwork parse(String fileName, String rawx) throws Exception {
		Path path = tempDir.resolve(fileName);
		Files.writeString(path, rawx);
		return new PSSEJsonDirectParser().parse(path.toString());
	}

	private static void assertConverged(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algorithm = LoadflowAlgoObjectFactory
				.createLoadflowAlgorithm(net);
		algorithm.setLfMethod(AclfMethodType.NR);
		algorithm.setTolerance(1.0E-8);
		algorithm.setMaxIterations(50);
		assertTrue(algorithm.loadflow(),
				() -> "Power flow failed: " + algorithm.getTerminationReport());
	}
}
