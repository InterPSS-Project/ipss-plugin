 /*
  * @(#)IpssAcsc.java   
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.simu.net;

import static com.interpss.core.funcImpl.AcscFunction.acscPSXfrAptr;
import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static com.interpss.core.funcImpl.AcscFunction.str2ScGroundCode;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.simu.BaseDSL;

import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;

/**
 * a wrapper of AcscNetwork for defining network parameters
 *
	 * Add a contributing bus to the network object
	 * 
		addAcscBus(busId, name)
				.baseVoltage(baseVolt, UnitType.Volt)
				.scCode(BusScCode.CONTRIBUTE)
				.z(new Complex(r1, x1), SequenceCode.POSITIVE, zUnit)
				.z(new Complex(r2, x2), SequenceCode.NEGATIVE, zUnit)
				.z(new Complex(r0, x0), SequenceCode.ZERO, zUnit)
				.groundCode(gCode)
				.groundZ(new Complex(rg,xg), gzUnit);
				
	 * Add a non-contributing bus to the network object
		addAcscBus(id, name)
				.baseVoltage(baseV, UnitType.Volt)
				.scCode(BusScCode.NON_CONTRI);
				
	 * Add a line branch to the network object

		addAcscBranch(fromBusId, toBusId)
				.branchCode(AclfBranchCode.LINE)
				.z(new Complex(r1,x1), zUnit)
				.z0(new Complex(r0,x0), zUnit);
				
	 * Add a xfr branch to the network object
	 * 
		addAcscBranch(fromBusId, toBusId)
				.branchCode(AclfBranchCode.XFORMER)
				.z(new Complex(r1,x1), zUnit)
				.z0(new Complex(r0,x0), zUnit)
				.fromGrounding(fromConCode, new Complex(fromRg,fromXg), zgUnit)
				.toGrounding(toConCode, new Complex(toRg,toXg), zgUnit);
				
	 * Add a PSXfr branch to the network object
	 * 
		addAcscBranch(fromBusId, toBusId)
				.branchCode(AclfBranchCode.XFORMER)
				.z(new Complex(r1,x1), zUnit)
				.z0(new Complex(r0,x0), zUnit)
				.shiftAngle(fromAng, toAng, angUnit)
				.fromGrounding(fromConCode, new Complex(fromRg,fromXg), zgUnit)
				.toGrounding(toConCode, new Complex(toRg,toXg), zgUnit);
				
 */

public class IpssAcscNet extends BaseDSL {
	/*
	 *   AcscNetwork and SimpleFaultNetwork creation
	 */
	public static AcscNetworkDSL createAcscNetwork(String id) {
		return new AcscNetworkDSL(id);
	}
	
	public static class AcscNetworkDSL extends IpssAclfNet.AclfBaseNetDSL<BaseAcscNetwork<?, ?>> {
		public AcscNetworkDSL(String id) {
			super(id, CoreObjectFactory.createAcscNetwork());
		}
		public BaseAcscNetwork<?, ?> getAcscNet() {return this.net; }
		public BaseAcscNetwork<?, ?> getFaultNet() {return this.net; }

		public AcscBusDSL addAcscBus(String busId, String busName) throws InterpssException {
			return new AcscBusDSL(busId, busName, getAcscNet()); }	
		public AcscBranchDSL addAcscBranch(String fromBusId, String toBusId) throws InterpssException {
			return new AcscBranchDSL(fromBusId, toBusId, getAcscNet()); }
	}
	
	/*
	 * 	Add Acsc Bus
	 */
	public static AcscBusDSL addAcscBus(String busId, String busName, AcscNetwork net) throws InterpssException {
		return new AcscBusDSL(busId, busName, net);
	}	
	
	public static class AcscBusDSL extends IpssAclfNet.AclfBusBaseDSL<AcscBus, BaseAcscNetwork<?, ?>, AcscBusDSL>{
		public AcscBusDSL(String busId, String busName, BaseAcscNetwork<?, ?> net) throws InterpssException {
			super(busId, busName, CoreObjectFactory.createAcscBus(busId, null).get(), net);
		}

