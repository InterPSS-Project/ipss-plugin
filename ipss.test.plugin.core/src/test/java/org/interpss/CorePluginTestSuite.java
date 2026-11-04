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
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderScrxTest;
import org.interpss.core.adapter.builder.dstab.Dc3aExciterTest;
import org.interpss.core.adapter.builder.dstab.Esac4aExciterTest;
import org.interpss.core.adapter.builder.dstab.Exac4ExciterTest;
import org.interpss.core.adapter.builder.dstab.Exst2ExciterTest;
import org.interpss.core.adapter.builder.dstab.St5bExciterTest;
import org.interpss.core.adapter.builder.dstab.Dera1ModelTest;
import org.interpss.core.adapter.builder.dstab.Regcb1ModelTest;
import org.interpss.core.adapter.builder.dstab.Exdc2aOpenSourceEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ieeex1AndesEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ieeet4OpenSourceEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.IeelLoadModelTest;
import org.interpss.core.adapter.builder.dstab.Ac8bAndesEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ac7bDynawoEquationConformanceTest;
import org.interpss.core.adapter.builder.dstab.Ac7bImportTest;
import org.interpss.core.adapter.builder.dstab.RexsysExciterTest;
import org.interpss.core.adapter.builder.dstab.Esac6aExciterTest;
import org.interpss.core.adapter.builder.dstab.Expic1ExciterTest;
import org.interpss.core.dstab.Expic1NativeVerificationTest;
import org.interpss.core.dstab.Exac2FieldBaseVerificationTest;
import org.interpss.core.dstab.WttqaCompatibilityTest;
import org.interpss.dstab.renewable.WttqaCurveInputConformanceTest;
import org.interpss.core.dstab.ReaxbGenericHostTest;
import org.interpss.core.adapter.builder.dstab.Dc4bExciterTest;
import org.interpss.core.adapter.builder.dstab.St6bExciterTest;
import org.interpss.core.adapter.builder.dstab.St6cExciterTest;
import org.interpss.core.adapter.builder.dstab.St4cExciterTest;
import org.interpss.core.adapter.builder.dstab.St2cExciterTest;
import org.interpss.core.adapter.builder.dstab.St3cExciterTest;
import org.interpss.core.adapter.builder.dstab.St5cExciterTest;
import org.interpss.core.adapter.builder.dstab.St7bExciterTest;
import org.interpss.core.adapter.builder.dstab.St7cExciterTest;
import org.interpss.core.adapter.builder.dstab.St8cExciterTest;
import org.interpss.core.adapter.builder.dstab.St9cExciterTest;
import org.interpss.core.adapter.builder.dstab.St10cExciterTest;
import org.interpss.core.adapter.builder.dstab.St1cExciterTest;
import org.interpss.core.adapter.builder.dstab.SexsExciterTest;
import org.interpss.core.adapter.builder.dstab.ExeliExciterTest;
import org.interpss.core.adapter.builder.dstab.Esst2aExciterTest;
import org.interpss.core.adapter.builder.dstab.Exst3ExciterTest;
import org.interpss.core.dstab.Exst3IndependentSmibConformanceTest;
import org.interpss.core.dstab.Bbsex1PsseSmibConformanceTest;
import org.interpss.core.dstab.Ieeex1PsseSmibConformanceTest;
import org.interpss.core.dstab.Exdc2PsseSmibConformanceTest;
import org.interpss.core.dstab.Ieeet4PsseSmibConformanceTest;
import org.interpss.core.dstab.ExeliPsseSmibConformanceTest;
import org.interpss.core.dstab.ScrxPsseSmibConformanceTest;
import org.interpss.core.dstab.Exst2PsseSmibConformanceTest;
import org.interpss.core.dstab.IeeestPsseSmibConformanceTest;
import org.interpss.core.dstab.Pss3bPsseSmibConformanceTest;
import org.interpss.core.dstab.Pss2cPsseSmibConformanceTest;
import org.interpss.core.dstab.Pss4bPsseSmibConformanceTest;
import org.interpss.core.dstab.PsssbIndependentSmibConformanceTest;
import org.interpss.core.dstab.St2cutPsseSmibConformanceTest;
import org.interpss.core.dstab.Pss2bIndependentSmibConformanceTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bCompiledStageTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bSharedStateTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bReferenceReplayTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bCoupledBoundaryConformanceTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bLongRunConformanceTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bCoupledPlaybackTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bInputLagPreparationTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Pss2bAcceptedPathHistoryTest;
import org.interpss.core.adapter.builder.dstab.Pss2bImportValidationTest;
import org.interpss.core.dstab.Pss3cPsseSmibConformanceTest;
import org.interpss.core.dstab.Pss5cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Pss4cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Pss6cPsseSmibConformanceTest;
import org.interpss.core.dstab.Pss7cPsseSmibConformanceTest;
import org.interpss.core.dstab.St7bPsseSmibConformanceTest;
import org.interpss.core.dstab.St7cPsseSmibConformanceTest;
import org.interpss.core.dstab.St6cPsseSmibConformanceTest;
import org.interpss.core.dstab.St8cPsseSmibConformanceTest;
import org.interpss.core.dstab.St9cPsseSmibConformanceTest;
import org.interpss.core.dstab.St10cPsseSmibConformanceTest;
import org.interpss.core.adapter.builder.dstab.Esst3aExciterTest;
import org.interpss.core.dstab.Esst3aIndependentSmibConformanceTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac5aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderExac1Test;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsurryTest;
import org.interpss.core.dstab.EsurryIndependentSmibConformanceTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc1cTest;
import org.interpss.core.dstab.Ac1cIndependentSmibConformanceTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc2cTest;
import org.interpss.core.dstab.Ac2cIndependentSmibConformanceTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc3cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc4cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc5cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc6cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc7cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc8cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc9cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderAc11cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderBbsex1Test;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsdc1a2aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderDc1c2cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderDc4cTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderExac1aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderExac2Test;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac1aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac2aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac3aTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderEsac8bTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderGovernorTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderGastdTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderIeeeg1dTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderIeesgodTest;
import org.interpss.core.adapter.builder.dstab.DStabNetworkBuilderTgov1dTest;
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
import org.interpss.core.adapter.builder.dstab.PssCanonicalNativeSchemaTest;
import org.interpss.core.adapter.builder.dstab.PssCompensationBaseTest;
import org.interpss.core.adapter.builder.dstab.ExciterInputContractTest;
import org.interpss.core.dstab.PssCompensationNativeConformanceTest;
import org.interpss.core.adapter.builder.dstab.PsseEsst4bExciterTest;
import org.interpss.core.adapter.builder.dstab.PsseEsst1aExciterTest;
import org.interpss.core.adapter.builder.dstab.PsseExst1FieldLimitTest;
import org.interpss.core.adapter.builder.dstab.PsseGgov1GovernorTest;
import org.interpss.core.adapter.builder.dstab.PsseH6eGovernorTest;
import org.interpss.core.adapter.builder.dstab.PsseHyg3GovernorTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3PreparedCurveTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3SharedStateTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3SharedSmibTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3StateConformanceTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3LimitConformanceTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3LongDurationConformanceTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3BypassConformanceTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3DerivativePreparationConformanceTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3ParameterValidationTest;
import org.interpss.dstab.control.gov.psse.hyg3.Hyg3FastFeedbackReferenceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1SharedStateTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tSharedStateTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tReferenceTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tCommandBoundaryTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tPowerBoundaryTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tRunBoundaryTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tProductionStepTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tValveLimitsTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tMachineTripTest;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tValveReleaseTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1SharedSmibTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1StateConformanceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1BypassLimitConformanceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1TimePreparationTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1TimeReferenceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1InitialLimitsTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1InitialReferenceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1ShaftInitializationTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1BaseConformanceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1InactiveReferenceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1BoundaryPlaybackTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1MachineEventConformanceTest;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1MeasurementDependenciesTest;
import org.interpss.core.adapter.builder.dstab.PsseHygovrGovernorTest;
import org.interpss.core.adapter.builder.dstab.Lcfb1PrefControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseHygovGovernorTest;
import org.interpss.core.adapter.builder.dstab.PsseLegacyControllerMappingTest;
import org.interpss.core.adapter.builder.dstab.PSSEDStabDirectParserReportTest;
import org.interpss.core.adapter.builder.dstab.PsseDyrRecordReaderTest;
import org.interpss.core.adapter.builder.dstab.PsseRegca1ConverterTest;
import org.interpss.core.adapter.builder.dstab.PsseRenewableTwoStepIntegrationTest;
import org.interpss.core.adapter.builder.dstab.PsseRegfma1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseRegfma1NativeSchemaTest;
import org.interpss.core.adapter.builder.dstab.PsseReeca1ControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseReecb1ControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseReecc1ControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseReecd1ControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseReeceModelTest;
import org.interpss.core.dstab.ReeceNativeImportSmibIntegrationTest;
import org.interpss.core.dstab.Regfmb1NativeContractConformanceTest;
import org.interpss.core.dstab.Regfmc1NativeContractConformanceTest;
import org.interpss.core.dstab.Regfmc1NativeRuntimePartialConformanceTest;
import org.interpss.core.dstab.Regfmc1NativeImportSmibTest;
import org.interpss.core.dstab.Repcgfmc1NativeContractConformanceTest;
import org.interpss.core.dstab.RepccNativeContractConformanceTest;
import org.interpss.core.dstab.RepccKernelNativeConformanceTest;
import org.interpss.core.dstab.RepccShuntNativeConformanceTest;
import org.interpss.core.dstab.RepccNetworkBoundaryConformanceTest;
import org.interpss.core.dstab.RepccTypedRuntimeSmibTest;
import org.interpss.core.dstab.RepccRuntimeNativeSmibTest;
import org.interpss.core.dstab.RepccSwitchingNativeSmibTest;
import org.interpss.core.dstab.HplntdAggregationNativeConformanceTest;
import org.interpss.core.dstab.ReaxdLagNativeConformanceTest;
import org.interpss.core.dstab.HplntdKernelNativeConformanceTest;
import org.interpss.core.dstab.HplntdBoundaryConformanceTest;
import org.interpss.core.dstab.HplntdTypedRuntimeSmibTest;
import org.interpss.core.dstab.HplntdImportedSmibTest;
import org.interpss.core.dstab.HplntdFaultNativeSmibTest;
import org.interpss.core.dstab.HplntdSelectorNativeSmibTest;
import org.interpss.core.dstab.HplntdProportionalNativeSmibTest;
import org.interpss.core.dstab.HplntdReferenceLimitNativeConformanceTest;
import org.interpss.core.dstab.HplntdReferenceLimitNativeSmibTest;
import org.interpss.core.dstab.HplntdShuntNativeConformanceTest;
import org.interpss.core.dstab.HplntdSwitchingNativeSmibTest;
import org.interpss.core.dstab.HplntdDischargeNativeConformanceTest;
import org.interpss.core.adapter.builder.dstab.PsseRepccModelTest;
import org.interpss.core.dstab.Repcgfmc1KernelNativeConformanceTest;
import org.interpss.core.dstab.Repcgfmc1NativeImportSmibTest;
import org.interpss.core.adapter.builder.dstab.PsseRepcgfmc1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseRegfmc1ModelTest;
import org.interpss.core.dstab.Regfmb1NativeRuntimePartialConformanceTest;
import org.interpss.core.dstab.Regfmb1NativeImportSmibTest;
import org.interpss.core.adapter.builder.dstab.PsseRegfmb1ModelTest;
import org.interpss.core.dstab.ReeceRegcbNativePartialConformanceTest;
import org.interpss.core.adapter.builder.dstab.PsseRepca1PlantControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseType3WindControllerTest;
import org.interpss.core.adapter.builder.dstab.PsseWtdtb1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseCsvgn5ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseSvsmo1t2ModelTest;
import org.interpss.core.adapter.builder.dstab.Plntbu1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt1g1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt2g1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt2e1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt3g1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt3g2ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt3p1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt3t1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt4e1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt4g1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt3e1ModelTest;
import org.interpss.core.adapter.builder.dstab.Gewtecu1ModelTest;
import org.interpss.core.adapter.builder.dstab.Gewt2mu1ModelTest;
import org.interpss.core.adapter.builder.dstab.Gewtaru1ModelTest;
import org.interpss.core.adapter.builder.dstab.Gewtgcu1ModelTest;
import org.interpss.core.adapter.builder.dstab.Gewtgdu1ModelTest;
import org.interpss.core.adapter.builder.dstab.Gewtptu1ModelTest;
import org.interpss.core.adapter.builder.dstab.Reax3bu1ModelTest;
import org.interpss.core.adapter.builder.dstab.Reax4bu1ModelTest;
import org.interpss.core.adapter.builder.dstab.WshyddGovernorTest;
import org.interpss.core.adapter.builder.dstab.WshygpGovernorTest;
import org.interpss.core.adapter.builder.dstab.PsseWt12t1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt12a1ModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWt12a1bModelTest;
import org.interpss.dstab.renewable.RenewableControlIntegrationTest;
import org.interpss.core.dstab.RenewableAggregateQvModeTest;
import org.interpss.core.dstab.Reecc1NativeConformanceTest;
import org.interpss.core.dstab.PsseDyrRepresentativeSelectorTest;
import org.interpss.core.dstab.EmbeddedTrajectoryCoverageInventoryTest;
import org.interpss.core.dstab.ReferenceArtifactManifestIntegrityTest;
import org.interpss.core.dstab.PowerWorldCsvRepeatabilityTest;
import org.interpss.core.dstab.PsseRunQualityTest;
import org.interpss.core.dstab.EndpointAlignedStateMonitorTest;
import org.interpss.core.dstab.RegcaZeroQRecoveryTest;
import org.interpss.core.dstab.RegcaCurrentLimitNativeTest;
import org.interpss.core.dstab.RegcaNetworkIterationTest;
import org.interpss.fadapter.psse.PsseNetworkConvergenceTest;
import org.interpss.fadapter.psse.PsseNetworkIterationLifecycleTest;
import org.interpss.core.dstab.dynLoad.Perc1ModelTest;
import org.interpss.core.dstab.dynLoad.Cmldznu2ModelTest;
import org.interpss.core.dstab.dynLoad.Cmldblu1SharedStateTest;
import org.interpss.core.dstab.dynLoad.Cmldblu1SharedSmibTest;
import org.interpss.core.dstab.dynLoad.Cmldblu1StateConformanceTest;
import org.interpss.core.dstab.dynLoad.Cmldblu1InitializationTest;
import org.interpss.core.dstab.dynLoad.Cmldblu1CompressorLifecycleTest;
import org.interpss.core.dstab.dynLoad.LD1PACParameterBindingTest;
import org.interpss.core.dstab.dynLoad.Cmldblu1CompressorInitializationTest;
import org.interpss.core.dstab.dynLoad.CompressorCurrentConformanceTest;
import org.interpss.core.dstab.dynLoad.Acmtblu1SharedSmibTest;
import org.interpss.core.dstab.dynLoad.CompressorFrequencyInputTest;
import org.interpss.core.dstab.dynLoad.CompressorProtectionConformanceTest;
import org.interpss.core.dstab.dynLoad.CompressorThermalConformanceTest;
import org.interpss.core.dstab.dynLoad.CompressorStartupFrequencyTest;
import org.interpss.core.dstab.dynLoad.CompressorThermalBypassTest;
import org.interpss.core.dstab.dynLoad.CompressorShortLagTest;
import org.interpss.core.dstab.dynLoad.CompositeStaticPolynomialSmibTest;
import org.interpss.core.dstab.dynLoad.CompositeOmittedDistributionTest;
import org.interpss.core.dstab.dynLoad.CompositeStaticLowVoltageTest;
import org.interpss.core.dstab.dynLoad.ElectronicFullRecoverySmibTest;
import org.interpss.core.dstab.dynLoad.DistributedStaticPortLifecycleTest;
import org.interpss.core.dstab.dynLoad.CompositeTapControlSmibTest;
import org.interpss.core.dstab.Tiocr1AcceptedHistoryTest;
import org.interpss.core.dstab.Tiocr1ResetReferenceTest;
import org.interpss.core.dstab.Tiocr1LoadOnlyReferenceTest;
import org.interpss.core.dstab.Cdc6tReferenceContractTest;
import org.interpss.core.dstab.Cdc6tMeasurementsTest;
import org.interpss.core.dstab.Cdc6tTransducerBoundaryTest;
import org.interpss.core.dstab.Cdc6tCurrentOrderTest;
import org.interpss.core.dstab.Cdc6tVoltageCurrentLimitTest;
import org.interpss.core.dstab.Cdc6tConverterBoundaryTest;
import org.interpss.core.dstab.Cdc6tConductingLinkTest;
import org.interpss.core.dstab.Cdc6tRecoveryRampTest;
import org.interpss.core.dstab.Cdc6tRecoveryBaseTest;
import org.interpss.core.dstab.Cdc6tTransferredModeBaseTest;
import org.interpss.core.dstab.Cdc6tDeepRecoveryTest;
import org.interpss.core.dstab.Cdc6tLongRunTest;
import org.interpss.core.dstab.Cdc6tInitialBalanceLifecycleTest;
import org.interpss.core.dstab.Cdc6tGovernedSourceTest;
import org.interpss.core.dstab.Cdc6tRectifierProtectionTest;
import org.interpss.core.dstab.Cdc6tInverterProtectionTest;
import org.interpss.core.dstab.Cdc6tBypassTest;
import org.interpss.core.dstab.Cdc6tModeSwitchTest;
import org.interpss.core.dstab.Cdc6tNormalRuntimeTest;
import org.interpss.core.dstab.Cdc6tNetworkOwnershipTest;
import org.interpss.core.dstab.Cdc6tTerminalPortsTest;
import org.interpss.core.dstab.Cdc6tCoupledNetworkTest;
import org.interpss.core.dstab.Cdc6tCoupledReferenceTest;
import org.interpss.core.dstab.Cdc6tOperatingBoundaryTest;
import org.interpss.core.dstab.Cdc6tProtectionCoordinatorTest;
import org.interpss.core.dstab.Cdc6tProtectedNetworkTest;
import org.interpss.core.dstab.Cdc6tMinimumCurrentTest;
import org.interpss.core.dstab.Cdc6tParametersTest;
import org.interpss.core.dstab.Chvdc2ParametersTest;
import org.interpss.core.dstab.Chvdc2DcLineTest;
import org.interpss.core.dstab.Chvdc2CurrentControlsTest;
import org.interpss.core.dstab.Chvdc2CurrentControlsReferenceTest;
import org.interpss.core.dstab.Chvdc2OrderControlsTest;
import org.interpss.core.dstab.Chvdc2OrderControlsReferenceTest;
import org.interpss.core.dstab.Chvdc2ConverterBoundaryTest;
import org.interpss.core.dstab.Chvdc2ConverterReferenceTest;
import org.interpss.core.dstab.Chvdc2ProtectionTest;
import org.interpss.core.dstab.Chvdc2ProtectionReferenceTest;
import org.interpss.core.dstab.Chvdc2ControlAssemblyTest;
import org.interpss.core.dstab.Chvdc2ControlAssemblyReferenceTest;
import org.interpss.core.dstab.Chvdc2CoupledRuntimeTest;
import org.interpss.core.dstab.Chvdc2CoupledReferenceTest;
import org.interpss.core.dstab.Chvdc2CapacitanceReferenceTest;
import org.interpss.core.dstab.Chvdc2RunBoundaryReferenceTest;
import org.interpss.core.dstab.Chvdc2MeasurementReferenceTest;
import org.interpss.core.dstab.Chvdc2CurrentSamplingReferenceTest;
import org.interpss.core.dstab.Chvdc2EventIntervalReferenceTest;
import org.interpss.core.dstab.Chvdc2NetworkPreparationTest;
import org.interpss.core.dstab.Chvdc2NetworkPreparationReferenceTest;
import org.interpss.core.dstab.Chvdc2TerminalPortsTest;
import org.interpss.core.dstab.Chvdc2TerminalReferenceTest;
import org.interpss.core.dstab.Cdc6tNetworkPreparationTest;
import org.interpss.core.dstab.Cdc6tConstantGammaTest;
import org.interpss.core.dstab.Cdc6tLimitedGammaTest;
import org.interpss.core.dstab.LccSourceControlsTest;
import org.interpss.core.dstab.Cdc6tBypassSwitchNetworkTest;
import org.interpss.core.dstab.HspUsageRankedModelTest;
import org.interpss.core.dstab.HspModelReferenceConformanceTest;
import org.interpss.core.dstab.Type3WindAndesTrajectoryTest;
import org.interpss.core.dstab.Type3WindIndependentTrajectoryTest;
import org.interpss.core.dstab.Texas2kWindProfileCoverageTest;
import org.interpss.core.dstab.Texas2kRegfma1CoverageTest;
import org.interpss.core.dstab.Texas2kFullDynamicCoverageTest;
import org.interpss.core.dstab.Texas2kHygovCoverageTest;
import org.interpss.core.dstab.Texas2kLegacyControllerProfileTest;
import org.interpss.core.dstab.Texas2kOneSecondDriftTest;
import org.interpss.core.dstab.Texas2kIndependentRenewableParameterParityTest;
import org.interpss.core.dstab.Texas2kSixCaseDynamicSmokeTest;
import org.interpss.core.dstab.Texas2kSynchronousMachineCoverageTest;
import org.interpss.core.dstab.Texas2kGenrouFaultBenchmarkTest;
import org.interpss.core.dstab.Esst4bAndesSmibConformanceTest;
import org.interpss.core.dstab.Esst4bIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esac5aAndesSmibConformanceTest;
import org.interpss.core.dstab.Esac5aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esac6aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esac8bIndependentSmibConformanceTest;
import org.interpss.core.dstab.Exac1AndesSmibConformanceTest;
import org.interpss.core.dstab.Exac1IndependentSmibConformanceTest;
import org.interpss.core.dstab.Exac1aAndesSmibConformanceTest;
import org.interpss.core.dstab.Exac1aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Exac2AndesSmibConformanceTest;
import org.interpss.core.dstab.Exac2IndependentSmibConformanceTest;
import org.interpss.core.dstab.Exac4IndependentSmibConformanceTest;
import org.interpss.core.dstab.Esac2aAndesExac2SmibConformanceTest;
import org.interpss.core.dstab.Esac2aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esac3aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esac4aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac3cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac4cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac5cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac6cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac7cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac8cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac8cu1PsseSmibConformanceTest;
import org.interpss.core.dstab.Ac9cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac11cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Dc1cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Dc2cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Dc4cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Dc4cu1PsseSmibConformanceTest;
import org.interpss.core.dstab.Esdc1aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Dc3aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esac1aAndesSmibConformanceTest;
import org.interpss.core.dstab.Esac1aIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esst1aAndesSmibConformanceTest;
import org.interpss.core.dstab.Esst1aIndependentSmibConformanceTest;
import org.interpss.core.dstab.PsseSt1LocalIntegrationTest;
import org.interpss.core.dstab.DynamicParameterValidationTest;
import org.interpss.dstab.control.exc.ieee.y1981.st1.St1InputEquationsTest;
import org.interpss.core.dstab.PsseStaticCurrentLoadVoltageCurveTest;
import org.interpss.core.dstab.Exst1AndesSmibConformanceTest;
import org.interpss.core.dstab.HygovAndesSmibConformanceTest;
import org.interpss.core.dstab.HygovIndependentSmibConformanceTest;
import org.interpss.core.dstab.Hygov2dIndependentSmibConformanceTest;
import org.interpss.core.dstab.HygovdIndependentSmibConformanceTest;
import org.interpss.core.dstab.HygovdSmibIntegrationTest;
import org.interpss.core.dstab.Hygovr1IndependentSmibConformanceTest;
import org.interpss.core.dstab.Ggov1IndependentSmibConformanceTest;
import org.interpss.core.dstab.Ieeeg3dIndependentSmibConformanceTest;
import org.interpss.core.dstab.IeesgodIndependentSmibConformanceTest;
import org.interpss.core.dstab.WesgovdIndependentSmibConformanceTest;
import org.interpss.core.dstab.PidgovdIndependentSmibConformanceTest;
import org.interpss.core.dstab.PidgovPsseSmibConformanceTest;
import org.interpss.core.dstab.Csvgn5PsseSmibConformanceTest;
import org.interpss.core.dstab.Dera1NativeSmibConformanceTest;
import org.interpss.core.dstab.Regcb1NativeSmibConformanceTest;
import org.interpss.core.dstab.Svsmo1t2PsseConformanceTest;
import org.interpss.core.dstab.Svsmo3t2CoupledSmibTest;
import org.interpss.core.dstab.Svsmo3t2StaggeredSmibTest;
import org.interpss.core.dstab.Svsmo3t2LimiterSmibTest;
import org.interpss.core.dstab.Svsmo3t2MssKernelTest;
import org.interpss.core.dstab.Svsmo3t2MssReplayTest;
import org.interpss.dstab.svc.Svsmo3t2MssBoundaryTest;
import org.interpss.core.adapter.builder.dstab.PsseSvsmo3t2ModelTest;
import org.interpss.core.dstab.mach.Wt1g1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt2g1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt2e1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt3g1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt3g2PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt3t1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt3p1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt4g1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Wt4e1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.GenqejPsseSmibConformanceTest;
import org.interpss.core.dstab.mach.GenqecuPsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Gentpj1PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.Gewtgcu1NativeConformanceTest;
import org.interpss.core.dstab.mach.Wt3e1PsseSmibConformanceTest;
import org.interpss.core.dstab.WpidhydIndependentSmibConformanceTest;
import org.interpss.core.dstab.GastdIndependentSmibConformanceTest;
import org.interpss.core.dstab.Gast2adIndependentSmibConformanceTest;
import org.interpss.core.dstab.GastwddIndependentSmibConformanceTest;
import org.interpss.core.dstab.Degov1dIndependentSmibConformanceTest;
import org.interpss.core.dstab.Tgov3dIndependentSmibConformanceTest;
import org.interpss.core.dstab.H6eIndependentSmibConformanceTest;
import org.interpss.core.dstab.Hyg3IndependentSmibConformanceTest;
import org.interpss.core.dstab.Ieeeg1AndesSmibConformanceTest;
import org.interpss.core.dstab.Ieeeg1IndependentSmibConformanceTest;
import org.interpss.core.dstab.Ieeeg1dPsseSmibConformanceTest;
import org.interpss.core.dstab.Ieeet1AndesSmibConformanceTest;
import org.interpss.core.dstab.Ieeet1IndependentSmibConformanceTest;
import org.interpss.core.dstab.Pss2aIndependentSmibConformanceTest;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aScalarPreparationTest;
import org.interpss.core.dstab.Pss1aPowerWorldPlaybackConformanceTest;
import org.interpss.core.dstab.Pss1aPowerWorldActiveConformanceTest;
import org.interpss.core.dstab.Wtdtb1PowerWorldPlaybackConformanceTest;
import org.interpss.core.dstab.Wtdtb1SmibIntegrationTest;
import org.interpss.core.dstab.Wtdtb1NativePartialConformanceTest;
import org.interpss.core.dstab.Wtptb1NativePartialConformanceTest;
import org.interpss.core.dstab.Wtptb1SmibIntegrationTest;
import org.interpss.core.dstab.Wtptb1NativeExtendedConformanceTest;
import org.interpss.core.dstab.WtgwgoaNativePartialConformanceTest;
import org.interpss.core.dstab.WtgibffraNativePartialConformanceTest;
import org.interpss.core.dstab.ReeceLocalPiNativePartialConformanceTest;
import org.interpss.core.dstab.ReeceRuntimeNativePartialConformanceTest;
import org.interpss.core.dstab.ReeceTypedSmibIntegrationTest;
import org.interpss.core.dstab.WtgibffraSmibIntegrationTest;
import org.interpss.core.adapter.builder.dstab.PsseWtgibffraModelTest;
import org.interpss.core.adapter.builder.dstab.PsseGenclsGovernorTest;
import org.interpss.core.dstab.ReecdAuxiliaryPowerNativeConformanceTest;
import org.interpss.core.dstab.WtgwgoaSmibIntegrationTest;
import org.interpss.core.adapter.builder.dstab.PsseWtgwgoaModelTest;
import org.interpss.core.adapter.builder.dstab.PsseWtptb1ModelTest;
import org.interpss.core.dstab.Tgov1IndependentSmibConformanceTest;
import org.interpss.core.dstab.Tgov1LimitNativeConformanceTest;
import org.interpss.core.dstab.Tgov1PowerWorldLimitSmibTest;
import org.interpss.core.dstab.Tgov1BaseNativeSmibTest;
import org.interpss.core.dstab.WttqaSpeedFeedbackConformanceTest;
import org.interpss.core.dstab.WttqaNativePartialConformanceTest;
import org.interpss.core.dstab.WtdtaNativeConformanceTest;
import org.interpss.core.dstab.WtdtaNativeObservationConformanceTest;
import org.interpss.core.dstab.WtaraAlgebraicConformanceTest;
import org.interpss.core.dstab.WtaraNativeConformanceTest;
import org.interpss.core.dstab.WtptaNativeConformanceTest;
import org.interpss.core.dstab.WtptaNativeObservationConformanceTest;
import org.interpss.core.dstab.ReecaNativeBoundaryConformanceTest;
import org.interpss.core.dstab.ReecaNativeConformanceTest;
import org.interpss.core.dstab.Lcfb1IndependentSmibConformanceTest;
import org.interpss.core.dstab.Dc4bIndependentSmibConformanceTest;
import org.interpss.core.dstab.St1cIndependentSmibConformanceTest;
import org.interpss.core.dstab.Esst2aIndependentSmibConformanceTest;
import org.interpss.core.dstab.St2cIndependentSmibConformanceTest;
import org.interpss.core.dstab.St3cIndependentSmibConformanceTest;
import org.interpss.core.dstab.St4cIndependentSmibConformanceTest;
import org.interpss.core.dstab.St4cu1PsseSmibConformanceTest;
import org.interpss.core.dstab.St5bIndependentSmibConformanceTest;
import org.interpss.core.dstab.St5cIndependentSmibConformanceTest;
import org.interpss.core.dstab.St6bIndependentSmibConformanceTest;
import org.interpss.core.adapter.cim.CIMDirectParserTest;
import org.interpss.core.adapter.cim.IEEE118CimVsMatpowerJsonCompareTest;
import org.interpss.core.adapter.cim.CGMESCasType3HourCoverageStubTest;
import org.interpss.core.adapter.cim.CGMESCasP4AclfSmokeStubTest;
import org.interpss.core.adapter.cim.CGMESCasCoverageStubTest;
import org.interpss.core.adapter.cim.CGMESReliCapDcCoverageStubTest;
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
import org.interpss.core.dstab.Ac8bIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ac7bIndependentSmibConformanceTest;
import org.interpss.core.dstab.RexsysIndependentSmibConformanceTest;
import org.interpss.core.dstab.Ieeex1AndesSmibConformanceTest;
import org.interpss.core.dstab.IeeeVcPsseSmibConformanceTest;
import org.interpss.core.dstab.Ieeex2PsseSmibConformanceTest;
import org.interpss.core.dstab.Uel1PsseSmibConformanceTest;
import org.interpss.core.dstab.Uel2cu1NativeConformanceTest;
import org.interpss.core.adapter.builder.dstab.Uel2cLimiterTest;
import org.interpss.core.dstab.Oel2cu1NativeConformanceTest;
import org.interpss.core.adapter.builder.dstab.Oel2cLimiterTest;
import org.interpss.core.dstab.Ieeex1SmibIntegrationTest;
import org.interpss.core.dstab.cml.block.DelayControlBlockTests;
import org.interpss.core.dstab.cml.block.FilterControlBlockTests;
import org.interpss.core.dstab.cml.block.FilterNthOrderBlockTests;
import org.interpss.core.dstab.cml.block.IntegrationControlBlockTests;
import org.interpss.core.dstab.cml.block.PIControlBlockTests;
import org.interpss.core.dstab.cml.block.FreezeNonWindupPIControlBlockTest;
import org.interpss.core.dstab.cml.block.Esst4bPIControlBlockTest;
import org.interpss.core.dstab.cml.block.Pss2aLeadLagBlockTest;
import org.interpss.core.dstab.cml.block.WashoutControlBlockTests;
import org.interpss.core.dstab.cml.controller.AnnotateParserTests;
import org.interpss.core.dstab.cml.controller.AnnotationExciterTests;
import org.interpss.core.dstab.mach.Cimtr4PsseSmibConformanceTest;
import org.interpss.core.dstab.mach.EConstMachineTest;
import org.interpss.core.dstab.mach.GensalConformanceTest;
import org.interpss.core.dstab.mach.GensalIndependentSmibConformanceTest;
import org.interpss.core.dstab.mach.GensalHydroSmibTest;
import org.interpss.core.dstab.mach.GenqecIndependentSmibConformanceTest;
import org.interpss.core.dstab.mach.GentraPsseSmibConformanceTest;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReferenceTest;
import org.interpss.core.dstab.Regfma1IndependentTrajectoryTest;
import org.interpss.core.dstab.Regfma1NativeTrajectoryTest;
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
	org.interpss.core.dclf.edclf.IEEE14_EDclfPaperOracleTest.class,
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
	FreezeNonWindupPIControlBlockTest.class,
	Esst4bPIControlBlockTest.class,
	Pss2aLeadLagBlockTest.class,
	WashoutControlBlockTests.class,

	// DStab Machine
	///Eq1Ed1MachineTest.class,
	EConstMachineTest.class,
	///Eq1MachineCaseTest.class,
	GensalConformanceTest.class,
	GensalHydroSmibTest.class,
	GensalIndependentSmibConformanceTest.class,
	GenqecIndependentSmibConformanceTest.class,
	GentraPsseSmibConformanceTest.class,
	Cimtr4PsseSmibConformanceTest.class,
	IeeeVcPsseSmibConformanceTest.class,
	Ieeex2PsseSmibConformanceTest.class,
	Uel1PsseSmibConformanceTest.class,
	Uel2cLimiterTest.class,
	Uel2cu1NativeConformanceTest.class,
	Oel2cLimiterTest.class,
	Oel2cu1NativeConformanceTest.class,
	Bbsex1PsseSmibConformanceTest.class,
	Ieeex1PsseSmibConformanceTest.class,
	Exdc2PsseSmibConformanceTest.class,
	Ieeet4PsseSmibConformanceTest.class,
	ExeliPsseSmibConformanceTest.class,
	ScrxPsseSmibConformanceTest.class,
	DStabNetworkBuilderScrxTest.class,
	Dc3aExciterTest.class,
	Esac4aExciterTest.class,
	Exac4ExciterTest.class,
	Exst2ExciterTest.class,
	St5bExciterTest.class,
	SexsExciterTest.class,
	Exst2PsseSmibConformanceTest.class,
	IeeestPsseSmibConformanceTest.class,
	org.interpss.core.dstab.IeeestPowerInputSmibTest.class,
	org.interpss.core.dstab.IeeestBusMeasurementSmibTest.class,
	org.interpss.core.dstab.IeeestDerivativeSmibTest.class,
	org.interpss.core.dstab.IeeestStaggeredSmibTest.class,
	org.interpss.core.dstab.IeeestZeroNumeratorSmibTest.class,
	Pss3bPsseSmibConformanceTest.class,
	Pss2cPsseSmibConformanceTest.class,
	Pss4bPsseSmibConformanceTest.class,
	PsssbIndependentSmibConformanceTest.class,
	St2cutPsseSmibConformanceTest.class,
	Pss2bIndependentSmibConformanceTest.class,
    Pss2bCompiledStageTest.class,
    Pss2bSharedStateTest.class,
    Pss2bReferenceReplayTest.class,
    Pss2bCoupledBoundaryConformanceTest.class,
    Pss2bLongRunConformanceTest.class,
    Pss2bCoupledPlaybackTest.class,
    Pss2bInputLagPreparationTest.class,
    Pss2bAcceptedPathHistoryTest.class,
    Pss2bImportValidationTest.class,
    org.interpss.core.adapter.builder.dstab.Gewta2NativeRecordTest.class,
    org.interpss.core.adapter.builder.dstab.Gewtp2NativeRecordTest.class,
    org.interpss.core.adapter.builder.dstab.Gewtt1NativeRecordTest.class,
	Pss3cPsseSmibConformanceTest.class,
    Pss4cIndependentSmibConformanceTest.class,
    Pss5cIndependentSmibConformanceTest.class,
    org.interpss.core.adapter.builder.dstab.Pss5cNativeModelTest.class,
    org.interpss.core.adapter.builder.dstab.Pss5cLocalLinearizerTest.class,
    org.interpss.core.adapter.builder.dstab.Pss5cFirstOrderLowTest.class,
    org.interpss.core.adapter.builder.dstab.Pss5cSecondOrderHighTest.class,
    org.interpss.core.adapter.builder.dstab.Pss5cCoordinateObservationTest.class,
    org.interpss.core.adapter.builder.dstab.Pss5cStaticLowTest.class,
    org.interpss.core.dstab.RegfmaLimitNativeTest.class,
    org.interpss.core.adapter.builder.dstab.Oel3cKernelTest.class,
    org.interpss.core.adapter.builder.dstab.Basoel2KernelTest.class,
    org.interpss.core.adapter.builder.dstab.Basoel2ModelTest.class,
    org.interpss.core.adapter.builder.dstab.Oel3cModelTest.class,
    org.interpss.core.dstab.Pss5cNativeSmibTest.class,
    Pss6cPsseSmibConformanceTest.class,
    Pss7cPsseSmibConformanceTest.class,
	St7bPsseSmibConformanceTest.class,
	St7cPsseSmibConformanceTest.class,
	St6cPsseSmibConformanceTest.class,
	St8cPsseSmibConformanceTest.class,
	St9cPsseSmibConformanceTest.class,
	St10cPsseSmibConformanceTest.class,
	EmbeddedTrajectoryReferenceTest.class,
	Regfma1IndependentTrajectoryTest.class,
	Regfma1NativeTrajectoryTest.class,
	org.interpss.core.dstab.RegfmaSharedNativeTrajectoryTest.class,
	org.interpss.core.dstab.RegfmaSharedIntegrationTest.class,
	GenrouConformanceTest.class,
	GenrouAndesSmibConformanceTest.class,
	org.interpss.core.dstab.GenrouNativeSmibTest.class,
	org.interpss.core.dstab.GensalNativeSmibTest.class,
	org.interpss.core.dstab.GensaeFieldSmibTest.class,
	org.interpss.core.dstab.GensaeRecoverySmibTest.class,
	org.interpss.core.dstab.NativeFloatCsvCodecTest.class,
	Esst4bAndesSmibConformanceTest.class,
	Esst4bIndependentSmibConformanceTest.class,
	org.interpss.core.dstab.St4EndpointReuseTest.class,
	Esac5aAndesSmibConformanceTest.class,
	Esac5aIndependentSmibConformanceTest.class,
	Esac6aIndependentSmibConformanceTest.class,
	Esac8bIndependentSmibConformanceTest.class,
	Exac1AndesSmibConformanceTest.class,
	Exac1IndependentSmibConformanceTest.class,
	Exac1aAndesSmibConformanceTest.class,
	Exac1aIndependentSmibConformanceTest.class,
	Exac2AndesSmibConformanceTest.class,
	Exac2IndependentSmibConformanceTest.class,
	Exac4IndependentSmibConformanceTest.class,
	Esac2aAndesExac2SmibConformanceTest.class,
	Esac2aIndependentSmibConformanceTest.class,
	Esac3aIndependentSmibConformanceTest.class,
	Esac4aIndependentSmibConformanceTest.class,
	Ac3cIndependentSmibConformanceTest.class,
	Ac4cIndependentSmibConformanceTest.class,
	Ac5cIndependentSmibConformanceTest.class,
	Ac6cIndependentSmibConformanceTest.class,
	Ac7cIndependentSmibConformanceTest.class,
	Ac8cIndependentSmibConformanceTest.class,
	Ac8cu1PsseSmibConformanceTest.class,
	Ac9cIndependentSmibConformanceTest.class,
	Ac11cIndependentSmibConformanceTest.class,
	Dc1cIndependentSmibConformanceTest.class,
	Dc2cIndependentSmibConformanceTest.class,
	Dc4cIndependentSmibConformanceTest.class,
	Dc4cu1PsseSmibConformanceTest.class,
	Esdc1aIndependentSmibConformanceTest.class,
	Dc3aIndependentSmibConformanceTest.class,
	Esac1aAndesSmibConformanceTest.class,
	Esac1aIndependentSmibConformanceTest.class,
	Esst1aAndesSmibConformanceTest.class,
	Esst1aIndependentSmibConformanceTest.class,
	org.interpss.core.dstab.Esst1aWashoutSmibTest.class,
	org.interpss.core.dstab.Esst1aBypassSmibTest.class,
	org.interpss.core.dstab.Esst1aEventSideSmibTest.class,
	org.interpss.core.dstab.EventSideTrajectorySmibTest.class,
	PsseSt1LocalIntegrationTest.class,
	DynamicParameterValidationTest.class,
    St1InputEquationsTest.class,
    org.interpss.dstab.control.exc.ieee.y1981.st1.St1BoundaryReuseTest.class,
    org.interpss.dstab.control.exc.ieee.y1981.st1.St1FixedScheduleTest.class,
	PsseStaticCurrentLoadVoltageCurveTest.class,
	Exst1AndesSmibConformanceTest.class,
	Ieeet1AndesSmibConformanceTest.class,
	Ieeet1IndependentSmibConformanceTest.class,
	Ieeeg1AndesSmibConformanceTest.class,
	Ieeeg1IndependentSmibConformanceTest.class,
	Ieeeg1dPsseSmibConformanceTest.class,
	HygovAndesSmibConformanceTest.class,
	HygovIndependentSmibConformanceTest.class,
	Hygov2dIndependentSmibConformanceTest.class,
	HygovdIndependentSmibConformanceTest.class,
	HygovdSmibIntegrationTest.class,
	Hygovr1IndependentSmibConformanceTest.class,
	Ggov1IndependentSmibConformanceTest.class,
	Ieeeg3dIndependentSmibConformanceTest.class,
	IeesgodIndependentSmibConformanceTest.class,
	WesgovdIndependentSmibConformanceTest.class,
	PidgovdIndependentSmibConformanceTest.class,
	PidgovPsseSmibConformanceTest.class,
	Csvgn5PsseSmibConformanceTest.class,
	Dera1NativeSmibConformanceTest.class,
	Dera1ModelTest.class,
	Regcb1NativeSmibConformanceTest.class,
	Regcb1ModelTest.class,
	Svsmo1t2PsseConformanceTest.class,
    Svsmo3t2CoupledSmibTest.class,
    Svsmo3t2StaggeredSmibTest.class,
    Svsmo3t2LimiterSmibTest.class,
    Svsmo3t2MssKernelTest.class,
    Svsmo3t2MssReplayTest.class,
    Svsmo3t2MssBoundaryTest.class,
    org.interpss.core.dstab.Svsmo3t2MssTripOrderingTest.class,
    PsseSvsmo3t2ModelTest.class,
	Wt1g1PsseSmibConformanceTest.class,
	Wt2g1PsseSmibConformanceTest.class,
	Wt2e1PsseSmibConformanceTest.class,
	Wt3g1PsseSmibConformanceTest.class,
	Wt3g2PsseSmibConformanceTest.class,
	Wt3t1PsseSmibConformanceTest.class,
	Wt3p1PsseSmibConformanceTest.class,
	Wt4g1PsseSmibConformanceTest.class,
	Wt4e1PsseSmibConformanceTest.class,
	GenqejPsseSmibConformanceTest.class,
	GenqecuPsseSmibConformanceTest.class,
	Gentpj1PsseSmibConformanceTest.class,
	Gewtgcu1NativeConformanceTest.class,
	Wt3e1PsseSmibConformanceTest.class,
	WpidhydIndependentSmibConformanceTest.class,
	GastdIndependentSmibConformanceTest.class,
	Gast2adIndependentSmibConformanceTest.class,
	GastwddIndependentSmibConformanceTest.class,
	Degov1dIndependentSmibConformanceTest.class,
	Tgov3dIndependentSmibConformanceTest.class,
	H6eIndependentSmibConformanceTest.class,
	Hyg3IndependentSmibConformanceTest.class,
	Pss2aIndependentSmibConformanceTest.class,
    Pss2aScalarPreparationTest.class,
	Pss1aPowerWorldPlaybackConformanceTest.class,
	Pss1aPowerWorldActiveConformanceTest.class,
	Wtdtb1PowerWorldPlaybackConformanceTest.class,
	Wtdtb1SmibIntegrationTest.class,
	Wtdtb1NativePartialConformanceTest.class,
	Wtptb1NativePartialConformanceTest.class,
	Wtptb1SmibIntegrationTest.class,
	Wtptb1NativeExtendedConformanceTest.class,
	WtgwgoaNativePartialConformanceTest.class,
	WtgibffraNativePartialConformanceTest.class,
	ReeceLocalPiNativePartialConformanceTest.class,
	ReeceRuntimeNativePartialConformanceTest.class,
	ReeceTypedSmibIntegrationTest.class,
	WtgibffraSmibIntegrationTest.class,
	PsseWtgibffraModelTest.class,
	PsseGenclsGovernorTest.class,
	ReecdAuxiliaryPowerNativeConformanceTest.class,
	WtgwgoaSmibIntegrationTest.class,
	PsseWtgwgoaModelTest.class,
	PsseWtptb1ModelTest.class,
	Tgov1IndependentSmibConformanceTest.class,
	Tgov1LimitNativeConformanceTest.class,
	Tgov1PowerWorldLimitSmibTest.class,
	Tgov1BaseNativeSmibTest.class,
	WttqaSpeedFeedbackConformanceTest.class,
	WttqaNativePartialConformanceTest.class,
	WtdtaNativeConformanceTest.class,
	WtdtaNativeObservationConformanceTest.class,
	WtaraAlgebraicConformanceTest.class,
	WtaraNativeConformanceTest.class,
	WtptaNativeConformanceTest.class,
	WtptaNativeObservationConformanceTest.class,
	ReecaNativeBoundaryConformanceTest.class,
	ReecaNativeConformanceTest.class,
	Lcfb1IndependentSmibConformanceTest.class,
	Dc4bIndependentSmibConformanceTest.class,
	St1cIndependentSmibConformanceTest.class,
	Esst2aIndependentSmibConformanceTest.class,
	St2cIndependentSmibConformanceTest.class,
	St3cIndependentSmibConformanceTest.class,
	St4cIndependentSmibConformanceTest.class,
	St4cu1PsseSmibConformanceTest.class,
	St5bIndependentSmibConformanceTest.class,
	St5cIndependentSmibConformanceTest.class,
	St6bIndependentSmibConformanceTest.class,
	Exdc2AndesSmibConformanceTest.class,
	Ac8bAndesSmibConformanceTest.class,
	Ac8bIndependentSmibConformanceTest.class,
	Ac7bIndependentSmibConformanceTest.class,
	RexsysIndependentSmibConformanceTest.class,
	Exdc2aOpenSourceEquationConformanceTest.class,
	Ieeet4OpenSourceEquationConformanceTest.class,
	Ac8bAndesEquationConformanceTest.class,
	Ac7bDynawoEquationConformanceTest.class,
	Ac7bImportTest.class,
	RexsysExciterTest.class,
	Esac6aExciterTest.class,
	Expic1ExciterTest.class,
	Expic1NativeVerificationTest.class,
	Exac2FieldBaseVerificationTest.class,
	WttqaCompatibilityTest.class,
	WttqaCurveInputConformanceTest.class,
	ReaxbGenericHostTest.class,
	Dc4bExciterTest.class,
	DStabNetworkBuilderDc4cTest.class,
	St6bExciterTest.class,
	St6cExciterTest.class,
	St4cExciterTest.class,
	St2cExciterTest.class,
	St3cExciterTest.class,
	St5cExciterTest.class,
	St7bExciterTest.class,
	St7cExciterTest.class,
	St8cExciterTest.class,
	St9cExciterTest.class,
	St10cExciterTest.class,
	St1cExciterTest.class,
	ExeliExciterTest.class,
	Esst2aExciterTest.class,
	Exst3ExciterTest.class,
	Exst3IndependentSmibConformanceTest.class,
	Esst3aExciterTest.class,
	Esst3aIndependentSmibConformanceTest.class,
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
	Ac1cIndependentSmibConformanceTest.class,
	DStabNetworkBuilderAc2cTest.class,
	Ac2cIndependentSmibConformanceTest.class,
	DStabNetworkBuilderAc3cTest.class,
	DStabNetworkBuilderAc4cTest.class,
	DStabNetworkBuilderAc5cTest.class,
	DStabNetworkBuilderAc6cTest.class,
	DStabNetworkBuilderAc7cTest.class,
	DStabNetworkBuilderAc8cTest.class,
	DStabNetworkBuilderAc9cTest.class,
	DStabNetworkBuilderAc11cTest.class,
	DStabNetworkBuilderBbsex1Test.class,
	DStabNetworkBuilderEsdc1a2aTest.class,
	DStabNetworkBuilderDc1c2cTest.class,
	Ieeex1AndesEquationConformanceTest.class,
	DStabNetworkBuilderEsac5aTest.class,
	DStabNetworkBuilderExac1Test.class,
	DStabNetworkBuilderEsurryTest.class,
	EsurryIndependentSmibConformanceTest.class,
	DStabNetworkBuilderExac1aTest.class,
	DStabNetworkBuilderExac2Test.class,
	DStabNetworkBuilderEsac1aTest.class,
	DStabNetworkBuilderEsac2aTest.class,
	DStabNetworkBuilderEsac3aTest.class,
	DStabNetworkBuilderEsac8bTest.class,
	DStabNetworkBuilderGovernorTest.class,
	DStabNetworkBuilderGastdTest.class,
	DStabNetworkBuilderIeeeg1dTest.class,
	DStabNetworkBuilderIeesgodTest.class,
	DStabNetworkBuilderTgov1dTest.class,
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
	PssCanonicalNativeSchemaTest.class,
	PssCompensationBaseTest.class,
	ExciterInputContractTest.class,
	PssCompensationNativeConformanceTest.class,
	PsseEsst4bExciterTest.class,
	PsseEsst1aExciterTest.class,
	PsseExst1FieldLimitTest.class,
	PsseGgov1GovernorTest.class,
	PsseH6eGovernorTest.class,
	PsseHyg3GovernorTest.class,
    Hyg3PreparedCurveTest.class,
    Hyg3SharedStateTest.class,
    Hyg3SharedSmibTest.class,
    Hyg3StateConformanceTest.class,
    Hyg3LimitConformanceTest.class,
    Hyg3LongDurationConformanceTest.class,
    org.interpss.dstab.control.gov.psse.hyg3.Hyg3InitialCoordinateReplayTest.class,
    org.interpss.dstab.control.gov.psse.hyg3.Hyg3MechanicalBoundaryReplayTest.class,
    Hyg3BypassConformanceTest.class,
    Hyg3DerivativePreparationConformanceTest.class,
    Hyg3ParameterValidationTest.class,
    Hyg3FastFeedbackReferenceTest.class,
    Wsieg1SharedStateTest.class,
    Urgs3tSharedStateTest.class,
    Urgs3tReferenceTest.class,
    Urgs3tCommandBoundaryTest.class,
    Urgs3tPowerBoundaryTest.class,
    Urgs3tRunBoundaryTest.class,
    Urgs3tProductionStepTest.class,
    Urgs3tValveLimitsTest.class,
    Urgs3tMachineTripTest.class,
    Urgs3tValveReleaseTest.class,
    org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tReactivationLifecycleTest.class,
    org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tReactivationReferenceTest.class,
    org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tTripPublicationOrderTest.class,
    Wsieg1SharedSmibTest.class,
    Wsieg1StateConformanceTest.class,
    org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1ReactivationTest.class,
    Wsieg1BypassLimitConformanceTest.class,
    Wsieg1TimePreparationTest.class,
    Wsieg1TimeReferenceTest.class,
    Wsieg1InitialLimitsTest.class,
    Wsieg1InitialReferenceTest.class,
    Wsieg1ShaftInitializationTest.class,
    Wsieg1BaseConformanceTest.class,
    Wsieg1InactiveReferenceTest.class,
    Wsieg1BoundaryPlaybackTest.class,
    Wsieg1MachineEventConformanceTest.class,
    Wsieg1MeasurementDependenciesTest.class,
	PsseHygovrGovernorTest.class,
	Lcfb1PrefControllerTest.class,
	PsseRegca1ConverterTest.class,
	PsseRenewableTwoStepIntegrationTest.class,
	PsseRegfma1ModelTest.class,
	PsseRegfma1NativeSchemaTest.class,
	PsseReeca1ControllerTest.class,
	PsseReecb1ControllerTest.class,
	PsseReecc1ControllerTest.class,
	PsseReecd1ControllerTest.class,
	PsseReeceModelTest.class,
	ReeceNativeImportSmibIntegrationTest.class,
	Regfmb1NativeContractConformanceTest.class,
	Regfmc1NativeContractConformanceTest.class,
	Regfmc1NativeRuntimePartialConformanceTest.class,
	Regfmc1NativeImportSmibTest.class,
	Repcgfmc1NativeContractConformanceTest.class,
	RepccNativeContractConformanceTest.class,
	RepccKernelNativeConformanceTest.class,
	RepccShuntNativeConformanceTest.class,
	RepccNetworkBoundaryConformanceTest.class,
	RepccTypedRuntimeSmibTest.class,
	RepccRuntimeNativeSmibTest.class,
	RepccSwitchingNativeSmibTest.class,
	HplntdAggregationNativeConformanceTest.class,
	ReaxdLagNativeConformanceTest.class,
	HplntdKernelNativeConformanceTest.class,
	HplntdBoundaryConformanceTest.class,
	HplntdTypedRuntimeSmibTest.class,
	HplntdImportedSmibTest.class,
	HplntdFaultNativeSmibTest.class,
	HplntdSelectorNativeSmibTest.class,
	HplntdProportionalNativeSmibTest.class,
	HplntdReferenceLimitNativeConformanceTest.class,
	HplntdReferenceLimitNativeSmibTest.class,
	HplntdShuntNativeConformanceTest.class,
	HplntdSwitchingNativeSmibTest.class,
	HplntdDischargeNativeConformanceTest.class,
	PsseRepccModelTest.class,
	Repcgfmc1KernelNativeConformanceTest.class,
	Repcgfmc1NativeImportSmibTest.class,
	PsseRepcgfmc1ModelTest.class,
	PsseRegfmc1ModelTest.class,
	Regfmb1NativeRuntimePartialConformanceTest.class,
	Regfmb1NativeImportSmibTest.class,
	PsseRegfmb1ModelTest.class,
	ReeceRegcbNativePartialConformanceTest.class,
	PsseRepca1PlantControllerTest.class,
	PsseType3WindControllerTest.class,
	PsseWtdtb1ModelTest.class,
	PsseCsvgn5ModelTest.class,
	PsseSvsmo1t2ModelTest.class,
	Plntbu1ModelTest.class,
	PsseWt1g1ModelTest.class,
	PsseWt2g1ModelTest.class,
	PsseWt2e1ModelTest.class,
	PsseWt3g1ModelTest.class,
	PsseWt3g2ModelTest.class,
	PsseWt3p1ModelTest.class,
	PsseWt3t1ModelTest.class,
	PsseWt4e1ModelTest.class,
	PsseWt4g1ModelTest.class,
	PsseWt3e1ModelTest.class,
	Gewtecu1ModelTest.class,
	Gewt2mu1ModelTest.class,
	Gewtaru1ModelTest.class,
	Gewtgcu1ModelTest.class,
	Gewtgdu1ModelTest.class,
	Gewtptu1ModelTest.class,
	Reax3bu1ModelTest.class,
	Reax4bu1ModelTest.class,
	WshyddGovernorTest.class,
	WshygpGovernorTest.class,
	PsseWt12t1ModelTest.class,
	PsseWt12a1ModelTest.class,
	PsseWt12a1bModelTest.class,
	org.interpss.core.adapter.builder.dstab.GenqecNativeSchemaTest.class,
	org.interpss.core.dstab.GenqecNativeSelectorConformanceTest.class,
	org.interpss.core.dstab.GenqecSalientNativeConformanceTest.class,
	org.interpss.core.dstab.GenqecKwNativeConformanceTest.class,
	org.interpss.core.dstab.Wt12a1bNativeConformanceTest.class,
	RenewableControlIntegrationTest.class,
	RenewableAggregateQvModeTest.class,
	Reecc1NativeConformanceTest.class,
    org.interpss.core.dstab.ReeccPlantReplayConformanceTest.class,
	Type3WindAndesTrajectoryTest.class,
	Type3WindIndependentTrajectoryTest.class,
	PsseHygovGovernorTest.class,
	PsseLegacyControllerMappingTest.class,
	PSSEDStabDirectParserReportTest.class,
	PsseDyrRecordReaderTest.class,
	PsseDyrRepresentativeSelectorTest.class,
	EmbeddedTrajectoryCoverageInventoryTest.class,
	ReferenceArtifactManifestIntegrityTest.class,
	org.interpss.core.dstab.reference.BenchmarkOutputGuardTest.class,
	PowerWorldCsvRepeatabilityTest.class,
	PsseRunQualityTest.class,
	EndpointAlignedStateMonitorTest.class,
	RegcaZeroQRecoveryTest.class,
	RegcaCurrentLimitNativeTest.class,
	RegcaNetworkIterationTest.class,
	org.interpss.core.dstab.RegcaReferenceGainMemoryTest.class,
	org.interpss.core.dstab.RegcaReferenceHighVoltageCurrentTest.class,
	org.interpss.core.dstab.RegcaAcceptedVoltageMemoryTest.class,
	org.interpss.core.dstab.ReeccPlantNetworkConformanceTest.class,
	PsseNetworkConvergenceTest.class,
	PsseNetworkIterationLifecycleTest.class,
	org.interpss.core.dstab.StaticLoadMatrixSplitTest.class,
	Perc1ModelTest.class,
	Cmldznu2ModelTest.class,
    Cmldblu1SharedStateTest.class,
    Cmldblu1SharedSmibTest.class,
    Cmldblu1StateConformanceTest.class,
    Cmldblu1InitializationTest.class,
    Cmldblu1CompressorLifecycleTest.class,
    LD1PACParameterBindingTest.class,
    Cmldblu1CompressorInitializationTest.class,
    CompressorCurrentConformanceTest.class,
    Acmtblu1SharedSmibTest.class,
    CompressorFrequencyInputTest.class,
    CompressorProtectionConformanceTest.class,
    CompressorThermalConformanceTest.class,
    CompressorStartupFrequencyTest.class,
    CompressorThermalBypassTest.class,
    CompressorShortLagTest.class,
    CompositeStaticPolynomialSmibTest.class,
    CompositeOmittedDistributionTest.class,
    CompositeStaticLowVoltageTest.class,
    ElectronicFullRecoverySmibTest.class,
    DistributedStaticPortLifecycleTest.class,
    CompositeTapControlSmibTest.class,
    org.interpss.core.dstab.dynLoad.CompositeTapMeasurementCoordinateTest.class,
    Tiocr1AcceptedHistoryTest.class,
    Tiocr1ResetReferenceTest.class,
    Tiocr1LoadOnlyReferenceTest.class,
    org.interpss.core.dstab.Tiocr1TerminalReferenceTest.class,
    org.interpss.core.dstab.Tiocr1PhysicalTerminalReferenceTest.class,
    org.interpss.core.dstab.Tiocr1BreakerTransferReferenceTest.class,
    org.interpss.core.dstab.Tiocr1MultipleTransferReferenceTest.class,
    org.interpss.core.dstab.Tiocr1LowVoltageReferenceTest.class,
    org.interpss.core.dstab.Tiocr1ReferenceSegmentationContractTest.class,
    org.interpss.core.dstab.StagedRelayTargetLifecycleTest.class,
    org.interpss.core.dstab.dynLoad.Tiocr1CompositeLoadTest.class,
    org.interpss.core.dstab.Tiocr1ExactLoadImportTest.class,
    org.interpss.core.dstab.dynLoad.CompositeCollapsedFullTripTest.class,
    org.interpss.core.dstab.dynLoad.CompositePartlyCollapsedFullTripTest.class,
    org.interpss.core.dstab.dynLoad.CompositeMotorRecloseTest.class,
    org.interpss.core.dstab.dynLoad.CompositeMotorProtectionTimingTest.class,
    org.interpss.core.dstab.dynLoad.MotorProtectionThresholdAuditTest.class,
    org.interpss.core.dstab.dynLoad.CompositeMotorOverlapValidationTest.class,
    org.interpss.core.dstab.dynLoad.CompositeMotorOverlapTest.class,
    org.interpss.core.dstab.dynLoad.CompositeLoadChangeTest.class,
    Cdc6tReferenceContractTest.class,
    Cdc6tMeasurementsTest.class,
    Cdc6tTransducerBoundaryTest.class,
    Cdc6tCurrentOrderTest.class,
    Cdc6tVoltageCurrentLimitTest.class,
    Cdc6tConverterBoundaryTest.class,
    Cdc6tConductingLinkTest.class,
    Cdc6tRecoveryRampTest.class,
    Cdc6tRecoveryBaseTest.class,
    Cdc6tTransferredModeBaseTest.class,
    Cdc6tDeepRecoveryTest.class,
    Cdc6tLongRunTest.class,
    Cdc6tInitialBalanceLifecycleTest.class,
    Cdc6tGovernedSourceTest.class,
    Cdc6tRectifierProtectionTest.class,
    Cdc6tInverterProtectionTest.class,
    Cdc6tBypassTest.class,
    Cdc6tModeSwitchTest.class,
    Cdc6tNormalRuntimeTest.class,
    Cdc6tNetworkOwnershipTest.class,
    Cdc6tTerminalPortsTest.class,
    Cdc6tCoupledNetworkTest.class,
    Cdc6tCoupledReferenceTest.class,
    Cdc6tOperatingBoundaryTest.class,
    Cdc6tProtectionCoordinatorTest.class,
    Cdc6tProtectedNetworkTest.class,
    Cdc6tMinimumCurrentTest.class,
    Cdc6tParametersTest.class,
    Chvdc2ParametersTest.class,
    Chvdc2DcLineTest.class,
    Chvdc2CurrentControlsTest.class,
    Chvdc2CurrentControlsReferenceTest.class,
    Chvdc2OrderControlsTest.class,
    Chvdc2OrderControlsReferenceTest.class,
    Chvdc2ConverterBoundaryTest.class,
    Chvdc2ConverterReferenceTest.class,
    Chvdc2ProtectionTest.class,
    Chvdc2ProtectionReferenceTest.class,
    Chvdc2ControlAssemblyTest.class,
    Chvdc2ControlAssemblyReferenceTest.class,
    Chvdc2CoupledRuntimeTest.class,
    Chvdc2CoupledReferenceTest.class,
    Chvdc2CapacitanceReferenceTest.class,
    Chvdc2RunBoundaryReferenceTest.class,
    Chvdc2MeasurementReferenceTest.class,
    Chvdc2CurrentSamplingReferenceTest.class,
    Chvdc2EventIntervalReferenceTest.class,
    Chvdc2NetworkPreparationTest.class,
    Chvdc2NetworkPreparationReferenceTest.class,
    Chvdc2TerminalPortsTest.class,
    Chvdc2TerminalReferenceTest.class,
    Cdc6tNetworkPreparationTest.class,
    org.interpss.core.dstab.Cdc6tInitialCeilingTest.class,
    Cdc6tConstantGammaTest.class,
    Cdc6tLimitedGammaTest.class,
    LccSourceControlsTest.class,
    Cdc6tBypassSwitchNetworkTest.class,
    org.interpss.core.dstab.Cdc6tBypassSamplingReferenceTest.class,
    org.interpss.core.dstab.Cdc6tCallBoundaryReferenceTest.class,
    HspUsageRankedModelTest.class,
    HspModelReferenceConformanceTest.class,
	IeelLoadModelTest.class,
	Texas2kWindProfileCoverageTest.class,
	Texas2kRegfma1CoverageTest.class,
	Texas2kFullDynamicCoverageTest.class,
	Texas2kHygovCoverageTest.class,
	Texas2kLegacyControllerProfileTest.class,
	Texas2kOneSecondDriftTest.class,
	Texas2kIndependentRenewableParameterParityTest.class,
	Texas2kSixCaseDynamicSmokeTest.class,
    org.interpss.core.dstab.Texas7kSimulationTest.class,
    org.interpss.core.dstab.BsgSmibVerificationTest.class,
    org.interpss.core.adapter.builder.dstab.PsseBsgLoaderTest.class,
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
	org.interpss.core.adapter.psse.raw.nbreaker.NodebreakerConverterAuditTest.class,
	org.interpss.core.adapter.psse.raw.nbreaker.LccResistanceReferenceTest.class,

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
	//IEEE118CimVsMatpowerJsonCompareTest.class,
	CGMESCasType3HourCoverageStubTest.class,
	CGMESCasCoverageStubTest.class,
	CGMESReliCapDcCoverageStubTest.class,
	CGMESCasP4AclfSmokeStubTest.class,
	//CGMESCasP4AclfUnconvergedStubTest.class,

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
