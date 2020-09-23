package org.interpss.multiNet.test;

import org.interpss.multiNet.test.trans_dist.TestTnDCombinedPowerflow;
import org.interpss.multiNet.test.trans_dist.TestTnD_IEEE300_13busFeeder;
import org.interpss.multiNet.test.trans_dist.TestTnD_IEEE39_123BusFeeder;
import org.interpss.multiNet.test.trans_dist.TestTnD_IEEE39_Feeder;
import org.interpss.multiNet.test.trans_dist.TestTnD_IEEE9_13busFeeder;
import org.interpss.multiNet.test.trans_dist.TestTnD_IEEE9_6BusFeeder;
import org.interpss.multiNet.test.trans_dist.TestTnD_IEEE9_8BusFeeder;
import org.interpss.multiNet.test.unit.IEEE9_3Phase_1PAC_mnet_3ph3seq_test;
import org.interpss.multiNet.test.unit.TestAddDummyBus;
import org.interpss.multiNet.test.unit.TestMultiNet3Ph3SeqSimHelper;
import org.interpss.multiNet.test.unit.TestMultiNetDStab;
import org.interpss.multiNet.test.unit.TestMultiNetDStabSimuHelper;
import org.interpss.multiNet.test.unit.TestNetworkEquivUtil;
import org.interpss.multiNet.test.unit.TestSubNetEquiv;
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