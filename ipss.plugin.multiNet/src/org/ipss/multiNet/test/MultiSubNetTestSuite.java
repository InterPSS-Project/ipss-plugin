package org.ipss.multiNet.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	TestSubNetEquiv.class,
	TestAddDummyBus.class,
	TestMultiNetDStab.class,
	
})
public class MultiSubNetTestSuite {

}

