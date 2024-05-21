/*
 * @(#) DcSysResultOutput.java   
 *
 * Copyright (C) 2006-2011 www.interpss.com
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
 * @Date 12/02/2010
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.dc.output;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.FileUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dc.DcBranch;
import com.interpss.dc.DcBusCode;
import com.interpss.dc.DcNetwork;
import com.interpss.dc.common.IpssDcSysException;
import com.interpss.dc.pv.PVDcBus;

/**
 * Output DcSytem analysis results
 * 
 * @author mzhou
 *
 */
public class DcSysResultOutput {
	private StringBuffer buffer = null;
	private String filename = null;
	
	public DcSysResultOutput() {
	}
 
	public DcSysResultOutput(StringBuffer buffer) {
		this.buffer = buffer;
	}
	
	public DcSysResultOutput(String filename) {
		this.filename = filename;
	}

	public void visit(DcNetwork dcNet) throws InterpssException {
		if (this.buffer != null)
			this.buffer.append(solarAnalysisReuslt(dcNet));
		else if (this.filename != null)
			FileUtil.writeText2File(this.filename, solarAnalysisReuslt(dcNet).toString());
		else
			System.out.println(solarAnalysisReuslt(dcNet));		
	}
	
	public static StringBuffer solarAnalysisReuslt(DcNetwork dcNet) {
		StringBuffer buffer = new StringBuffer(); 
			
		String str = "\n" +
		 	   "                 DC Solor Power Flow Results\n\n";
		str += "   ===================== Bus Output ================================\n";
		str += "       Bus id         Vdc(Volt)     Pdc(W)      Pac(W)     Qac(Var)\n";
		str += "   =================================================================\n";
		buffer.append(str);
		
		for (Bus b : dcNet.getBusList()) {
			PVDcBus bus = (PVDcBus)b;
			str = String.format("     %-12s    %8.2f    %10.2f", bus.getId(),
						bus.getVoltage(UnitType.Volt), 
						bus.powerInjection(UnitType.Watt));
			
			try {
				if (bus.getCode() == DcBusCode.INVERTER)
					str += String.format("  %10.2f  %10.2f", 
							bus.getInverter().getPac(UnitType.Watt),
							bus.getInverter().getQac(UnitType.Var));
			} catch( IpssDcSysException e) {
				IpssLogger.getLogger().severe(e.toString());
				str += e.toString();
			}
			
			buffer.append(str + "\n");
		}

		str = "\n\n";
		str += "   =================== Branch Output =================\n";
		str += "        from         to              Amp       Loss\n";
		str += "   ===================================================\n";
		buffer.append(str);
		
		for (Branch b : dcNet.getBranchList()) {
			DcBranch branch = (DcBranch)b;
			str = String.format("     %-12s  %-12s  %8.3f   %8.4f", 
						branch.getFromBus().getId(), branch.getToBus().getId(),
						Math.abs(branch.amp_ij(UnitType.Amp)), 
						branch.loss(UnitType.Watt));
			buffer.append(str + "\n");
		}
		return buffer;
	}
}
