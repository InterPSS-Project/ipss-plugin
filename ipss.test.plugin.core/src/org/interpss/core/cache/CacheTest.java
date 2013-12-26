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

package org.interpss.core.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.TestUtilFunc;
import org.interpss.pssl.plugin.IpssAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.interpss.CoreObjectFactory;
import com.interpss.cache.UgidGenerator;
import com.interpss.cache.aclf.AclfNetCacheWrapper;
import com.interpss.cache.acsc.AcscNetCacheWrapper;
import com.interpss.cache.dstab.DStabNetCacheWrapper;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.IpssCacheException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.dstab.util.sample.SampleDStabCase;
import com.interpss.simu.util.sample.SampleCases;

/*
 * Please note: start the TestCacheServer before running this test case. 
 */

public class CacheTest extends CorePluginTestSetup {
	HazelcastInstance client;
	
	@Before
	public void init() {
		ClientConfig clientConfig = new ClientConfig();
		
		clientConfig.addAddress("127.0.0.1");
		client = HazelcastClient.newHazelcastClient(clientConfig);
		UgidGenerator.IdGenerator = client.getIdGenerator("GuidGenerator");		
	}
	
	///////////////////////////////////////////////////////////////////
	////          Aclf Test                              //////////////
	///////////////////////////////////////////////////////////////////	
	   
	@Test
	public void Bus5SampleTest() throws IpssCacheException {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);
		long key = cache.put(net);
		 
		AclfNetwork net1 = cache.get(key);
		//System.out.println(net1.net2String());

		/*
		 * run basecase Aclf
		 */
	  	net.accept(CoreObjectFactory.createLfAlgoVisitor());
  		assertTrue(net.isLfConverged());
  		
