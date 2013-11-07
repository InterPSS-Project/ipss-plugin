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

package org.interpss.sample.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.common.visitor.IAclfNetBVisitor;
import com.interpss.simu.mnet.AclfBusChildNetRef;
import com.interpss.simu.util.sample.SampleCases;

public class AclfMultiNetSample {
	public static void main(String args[])  throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork mainNet = createNet();
		
		IAclfNetBVisitor netAlgoVisitor = CoreObjectFactory.createLfAlgoVisitor();
		mainNet.accept(netAlgoVisitor);
		
	  	System.out.println(AclfOutFunc.loadFlowSummary(mainNet));
	  	
	  	System.out.println("--------> LF results of ChildNet at Bus2");
	  	AclfBusChildNetRef bus2 = (AclfBusChildNetRef)mainNet.getAclfBus("0002");
	  	System.out.println(AclfOutFunc.loadFlowSummary((AclfNetwork)bus2.getChildNetRef()));

	  	System.out.println("--------> LF results of ChildNet at Bus3");
	  	AclfBusChildNetRef bus3 = (AclfBusChildNetRef)mainNet.getAclfBus("0003");
	  	System.out.println(AclfOutFunc.loadFlowSummary((AclfNetwork)bus3.getChildNetRef()));
	}

	public static AclfNetwork createNet() throws InterpssException {
		/*
		 * create a main net
		 */
		// Create an AclfNetwork object
		AclfNetwork mainNet = CoreObjectFactory.createAclfNetwork();
		mainNet.setBaseKva(100000.0);
	  	
  		AclfBus bus1 = CoreObjectFactory.createAclfBus("0001", mainNet);
  		//net.addBus(bus1);
  		bus1.setAttributes("Bus 1", "");
  		bus1.setBaseVoltage(4000.0);
  		bus1.setGenCode(AclfGenCode.SWING);
  		AclfSwingBus swingBus = bus1.toSwingBus();
  		swingBus.setVoltMag(1.0, UnitType.PU);
  		swingBus.setVoltAng(0.0, UnitType.Deg);
  		
  		AclfBusChildNetRef bus2 = SimuObjectFactory.createAclfBusNetRef("0002", mainNet);
  		bus2.setAttributes("Bus 2", "");
  		bus2.setBaseVoltage(4000.0);
  		bus2.setGenCode(AclfGenCode.GEN_PQ);
  		bus2.setLoadCode(AclfLoadCode.CONST_P);
  		
  		AclfBusChildNetRef bus3 = SimuObjectFactory.createAclfBusNetRef("0003", mainNet);
  		bus2.setAttributes("Bus 3", "");
  		bus2.setBaseVoltage(4000.0);
  		bus2.setGenCode(AclfGenCode.GEN_PQ);
  		bus2.setLoadCode(AclfLoadCode.CONST_P);

  		AclfBranch branch = CoreObjectFactory.createAclfBranch();
  		mainNet.addBranch(branch, "0001", "0002");
  		branch.setAttributes("Branch 1", "", "1");
  		branch.setBranchCode(AclfBranchCode.LINE);
		branch.toLine().setZ(new Complex(0.005, 0.01), UnitType.PU, 4000.0);
		
  		branch = CoreObjectFactory.createAclfBranch();
  		mainNet.addBranch(branch, "0002", "0003");
  		branch.setAttributes("Branch 2", "", "1");
  		branch.setBranchCode(AclfBranchCode.LINE);
		branch.toLine().setZ(new Complex(0.005, 0.01), UnitType.PU, 4000.0);

		/* 
		 * create two Aclf networks as child net 
		 */
		AclfNetwork netSub1 = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(netSub1);
		// set netSub1 as a childNet at bus2, 
		// assumption
		//    - bus2 is a PQ bus
		//    - there is one and only one Swing bus in the child net netSub1, which is linked to bus2 
		//      of the mainNet
		bus2.setChildNetRef(netSub1);
		
		AclfNetwork netSub2 = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(netSub2);
		// set netSub2 as a childNet at bus3
		bus3.setChildNetRef(netSub2);
		
		return mainNet;
	}
}

