package org.interpss.core.adapter.pwd;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Fast PowerWorld AUX subset for {@code PWDDirectParser} / {@code PWDFormat} coverage.
 * Run: {@code mvn -pl ipss.test.plugin.core test -Dtest=PWDAdapterTestSuite}
 */
@Suite
@SelectClasses({
	PWDIEEE14BusTestCase.class,
	PWDDirectParser_ObjectGate_Test.class,
	SixBus_DclfPsXfr_pwd.class,
	SixBus_XfrControl_pwd.class,
})
public class PWDAdapterTestSuite {
}
