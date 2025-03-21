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

package org.interpss.plugin.pssl.util;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.simu.net.IpssAcscNet;
import org.interpss.plugin.pssl.simu.net.IpssAcscNet.AcscNetworkDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;

public class AcscSample {
	public static AcscNetwork create5BusSampleNet() throws InterpssException {
		AcscNetworkDSL netDsl = IpssAcscNet.createAcscNetwork("Sample AcscNetwork");
		netDsl.baseMva(100.0);

		netDsl.addAcscBus("1", "name-Bus 1")
		            .baseVoltage(13800.0)
		            .loadCode(AclfLoadCode.CONST_P)
		            .load(new Complex(1.6, 0.8), UnitType.PU)
		            .scCode(BusScCode.NON_CONTRI);
		         
		netDsl.addAcscBus("2", "name-Bus 2")
		            .baseVoltage(13800.0)  
		            .loadCode(AclfLoadCode.CONST_P)
		            .load(new Complex(2.0, 1.0), UnitType.PU)
		            .scCode(BusScCode.NON_CONTRI);
		
		netDsl.addAcscBus("3", "name-Bus 3")
        			.baseVoltage(13800.0)  
        			.loadCode(AclfLoadCode.CONST_P)
        			.load(new Complex(3.7, 1.3), UnitType.PU)
        			.scCode(BusScCode.NON_CONTRI);		
		
		netDsl.addAcscBus("4", "name-Bus 4")
        			.baseVoltage(1000.0)  
        			.genCode(AclfGenCode.GEN_PV)
        			.genP_vMag(5.0, UnitType.PU, 1.05, UnitType.PU)
        			.scCode(BusScCode.CONTRIBUTE)
        			.z(new Complex(0.0,0.02), SequenceCode.POSITIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.02), SequenceCode.NEGATIVE, UnitType.PU) 
        			.z(new Complex(0.0,1.0e10), SequenceCode.ZERO, UnitType.PU) 
        			.groundCode("SolidGrounded")
        			.groundZ(new Complex(0.0, 0.0), UnitType.PU);	
		
		netDsl.addAcscBus("5", "name-Bus 5")
        			.baseVoltage(4000.0)  
        			.genCode(AclfGenCode.SWING)
        			.voltageSpec(1.05, UnitType.PU, 5.0, UnitType.Deg)
        			.scCode(BusScCode.CONTRIBUTE)
        			.z(new Complex(0.0,0.02), SequenceCode.POSITIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.02), SequenceCode.NEGATIVE, UnitType.PU) 
        			.z(new Complex(0.0,1.0e10), SequenceCode.ZERO, UnitType.PU) 
        			.groundCode("SolidGrounded")
        			.groundZ(new Complex(0.0, 0.0), UnitType.PU);	
		
		
		netDsl.addAcscBranch("1", "2")
		            .branchCode(AclfBranchCode.LINE)
		            .z(new Complex(0.04, 0.25), UnitType.PU)
		            .shuntB(0.5, UnitType.PU)
		            .z0(new Complex(0.0, 0.7), UnitType.PU);     
		
		netDsl.addAcscBranch("1", "3")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.1, 0.35), UnitType.PU) 
        			.z0(new Complex(0.0,1.0), UnitType.PU);
		
		netDsl.addAcscBranch("2", "3")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.08, 0.3), UnitType.PU)
        			.shuntB(0.5, UnitType.PU)
        			.z0(new Complex(0.0,0.75), UnitType.PU);
		
		netDsl.addAcscBranch("4", "2")
					.branchCode(AclfBranchCode.XFORMER)
					.z(new Complex(0.0, 0.015), UnitType.PU)
					.turnRatio(1.0,  1.05, UnitType.PU)
					.z0( new Complex(0.0, 0.03), UnitType.PU);
					//.fromGrounding(XfrConnectCode.WYE_UNGROUNDED)
					//.toGrounding(XfrConnectCode.DELTA);
		
		netDsl.addAcscBranch("5", "3")
					.branchCode(AclfBranchCode.XFORMER)
					.z(new Complex(0.0, 0.03), UnitType.PU)
					.turnRatio(1.0,  1.05, UnitType.PU)
					.z0(new Complex(0.0, 0.03), UnitType.PU);
					//.fromGrounding(XfrConnectCode.WYE_UNGROUNDED)
					//.toGrounding(XfrConnectCode.DELTA);

		//System.out.println(netDsl.getAcscNet().net2String());
		return (AcscNetwork)netDsl.getAclfNet();
	}
}