  		assertEquals(true, Math.abs(net.areaOutputPower(1, UnitType.PU)-1.28164)<0.0001);

  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);
		
		/*
		 * run cached case Aclf
		 */
	  	net1.accept(CoreObjectFactory.createLfAlgoVisitor());
  		assertTrue(net1.isLfConverged());
  		
  		assertEquals(true, Math.abs(net1.areaOutputPower(1, UnitType.PU)-1.28164)<0.0001);

  		swingBus = (AclfBus)net1.getBus("5");
		swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);
	}

	@Test
	public void Bus5PVLimitTest() throws IpssCacheException, InterpssException {
  		AclfNetwork netbase = SampleCases.create5BusAclfPVBusLimit();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(netbase);
		 
		AclfNetwork net = cache.get(key);
		//System.out.println(net1.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
//  		System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		//	gen       : 2.2 + 2.81i pu   220,305.69 + 281,286.09i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.20306)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.81286)<0.0001);		
	}
	
	@Test
	public void Bus5PQLimitTest() throws IpssCacheException, InterpssException {
  		AclfNetwork netbase = SampleCases.create5BusAclfPQBusLimit();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(netbase);
		 
		AclfNetwork net = cache.get(key);
		//System.out.println(net1.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		//	gen       : 2.31 + 2.21i pu   230,828.51 + 220,525.26i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.308285)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.20525)<0.0001);		
	}
	
	@Test
	public void Bus5FuncLoadTest() throws IpssCacheException, InterpssException {
  		AclfNetwork netbase = SampleCases.create5BusAclfFuncLoad();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(netbase);
		 
		AclfNetwork net = cache.get(key);
		//System.out.println(net1.net2String());
		
  		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
		algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		//       gen       : 2.42 + 2.22i pu   242,384.84 + 222,269.55i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.42385)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.22270)<0.0001);		
	}

	@Test
	public void Bus5ReQBusVoltTest() throws IpssCacheException, InterpssException {
  		AclfNetwork netbase = SampleCases.create5BusAclfReQBusVolt();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(netbase);
		 
		AclfNetwork net = cache.get(key);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.setMaxIterations(50);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		//	gen       : 2.27 + 2.41i pu   226,904.73 + 240,532.99i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.26904)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.40532)<0.0001);
	}

	@Test
	public void Bus5ReQBranchQTest() throws IpssCacheException, InterpssException {
  		AclfNetwork netbase = SampleCases.create5BusAclfReQBranchQ();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(netbase);
		 
		AclfNetwork net = cache.get(key);

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.setMaxIterations(50);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		//	 gen       : 2.27 + 2.4i pu   226,956.27 + 240,255.07i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.26956)<0.001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.402255)<0.001);
	}

	@Test
	public void TapControlTest() throws IpssCacheException, InterpssException {
		AclfNetwork netbase = SampleCases.sample2BusXfrTapControl();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(netbase);
		 
		AclfNetwork net = cache.get(key);
		//System.out.println(net1.net2String());	
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)net.getBus("0001");
		AclfSwingBus swing = swingBus.toSwingBus();
		//      gen       : 1.12 + 1.03i pu   111,529.19 + 103,059.25i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-1.1153)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-1.0306)<0.0001);
		
	}

	@Test
	public void PsXfrPControlTest() throws IpssCacheException, InterpssException {
		AclfNetwork netbase = SampleCases.sample3BusPSXfrPControl();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(netbase);
		 
		AclfNetwork net = cache.get(key);
		//System.out.println(net1.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)net.getBus("0001");
		AclfSwingBus swing = swingBus.toSwingBus();
//		//     gen       : 0.4 + 0.51i pu   39,997.07 + 50,711.88i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-0.39997)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.50711)<0.0001);		
	}
	
	@Test
	public void IEEE14BusTest() throws IpssCacheException, InterpssException {
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(net);
		 
		AclfNetwork net1 = cache.get(key);
		//System.out.println(net1.net2String());
		
	  	net1.accept(CoreObjectFactory.createLfAlgoVisitor());
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net1.isLfConverged());		
  		AclfSwingBus swing = net1.getBus("Bus1").toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32393)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16549)<0.0001);	
  	}
	
	///////////////////////////////////////////////////////////////////
	////          Acsc Test                              //////////////
	///////////////////////////////////////////////////////////////////

	@Test
	public void Acsc5Bus() throws IpssCacheException, InterpssException {
  		AcscNetwork acscNet = CoreObjectFactory.createAcscNetwork();
		SampleCases.load_SC_5BusSystem(acscNet);
		//System.out.println(acscNet.net2String());
		
		AcscNetCacheWrapper cache = new AcscNetCacheWrapper(client);

		long key = cache.put(acscNet);
		 
		AcscNetwork net = cache.get(key);
		//System.out.println(net.net2String());		
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(net);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
  		//System.out.println(fault.toString(faultBus.getBaseVoltage(), faultNet.getBaseKva()));
		/*
		 fault amps(1): (  0.0000 + j 32.57143) pu
		 fault amps(2): (  0.0000 + j  0.0000) pu
		 fault amps(0): (  0.0000 + j  0.0000) pu
		 */
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, 0.0, 32.57142857157701, 0.0, 0.0) );
		
		//System.out.println(AcscOut.faultResult2String(faultNet));
	}	

	///////////////////////////////////////////////////////////////////
	////          DStab Test                             //////////////
	///////////////////////////////////////////////////////////////////

	@Test
	public void EConstMachineTest() throws IpssCacheException, InterpssException {
		DStabilityNetwork basenet = SampleDStabCase.createDStabTestNet();
		EConstMachine mach = SampleDStabCase.createEConstMachine(basenet);
	   // System.out.println(basenet.net2String());
		
	    DStabNetCacheWrapper cache = new DStabNetCacheWrapper(client);

		long key = cache.put(basenet);
		 
		DStabilityNetwork net = cache.get(key);
	//	System.out.println(net.net2String());
		
		// calculate mach state init values
		DStabBus bus = net.getDStabBus("Gen");
		mach.initStates(bus);

		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);

		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.51456) < 0.00001);
		assertTrue(Math.abs(mach.getSpeed()-1.0002) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}

	@Test
	public void Eq1MachineCaseTest() throws IpssCacheException, InterpssException {
		DStabilityNetwork basenet = SampleDStabCase.createDStabTestNet();
		Eq1Machine mach = SampleDStabCase.createEq1Machine(basenet);

	    DStabNetCacheWrapper cache = new DStabNetCacheWrapper(client);

		long key = cache.put(basenet);
		 
		DStabilityNetwork net = cache.get(key);
	//	System.out.println(net.net2String());
		
		// calculate mach state init values
		DStabBus bus = net.getDStabBus("Gen");
		mach.initStates(bus);

		assertTrue(Math.abs(mach.getYgen().getReal()-0.01208) < 0.00001);
		assertTrue(Math.abs(mach.getYgen().getImaginary()+2.63678) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getReal()-0.81207) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getImaginary()+3.23676) < 0.00001);			

		// the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation. There
		// should be no change
		assertTrue(Math.abs(mach.getSpeed()-1.0) < 0.00001);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(mach.getSpeed()-1.0) < 0.00001);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		
		// again, the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.60114) < 0.00001);
		assertTrue(Math.abs(mach.getSpeed()-1.0002) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}

	@Test
	public void Eq1Ed1MachineCaseTest() throws IpssCacheException, InterpssException {
		DStabilityNetwork basenet = SampleDStabCase.createDStabTestNet();
		Eq1Ed1Machine mach = SampleDStabCase.createEq1Ed1Machine(basenet);

	    DStabNetCacheWrapper cache = new DStabNetCacheWrapper(client);

		long key = cache.put(basenet);
		 
		DStabilityNetwork net = cache.get(key);
	//	System.out.println(net.net2String());
		
		// calculate mach state init values
		DStabBus bus = net.getDStabBus("Gen");
		mach.initStates(bus);
		assertTrue(Math.abs(mach.getYgen().getReal()-0.0567) < 0.00001);
		assertTrue(Math.abs(mach.getYgen().getImaginary()+4.34709) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getReal()-0.85669) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getImaginary()+4.94707) < 0.00001);	
		
		// the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation. There
		// should be no change
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.60114) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}
	
	@Test
	public void SalientPoleMachineTest() throws IpssCacheException, InterpssException {
		DStabilityNetwork basenet = SampleDStabCase.createDStabTestNet();
		SalientPoleMachine mach = SampleDStabCase.createSalientPoleMachine(basenet);

	    DStabNetCacheWrapper cache = new DStabNetCacheWrapper(client);

		long key = cache.put(basenet);
		 
		DStabilityNetwork net = cache.get(key);
	//	System.out.println(net.net2String());
		
		// calculate mach state init values
		DStabBus bus = net.getDStabBus("Gen");
		mach.initStates(bus);
		//System.out.println("Ygen: " + mach.getYgen());
		//System.out.println("Igen: " + mach.getIgen());
		assertTrue(Math.abs(mach.getYgen().getReal()-0.16658) < 0.00001);
		assertTrue(Math.abs(mach.getYgen().getImaginary()+7.49625) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getReal()-0.96657) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getImaginary()+8.09623) < 0.00001);		

		// the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(mach.getAngle()-0.48142) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40106) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.9959) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation. There
		// should be no change
		assertTrue(Math.abs(mach.getAngle()-0.48142) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40106) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.9959) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(mach.getAngle()-0.48142) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40106) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.9959) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(mach.getAngle()-0.481731) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40101228537298095) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.9959) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}
	
	@Test
	public void RoundRotorMachineTest() throws IpssCacheException, InterpssException {
		// create a machine in a two-bus network. The loadflow already converged
		DStabilityNetwork basenet = SampleDStabCase.createDStabTestNet();
		RoundRotorMachine mach = SampleDStabCase.createRoundRotorMachine(basenet);

	    DStabNetCacheWrapper cache = new DStabNetCacheWrapper(client);

		long key = cache.put(basenet);
		 
		DStabilityNetwork net = cache.get(key);
	//	System.out.println(net.net2String());
		
		// calculate mach state init values
		DStabBus bus = net.getDStabBus("Gen");
		mach.initStates(bus);
		assertTrue(Math.abs(mach.getYgen().getReal()-0.16658) < 0.00001);
		assertTrue(Math.abs(mach.getYgen().getImaginary()+7.49625) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getReal()-0.96657) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getImaginary()+8.09623) < 0.00001);

		// the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.99590) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40106) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation. There
		// should be no change
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.99590) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40106) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.99590) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40106) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.60114) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEq11()-0.9959) < 0.00001);
		assertTrue(Math.abs(mach.getEd11()+0.40106) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}

	@Test
	public void ControllerDataTest() throws IpssCacheException, InterpssException {
		DynamicSimuAlgorithm dstabAlgo = IpssAdapter.importNet("testData/odm/dstab/Tran_2Bus_062011.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();	   
		DStabilityNetwork basenet = dstabAlgo.getNetwork();
		//System.out.println(basenet.net2String());
		
	    DStabNetCacheWrapper cache = new DStabNetCacheWrapper(client);

		long key = cache.put(basenet);
		 
		DStabilityNetwork net = cache.get(key);
		//System.out.println(net.net2String());

		Machine machb = basenet.getBus("Bus-1").getMachine();
		Machine mach = net.getBus("Bus-1").getMachine();
		assertTrue(machb.getExciter().getDataClass().getName().equals(mach.getExciter().getDataClass().getName()));
		assertTrue(machb.getGovernor().getDataClass().getName().equals(mach.getGovernor().getDataClass().getName()));
		assertTrue(machb.getStabilizer().getDataClass().getName().equals(mach.getStabilizer().getDataClass().getName()));
	}
	
	@After
	public void cleanup() {
		client.shutdown();		
	}
}

