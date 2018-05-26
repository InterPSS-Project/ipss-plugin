 /*
  * @(#)IeeeHTurbineGovernor.java   
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

package org.interpss.dstab.control.gov.ieee.hydro1981Type2;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.AnnotateGovernor;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.block.GainBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

@AnController(
        input="mach.speed - 1.0",
        output="this.wFilterBlock.y",
        refPoint="this.gainBlock.u0 + this.delayBlock.y",
        display= {})
public class Ieee1981Type2HydroGovernor extends AnnotateGovernor {
	 public double k = 1.0, t2 = 0.5, t1 = 0.01;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="mach.speed - 1.0",
            parameter={"type.NoLimit", "this.k", "this.t2", "this.t1"},
            y0="this.delayBlock.u0"	)
    FilterControlBlock filterBlock;

	 public double k1 = 10.0, t3 = 0.5;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.filterBlock.y",
            parameter={"type.NoLimit", "this.k1", "this.t3"},
            y0="this.refPoint - this.gainBlock.y"	)
    DelayControlBlock delayBlock;

    public double kgain = 1.0, pmax = 1.2, pmin = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.StaticBlock,
            input="this.refPoint - this.delayBlock.y",
            parameter={"type.Limit", "this.kgain", "this.pmax", "this.pmin"},
            y0="this.wFilterBlock.u0"	)
    GainBlock gainBlock;

	 public double kf2 = 1.0, tw = 0.1, t4 = -0.5*tw, t5 = 0.5*tw;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.gainBlock.y",
            parameter={"type.NoLimit", "this.kf2", "this.t4", "this.t5"},
            y0="mach.pm"	)
    FilterControlBlock wFilterBlock;

    // UI Editor panel
    private static NBIeeeHTurbineEditPanel _editPanel = new NBIeeeHTurbineEditPanel();
    
    /**
     * Default Constructor
     *
     */
    public Ieee1981Type2HydroGovernor() {
        this.setName("ieeeHTurbineGovernor");
        this.setCategory("IEEE");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public Ieee1981Type2HydroGovernor(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new Ieee1981Type3HydroGovernorData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public Ieee1981Type3HydroGovernorData getData() {
        return (Ieee1981Type3HydroGovernorData)_data;
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
        this.t3 = getData().getT3();
        this.pmax = getData().getPmax();
        this.pmin = getData().getPmin();
        this.tw = getData().getTw();
        return super.initStates(bus, mach);
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
	public AnController getAnController() {
    	return getClass().getAnnotation(AnController.class);  }
    @Override
	public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);   }
    @Override
	public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);    }

} // SimpleGovernor
