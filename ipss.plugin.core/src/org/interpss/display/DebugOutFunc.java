/*
 * @(#)DebugOutFunc.java   
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
 * @Date 10/20/2012
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.display;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.net.Branch;


/**
 * Debug output functions
 * 
 * @author mzhou
 *
 */
public class DebugOutFunc {
	private static final String OffSet_Space = "   ";
	
	/**
	 * retrieve bus and connected branches info, level = 1
	 * 
	 * @param bus
	 * @return
	 */
	public static StringBuffer busConnectivityInfo(AclfBus bus) {
		return busConnectivityInfo(bus, 1, "");
	}
	
	/**
	 * Recursively retrieve bus and connected branches info to the level
	 * 
	 * @param bus
	 * @param level
	 * @return
	 */
	public static StringBuffer busConnectivityInfo(AclfBus bus, int level) {
		return busConnectivityInfo(bus, level, "");
	}

	private static StringBuffer busConnectivityInfo(AclfBus bus, int level, String offSet) {
		StringBuffer buf = new StringBuffer();
	
		buf.append("\n\n" + offSet + "Bus Id: " + bus.getId() + "\n");
		buf.append(offSet + "Bus status: " + bus.isActive() + "\n");
		
		buf.append("\n" + offSet + "Connected Branch info: \n");
		for (Branch bra : bus.getBranchList()) {
			AclfBranch branch = (AclfBranch)bra;
			buf.append(offSet + "  Branch Id : " + branch.getId() + "\n");
			buf.append(offSet + "  Branch status: " + branch.isActive() + "\n");
			buf.append(offSet + "  Branch type: " + branch.getBranchCode() + "\n\n");
			
			if (level > 1)
				buf.append(busConnectivityInfo((AclfBus)branch.getOppositeBus(bus), level-1, offSet + OffSet_Space));
		}

		// display debug info the connected branches
		return buf;
	}


}
