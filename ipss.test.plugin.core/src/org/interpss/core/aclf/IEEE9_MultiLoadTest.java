package org.interpss.core.aclf;

import static org.interpss.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE9_MultiLoadTest extends CorePluginTestSetup{
	
	/*
	 * original load data
	 * 8,'1 ',1,   1,   1,   100.000,    35.000,     0.000,     0.000,     0.000,     0.000,   1
	 * 
	 * 
	 * New load data for Bus8
	 *      8,'1 ',1,   1,   1,   50.000,    30.000,     0.000,     0.000,     0.000,     0.000,   1
	        8,'2 ',1,   1,   1,   50.000,     5.000,     0.000,     0.000,     0.000,     0.000,   1
	 */
	
	@Test
	public void multiLoadTest() throws InterpssException{
	
		AclfNetwork net = IpssAdapter.importNet("testdata/adpter/psse/v30/IEEE9Bus/ieee9_multiLoad.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();
		
		System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
  	    /*-------------------------
  	     *       base case
  	     *------------------------
  	     */
	  	assertTrue(net.isLfConverged());
 		System.out.println(AclfOutFunc.loadFlowSummary(net));
 		//System.out.println(AclfOut_PSSE.lfResults(net, Format.GUI));
 		Complex gen1_PQ=net.getBus("Bus3").getGenList().get(0).getGen();
 		System.out.println(gen1_PQ);
 		assertTrue(NumericUtil.equals(gen1_PQ,new Complex(0.85, -0.1092),1.0E-4));
 		
 		//Test loadPQ of bus8
 		assertTrue(NumericUtil.equals(net.getBus("Bus8").getLoadPQ(),new Complex(1.0, 0.35),1.0E-4));
	  	
 		
 		/*------------------------------------
  	     *       case -2: Load-1 at bus8 is out of service
  	     *------------------------------------
 		 */
 		net.getBus("Bus8").getLoadList().get(0).setStatus(false);
 		algo.loadflow();
 	  	
	  	assertTrue(net.isLfConverged());
 		System.out.println(AclfOutFunc.loadFlowSummary(net));
 		
 		//test the total load of Bus8
 
 		assertTrue(NumericUtil.equals(net.getBus("Bus8").getLoadPQ(),new Complex(0.5, 0.05),1.0E-4));
	}

}
