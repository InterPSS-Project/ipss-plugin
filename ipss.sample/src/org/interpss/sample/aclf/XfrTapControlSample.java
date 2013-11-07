 /*
  * @(#)ZBusSample.java   
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
  * @Date 10/15/2010
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adpter.AclfLoadBus;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.aclf.adpter.AclfXformer;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleCases;
import com.interpss.spring.CoreCommonSpringFactory;


public class XfrTapControlSample {
	public static void main(String args[]) throws IpssNumericException, InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork net = SampleCases.sample2BusXfr();
		
		// xfr tap could be used to control voltage or mvar flow. In this
		// example, it is used to control voltage
		AclfBranch branch = net.getBranch("0001->0002(1)");
		TapControl tap = CoreObjectFactory.createTapVControlBusVoltage(branch, 
							AdjControlType.POINT_CONTROL, net, "0002");
		// tap limit
		tap.setControlLimit(new LimitType(1.10, 0.9));
		// control voltage on the toside
		tap.setControlOnFromSide(false);
		// use the toside tap to control
		tap.setTapOnFromSide(false);
		// control voltage to 0.90 pu
		tap.setControlSpec(0.90);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

	  	// after the LF, turn ratio on the toside has been adjusted
		assert(Math.abs(branch.getToTurnRatio()-1.06717)<0.0001);
		assert(tap.isActive());

		// toside bus voltage is controled to 0.9 pu
		assert(Math.abs(tap.getVcBus().getVoltageMag()-0.9)<0.0001);
		assert(Math.abs(net.getBus("0002").getVoltageMag()-0.9)<0.0001);
	}	
	
	public static AclfNetwork sampleNet2BusWithXfr()	{
		// Create an AclfNetwork object
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		net.setBaseKva(100000.0);
	  	
  		AclfBus bus1 = CoreObjectFactory.createAclfBus("0001", net);
  		//net.addBus(bus1);
  		bus1.setAttributes("Bus 1", "");
  		bus1.setBaseVoltage(10000.0);
  		bus1.setGenCode(AclfGenCode.SWING);
  		AclfSwingBus swingBus = bus1.toSwingBus();
  		swingBus.setVoltMag(1.0, UnitType.PU);
  		swingBus.setVoltAng(0.0, UnitType.Deg);
  		
  		AclfBus bus2 = CoreObjectFactory.createAclfBus("0002", net);
  		//net.addBus(bus2);
  		bus2.setAttributes("Bus 2", "");
  		bus2.setBaseVoltage(4000.0);
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		bus2.setLoadCode(AclfLoadCode.CONST_P);
  		AclfLoadBus loadBus = bus2.toLoadBus();
  		loadBus.setLoad(new Complex(1.0, 0.8), UnitType.PU);
  		
  		AclfBranch branch = CoreObjectFactory.createAclfBranch();
  		net.addBranch(branch, "0001", "0002");
  		branch.setAttributes("Branch 1", "", "1");
  		branch.setBranchCode(AclfBranchCode.XFORMER);
		AclfXformer xfr = branch.toXfr();
		xfr.setZ(new Complex(0.05, 0.1), UnitType.PU, 4000.0);
	  	xfr.setFromTurnRatio(1.0);
	  	
  		return net;
	}	
}
