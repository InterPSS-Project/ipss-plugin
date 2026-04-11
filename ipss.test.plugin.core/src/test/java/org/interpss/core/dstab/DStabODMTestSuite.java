package org.interpss.core.dstab;

import org.interpss.core.adapter.odm.dstab.DStab_2Bus;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
	DStab_2Bus.class,
})
public class DStabODMTestSuite {
}
