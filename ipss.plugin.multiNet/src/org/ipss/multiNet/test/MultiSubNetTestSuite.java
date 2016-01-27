package org.ipss.multiNet.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	// test basic functions or utils
	TestSubNetEquiv.class,
	TestAddDummyBus.class,
	TestNetworkEquivUtil.class,
	TestMultiNetDStab.class,
	TestMultiNetDStabSimuHelper.class,
	TestMultiNet3Ph3SeqSimHelper.class,
	
	// test multiNet power flow
	TestTnDCombinedPowerflow.class,
	
	// test  multiNet dynamic simulation
	 
	   // positive sequence base and mixed modeling
	//TestIEEE39_MultiNet3ph3seqDstab.class,
	   
	   // mixed modeling
	IEEE9_3Phase_1PAC_mnet_3ph3seq_test.class,
	
})
public class MultiSubNetTestSuite {

}