 /*
  * @(#)Ieee1968Type2Exciter.java   
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
package org.interpss.dstab.control.exc.ieee.y1968.type2;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.SeFunction;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

@AnController(
		   input="this.refPoint - mach.vt + pss.vs - this.washoutBlock.y",
		   output="this.delayBlock.y",
		   refPoint="this.kaDelayBlock.u0 - pss.vs + mach.vt + this.washoutBlock.y",
		   display= {} )
public class Ieee1968Type2Exciter extends AnnotateExciter {
	   public double ka = 50.0, ta = 0.05, vrmax = 10.0, vrmin = 0.0;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.refPoint + pss.vs - mach.vt - this.washoutBlock.y",
	      parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
	      y0="this.delayBlock.u0 + this.seFunc.y"	)
	   DelayControlBlock kaDelayBlock;

	   public double ke1 = 1.0 /* ke1 = 1.0/Ke */, te_ke = 0.05 /* te_ke = Te/Ke*/;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.kaDelayBlock.y - this.seFunc.y",
	      parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
	      y0="mach.efd"	)
	   DelayControlBlock delayBlock;

	   public double e1 = 3.1, seE1 = 0.33, e2 = 2.3, seE2 = 0.1;
	   @AnFunctionField(
	      input= "this.delayBlock.y",
	      parameter={"this.e1", "this.seE1", "this.e2", "this.seE2"}	)
	   SeFunction seFunc;

	   public double kf2 = 1.0, tf2 = 0.05;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.kaDelayBlock.y",
	      parameter={"type.NoLimit", "this.kf2", "this.tf2"},
	      feedback = true	)
	   DelayControlBlock f2DelayBlock;

	   public double kf = 0.05, tf1 = 0.05, k = kf/tf1;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.f2DelayBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf1"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;

    // UI Editor panel
    private static NBIeee1968Type2EditPanel _editPanel = new NBIeee1968Type2EditPanel();
    
    /**
     * Default Constructor
     *
     */
    public Ieee1968Type2Exciter() {
        this.setName("IEEE-1968 Type2");
        this.setCategory("IEEE-1968");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public Ieee1968Type2Exciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new Ieee1968Type2ExciterData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public Ieee1968Type2ExciterData getData() {
        return (Ieee1968Type2ExciterData)_data;
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
        this.vrmax = getData().getVrmax();
        this.vrmin = getData().getVrmin();
		this.ke1 = 1.0/getData().getKe();
		this.te_ke = getData().getTe() / getData().getKe();
		this.e1 = getData().getE1();
		this.seE1 = getData().getSeE1();
		this.e2 = getData().getE2();
		this.seE2 = getData().getSeE2();
		this.k = getData().getKf() / getData().getTf1();
		this.tf1 = getData().getTf1();
		this.tf2 = getData().getTf2();

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

