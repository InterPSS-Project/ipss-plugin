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
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.DclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.DclfObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.dclf.DclfAlgorithm;
import com.interpss.core.net.Bus;
import com.interpss.core.net.RefBusType;
import com.interpss.pssl.plugin.IpssAdapter;
import com.interpss.pssl.plugin.IpssAdapter.PsseVersion;

public class SixBus_DclfPsXfr extends CorePluginTestSetup {
	@Test
	public void aclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load(true, "output/odm.xml")
					.getAclfNet();
  		//System.out.println(net.net2String());

	  	net.accept(CoreObjectFactory.createLfAlgoVisitor());
	  	
  		assertTrue(net.isLfConverged());
  		
		System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfSwingBus swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.1032)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-0.5212)<0.0001);	   		
 	}
	
	@Test
	public void dclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
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
  		
		for (Bus b : net.getBusList()) {
			//System.out.println(b.getId() + " mismatch " + algo.getMismatch((AclfBus)b));
			if (!((AclfBus)b).isRefBus())
				assertTrue(Math.abs(algo.getMismatch((AclfBus)b)) < 0.00001);
		}
		
		algo.destroy();			
	}
	
	@Test
	public void dclfRef() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getAclfNet();

		DclfAlgorithm algo = DclfObjectFactory.createDclfAlgorithm(net);
		
		net.setRefBusId("Bus3");
		net.setRefBusType(RefBusType.USER_DEFINED);
		
		net.getBus("Bus1").setGenP(3.0723);
		
		algo.calculateDclf();

		System.out.println(DclfOutFunc.dclfResults(algo, false));
		//System.out.println(algo.getBusPower(net.getAclfBus("Bus1")));
  		assertTrue(Math.abs(algo.getBusPower(net.getBus("Bus1"))-3.0623)<0.0001);
  		assertTrue(Math.abs(Math.toDegrees(algo.getBusAngle("Bus1"))-4.38)<0.01);

		algo.destroy();			
	}

	//@Test
	public void aclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/SixBus_2WPsXfr_1.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getAclfNet();
  		//System.out.println(net.net2String());

	  	net.accept(CoreObjectFactory.createLfAlgoVisitor());
	  	
  		assertTrue(net.isLfConverged());
  		
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfSwingBus swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.1032)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-0.5212)<0.0001);	  		
 	}
	
	@Test
	public void dclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/SixBus_2WPsXfr_1.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getAclfNet();

		DclfAlgorithm algo = DclfObjectFactory.createDclfAlgorithm(net);
		algo.calculateDclf();
		
		//System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(net.getBus("Bus1"))-3.0723)<0.0001);
		
		algo.destroy();	
	}
}

