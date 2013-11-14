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

package org.interpss.core.dist;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.simu.IpssDist;
import org.interpss.pssl.simu.net.IpssDistNet.DistNetDSL;
import org.junit.Test;

import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.dist.DistBus;
import com.interpss.dist.adpter.DistGenerator;
import com.interpss.dist.adpter.DistUtility;
import com.interpss.dist.adpter.DistXformer;

public class DistSys_Test extends CorePluginTestSetup {
	@Test
	public void singlePointTest1() {
		DistNetDSL distNet = IpssDist.createDistNetwork("Sample DistNetwork")      
        						.setBaseKva(100000.0);
		
		DistUtility util = distNet.addUtility("Bus1", 138.0, UnitType.kV);
		util.setVoltage(1.0, UnitType.PU, 0.0, UnitType.Deg);
		util.setMvaRating(1000.0, 800.0, UnitType.mVA);
		util.setX_R(100.0, 100.0);

		DistGenerator gen = distNet.addGenerator("Bus2", 4160.0, UnitType.Volt);
		gen.setRatedKW(5.0, UnitType.mW);
		gen.setRatedVoltage(1.0, UnitType.PU);
		gen.setPFactor(0.8, UnitType.PU);
		gen.setZ1(new Complex(0.0, 0.1));
		gen.setZ0_2(new Complex(0.0,0.05), new Complex(0.0, 0.1));
		gen.setZUnit(UnitType.PU);
		
		DistXformer xfr = distNet.addXformer("Bus1", "Bus2");
		xfr.setRating(10.0, UnitType.mVA);
		xfr.setRatedVoltage(138.0, 4.160, UnitType.kV);
		xfr.setZ(new Complex(0.0, 7.0), new Complex(0.0, 7.0), UnitType.Percent);
		xfr.setTurnRatio(1.0, 1.0, UnitType.PU);
		xfr.setConnect(XFormerConnectCode.WYE, XFormerConnectCode.DELTA);
		xfr.getPrimaryGrounding().setCode(BusGroundCode.UNGROUNDED);
		
		distNet.loadflow();
		//System.out.println(distNet.getAclfNetwork().net2String());
		//System.out.println(AclfOutFunc.lfResultsBusStyle(distNet.getAclfNetwork()));
	  	
	  	DistBus bus = (DistBus)distNet.getDistNetwork().getBus("Bus1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getReal() + 0.05) < 0.001);
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getImaginary() + 0.0349) < 0.0001);
	}
}

