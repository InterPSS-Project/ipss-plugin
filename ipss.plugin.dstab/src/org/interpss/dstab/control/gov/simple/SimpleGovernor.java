 /*
  * @(#)SimpleGovernor.java   
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

package org.interpss.dstab.control.gov.simple;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateGovernor;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * 
 * An implementation of the Simple governor model using InterPSS CML
 *
 */
@AnController(
        input="mach.speed - 1.0",
        output="this.gainBlock.y",
        refPoint="this.gainBlock.u0 + this.delayBlock.y",
        display= {})
public class SimpleGovernor extends AnnotateGovernor {
	// define a CML delay block
	public double ka = 10.0, ta = 0.5;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="mach.speed - 1.0",
            parameter={"type.NoLimit", "this.ka", "this.ta"},
            y0="this.refPoint - this.gainBlock.u0"	)
    DelayControlBlock delayBlock;
	
	// define a CML gain block
    public double ks = 1.0, pmax = 1.2, pmin = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.StaticBlock,
            input="this.refPoint - this.delayBlock.y",
            parameter={"type.Limit", "this.ks", "this.pmax", "this.pmin"},
            y0="mach.pm"	)
    GainBlock gainBlock;
 	
    // UI Editor panel
    private static NBSimpleGovernorEditPanel _editPanel = new NBSimpleGovernorEditPanel();
    
    /**
     * Default Constructor
     *
     */
    public SimpleGovernor() {
		this("id", "name", "caty");
        this.setName("SimpleGovernor");
        this.setCategory("InterPSS");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public SimpleGovernor(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new SimpleGovernorData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public SimpleGovernorData getData() {
        return (SimpleGovernorData)_data;
    }
    
    /**
     *  Init the controller states
     *
     *  @param msg the SessionMsg object
     */
    @Override public boolean initStates(DStabBus bus, Machine mach) {
    	// init the controller parameters using the data defined in the 
    	// data object    	
        this.ka = getData().getK();
        this.ta = getData().getT1();
        this.pmax = getData().getPmax();
        this.pmin = getData().getPmin();
        // call the super method to init CML field/controller states
        return super.initStates(bus, mach);
    }

    /**
     * Get the editor panel for controller data editing
     *
     * @return the editor panel object
     */
    @Override public Object getEditPanel() {
        _editPanel.init(this);
        return _editPanel;
    }
 
    // the following statement must be added to all CML controller
    @Override public AnController getAnController() {
    	return getClass().getAnnotation(AnController.class);  }
    @Override public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);   }
    @Override public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);    }
} // SimpleGovernor
