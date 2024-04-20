package org.interpss.core.adapter;

import org.interpss.core.adapter.ieee.IEEECommonFormatTest;
import org.interpss.core.adapter.ieee.IEEECommonFormat_CommaTest;
import org.interpss.core.adapter.internal.Bus1824Test;
import org.interpss.core.adapter.internal.IEEE14Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	IEEECommonFormat_CommaTest.class,
	IEEECommonFormatTest.class,
	
	IEEE14Test.class,
	Bus1824Test.class,
	//Bus6384Test.class,
	//Bus11856Test.class,
	
	//CR_UserTestCases.class,
})
public class CoreAdapterTestSuite {
}
