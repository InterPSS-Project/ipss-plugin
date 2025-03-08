package org.interpss.plugin.QA.util;

import static org.interpss.CorePluginFunction.BusLfResultBusStyle;

import org.apache.commons.math3.complex.Complex;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.net.Branch;


public abstract class QAOutFunc {
	public static StringBuffer busInfo(String busId, AclfNetwork net) {
		StringBuffer buf = new StringBuffer();
		AclfBus bus = net.getBus(busId);
		Complex  mis = bus.mismatch(AclfMethodType.NR);		
		buf.append("largest mismatch: " + mis.abs() + 
				"  @" + bus.getId() + "\n" + 
		                    "\nBus LF info: \n\n" + BusLfResultBusStyle.f(net, bus));
		
		// display debug info of the bus and connected buses and branches
		buf.append("\nBus/Branch debug info: \n\n" + bus.toString(net.getBaseKva()));
		for (Branch b : bus.getBranchList()) {
			AclfBranch bra = (AclfBranch)b;
			buf.append("\n\n" + bra.getOppositeBus(bus).get().toString(net.getBaseKva()));
			buf.append("\n\n" + bra.toString(net.getBaseKva()));
		}

		// display debug info the connected branches
		return buf;
	}
}
