package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.junit.jupiter.api.Test;

public class DistOpfOpenDssImportTest {

	@Test
	public void extractsExistingOpenDssIeee123Feeder() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master_Modified_v2.dss");
		parser.calcVoltageBases();
		parser.convertActualValuesToPU(1.0);

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);

		assertEquals("150", data.getSwingBusId());
		assertTrue(data.getBuses().size() > 100);
		assertTrue(data.getBranches().size() > 100);
		assertEquals(data.getBuses().size() - 1, data.getBranches().size());
	}

	@Test
	public void extractsExistingOpenDssIeee13FeederForDistOpf() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData/feeder/IEEE13", "IEEE13Nodeckt.dss");
		parser.calcVoltageBases();
		parser.convertActualValuesToPU(1.0);

		DStabNetwork3Phase distNet = parser.getDistNetwork();
		DistOpfModelData data = new DistOpfModelDataExtractor().extract(distNet);

		assertTrue(data.getBuses().size() >= 13);
		assertEquals(data.getBuses().size() - 1, data.getBranches().size());
	}
}
