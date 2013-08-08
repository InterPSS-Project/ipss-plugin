 /*
  * @(#) ISONE_CompreResult.java   
  *
  * Copyright (C) 2008 www.interpss.org
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
  * @Date 02/15/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.adapter.pwd;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.common.ODMLogger;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.sparse.base.ISparseEquation.SolverType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.pssl.plugin.IpssAdapter;

public class SixBus_XfrControl_pwd extends CorePluginTestSetup {
	@Test
	public void aclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/pwd/SixBus_XfrControl.aux")
					.setFormat(IpssAdapter.FileFormat.PWD)
					//.load(true, "output/odm.xml")
					.load()
					.getAclfNet();
  		System.out.println(net.net2String());
  		
  		AclfBranch branch = net.getBranch("Bus1->Bus3(1)");
  		assertTrue(branch != null);
  		assertTrue(branch.getTapControl() != null);
  		assertTrue(NumericUtil.equals(branch.getTapControl().getControlSpec(), 1.0450, 0.0001));
  		
  		branch = net.getBranch("Bus5->Bus6(T9)");
  		assertTrue(branch != null);
  		assertTrue(branch.getPSXfrPControl() != null);
  		assertTrue(NumericUtil.equals(branch.getPSXfrPControl().getControlSpec(), -0.75, 0.0001));
  		
  		
	}
}

