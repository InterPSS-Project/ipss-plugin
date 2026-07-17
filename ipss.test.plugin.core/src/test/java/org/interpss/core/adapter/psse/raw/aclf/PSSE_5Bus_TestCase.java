package org.interpss.core.adapter.psse.raw.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.numeric.datatype.Unit.UnitType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSE_5Bus_TestCase extends CorePluginTestSetup { 
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = new PSSEDirectParser(30).parse("testData/adpter/psse/PSSE_5Bus_Test.raw");
		
		//System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

	  	AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
  		//System.out.println(p.getReal() + "  " + p.getImaginary());
  		assertTrue(Math.abs(p.getReal()-22.546)<0.01);
  		assertTrue(Math.abs(p.getImaginary()-15.853)<0.01);	  			
	}
}


