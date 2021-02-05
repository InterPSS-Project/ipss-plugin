 /*
  * @(#)AclfSampleTest.java   
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
  * @Date 07/15/2007
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.ca;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.NetGenAdjustAlgorithm;
import com.interpss.core.common.ReferenceBusException;

public class Ieee14_GSF_Test extends CorePluginTestSetup {
	
	@Test
	public void gsfTest()  throws ReferenceBusException, InterpssException   {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();	
		
		NetGenAdjustAlgorithm algo = DclfAlgoObjectFactory.createGenAdjustAlgorithm(net);
		algo.addInjectBus(net.getBus("Bus2"), 1.0);
		algo.addWithdrawBus(net.getBus("Bus3"), 1.0);
		
		double f = algo.genTransferDistFactor(net.getBranch("Bus2->Bus3(1)"));
		//System.out.println("monitorBranch - 2->3");
		//System.out.println("GSF: " + f );	
		assertTrue(NumericUtil.equals(f, 0.55937609, 0.00001));

		f = algo.genTransferDistFactor(net.getBranch("Bus3->Bus4(1)"));
		//System.out.println("GSF: " + f );	
		assertTrue(NumericUtil.equals(f,-0.440623, 0.00001));

		algo.getInjectBusList().clear();
		algo.addInjectBus(net.getBus("Bus2"), 1.0);
		algo.getWithdrawBusList().clear();
		algo.addWithdrawBus(net.getBus("Bus14"), 0.5);
		algo.addWithdrawBus(net.getBus("Bus13"), 0.5);
		
		f = algo.genTransferDistFactor(net.getBranch("Bus9->Bus14(1)"));
		//System.out.println("GSF: " + f );	
		assertTrue(NumericUtil.equals(f, 0.424185, 0.00001));

		f = algo.genTransferDistFactor(net.getBranch("Bus6->Bus13(1)"));
		//System.out.println("GSF: " + f );	
		assertTrue(NumericUtil.equals(f, 0.447801, 0.00001));

		f = algo.genTransferDistFactor(net.getBranch("Bus12->Bus13(1)"));
		//System.out.println("GSF: " + f );
		assertTrue(NumericUtil.equals(f, 0.128015, 0.00001));
	
		algo.getInjectBusList().clear();
		algo.addInjectBus(net.getBus("Bus2"), 1.0);
		algo.getWithdrawBusList().clear();
		algo.addWithdrawBus(net.getBus("Bus14"), 0.9);
		algo.addWithdrawBus(net.getBus("Bus13"), 0.1);

		f =algo.genTransferDistFactor(net.getBranch("Bus9->Bus14(1)"));
		//System.out.println("GSF: " + f );	
		assertTrue(NumericUtil.equals(f, 0.569027, 0.00001));

		f =	algo.genTransferDistFactor(net.getBranch("Bus6->Bus13(1)"));
		//System.out.println("GSF: " + f);	
		assertTrue(NumericUtil.equals(f, 0.335159, 0.00001));

		f = algo.genTransferDistFactor(net.getBranch("Bus12->Bus13(1)"));
		//System.out.println("GSF: " + f );	
		assertTrue(NumericUtil.equals(f, 0.095813, 0.00001));
	}
}

