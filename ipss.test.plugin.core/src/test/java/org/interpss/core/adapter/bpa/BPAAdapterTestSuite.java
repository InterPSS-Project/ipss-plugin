package org.interpss.core.adapter.bpa;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Fast BPA NETWORK_DATA subset for {@code BPADirectParser} / {@code BPAFormat} coverage.
 * Run: {@code mvn -pl ipss.test.plugin.core test -Dtest=BPAAdapterTestSuite}
 */
@Suite
@SelectClasses({
	BPASampleTestCases.class,
	BPADirectParser_CardGate_Test.class,
	Bpa07c_0615_Test.class,
	BpaO7CTest.class,
})
public class BPAAdapterTestSuite {
}
