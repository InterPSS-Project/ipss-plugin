 /*
  * @(#)SVCTest.java   
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

package org.interpss.plugin.beanModel;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.mapper.bean.aclf.AclfBean2NetMapper;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.VarCompensatorControlMode;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleCases;

public class SwitchedShuntTest extends CorePluginTestSetup {
	@Test
	public void fixedModeTest() throws InterpssException {
  		AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(aclfNet);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus = aclfNet.getBus("1");
		bus.setLoadQ(1.0);
		
		SwitchedShunt svc = CoreObjectFactory.createSwitchedShunt(bus, VarCompensatorControlMode.FIXED);
		svc.setBInit(0.2/0.86215/0.86215);
		
		// map back and forth through the bean model
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2NetMapper().map2Model(netBean)
				.getAclfNet();
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.547578)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.097536)<0.0001);
	}

	/*
	 * SVC continuous adj mode, no limit violation
	 */
	@Test
	public void contiModeTest() throws InterpssException {
  		AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(aclfNet);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus = aclfNet.getBus("1");
		bus.setLoadQ(0.8);
		
		SwitchedShunt svc = CoreObjectFactory.createSwitchedShunt(bus, VarCompensatorControlMode.CONTINUOUS);
		svc.setVSpecified(0.9);
		svc.setQLimit(new LimitType(1.0, 0.0));
		
		IpssLogger.ipssLogger.setLevel(Level.INFO);
		
		//System.out.println(aclfNet.net2String());
		// map back and forth through the bean model
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2NetMapper().map2Model(netBean)
				.getAclfNet();
		
		//System.out.println(net.net2String());
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		AclfBus svcBus = (AclfBus)net.getBus("1");
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(svcBus.getVoltageMag()-0.9)<0.001);
		assertTrue(Math.abs(svcBus.getSwitchedShunt().getQ()-0.1774)<0.0001);
	}

	/*
	 * SVC continuous adj mode, remote bus v adjustment, no limit violation
	 */
	@Test
	public void contiModeRemoteBusTest_Bean() throws InterpssException {
  		AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(aclfNet);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus1 = aclfNet.getBus("1");

		AclfBus bus6 = CoreObjectFactory.createAclfBus("6", aclfNet);
		bus6.setBaseVoltage(bus1.getBaseVoltage());
		
		AclfBranch branch = CoreObjectFactory.createAclfBranch();
		aclfNet.addBranch(branch, bus6.getId(), bus1.getId());
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.01));
		
		SwitchedShunt svc = CoreObjectFactory.createSwitchedShunt(bus6, VarCompensatorControlMode.CONTINUOUS);
		svc.setVSpecified(0.9);// TODO: Mike, it seems the Vspecified does not work since bus1.getVoltageMag() = 1.0 
		svc.setQLimit(new LimitType(1.0, 0.0));
		svc.setRemoteBus(bus1);		
		
		System.out.println(aclfNet.net2String());		
		
		// map back and forth through the bean model
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2NetMapper().map2Model(netBean)
				.getAclfNet();
		
		System.out.println(net.net2String());		
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
  		System.out.println(bus1.getVoltageMag());
		assertTrue(Math.abs(bus1.getVoltageMag()-0.9)<0.001);
		assertTrue(Math.abs(bus6.getSwitchedShunt().getQ()-0.18278)<0.0001);
	}
	
	@Test
	public void contiModeRemoteBusTest_NoBean() throws InterpssException {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus1 = net.getBus("1");

		AclfBus bus6 = CoreObjectFactory.createAclfBus("6", net);
		bus6.setBaseVoltage(bus1.getBaseVoltage());
		
		AclfBranch branch = CoreObjectFactory.createAclfBranch();
		net.addBranch(branch, bus6.getId(), bus1.getId());
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.01));
		
		SwitchedShunt svc = CoreObjectFactory.createSwitchedShunt(bus6, VarCompensatorControlMode.CONTINUOUS);
		svc.setVSpecified(0.9);
		svc.setQLimit(new LimitType(1.0, 0.0));
		svc.setRemoteBus(bus1);		
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
  		System.out.println(bus1.getVoltageMag());
		assertTrue(Math.abs(bus1.getVoltageMag()-0.9)<0.001);
		assertTrue(Math.abs(bus6.getSwitchedShunt().getQ()-0.18278)<0.0001);
	}
	
	@Test
	public void beanModelVerification() throws Exception {
		//AclfNetwork net = SampleCases.sample3BusPSXfrPControl();	
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus = net.getBus("1");
		bus.setLoadQ(0.8);
		
		SwitchedShunt svc = CoreObjectFactory.createSwitchedShunt(bus, VarCompensatorControlMode.CONTINUOUS);
		svc.setVSpecified(0.9);
		svc.setQLimit(new LimitType(1.0, 0.0));
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2NetMapper().map2Model(netBean)
				.getAclfNet();

		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2BeanMapper().map2Model(aclfNet);

		/*
		 * compare two AclfNetBean objects
		 * 
		 * netBean - mapped from the original AclfNet object net netBean1 -
		 * mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);		
		
	}
}

