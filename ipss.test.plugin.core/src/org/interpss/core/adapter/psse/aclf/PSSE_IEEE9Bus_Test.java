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

package org.interpss.core.adapter.psse.aclf;

import static com.interpss.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.pssl.plugin.IpssAdapter;
import com.interpss.pssl.plugin.IpssAdapter.PsseVersion;

public class PSSE_IEEE9Bus_Test extends CorePluginTestSetup { 
	//@Test
	public void load() throws Exception {
		// load the test data V33
		AclfNetwork net33 = IpssAdapter.importNet("testdata/psse/v31/ieee9_v31.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_31)
				.load()
				.getNet();
	}

	//@Test
	public void compare() throws Exception {
		// load the test data V30
		AclfNetwork net30 = IpssAdapter.importNet("testdata/psse/v30/IEEE9Bus/ieee9.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getNet();
		AclfNetBean netBean30 = new AclfNet2BeanMapper().map2Model(net30);
		
		// load the test data V29
		AclfNetwork net29 = IpssAdapter.importNet("testdata/psse/v29/ieee9_v29.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_29)
				.load()
				.getNet();
		AclfNetBean netBean29 = new AclfNet2BeanMapper().map2Model(net29);
		
		// compare the data model with V30
		netBean30.compareTo(netBean29);

		// load the test data V31
		AclfNetwork net31 = IpssAdapter.importNet("testdata/psse/v31/ieee9_v31.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_31)
				.load()
				.getNet();
		AclfNetBean netBean31 = new AclfNet2BeanMapper().map2Model(net31);
		
		// compare the data model  with V30
		netBean30.compareTo(netBean31);
		
		// load the test data V32
		AclfNetwork net32 = IpssAdapter.importNet("testdata/psse/v32/ieee9_v32.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_32)
				.load()
				.getNet();
		AclfNetBean netBean32 = new AclfNet2BeanMapper().map2Model(net32);
		
		// compare the data model  with V30
		netBean30.compareTo(netBean32);
		
		// load the test data V33
		AclfNetwork net33 = IpssAdapter.importNet("testdata/psse/v33/ieee9_v33.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getNet();
		AclfNetBean netBean33 = new AclfNet2BeanMapper().map2Model(net33);
		
		// compare the data model with V30
		netBean30.compareTo(netBean33);		
	}
	
	@Test
	public void testV30() throws Exception {
		AclfNetwork net = IpssAdapter.importNet("testdata/psse/v30/IEEE9Bus/ieee9.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getNet();

		testVAclf(net);
	}

	@Test
	public void testV29() throws Exception {
		AclfNetwork net = IpssAdapter.importNet("testdata/psse/v29/ieee9_v29.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_29)
				.load()
				.getNet();

		testVAclf(net);
	}

	@Test
	public void testV31() throws Exception {
		AclfNetwork net = IpssAdapter.importNet("testdata/psse/v31/ieee9_v31.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_31)
				.load()
				.getNet();

		testVAclf(net);
	}
	
	@Test
	public void testV32() throws Exception {
		AclfNetwork net = IpssAdapter.importNet("testdata/psse/v32/ieee9_v32.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_32)
				.load()
				.getNet();

		testVAclf(net);
	}
	
	@Test
	public void testV33() throws Exception {
		AclfNetwork net = IpssAdapter.importNet("testdata/psse/v33/ieee9_v33.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getNet();

		testVAclf(net);
	}
	
	private void testVAclf(AclfNetwork net) throws Exception {
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

	  	AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBus swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.71646)<0.00001);
  		assertTrue(Math.abs(p.getImaginary()-0.27107)<0.00001);
	}	
}


