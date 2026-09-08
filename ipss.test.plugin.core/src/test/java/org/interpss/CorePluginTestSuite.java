package org.interpss;

import org.interpss.core.aclf.IEEE14_3WXfrTest;
import org.interpss.core.aclf.IEEE14_YMatrixSetTest;
import org.interpss.core.aclf.IEEE9_MultiGenTest;
import org.interpss.core.aclf.IEEE9_MultiLoadTest;
import org.interpss.core.aclf.Kundur_2Area_LCCHVDC2T_Aclf_Test;
import org.interpss.core.aclf.SwingBusSubAreaTest;
import org.interpss.core.algo.cpf.ContinuationPowerFlowPsseTest;
import org.interpss.core.aclf.svc.IEEE14_PVLimit_SVCTest;
import org.interpss.core.adapter.bpa.BPADirectParser_CardGate_Test;
import org.interpss.core.adapter.bpa.BPASampleTestCases;
import org.interpss.core.adapter.bpa.Bpa07c_0615_Test;
import org.interpss.core.adapter.bpa.BpaO7CTest;
import org.interpss.core.adapter.builder.aclf.AclfNetworkBuilder3WAndFinalizeTest;
import org.interpss.core.adapter.builder.aclf.AclfNetworkBuilderAdjDeviceTest;
import org.interpss.core.adapter.builder.aclf.AclfNetworkBuilderBranchTest;
import org.interpss.core.adapter.builder.aclf.AclfNetworkBuilderCoreTest;
import org.interpss.core.adapter.builder.aclf.AclfNetworkBuilderHvdcTest;
import org.interpss.core.adapter.builder.acsc.AcscNetworkBuilderBranchTest;
import org.interpss.core.adapter.builder.acsc.AcscNetworkBuilderCoreTest;
import org.interpss.core.adapter.builder.acsc.AcscNetworkBuilderFinalizeTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderExciterTest;
import org.interpss.core.adapter.builder.dstab.Exdc2aOpenSourceEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ieeex1AndesEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ieeet4OpenSourceEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ac8bAndesEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ac7bDynawoEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ac7bImportTest;
import org.interpss.core.adapter.builder.dstab.RexsysExciterTest;
import org.interpss.core.adapter.builder.dstab.Esac6aExciterTest;
import org.interpss.core.adapter.builder.dstab.Dc4bExciterTest;
import org.interpss.core.adapter.builder.dstab.St6bExciterTest;
import org.interpss.core.adapter.builder.dstab.Esst2aExciterTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac5aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderExac1Test;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsurryTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc1cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc2cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc3cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc4cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc5cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc6cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc7cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc8cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderExac1aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderExac2Test;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac1aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac2aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderGovernorTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderIeeeg3Test;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderIeeeg3dTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderWesgovdTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderDegov1dTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderPidgovdTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderTgov3dTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderHygov2dTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderWpidhydTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderGastwddTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderGast2adTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderMachineTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderStabilizerTest;
import org.interpss.core.adapter.builder.dstab.PsseEsst4bExciterTest;
import org.interpss.core.adapter.builder.dstab.PsseEsst1aExciterTest;
import org.interpss.core.adapter.builder.dstab.PsseGgov1GovernorTest;
import org.interpss.core.adapter.builder.dstab.PsseH6eGovernorTest;
import org.interpss.core.adapter.builder.dstab.PsseHygovrGovernorTest;
import org.interpss.core.adapter.builder.dstab.Lcfb1PrefControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseHygovGovernorTest;
import org.interpss.core.adapter.builder.dstab.PsseLegacyControllerMappingTest;
import org.interpss.core.adapter.builder.dstab.PSSEDStabDirectParserReportTest;
import org.interpss.core.adapter.builder.dstab.PsseDyrRecordReaderTest;
import org.interpss.core.adapter.builder.dstab.PsseRegca1ConverterTest;
import org.interpss.core.adapter.builder.dstab.PsseRegfma1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseReeca1ControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseReecb1ControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseRepca1PlantControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseType3WindControllerTest;
import org.interpss.dstab.renewable.RenewableControlIntegrationTest;
import org.interpss.core.dstab.Texas2kWindProfileCoverageTest;
import org.interpss.core.dstab.Texas2kRegfma1CoverageTest;
import org.interpss.core.dstab.Texas2kFullDynamicCoverageTest;
import org.interpss.core.dstab.Texas2kHygovCoverageTest;
import org.interpss.core.dstab.Texas2kLegacyControllerProfileTest;
import org.interpss.core.dstab.Texas2kOneSecondDriftTest;
import org.interpss.core.dstab.Texas2kPowerWorldRenewableParameterParityTest;
import org.interpss.core.dstab.Texas2kSixCaseDynamicSmokeTest;
import org.interpss.core.dstab.Texas2kSynchronousMachineCoverageTest;
import org.interpss.core.dstab.Texas2kGenrouFaultBenchmarkTest;
import org.interpss.core.dstab.Esst4bAndesSmibConformanceTest;
import org.interpss.core.dstab.Esst1aAndesSmibConformanceTest;
import org.interpss.core.dstab.Exst1AndesSmibConformanceTest;
import org.interpss.core.dstab.HygovAndesSmibConformanceTest;
import org.interpss.core.dstab.Ieeeg1AndesSmibConformanceTest;
import org.interpss.core.dstab.Ieeet1AndesSmibConformanceTest;
import org.interpss.core.adapter.cim.CIMDirectParserTest;
import org.interpss.core.adapter.cim.IEEE118CimVsMatpowerJsonCompareTest;
import org.interpss.core.adapter.ge.EpcDirectParser_SectionGate_Test;
import org.interpss.core.adapter.ge.GESampleTestCases;
import org.interpss.core.adapter.ieee.IEEE009Bus_Test;
import org.interpss.core.adapter.ieee.IEEE118Bus_Test;
import org.interpss.core.adapter.ieee.IEEE14BusTest;
import org.interpss.core.adapter.ieee.IEEECommonFormat_CommaTest;
import org.interpss.core.adapter.internal.Bus11856Test;
import org.interpss.core.adapter.internal.Bus1824Test;
import org.interpss.core.adapter.internal.Bus6384Test;
import org.interpss.core.adapter.internal.IEEE14Test;
import org.interpss.core.adapter.matpower.MatpowerCase13659PegaseTest;
import org.interpss.core.adapter.matpower.MatpowerCase3012WpTest;
import org.interpss.core.adapter.matpower.MatpowerCase3120SpTest;
import org.interpss.core.adapter.matpower.MatpowerCase3375WpTest;
import org.interpss.core.adapter.matpower.MatpowerCase6468RteTest;
import org.interpss.core.adapter.matpower.MatpowerCase8387PegaseTest;
import org.interpss.core.adapter.matpower.MatpowerCase9241PegaseTest;
import org.interpss.core.adapter.matpower.MatpowerFormatTest;
import org.interpss.core.adapter.psse.compare.PSSE_ACTIVSg2000BusCompare_Test;
import org.interpss.core.adapter.psse.compare.PSSE_ACTIVSg25kObjectCompareTest;
import org.interpss.core.adapter.psse.json.aclf.PSSEJSon_IEEE9Bus_BusSet_Test;
import org.interpss.core.adapter.psse.json.aclf.PSSEJSon_IEEE9Bus_DSL_Test;
import org.interpss.core.adapter.psse.json.aclf.PSSEJSon_IEEE9Bus_FAdapter_Test;
import org.interpss.core.adapter.psse.json.aclf.powsybl.PSSE_PowSyBl_RAWX_Smoke_Test;
import org.interpss.core.adapter.psse.largeNet.PSSE_ACTIVSg2000Bus_Test;
import org.interpss.core.adapter.psse.largeNet.PSSE_ACTIVSg25kBus_Test;
import org.interpss.core.adapter.psse.raw.aclf.CR_UserTestCases;
import org.interpss.core.adapter.psse.raw.aclf.GuideSample_TestCase;
import org.interpss.core.adapter.psse.raw.aclf.Kundur_2Area_LCCHVDC2T_Test;
import org.interpss.core.adapter.psse.raw.aclf.Kundur_2Area_VSCHVDC2T_Test;
import org.interpss.core.adapter.psse.raw.aclf.Mod_SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.raw.aclf.PSSE_5Bus_TestCase;
import org.interpss.core.adapter.psse.raw.aclf.PSSE_AutoVersion_Bus0_Regression_Test;
import org.interpss.core.adapter.psse.raw.aclf.PSSE_IEEE9Bus_Test;
import org.interpss.core.adapter.psse.raw.aclf.PSSE_Savnw_v33_Test;
import org.interpss.core.adapter.psse.raw.aclf.PSSEDirectParser_VersionGate_Test;
import org.interpss.core.adapter.psse.raw.aclf.PSSEV31_v36_IEEE9_Test;
import org.interpss.core.adapter.psse.raw.aclf.PSSEV31_v36_Sample_Test;
import org.interpss.core.adapter.psse.raw.aclf.PsseVersionParserTest;
import org.interpss.core.adapter.psse.raw.aclf.SixBus_DclfPsXfr;
import org.interpss.core.adapter.psse.raw.aclf.powsybl.PSSE_PowSyBl_Equipment_Smoke_Test;
import org.interpss.core.adapter.psse.raw.aclf.powsybl.PSSE_PowSyBl_IEEE_Smoke_Test;
import org.interpss.core.adapter.psse.raw.aclf.powsybl.PSSE_PowSyBl_InitMismatch_Test;
import org.interpss.core.adapter.psse.raw.aclf.powsybl.PSSE_PowSyBl_Parser_Smoke_Test;
import org.interpss.core.adapter.psse.raw.aclf.powsybl.PSSE_PowSyBl_TwoTerminalDc_Test;
import org.interpss.core.aclf.PSSE_5Bus_SwitchedShunt_Test;
import org.interpss.core.adapter.psse.raw.acsc.IEEE39Bus_Acsc_Test;
import org.interpss.core.adapter.psse.raw.acsc.IEEE9Bus_Acsc_Test;
import org.interpss.core.adapter.psse.raw.dstab.IEEE9_Dstab_Adapter_Test;
import org.interpss.core.adapter.psse.raw.dstab.PsseGnetIdvProcessorTest;
import org.interpss.core.adapter.psse.raw.dstab.PsseModelRemoveIdvProcessorTest;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_FiveBus_NB_ExportImport_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_FiveBus_NB_TopoAnalysis_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_IEEE14_NB_BusMerge_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_IEEE14_NB_BusSplit_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_IEEE14_NB_SplitBus_Import_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_IEEE14_NB_Topo_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_IEEE14_NodeBreaker_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_NB_BusWithoutInjection_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_Sample_NB_Aclf_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_Sample_NB_TopoAnalysis_Test;
import org.interpss.core.adapter.psse.raw.nbreaker.PSSE_TwoSubstations_NB_TopoAnalysis_Test;
import org.interpss.core.adapter.psse.rawx.PSSE_Sample_NB_Rawx_TopoAnalysis_Test;
import org.interpss.core.adapter.pwd.PWDDirectParser_ObjectGate_Test;
import org.interpss.core.adapter.pwd.PWDIEEE14BusTestCase;
import org.interpss.core.adapter.pwd.SixBus_DclfPsXfr_pwd;
import org.interpss.core.adapter.pwd.SixBus_XfrControl_pwd;
import org.interpss.core.adapter.ucte.UCTEFormatAusPowerTest;
import org.interpss.core.ca.IEEE14_N1Scan_Test;
import org.interpss.core.ca.Ieee14_CA_Test;
import org.interpss.core.ca.Ieee14_GSF_Test;
import org.interpss.core.ca.aclf.IEEE14_AclfN1Scan_Test;
import org.interpss.core.contingency.parser.ConFileParser_Test;
import org.interpss.core.contingency.parser.ConToIpssMapper_Test;
import org.interpss.core.dclf.IEEE14_Dclf_Test;
import org.interpss.json.PSSE_IEEE14_NBreakerCompare_Test;
import org.interpss.json.Texas2KJsonCompareTest;
import org.interpss.plugin.fstate.AuxFSPluginDclfAlgoRunTest;
import org.interpss.plugin.fstate.AuxFSPluginWeekDclfAlgoRunTest;
import org.interpss.plugin.fstate.FSPluginDclfAlgoRunTest;
import org.interpss.plugin.fstate.PowerWorld2PlanMaintainAdapterTest;
import org.interpss.plugin.fstate.PowerWorld2PlanMaintainWeekAdapterTest;
import org.interpss.plugin.optadj.Texas2K_SenMatrixHelper_Test;
import org.interpss.plugin.optadj.texas2K.dense.Texas2K_OptBasecase_SsaResult_Test;
import org.interpss.plugin.optadj.texas2K.dense.Texas2K_OptN1Scan_SsaResult_Test1;
import org.interpss.plugin.optadj.texas2K.dense.Texas2K_OptN1Scan_SsaResult_Test;
import org.interpss.plugin.optadj.texas2K.sparse.Texas2K_OptBasecase_SsaResult_Sparse_Test;
import org.interpss.plugin.optadj.texas2K.sparse.Texas2K_OptN1Scan_SsaResult_Sparse_Test1;
import org.interpss.plugin.optadj.texas2K.sparse.Texas2K_OptN1Scan_SsaResult_Sparse_Test;
import org.interpss.core.dclf.edclf.IEEE118_EDclf_Test;
import org.interpss.core.dclf.edclf.IEEE14_EDclf_Test;
import org.interpss.core.dclf.edclf.IEEE39_EDclf_Test;
import org.interpss.core.dstab.DStab_IEEE9Bus_Test;
import org.interpss.core.dstab.Exdc2AndesSmibConformanceTest;
import org.interpss.core.dstab.Ac8bAndesSmibConformanceTest;
import org.interpss.core.dstab.Ieeex1AndesSmibConformanceTest;
import org.interpss.core.dstab.Ieeex1SmibIntegrationTest;
import org.interpss.core.dstab.cml.block.DelayControlBlockTests;
import org.interpss.core.dstab.cml.block.FilterControlBlockTests;
import org.interpss.core.dstab.cml.block.FilterNthOrderBlockTests;
import org.interpss.core.dstab.cml.block.IntegrationControlBlockTests;
import org.interpss.core.dstab.cml.block.PIControlBlockTests;
import org.interpss.core.dstab.cml.block.Pss2aLeadLagBlockTest;
import org.interpss.core.dstab.cml.block.WashoutControlBlockTests;
import org.interpss.core.dstab.cml.controller.AnnotateParserTests;
import org.interpss.core.dstab.cml.controller.AnnotationExciterTests;
import org.interpss.core.dstab.mach.EConstMachineTest;
import org.interpss.core.dstab.mach.GensalConformanceTest;
import org.interpss.core.dstab.mach.GensalHydroSmibTest;
import org.interpss.core.dstab.mach.GenrouConformanceTest;
import org.interpss.core.dstab.mach.GenrouAndesSmibConformanceTest;
import org.interpss.core.dstab.mach.MachineSaturationTest;
import org.interpss.core.dstab.mach.RoundRotorMachineTest;
import org.interpss.core.dstab.mach.SMIB_Gen_Test;
import org.interpss.core.dstab.mach.SalientPoleMachineTest;
import org.interpss.core.script.mvel.MvelExprEval_Test;
import org.interpss.core.zeroz.IEEE14ZeroZBranchAclfTest;
import org.interpss.core.zeroz.IEEE14ZeroZBranchDeconsolidateTest;
import org.interpss.core.zeroz.ZBrAclfDeconOutputTest;
import org.interpss.core.zeroz.ZeroZBranchNetUtilTest;
import org.interpss.core.zeroz.topo.IEEE14ZeroZBranchFuncLoopTest;
import org.interpss.core.zeroz.topo.IEEE14ZeroZBranchFuncTest;
import org.interpss.core.zeroz.topo.ZeroZBranchFuncTest;
import org.interpss.dep.plugin.beanModel.AclfBeanMapperTest;
import org.interpss.plugin.exchange.AclfResultExchagneIeee14Test;
import org.interpss.plugin.exchange.ContingencyExchagneIeee14Test;
import org.interpss.plugin.lfGCtrl.SwitchedShuntGControlTest;
import org.interpss.plugin.piecewise.Acsc5BusTestSubAreaNet;
import org.interpss.plugin.piecewise.IEEE14TestAclfSubAreaBuild;
import org.interpss.plugin.piecewise.IEEE14TestAclfSubNetBuild;
import org.interpss.plugin.piecewise.IEEE14TestSubAreaSearch;
import org.interpss.plugin.piecewise.IEEE9BusTestDStabSubAreaNet;
import org.interpss.plugin.result.AclfResultDFrameAdapterTest;
import org.interpss.core.adapter.psse.raw.aclf.PSSE_MTHVDC_Test;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
	// aclf
	IEEE14_3WXfrTest.class,
	IEEE9_MultiGenTest.class,
	IEEE9_MultiLoadTest.class,
	IEEE14_YMatrixSetTest.class,
	SwingBusSubAreaTest.class,
	Kundur_2Area_LCCHVDC2T_Aclf_Test.class,
	Kundur_2Area_LCCHVDC2T_Test.class,
	Kundur_2Area_VSCHVDC2T_Test.class,
	ContinuationPowerFlowPsseTest.class,

	// SVC and PV limit
	IEEE14_PVLimit_SVCTest.class,
	
	// large PSSE network
	PSSE_ACTIVSg2000Bus_Test.class,
	PSSE_ACTIVSg25kBus_Test.class,
	
	// Json Compare
	Texas2KJsonCompareTest.class,
	PSSE_ACTIVSg25kObjectCompareTest.class,
	PSSE_ACTIVSg2000BusCompare_Test.class,
	PSSE_IEEE14_NBreakerCompare_Test.class,
	
	// ZeroZ branch
	///ZeroZBranchNetUtilTest.class,        // TODO needs ODM XML loader
	ZeroZBranchFuncTest.class,
	///IEEE14ZeroZBranchFuncTest.class,      // TODO needs ODM XML loader
	///IEEE14ZeroZBranchFuncLoopTest.class,  // TODO needs ODM XML loader
	///IEEE14ZeroZBranchAclfTest.class,      // TODO needs ODM XML loader
	///IEEE14ZeroZBranchDeconsolidateTest.class, // TODO needs ODM XML loader
	///ZBrAclfDeconOutputTest.class,         // TODO needs ODM XML loader
	///IEEE9Bus_ZbrNRSolver_Test.class,
	
	// DFrame
	AclfResultDFrameAdapterTest.class,
	
	// acsc
	IEEE9Bus_Acsc_Test.class,
	IEEE39Bus_Acsc_Test.class,
	PsseGnetIdvProcessorTest.class,
	PsseModelRemoveIdvProcessorTest.class,
	
	// Dclf
	Mod_SixBus_DclfPsXfr.class,
	SixBus_DclfPsXfr.class,
	///IEEE14BusBreaker_dclf_Test.class,
	///IEEE14BusBreaker_equivCABranch_Test.class,
	
	// ca
	Ieee14_CA_Test.class,
	Ieee14_GSF_Test.class,
	IEEE14_N1Scan_Test.class,
	
	IEEE14_AclfN1Scan_Test.class,
	
	// EDclf
	IEEE14_Dclf_Test.class,
	IEEE14_EDclf_Test.class,
	IEEE39_EDclf_Test.class,
	IEEE118_EDclf_Test.class,

	// Optimization adjustment
	Texas2K_SenMatrixHelper_Test.class,
	Texas2K_OptBasecase_SsaResult_Test.class,
	Texas2K_OptBasecase_SsaResult_Sparse_Test.class,
	Texas2K_OptN1Scan_SsaResult_Test.class,
	Texas2K_OptN1Scan_SsaResult_Test1.class,
	Texas2K_OptN1Scan_SsaResult_Sparse_Test.class,
	Texas2K_OptN1Scan_SsaResult_Sparse_Test1.class,
	
	// small Z branch
	///SampleSwitchBreakerModelTest.class,
	///IEEE14BusBreaker_lf_Test.class,
	///IEEE14BusBreakerTest.class,
	
	// Ascsc ODM
	///Acsc5Bus_ODM_TestCase.class,

	// SE
	///SE_IEEE118Test.class,
	
	// DStab controller building blocks
	DelayControlBlockTests.class,
	FilterControlBlockTests.class,
	FilterNthOrderBlockTests.class,
	IntegrationControlBlockTests.class,
	PIControlBlockTests.class,
	Pss2aLeadLagBlockTest.class,
	WashoutControlBlockTests.class,
	
	// DStab Machine
	///Eq1Ed1MachineTest.class,
	EConstMachineTest.class,
	///Eq1MachineCaseTest.class,
	GensalConformanceTest.class,
	GensalHydroSmibTest.class,
	GenrouConformanceTest.class,
	GenrouAndesSmibConformanceTest.class,
	Esst4bAndesSmibConformanceTest.class,
	Esst1aAndesSmibConformanceTest.class,
	Exst1AndesSmibConformanceTest.class,
	Ieeet1AndesSmibConformanceTest.class,
	Ieeeg1AndesSmibConformanceTest.class,
	HygovAndesSmibConformanceTest.class,
	Exdc2AndesSmibConformanceTest.class,
	Ac8bAndesSmibConformanceTest.class,
	Exdc2aOpenSourceEquationConformanceTest.class,
	Ieeet4OpenSourceEquationConformanceTest.class,
	Ac8bAndesEquationConformanceTest.class,
	Ac7bDynawoEquationConformanceTest.class,
	Ac7bImportTest.class,
	RexsysExciterTest.class,
	Esac6aExciterTest.class,
	Dc4bExciterTest.class,
	St6bExciterTest.class,
	Esst2aExciterTest.class,
	Ieeex1AndesSmibConformanceTest.class,
	Ieeex1SmibIntegrationTest.class,
	MachineSaturationTest.class,
	RoundRotorMachineTest.class,
	SalientPoleMachineTest.class,
	SMIB_Gen_Test.class,
	
	//DStab dynamic devic model
	///TestInductionMotorModel.class,
	///TestLd1pacModel.class,
	///TestDER_AModel.class,
	///TestCMPLDWModel.class,
	///TestCMPLDWGModel.class,
	
	///TestCalBusDStabLoad.class,


	// DStab ODM
	//TODO ODM file missing Gen sourceZ or genPosZ
	///DStab_2Bus.class,
	
	//DStab PSS/E
	DStab_IEEE9Bus_Test.class,
	Kundur_2Area_VSCHVDC2T_Test.class,
	
	// CML
	DelayControlBlockTests.class,
	FilterControlBlockTests.class,
	///GainBlockExtensionTests.class,
	IntegrationControlBlockTests.class,
	PIControlBlockTests.class,
	WashoutControlBlockTests.class,
	
	AnnotateParserTests.class,
	AnnotationExciterTests.class,	
	
	// Dist
	///DistLF14Bus_PathLF_Test.class,
	///DistLF14BusTest.class,
	///DistSample2BusTest.class,
	///DistSys_Test.class,	
	
	// DC System
	///DcSample_2BusTest.class,
	///Inverter_2BusTest.class,
	///PVModelList_2BusTest.class,
	
	///POC_Test1.class,
	///POC_Test2_1.class,
	///POC_Test2_2.class,
	///POC_Test2_3.class,
	
	// core file adapter
	IEEECommonFormat_CommaTest.class,
	///IEEECommonFormatTest.class,
	IEEE009Bus_Test.class,
	IEEE14BusTest.class,
	IEEE118Bus_Test.class,
	BPASampleTestCases.class,
	BPADirectParser_CardGate_Test.class,
	Bpa07c_0615_Test.class,
	BpaO7CTest.class,

	// AclfNetworkBuilder unit tests
	AclfNetworkBuilderCoreTest.class,
	AclfNetworkBuilderBranchTest.class,
	AclfNetworkBuilderAdjDeviceTest.class,
	AclfNetworkBuilderHvdcTest.class,
	AclfNetworkBuilder3WAndFinalizeTest.class,

	// AcscNetworkBuilder unit tests
	AcscNetworkBuilderCoreTest.class,
	AcscNetworkBuilderBranchTest.class,
	AcscNetworkBuilderFinalizeTest.class,

	// DStabNetworkBuilder unit tests
	DStabNetworkBuilderMachineTest.class,
	DStabNetworkBuilderExciterTest.class,
	DStabNetworkBuilderAc1cTest.class,
	DStabNetworkBuilderAc2cTest.class,
	DStabNetworkBuilderAc3cTest.class,
	DStabNetworkBuilderAc4cTest.class,
	DStabNetworkBuilderAc5cTest.class,
	DStabNetworkBuilderAc6cTest.class,
	DStabNetworkBuilderAc7cTest.class,
	DStabNetworkBuilderAc8cTest.class,
	Ieeex1AndesEquationConformanceTest.class,
	DStabNetworkBuilderEsac5aTest.class,
	DStabNetworkBuilderExac1Test.class,
	DStabNetworkBuilderEsurryTest.class,
	DStabNetworkBuilderExac1aTest.class,
	DStabNetworkBuilderExac2Test.class,
	DStabNetworkBuilderEsac1aTest.class,
	DStabNetworkBuilderEsac2aTest.class,
	DStabNetworkBuilderGovernorTest.class,
	DStabNetworkBuilderIeeeg3Test.class,
	DStabNetworkBuilderIeeeg3dTest.class,
	DStabNetworkBuilderWesgovdTest.class,
	DStabNetworkBuilderDegov1dTest.class,
	DStabNetworkBuilderPidgovdTest.class,
	DStabNetworkBuilderTgov3dTest.class,
	DStabNetworkBuilderHygov2dTest.class,
	DStabNetworkBuilderWpidhydTest.class,
	DStabNetworkBuilderGastwddTest.class,
	DStabNetworkBuilderGast2adTest.class,
	DStabNetworkBuilderStabilizerTest.class,
	PsseEsst4bExciterTest.class,
	PsseEsst1aExciterTest.class,
	PsseGgov1GovernorTest.class,
	PsseH6eGovernorTest.class,
	PsseHygovrGovernorTest.class,
	Lcfb1PrefControllerTest.class,
	PsseRegca1ConverterTest.class,
	PsseRegfma1ModelTest.class,
	PsseReeca1ControllerTest.class,
	PsseReecb1ControllerTest.class,
	PsseRepca1PlantControllerTest.class,
	PsseType3WindControllerTest.class,
	RenewableControlIntegrationTest.class,
	PsseHygovGovernorTest.class,
	PsseLegacyControllerMappingTest.class,
	PSSEDStabDirectParserReportTest.class,
	PsseDyrRecordReaderTest.class,
	Texas2kWindProfileCoverageTest.class,
	Texas2kRegfma1CoverageTest.class,
	Texas2kFullDynamicCoverageTest.class,
	Texas2kHygovCoverageTest.class,
	Texas2kLegacyControllerProfileTest.class,
	Texas2kOneSecondDriftTest.class,
	Texas2kPowerWorldRenewableParameterParityTest.class,
	Texas2kSixCaseDynamicSmokeTest.class,
	Texas2kSynchronousMachineCoverageTest.class,
	Texas2kGenrouFaultBenchmarkTest.class,

	//matpower
	MatpowerFormatTest.class,
	MatpowerCase3012WpTest.class,
	MatpowerCase3120SpTest.class,
	MatpowerCase3375WpTest.class,
	MatpowerCase8387PegaseTest.class,
	MatpowerCase9241PegaseTest.class,
	MatpowerCase13659PegaseTest.class,
	MatpowerCase6468RteTest.class,
	UCTEFormatAusPowerTest.class,

	// PowerWorld AUX ACLF + future-state
	PowerWorld2PlanMaintainAdapterTest.class,
	PowerWorld2PlanMaintainWeekAdapterTest.class,
	SixBus_DclfPsXfr.class,
	PWDIEEE14BusTestCase.class,
	PWDDirectParser_ObjectGate_Test.class,
	SixBus_DclfPsXfr_pwd.class,
	SixBus_XfrControl_pwd.class,

	// Future-state
	FSPluginDclfAlgoRunTest.class,
	AuxFSPluginDclfAlgoRunTest.class,
	AuxFSPluginWeekDclfAlgoRunTest.class,

	//PSSE Raw
	///Bus42_3winding.class, // this is a bug
	CR_UserTestCases.class,
	GuideSample_TestCase.class,
	Mod_SixBus_DclfPsXfr.class,
	PSSE_5Bus_TestCase.class,
	PSSE_5Bus_SwitchedShunt_Test.class,
	PSSE_IEEE9Bus_Test.class,
	PSSEV31_v36_Sample_Test.class,
	PSSEV31_v36_IEEE9_Test.class,
	PSSE_Savnw_v33_Test.class,
	PSSEDirectParser_VersionGate_Test.class,
	PsseVersionParserTest.class,
	PSSE_AutoVersion_Bus0_Regression_Test.class,
	IEEE9_Dstab_Adapter_Test.class,
	
	// PSSE JSON
	PSSEJSon_IEEE9Bus_DSL_Test.class,
	PSSEJSon_IEEE9Bus_FAdapter_Test.class,
	PSSEJSon_IEEE9Bus_BusSet_Test.class,

	// PSSE Global Adjustment Control
	SwitchedShuntGControlTest.class,
	
	// PSSE Contingency
	ConFileParser_Test.class,
	ConToIpssMapper_Test.class,

	// PSSE Node Breaker
	PSSE_IEEE14_NodeBreaker_Test.class,
	PSSE_IEEE14_NB_Topo_Test.class,
	PSSE_IEEE14_NB_BusSplit_Test.class,
	PSSE_IEEE14_NB_BusMerge_Test.class,
	PSSE_IEEE14_NB_SplitBus_Import_Test.class,
	PSSE_FiveBus_NB_TopoAnalysis_Test.class,
	PSSE_FiveBus_NB_ExportImport_Test.class,
	PSSE_TwoSubstations_NB_TopoAnalysis_Test.class,
	PSSE_NB_BusWithoutInjection_Test.class,
	PSSE_Sample_NB_TopoAnalysis_Test.class,
	PSSE_Sample_NB_Rawx_TopoAnalysis_Test.class,
	PSSE_Sample_NB_Aclf_Test.class,

	// PowSyBl catalog coverage
	PSSE_PowSyBl_IEEE_Smoke_Test.class,
	PSSE_PowSyBl_InitMismatch_Test.class,
	PSSE_PowSyBl_TwoTerminalDc_Test.class,
	PSSE_PowSyBl_Equipment_Smoke_Test.class,
	PSSE_PowSyBl_Parser_Smoke_Test.class,
	PSSE_PowSyBl_RAWX_Smoke_Test.class,

	// PSSE MTHVDC
	PSSE_MTHVDC_Test.class,
	
	// CIM File Adapter
	CIMDirectParserTest.class,
	IEEE118CimVsMatpowerJsonCompareTest.class,
	
	///Acsc5Bus_ODM_TestCase.class,
	IEEE9Bus_Acsc_Test.class,
	
	GESampleTestCases.class,
	EpcDirectParser_SectionGate_Test.class,
	
	IEEE14Test.class,
	Bus1824Test.class,
	Bus6384Test.class,
	Bus11856Test.class,
	
	GuideSample_TestCase.class,
	
	AclfBeanMapperTest.class,
	
	// exchange
	AclfResultExchagneIeee14Test.class,
	ContingencyExchagneIeee14Test.class,
	
	// Piecewise Algorithm
	IEEE14TestSubAreaSearch.class,
	//IEEE14TestAclfNetPiesewise.class,
	IEEE14TestAclfSubNetBuild.class,
	IEEE14TestAclfSubAreaBuild.class,
	
	Acsc5BusTestSubAreaNet.class,
	///Acsc5BusTesPiecewiseAlgo.class,
	
	IEEE9BusTestDStabSubAreaNet.class,
	
	// Mvel Expression
	MvelExprEval_Test.class,
})
public class CorePluginTestSuite {
}
