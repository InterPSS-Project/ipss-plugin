package org.ipss.multiNet.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	TestMaxtrix3x3Multiply.class,
	TestSubNetEquiv.class,
	TestAddDummyBus.class,
	TestNetworkEquivUtil.class,
	TestMultiNetDStab.class,
	TestMultiNetDStabSimuHelper.class,
	TestMultiNet3Ph3SeqSimHelper.class,
	
})
public class MultiSubNetTestSuite {

}