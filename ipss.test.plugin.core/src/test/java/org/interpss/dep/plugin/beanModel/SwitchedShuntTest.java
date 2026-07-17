package org.interpss.dep.plugin.beanModel;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.SwitchedShunt;
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
		LoadflowAlgorithm expectedAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm();
		aclfNet.accept(expectedAlgo);
		assertTrue(aclfNet.isLfConverged());
		Complex expectedSwingGen = aclfNet.getBus("5").toSwingBus().getGenResults(UnitType.PU);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU));
		Complex swingGen = swing.getGenResults(UnitType.PU);
		assertTrue(Math.abs(swingGen.getReal()-expectedSwingGen.getReal())<0.0001, "swing P = " + swingGen.getReal());
		assertTrue(Math.abs(swingGen.getImaginary()-expectedSwingGen.getImaginary())<0.0001, "swing Q = " + swingGen.getImaginary());
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
		
		//System.out.println(aclfNet.net2String());
		// map back and forth through the bean model
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(aclfNet);
		LoadflowAlgorithm expectedAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm();
		aclfNet.accept(expectedAlgo);
		assertTrue(aclfNet.isLfConverged());
		AclfBus expectedSvcBus = aclfNet.getBus("1");
		double expectedSvcBusVoltage = expectedSvcBus.getVoltageMag();
		double expectedSvcQ = expectedSvcBus.getFirstSwitchedShunt(true).getQ();

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
		//System.out.println(net.net2String());
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		AclfBus svcBus = (AclfBus)net.getBus("1");
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		double svcBusVoltage = svcBus.getVoltageMag();
		double svcQ = svcBus.getFirstSwitchedShunt(true).getQ();
		assertTrue(Math.abs(svcBusVoltage-expectedSvcBusVoltage)<0.0001, "svc bus voltage = " + svcBusVoltage);
		assertTrue(Math.abs(svcQ-expectedSvcQ)<0.0001, "svc Q = " + svcQ);
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
		LoadflowAlgorithm expectedAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm();
		aclfNet.accept(expectedAlgo);
		assertTrue(aclfNet.isLfConverged());
		double expectedRemoteBusVoltage = aclfNet.getBus("1").getVoltageMag();
		double expectedRemoteSvcQ = aclfNet.getBus("6").getFirstSwitchedShunt(true).getQ();

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
		//System.out.println(net.net2String());		
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm();

	  	net.accept(algo);
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
		
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
  		bus1 = net.getBus("1");
  		bus6 = net.getBus("6");
  		//System.out.println(bus1.getVoltageMag());
		double remoteBusVoltage = bus1.getVoltageMag();
		double remoteSvcQ = bus6.getFirstSwitchedShunt(true).getQ();
		assertTrue(Math.abs(remoteBusVoltage-expectedRemoteBusVoltage)<0.0001, "remote bus voltage = " + remoteBusVoltage);
		assertTrue(Math.abs(remoteSvcQ-expectedRemoteSvcQ)<0.0001, "remote svc Q = " + remoteSvcQ);
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
						"testData/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();

		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory
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

