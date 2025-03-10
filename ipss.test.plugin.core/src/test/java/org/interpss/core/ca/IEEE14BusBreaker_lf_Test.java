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

package org.interpss.core.ca;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.interpss.CorePluginFunction;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.ZeroZBranchProcesor;


public class IEEE14BusBreaker_lf_Test extends CorePluginTestSetup {
	//@Test there is a loop in the system. 
	public void case1_regularMethod() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	System.out.println(CorePluginFunction.aclfResultBusStyle.apply(net));

	  	//System.out.println("Active buses: " + net.getNoActiveBus() + ", branches: " + net.getNoActiveBranch());
	  	assertTrue(net.getNoActiveBus() == 23);
	  	assertTrue(net.getNoActiveBranch() == 30);
	  	
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3240)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1655)<0.0001);
    }	

	@Test 
	public void case2_zeroZBranchProcessingBranchType()  throws InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();

		IpssLogger.getLogger().setLevel(Level.INFO);
		
	  	// process zero impedance branches in the network
	  	net.setZeroZBranchThreshold(0.00001);
	  	net.accept(new ZeroZBranchProcesor(true));
	  	//net.accept(new ZeroZBranchProcesor(true));
	  	assertTrue(net.isZeroZBranchProcessed());
	  	//System.out.println(net.net2String());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	//System.out.println(AclfOutFunc.loadFlowSummary(net));

	  	System.out.println("Active buses: " + net.getNoActiveBus() + ", branches: " + net.getNoActiveBranch());
	  	assertTrue(net.getNoActiveBus() == 14);
	  	assertTrue(net.getNoActiveBranch() == 20);

  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3239)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1655)<0.0001);
	}	

	@Test 
	public void case2_zeroZBranchProcessingZValue()  throws InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();

		IpssLogger.getLogger().setLevel(Level.INFO);
		
	  	// process zero impedance branches in the network
		net.setZeroZBranchThreshold(0.00001);
	  	net.accept(new ZeroZBranchProcesor(true));
	  	assertTrue(net.isZeroZBranchProcessed());
	  	//System.out.println(net.net2String());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	//System.out.println(AclfOutFunc.loadFlowSummary(net));

	  	//System.out.println("Active buses: " + net.getNoActiveBus() + ", branches: " + net.getNoActiveBranch());
	  	assertTrue(net.getNoActiveBus() == 14);
	  	assertTrue(net.getNoActiveBranch() == 20);

  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3239)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1655)<0.0001);
	}	

	@Test 
	public void case2_zeroZBranch_ProtectedBranch()  throws InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();

		IpssLogger.getLogger().setLevel(Level.INFO);
		
	  	// process zero impedance branches in the network
		net.setZeroZBranchThreshold(0.00001);
	  	ZeroZBranchProcesor proc = new ZeroZBranchProcesor(true);
	  	
	  	// add one protected branch
	  	List<String> proList = new ArrayList<String>();
	  	proList.add("Bus18->Bus14(1)");
	  	proc.setProtectedBranchIdList(proList);
	  	
	  	net.accept(proc);
	  	assertTrue(net.isZeroZBranchProcessed());
	  	//System.out.println(net.net2String());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	//System.out.println(AclfOutFunc.loadFlowSummary(net));

	  	System.out.println("Active buses: " + net.getNoActiveBus() + ", branches: " + net.getNoActiveBranch());
	  	assertTrue(net.getNoActiveBus() == 15);
	  	assertTrue(net.getNoActiveBranch() == 21);

  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3239)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1655)<0.0001);
	}
	
	@Test 
	public void case2_zeroZBranchProcessingZValue_1()  throws InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker_1.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();

		IpssLogger.getLogger().setLevel(Level.INFO);
		
	  	// process zero impedance branches in the network
		net.setZeroZBranchThreshold(0.00001);
	  	net.accept(new ZeroZBranchProcesor(true));
	  	assertTrue(net.isZeroZBranchProcessed());
	  	//System.out.println(net.net2String());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	//System.out.println(AclfOutFunc.loadFlowSummary(net));

	  	//System.out.println("Active buses: " + net.getNoActiveBus() + ", branches: " + net.getNoActiveBranch());
	  	assertTrue(net.getNoActiveBus() == 14);
	  	assertTrue(net.getNoActiveBranch() == 20);

  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
	  	System.out.println("----> " + swing.getGenResults(UnitType.PU).getReal() + ", " + swing.getGenResults(UnitType.PU).getImaginary());
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3239)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1655)<0.0001);
	}	
	
}
