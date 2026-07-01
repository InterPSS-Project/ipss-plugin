package org.interpss.odm.mapper.impl.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.net.Area;
import com.interpss.core.net.Zone;

public class PSSEV36RawLabelMetadataMapperTest {

	@Test
	public void ieee9RawLabelsMapToCoreNameTagMetadata() throws Exception {
		AclfNetwork net = load("ieee9_v36_labeled.raw");

		assertEquals("bus_1_16.5kv", net.getBus("Bus1").getExtUID());
		assertEquals("[ bus_1_16.5kv]", net.getBus("Bus1").getDesc());

		AclfGen gen = net.getBus("Bus1").getContributeGenList().get(0);
		assertEquals("generator_1_1_16.5kv", gen.getExtUID());
		assertEquals("[ generator_1_1_16.5kv, gen_BUS-1_1_16.5kv ]", gen.getDesc());

		AclfLoad load = net.getBus("Bus5").getContributeLoadList().get(0);
		assertEquals("load_5_1_230kv", load.getExtUID());
		assertEquals("[ load_5_1_230kv, load_BUS-5_1_230kv ]", load.getDesc());

		AclfBranch line = branch(net, "Bus4", "Bus5", "0");
		assertEquals("line_4_5_0_230kv", line.getExtUID());
		assertEquals("[ line_4_5_0_230kv, branch_BUS-4_BUS-5_0_230kv ]", line.getDesc());

		AclfBranch transformer = branch(net, "Bus1", "Bus4", "1");
		assertEquals("transformer_1_4_1_16.5kv", transformer.getExtUID());
		assertEquals("[ transformer_1_4_1_16.5kv, xfmr_BUS-1_BUS-4_1_16.5kv ]", transformer.getDesc());
	}

	@Test
	public void texas2kRawLabelsMapToCoreNameTagMetadata() throws Exception {
		AclfNetwork net = load("Texas2k_series24_case1_2016summerPeak_v36_labeled.RAW");

		assertEquals(2000, net.getNoBus());
		assertEquals("bus_1001_115kv", net.getBus("Bus1001").getExtUID());
		assertEquals("[ bus_1001_115kv]", net.getBus("Bus1001").getDesc());

		AclfLoad load = net.getBus("Bus1001").getContributeLoadList().get(0);
		assertEquals("load_1001_1_115kv", load.getExtUID());
		assertEquals("[ load_1001_1_115kv, load_ODESSA_2_0_1001_1_115kv ]", load.getDesc());

		AclfGen gen = net.getBus("Bus1004").getContributeGenList().get(0);
		assertEquals("generator_1004_1_230kv", gen.getExtUID());
		assertEquals("[ generator_1004_1_230kv, gen_O_DONNELL__2_1004_1_230kv ]", gen.getDesc());

		AclfBranch line = branch(net, "Bus1001", "Bus1064", "1");
		assertEquals("line_1001_1064_1_115kv", line.getExtUID());
		assertEquals("[ line_1001_1064_1_115kv, branch_ODESSA_2_0_ODESSA_3_0_1001_1064_1_115kv ]", line.getDesc());

		AclfBranch transformer = branch(net, "Bus1004", "Bus1003", "1");
		assertEquals("transformer_1004_1003_1_230kv", transformer.getExtUID());
		assertEquals("[ transformer_1004_1003_1_230kv, xfmr_O_DONNELL__2_O_DONNELL__1_1004_1003_1_230kv ]", transformer.getDesc());

		SwitchedShunt switchedShunt = net.getBus("Bus1007").getSwitchedShunt("1");
		assertNotNull(switchedShunt);
		assertEquals("switched_shunt_1007_1_115kv", switchedShunt.getExtUID());
		assertEquals("[ switched_shunt_1007_1_115kv, swsh_VAN_HORN_0_1007_1_115kv ]", switchedShunt.getDesc());

		Area area = net.getArea("1");
		assertNotNull(area);
		assertEquals("area_1", area.getExtUID());
		assertEquals("[ area_1, area_FAR_WEST_1 ]", area.getDesc());

		Zone zone = net.getZone("1");
		assertNotNull(zone);
		assertEquals("zone_1", zone.getExtUID());
		assertEquals("[ zone_1, zone_BAY_CITY_1 ]", zone.getDesc());
	}

	private AclfNetwork load(String fileName) throws Exception {
		return IpssAdapter.importAclfNet(casePath(fileName))
				.setFormat(FileFormat.PSSE)
				.setPsseVersion(PsseVersion.PSSE_36)
				.load()
				.getImportedObj();
	}

	private String casePath(String fileName) {
		Path fromModule = Paths.get("..", "ipss.test.plugin.core", "testData", "adpter", "psse", "v36", fileName);
		if (Files.exists(fromModule)) {
			return fromModule.toString();
		}
		return Paths.get("ipss.test.plugin.core", "testData", "adpter", "psse", "v36", fileName).toString();
	}

	private AclfBranch branch(AclfNetwork net, String fromBusId, String toBusId, String circuitId) {
		AclfBranch branch = net.getBranch(fromBusId, toBusId, circuitId);
		if (branch == null) {
			branch = net.getBranch(toBusId, fromBusId, circuitId);
		}
		assertNotNull(branch, "Missing branch " + fromBusId + "->" + toBusId + "(" + circuitId + ")");
		return branch;
	}
}
