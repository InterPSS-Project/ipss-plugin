/*
 * @(#)ExciterObjectFactory.java   
 *
 * Copyright (C) 2008-2010 www.interpss.org
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
 * @Date 08/15/2006
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.dstab.control.exc.simple;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * 
 * An implementation of the Simple exciter model using InterPSS CML
 *
 */
@AnController(
		// annotate the controller with input, output, refPoint and display definition
        input="this.refPoint + pss.vs - mach.vt",
        output="this.delayBlock.y",
        refPoint="this.delayBlock.u0 - pss.vs + mach.vt",
        display= {} )
public class SimpleExciter extends AnnotateExciter {
	// define a CML delay block
	public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.refPoint + pss.vs - mach.vt",
            parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
            y0="mach.efd"	)
    DelayControlBlock delayBlock;
 	
    // UI Editor panel
    private static NBSimpleExciterEditPanel _editPanel = new NBSimpleExciterEditPanel();
    
    /**
     * Default Constructor
     *
     */
    public SimpleExciter() {
		this("id", "name", "caty");
        this.setName("SimpleExcitor");
        this.setCategory("InterPSS");
    }
    
    /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter catogory
     */
    public SimpleExciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data field is defined in the parent class. However init it here is a MUST
        this._data = new SimpleExciterData();
    }
    
    /**
     * Get the exciter data
     *
     * @return the data object
     */
    public SimpleExciterData getData() {
        return (SimpleExciterData)this._data;
    }
    
    /**
     *  Init the controller states
     *
     *  @param msg the SessionMsg object
     */
    @Override public boolean initStates(DStabBus bus, Machine mach) {
    	// init the controller parameters using the data defined in the 
    	// data object
        this.k = getData().getKa();
        this.t = getData().getTa();
        this.vmax = getData().getVrmax();
        this.vmin = getData().getVrmin();
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
} // SimpleExciter

