 /*
  * @(#)Ieee1968Type3Exciter.java   
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

package org.interpss.dstab.control.exc.ieee.y1968.type3;

import java.lang.reflect.Field;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.block.ICMLStaticBlock;
import com.interpss.dstab.controller.block.adapt.CMLStaticBlockAdapter;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

@AnController(
		   input="this.refPoint - mach.vt + pss.vs - this.washoutBlock.y",
		   output="this.delayBlock.y",
		   refPoint="this.kaDelayBlock.u0 - pss.vs + mach.vt + this.washoutBlock.y",
		   display= {})
public class Ieee1968Type3Exciter extends AnnotateExciter {
	   public double ka = 50.0, ta = 0.05, vrmax = 10.0, vrmin = 0.0;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.refPoint + pss.vs - mach.vt - this.washoutBlock.y",
	      parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
	      y0="this.customBlock.u0"	)
	   DelayControlBlock kaDelayBlock;

	   public double kp = 2.0, ki = 1.0, vbmax = 10.0;
	   @AnControllerField(
	      type= CMLFieldEnum.StaticBlock,
	      input= "this.kaDelayBlock.y", 
	      y0="this.delayBlock.u0"    )
	   public ICMLStaticBlock customBlock = new CMLStaticBlockAdapter() {
	      private LimitType limit = new LimitType(vbmax, 0.0);
	      private boolean A_gt_1 = false;
	      private double u = 0.0;
	                  
	      @Override
		public boolean initStateY0(double y0) {
	         if ( y0 > vbmax || y0 < 0.0) {
	            IpssLogger.getLogger().warning("CustomBlock init problem: y0 > vbmax or y0 < 0.0");
	            return false;
	         }
	         double x = calFunc();
	         if ( this.A_gt_1 && y0 != 0.0 ) {
	        	 IpssLogger.getLogger().warning("CustomBlock init problem: A > 1 and y0 != 0.0");
	            return false;         
	         }
	         this.u = y0 - x;
	         return true;
	      }

	      @Override
		public double getU0() {
	         return this.u;
	      }

	      @Override
		public void eulerStep1(double u, double dt) {
	         this.u = u;
	      }

	      @Override
		public void eulerStep2(double u, double dt) {
	         this.u = u;
	      }
	 
	      @Override
		public double getY() {
	         double x = calFunc();
	         if ( this.A_gt_1 )
	           return 0.0;
	         else {
	           return this.limit.limit(this.u + x);
	         }
	      }

	      private double calFunc() {
	         Machine mach = getMachine();
	         DStabBus dbus = mach.getDStabBus();

	         // calculate Ve
	         double vt = mach.getVdq(dbus).abs();
	         double it = mach.getIdq(dbus).abs();
	         double ve = new Complex(kp*vt, ki*it).abs();

	         // calculate sqrt( 1 - A )
	         double xad = mach.getMachData().getXd() - mach.getMachData().getXl();
	         double ifd = mach.calculateIfd(dbus, MachineIfdBase.MACHINE);
	         double a = 0.78 * xad * ifd * ifd / ve;
	         if (a > 1.0) {
	            //System.out.println("ve, xad, ifd, a: " + ve + ", " + xad + ", " + ifd + ", " + a);
	            this.A_gt_1 = true;
	 				return 0.0;
	         }
	         else {
	            this.A_gt_1 = false;
	            return ve * Math.sqrt(1.0 - a);
	         }
	      }
	   };

	   public double ke1 = 1.0 /* ke1 = 1/Ke  */, te_ke = 0.1 /* te_ke = Te/Ke */;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.customBlock.y",
	      parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
	      y0="mach.efd"	)
	   DelayControlBlock delayBlock;


	   public double kf = 0.1, tf = 0.5, k = kf/tf;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.delayBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;

    // UI Editor panel
    private static NBIeee1968Type3EditPanel _editPanel = new NBIeee1968Type3EditPanel();
    
    /**
     * Default Constructor
     *
     */
    public Ieee1968Type3Exciter() {
        this.setName("IEEE-1968 Type2");
        this.setCategory("IEEE-1968");
    }
    
    /**
     * Constructor
     *
     * @param id excitor id
     * @param name excitor name
     */
    public Ieee1968Type3Exciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. However init it here is a MUST
        _data = new Ieee1968Type3ExciterData();
    }
    
    /**
     * Get the excitor data
     *
     * @return the data object
     */
    public Ieee1968Type3ExciterData getData() {
        return (Ieee1968Type3ExciterData)_data;
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
		this.kp = getData().getKp();
		this.ki = getData().getKi();
		this.vbmax = getData().getVbmax();
		this.k = getData().getKf() / getData().getTf();
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

