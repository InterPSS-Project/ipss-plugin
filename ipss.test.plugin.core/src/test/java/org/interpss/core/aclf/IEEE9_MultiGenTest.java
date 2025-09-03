package org.interpss.core.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
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
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/IEEE9Bus/ieee9_multiGen.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();
		
		//System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
  	    /*-------------------------
  	     *       base case
  	     *------------------------
  	     */
	  	assertTrue(net.isLfConverged());
 		//System.out.println(AclfOutFunc.loadFlowSummary(net));
 		//System.out.println(AclfOut_PSSE.lfResults(net, Format.GUI));
 		Complex gen1_PQ=net.getBus("Bus3").getContributeGenList().get(0).getGen();
 		//System.out.println(gen1_PQ);
 		assertTrue(NumericUtil.equals(gen1_PQ,new Complex(0.425,-0.054617),1.0E-4));
	  	
 		//test the getGenP
 		assertTrue(net.getBus("Bus3").getGenP()==0.850);
 		
 		/*------------------------------------
  	     *       case -2: Gen-1 at bus3 is out of service
  	     *------------------------------------
 		 */
 		net.getBus("Bus3").getContributeGenList().get(0).setStatus(false);
 		
 		 // we need re-initialize bus P and Q again.
 		net.getBus("Bus3").initContributeGen(false);
 		 // we set initNetwork to false below to check if the initContributeGen() above is sufficient to update the network for power flow   
 		algo.loadflow(false);
 	  	
	  	assertTrue(net.isLfConverged());
 		//System.out.println(AclfOutFunc.loadFlowSummary(net));
 		
 		//test the getGenP
 		assertTrue(net.getBus("Bus3").getGenP()==0.425); 
 		
 		/*------------------------------------
  	     *       case -3 : Gen-1 at bus3 is back to service
  	     *------------------------------------
 		 */
 		net.getBus("Bus3").getContributeGenList().get(0).setStatus(true);
 		
 	     // loadflow(true) will re-initialize the network
 		
 		algo.loadflow(true);
 	  	
	  	assertTrue(net.isLfConverged());
 		//System.out.println(AclfOutFunc.loadFlowSummary(net));
 		
 		//test the getGenP
 		assertTrue(net.getBus("Bus3").getGenP()==0.85); 
 		
	}

}
