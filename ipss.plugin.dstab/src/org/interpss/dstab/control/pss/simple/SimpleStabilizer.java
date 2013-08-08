 /*
  * @(#)SimpleStabilizer.java   
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


package org.interpss.dstab.control.pss.simple;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.FilterControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateStabilizer;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * 
 * An implementation of the Simple stabilizer model using InterPSS CML
 *
 */
@AnController(
        input="mach.speed",
        output="this.filterBlock2.y",
        refPoint="mach.speed",
        display= {})
public class SimpleStabilizer extends AnnotateStabilizer {
	// define a CML filer block
	public double k1 = 1.0, t1 = 0.05, t2 = 0.5;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="mach.speed - this.refPoint",
            parameter={"type.NoLimit", "this.k1", "this.t1", "this.t2"},
            y0="this.filterBlock2.u0"	)
    FilterControlBlock filterBlock1;
	
	// define a CML filer block
    public double k2 = 1.0, t3 = 0.05, t4 = 0.25, vmax = 0.2, vmin = -0.2;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.filterBlock1.y",
            parameter={"type.Limit", "this.k2", "this.t3", "this.t4", "this.vmax", "this.vmin"},
            y0="pss.vs"	)
    FilterControlBlock filterBlock2;

	// UI Editor panel
	private static final NBSimpleStabilizerEditPanel _editPanel = new NBSimpleStabilizerEditPanel();
	
	public SimpleStabilizer() {
		this("id", "name", "caty");
        this.setName("Simple Stabilizer");
        this.setCategory("InterPSS");
	}
	
	/**
	 * Constructor
	 * 
	 * @param id pss id
	 * @param name pss name
	 */	
	public SimpleStabilizer(final String id, final String name, final String caty) {
		super(id, name, caty);
		// _data is defined in the parent class. However init it here is a MUST
		_data = new SimpleStabilizerData();
	}
	
	/**
	 * Get the PSS data 
	 * 
	 * @return the data object
	 */
	public SimpleStabilizerData getData() {
		return (SimpleStabilizerData)_data;
	}
	
	/**
	 *  Init the controller states
	 *  
	 *  @param msg the SessionMsg object
	 */
	@Override
	public boolean initStates(DStabBus abus, Machine mach) {
    	// init the controller parameters using the data defined in the 
    	// data object		
        this.k1 = getData().getKs();
        this.t1 = getData().getT1();
        this.t2 = getData().getT2();
        this.t3 = getData().getT3();
        this.t4 = getData().getT4();
        this.vmax = getData().getVsmax();
        this.vmin = getData().getVsmin();
        // call the super method to init CML field/controller states
        return super.initStates(abus, mach);
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
} // SimpleStabilizer
