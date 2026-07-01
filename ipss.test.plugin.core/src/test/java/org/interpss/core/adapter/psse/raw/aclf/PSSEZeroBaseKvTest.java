package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

public class PSSEZeroBaseKvTest extends CorePluginTestSetup {

	@Test
	public void importBusWithZeroBaseKv() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/PSSE_5Bus_ZeroBaseKv.raw")
				.setFormat(FileFormat.PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();

		AclfBus bus = net.getBus("Bus1");
		assertNotNull(bus);
		assertEquals(1000.0, bus.getBaseVoltage(), 0.0);
	}
}
