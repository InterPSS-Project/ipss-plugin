package org.interpss;

import org.interpss.core.aclf.IEEE14_3WXfrTest;
import org.interpss.core.adapter.bpa.BPASampleTestCases;
import org.interpss.core.adapter.ge.GESampleTestCases;
import org.interpss.core.adapter.ieee.IEEE009Bus_Test;
import org.interpss.core.adapter.ieee.IEEECommonFormatTest;
import org.interpss.core.adapter.ieee.IEEECommonFormat_CommaTest;
import org.interpss.core.adapter.internal.Bus11856Test;
import org.interpss.core.adapter.internal.Bus1824Test;
import org.interpss.core.adapter.internal.Bus6384Test;
import org.interpss.core.adapter.internal.IEEE14Test;
import org.interpss.core.adapter.psse.aclf.CR_UserTestCases;
import org.interpss.core.adapter.psse.aclf.GuideSample_TestCase;
import org.interpss.core.adapter.psse.aclf.Mod_SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.aclf.PSSE_5Bus_TestCase;
import org.interpss.core.adapter.psse.aclf.PSSE_IEEE9Bus_Test;
import org.interpss.core.adapter.psse.aclf.SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.acsc.Acsc5Bus_ODM_TestCase;
import org.interpss.core.adapter.psse.acsc.IEEE9Bus_Acsc_test;
import org.interpss.core.adapter.pwd.PWDIEEE14BusTestCase;
import org.interpss.core.adapter.ucte.UCTEFormatAusPowerTest;
import org.interpss.core.ca.IEEE14BusBreakerTest;
import org.interpss.core.ca.IEEE14BusBreaker_dclf_Test;
import org.interpss.core.ca.IEEE14BusBreaker_equivCABranch_Test;
import org.interpss.core.ca.IEEE14BusBreaker_islandBus_Test;
import org.interpss.core.ca.IEEE14BusBreaker_lf_Test;
import org.interpss.core.ca.SampleSwitchBreakerModelTest;
import org.interpss.core.mnet.MNet_IEEE14Bus_Test;
import org.interpss.dstab.DStab_2Bus;
import org.interpss.dstab.control.cml.block.DelayControlBlockTests;
import org.interpss.dstab.control.cml.block.FilterControlBlockTests;
import org.interpss.dstab.control.cml.block.IntegrationControlBlockTests;
import org.interpss.dstab.control.cml.block.PIControlBlockTests;
import org.interpss.dstab.control.cml.block.WashoutControlBlockTests;
import org.interpss.dstab.mach.EConstMachineTest;
import org.interpss.dstab.mach.Eq1Ed1MachineTest;
import org.interpss.dstab.mach.Eq1MachineCaseTest;
import org.interpss.dstab.mach.MachineSaturationTest;
import org.interpss.dstab.mach.RoundRotorMachineTest;
import org.interpss.dstab.mach.SalientPoleMachineTest;
import org.interpss.plugin.beanModel.AclfBeanMapperTest;
import org.interpss.spring.SimuAppCtxTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	// aclf
	IEEE14_3WXfrTest.class,
	
	// Dclf
	Mod_SixBus_DclfPsXfr.class,
	SixBus_DclfPsXfr.class,
	IEEE14BusBreaker_dclf_Test.class,
	IEEE14BusBreaker_equivCABranch_Test.class,
	IEEE14BusBreaker_islandBus_Test.class,
	
	// small Z branch
	SampleSwitchBreakerModelTest.class,
	IEEE14BusBreaker_lf_Test.class,
	IEEE14BusBreakerTest.class,
	
	// DStab controller building blocks
	DelayControlBlockTests.class,
	FilterControlBlockTests.class,
	IntegrationControlBlockTests.class,
	PIControlBlockTests.class,
	WashoutControlBlockTests.class,
	
	// DStab Machine
	Eq1Ed1MachineTest.class,
	EConstMachineTest.class,
	Eq1MachineCaseTest.class,
	MachineSaturationTest.class,
	RoundRotorMachineTest.class,
	SalientPoleMachineTest.class,
	
	// Ascsc ODM
	Acsc5Bus_ODM_TestCase.class,
	
	// DStab ODM
	DStab_2Bus.class,
	
	// Spring 
	SimuAppCtxTest.class,	

	// core file adapter
	IEEECommonFormat_CommaTest.class,
	IEEECommonFormatTest.class,
	IEEE009Bus_Test.class,
	BPASampleTestCases.class,
	UCTEFormatAusPowerTest.class,
	CR_UserTestCases.class,
	GuideSample_TestCase.class,
	PSSE_5Bus_TestCase.class,
	SixBus_DclfPsXfr.class,
	Mod_SixBus_DclfPsXfr.class,
	PWDIEEE14BusTestCase.class,
	PSSE_IEEE9Bus_Test.class,
	
	Acsc5Bus_ODM_TestCase.class,
	IEEE9Bus_Acsc_test.class,
	
	GESampleTestCases.class,
	
	IEEE14Test.class,
	Bus1824Test.class,
	Bus6384Test.class,
	Bus11856Test.class,
	
	GuideSample_TestCase.class,
	
	AclfBeanMapperTest.class,
	
	MNet_IEEE14Bus_Test.class,
})
public class CorePluginTestSuite {
}