  		public AcscBusDSL setScCode(BusScCode code) { 
  							getAcscBus().setScCode(code);
  							if (code == BusScCode.NON_CONTRI) {
  								getAcscBus().setScGenZ(new Complex(0.0, 1.0e10), SequenceCode.POSITIVE);
  								getAcscBus().setScGenZ(new Complex(0.0, 1.0e10), SequenceCode.NEGATIVE);
  								getAcscBus().setScGenZ(new Complex(0.0, 1.0e10), SequenceCode.ZERO);  								
  							} return this;}
  		public AcscBusDSL setZ(Complex z, SequenceCode seq, UnitType unit) { getAcscBus().setScGenZ(z, seq, unit); return this;}
  		public AcscBusDSL setGroundCode(String code) { getAcscBus().getGrounding().setGroundCode(str2ScGroundCode.apply(code)); return this;}
  		public AcscBusDSL setGroundZ(Complex z, UnitType unit) { getAcscBus().getGrounding().setZ(z, unit, getAcscBus().getBaseVoltage(), getAcscNet().getBaseKva()); return this;}

  		public AcscBusDSL scCode(BusScCode code) { return setScCode(code); } 
  		public AcscBusDSL z(Complex z, SequenceCode seq, UnitType unit) { return setZ(z, seq, unit); }
  		public AcscBusDSL groundCode(String code) { return setGroundCode(code);}
  		public AcscBusDSL groundZ(Complex z, UnitType unit){ return setGroundZ(z, unit);}

		public AcscBus getAcscBus() { return (AcscBus)getObject(); }
  		public AcscNetwork getAcscNet() { return (AcscNetwork)getNet(); }
	}
	
	/*
	 *   Add Acsc Branch
	 */
	public static AcscBranchDSL addAcscBranch(String fromBusId, String toBusId, AcscNetwork net) throws InterpssException {
		return new AcscBranchDSL(fromBusId, toBusId, net);
	}
	
	public static class AcscBranchDSL extends IpssAclfNet.AclfBranchBaseDSL<AcscBranch, BaseAcscNetwork<?, ?>, AcscBranchDSL>{
		public AcscBranchDSL() {
		}
		
		public AcscBranchDSL(String fromBusId, String toBusId, BaseAcscNetwork<?, ?> net) throws InterpssException {
			this(fromBusId, toBusId, "1", net);
		}
		public AcscBranchDSL(String fromBusId, String toBusId, String cirId, BaseAcscNetwork<?, ?> net) throws InterpssException {
			//super(fromBusId, toBusId, cirId, CoreObjectFactory.createAcscBranch(), net);
			this.net = net;
			AcscBranch branch = CoreObjectFactory.createAcscBranch();
			branch.setCircuitNumber(cirId);
			setObject(branch);
			getAcscNet().addBranch(branch, fromBusId, toBusId);
		}
		
		public AcscBranchDSL z0(Complex z, UnitType unit) { return setZ0(z, unit); }
		public AcscBranchDSL fromGrounding(XfrConnectCode code, Complex z, UnitType unit) { return setFromGrounding(code, z, unit); }
		public AcscBranchDSL fromGrounding(XfrConnectCode code) { return setFromGrounding(code, new Complex(0.0,0.0), UnitType.PU); }
		public AcscBranchDSL toGrounding(XfrConnectCode code, Complex z, UnitType unit) { return setToGrounding(code, z, unit); }
		public AcscBranchDSL toGrounding(XfrConnectCode code) { return setToGrounding(code, new Complex(0.0,0.0), UnitType.PU); }

		public AcscBranchDSL setZ0(Complex z, UnitType unit) {
			double baseV = getAcscBranch().getFromBus().getBaseVoltage(); 
			if (getAclfBranch().getBranchCode() == AclfBranchCode.XFORMER || 
				getAclfBranch().getBranchCode() == AclfBranchCode.PS_XFORMER) 
				baseV = getAclfBranch().getFromAclfBus().getBaseVoltage() > getAclfBranch().getToAclfBus().getBaseVoltage() ?
						getAclfBranch().getFromAclfBus().getBaseVoltage() : getAclfBranch().getToAclfBus().getBaseVoltage();
			getAcscBranch().setZ0( UnitHelper.zConversion(z, baseV,
						getAcscNet().getBaseKva(), unit, UnitType.PU)); return this; };
		public AcscBranchDSL setFromGrounding(XfrConnectCode code, Complex z, UnitType unit) { 
								AcscXformerAdapter xfr = acscXfrAptr.apply(getAcscBranch());
								xfr.setFromConnectGroundZ(code, z, unit);
								return this; };
		public AcscBranchDSL setToGrounding(XfrConnectCode code, Complex z, UnitType unit) { 
								AcscXformerAdapter xfr = acscPSXfrAptr.apply(getAcscBranch());
								xfr.setToConnectGroundZ(code, z, unit);
								return this; };
								
		public AcscBranch getAcscBranch() { return (AcscBranch)getObject(); }
  		public AcscNetwork getAcscNet() { return (AcscNetwork)getNet(); }
	}
}
