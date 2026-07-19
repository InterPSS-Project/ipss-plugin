package org.interpss.core.adapter.ge;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Fast GE PSLF EPC subset for {@code GEPslfDirectParser} / {@code GEFormat} coverage.
 * Run: {@code mvn -pl ipss.test.plugin.core test -Dtest=GEAdapterTestSuite}
 */
@Suite
@SelectClasses({
	GESampleTestCases.class,
	GEPslfDirectParser_SectionGate_Test.class,
})
public class GEAdapterTestSuite {
}
