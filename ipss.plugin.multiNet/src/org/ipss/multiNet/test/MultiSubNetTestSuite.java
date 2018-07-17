package org.ipss.multiNet.test;

import org.ipss.multiNet.test.trans_dist.TestTnDCombinedPowerflow;
import org.ipss.multiNet.test.trans_dist.TestTnD_IEEE300_13busFeeder;
import org.ipss.multiNet.test.trans_dist.TestTnD_IEEE39_123BusFeeder;
import org.ipss.multiNet.test.trans_dist.TestTnD_IEEE39_Feeder;
import org.ipss.multiNet.test.trans_dist.TestTnD_IEEE9_13busFeeder;
import org.ipss.multiNet.test.trans_dist.TestTnD_IEEE9_6BusFeeder;
import org.ipss.multiNet.test.trans_dist.TestTnD_IEEE9_8BusFeeder;
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
	   
	   // mixed modeling and Transmission and distribution co-simulation
	IEEE9_3Phase_1PAC_mnet_3ph3seq_test.class,
	TestTnD_IEEE9_6BusFeeder.class,
	TestTnD_IEEE9_8BusFeeder.class,
	TestTnD_IEEE9_13busFeeder.class,
	TestTnD_IEEE39_Feeder.class,
	TestTnD_IEEE39_123BusFeeder.class,
	TestTnD_IEEE300_13busFeeder.class,
	
	
	
})
public class MultiSubNetTestSuite {

}