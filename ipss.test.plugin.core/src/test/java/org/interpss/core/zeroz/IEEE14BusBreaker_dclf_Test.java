 /*
  * @(#)SampleLoadflow.java   
  *
  * Copyright (C) 2006 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.zeroz;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.funcImpl.zeroz.ZeroZBranchProcesor;


public class IEEE14BusBreaker_dclf_Test extends CorePluginTestSetup {
	@Test 
	public void case1_regularMethod() throws  InterpssException, ReferenceBusException, IpssNumericException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-2.1900)<0.01);

		//algo.destroy();	
    }	
	
	@Test 
	public void case1_smallZ() throws  InterpssException, ReferenceBusException, IpssNumericException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	assertTrue(net.isZeroZBranchModel());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
 		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-2.1900)<0.01);

		//algo.destroy();	
    }	

	@Test 
	public void case1_smallZ_1() throws  InterpssException, ReferenceBusException, IpssNumericException {
		// test casa with a small-Z brach loop at Bus-14
		
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker_1.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	assertTrue(net.isZeroZBranchModel());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
 		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-2.1900)<0.01);

		//algo.destroy();	
    }
}
