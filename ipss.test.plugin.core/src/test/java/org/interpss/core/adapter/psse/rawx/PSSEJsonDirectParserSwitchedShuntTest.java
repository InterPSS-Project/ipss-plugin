package org.interpss.core.adapter.psse.rawx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.SwitchedShunt;

public class PSSEJsonDirectParserSwitchedShuntTest extends CorePluginTestSetup {

	@TempDir
	Path tempDir;

	@Test
	public void inactiveSwitchedShuntKeepsSavedBinitWithoutApplyingIt() throws Exception {
		Path rawx = tempDir.resolve("inactive-swshunt.rawx");
		Files.writeString(rawx, """
				{
				  "network": {
				    "caseid": {
				      "fields": ["ic", "sbase", "rev"],
				      "data": [[0, 100.0, 35]]
				    },
				    "bus": {
				      "fields": ["ibus", "name", "baskv", "ide", "area", "zone", "owner", "vm", "va"],
				      "data": [[1, "BUS-1", 230.0, 1, 1, 1, 1, 1.0, 0.0]]
				    },
				    "swshunt": {
				      "fields": ["ibus", "shntid", "modsw", "adjm", "stat", "vswhi", "vswlo", "swreg", "nreg", "rmpct", "rmidnt", "binit", "s1", "n1", "b1"],
				      "data": [[1, "1", 1, 0, 0, 1.05, 0.95, 0, 0, 50.0, "", 25.0, 1, 1, 25.0]]
				    }
				  }
				}
				""");

		AclfNetwork net = new PSSEJsonDirectParser().parse(rawx.toString());
		AclfBus bus = net.getBus("Bus1");
		assertNotNull(bus);
		assertTrue(bus.isSwitchedShunt());

		SwitchedShunt shunt = bus.getFirstSwitchedShunt(false);
		assertNotNull(shunt);
		assertFalse(shunt.isActive());
		assertEquals(0.25, shunt.getBInit(), 1.0E-10);
		assertEquals(0.0, shunt.getBActual(), 1.0E-10);
		assertEquals(50.0, shunt.getRemoteControlPercentage(), 1.0E-10);
	}
}
