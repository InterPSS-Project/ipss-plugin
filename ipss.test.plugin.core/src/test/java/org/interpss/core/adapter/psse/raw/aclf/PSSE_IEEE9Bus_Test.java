 /*
  * @(#)IEEE9Bus_Test.java   
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
  * @Author Stephen Hou
  * @Version 1.0
  * @Date 02/01/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.adapter.psse.raw.aclf;
 
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dep.datamodel.bean.aclf.AclfNetBean;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.BusBranchControlType;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.adpter.AclfGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSE_IEEE9Bus_Test extends CorePluginTestSetup { 
	//@Test
	public void load() throws Exception {
		// load the test data V33
		AclfNetwork net33 = IpssAdapter.importAclfNet("testdata/adpter/psse/v31/ieee9_v31.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_31) 
				.load()
				.getImportedObj();
	}

	//@Test
	public void compare() throws Exception {
		// load the test data V30
		AclfNetwork net30 = IpssAdapter.importAclfNet("testdata/adpter/psse/v30/IEEE9Bus/ieee9.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();
		AclfNetBean netBean30 = new AclfNet2AclfBeanMapper().map2Model(net30);
		
		// load the test data V29
		AclfNetwork net29 = IpssAdapter.importAclfNet("testdata/adpter/psse/v29/ieee9_v29.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_29)
				.load()
				.getImportedObj();
		AclfNetBean netBean29 = new AclfNet2AclfBeanMapper().map2Model(net29);
		
		// compare the data model with V30
		netBean30.compareTo(netBean29);

		// load the test data V31
		AclfNetwork net31 = IpssAdapter.importAclfNet("testdata/adpter/psse/v31/ieee9_v31.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_31)
				.load()
				.getImportedObj();
		AclfNetBean netBean31 = new AclfNet2AclfBeanMapper().map2Model(net31);
		
		// compare the data model  with V30
		netBean30.compareTo(netBean31);
		
		// load the test data V32
		AclfNetwork net32 = IpssAdapter.importAclfNet("testdata/psse/v32/ieee9_v32.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_32)
				.load()
				.getImportedObj();
		AclfNetBean netBean32 = new AclfNet2AclfBeanMapper().map2Model(net32);
		
		// compare the data model  with V30
		netBean30.compareTo(netBean32);
		
		// load the test data V33
		AclfNetwork net33 = IpssAdapter.importAclfNet("testdata/adpter/psse/V33/ieee9_v33.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
		AclfNetBean netBean33 = new AclfNet2AclfBeanMapper().map2Model(net33);
		
		// compare the data model with V30
		netBean30.compareTo(netBean33);		
	}
	
	@Test
	public void testV30() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/IEEE9Bus/ieee9.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();

		testVAclf(net);
	}

	//TODO: V29 is not working, and we don't want to support it any more
	// @Test
	// public void testV29() throws Exception {
	// 	AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v29/ieee9_v29.raw")
	// 			.setFormat(PSSE)
	// 			.setPsseVersion(PsseVersion.PSSE_29)
	// 			.load()
	// 			.getImportedObj();

	// 	testVAclf(net);
	// }

	@Test
	public void testV31() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v31/ieee9_v31.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_31)
				.load()
				.getImportedObj();
		System.out.println(net.net2String());
		testVAclf(net);
	}
	
	@Test
	public void testV32() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v32/ieee9_v32.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_32)
				.load()
				.getImportedObj();

		testVAclf(net);
	}
	
	@Test
	public void testV33() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v33/ieee9_v33.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();

		testVAclf(net);
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
	}
	
	@Test
	public void testRAWXJson() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/json/ieee9.rawx")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_JSON)
				.load()
				.getImportedObj();

		testVAclf(net);
		

	}

	@Test
	public void testAclfSpeical2WXfrData() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v35/ieee9_qa_v35.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_35)
				.load()
				.getImportedObj();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.loadflow();

		/*
		 *    BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       0.7198    0.3803    0.0000    0.0000   BUS-1      
  Bus2         PV                   1.02500        5.38       1.6300   -0.1312    0.0000    0.0000   BUS-2      
  Bus3         PV                   1.02500        6.41       0.8500   -0.2607    0.0000    0.0000   BUS-3      
  Bus4                              1.05844       -0.58       0.0000    0.0000    0.0000    0.0000   BUS-4      
  Bus5                ConstP        1.02435       -2.20       0.0000    0.0000    1.2500    0.5000   BUS-5      
  Bus6                ConstP        1.03802       -1.91       0.0000    0.0000    0.9000    0.3000   BUS-6      
  Bus7                              1.04235        5.34       0.0000    0.0000    0.0000    0.0000   BUS-7      
  Bus8                ConstP        1.02958        2.47       0.0000    0.0000    1.0000    0.3500   BUS-8      
  Bus9                              1.04104        3.73       0.0000    0.0000    0.0000    0.0000   BUS-9    
		 */

		AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.7198)<0.0001);
  		assertTrue(""+p.getImaginary(), Math.abs(p.getImaginary()-0.3880)<0.0001);

		AclfBus bus4 = net.getBus("Bus4");
		double voltageMag = bus4.getVoltageMag();
		assertTrue(""+voltageMag, Math.abs(voltageMag - 1.05832) < 0.0001);


		AclfBus bus7 = net.getBus("Bus7");
		double voltageMagBus7 = bus7.getVoltageMag();
		assertTrue(Math.abs(voltageMagBus7 - 1.04235) < 0.0001);
		

	}

	@Test
	public void testAclfSpeical3WXfrData() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v35/ieee9_qa_3wxfr_v35.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_35)
				.load()
				.getImportedObj();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.getNrMethodConfig().setNonDivergent(true);
		algo.loadflow();
		System.out.println(AclfOutFunc.loadFlowSummary(net));

		/*
				BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
			----------------------------------------------------------------------------------------------------------------
			Bus1         Swing                1.04000        0.00       0.7226    0.3570    0.0000    0.0000   BUS-1      
			Bus2         PV                   1.02500        5.34       1.6300   -0.1688    0.0000    0.0000   BUS-2      
			Bus3         PV                   1.02500        9.17       0.8500   -0.1667    0.0000    0.0000   BUS-3      
			Bus4                              1.05878       -0.58       0.0000    0.0000    0.0000    0.0000   BUS-4      
			Bus5                ConstP        1.02463       -2.21       0.0000    0.0000    1.2500    0.5000   BUS-5      
			Bus6                ConstP        1.04045       -1.93       0.0000    0.0000    0.9000    0.3000   BUS-6      
			Bus7                              1.04237        5.30       0.0000    0.0000    0.0000    0.0000   BUS-7      
			Bus8                ConstP        1.03210        2.41       0.0000    0.0000    1.0000    0.3500   BUS-8      
			Bus9                              1.04690        3.62       0.0000    0.0000    0.0000    0.0000   BUS-9      
			Bus10                             1.03514        6.28       0.0000    0.0000    0.0000    0.0000   BUS-10     
			3WNDTR_10_9_3_1                      1.03516        6.28       0.0000    0.0000    0.0000    0.0000   3W Xfr Star Bus  
		 */

		AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.7226)<0.0001);
  		assertTrue(""+p.getImaginary(), Math.abs(p.getImaginary()-0.3650)<0.0001);

		AclfBus bus4 = net.getBus("Bus4");
		double voltageMag = bus4.getVoltageMag();
		assertTrue(""+voltageMag, Math.abs(voltageMag - 1.05866) < 0.0001);


		AclfBus bus7 = net.getBus("Bus7");
		double voltageMagBus7 = bus7.getVoltageMag();
		assertTrue(Math.abs(voltageMagBus7 - 1.04237) < 0.0001);
		

		AclfBus bus10 = net.getBus("Bus10");
		double voltageMagBus10 = bus10.getVoltageMag();
		assertTrue(Math.abs(voltageMagBus10 - 1.03514) < 0.0001);
	}

	@Test
	public void testAclf3WXfrZCorrData() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v35/ieee9_qa_3wxfr_zcorr_v35.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_35)
				.load()
				.getImportedObj();

		// Check the 3W Xfr Z Corr Table 
		assertNotNull(net.getXfrZTable());
		assertTrue(net.getXfrZTable().size() == 1);


		// check the 3w Xfr z correction
		// 3WNDTR_90_91_92_1->Bus92(1)
		AclfBranch xfr_92_str = net.getBranch("3WNDTR_90_91_92_1->Bus92(1)");
		assertNotNull(xfr_92_str);
		assertTrue(xfr_92_str.getXfrZTableNumber() == 1);
		assertTrue(xfr_92_str.getZ().getReal() == 0.0);
		// check the scale factor
		assertTrue(xfr_92_str.getZMultiplyFactor().getReal() == 0.01);
		assertTrue(xfr_92_str.getZMultiplyFactor().getImaginary() == 0.0);
		assertEquals( 0.41194*0.01,xfr_92_str.getAdjustedZ().getImaginary(),1.0e-5); // 0.41194 is the Z Corr value in the table, 0.01 is the scale factor

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.getNrMethodConfig().setNonDivergent(true);
		algo.loadflow();
		//System.out.println(AclfOutFunc.loadFlowSummary(net));

		/*
				BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
			----------------------------------------------------------------------------------------------------------------
			Bus1         Swing                1.04000        0.00      -0.0136    0.5989    0.0000    0.0000   BUS-1      
			Bus2         PV                   1.02500        9.94       1.6300   -0.0515    0.0000    0.0000   BUS-2      
			Bus3         PV                   1.02500       13.85       0.8500   -0.1697    0.0000    0.0000   BUS-3      
			Bus4                              1.05531        0.02       0.0000    0.0000    0.0000    0.0000   BUS-4      
			Bus5                ConstP        1.01843       -0.29       0.0000    0.0000    1.2500    0.5000   BUS-5      
			Bus6                ConstP        1.02875        0.98       0.0000    0.0000    0.9000    0.3000   BUS-6      
			Bus7                              1.04231        9.90       0.0000    0.0000    0.0000    0.0000   BUS-7      
			Bus8                ConstP        1.02737        8.21       0.0000    0.0000    1.0000    0.3500   BUS-8      
			Bus9                              1.03584       11.16       0.0000    0.0000    0.0000    0.0000   BUS-9      
			Bus90                             1.03430       11.57       0.0000    0.0000    0.0000    0.0000   BUS-90     
			Bus91        PV                   1.00000       22.69       0.4600   -0.0311    0.0000    0.0000   BUS-91     
			Bus92        PQ                   1.03870       10.45       0.3200   -0.0400    0.0000    0.0000   BUS-92     
			3WNDTR_90_91_92_1                      1.03886       10.38       0.0000    0.0000    0.0000    0.0000   3WXfr StarBus 
		 */

		AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()+0.0136)<0.0001);
  		assertTrue(""+p.getImaginary(), Math.abs(p.getImaginary()-0.5989)<0.0001);

		AclfBus bus4 = net.getBus("Bus4");
		double voltageMag = bus4.getVoltageMag();
		assertTrue(""+voltageMag, Math.abs(voltageMag - 1.05531) < 0.0001);

		//bus92 voltage
		AclfBus bus92 = net.getBus("Bus92");
		double voltageMagBus92 = bus92.getVoltageMag();
		assertTrue(Math.abs(voltageMagBus92 - 1.03870) < 0.0001);
		//bus92 voltage angle
		assertTrue(Math.abs(bus92.getVoltageAng() - 10.45/180*Math.PI) < 0.0001);

		//bus 92 gen results
		Complex pGenBus92 = bus92.toGenBus().getGenResults(UnitType.PU);
		assertTrue(Math.abs(pGenBus92.getReal() - 0.3200) < 0.0001);
		assertTrue(Math.abs(pGenBus92.getImaginary() + 0.0400) < 0.0001);

		//bus 91 gen results
		AclfBus bus91 = net.getBus("Bus91");
		Complex pGenBus91 = bus91.toGenBus().getGenResults(UnitType.PU);
		assertTrue(Math.abs(pGenBus91.getReal() - 0.4600) < 0.0001);
		assertTrue(Math.abs(pGenBus91.getImaginary() + 0.0311) < 0.0001);

	}
	
	private void testVAclf(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

	  	AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.71646)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-0.27107)<0.0001);
	}

	@Test
	public void testLoadWithDGen() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v34/ieee9_dgen_v34.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_34)
				.load()
				.getImportedObj();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.getNrMethodConfig().setNonDivergent(true);
		algo.loadflow();

		assertTrue("Loadflow converged", net.isLfConverged());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));

		/*
		 * @!   I,'ID',STAT,AREA,ZONE,      PL,        QL,        IP,        IQ,        YP,        YQ, OWNER,SCALE,INTRPT,  DGENP,     DGENQ, DGENF
     5,'1 ',   1,   1,   1,   150.000,    60.000,     0.000,     0.000,     0.000,     0.000,   1,    1,  0,     25.000,     10.000,   1
     6,'1 ',   1,   1,   1,    90.000,    30.000,     0.000,     0.000,     0.000,     0.000,   1,    1,  0,     10.000,     5.000,   0
     8,'1 ',   1,   1,   1,   100.000,    35.000,     0.000,     0.000,     0.000,     0.000,   1,    1,  0,     0.000,     0.000,   0
		 */

		//check the contribute load records at bus 5 and 6 to see if the distributed generation is included
		// Bus 5 has 150 MW load and 60 MVar load, and 0.25 MW + j0.1 MVar distributed generation
		AclfBus bus5 = net.getBus("Bus5");
		assertTrue(Math.abs(bus5.getLoadP() - 1.5 + 0.25) < 0.0001); // load P + dgen P
		assertTrue(Math.abs(bus5.getLoadQ() - 0.6 + 0.1) < 0.0001); // load Q + dgen Q

		// Bus 6 has 0.9 MW load and 0.3 MVar load, and 0.1 MW + j0.05 MVar distributed generation, but it is offline
		AclfBus bus6 = net.getBus("Bus6");
		assertTrue(Math.abs(bus6.getLoadP() - 0.9 ) < 0.0001); // load P + dgen P
		assertTrue(Math.abs(bus6.getLoadQ() - 0.3 ) < 0.0001); // load Q + dgen Q

		AclfLoad load6 = bus6.getContributeLoadList().get(0);
		// check the distributed generation load record is offline
		assertTrue("DGen load record is not offline", load6.isDistGenStatus() == false);


		testVAclf(net);

		
	}
 
	@Test
	public void testSVCLocalControl() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v33/ieee9_svc_v33.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();

		//check the SVC data connected to Bus-5
		/*
		 * @!  'NAME',         I,     J,MODE,PDES,   QDES,  VSET,   SHMX,   TRMX,   VTMN,   VTMX,   VSMX,    IMX,   LINX,   RMPCT,OWNER,  SET1,    SET2,VSREF, FCREG,   'MNAME'
			"SVC1",5,     0, 1,  0.000,  0.000,1.00,50.000,  0.000,0.90000,1.10000,1.00000,  0.000,0.05000,  100.0, 0, 0.00000, 0.00000,   0, 0,   "            "
		 */

		AclfBus bus5 = net.getBus("Bus5");

		//TODO: Should bus5 becomes a GenPV bus due to the addition of SVC?
		// No. For SVC control, the bus Q (modeled as the load) is adjusted according
		// to sensitivity dQ/dV, so the bus shold be a GenPQ bus with fixed Q (gen).
		// For a GenPV bus, the bus Q is calculated according to the voltage set point, 
		// which is not the case for SVC.
		
		StaticVarCompensator svc1 = bus5.getStaticVarCompensator();
		assertNotNull("Bus5 has SVC connected", svc1);
		
		assertTrue("SVC name is correct", svc1.getName().equals("SVC1"));

		assertTrue("SVC voltage set point is correct", Math.abs(svc1.getVSpecified() - 1.01) < 1e-6);

		//TODO: as the Qlimit is related to the actual Q output, which is dependent on the load flow bus voltage, so it is better to use another variable to store the capacitive rating, maybe Binit
		assertTrue("SVC capacitive rating is correct", Math.abs(svc1.getQLimit().getMax() - 0.5) < 1e-6);

		// rmpct is the percentage of the SVC remote control percentage, which is 100% in this case
		assertTrue("SVC remote control percentage is correct", Math.abs(svc1.getRemoteControlPercentage() - 100.0) < 1e-6);

		//remote control bus is bus 5 (local bus)
		assertTrue("SVC remote control bus is correct", svc1.getRemoteBus().getId().equals("Bus5"));

		//default control mode is continuous
		assertTrue("SVC control mode is correct", svc1.getControlMode() == AclfAdjustControlMode.CONTINUOUS);

		//control type, default is  bus voltage control
		assertTrue("SVC control type is correct", svc1.getRemoteQControlType() == BusBranchControlType.BUS_VOLTAGE);

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		//algo.setNonDivergent(true);
		algo.loadflow();

		assertTrue("Loadflow converged", net.isLfConverged());

		System.out.println(AclfOutFunc.loadFlowSummary(net));

		//check the SVC results
		//bus 5 is a genPV bus, so the voltage is 1.01 pu
		assertTrue("Bus5 voltage magnitude is correct", Math.abs(bus5.getVoltageMag() - 1.01) < 1e-6);
		// TODO: note the SVC is controlling the local bus voltage, so it is a GenPV bus
		assertTrue( bus5.isGenPV()); 
		RemoteQBus re = bus5.getRemoteQBus();
		AclfGenBusAdapter genBus = re.getParentBus().toGenBus();
		double q = genBus.getGenResults(UnitType.PU).getImaginary();
		System.out.println("Bus5 svc q: " + q);
		assertTrue("SVC Q output is correct", Math.abs(q - 0.1598) < 1e-3); // Q output is 0.5 pu, which is the capacitive rating
	}

	@Test
	public void testSVCRemoteControl() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v33/ieee9_svc_remote_v33.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
		//IpssLogger.getLogger().setLevel(Level.INFO);

		//check the SVC data connected to Bus-5
		/*
		 @!  'NAME',         I,     J,MODE,PDES,   QDES,  VSET,   SHMX,   TRMX,   VTMN,   VTMX,   VSMX,    IMX,   LINX,   RMPCT,OWNER,  SET1,    SET2,VSREF, FCREG,   'MNAME'
"SVC1",5,     0, 1,  0.000,  0.000,1.01,50.000,  0.000,0.90000,1.10000,1.00000,  0.000,0.05000,  100.0, 0, 0.00000, 0.00000,   0, 50,   "            "
		 */

		AclfBus bus50 = net.getBus("Bus50");

		StaticVarCompensator svc1 = bus50.getStaticVarCompensator();
		assertNotNull("Bus50 has SVC connected", svc1);

		assertTrue("SVC name is correct", svc1.getName().equals("SVC1"));

		assertTrue("SVC voltage set point is correct", Math.abs(svc1.getVSpecified() - 1.01) < 1e-6);

		//TODO: as the Qlimit is related to the actual Q output, which is dependent on the load flow bus voltage, so it is better to use another variable to store the capacitive rating, maybe Binit
		assertTrue("SVC capacitive rating is correct", Math.abs(svc1.getQLimit().getMax() - 1.0) < 1e-6);

		// rmpct is the percentage of the SVC remote control percentage, which is 100% in this case
		assertTrue("SVC remote control percentage is correct", Math.abs(svc1.getRemoteControlPercentage() - 100.0) < 1e-6);

		//remote control bus is bus 4
		assertTrue("SVC remote control bus is correct", svc1.getRemoteBus().getId().equals("Bus5"));

		//default control mode is continuous
		assertTrue("SVC control mode is correct", svc1.getControlMode() == AclfAdjustControlMode.CONTINUOUS);

		//control type, default is  bus voltage control
		assertTrue("SVC control type is correct", svc1.getRemoteQControlType() == BusBranchControlType.BUS_VOLTAGE);

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		//algo.setNonDivergent(true);
		algo.loadflow();

		assertTrue("Loadflow converged", net.isLfConverged());

		/*
		 * How to investigate the issue:
		 */
		// 1. print out the bus detailed info
		//System.out.println(bus50.toString(net.getBaseKva()));
		// 2. trace the bus mismatch calculation process
		//Complex busPQ = bus50.mismatch(AclfMethodType.NR);
		
		System.out.println(AclfOutFunc.loadFlowSummary(net));

		//check the SVC results
		AclfBus bus5 = net.getBus("Bus5");
		assertTrue("Bus5 voltage magnitude is correct", Math.abs(bus5.getVoltageMag() - svc1.getVSpecified()) < 1e-3);
		double q = bus50.toCapacitorBus().getQResults();
		assertTrue("SVC Q output is correct"+q, Math.abs(q - 0.1477) < 1e-3); // Q output is 0.5 pu, which is the capacitive rating

		/*
		 NOTE: there is no difference in the results below, even though the Qg value is different for Bus 50
		 NO SVC
      BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       0.7165    0.2659    0.0000    0.0000   BUS-1      
  Bus2         PV                   1.02500        9.31       1.6300    0.0629    0.0000    0.0000   BUS-2      
  Bus3         PV                   1.02500        4.70       0.8500   -0.1107    0.0000    0.0000   BUS-3      
  Bus4                              1.02624       -2.18       0.0000    0.0000    0.0000    0.0000   BUS-4      
  Bus5                              0.99651       -3.95       0.0000    0.0000    0.0000    0.0000   BUS-5      
  Bus6                ConstP        1.01301       -3.65       0.0000    0.0000    0.9000    0.3000   BUS-6      
  Bus7                              1.02599        3.75       0.0000    0.0000    0.0000    0.0000   BUS-7      
  Bus8                ConstP        1.01607        0.76       0.0000    0.0000    1.0000    0.3500   BUS-8      
  Bus9                              1.03247        2.00       0.0000    0.0000    0.0000    0.0000   BUS-9      
  Bus50               ConstP        0.99589       -4.02       0.0000    0.0000    1.2500    0.5000   BUS-50    
  
  

		 * With SVC
     BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       0.7156    0.1681    0.0000    0.0000   BUS-1      
  Bus2         PV                   1.02500        9.29       1.6300    0.0043    0.0000    0.0000   BUS-2      
  Bus3         PV                   1.02500        4.72       0.8500   -0.1389    0.0000    0.0000   BUS-3      
  Bus4                              1.03157       -2.17       0.0000    0.0000    0.0000    0.0000   BUS-4      
  Bus5                              1.01082       -3.96       0.0000    0.0000    0.0000    0.0000   BUS-5      
  Bus6                ConstP        1.01723       -3.61       0.0000    0.0000    0.9000    0.3000   BUS-6      
  Bus7                              1.02955        3.75       0.0000    0.0000    0.0000    0.0000   BUS-7      
  Bus8                ConstP        1.01890        0.78       0.0000    0.0000    1.0000    0.3500   BUS-8      
  Bus9                              1.03409        2.03       0.0000    0.0000    0.0000    0.0000   BUS-9      
  Bus50               ConstP        1.01036       -4.02       0.0000    0.1607    1.2500    0.5000   BUS-50    

                Remote Q Voltage Adjustment/Control

       VcBus    Type    ReQBus/Branch   Actual    Spec       Q      Qmax     Qmin   Status
     -------- -------- --------------- -------- -------- -------- -------- -------- ------
    Bus50     Voltage            Bus5   1.0108   1.0100     0.16     1.00     0.00    on
*/
	}
	
	
}


