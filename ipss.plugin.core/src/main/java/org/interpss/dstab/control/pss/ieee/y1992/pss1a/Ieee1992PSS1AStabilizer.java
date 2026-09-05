 /*
  * @(#)Ieee1992PSS1AStabilizer.java   
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


package org.interpss.dstab.control.pss.ieee.y1992.pss1a;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.TFunc2ndOrderBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

@AnController(
        input="mach.speed",
        output="this.outputGate.y",
        refPoint="0.0",
        display= {})
public class Ieee1992PSS1AStabilizer extends AnnotateStabilizer {
	    public double one = 1.0, t6 = 0.05;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.speedGain*mach.speed-this.speedGain*this.speedRef"
	                    + "+this.peGain*mach.pe-this.peGain*this.peRef"
	                    + "+this.accelGain*mach.pm-this.accelGain*mach.pe-this.accelGain*this.accelRef"
	                    + "+this.voltageGain*mach.vt-this.voltageGain*this.vtRef",
	            parameter={"type.NoLimit", "this.one", "this.t6"},
	            y0="this.washoutBlock.u0", initOrderNumber=1	)
	    public DelayControlBlock delayBlock;

	    public double ks = 1.0, t5 = 0.1;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.delayBlock.y",
	            parameter={"type.NoLimit", "this.ks", "this.t5"},
	            y0="this.order2ndBlock.u0", initOrderNumber=2	)
	    public WashoutControlBlock washoutBlock;

	    public double a1 = 0.05, a2 = 0.5;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.washoutBlock.y",
	            parameter={"type.NoLimit", "this.one", "this.a1", "this.a2"},
	            y0="this.filterBlock1.u0", initOrderNumber=3	)
	    public TFunc2ndOrderBlock order2ndBlock;

	    public double t1 = 0.05, t2 = 0.5;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.order2ndBlock.y",
	            parameter={"type.NoLimit", "this.one", "this.t1", "this.t2"},
	            y0="this.filterBlock2.u0", initOrderNumber=4	)
	    public FilterControlBlock filterBlock1;
		
	    public double k2 = 1.0, t3 = 0.05, t4 = 0.25, vmax = 0.2, vmin = -0.2;
	    @AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.filterBlock1.y",
	            parameter={"type.Limit", "this.k2", "this.t3", "this.t4", "this.vmax", "this.vmin"},
	            y0="this.outputGate.u0", initOrderNumber=5	)
	    public FilterControlBlock filterBlock2;

	    public double speedRef, peRef, accelRef, vtRef;
	    public double speedGain, peGain, accelGain, voltageGain;
	    public double vcu, vcl;
	    @AnControllerField(
	            type=CMLFieldEnum.StaticBlock,
	            input="this.filterBlock2.y",
	            y0="pss.vs", initOrderNumber=6)
	    public ICMLStaticBlock outputGate = new GainBlock() {
	        @Override
	        public boolean initStateY0(double y0) {
	            super.k = 1.0;
	            return super.initStateY0(y0);
	        }

	        @Override
	        public double getY() {
	            double vt = getMachine().getDStabBus().getVoltageMag();
	            boolean above = vcu != 0.0 && vt > vcu;
	            boolean below = vcl != 0.0 && vt < vcl;
	            return above || below ? 0.0 : super.getY();
	        }
	    };

	// UI Editor panel
//	private static final NBIeee1992PSS1AEditPanel _editPanel = new NBIeee1992PSS1AEditPanel();
	
	public Ieee1992PSS1AStabilizer() {
        this.setName("ieee1992PSS1AStabilizer");
        this.setCategory("IEEE-1992");
	}
	
	/**
	 * Constructor
	 * 
	 * @param id pss id
	 * @param name pss name
	 */	
	public Ieee1992PSS1AStabilizer(final String id, final String name, final String caty) {
		super(id, name, caty);
		// _data is defined in the parent class. However init it here is a MUST
		_data = new Ieee1992PSS1AStabilizerData();
	}
	
	/**
	 * Get the PSS data 
	 * 
	 * @return the data object
	 */
	public Ieee1992PSS1AStabilizerData getData() {
		return (Ieee1992PSS1AStabilizerData)_data;
	}
	
	/**
	 *  Init the controller states
	 *  
	 *  @param msg the SessionMsg object
	 */
	@Override
	public boolean initStates(BaseDStabBus<?,?> abus, Machine mach) {
		int ics = getData().getIcs();
		speedRef = mach.getSpeed();
		peRef = mach.getPe();
		accelRef = mach.getPm() - mach.getPe();
		vtRef = abus.getVoltageMag();
		speedGain = ics == 1 ? 1.0 : 0.0;
		peGain = ics == 3 ? 1.0 : 0.0;
		accelGain = ics == 4 ? 1.0 : 0.0;
		voltageGain = ics == 5 ? 1.0 : 0.0;
        this.ks = getData().getKs();
        this.t1 = getData().getT1();
        this.t2 = getData().getT2();
        this.t3 = getData().getT3();
        this.t4 = getData().getT4();
        this.t5 = getData().getT5();
        this.t6 = getData().getT6();
        this.vmax = getData().getVstmax();
        this.vmin = getData().getVstmin();
        this.a1 = getData().getA1();
        this.a2 = getData().getA2();
		this.vcu = getData().getVcu();
		this.vcl = getData().getVcl();
        return super.initStates(abus, mach);
	}

	/**
	 * Get the editor panel for controller data editing
	 * 
	 * @return the editor panel object
	 */	
//	@Override
//	public Object getEditPanel() {
//		_editPanel.init(this);
//		return _editPanel;
//	}
	
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
