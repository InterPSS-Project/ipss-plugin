package org.interpss.core.dclf.edclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.EDclfAlgorithm;
import static com.interpss.core.algo.dclf.solver.IConnectBusProcessor.PredicateConnectBus;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.algo.dclf.solver.IEDclfSolver;
import com.interpss.core.algo.impl.solver.YMatrixSolver;
import com.interpss.core.datatype.Mismatch;

public class IEEE14_EDclf_Test extends CorePluginTestSetup {
	@Test 
	public void edclfTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf(DclfMethod.STD);
		/*
                V_N (merged eDCLF voltage iteration)
		  Bus4  ~1.01638
		  Bus5  ~1.01805
		  Bus14 ~1.03498
		*/
		assertEquals(1.01638, aclfNet.getBus("Bus4").getVoltageMag(), 0.0001);
		assertEquals(1.01805, aclfNet.getBus("Bus5").getVoltageMag(), 0.0001);
		assertEquals(1.03498, aclfNet.getBus("Bus14").getVoltageMag(), 0.0001);	
		
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		System.out.println("Swing Bus P(2.32393): " + edclfAlgo.getBusPower(edclfAlgo.getDclfAlgoBus("Bus1")));
	}
	
	@Test 
	public void edclfLossTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf();
		
		System.out.println("EDclf/Loss Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		System.out.println("Swing Bus P(2.32393): " + edclfAlgo.getBusPower(edclfAlgo.getDclfAlgoBus("Bus1")));
	}	
	
	@Test 
	public void edclfVCorrectionTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf();
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR, PredicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		
		DclfAlgoObjectFactory.createConnectBusProcessor(aclfNet)
							 .updateConnectBusVoltage();

		System.out.println("EDclf/VCorrection Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		mis = aclfNet.maxMismatch(AclfMethodType.NR, PredicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
	}
	
	@Test 
	public void edclfVCorInactiveBusTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		aclfNet.getBus("Bus14").setStatus(false);
		aclfNet.getBranch("Bus13->Bus14(1)").setStatus(false);
		aclfNet.getBranch("Bus9->Bus14(1)").setStatus(false);
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf(DclfMethod.STD);
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR, PredicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		
		DclfAlgoObjectFactory.createConnectBusProcessor(aclfNet)
							 .updateConnectBusVoltage();

		System.out.println("EDclf/VCorrection Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		mis = aclfNet.maxMismatch(AclfMethodType.NR, PredicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
	}	


	@Test 
	public void checkVoltageTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateDclf();
		edclfAlgo.transfer2AclfNet(true);
		
		IEDclfSolver solver = DclfAlgoObjectFactory.createEDclfSolver(edclfAlgo);
		
		Complex[] voltAry = solver.calPQBusVoltMag();
		/*
               V_N (merged calPQBusVoltMag)
		  index0 Bus4  ~1.01638
		  index1 Bus5  ~1.01805
		  index8 Bus14 ~1.03510
		*/
		assertEquals(1.01638, voltAry[0].getReal(), 0.0001);
		assertEquals(1.01805, voltAry[1].getReal(), 0.0001);
		assertEquals(1.03510, voltAry[8].getReal(), 0.0001);		
	}
	
	@Test 
	public void connectionBusTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		//aclfNet.initContributeGenLoad();
		
		assertTrue(PredicateConnectBus.test(aclfNet.getBus("Bus7")) == true);
		assertTrue(PredicateConnectBus.test(aclfNet.getBus("Bus14")) == false);
		
		ISparseEqnComplex[] ySet = new YMatrixSolver(aclfNet)
				.formYMatrixSet(false, PredicateConnectBus);
		
		assertTrue(ySet[0].getDimension() == 1);
		assertTrue(ySet[3].getDimension() == 13);
	}	
}

