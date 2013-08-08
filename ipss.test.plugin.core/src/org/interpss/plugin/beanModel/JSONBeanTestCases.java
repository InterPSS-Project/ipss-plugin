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

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.mapper.bean.aclf.AclfNetBeanMapper;
import org.interpss.mapper.bean.aclf.AclfResultBeanMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.google.gson.Gson;
import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;

public class JSONBeanTestCases extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
		AclfNetBean netBean = createNetCaseData1();
		
		AclfNetwork aclfNet = new AclfNetBeanMapper()
			.map2Model(netBean)
			.getAclfNet();
		
	  	aclfNet.accept(CoreObjectFactory.createLfAlgoVisitor());
  		//System.out.println(net.net2String());
	  	
  		assertTrue(aclfNet.isLfConverged());	
  		
  		AclfBus swingBus = aclfNet.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);
		
		AclfNetResultBean aclfResult = new AclfResultBeanMapper()
				.map2Model(aclfNet);
		
		System.out.println(new Gson().toJson(aclfResult));
	}	
	
	@Test
	public void testCase2() throws Exception {
		AclfNetBean netBean = createNetCaseData2();
		
		AclfNetwork aclfNet = new AclfNetBeanMapper()
			.map2Model(netBean)
			.getAclfNet();
		
	  	aclfNet.accept(CoreObjectFactory.createLfAlgoVisitor());
  		//System.out.println(net.net2String());
	  	
  		assertTrue(aclfNet.isLfConverged());	
  		
  		AclfBus swingBus = aclfNet.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);

	}	
	
	private AclfNetBean createNetCaseData1() {
		String str = 
				"{'bus_list':[" +
				"  {'base_v':13800.0,'area':'1','zone':'1','load_code':'ConstP','v_mag':1.0,'v_ang':0.0,'load':{'re':1.6,'im':0.8},'id':'1'}," +
				"  {'base_v':13800.0,'area':'1','zone':'1','load_code':'ConstP','v_mag':1.0,'v_ang':0.0,'load':{'re':2.0,'im':1.0},'id':'2'}," +
				"  {'base_v':13800.0,'area':'1','zone':'1','load_code':'ConstP','v_mag':1.0,'v_ang':0.0,'load':{'re':3.7,'im':1.3},'id':'3'}," +
				"  {'base_v':1000.0,'area':'1','zone':'1','gen_code':'PV','v_mag':1.05,'v_ang':0.0,'gen':{'re':5.0,'im':0.0},'id':'4'}," +
				"  {'base_v':4000.0,'area':'1','zone':'1','gen_code':'Swing','v_mag':1.05,'v_ang':0.0,'id':'5'}" +
				"]," +
				"'branch_list':[" +
				"  {'z':{'re':0.04,'im':0.25},'shunt_y':{'re':0.0,'im':0.5},'f_id':'1','t_id':'2','cir_id':'1','bra_code':'Line'}," +
				"  {'z':{'re':0.1,'im':0.35},'f_id':'1','t_id':'3','cir_id':'1','bra_code':'Line'}," +
				"  {'z':{'re':0.08,'im':0.3},'shunt_y':{'re':0.0,'im':0.5},'f_id':'2','t_id':'3','cir_id':'1','bra_code':'Line'}," +
				"  {'z':{'re':0.0,'im':0.015},'ratio':{'f':1.0,'t':1.05},'f_id':'4','t_id':'2','cir_id':'1','bra_code':'Xfr'}," +
				"  {'z':{'re':0.0,'im':0.03},'ratio':{'f':1.0,'t':1.05},'f_id':'5','t_id':'3','cir_id':'1','bra_code':'Xfr'}" +
				"]," +
				"'base_kva':100000.0,'unit_ang':'Deg','unit_bus_v':'PU','unit_bus_p':'PU','unit_branch_z':'PU','unit_branch_cur':'Amp','unit_branch_b':'PU','name':'Test case','desc':'A Aclf JSon test case'}";
		return new Gson().fromJson(str, AclfNetBean.class);
	}
	
	private AclfNetBean createNetCaseData2() {
		AclfNetBean netBean = new AclfNetBean();
		netBean.base_kva = 100000.0;
		netBean.name = "Test case";
		netBean.desc = "A Aclf JSon test case";
		
		AclfBusBean busBean = new AclfBusBean();
		netBean.bus_list.add(busBean);
		// "1", 13800, 1, 1, AclfLoadCode.CONST_P, 1.6, 0.8, UnitType.PU );
		busBean.id = "1";
		busBean.base_v = 13800.0;
		busBean.area = 1;
		busBean.zone = 1;
		busBean.load_code = AclfBusBean.LoadCode.ConstP;
		busBean.load = new ComplexBean(1.6, 0.8);
				
		busBean = new AclfBusBean();
		netBean.bus_list.add(busBean);
		// "2", 13800, 1, 1, AclfLoadCode.CONST_P, 2.0, 1.0, UnitType.PU );
		busBean.id = "2";
		busBean.base_v = 13800.0;
		busBean.area = 1;
		busBean.zone = 1;
		busBean.load_code = AclfBusBean.LoadCode.ConstP;
		busBean.load = new ComplexBean(2.0, 1.0);
		
		busBean = new AclfBusBean();
		netBean.bus_list.add(busBean);
		// "3", 13800, 2, 1, AclfLoadCode.CONST_P, 3.7, 1.3, UnitType.PU );
		busBean.id = "3";
		busBean.base_v = 13800.0;
		busBean.area = 1;
		busBean.zone = 1;
		busBean.load_code = AclfBusBean.LoadCode.ConstP;
		busBean.load = new ComplexBean(3.7, 1.3);
		
		busBean = new AclfBusBean();
		netBean.bus_list.add(busBean);
		// "4", 1000,  1, 1, 5.0, UnitType.PU, 1.05, UnitType.PU );
		busBean.id = "4";
		busBean.base_v = 1000.0;
		busBean.area = 1;
		busBean.zone = 1;
		busBean.gen_code = AclfBusBean.GenCode.PV;
		busBean.v_mag = 1.05;
		busBean.gen = new ComplexBean(5.0, 0.0);
		
		busBean = new AclfBusBean();
		netBean.bus_list.add(busBean);
		// AclfInputUtilFunc.addSwingBusTo(net, "5", 4000,  2, 1, 1.05, UnitType.PU, 0.0, UnitType.Deg );	
		busBean.id = "5";
		busBean.base_v = 4000.0;
		busBean.area = 1;
		busBean.zone = 1;
		busBean.gen_code = AclfBusBean.GenCode.Swing;
		busBean.v_mag = 1.05;
		busBean.v_ang = 0.0;
		
		

		AclfBranchBean braBean = new AclfBranchBean();
		netBean.branch_list.add(braBean);
		//1   1   2    0.04    0.25    -0.25
		braBean.f_id = "1";
		braBean.t_id = "2";
		braBean.bra_code = BaseBranchBean.BranchCode.Line;
		braBean.z = new ComplexBean(0.04, 0.25);
		braBean.shunt_y = new ComplexBean(0.0, 0.5);
		
		braBean = new AclfBranchBean();
		netBean.branch_list.add(braBean);
		//2   1   3    0.1     0.35     0.0
		braBean.f_id = "1";
		braBean.t_id = "3";
		braBean.bra_code = BaseBranchBean.BranchCode.Line;
		braBean.z = new ComplexBean(0.1, 0.35);

		braBean = new AclfBranchBean();
		netBean.branch_list.add(braBean);
		//3   2   3    0.08    0.3     -0.25
		braBean.f_id = "2";
		braBean.t_id = "3";
		braBean.bra_code = BaseBranchBean.BranchCode.Line;
		braBean.z = new ComplexBean(0.08, 0.3);
		braBean.shunt_y = new ComplexBean(0.0, 0.5);

		braBean = new AclfBranchBean();
		netBean.branch_list.add(braBean);
		//4   4   2    0.0     0.015    1.05
		braBean.f_id = "4";
		braBean.t_id = "2";
		braBean.bra_code = BaseBranchBean.BranchCode.Xfr;
		braBean.z = new ComplexBean(0.0, 0.015);
		braBean.ratio = new BranchValueBean(1.0, 1.05);

		braBean = new AclfBranchBean();
		netBean.branch_list.add(braBean);
		//5   5   3    0.0     0.03     1.05
		braBean.f_id = "5";
		braBean.t_id = "3";
		braBean.bra_code = BaseBranchBean.BranchCode.Xfr;
		braBean.z = new ComplexBean(0.0, 0.03);
		braBean.ratio = new BranchValueBean(1.0, 1.05);
	
		//System.out.println(new Gson().toJson(netBean));
		
		return netBean;
	}
}

