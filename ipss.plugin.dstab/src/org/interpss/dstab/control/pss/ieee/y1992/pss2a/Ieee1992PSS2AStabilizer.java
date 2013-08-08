 /*
  * @(#)Ieee1992PSS2AStabilizer.java   
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


package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.FilterNthOrderBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateStabilizer;
import com.interpss.dstab.controller.annotate.AbstractChildAnnotateController;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

@AnController(
        input="mach.speed",
        output="this.filterBlock2.y",
        refPoint="0.0",
        display= {})
public class Ieee1992PSS2AStabilizer extends AnnotateStabilizer {
	    public double tw1 = 0.1, tw2 = 0.05, t6 = 0.05;
	    @AnControllerField(
	            type= CMLFieldEnum.Controller,
	            input="mach.speed",
	            y0="0.0",
	            initOrderNumber=-2	)
	    public CustomExciter customBlock1 = new CustomExciter(tw1, tw2, 1.0, t6);

	    public double tw3 = 0.1, tw4 = 0.05, t7 = 0.05, ks2 = 1.0;
	    @AnControllerField(
	            type= CMLFieldEnum.Controller,
	            input="mach.pe",
	            y0="0.0",
	            initOrderNumber=-3	)
	    public CustomExciter customBlock2 = new CustomExciter(tw3, tw4, ks2, t7);

	    public double t8 = 0.1, t9 = 0.05, ks3 = 1.0;
	    public int m = 1, n = 1;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.customBlock1.y + this.ks3*this.customBlock2.y",
	            parameter={"type.t8", "this.t9", "this.m", "this.n"},
	            y0="this.filterBlock1.u0 - this.refPoint + this.customBlock2.y"	)
	    FilterNthOrderBlock filterNthBlock;

	    public double ks1 = 10.0, t1 = 0.05, t2 = 0.5;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.refPoint + this.filterNthBlock.y - this.customBlock2.y",
	            parameter={"type.NoLimit", "this.ks1", "this.t1", "this.t2"},
	            y0="this.filterBlock2.u0"	)
	    FilterControlBlock filterBlock1;
		
	    public double one = 1.0, t3 = 0.05, t4 = 0.25, vstmax = 0.2, vstmin = -0.2;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.filterBlock1.y",
	            parameter={"type.Limit", "this.one", "this.t3", "this.t4", "this.vstmax", "this.vstmin"},
	            y0="pss.vs"	)
	    FilterControlBlock filterBlock2;

	@AnController(
			output="this.delayBlock.y",
			refPoint="0.0"
	)
	class CustomExciter extends AbstractChildAnnotateController {
	    public CustomExciter(double tw1, double tw2, double k, double t) {
	        super();
	        this.tw1 = tw1;
	        this.tw2 = tw2;
	        this.k = k;
	        this.t1 = t;
	    }

		 public double one = 1.0, tw1 = 0.05;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.input - this.refPoint",
	            parameter={"type.NoLimit", "this.one", "this.tw1"},
	            y0="this.washoutBlock2.u0"	)
	    WashoutControlBlock washoutBlock1;

		 public double tw2 = 0.05;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.washoutBlock1.y",
	            parameter={"type.NoLimit", "this.one", "this.tw2"},
	            y0="this.delayBlock.u0"	)
	    WashoutControlBlock washoutBlock2;

		 public double k = 1.0, t1 = 0.05;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.washoutBlock2.y",
	            parameter={"type.NoLimit", "this.k", "this.t1"},
	            y0="this.output"	)
	    DelayControlBlock delayBlock;
	 	
	    @Override
		public AnController getAnController() {
	    	return getClass().getAnnotation(AnController.class);  }
	    @Override
		public Field getField(String fieldName) throws Exception {
	    	return getClass().getField(fieldName);   }
	    @Override
		public Object getFieldObject(Field field) throws Exception {
	    	return field.get(this);    }
	}
	
	// UI Editor panel
	private static final NBIeee1992PSS2AEditPanel _editPanel = new NBIeee1992PSS2AEditPanel();
	
	public Ieee1992PSS2AStabilizer() {
        this.setName("ieee1992PSS2AStabilizer");
        this.setCategory("IEEE-1992");
	}
	
	/**
	 * Constructor
	 * 
	 * @param id pss id
	 * @param name pss name
	 */	
	public Ieee1992PSS2AStabilizer(final String id, final String name, final String caty) {
		super(id, name, caty);
		// _data is defined in the parent class. However init it here is a MUST
		_data = new Ieee1992PSS2AStabilizerData();
	}
	
	/**
	 * Get the PSS data 
	 * 
	 * @return the data object
	 */
	public Ieee1992PSS2AStabilizerData getData() {
		return (Ieee1992PSS2AStabilizerData)_data;
	}
	
	/**
	 *  Init the controller states
	 *  
	 *  @param msg the SessionMsg object
	 */
	@Override
	public boolean initStates(DStabBus abus, Machine mach) {
        this.ks1 = getData().getKs1();
        this.t1 = getData().getT1();
        this.t2 = getData().getT2();
        this.t3 = getData().getT3();
        this.t4 = getData().getT4();
        this.t6 = getData().getT6();
        this.t7 = getData().getT7();
        this.t8 = getData().getT8();
        this.t9 = getData().getT9();
        this.n = getData().getN();
        this.m = getData().getM();
        this.vstmax = getData().getVstmax();
        this.vstmin = getData().getVstmin();
        this.ks2 = getData().getKs2();
        this.ks3 = getData().getKs3();
        this.tw1 = getData().getTw1();
        this.tw2 = getData().getTw2();
        this.tw3 = getData().getTw3();
        this.tw4 = getData().getTw4();
        
        return super.initStates(abus, mach);
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

} // SimpleStabilizer
