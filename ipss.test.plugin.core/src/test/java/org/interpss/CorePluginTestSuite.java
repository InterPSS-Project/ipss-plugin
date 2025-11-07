package org.interpss;

import org.interpss.core.aclf.IEEE14_3WXfrTest;
import org.interpss.core.aclf.IEEE14_YMatrixSetTest;
import org.interpss.core.aclf.IEEE9_MultiGenTest;
import org.interpss.core.aclf.IEEE9_MultiLoadTest;
import org.interpss.core.aclf.Kundur_2Area_LCCHVDC2T_Aclf_Test;
import org.interpss.core.aclf.SwingBusSubAreaTest;
import org.interpss.core.aclf.svc.IEEE14_PVLimit_SVCTest;
import org.interpss.core.adapter.bpa.BPASampleTestCases;
import org.interpss.core.adapter.ge.GESampleTestCases;
import org.interpss.core.adapter.ieee.IEEE009Bus_Test;
import org.interpss.core.adapter.ieee.IEEE118Bus_Test;
import org.interpss.core.adapter.ieee.IEEE14BusTest;
import org.interpss.core.adapter.ieee.IEEECommonFormat_CommaTest;
import org.interpss.core.adapter.internal.Bus11856Test;
import org.interpss.core.adapter.internal.Bus1824Test;
import org.interpss.core.adapter.internal.Bus6384Test;
import org.interpss.core.adapter.internal.IEEE14Test;
import org.interpss.core.adapter.psse.compare.PSSE_ACTIVSg2000BusCompare_Test;
import org.interpss.core.adapter.psse.compare.PSSE_ACTIVSg25kObjectCompareTest;
import org.interpss.core.adapter.psse.largeNet.PSSE_ACTIVSg2000Bus_Test;
import org.interpss.core.adapter.psse.largeNet.PSSE_ACTIVSg25kBus_Test;
import org.interpss.core.adapter.psse.raw.aclf.CR_UserTestCases;
import org.interpss.core.adapter.psse.raw.aclf.GuideSample_TestCase;
import org.interpss.core.adapter.psse.raw.aclf.Kundur_2Area_LCCHVDC2T_Test;
import org.interpss.core.adapter.psse.raw.aclf.Kundur_2Area_VSCHVDC2T_Test;
import org.interpss.core.adapter.psse.raw.aclf.Mod_SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.raw.aclf.PSSE_5Bus_TestCase;
import org.interpss.core.adapter.psse.raw.aclf.PSSE_IEEE9Bus_Test;
import org.interpss.core.adapter.psse.raw.aclf.SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.raw.acsc.IEEE39Bus_Acsc_Test;
import org.interpss.core.adapter.psse.raw.acsc.IEEE9Bus_Acsc_Test;
import org.interpss.core.adapter.psse.raw.dstab.IEEE9_Dstab_Adapter_Test;
import org.interpss.core.adapter.pwd.PWDIEEE14BusTestCase;
import org.interpss.core.adapter.ucte.UCTEFormatAusPowerTest;
import org.interpss.core.ca.IEEE14_N1Scan_Test;
import org.interpss.core.ca.Ieee14_CA_Test;
import org.interpss.core.ca.Ieee14_GSF_Test;
import org.interpss.core.dclf.IEEE14_Dclf_Test;
import org.interpss.core.dclf.edclf.IEEE118_EDclf_Test;
import org.interpss.core.dclf.edclf.IEEE14_EDclf_Test;
import org.interpss.core.dclf.edclf.IEEE39_EDclf_Test;
import org.interpss.core.dist.DistSys_Test;
import org.interpss.core.dstab.DStab_IEEE9Bus_Test;
import org.interpss.core.dstab.cml.block.DelayControlBlockTests;
import org.interpss.core.dstab.cml.block.FilterControlBlockTests;
import org.interpss.core.dstab.cml.block.IntegrationControlBlockTests;
import org.interpss.core.dstab.cml.block.PIControlBlockTests;
import org.interpss.core.dstab.cml.block.WashoutControlBlockTests;
import org.interpss.core.dstab.cml.controller.AnnotateParserTests;
import org.interpss.core.dstab.cml.controller.AnnotationExciterTests;
import org.interpss.core.dstab.dynLoad.TestCMPLDWGModel;
import org.interpss.core.dstab.dynLoad.TestCMPLDWModel;
import org.interpss.core.dstab.dynLoad.TestCalBusDStabLoad;
import org.interpss.core.dstab.dynLoad.TestDER_AModel;
import org.interpss.core.dstab.dynLoad.TestInductionMotorModel;
import org.interpss.core.dstab.dynLoad.TestLd1pacModel;
import org.interpss.core.dstab.mach.EConstMachineTest;
import org.interpss.core.dstab.mach.MachineSaturationTest;
import org.interpss.core.dstab.mach.RoundRotorMachineTest;
import org.interpss.core.dstab.mach.SMIB_Gen_Test;
import org.interpss.core.dstab.mach.SalientPoleMachineTest;
import org.interpss.core.optadj.IEEE14_OptAdj_BasecaseSSAResult_Test;
import org.interpss.core.optadj.IEEE14_OptAdj_Basecase_Test;
import org.interpss.core.optadj.IEEE14_OptAdj_N1ScanSSAResult_Test;
import org.interpss.core.optadj.IEEE14_OptAdj_N1Scan_Test;
import org.interpss.core.script.mvel.MvelExprEval_Test;
import org.interpss.core.zeroz.IEEE14ZeroZBranchAclfTest;
import org.interpss.core.zeroz.IEEE14ZeroZBranchDeconsolidateTest;
import org.interpss.core.zeroz.ZBrAclfDeconOutputTest;
import org.interpss.core.zeroz.ZeroZBranchNetUtilTest;
import org.interpss.core.zeroz.algo.IEEE9Bus_ZbrNRSolver_Test;
import org.interpss.core.zeroz.topo.IEEE14ZeroZBranchFuncLoopTest;
import org.interpss.core.zeroz.topo.IEEE14ZeroZBranchFuncTest;
import org.interpss.core.zeroz.topo.ZeroZBranchFuncTest;
import org.interpss.plugin.beanModel.AclfBeanMapperTest;
import org.interpss.plugin.lfGCtrl.SwitchedShuntGControlTest;
import org.interpss.plugin.piecewise.Acsc5BusTestSubAreaNet;
import org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise;
import org.interpss.plugin.piecewise.IEEE14TestAclfSubAreaBuild;
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
	IEEE14_YMatrixSetTest.class,
	SwingBusSubAreaTest.class,
	Kundur_2Area_LCCHVDC2T_Aclf_Test.class,
	Kundur_2Area_LCCHVDC2T_Test.class,
	Kundur_2Area_VSCHVDC2T_Test.class,

	// SVC and PV limit
	IEEE14_PVLimit_SVCTest.class,
	
	// large PSSE network
	PSSE_ACTIVSg2000Bus_Test.class,
	PSSE_ACTIVSg25kBus_Test.class,
	
	// Compare
	PSSE_ACTIVSg25kObjectCompareTest.class,
	PSSE_ACTIVSg2000BusCompare_Test.class,
	
	// ZeroZ branch
	ZeroZBranchNetUtilTest.class,
	ZeroZBranchFuncTest.class,
	IEEE14ZeroZBranchFuncTest.class,
	IEEE14ZeroZBranchFuncLoopTest.class,
	IEEE14ZeroZBranchAclfTest.class,
	IEEE14ZeroZBranchDeconsolidateTest.class,
	ZBrAclfDeconOutputTest.class,
	IEEE9Bus_ZbrNRSolver_Test.class,
	
	// acsc
	IEEE9Bus_Acsc_Test.class,
	IEEE39Bus_Acsc_Test.class,
	
	// Dclf
	Mod_SixBus_DclfPsXfr.class,
	SixBus_DclfPsXfr.class,
	//IEEE14BusBreaker_dclf_Test.class,
	//IEEE14BusBreaker_equivCABranch_Test.class,
	
	// ca
	Ieee14_CA_Test.class,
	//ieee14_CAClosurePSSL_Test.class,
	Ieee14_GSF_Test.class,
	IEEE14_N1Scan_Test.class,
	
	// EDclf
	IEEE14_Dclf_Test.class,
	IEEE14_EDclf_Test.class,
	IEEE39_EDclf_Test.class,
	IEEE118_EDclf_Test.class,
	
	// Optimization adjustment
	IEEE14_OptAdj_Basecase_Test.class,
	IEEE14_OptAdj_N1Scan_Test.class,
	IEEE14_OptAdj_BasecaseSSAResult_Test.class,
	IEEE14_OptAdj_N1ScanSSAResult_Test.class,
	
	// small Z branch
	//SampleSwitchBreakerModelTest.class,
	//IEEE14BusBreaker_lf_Test.class,
	//IEEE14BusBreakerTest.class,
	
	// Ascsc ODM
	//Acsc5Bus_ODM_TestCase.class,

	// SE
	//SE_IEEE118Test.class,
	
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
	TestDER_AModel.class,
	TestCMPLDWModel.class,
	TestCMPLDWGModel.class,
	
	TestCalBusDStabLoad.class,


	// DStab ODM
	//TODO ODM file missing Gen sourceZ or genPosZ
	//DStab_2Bus.class,
	
	//DStab PSS/E
	DStab_IEEE9Bus_Test.class,
	Kundur_2Area_VSCHVDC2T_Test.class,
	
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
	//DistLF14Bus_PathLF_Test.class,
	//DistLF14BusTest.class,
	//DistSample2BusTest.class,
	DistSys_Test.class,	
	
	// DC System
	//DcSample_2BusTest.class,
	//Inverter_2BusTest.class,
	//PVModelList_2BusTest.class,
	
	//POC_Test1.class,
	//POC_Test2_1.class,
	//POC_Test2_2.class,
	//POC_Test2_3.class,
	
	// core file adapter
	IEEECommonFormat_CommaTest.class,
	//IEEECommonFormatTest.class,
	IEEE009Bus_Test.class,
	IEEE14BusTest.class,
	IEEE118Bus_Test.class,
	BPASampleTestCases.class,
	UCTEFormatAusPowerTest.class,

	SixBus_DclfPsXfr.class,
	PWDIEEE14BusTestCase.class,

	//PSSE Raw
	//Bus42_3winding.class, // this is a bug
	CR_UserTestCases.class,
	GuideSample_TestCase.class,
	Mod_SixBus_DclfPsXfr.class,
	PSSE_5Bus_TestCase.class,
	PSSE_IEEE9Bus_Test.class,
	IEEE9_Dstab_Adapter_Test.class,

	// PSSE Global Adjustment Control
	SwitchedShuntGControlTest.class,
	
	//Acsc5Bus_ODM_TestCase.class,
	IEEE9Bus_Acsc_Test.class,
	
	GESampleTestCases.class,
	
	IEEE14Test.class,
	Bus1824Test.class,
	Bus6384Test.class,
	Bus11856Test.class,
	
	GuideSample_TestCase.class,
	
	AclfBeanMapperTest.class,
	
	
	// Piecewise Algorithm
	IEEE14TestSubAreaSearch.class,
	//IEEE14TestAclfNetPiesewise.class,
	IEEE14TestAclfSubNetBuild.class,
	IEEE14TestAclfSubAreaBuild.class,
	
	Acsc5BusTestSubAreaNet.class,
	//Acsc5BusTesPiecewiseAlgo.class,
	
	IEEE9BusTestDStabSubAreaNet.class,
	
	// Mvel Expression
	MvelExprEval_Test.class,
})
public class CorePluginTestSuite {
}
