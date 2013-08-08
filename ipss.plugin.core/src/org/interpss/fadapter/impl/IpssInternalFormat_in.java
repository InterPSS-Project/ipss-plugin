 /*
  * @(#)IpssInternalFormat_in.java   
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

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adpter.AclfCapacitorBus;
import com.interpss.core.aclf.adpter.AclfLine;
import com.interpss.core.aclf.adpter.AclfLoadBus;
import com.interpss.core.aclf.adpter.AclfPQGenBus;
import com.interpss.core.aclf.adpter.AclfPVGenBus;
import com.interpss.core.aclf.adpter.AclfSwingBus;

public class IpssInternalFormat_in {
    public static AclfNetwork loadFile(final java.io.BufferedReader din, final IPSSMsgHub msg) throws Exception {
    	// create a AclfAdjNetwork object to hold the loadflow data
    	final AclfNetwork  adjNet = CoreObjectFactory.createAclfNetwork();
    	adjNet.setAllowParallelBranch(true);
    	
    	// process loadflow data line-by-line
      	String str = din.readLine();
      	if (!str.startsWith("AclfNetInfo")) {
			throw new Exception("The file line in input file is not AclfNetInfo, <" + str + ">");
		}  

      	do {
          	str = din.readLine();   //kvaBase
          	if (!str.startsWith("EndOfFile")) {
            	str = din.readLine();
            	if (str ==  null || str.equals(""))
            		; // do nothing
            	else if (str.startsWith("BusInfoNoBaseV")) {
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadBusInfoNoBaseV(str, adjNet);
                		//msgHub.sendInfoMsg("Bus Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
            	else if (str.startsWith("BusInfo")) {
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadBusInfo(str, adjNet);
                		//msgHub.sendInfoMsg("Bus Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
            	else if (str.startsWith("SwingBusInfo")) {
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadSwingBusInfo(str, adjNet);
                		//msgHub.sendInfoMsg("SwingBus Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
            	else if (str.startsWith("PVBusInfo")) {
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadPVBusInfo(str, adjNet);
                		//msgHub.sendInfoMsg("PVBus Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
            	else if (str.startsWith("PQBusInfo")) {
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadPQBusInfo(str, adjNet);
                		//msgHub.sendInfoMsg("PQBus Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
            	else if (str.startsWith("CapacitorBusInfo")) {
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadCapacitorBusInfo(str, adjNet);
                		//msgHub.sendInfoMsg("Capacitor Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
            	else if (str.startsWith("BranchInfo")){
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadBranchInfo(str, adjNet, msg);
                		//msgHub.sendInfoMsg("Branch Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
            	else if (str.startsWith("XformerInfo")){
              		do {
                		str = din.readLine();
                		if (!str.startsWith("end")) {
							loadXformerInfo(str, adjNet);
                		//msgHub.sendInfoMsg("Xfr Loaded: " + String.valueOf(++cnt));
						}
              		} while (!str.startsWith("end"));
            	}
           	  }
        	} while (str != null && !str.startsWith("EndOfFile"));
      	return adjNet;
    }

    public static void loadBusInfo(final String str, final AclfNetwork net) {
      	// MsgHub.sendInfoMsg("Bus: " + str);
      	final java.util.StringTokenizer st =
         		new java.util.StringTokenizer(str);
      	String id = null;
      	double vBase=0.0, vAct=0.0, ang=0.0, pg=0.0,
             qg=0.0, pl=0.0, ql=0.0;
      	while (st.hasMoreTokens()) {
        	id    = st.nextToken().trim();
        	vBase = new Double(st.nextToken()).doubleValue();
        	vAct  = new Double(st.nextToken()).doubleValue();
        	ang   = new Double(st.nextToken()).doubleValue();
        	pg    = new Double(st.nextToken()).doubleValue();
        	qg    = new Double(st.nextToken()).doubleValue();
        	pl    = new Double(st.nextToken()).doubleValue();
        	if (!st.hasMoreTokens()) {
        		System.out.println("--->" + str);
        	}
        	ql    = new Double(st.nextToken()).doubleValue();
        	if (st.hasMoreTokens()) {
				throw new InterpssRuntimeException("AclfDataFile.loadBusInfo, BusInfo str wrong");
			}
      	}

      	final AclfBus bus = CoreObjectFactory.createAclfBus(id, net);
      	//net.addBus(bus);
    	bus.setBaseVoltage(vBase, UnitType.Volt);
    	bus.setVoltage(vAct, ang);
    	if ( ( pg != 0.0 ) || ( qg != 0.0 ) ) {
    		 bus.setGenCode(AclfGenCode.GEN_PQ);
    		 bus.setLoadCode(AclfLoadCode.CONST_P);
   			 final AclfPQGenBus gen = bus.toPQBus();
    		 gen.setGen(new Complex(pg,qg), UnitType.mVA);
    		 gen.setLoad(new Complex(pl,ql), UnitType.mVA);
    	}
    	else if ( ( pl != 0.0 ) || ( ql != 0.0 ) ) {
    		 bus.setGenCode(AclfGenCode.NON_GEN);
    		 //bus.setGen(new complex(pg,qg), UnitType.mVA, net.getBaseKva());
    		 bus.setLoadCode(AclfLoadCode.CONST_P);
   			 final AclfLoadBus load = bus.toLoadBus();
    		 load.setLoad(new Complex(pl,ql), UnitType.mVA);
    	}
    	else {
    		 bus.setGenCode(AclfGenCode.NON_GEN);
   			 final AclfPQGenBus gen = bus.toPQBus();
    		 gen.setGen(new Complex(0.0,0.0), UnitType.mVA);
    		 bus.setLoadCode(AclfLoadCode.NON_LOAD);
    		 //bus.setLoad(new complex(0.0,0.0), UnitType.mVA, net.getBaseKva());
    	}
    }

    public static void loadBusInfoNoBaseV(final String str, final AclfNetwork net) throws Exception {
      	// MsgHub.sendInfoMsg("Bus: " + str);
      	final java.util.StringTokenizer st = new java.util.StringTokenizer(str);
      	String id = null;
      	double vBase=0.0, vAct=0.0, ang=0.0, pg=0.0,
             qg=0.0, pl=0.0, ql=0.0;
      	while (st.hasMoreTokens()) {
        	id    = st.nextToken().trim();
        	vBase = 10000; //new Double(st.nextToken()).doubleValue();
        	vAct  = new Double(st.nextToken()).doubleValue();
        	ang   = new Double(st.nextToken()).doubleValue();
        	pg    = new Double(st.nextToken()).doubleValue();
        	qg    = new Double(st.nextToken()).doubleValue();
        	pl    = new Double(st.nextToken()).doubleValue();
        	ql    = new Double(st.nextToken()).doubleValue();
        	if (st.hasMoreTokens()) {
				throw new InterpssException("AclfDataFile.loadBusInfoNoBaseV, BusInfo str wrong");
			}
      	}

      	final AclfBus bus = CoreObjectFactory.createAclfBus(id, net);
    	//net.addBus(bus);
     	bus.setBaseVoltage(vBase, UnitType.Volt);
     	bus.setVoltage(vAct, ang);
     	if ( ( pg != 0.0 ) || ( qg != 0.0 ) ) {
    	 	bus.setGenCode(AclfGenCode.GEN_PQ);
    	 	bus.setLoadCode(AclfLoadCode.CONST_P);
  			final AclfPQGenBus gen = bus.toPQBus();
    	 	gen.setGen(new Complex(pg,qg), UnitType.mVA);
    	 	gen.setLoad(new Complex(pl,ql), UnitType.mVA);
     	}
     	else if ( ( pl != 0.0 ) || ( ql != 0.0 ) ) {
    	 	bus.setGenCode(AclfGenCode.NON_GEN);
    	 	//bus.setGen(new complex(pg,qg), UnitType.mVA, net.getBaseKva());
    	 	bus.setLoadCode(AclfLoadCode.CONST_P);
  			final AclfLoadBus load = bus.toLoadBus();
    	 	load.setLoad(new Complex(pl,ql), UnitType.mVA);
     	}
     	else {
    	 	bus.setGenCode(AclfGenCode.GEN_PQ);
  			final AclfPQGenBus gen = bus.toPQBus();
    	 	gen.setGen(new Complex(0.0,0.0), UnitType.mVA);
    	 	bus.setLoadCode(AclfLoadCode.NON_LOAD);
    	 	//bus.setLoad(new complex(0.0,0.0), UnitType.mVA, net.getBaseKva());
     	}
    }

    public static void loadSwingBusInfo(final String str, final AclfNetwork net) {
      	// MsgHub.sendInfoMsg("SwingBus: " + str);
      	final java.util.StringTokenizer st =
         		new java.util.StringTokenizer(str);
      	String id = null;
      	while (st.hasMoreTokens()) {
        	id    = st.nextToken().trim();
        	if (st.hasMoreTokens()) {
				throw new InterpssRuntimeException("AclfDataFile.loadSwingBusInfo_1, SwingBusInfo str wrong");
			}
      	}

      	AclfBus bus = (AclfBus)net.getBus(id);
      	if (bus != null ) {
        	bus.setGenCode(AclfGenCode.SWING);
			final AclfSwingBus swing = bus.toSwingBus();
    		swing.setVoltMag(bus.getVoltageMag(), UnitType.PU);
    		swing.setVoltAng(bus.getVoltageAng(UnitType.Rad), UnitType.Rad);
      	} else {
			throw new InterpssRuntimeException("AclfDataFile.loadSwingBusInfo_2, Swing bus:" + id + " is not in the system" );
		}
    }

    public static void loadPVBusInfo(final String str, final AclfNetwork adjNet) {
      	// MsgHub.sendInfoMsg("PVBus: " + str);
      	final java.util.StringTokenizer st = new java.util.StringTokenizer(str);
      	String id = null;
    	double v=0.0, qmax=0.0, qmin=0.0;
      	while (st.hasMoreTokens()) {
        	id   = st.nextToken().trim();
        	v    = new Double(st.nextToken()).doubleValue();
        	qmin = new Double(st.nextToken()).doubleValue();
        	qmax = new Double(st.nextToken()).doubleValue();
        	if (st.hasMoreTokens()) {
				throw new InterpssRuntimeException("AclfDataFile.loadPVBusInfo_1, PVBusInfo str wrong");
			}
      	}

      	AclfBus bus = adjNet.getBus(id);
    	if (bus != null ) {
        	bus.setGenCode(AclfGenCode.GEN_PV);
      		final PVBusLimit pvLimit = CoreObjectFactory.createPVBusLimit(bus);
      		pvLimit.setVSpecified(v, UnitType.PU);
      		pvLimit.setQLimit(new LimitType(qmax,qmin), UnitType.mVA);
      		pvLimit.setStatus(true);
			final AclfPVGenBus pv = bus.toPVBus();
        	pv.setVoltMag(pvLimit.getVSpecified(UnitType.PU), UnitType.PU);
      	} else {
      		ipssLogger.info(str);
			throw new InterpssRuntimeException("AclfDataFile.loadPVBusInfo_2, PV bus:" + id + " is not in the system" );
		}
    }

    public static void loadPQBusInfo(final String str, final AclfNetwork adjNet) {
        // do nothing. loadBusInfo already done loading info
    }

    public static void loadCapacitorBusInfo(final String str, final AclfNetwork adjNet) {
      	// MsgHub.sendInfoMsg("CapacitorBus: " + str);
      	final java.util.StringTokenizer st = 	new java.util.StringTokenizer(str);
      	String id = null;
		double b=0.0;
      	while (st.hasMoreTokens()) {
        	id   = st.nextToken().trim();
        	b    = new Double(st.nextToken()).doubleValue();
        	if (st.hasMoreTokens()) {
				throw new InterpssRuntimeException("AclfDataFile.loadCapacitorBusInfo_1, CapacitorBusInfo str wrong");
			}
      	}

      	AclfBus bus = adjNet.getBus(id);
    	if (bus != null) {
       	    bus.setGenCode(AclfGenCode.CAPACITOR);
			final AclfCapacitorBus cap = bus.toCapacitorBus();
			cap.setQ(b, UnitType.PU);
    	} else {
			throw new InterpssRuntimeException("AclfDataFile.loadCapacitorBusInfo_2, Capacitor bus:" + id + " is not in the system" );
		}
    }

    public static void loadBranchInfo(final String str, final AclfNetwork net, final IPSSMsgHub msgHub) 
	       throws Exception {
      	// MsgHub.sendInfoMsg("Branch: " + str);
      	final java.util.StringTokenizer st =
         			new java.util.StringTokenizer(str);
      	String fid=null, tid=null;
    	 		double r=0.0, x=0.0, b=0.0;
      	while (st.hasMoreTokens()) {
        	fid = st.nextToken().trim();
        	tid = st.nextToken().trim();
        	r   = new Double(st.nextToken()).doubleValue();
        	x   = new Double(st.nextToken()).doubleValue();
        	b   = new Double(st.nextToken()).doubleValue();
        	if (st.hasMoreTokens()) {
				throw new InterpssException("AclfDataFile.loadBranchInfo_1, BranchInfo str wrong");
			}
      	}

      	AclfBranch bra = CoreObjectFactory.createAclfBranch();
      	net.addBranch(bra, fid, tid);
    	bra.setBranchCode(AclfBranchCode.LINE);
		AclfLine line = bra.toLine();
    	
    	line.getAclfBranch().setZ(new Complex(r,x));
    	line.setHShuntY(new Complex(0.0,Math.abs(b)), UnitType.PU, 1.0); // Unit is PU, no need to enter baseV
    }

    public static void loadXformerInfo(final String str, final AclfNetwork net) {
      	// MsgHub.sendInfoMsg("Xformer: " + str);
      	final java.util.StringTokenizer st =
         		new java.util.StringTokenizer(str);
      	String fid=null, tid=null;
    	String cirNo="1";
      	double t=0.0;
      	while (st.hasMoreTokens()) {
        	fid = st.nextToken().trim();
        	tid = st.nextToken().trim();
        	cirNo = st.nextToken();
        	t     = new Double(st.nextToken()).doubleValue();
        	if (st.hasMoreTokens()) {
				throw new InterpssRuntimeException("AclfDataFile.loadXformerInfo_1, XformerInfo str wrong");
			}
      	}

    	AclfBranch bra = (AclfBranch)net.getBranch(fid, tid, cirNo);
    	if (bra != null) {
    	 	bra.setBranchCode(AclfBranchCode.XFORMER);
        	bra.setFromTurnRatio( t );
        	bra.setToTurnRatio( 1.0 );
      	}
    	else {
    		bra = (AclfBranch)net.getBranch(tid, fid, cirNo);
        	if (bra != null) {
          	bra.setBranchCode(AclfBranchCode.XFORMER);
    			bra.setFromTurnRatio(1.0);
    			bra.setToTurnRatio(t);
        	} else {
				throw new InterpssRuntimeException("AclfDataFile.loadXformerInfo_1, Xformar branch:" + fid + "->" + tid + " is not in the system" );
			}
       }
    }
}