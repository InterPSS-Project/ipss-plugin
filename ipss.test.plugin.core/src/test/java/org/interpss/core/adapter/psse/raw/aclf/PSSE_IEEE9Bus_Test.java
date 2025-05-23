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
 
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dep.datamodel.bean.aclf.AclfNetBean;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
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

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
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
  		assertTrue(Math.abs(p.getImaginary()-0.3803)<0.0001);

		AclfBus bus4 = net.getBus("Bus4");
		double voltageMag = bus4.getVoltageMag();
		assertTrue(Math.abs(voltageMag - 1.05844) < 0.0001);


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

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.setNonDivergent(true);
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
  		assertTrue(Math.abs(p.getImaginary()-0.3570)<0.0001);

		AclfBus bus4 = net.getBus("Bus4");
		double voltageMag = bus4.getVoltageMag();
		assertTrue(Math.abs(voltageMag - 1.05878) < 0.0001);


		AclfBus bus7 = net.getBus("Bus7");
		double voltageMagBus7 = bus7.getVoltageMag();
		assertTrue(Math.abs(voltageMagBus7 - 1.04237) < 0.0001);
		

		AclfBus bus10 = net.getBus("Bus10");
		double voltageMagBus10 = bus10.getVoltageMag();
		assertTrue(Math.abs(voltageMagBus10 - 1.03514) < 0.0001);
	}
	
	private void testVAclf(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

	  	AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.71646)<0.00001);
  		assertTrue(Math.abs(p.getImaginary()-0.27107)<0.00001);
	}	
}


