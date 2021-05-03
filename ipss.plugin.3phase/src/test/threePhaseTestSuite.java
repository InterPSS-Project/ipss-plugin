package test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import test.dataparser.TestODM3PhaseDstabMapper;
import test.dataparser.TestOpenDSSDataParser;
import test.model.TestPVDistGen3Phase;
import test.model.TestSinglePhaseACMotorModel;
import test.system.IEEE123Feeder_Dstab_Test;
import test.system.IEEE9Bus_3phase_LF_init_test;
import test.system.IEEE9_3Phase_1PAC_test;
import test.system.IEEE_13BusFeeder_Test;
import test.system.Test3PhaseInductionMotor;
import test.system.Test6BusFeeder;
import test.system.TestDistributionPowerflowAlgo;
import test.system.TestIEEETestFeederPowerFlow;
import test.system.ThreeBus_3Phase_Test;
import test.system.TwoBus_3Phase_Test;


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
