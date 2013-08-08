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

package org.interpss.core.adapter.psse;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.ODMLogger;
import org.interpss.CorePluginFunction;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.display.DclfOutFunc;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.DclfObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.dclf.DclfAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.pssl.plugin.IpssAdapter;
import com.interpss.pssl.plugin.IpssAdapter.PsseVersion;

/*
 * This test case compares InterPSS and PSS/E xfr branch model
 */
public class Mod_SixBus_DclfPsXfr extends CorePluginTestSetup {
	@Test
	public void aclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.xfrBranchModel(ODMAclfNetMapper.XfrBranchModel.InterPSS)
					.load(true, "output/odm.xml")
					.getAclfNet();
  		//System.out.println(net.net2String());

	  	net.accept(CoreObjectFactory.createLfAlgoVisitor());
	  	
  		assertTrue(net.isLfConverged());
  		
		System.out.println(CorePluginFunction.AclfResultBusStyle.f(net));
  		AclfSwingBus swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.2954)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-0.9567)<0.0001);	   		
 	}
	
	@Test
	public void aclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.xfrBranchModel(ODMAclfNetMapper.XfrBranchModel.PSSE)
					.load(true, "output/odm.xml")
					.getAclfNet();
  		//System.out.println(net.net2String());

	  	net.accept(CoreObjectFactory.createLfAlgoVisitor());
	  	
  		assertTrue(net.isLfConverged());
  		
		System.out.println(CorePluginFunction.AclfResultBusStyle.f(net));
  		AclfSwingBus swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.2955)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-0.9571)<0.0001);	   		
 	}
	
	@Test
	public void dclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.format(IpssAdapter.FileFormat.PSSE)
					.psseVersion(PsseVersion.PSSE_30)
					.xfrBranchModel(ODMAclfNetMapper.XfrBranchModel.InterPSS)
					.load()
					.getAclfNet();
  		//System.out.println(net.net2String());
		/*
		net.accept(CoreObjectFactory.createBusNoArrangeVisitor());
		for (Bus b : net.getBusList())
			System.out.println(b.getId() + ": " + b.getSortNumber());
 		System.out.println(net.formB1Matrix());
		*/
		
		// because of InterPSS xfrBranchModel, we need to convert to the PSS/E model
		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch)b;
			if (branch.isXfr() || branch.isPSXfr()) {
				if (branch.getToTurnRatio() != 1.0) {
					branch.setZ(branch.getZ().multiply(branch.getToTurnRatio()*branch.getToTurnRatio()));
					branch.setFromTurnRatio(branch.getFromTurnRatio()/branch.getToTurnRatio());
					branch.setToTurnRatio(1.0);
					if (branch.isPSXfr()) {
						branch.setFromPSXfrAngle(branch.getFromPSXfrAngle() - branch.getToPSXfrAngle());
						branch.setToPSXfrAngle(0.0);
					}
				}
			}
		} 
		
		DclfAlgorithm algo = DclfObjectFactory.createDclfAlgorithm(net);
		algo.calculateDclf();

		System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(net.getBus("Bus1"))-3.0723)<0.0001);

		algo.destroy();			
	}

	@Test
	public void dclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.format(IpssAdapter.FileFormat.PSSE)
					.psseVersion(PsseVersion.PSSE_30)
					.xfrBranchModel(ODMAclfNetMapper.XfrBranchModel.PSSE)
					.load()
					.getAclfNet();
  		//System.out.println(net.net2String());
		/*
		net.accept(CoreObjectFactory.createBusNoArrangeVisitor());
		for (Bus b : net.getBusList())
			System.out.println(b.getId() + ": " + b.getSortNumber());
 		System.out.println(net.formB1Matrix());
		*/
		
		DclfAlgorithm algo = DclfObjectFactory.createDclfAlgorithm(net);
		algo.calculateDclf();

		System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(net.getBus("Bus1"))-3.0723)<0.0001);

		algo.destroy();			
	}
}

