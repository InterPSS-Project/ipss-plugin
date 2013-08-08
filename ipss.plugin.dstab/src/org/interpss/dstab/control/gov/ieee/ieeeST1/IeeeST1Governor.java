 /*
  * @(#)IeeeST1Governor.java   
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

package org.interpss.dstab.control.gov.ieee.ieeeST1;

import org.interpss.numeric.datatype.LimitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.common.util.IpssLogger;
import com.interpss.common.util.XmlBeanUtil;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

public class IeeeST1Governor extends AbstractGovernor {
	// state variables
	private double statePm = 0.0, statePref = 0.0, stateX1 = 0.0, stateX2 = 0.0, stateX3 = 0.0, stateX4 = 0.0;
	private LimitType limit = null;
	
	// UI Editor panel
	private static final NBIeeeST1GovernorEditPanel _editPanel = new NBIeeeST1GovernorEditPanel();

	/**
	 * Default Constructor
	 *
	 */
	/*
	public IeeeST1Governor() {
        this.setName("InterPSS IEEEST1");
        this.setCategory("InterPSS");
	}
	*/
	/**
	 * Constructor
	 * 
	 * @param id governor id
	 * @param name governor name
	 */	
	public IeeeST1Governor(final String id, final String name, final String caty) {
		super(id, name, caty);
		// _data is defined in the parent class. However init it here is a MUST
		_data = new IeeeST1GovernorData();
	}
	
	/**
	 * Get the governor data 
	 * 
	 * @return the data object
	 */
	public IeeeST1GovernorData getData() {
		return (IeeeST1GovernorData)_data;
	}
	
	/**
	 * Set controller parameters
	 * 
	 * @param xmlString controller parameter xml string
	 */
	@Override
	public void setDataXmlString(final String xmlString) {
		super.setDataXmlString(xmlString);
		_data = XmlBeanUtil.toObject(xmlString, IeeeST1GovernorData.class);
	}
	
	/**
	 *  Init the controller states
	 *  
	 *  @param msg the SessionMsg object
	 */
	@Override
	public boolean initStates(DStabBus abus, Machine mach) {
		limit = new LimitType(getData().getPmax(), getData().getPmin());
		statePref = getMachine().getPm();
        if (limit.isViolated(statePref)) {
        	IpssLogger.getLogger().severe("Machine initial mechanical power Pm0 violates its governor power limits, " +
        			"machine id: " + getMachine().getId());
        }
		stateX1 = 0.0;
		stateX2 = statePref;
		stateX3 = stateX2;
		stateX4 = (1.0 - getData().getFp()) * stateX3;
		IpssLogger.getLogger().fine("Governor Limit:      " + limit);
		return true;
	}

	/**
	 * Perform one step d-eqn calculation
	 *  
	 * @param dt simulation time interval
	 * @param method d-eqn solution method
	 *  @param msg the SessionMsg object
	 */	
	@Override
	public boolean nextStep(final double dt, final DynamicSimuMethod method, Machine mach) {
		if (method == DynamicSimuMethod.MODIFIED_EULER) {
			/*
			 *     Step-1 : x(1) = x(0) + dx_dt(1) * dt
			 *     Step-2 : x(2) = x(0) + 0.5 * (dx_dt(2) + dx_dt(1)) * dt
			 */
			final double dX1_dt = cal_dX1_dt(stateX1);
			final double dX2_dt = cal_dX2_dt(stateX1, stateX2);
			final double dX3_dt = cal_dX3_dt(stateX2, stateX3);
			final double dX4_dt = cal_dX4_dt(stateX3, stateX4);

			IpssLogger.getLogger().fine("dX1_dt, dX2_dt, dX3_dt, dX4_dt: " + dX1_dt + ", " + 
					dX2_dt + ", " + dX3_dt + ", " + dX4_dt);
			
			final double x1_1 = stateX1 + dX1_dt * dt;
			final double x2_1 = limit.limit(stateX2 + dX2_dt * dt);
			final double x3_1 = stateX3 + dX3_dt * dt;
			final double x4_1 = stateX4 + dX4_dt * dt;
			stateX1 = stateX1 + 0.5 * (cal_dX1_dt(x1_1) + dX1_dt) * dt;
			stateX2 = limit.limit(stateX2 + 0.5 * (cal_dX2_dt(x1_1,x2_1) + dX2_dt) * dt);
			stateX3 = stateX3 + 0.5 * (cal_dX3_dt(x2_1,x3_1) + dX3_dt) * dt;
			stateX4 = stateX4 + 0.5 * (cal_dX4_dt(x3_1,x4_1) + dX4_dt) * dt;

			IpssLogger.getLogger().fine("stateX1, stateX2, stateX3, stateX4: " + stateX1 + ", " + 
					stateX2 + ", " + stateX3 + ", " + stateX4);
			return true;
		}
		else if (method == DynamicSimuMethod.RUNGE_KUTTA) {
			// TODO: TBImpl
			return false;
		} else {
			throw new InterpssRuntimeException("SimpleGovernor.nextStep(), invalid method");
		}
	}	
	
	private double cal_dX1_dt(final double x1) {
		return ( 100.0*(getMachine().getSpeed() - 1.0)/getData().getR()  - x1 ) / getData().getT1();
	}
	
	private double cal_dX2_dt(final double x1, final double x2) {
		double p = getData().getOptMode() == AbstractGovernor.DroopMode? statePref : statePm;
		return ( p - x1 - x2 ) / getData().getT2();
	}

	private double cal_dX3_dt(final double x2, final double x3) {
		return ( x2 - x3 ) / getData().getT3();
	}

	private double cal_dX4_dt(final double x3, final double x4) {
		return ( x3 * ( 1.0 - getData().getFp()) - x4 ) / getData().getT4();
	}

	/**
	 * Get the controller output
	 * 
	 * @return the output
	 */	
	@Override
	public double getOutput(Machine mach) {
		IpssLogger.getLogger().fine("Pm: " + (stateX3 * getData().getFp() + stateX4));
		return stateX3 * getData().getFp() + stateX4;
	}

	/**
	 * Get the editor panel for controller data editing
	 * 
	 * @return the editor panel object
	 */	
	@Override
	public Object getEditPanel() {
		_editPanel.init(this);
		return _editPanel;
	}
	
	@Override
	public void setRefPoint(double x) {
		statePref = x;
	}	
} // SimpleExcAdapter
