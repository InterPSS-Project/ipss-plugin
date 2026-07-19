package org.interpss.core.adapter.psse.raw.aclf;

import org.interpss.core.aclf.PSSE_5Bus_SwitchedShunt_Test;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Fast PSS/E RAW (v30–v36) subset for {@code PSSEDirectParser} / adapter coverage.
 * Run: {@code mvn -pl ipss.test.plugin.core test -Dtest=PSSEAdapterTestSuite}
 */
@Suite
@SelectClasses({
	// v30 baseline
	PSSE_5Bus_TestCase.class,
	PSSE_IEEE9Bus_Test.class,
	// version matrix + gates
	PSSEV31_v36_Sample_Test.class,
	PSSEV31_v36_IEEE9_Test.class,
	PSSE_Savnw_v33_Test.class,
	PSSEDirectParser_VersionGate_Test.class,
	PSSE_5Bus_SwitchedShunt_Test.class,
	// auto-version / Bus0 regressions
	PsseVersionParserTest.class,
	PSSE_AutoVersion_Bus0_Regression_Test.class,
	// user cases
	CR_UserTestCases.class,
})
public class PSSEAdapterTestSuite {
}
