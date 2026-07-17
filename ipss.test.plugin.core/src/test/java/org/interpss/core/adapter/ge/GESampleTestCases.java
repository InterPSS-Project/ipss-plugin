package org.interpss.core.adapter.ge;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class GESampleTestCases extends CorePluginTestSetup {
	@Test
	public void odmAdapterTestCase() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.GE_PSLF)
				.load("testData/adpter/ge/Sample18Bus.epc")
				.getAclfNet();	
		
//		IODMAdapter adapter = ODMObjectFactory.createODMAdapter(ODMFileFormatEnum.GePSLF);
//		assertTrue(adapter.parseInputFile("testdata/ge/Sample18Bus.epc"));		
//		
//		AclfNetwork net = PluginSpringCtx
//				.getOdm2AclfMapper()
//				.map2Model((AclfModelParser)adapter.getModel())
//				.getAclfNet();
		
		assertTrue(net.getNoBus() == 18);
		assertTrue(net.getNoBranch() == 24);
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	
	  	algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);
	  	
	  	algo.loadflow();
		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
  		AclfBus swingBus = (AclfBus)net.getBus("Bus101");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		// TODO System.out.println(swingBus.toString(net.getBaseKva()));
  		//assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-5.234)<0.01);
  		//assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-1.108)<0.01);
	}	
}

