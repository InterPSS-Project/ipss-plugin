package org.interpss.core.adapter.ge;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Fast EPC subset for {@code EpcDirectParser} / {@code GEFormat} coverage.
 * Run: {@code mvn -pl ipss.test.plugin.core test -Dtest=GEAdapterTestSuite}
 */
@Suite
@SelectClasses({
	GESampleTestCases.class,
	EpcDirectParser_SectionGate_Test.class,
	Epc2k10kComparisonTest.class,
})
public class GEAdapterTestSuite {
}
