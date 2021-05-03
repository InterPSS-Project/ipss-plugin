package org.interpss;

import org.interpss.core.aclf.IEEE14_3WXfrTest;
import org.interpss.core.aclf.IEEE9_MultiGenTest;
import org.interpss.core.aclf.IEEE9_MultiLoadTest;
import org.interpss.core.adapter.bpa.BPASampleTestCases;
import org.interpss.core.adapter.ge.GESampleTestCases;
import org.interpss.core.adapter.ieee.IEEE009Bus_Test;
import org.interpss.core.adapter.ieee.IEEECommonFormatTest;
import org.interpss.core.adapter.ieee.IEEECommonFormat_CommaTest;
import org.interpss.core.adapter.internal.Bus11856Test;
import org.interpss.core.adapter.internal.Bus1824Test;
import org.interpss.core.adapter.internal.Bus6384Test;
import org.interpss.core.adapter.internal.IEEE14Test;
import org.interpss.core.adapter.odm.acsc.Acsc5Bus_ODM_TestCase;
import org.interpss.core.adapter.odm.dist.DistLF14BusTest;
import org.interpss.core.adapter.odm.dist.DistLF14Bus_PathLF_Test;
import org.interpss.core.adapter.odm.dist.DistSample2BusTest;
import org.interpss.core.adapter.psse.aclf.CR_UserTestCases;
import org.interpss.core.adapter.psse.aclf.GuideSample_TestCase;
import org.interpss.core.adapter.psse.aclf.Kunder_2Area_VSCHVDC2T_Test;
import org.interpss.core.adapter.psse.aclf.Mod_SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.aclf.PSSE_5Bus_TestCase;
import org.interpss.core.adapter.psse.aclf.PSSE_IEEE9Bus_Test;
import org.interpss.core.adapter.psse.aclf.SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.acsc.IEEE9Bus_Acsc_test;
import org.interpss.core.adapter.psse.dstab.IEEE9_Dstab_Adapter_Test;
import org.interpss.core.adapter.pwd.PWDIEEE14BusTestCase;
import org.interpss.core.adapter.ucte.UCTEFormatAusPowerTest;
import org.interpss.core.ca.IEEE14BusBreakerTest;
import org.interpss.core.ca.IEEE14BusBreaker_dclf_Test;
import org.interpss.core.ca.IEEE14BusBreaker_equivCABranch_Test;
import org.interpss.core.ca.IEEE14BusBreaker_islandBus_Test;
import org.interpss.core.ca.IEEE14BusBreaker_lf_Test;
import org.interpss.core.ca.SampleSwitchBreakerModelTest;
import org.interpss.core.dcsys.DcSample_2BusTest;
import org.interpss.core.dcsys.Inverter_2BusTest;
import org.interpss.core.dcsys.POC_Test1;
import org.interpss.core.dcsys.POC_Test2_1;
import org.interpss.core.dcsys.POC_Test2_2;
import org.interpss.core.dcsys.POC_Test2_3;
import org.interpss.core.dcsys.PVModelList_2BusTest;
import org.interpss.core.dist.DistSys_Test;
import org.interpss.core.dstab.DStab_IEEE9Bus_Test;
import org.interpss.core.dstab.cml.block.DelayControlBlockTests;
import org.interpss.core.dstab.cml.block.FilterControlBlockTests;
import org.interpss.core.dstab.cml.block.IntegrationControlBlockTests;
import org.interpss.core.dstab.cml.block.PIControlBlockTests;
import org.interpss.core.dstab.cml.block.WashoutControlBlockTests;
import org.interpss.core.dstab.cml.controller.AnnotateParserTests;
import org.interpss.core.dstab.cml.controller.AnnotationExciterTests;
import org.interpss.core.dstab.dynLoad.TestCalBusDStabLoad;
import org.interpss.core.dstab.dynLoad.TestInductionMotorModel;
import org.interpss.core.dstab.dynLoad.TestLd1pacModel;
import org.interpss.core.dstab.mach.EConstMachineTest;
import org.interpss.core.dstab.mach.Eq1Ed1MachineTest;
import org.interpss.core.dstab.mach.Eq1MachineCaseTest;
import org.interpss.core.dstab.mach.MachineSaturationTest;
import org.interpss.core.dstab.mach.RoundRotorMachineTest;
import org.interpss.core.dstab.mach.SMIB_Gen_Test;
import org.interpss.core.dstab.mach.SalientPoleMachineTest;
import org.interpss.plugin.beanModel.AclfBeanMapperTest;
import org.interpss.plugin.piecewise.Acsc5BusTesPiecewiseAlgo;
import org.interpss.plugin.piecewise.Acsc5BusTestSubAreaNet;
import org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise;
import org.interpss.plugin.piecewise.IEEE14TestAclfSubNetBuild;
import org.interpss.plugin.piecewise.IEEE14TestSubAreaSearch;
import org.interpss.plugin.piecewise.IEEE9BusTestDStabSubAreaNet;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	// aclf
	IEEE14_3WXfrTest.class,
	IEEE9_MultiGenTest.class,
	IEEE9_MultiLoadTest.class,
	
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
	
	// Ascsc ODM
	Acsc5Bus_ODM_TestCase.class,

	// DStab controller building blocks
	DelayControlBlockTests.class,
	FilterControlBlockTests.class,
	IntegrationControlBlockTests.class,
	PIControlBlockTests.class,
	WashoutControlBlockTests.class,
	
	// DStab Machine
	//Eq1Ed1MachineTest.class,
	EConstMachineTest.class,
	//Eq1MachineCaseTest.class,
	MachineSaturationTest.class,
	RoundRotorMachineTest.class,
	SalientPoleMachineTest.class,
	SMIB_Gen_Test.class,
	
	//DStab dynamic devic model
	TestInductionMotorModel.class,
	TestLd1pacModel.class,
	
	TestCalBusDStabLoad.class,

	// DStab ODM
	//TODO ODM file missing Gen sourceZ or genPosZ
	//DStab_2Bus.class,
	
	//DStab PSS/E
	DStab_IEEE9Bus_Test.class,
	Kunder_2Area_VSCHVDC2T_Test.class,
	
	// CML
	DelayControlBlockTests.class,
	FilterControlBlockTests.class,
	//GainBlockExtensionTests.class,
	IntegrationControlBlockTests.class,
	PIControlBlockTests.class,
	WashoutControlBlockTests.class,
	
	AnnotateParserTests.class,
	AnnotationExciterTests.class,	
	
	// Dist
	DistLF14Bus_PathLF_Test.class,
	DistLF14BusTest.class,
	DistSample2BusTest.class,
	DistSys_Test.class,	
	
	// DC System
	DcSample_2BusTest.class,
	Inverter_2BusTest.class,
	PVModelList_2BusTest.class,
	
	POC_Test1.class,
	POC_Test2_1.class,
	POC_Test2_2.class,
	POC_Test2_3.class,
	
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
	IEEE9_Dstab_Adapter_Test.class,
	
	Acsc5Bus_ODM_TestCase.class,
	IEEE9Bus_Acsc_test.class,
	
	GESampleTestCases.class,
	
	IEEE14Test.class,
	Bus1824Test.class,
	Bus6384Test.class,
	Bus11856Test.class,
	
	GuideSample_TestCase.class,
	
	AclfBeanMapperTest.class,
	
	
	// Piecewise Algorithm
	IEEE14TestSubAreaSearch.class,
	IEEE14TestAclfNetPiesewise.class,
	IEEE14TestAclfSubNetBuild.class,
	
	Acsc5BusTestSubAreaNet.class,
	Acsc5BusTesPiecewiseAlgo.class,
	
	IEEE9BusTestDStabSubAreaNet.class,
})
public class CorePluginTestSuite {
}
