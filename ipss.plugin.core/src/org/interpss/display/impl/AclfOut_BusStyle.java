/*
 * @(#)AclfOutFunc.java   
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

package org.interpss.display.impl;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.dc.DcPluginFunction.OutputSolarNet;
import static org.interpss.CorePluginFunction.FormatKVStr;
import static org.interpss.CorePluginFunction.OutputBusId;

import org.apache.commons.math3.complex.Complex;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfCapacitorBus;
import com.interpss.core.aclf.adpter.AclfGenBus;
import com.interpss.core.aclf.adpter.AclfPSXformer;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;
import com.interpss.dc.DcNetwork;
import com.interpss.dist.DistNetwork;

/**
 * Aclf output functions, IEEE Bus Style
 * 
 * @author mzhou
 *
 */
public class AclfOut_BusStyle {
	public static StringBuffer busResult(AclfNetwork net, AclfBus bus) {
		StringBuffer str = new StringBuffer("");
		str.append(title());
		str.append(lfResultsBusStyle(bus, net, AclfOutFunc.BusIdStyle.BusId_No));
		return str;
	}
	
	/**
	 * output LF result in the bus style
	 * 
	 * @param mainNet
	 * @param style
	 * @return
	 */
	public static StringBuffer lfResultsBusStyle(AclfNetwork mainNet, AclfOutFunc.BusIdStyle style) {
		StringBuffer str = new StringBuffer("");
		try {
			str.append(busStyleTitle(mainNet));

			for (AclfBus bus : mainNet.getBusList()) {
				if (bus.isActive()) {
					str.append(lfResultsBusStyle(bus, mainNet, style));
				}
			}
			str.append("------------------------------------------------------------------------------------------------------------------------------------------\n");
		} catch (Exception emsg) {
			str.append(emsg.toString());
		}
		
		if (mainNet.isContainChildNet()) {
		  	for (Network n : mainNet.getChildNetworks()) {
	  			AclfNetwork childNet = null;
		  		if (n instanceof AclfNetwork) {
		  			childNet = (AclfNetwork)n;
		  		}
		  		if (n instanceof DistNetwork) {
		  			childNet = ((DistNetwork)n).getAclfNet();
		  		}
				str.append("\n\n                                                    *   *   *   *   *   *\n");
		  		str.append("\n\nChildNet : " + n.getId() + "\n");
		  		str.append("Parent net [" + mainNet.getId() + "] interface bus Id: " + n.getParentNetInterfaceBusId() + "\n");
		  		str.append(lfResultsBusStyle(childNet, style));
		  	}
		  	
		  	for (Network childNet : mainNet.getChildNetworks()) {
		  		if (childNet.isContainChildNet()) {
				  	for (Object obj : childNet.getChildNetworks()) {
				  		if (obj instanceof DcNetwork) {
				  			DcNetwork childNet3rd = (DcNetwork)obj;
							str.append("\n\n                                                    *   *   *   *   *   *\n");
					  		str.append("\n\nChildNet : " + childNet3rd.getId() + "\n");
					  		str.append("Parent net [" + childNet.getId() + "] interface bus Id: " + childNet3rd.getParentNetInterfaceBusId() + "\n");
				  			try {
				  			   str.append(OutputSolarNet.fx((DcNetwork)childNet3rd));
				  			} catch (InterpssException e) {
				  				ipssLogger.severe(e.toString());
				  				str.append(e.toString());
				  			}
				  		}
				  	}		  			
		  		}
		  	}		  	
		}
		
		return str;
	}
	

