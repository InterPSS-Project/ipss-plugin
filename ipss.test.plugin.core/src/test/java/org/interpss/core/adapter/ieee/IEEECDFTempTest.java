package org.interpss.core.adapter.ieee;

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
import com.interpss.simu.SimuContext;

public class IEEECDFTempTest extends CorePluginTestSetup {
	@Test
	public void bus39testCase() throws Exception{
		IpssFileAdapter adapter = CorePluginFactory.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF);
		SimuContext simuCtx = adapter.load("testData/adpter/ieee_format/ieee039.DAT");

		AclfNetwork net = simuCtx.getAclfNet();
  		assertTrue(net.getBusList().size() == 39);

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus31");
 		AclfSwingBusAdapter swing = swingBus.toSwingBus();
//		  31 BUS-31  100   1  1  3 0.982 0.     9.2      4.6       572.8349207.0362 100.    .98200 999900 -99990    0.      0.        0                                                                                                                                                                            
  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getReal()-5.7286653)<0.0001);
  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.0766519)<0.0001);
  		
  		//System.out.println(AclfOut.lfResultsBusStyle(net));
	}
}

