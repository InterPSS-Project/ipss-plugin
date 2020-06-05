package org.interpss.threePhase.test;

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
	
	
	// Init from positive sequence load flow
	IEEE9Bus_3phase_LF_init_test.class,
	
	
	// distribution load flow algo
	 TestDistributionPowerflowAlgo.class,
	 
	// dynamic models
	TestSinglePhaseACMotorModel.class,
	IEEE9_3Phase_1PAC_test.class,
    TestPVDistGen3Phase.class,
    Test3PhaseInductionMotor.class,
    
    
    //dynamic simulation
    Test6BusFeeder.class,
   
})
public class threePhaseTestSuite {

}
