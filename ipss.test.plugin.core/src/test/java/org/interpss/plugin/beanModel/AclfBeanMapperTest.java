 /*
  * @(#)CR_UserTestCases.java   
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

package org.interpss.plugin.beanModel;

import java.util.logging.Level;

import org.ieee.odm.common.ODMLogger;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.dep.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.dep.datamodel.bean.aclf.AclfBusBean;
import org.interpss.dep.datamodel.bean.aclf.AclfNetBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfBranchResultBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfNetResultBean;
import org.interpss.dep.datamodel.bean.base.BaseBranchBean.BranchCode;
import org.interpss.dep.datamodel.mapper.aclf.AclfBean2AclfNetMapper;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2ResultBeanMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.simu.util.sample.SampleTestingCases;

public class AclfBeanMapperTest extends CorePluginTestSetup {
	//@Test
	public void testCase() throws Exception {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);
		
	  	LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
			 		     .loadflow();
  		//System.out.println(net.net2String());
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
  		//System.out.println(aclfResultBusStyle.apply(net));
	}
	
	@Test
	public void testCase1() throws Exception {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);		
		
		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper()
			.map2Model(netBean)
			.getAclfNet();
		
	  	LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)
			 			 .loadflow();
		
  		assertTrue(aclfNet.isLfConverged());	
  		
  		AclfBus swingBus = aclfNet.getBus("5");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);
		
		AclfNetBean aclfBean = 
				    new AclfNet2ResultBeanMapper()
				    		.map2Model(aclfNet);
		AclfNetResultBean aclfResult = aclfBean.extension;
		//System.out.println(new Gson().toJson(aclfResult));
		
/*		
------------------------------------------------------------------------------------------------------------------------------------------
 Bus ID             Bus Voltage         Generation           Load             To             Branch P+jQ          Xfr Ratio   PS-Xfr Ang
              baseKV    Mag   Ang     (mW)    (mVar)    (mW)    (mVar)      Bus ID      (mW)    (mVar)   (kA)   (From)  (To) (from)   (to)
------------------------------------------------------------------------------------------------------------------------------------------
1             13.800  0.8622  -4.8     0.00     0.00   160.00    80.00  2             -146.62   -40.91   7.387 
                                                                        3              -13.38   -39.09   2.005 
2             13.800  1.0779  17.9     0.00     0.00   200.00   100.00  1              158.45    67.26   7.387 
                                                                        4             -500.00  -142.82  20.183   1.05       
                                                                        3              141.55   -24.43   5.575 
3             13.800  1.0364  -4.3     0.00     0.00   370.00   130.00  1               15.68    47.13   2.005 
                                                                        2             -127.74    20.32   5.575 
                                                                        5             -257.94  -197.45  13.113   1.05       
4              1.000  1.0500  21.8   500.00   181.31     0.00     0.00  2              500.00   181.31  278.52          1.05
5              4.000  1.0500   0.0   257.94   229.94     0.00     0.00  3              257.94   229.94  45.239          1.05
------------------------------------------------------------------------------------------------------------------------------------------	
*/		
		assertTrue(NumericUtil.equals(aclfResult.gen.re, (500.0+257.94)*0.01, 0.0001));
		//assertTrue(NumericUtil.equals(aclfResult.gen.im, (181.31+229.94)*0.01, 0.0001));
		
		assertTrue(NumericUtil.equals(aclfResult.load.re, (160.0+200.0+370.0)*0.01, 0.0001));
		assertTrue(NumericUtil.equals(aclfResult.load.im, (80.0+100.0+130.0)*0.01, 0.0001));

		assertTrue(NumericUtil.equals(aclfResult.loss.re, ((500.0+257.94)-(160.0+200.0+370.0))*0.01, 0.0001));
		//assertTrue(NumericUtil.equals(aclfResult.loss.im, (80.0+100.0+130.0)*0.01, 0.0001));

		for (AclfBusBean bus : aclfBean.getBusBeanList()) {
			if (bus.id.equals("5")) {
				assertTrue(NumericUtil.equals(bus.v_mag, 1.0500, 0.0001));
				assertTrue(NumericUtil.equals(bus.v_ang, 0.0, 0.0001));
				assertTrue(bus.gen_code == AclfBusBean.GenCode.Swing);
				//bus.gen;
				//System.out.println(bus.gen.re);
				assertTrue(NumericUtil.equals(bus.gen.re, 0, 0.0001));
				assertTrue(NumericUtil.equals(bus.gen.im, 0, 0.0001));
				assertTrue(bus.load_code == AclfBusBean.LoadCode.NonLoad);
				//bus.load;
				assertTrue(NumericUtil.equals(bus.load.re, 0.0, 0.0001));
				assertTrue(NumericUtil.equals(bus.load.im, 0.0, 0.0001));
			}
			else if (bus.id.equals("4")) {
				assertTrue(NumericUtil.equals(bus.v_mag, 1.0500, 0.0001));
				assertTrue(NumericUtil.equals(bus.v_ang, 21.8, 0.1));
				assertTrue(bus.gen_code == AclfBusBean.GenCode.PV);
				//bus.gen;
				assertTrue(NumericUtil.equals(bus.gen.re, 5.0, 0.0001));				
				assertTrue(NumericUtil.equals(bus.gen.im, 0.0, 0.0001));
				assertTrue(bus.load_code == AclfBusBean.LoadCode.NonLoad);
				//bus.load;
				assertTrue(NumericUtil.equals(bus.load.re, 0.0, 0.0001));
				assertTrue(NumericUtil.equals(bus.load.im, 0.0, 0.0001));
			}
			else if (bus.id.equals("1")) {
				assertTrue(NumericUtil.equals(bus.v_mag, 0.8622, 0.0001));
				assertTrue(NumericUtil.equals(bus.v_ang, -4.8, 0.1));
				assertTrue(bus.gen_code == AclfBusBean.GenCode.NonGen);
				//bus.gen;
				assertTrue(NumericUtil.equals(bus.gen.re, 0.0, 0.0001));
				assertTrue(NumericUtil.equals(bus.gen.im, 0.0, 0.0001));
				assertTrue(bus.load_code == AclfBusBean.LoadCode.ConstP);
				//bus.load;
				assertTrue(NumericUtil.equals(bus.load.re, 1.6, 0.0001));
				assertTrue(NumericUtil.equals(bus.load.im, 0.8, 0.0001));			}
		}
		
		for (AclfBranchBean<AclfBranchResultBean> bra : aclfBean.getBranchBeanList()) {
			if (bra.id.equals("1->2(1)")) {
				assertTrue(bra.bra_code == BranchCode.Line);
				/*
				bra.flow_f2t;  -146.62   -40.91   7.387
				bra.flow_t2f;   158.45    67.26   7.387
				bra.cur;
				*/
				AclfBranchResultBean braResult = bra.extension;
				assertTrue(NumericUtil.equals(braResult.flow_f2t.re, -1.4662, 0.0001));
				assertTrue(NumericUtil.equals(braResult.flow_f2t.im, -0.4091, 0.0001));
				assertTrue(NumericUtil.equals(braResult.flow_t2f.re,  1.5845, 0.0001));
				assertTrue(NumericUtil.equals(braResult.flow_t2f.im,  0.6726, 0.0001));
				assertTrue(NumericUtil.equals(braResult.cur,  7387, 1.0));
			}
			else if (bra.id.equals("4->2(1)")) {
				assertTrue(bra.bra_code == BranchCode.Xfr);
				/*
				bra.flow_f2t;  500.00   181.31   20.183
				bra.flow_t2f;  -500.00  -142.82  20.183
				bra.cur;
				*/
				AclfBranchResultBean braResult = bra.extension;
				assertTrue(NumericUtil.equals(braResult.flow_f2t.re,  5.0, 0.0001));
				assertTrue(NumericUtil.equals(braResult.flow_f2t.im,  1.8131, 0.0001));
				assertTrue(NumericUtil.equals(braResult.flow_t2f.re,  -5.0, 0.0001));
				assertTrue(NumericUtil.equals(braResult.flow_t2f.im,  -1.4282, 0.0001));
				assertTrue(NumericUtil.equals(braResult.cur,  20183, 1.0));
			}
		}
	}
	
	@Test
	public void testCase2() throws Exception {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);	
		
		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper()
			.map2Model(netBean)
			.getAclfNet();		
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2AclfBeanMapper().map2Model(aclfNet);		
			
		/*
		 * compare two AclfNetBean objects
		 * 
		 *    netBean - mapped from the original AclfNet object net
		 *    netBean1 - mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);
	}
	
	@Test
	public void testCase_2WPsxfr() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();
  		
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);	
		
		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper()
			.map2Model(netBean)
			.getAclfNet();		
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2AclfBeanMapper().map2Model(aclfNet);		
			
		/*
		 * compare two AclfNetBean objects
		 * 
		 *    netBean - mapped from the original AclfNet object net
		 *    netBean1 - mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);
	}
	
	@Test
	public void testCase_2WPsxfr_lf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);
		ODMLogger.getLogger().setLevel(Level.WARNING);
 
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();
  		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);
		
		/*net.accept(CoreObjectFactory.createLfAlgoVisitor());
		System.out.println(net.net2String());*/

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
				
	  	LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)
			 			 .loadflow(); 
		
  		assertTrue(aclfNet.isLfConverged());
  		
		//System.out.println(aclfNet.net2String());
		AclfBus bus = aclfNet.getBus("Bus1");
		//System.out.println(bus.getNetGenResults().getReal() +"," + bus.getNetGenResults().getImaginary());
		assertTrue(bus.calNetGenResults().getReal() - 3.10322 < 0.0001);
		assertTrue(bus.calNetGenResults().getImaginary() - 0.52117 < 0.0001);
	}	
	
}

