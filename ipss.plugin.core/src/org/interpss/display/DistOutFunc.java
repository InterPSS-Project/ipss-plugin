/*
 * @(#)DistOutFunc.java   
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
 * @Date 11/27/2007
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.display;

import com.interpss.core.net.Bus;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistNetwork;
import com.interpss.dist.adpter.DistBusAdapter;

/**
 * Distribution system output functions
 * 
 * @author mzhou
 *
 */
public class DistOutFunc {
	/**
	 * output distribution system LF results in the summary format
	 * 
	 * @param distNet
	 * @return
	 */
	public static StringBuffer lfSummary(DistNetwork distNet) {
		StringBuffer str = new StringBuffer("");

		for (int i = 0; i < distNet.getLoadScheduleData().getSchedulePoints(); i++) {
			str.append("\n\n                      ==========================");
			str.append(  "\n                       Load Schedule Point: " + (i + 1));
			str.append(  "\n                      ==========================");

			// set bus voltage
			distNet.setPointAclfNetData(i);
			for (Bus b : distNet.getBusList()) {
				DistBus distBus = (DistBus) b;
				DistBusAdapter aBusApt = (DistBusAdapter) distBus
						.getAdapter(DistBusAdapter.class);
				distBus.getAcscBus().setVoltage(aBusApt.getPointVoltage(i));
			}

			str.append(AclfOutFunc.loadFlowSummary(distNet.getAclfNet()));
		}

		return str;
	}
}
