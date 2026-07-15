package org.interpss.core.opf;

import org.interpss.CorePluginTestSetup;

/**
 * TODO: These tests used OpfMatpowerAdapter and ODMOpfParserMapper from org.ieee.odm.
 * No direct OPF parser is currently available to replace the ODM-based OPF mapping pipeline.
 * Re-enable once a direct Matpower OPF parser is implemented.
 */
public class MatpowerOpfMapperTest extends CorePluginTestSetup {
	// @Test
	// public void rtsStyleExtensionsMapToOpfNetwork() throws InterpssException {
	// 	// Previously used: OpfMatpowerAdapter + ODMOpfParserMapper
	// 	// OpfNetwork net = mapMatpower("testData/matpower/case3bus_rts_extensions.m");
	// 	// assertEquals(3, net.getBusList().size());
	// 	// ...
	// }

	// @Test
	// public void optionalRtsGmlcMatpowerMapsToOpfNetwork() throws InterpssException {
	// 	// Previously used: OpfMatpowerAdapter + ODMOpfParserMapper
	// 	// ...
	// }
}
