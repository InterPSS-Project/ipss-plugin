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
import org.interpss.core.adapter.psse.CR_UserTestCases;
import org.interpss.core.adapter.psse.GuideSampleTestCases;
import org.interpss.core.adapter.psse.Mod_SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.SixBus_DclfPsXfr;
import org.interpss.core.adapter.pwd.PWDIEEE14BusTestCase;
import org.interpss.core.adapter.ucte.UCTEFormatAusPowerTest;
import org.interpss.core.ca.IEEE14BusBreakerTest;
import org.interpss.core.ca.IEEE14BusBreaker_dclf_Test;
import org.interpss.core.ca.IEEE14BusBreaker_equivCABranch_Test;
import org.interpss.core.ca.IEEE14BusBreaker_islandBus_Test;
import org.interpss.core.ca.IEEE14BusBreaker_lf_Test;
import org.interpss.core.ca.SampleSwitchBreakerModelTest;
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
import org.interpss.odm.acsc.Acsc5Bus_ODM_TestCase;
import org.interpss.odm.psse.v30.GuideSample_TestCase;
import org.interpss.plugin.beanModel.JSONBeanTestCases;
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
	GuideSampleTestCases.class,
	SixBus_DclfPsXfr.class,
	Mod_SixBus_DclfPsXfr.class,
	PWDIEEE14BusTestCase.class,
	
	GESampleTestCases.class,
	
	IEEE14Test.class,
	Bus1824Test.class,
	Bus6384Test.class,
	Bus11856Test.class,
	
	GuideSample_TestCase.class,
	
	JSONBeanTestCases.class,
})
public class CorePluginTestSuite {
}
