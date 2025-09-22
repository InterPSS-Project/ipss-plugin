package org.interpss.core.aclf.svc;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.core.aclf.ShuntCompensatorType;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adpter.Aclf3WXformerAdapter;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE14_PVLimit_SVCTest extends CorePluginTestSetup {
	@Test 
	public void noLimitViolationTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		AclfBus bus8 = aclfNet.getBus("Bus8");
		bus8.getContributeGenList().get(0).setQGenLimit(new LimitType(0.1, -0.06));
		
		StaticVarCompensator svc = AclfAdjustObjectFactory
				.createStaticVarCompensator(bus8, AclfAdjustControlMode.CONTINUOUS, AclfAdjustControlType.POINT_CONTROL).get();

		svc.setBInit(0.0);
		svc.setBLimit(new LimitType(0.1, 0.0));
		svc.setDesiredControlRange(new LimitType(1.1, 0.9));
		
		ShuntCompensator bank1 = CoreObjectFactory.createShuntCompensator(svc, ShuntCompensatorType.CAPACITOR);
		bank1.setId("Bank1");
		bank1.setSteps(1);
		bank1.setUnitQMvar(0.5);
		bank1.setB(0.05);
		
		ShuntCompensator bank2 = CoreObjectFactory.createShuntCompensator(svc, ShuntCompensatorType.CAPACITOR);
		bank2.setId("Bank2");
		bank2.setSteps(1);
		bank2.setUnitQMvar(0.5);
		bank2.setB(0.1);

		// we set the svc to be off control status, it will be turned on after the PVBusLimit
		// hits the limit.
		svc.setStatus(false);
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, aclfNet.getBaseKva());
	  	algo.loadflow();
	  	
  		//System.out.println(aclfNet.net2String());
	  	
		//System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
        //printout the power flow results
 		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
	  	
	  	assertTrue(bus8.isGenPV());
	  	assertTrue(Math.abs(bus8.getVoltageMag() - 1.09) < 0.001);
	  	// the PVBusLimit control hits the limit.
	  	assertTrue(bus8.getPVBusLimit().isControlStatus());
	  	assertTrue(!bus8.getPVBusLimit().isAdjustStatus());
	  	
	  	// the SVC is active and controls the bus voltage
	  	assertTrue(svc.isControlStatus());
	  	assertTrue(svc.isAdjustStatus());	
	  	// Set SVC bActual to 0.06416 pu.
	  	assertTrue(Math.abs(svc.getBActual() - 0.06416) < 0.0001);
	}
	
	@Test 
	public void limitViolationTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		AclfBus bus8 = aclfNet.getBus("Bus8");
		// set the PV bus Q limit to a lower value to cause limit violation
		bus8.getContributeGenList().get(0).setQGenLimit(new LimitType(0.05, -0.06));
		
		StaticVarCompensator svc = AclfAdjustObjectFactory
				.createStaticVarCompensator(bus8, AclfAdjustControlMode.CONTINUOUS, AclfAdjustControlType.POINT_CONTROL).get();

		svc.setBInit(0.0);
		svc.setBLimit(new LimitType(0.1, 0.0));
		svc.setDesiredControlRange(new LimitType(1.1, 0.9));
		
		ShuntCompensator bank1 = CoreObjectFactory.createShuntCompensator(svc, ShuntCompensatorType.CAPACITOR);
		bank1.setId("Bank1");
		bank1.setSteps(1);
		bank1.setUnitQMvar(0.5);
		bank1.setB(0.05);
		
		ShuntCompensator bank2 = CoreObjectFactory.createShuntCompensator(svc, ShuntCompensatorType.CAPACITOR);
		bank2.setId("Bank2");
		bank2.setSteps(1);
		bank2.setUnitQMvar(0.5);
		bank2.setB(0.1);

		// we set the svc to be off control status, it will be turned on after the PVBusLimit
		// hits the limit.
		svc.setStatus(false);
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, aclfNet.getBaseKva());
	  	algo.loadflow();
	  	
  		//System.out.println(aclfNet.net2String());
	  	
		//System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
        //printout the power flow results
 		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
	  	
	  	assertTrue(bus8.isGenPQ());
	  	assertTrue(""+bus8.getVoltageMag(), Math.abs(bus8.getVoltageMag() - 1.0877) < 0.001);
	  	// the PVBusLimit control hits the limit.
	  	assertTrue(bus8.getPVBusLimit().isControlStatus());
	  	assertTrue(!bus8.getPVBusLimit().isAdjustStatus());
	  	
	  	// the SVC is inactive due to limit violation
	  	assertTrue(svc.isControlStatus());
	  	assertTrue(!svc.isAdjustStatus());	
	  	// Set SVC bActual to 0.1 pu, the SVC upper limit.
	  	assertTrue(Math.abs(svc.getBActual() - 0.1) < 0.0001);
	 }
}

