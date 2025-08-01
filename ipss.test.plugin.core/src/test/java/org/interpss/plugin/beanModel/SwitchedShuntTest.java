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
import org.interpss.dep.datamodel.bean.aclf.AclfNetBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfBusResultBean;
import org.interpss.dep.datamodel.mapper.aclf.AclfBean2AclfNetMapper;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2ResultBeanMapper;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleTestingCases;

public class SwitchedShuntTest extends CorePluginTestSetup {
	@Test
	public void fixedModeTest() throws InterpssException {
  		AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(aclfNet);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus = aclfNet.getBus("1");
		bus.setLoadQ(1.0);
		
		SwitchedShunt svc = AclfAdjustObjectFactory.createSwitchedShunt(bus, 
				AclfAdjustControlMode.FIXED, AclfAdjustControlType.POINT_CONTROL).get();
		svc.setBInit(0.2/0.86215/0.86215);
		
		// map back and forth through the bean model
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
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
		SampleTestingCases.load_LF_5BusSystem(aclfNet);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus = aclfNet.getBus("1");
		bus.setLoadQ(0.8);
		
		SwitchedShunt svc = AclfAdjustObjectFactory.createSwitchedShunt(bus, 
				AclfAdjustControlMode.CONTINUOUS, AclfAdjustControlType.POINT_CONTROL).get();
		svc.setVSpecified(0.9);
		svc.setBLimit(new LimitType(1.0, 0.0));
		
		IpssLogger.ipssLogger.setLevel(Level.INFO);
		
		//System.out.println(aclfNet.net2String());
		// map back and forth through the bean model
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
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
	public void contiModeRemoteBusTest() throws InterpssException {
  		AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(aclfNet);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus1 = aclfNet.getBus("1");

		AclfBus bus6 = CoreObjectFactory.createAclfBus("6", aclfNet).get();
		bus6.setBaseVoltage(bus1.getBaseVoltage());
		
		AclfBranch branch = CoreObjectFactory.createAclfBranch();
		aclfNet.addBranch(branch, bus6.getId(), bus1.getId());
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.01));
		
		SwitchedShunt svc = AclfAdjustObjectFactory.createSwitchedShunt(bus6, 
				AclfAdjustControlMode.CONTINUOUS, AclfAdjustControlType.POINT_CONTROL).get();
		svc.setVSpecified(0.9);
		svc.setBLimit(new LimitType(1.0, 0.0));
		svc.setRemoteBus(bus1);		
		
		//System.out.println(aclfNet.net2String());		
		
		// map back and forth through the bean model
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
		//System.out.println(net.net2String());		
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
  		bus1 = net.getBus("1");
  		bus6 = net.getBus("6");
  		//System.out.println(bus1.getVoltageMag());
		assertTrue(Math.abs(bus1.getVoltageMag()-0.9)<0.001);
		assertTrue(Math.abs(bus6.getSwitchedShunt().getQ()-0.18278)<0.0001);
	}
	
	
	@Test
	public void beanModelVerification() throws Exception {
		//AclfNetwork net = SampleCases.sample3BusPSXfrPControl();	
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());
	
		/*
		   LF Results : 
		      voltage   : 0.86215 pu   11897.67835 v
		      load      : 1.6000 + j0.8000 pu   160000.0000 + j80000.0000 kva
		*/
		AclfBus bus = net.getBus("1");
		bus.setLoadQ(0.8);
		
		SwitchedShunt svc = AclfAdjustObjectFactory.createSwitchedShunt(bus, 
				AclfAdjustControlMode.CONTINUOUS, AclfAdjustControlType.POINT_CONTROL).get();
		svc.setVSpecified(0.9);
		svc.setBLimit(new LimitType(1.0, 0.0));
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();

		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		/*
		 * compare two AclfNetBean objects
		 * 
		 * netBean - mapped from the original AclfNet object net netBean1 -
		 * mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);		
		
	}
	
	@Test
	public void testCase1() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testdata/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();

		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
		LoadflowAlgorithm algo = CoreObjectFactory
				.createLoadflowAlgorithm(aclfNet);
		algo.loadflow();
		assertTrue(aclfNet.isLfConverged());

		String swingId = "Bus1";
		
		AclfSwingBusAdapter swing = aclfNet.getBus(swingId).toSwingBus();
		//System.out.println("AclfNet Model: "+swing.getGenResults(UnitType.PU) );		
		AclfNetBean netBean1 = new AclfNet2ResultBeanMapper().map2Model(aclfNet);		
		AclfBusResultBean bean = netBean1.getBus(swingId).extension;		
		assertTrue(swing.getGenResults(UnitType.PU).getReal() - bean.lfGenResult.re < 0.0001);
		assertTrue(swing.getGenResults(UnitType.PU).getImaginary() - bean.lfGenResult.im < 0.0001);
	}
	
}

