package org.interpss.threePhase;

import org.interpss.threePhase.dataparser.TestODM3PhaseDstabMapper;
import org.interpss.threePhase.dataparser.TestOpenDSSDataParser;
import org.interpss.threePhase.model.Test3PhaseInductionMotor;
import org.interpss.threePhase.model.TestPVDistGen3Phase;
import org.interpss.threePhase.model.TestSinglePhaseACMotorModel;
import org.interpss.threePhase.sparse.Matrix3x3.TestSparseEqnComplexMatrix3x3Impl;
import org.interpss.threePhase.system.IEEE123Feeder_Dstab_Test;
import org.interpss.threePhase.system.IEEE9Bus_3phase_LF_init_test;
import org.interpss.threePhase.system.IEEE9_3Phase_1PAC_test;
import org.interpss.threePhase.system.IEEE_13BusFeeder_Test;
import org.interpss.threePhase.system.Test6BusFeeder;
import org.interpss.threePhase.system.TestDistributionPowerflowAlgo;
import org.interpss.threePhase.system.TestIEEETestFeederPowerFlow;
import org.interpss.threePhase.system.ThreeBus_3Phase_Test;
import org.interpss.threePhase.system.TwoBus_3Phase_Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;



@RunWith(Suite.class)
@SuiteClasses({

	//ODM
	TestODM3PhaseDstabMapper.class,
	TestOpenDSSDataParser.class,

	//maxtrix3x3
	TestSparseEqnComplexMatrix3x3Impl.class,

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
    TestSinglePhaseACMotorModel.class,
    Test3PhaseInductionMotor.class,


    //dynamic simulation of distribution feeders
    Test6BusFeeder.class,
    IEEE_13BusFeeder_Test.class,
    IEEE123Feeder_Dstab_Test.class,

    //dynamic simulation of transmission systems


})
public class threePhaseTestSuite {

}
