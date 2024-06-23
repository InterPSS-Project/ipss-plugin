package org.interpss._3phase;

import org.interpss._3phase.dataparser.TestODM3PhaseDstabMapper;
import org.interpss._3phase.dataparser.TestOpenDSSDataParser;
import org.interpss._3phase.model.TestPVDistGen3Phase;
import org.interpss._3phase.model.TestSinglePhaseACMotorModel;
import org.interpss._3phase.system.IEEE123Feeder_Dstab_Test;
import org.interpss._3phase.system.IEEE_13BusFeeder_Test;
import org.interpss._3phase.system.IEEE9Bus_3phase_LF_init_test;
import org.interpss._3phase.system.IEEE9_3Phase_1PAC_test;
import org.interpss._3phase.system.Test6BusFeeder;
import org.interpss._3phase.system.TestDistributionPowerflowAlgo;
import org.interpss._3phase.system.TestIEEETestFeederPowerFlow;
import org.interpss._3phase.system.ThreeBus_3Phase_Test;
import org.interpss._3phase.system.TwoBus_3Phase_Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;



@RunWith(Suite.class)
@SuiteClasses({
	
	//ODM
	TestODM3PhaseDstabMapper.class,
	
	// basic modeling
	TwoBus_3Phase_Test.class,
	ThreeBus_3Phase_Test.class,
	
	//OpenDSS data adapter
	TestOpenDSSDataParser.class,
	
	// Init from positive sequence load flow
	IEEE9Bus_3phase_LF_init_test.class,
	
	
	// distribution load flow algo
	 TestDistributionPowerflowAlgo.class,
	 TestIEEETestFeederPowerFlow.class,
	 
	// dynamic models
	TestSinglePhaseACMotorModel.class,
	IEEE9_3Phase_1PAC_test.class,
    TestPVDistGen3Phase.class,
    //Test3PhaseInductionMotor.class,
    
    
    //dynamic simulation
    Test6BusFeeder.class,
    IEEE_13BusFeeder_Test.class,
    IEEE123Feeder_Dstab_Test.class,
})
public class threePhaseTestSuite {

}
