package org.interpss.core.aclf;

import static org.interpss.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.impl.AclfOut_PSSE;
import org.interpss.display.impl.AclfOut_PSSE.Format;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE9_MultiGenTest extends CorePluginTestSetup{
	@Test
	public void multiGenTest() throws InterpssException{
		/*The original generator at Bus3 is split into 2 identical generators
		 * 
		 *  //Gen data
		 *      3,'1 ',    42.500,   -5.430, 99990.000,-99990.000,1.02500,     0,   100.000,   0.00000,   0.214,   0.00000,   0.00000,1.00000,1,  100.0,  9999.000, -9999.000,   1,1.0000,   0,0.0000,   0,0.0000,   0,0.0000
	     *      3,'2 ',    42.500,   -5.430, 99990.000,-99990.000,1.02500,     0,   100.000,   0.00000,   0.214,   0.00000,   0.00000,1.00000,1,  100.0,  9999.000, -9999.000,   1,1.0000,   0,0.0000,   0,0.0000,   0,0.0000
		 * 
		 */
		AclfNetwork net = IpssAdapter.importNet("testdata/adpter/psse/v30/IEEE9Bus/ieee9_multiGen.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();
		
		System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  	
	  	assertTrue(net.isLfConverged());
 		System.out.println(AclfOutFunc.loadFlowSummary(net));
 		//System.out.println(AclfOut_PSSE.lfResults(net, Format.GUI));
 		Complex gen1_PQ=net.getBus("Bus3").getGenList().get(0).getGen();
 		System.out.println(gen1_PQ);
 		assertTrue(NumericUtil.equals(gen1_PQ,new Complex(0.425,-0.054617),1.0E-4));
	  	
  		
	}

}
