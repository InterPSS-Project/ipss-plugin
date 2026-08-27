package org.interpss.core.adapter.psse.json.aclf;
 
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

import org.interpss.fadapter.psse.PSSEJsonDirectParser;
public class PSSEJSon_IEEE9Bus_DSL_Test extends CorePluginTestSetup { 
	@Test
	public void testJSon() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse("testData/adpter/psse/json/ieee9.rawx");

		testVAclf(net);
	}
	
	@Test
	public void testJSon_Converter_output() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse("testData/adpter/psse/json/ieee9_output.rawx");

		testVAclf(net);
	}
	
	
	private void testVAclf(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

	  	AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.71646)<0.00001);
  		assertTrue(Math.abs(p.getImaginary()-0.27107)<0.00001);
	}	
}


