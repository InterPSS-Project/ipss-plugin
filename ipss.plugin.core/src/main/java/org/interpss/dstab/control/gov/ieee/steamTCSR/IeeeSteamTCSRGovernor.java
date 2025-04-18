 /*
  * @(#)IeeeSteamTCSRGovernor.java   
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

package org.interpss.dstab.control.gov.ieee.steamTCSR;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateGovernor;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

@AnController(
		   input="mach.speed - 1.0",
		   output="this.fhp*this.chDelayBlock.y + this.fip*this.rhDelayBlock.y + this.flp*this.coDelayBlock.y",
		   refPoint="this.gainBlock.u0 + this.filterBlock.y + this.intBlock.y",
		   display= {})
public class IeeeSteamTCSRGovernor extends AnnotateGovernor {
    public double fhp = 0.2, fip = 0.3, flp = 0.5;

	public double k = 1.0, t1 = 0.5, t2 = 0.1;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="mach.speed - 1.0",
            parameter={"type.NoLimit", "this.k", "this.t2", "this.t1"},
            y0 = "this.refPoint - this.gainBlock.y - this.intBlock.y"  )
    FilterControlBlock filterBlock;
	
    public double k3 = 1.0 /* 1.0/t3 */, pup = 1.2, pdown = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.StaticBlock,
            input="this.refPoint - this.filterBlock.y - this.intBlock.y",
            parameter={"type.Limit", "this.k3", "this.pup", "this.pdown"},
            y0="this.intBlock.u0"	)
    GainBlock gainBlock;

    public double kint = 1.0, pmax = 10.0, pmin = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.gainBlock.y",
            parameter={"type.Limit", "this.kint", "this.pmax", "this.pmin"},
            y0="this.chDelayBlock.u0"	)
    IntegrationControlBlock intBlock;

    public double kch = 1.0, tch = 1.2;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.intBlock.y",
            parameter={"type.NoLimit", "this.kch", "this.tch"},
            y0="this.rhDelayBlock.u0"	)
    DelayControlBlock chDelayBlock;

    public double krh = 1.0, trh = 1.2;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.chDelayBlock.y",
            parameter={"type.NoLimit", "this.krh", "this.trh"},
            y0="this.coDelayBlock.u0"	)
    DelayControlBlock rhDelayBlock;

    public double kco = 1.0, /**/ tco = 1.2, factor = 1.0 / (fhp+fip+flp);
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.rhDelayBlock.y",
            parameter={"type.NoLimit", "this.kco", "this.tco"},
            y0="this.factor*mach.pm"	)
    DelayControlBlock coDelayBlock;

    // UI Editor panel
//    private static NBIeeeSteamTCSREditPanel _editPanel = new NBIeeeSteamTCSREditPanel();
    
    /**
     * Default Constructor
     *
     */
    public IeeeSteamTCSRGovernor() {
        this.setName("ieeeSteamTCSRGovernor");
        this.setCategory("IEEE");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public IeeeSteamTCSRGovernor(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new IeeeSteamTCSRGovernorData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public IeeeSteamTCSRGovernorData getData() {
        return (IeeeSteamTCSRGovernorData)_data;
    }
    
    /**
     *  Init the controller states
     *
     *  @param msg the SessionMsg object
     */
    @Override
	public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
        this.k = getData().getK();
        this.t1 = getData().getT1();
        this.t2 = getData().getT2();
        this.k3 = 1.0/getData().getT3();
        this.pmax = getData().getPmax();
        this.pmin = getData().getPmin();
        this.pup = getData().getPup();
        this.pdown = getData().getPdown();
        this.tch = getData().getTch();
        this.trh = getData().getTrh();
        this.tco = getData().getTco();
 	   	this.fhp = getData().getFhp();
 	   	this.fip = getData().getFip();
 	   	this.flp = getData().getFlp();
	    this.factor = 1.0 / (this.fhp+this.fip+this.flp);
        return super.initStates(bus, mach);
    }

    /**
     * Get the editor panel for controller data editing
     *
     * @return the editor panel object
     */
//    @Override
//	public Object getEditPanel() {
//        _editPanel.init(this);
//        return _editPanel;
//    }
 
    @Override
	public AnController getAnController() {
    	return getClass().getAnnotation(AnController.class);  }
    @Override
	public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);   }
    @Override
	public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);    }

} // SimpleGovernor
