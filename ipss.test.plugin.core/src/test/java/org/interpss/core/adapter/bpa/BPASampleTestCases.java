package org.interpss.core.adapter.bpa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class BPASampleTestCases extends CorePluginTestSetup {
	@Test
	public void odmAdapterTestCase() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.BPA)
				.load("testData/adpter/bpa/IEEE9.dat")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	
	  	algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);
	  	
	  	algo.loadflow();
		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		System.out.println(swing.getGenResults(UnitType.PU).getImaginary());
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-1.0586)<0.01);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.4366)<0.01);
	}	

	@Test
	public void odmAdapterTestCase1() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.BPA)
				.load("testData/adpter/bpa/Test009bpa.DAT")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-0.7164)<0.01);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.2705)<0.01);
	}
	
	public void outputTestCase() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.BPA)
				.load("testData/adpter/bpa/Test009bpa.dat")
				.getAclfNet();	
		//System.out.println(net.net2String());
	}
	
}

