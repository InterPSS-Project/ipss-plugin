/*
 * @(#)TextSimuOutputHandler.java   
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

package org.interpss.dstab.output;

import java.util.Hashtable;

import com.interpss.common.msg.IpssMessage;
import com.interpss.dstab.datatype.DStabSimuEvent;
import com.interpss.dstab.util.AbstractSimuOutputHandler;

public class TextSimuOutputHandler extends AbstractSimuOutputHandler {
	private boolean titlePrinted = false;
	
	public TextSimuOutputHandler() {
	}

	@Override
	public void onMsgEvent(IpssMessage event) {
		if (!this.titlePrinted) {
			this.titlePrinted = true;
			System.out.println(DStabOutFunc.getStateTitleStr());
		}
		
		// Plot step outout message processing
		DStabSimuEvent e = (DStabSimuEvent) event;
		if (e.getType() == DStabSimuEvent.PlotStepMachineStates) {
			Hashtable<String, Object> machStates = e.getHashtableData();
			try {
				System.out.print(DStabOutFunc.getStateStr(machStates));
			} catch (Exception exp) {
				exp.printStackTrace();
			}
		} 
	}

	@Override
	public boolean onMsgEventStatus(IpssMessage e) {
		onMsgEvent(e);
		return true;
	}
}