/*
 * @(#)DistBranchHelper.java   
 *
 * Copyright (C) 2011 www.interpss.org
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
 * @Date 02/15/2011
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.dist;

import static org.interpss.mapper.odm.base.ODMUnitHelper.toApparentPowerUnit;
import static org.interpss.mapper.odm.base.ODMUnitHelper.toCurrentUnit;
import static org.interpss.mapper.odm.base.ODMUnitHelper.toFactorUnit;
import static org.interpss.mapper.odm.base.ODMUnitHelper.toLengthUnit;
import static org.interpss.mapper.odm.base.ODMUnitHelper.toVoltageUnit;
import static org.interpss.mapper.odm.base.ODMUnitHelper.toYUnit;
import static org.interpss.mapper.odm.base.ODMUnitHelper.toZUnit;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.schema.BreakerDistBranchXmlType;
import org.ieee.odm.schema.FeederDistBranchXmlType;
import org.ieee.odm.schema.ReactorDistBranchXmlType;
import org.ieee.odm.schema.XFormerDistBranchXmlType;
import org.interpss.mapper.odm.base.ODMHelper;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.dist.DistBranch;
import com.interpss.dist.adpter.DistBreaker;
import com.interpss.dist.adpter.DistFeeder;
import com.interpss.dist.adpter.DistReactor;
import com.interpss.dist.adpter.DistXformer;

public class DistBranchHelper {
	private DistBranch branch = null;
	
	public DistBranchHelper(DistBranch branch) {
		this.branch = branch;
	}
	
	public void setFeederBranchData(FeederDistBranchXmlType branchRec) {
		DistFeeder feeder = this.branch.toFeeder();
		double l = 1.0;
		if (branchRec.getLength() != null) {
			l = branchRec.getLength().getValue();
			UnitType lunit = toLengthUnit.apply(branchRec.getLength().getUnit());
			feeder.setLength(l, lunit);
		}
		
		Complex z1 = new Complex(branchRec.getZ1().getRe(), branchRec.getZ1().getIm());
		UnitType zunit = toZUnit.apply(branchRec.getZ1().getUnit());
		Complex z0 = new Complex(0.0, 0.0);
	    if (branchRec.getZ0() != null)
	    	z0 = new Complex(branchRec.getZ0().getRe(), branchRec.getZ0().getIm());
		feeder.setZ(z1.multiply(l), z0.multiply(l), zunit);

		if (branchRec.getShuntY() != null) {
			Complex y1 = new Complex(branchRec.getShuntY().getRe(), branchRec.getShuntY().getIm());
			UnitType yunit = toYUnit.apply(branchRec.getShuntY().getUnit());
    		Complex y0 = new Complex(0.0, 0.0);
    		if (branchRec.getShuntY0() != null)
    			y0 = new Complex(branchRec.getShuntY0().getRe(), branchRec.getShuntY0().getIm());
    		feeder.setShuntY(y1, y0, yunit);
		}
		else
    		feeder.setShuntY(new Complex(0.0,0.0), new Complex(0.0,0.0), UnitType.PU);
	}

	public void setXFormerBranchData(XFormerDistBranchXmlType branchRec) {
		DistXformer xfrObj = this.branch.toXFormer();
		double rating = branchRec.getRating().getValue();
		UnitType runit = toApparentPowerUnit.apply(branchRec.getRating().getUnit());
		xfrObj.setRating(rating, runit);
		
		double priV = branchRec.getPrimaryRatedVoltage().getValue(),
		       senV = branchRec.getSecondaryRatedVoltage().getValue();
		UnitType vunit = UnitType.kV;
		xfrObj.setRatedVoltage(priV, senV, vunit);
		
		Complex z1 = new Complex(branchRec.getZ1().getRe(), branchRec.getZ1().getIm());
		UnitType zunit = toZUnit.apply(branchRec.getZ1().getUnit());
		Complex z0 = new Complex(0.0, 0.0);
		if (branchRec.getZ0() != null)
			z0 = new Complex(branchRec.getZ0().getRe(), branchRec.getZ0().getIm());
		xfrObj.setZ(z1, z0, zunit);
		
		double priRatio = branchRec.getPrimaryTurnRatio().getValue(),
		       senRatio = branchRec.getSecondaryTurnRatio().getValue();
		UnitType tunit = toFactorUnit.apply(branchRec.getPrimaryTurnRatio().getUnit());       
		xfrObj.setTurnRatio(priRatio, senRatio, tunit);
		
		if (branchRec.getPrimaryConnection() != null && branchRec.getSecondaryConnection() != null) {
			XFormerConnectCode priCode = ODMHelper.toXfrConnectCode(branchRec.getPrimaryConnection().getXfrConnection());
			XFormerConnectCode secCode = ODMHelper.toXfrConnectCode(branchRec.getSecondaryConnection().getXfrConnection());
			xfrObj.setConnect(priCode, secCode);
			
			if (branchRec.getPrimaryConnection().getGrounding() != null)
				DistBusHelper.setGroundingData(branchRec.getPrimaryConnection().getGrounding(), 
								xfrObj.getPrimaryGrounding(), xfrObj.getBranch().getFromBus());

			if (branchRec.getSecondaryConnection().getGrounding() != null)
				DistBusHelper.setGroundingData(branchRec.getSecondaryConnection().getGrounding(), 
								xfrObj.getSecondaryGrounding(), xfrObj.getBranch().getToBus());
		}
	}
	
	public void setReactorBranchData(ReactorDistBranchXmlType branchRec) {
		DistReactor reactor = this.branch.toReactor();
		double x = branchRec.getX().getX();
		UnitType xunit = toZUnit.apply(branchRec.getX().getUnit());
		reactor.setX(x, xunit);

		double v = branchRec.getRatedVoltage().getValue();
		UnitType vunit = toVoltageUnit.apply(branchRec.getRatedVoltage().getUnit());
		reactor.setRatedVolt(v, vunit);
		
		double amp = branchRec.getRatedAmp().getValue();
		UnitType ampunit = toCurrentUnit.apply(branchRec.getRatedAmp().getUnit());
		reactor.setRatedAmp(amp, ampunit);
	}
	
	public void setBreakerBranchData(BreakerDistBranchXmlType branchRec) {
		DistBreaker breaker = this.branch.toBreaker();
		double r = branchRec.getR().getR();
		UnitType runit = toZUnit.apply(branchRec.getR().getUnit());
		breaker.setR(r, runit);
	}
	
}
