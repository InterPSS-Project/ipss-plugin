 /*
  * @(#)BaseTestSetup.java   
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

package org.interpss;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.ODMLogger;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.BeforeClass;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;

public class CorePluginTestSetup {
	protected static IPSSMsgHub msg;

	@BeforeClass  
	public static void initTestEnv() {
		IpssCorePlugin.init();
		msg = CoreCommonFactory.getIpssMsgHub();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		ODMLogger.getLogger().setLevel(Level.WARNING);
	}

  public static DStabilityNetwork create2BusSystem() throws InterpssException{
		
		DStabilityNetwork net = DStabObjectFactory.createDStabilityNetwork();
		net.setFrequency(60.0);
		
		// First bus is PQ Gen bus
		DStabBus bus1 = (DStabBus) DStabObjectFactory.createDStabBus("Bus1", net).get();
		bus1.setName("Gen Bus");
		bus1.setBaseVoltage(1000);
		//bus1.setGenCode(AclfGenCode.GEN_PQ);
		bus1.setLoadCode(AclfLoadCode.CONST_P);

		bus1.setLoadP(0.8);
		bus1.setLoadQ(0.2);
		
		// Second bus is a Swing bus
		DStabBus bus2 = (DStabBus) DStabObjectFactory.createDStabBus("Swing", net).get();
		bus2.setName("Swing Bus");
		bus2.setBaseVoltage(1000);
		bus2.setGenCode(AclfGenCode.SWING);
		AclfSwingBusAdapter swing = bus2.toSwingBus();
		//swing.setDesiredVoltMag(1.06, UnitType.PU);
		//swing.setDesiredVoltAng(0, UnitType.Deg);
		
		DStabGen gen = DStabObjectFactory.createDStabGen("G1");
		//gen.setGen(new Complex(0.8,0.6));
		gen.setSourceZ(new Complex(0,0.2));
		bus2.getContributeGenList().add(gen);
		gen.setDesiredVoltMag(1.02);
		
		EConstMachine mach = (EConstMachine)DStabObjectFactory.
				createMachine("MachId", "MachName", MachineModelType.ECONSTANT, net, "Swing", "G1");
		//DStabBus bus = net.getDStabBus("Gen");
		mach.setRating(100000, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.calMultiFactors();
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setXd1(0.2);

		// a line branch connect the two buses
		DStabBranch branch = DStabObjectFactory.createDStabBranch("Bus1", "Swing", net);
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.05));
		
		//set positive info only
		net.setPositiveSeqDataOnly(true);
		net.setLfDataLoaded(true);

	  	net.initContributeGenLoad(false);

		// run load flow
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.loadflow();
	  	
	  	// uncommet this line to see the net object states
  		//System.out.println(net.net2String());
	  	
		return net;
	}
}

