 /*
  * @(#)IpssInternalFormat_out.java   
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.fadapter.impl;

import java.io.BufferedWriter;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adpter.AclfCapacitorBus;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.simu.SimuContext;

public class IpssInternalFormat_out {
	/**
	 * Output InterPSS simulation object model info to a text file in InterPSS internal data format. 
	 * Please note: this data format is a sample for testing purpose. It is not used for in InterPSS simulation engine 
	 * 
	 * @param out
	 * @param simuCtx
	 * @param msg
	 * @return
	 * @throws Exception
	 */
    public static boolean save(final BufferedWriter out, final SimuContext simuCtx, final IPSSMsgHub msg) throws Exception {
    	AclfNetwork net = simuCtx.getAclfNet();
    	
    	// out put network info
    	out.write("AclfNetInfo\n");
        out.write(String.format("%3.2f%n", net.getBaseKva()));
        out.write(String.format("%s%n%n", "end"));
        
        // output bus info
        double baseMva = net.getBaseKva() * 0.001;
        out.write(String.format("%s%n", "BusInfo"));
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus) b;
			out.write(String.format("%8s %10.0f %6.3f %5.1f %7.2f %7.2f %7.2f %7.2f %n", 
					bus.getId(),               // bus id
					bus.getBaseVoltage(),      // bus base voltage
					bus.getVoltageMag(),       // bus voltage in pu
					bus.getVoltageAng(UnitType.Deg), // bus voltage angle in deg
					bus.getGenP()*baseMva,         // bus gen P in MW
					bus.getGenQ()*baseMva,         // bus gen Q in MVar
					bus.getLoadP()*baseMva,        // bus load P in MW
					bus.getLoadQ()*baseMva));      // bus load Q in Mvar
		}
        out.write(String.format("%s%n%n", "end"));

        // out put swing bus info
        out.write(String.format("%s%n", "SwingBusInfo"));
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus) b;
			if (bus.isSwing())
				out.write(String.format("%8s%n", bus.getId()));  // Swing bus id
		}
        out.write(String.format("%s%n%n", "end"));

        // output PV Limit control bus info
        out.write(String.format("%s%n", "PVBusInfo"));
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus) b;
			if (bus.isPVBusLimit()) {
				PVBusLimit pv = bus.getPVBusLimit();
				out.write(String.format("%8s %7.4f %7.2f %7.2f %n", 
							pv.getParentBus().getId(),
							pv.getVSpecified(),
							pv.getQLimit().getMin()*baseMva,
							pv.getQLimit().getMax()*baseMva));
			}
		}
        out.write(String.format("%s%n%n", "end"));

        // output capacitor bus info 
        out.write(String.format("%s%n", "CapacitorBusInfo"));
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus) b;
			if (bus.isCapacitor()) {
				AclfCapacitorBus cap = bus.toCapacitorBus();
				out.write(String.format("%8s %7.2f %n", bus.getId(), cap.getQ())); // capacitor Q in pu
			}
		}
        out.write(String.format("%s%n%n", "end"));

        // output branch info
        out.write(String.format("%s%n", "BranchInfo"));
		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch) b;
			out.write(String.format("%8s %8s %10.5f %10.5f %10.5f%n", 
					branch.getFromBus().getId(), 
					branch.getToBus().getId(),
					branch.getZ().getReal(),
					branch.getZ().getImaginary(),
					branch.getHShuntY().getImaginary()));
		}
        out.write(String.format("%s%n%n", "end"));

        // output Xfr branch info
        out.write(String.format("%s%n", "XformerInfo"));
		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch) b;
			if (branch.isXfr())
				out.write(String.format("%8s %8s %3s   %7.4f %n", 
						branch.getFromBus().getId(), 
						branch.getToBus().getId(),
						branch.getCircuitNumber(),
						branch.getFromTurnRatio()));
		}
        out.write(String.format("%s%n%n", "end"));

        out.write(String.format("%s%n", "EndOfFile"));
         
        return true;
    }
}