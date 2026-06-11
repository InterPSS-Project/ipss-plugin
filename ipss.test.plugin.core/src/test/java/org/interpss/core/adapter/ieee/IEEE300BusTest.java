 /*
  * @(#)Test_IEEECommonFormat.java   
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

package org.interpss.core.adapter.ieee;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

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

public class IEEE300BusTest extends CorePluginTestSetup {
	@Test
	public void xtestCase5() throws Exception{
		AclfNetwork net = loadIeee300();
		
  		assertTrue((net.getBusList().size() == 300 && net.getBranchList().size() == 411));

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus7049");
 		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()).re);
		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()).im);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-4.57025)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.39049)<0.0001);
	}

	@Test
	public void testIEEE300ControlIsolation() throws Exception {
		assertFalse(runIeee300Loadflow(algo -> {
		}).isLfConverged());

		assertFalse(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getVoltAdjConfig().setAdjust(false);
		}).isLfConverged());

		assertFalse(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjust(false);
		}).isLfConverged());

		assertTrue(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		}).isLfConverged());

		assertTrue(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
		}).isLfConverged());
	}

	private AclfNetwork runIeee300Loadflow(Consumer<LoadflowAlgorithm> configureAlgo) throws Exception {
		AclfNetwork net = loadIeee300();
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		configureAlgo.accept(algo);
		algo.loadflow();
		return net;
	}

	private AclfNetwork loadIeee300() throws Exception {
		return CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee300.ieee")
				.getAclfNet();
	}
}