	private static StringBuffer lfResultsBusStyle(AclfBus bus, AclfNetwork net, AclfOutFunc.BusIdStyle style) {
		double baseKVA = net.getBaseKva();
		StringBuffer str = new StringBuffer("");

		AclfGenBus genBus = bus.toGenBus();
		Complex busGen = genBus.getGenResults(UnitType.mVA);
		Complex busLoad = genBus.getLoadResults(UnitType.mVA);
		if (bus.isCapacitor()) {
			AclfCapacitorBus cap = bus.toCapacitorBus();
			busGen = busGen.add(new Complex(0.0, cap.getQResults(UnitType.PU)));
		}
		String id = style == AclfOutFunc.BusIdStyle.BusId_No?
				OutputBusId.f(bus, net.getOriginalDataFormat()):
				bus.getName().trim();
		str.append(Number2String.toStr(-12, id) + " ");
		str.append(String.format(" %s ", FormatKVStr.f(bus.getBaseVoltage()*0.001)));
		str.append(Number2String.toStr("0.0000", bus.getVoltageMag(UnitType.PU)) + " ");
		str.append(Number2String.toStr("##0.0", bus.getVoltageAng(UnitType.Deg)) + " ");
		str.append(Number2String.toStr("####0.00", busGen.getReal()) + " ");
		str.append(Number2String.toStr("####0.00", busGen.getImaginary()) + " ");
		str.append(Number2String.toStr("####0.00", busLoad.getReal()) + " ");
		str.append(Number2String.toStr("####0.00", busLoad.getImaginary()) + " ");
		// str.append( " - - - - - - - - - - -\n" );

		int cnt = 0;
		for (Branch br : bus.getBranchList()) {
			AclfBranch bra = (AclfBranch) br;
			if (bra.isActive()) {

//				final AclfBus busj = bus.equals(bra.getFromAclfBus())?
//					bra.getToAclfBus() : bra.getFromAclfBus();

				Complex pq = new Complex(0.0, 0.0);
				double amp = 0.0, fromRatio = 1.0, toRatio = 1.0, fromAng = 0.0, toAng = 0.0;
				AclfBus toBus = null;
				if (bra.isActive()) {
					if (bus.getId().equals(bra.getFromAclfBus().getId())) {
						toBus = bra.getToAclfBus();
						pq = bra.powerFrom2To(UnitType.mVA);
						amp = UnitHelper.iConversion(bra.current(UnitType.PU), bra.getFromAclfBus().getBaseVoltage(),
								baseKVA, UnitType.PU, UnitType.Amp);
						if (bra.isXfr() || bra.isPSXfr()) {
							fromRatio = bra.getFromTurnRatio();
							toRatio = bra.getToTurnRatio();
							if (bra.isPSXfr()) {
								AclfPSXformer psXfr = bra.toPSXfr();
								fromAng = psXfr.getFromAngle(UnitType.Deg);
								toAng = psXfr.getToAngle(UnitType.Deg);
							}
						}
					} else {
						toBus = bra.getFromAclfBus();
						pq = bra.powerTo2From(UnitType.mVA);
						amp = UnitHelper.iConversion(bra.current(UnitType.PU), bra.getToAclfBus().getBaseVoltage(),
								baseKVA, UnitType.PU, UnitType.Amp);
						if (bra.isXfr() || bra.isPSXfr()) {
							toRatio = bra.getFromTurnRatio();
							fromRatio = bra.getToTurnRatio();
							if (bra.isPSXfr()) {
								AclfPSXformer psXfr = bra.toPSXfr();
								toAng = psXfr.getFromAngle(UnitType.Deg);
								fromAng = psXfr.getToAngle(UnitType.Deg);
							}
						}
					}
				}
				if (cnt++ > 0)
					str.append(Number2String.toStr(67, " ")	+ "    ");
				id = style == AclfOutFunc.BusIdStyle.BusId_No?
						OutputBusId.f(toBus, net.getOriginalDataFormat()):
						toBus.getName().trim();
				str.append(" " + Number2String.toStr(-12, id) + " ");
				str.append(Number2String.toStr("####0.00", pq.getReal()) + " ");
				str.append(Number2String.toStr("####0.00", pq.getImaginary()) + " ");
				str.append(Number2String.toStr("##0.0##", 0.001 * amp) + " ");
				if (bra.isXfr() || bra.isPSXfr()) {
					if (fromRatio != 1.0)
						str.append(Number2String.toStr("0.0###", fromRatio) + " ");
					else
						str.append("       ");

					if (toRatio != 1.0)
						str.append(Number2String.toStr("0.0###", toRatio));
					else
						str.append("      ");

					if (bra.isPSXfr()) {
						if (fromAng != 0.0)
							str.append("   " + Number2String .toStr("##0.0", fromAng));
						else
							str.append("        ");

						if (toAng != 0.0)
							str.append(" " + Number2String.toStr("##0.0", toAng));
						else
							str.append("      ");
					}
					str.append("\n");
				} else {
					str.append("\n");
				}
			}
		}
		return str;
	}
	
	private static StringBuffer busStyleTitle(AclfNetwork net) {
		StringBuffer str = new StringBuffer("");
		
		if (net.isContainChildNet()) 
			str.append("\n Main network: " + net.getId() + "\n");
		
		str.append("\n\n                                              Load Flow Results\n\n");
		str.append(AclfOutFunc.maxMismatchToString(net,"                    ") + "\n");
		str.append(title());
		return str;
	}
	
	private static StringBuffer title() {
		StringBuffer str = new StringBuffer("");
		str.append("------------------------------------------------------------------------------------------------------------------------------------------\n");
		str.append(" Bus ID             Bus Voltage         Generation           Load             To             Branch P+jQ          Xfr Ratio   PS-Xfr Ang\n");
		str.append("              baseKV    Mag   Ang     (mW)    (mVar)    (mW)    (mVar)      Bus ID      (mW)    (mVar)   (kA)   (From)  (To) (from)   (to)\n");
		str.append("------------------------------------------------------------------------------------------------------------------------------------------\n");
		return str;
	}
}