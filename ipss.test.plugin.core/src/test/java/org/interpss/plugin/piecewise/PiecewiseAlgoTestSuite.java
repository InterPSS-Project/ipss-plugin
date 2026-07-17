package org.interpss.plugin.piecewise;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
	IEEE14TestSubAreaSearch.class,
	IEEE14TestAclfNetPiesewise.class,
	IEEE14TestAclfSubNetBuild.class,
	IEEE14TestAclfSubAreaBuild.class,
	
	Acsc5BusTestSubAreaNet.class,
	Acsc5BusTesPiecewiseAlgo.class,
	
	IEEE9BusTestDStabSubAreaNet.class,
})
public class PiecewiseAlgoTestSuite {
}
