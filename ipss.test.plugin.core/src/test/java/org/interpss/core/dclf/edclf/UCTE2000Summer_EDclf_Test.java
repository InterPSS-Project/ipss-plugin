package org.interpss.core.dclf.edclf;

import static com.interpss.core.algo.dclf.solver.IConnectBusProcessor.predicateConnectBus;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.EDclfAlgorithm;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.datatype.Mismatch;

public class UCTE2000Summer_EDclf_Test extends CorePluginTestSetup {
	@Test 
	public void edclfTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/UCTE_2000_Summer.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf(DclfMethod.STD);
		
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
	}
	
	@Test 
	public void edclfLossTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/UCTE_2000_Summer.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf();
		
		System.out.println("EDclf/Loss Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
	}

	@Test 
	public void edclfVCorrectionTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/UCTE_2000_Summer.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf(DclfMethod.STD);
		
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR, predicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		
		DclfAlgoObjectFactory.createConnectBusProcessor(aclfNet)
        					 .updateConnectBusVoltage();

		System.out.println("EDclf/VCorrection Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		mis = aclfNet.maxMismatch(AclfMethodType.NR, predicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		assertTrue(mis.maxMis.abs() < 0.0001);
	}
	
	@Test 
	public void edclfVCorAlterDataTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/UCTE_2000_Summer.ieee")
				.getAclfNet();	
		
		AclfBus bus = aclfNet.getBus("Bus706");
		bus.getContributeLoad("Bus706-L1").setLoadCP(new Complex(1.5349, -1.0));

		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf(DclfMethod.STD);
		
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR, predicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		
		DclfAlgoObjectFactory.createConnectBusProcessor(aclfNet)
        					 .updateConnectBusVoltage();

		System.out.println("EDclf/VCorrection Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		mis = aclfNet.maxMismatch(AclfMethodType.NR, predicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		assertTrue(mis.maxMis.abs() < 0.0001);
	}	
}

