 /*
  * @(#)Ieee1968Type4Exciter.java   
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
package org.interpss.dstab.control.exc.ieee.y1968.type4;

import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.Limit;
import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.field.func.SeFunction;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.Limit;

@AnController(
		   input="this.refPoint - mach.vt + pss.vs - this.washoutBlock.y",
		   output="this.delayBlock.y",
		   refPoint="this.customBlock.u0 - pss.vs + mach.vt + this.washoutBlock.y",
		   display= {})
public class Ieee1968Type4Exciter extends AnnotateExciter {
	   public double trh = 1.0, kv = 0.05, vrmax = 10.0, vrmin = 0.0;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.refPoint + pss.vs - mach.vt - this.washoutBlock.y",
	      y0="this.delayBlock.u0 + this.seFunc.y"	)
	   public ICMLControlBlock customBlock = new CMLControlBlockAdapter() {
	       private IntegrationControlBlock block = new IntegrationControlBlock(
	                      Limit, 1.0/trh, vrmax, vrmin);

	       @Override
		public boolean initStateY0(double y0) {
	         return block.initStateY0(y0);
	       }
	       @Override
		public double getU0(){
	         return 0.0;
	       }  
	       @Override
		public void eulerStep1(double u, double dt){
	         block.eulerStep1(u, dt);
	       }
	       @Override
		public void eulerStep2(double u, double dt){
	         block.eulerStep2(u, dt);
	       }
	       @Override
		public double getY(){
		      double u = block.getU();
	         if ( u > kv )
	            return vrmax;
	         else if ( u < -kv )
	            return vrmin;
	         else
	            return block.getY();
	       }
	       @Override
		public double getStateX() {
	           return block.getStateX();
	       }
	   };

	   public double ke1 = 1.0 /* ke1 = 1/Ke  */, te_ke = 0.1 /* te_ke = Te/Ke */;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.customBlock.y - this.seFunc.y",
	      parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
	      y0="mach.efd"	)
	   DelayControlBlock delayBlock;

	   public double e1 = 3.1, seE1 = 0.33, e2 = 2.3, seE2 = 0.1;
	   @AnFunctionField(
	      input= {"this.delayBlock.y"},
	      parameter={"this.e1", "this.seE1", "this.e2", "this.seE2"}	)
	   SeFunction seFunc;

	   public double kf = 1.0, tf = 0.05, k = kf/tf;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.delayBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;

    // UI Editor panel
//    private static NBIeee1968Type4EditPanel _editPanel = new NBIeee1968Type4EditPanel();
    
    /**
     * Default Constructor
     *
     */
    public Ieee1968Type4Exciter() {
        this.setName("IEEE-1968 Type4");
        this.setCategory("IEEE-1968");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public Ieee1968Type4Exciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new Ieee1968Type4ExciterData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public Ieee1968Type4ExciterData getData() {
        return (Ieee1968Type4ExciterData)_data;
    }
    
    /**
     *  Init the controller states
     *
     *  @param msg the SessionMsg object
     */
    @Override
	public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
        this.trh = getData().getTrh();
        this.kv = getData().getKv();
        this.vrmax = getData().getVrmax();
        this.vrmin = getData().getVrmin();
		this.ke1 = 1.0/getData().getKe();
		this.te_ke = getData().getTe() / getData().getKe();
		this.e1 = getData().getE1();
		this.seE1 = getData().getSeE1();
		this.e2 = getData().getE2();
		this.seE2 = getData().getSeE2();
		this.k = getData().getKf() / getData().getTf();
		this.tf = getData().getTf();
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
} // SimpleExciter

