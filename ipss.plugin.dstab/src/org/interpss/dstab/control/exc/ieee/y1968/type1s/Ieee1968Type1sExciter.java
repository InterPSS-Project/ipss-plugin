 /*
  * @(#)Ieee1968Type1sExciter.java   
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
  * @Date 04/15/2007
  * 
  *   Revision History
  *   ================
  *
  */
package org.interpss.dstab.control.exc.ieee.y1968.type1s;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

@AnController(
		   input="this.refPoint - mach.vt + pss.vs - this.washoutBlock.y",
		   output="this.delayBlock.y",
		   refPoint="this.delayBlock.u0 - pss.vs + mach.vt + this.washoutBlock.y",
		   display= {} )
public class Ieee1968Type1sExciter extends AnnotateExciter {
	   public double ka = 50.0, ta = 0.05, kp = 10.0, vrmin = 0.0;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.refPoint + pss.vs - mach.vt - this.washoutBlock.y",
	      parameter={"type.Limit", "this.ka", "this.ta", "this.kp * mach.vt", "this.vrmin"},
	      y0="mach.efd"	)
	   DelayControlBlock delayBlock;

	   public double kf = 1.0, tf = 0.1, k = kf/tf;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.delayBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;

    // UI Editor panel
    private static NBIeee1968Type1sEditPanel _editPanel = new NBIeee1968Type1sEditPanel();
    
    /**
     * Default Constructor
     *
     */
    public Ieee1968Type1sExciter() {
        this.setName("IEEE-1968 Type1s");
        this.setCategory("IEEE-1968");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public Ieee1968Type1sExciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new Ieee1968Type1sExciterData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public Ieee1968Type1sExciterData getData() {
        return (Ieee1968Type1sExciterData)_data;
    }
    
    /**
     *  Init the controller states
     *
     *  @param msg the SessionMsg object
     */
    @Override
	public boolean initStates(DStabBus bus, Machine mach) {
        this.ka = getData().getKa();
        this.ta = getData().getTa();
        this.kp = getData().getKp();
        this.vrmin = getData().getVrmin();
        this.k = getData().getKf()/getData().getTf();
        this.tf = getData().getTf();
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
} // SimpleExciter

