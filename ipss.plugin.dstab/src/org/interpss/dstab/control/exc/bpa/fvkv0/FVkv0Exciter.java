/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.bpa.fvkv0;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.PIControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.block.ICMLStaticBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/*
 * Part-1: Define your controller using CML as usual
 * =================================================
 */
@AnController(
   input="this.refPoint - mach.vt + pss.vs",
   output="this.gainCustomBlock.y",
   refPoint="this.piBlock.u0 - pss.vs + mach.vt",
   display= {}
)

public class FVkv0Exciter extends AnnotateExciter {
	   //piBlock----K/sT2+K*T1/T2----Kv = 0
	   public double t1 = 0.16, t2 = 0.01, k = 1.4, kp = k*t1/t2, ki = k/t2;
	   @AnControllerField(
		  type= CMLFieldEnum.ControlBlock,
		  input="this.refPoint - mach.vt + pss.vs",
		  parameter={"type.NoLimit", "this.kp", "this.ki"},
		  y0="this.filterBlock.u0"	)
	   PIControlBlock piBlock;

	   //filterBlock----(1+sT3)/(1+sT4)
	   public double k1 = 1.0/*constant*/, t3 = 0.0, t4 = 0.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.piBlock.y",
		   parameter={"type.NoLimit", "this.k1", "this.t3", "this.t4"},
		   y0="this.kaDelayBlock.u0 + this.washoutBlock.y"  )
	   FilterControlBlock filterBlock;

	   //kaDelayBlock----Ka/(1+sTa) with limits
	   public double ka = 10.0, ta = 0.01, vamax = 10.0, vamin = -10.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.filterBlock.y - this.washoutBlock.y",
		   parameter={"type.NonWindup", "this.ka", "this.ta", "this.vamax", "this.vamin"},
		   y0="this.gainCustomBlock.u0"  )
	   DelayControlBlock kaDelayBlock;

	   //washoutBlock----sKf/(1+sTf)
	   public double kf = 0, tf = 100, kf1 = kf/tf /*kf1= Kf/Tf*/;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.kaDelayBlock.y",
		   parameter={"type.NoLimit", "this.kf1", "this.tf"},
		   feedback=true  )
	   WashoutControlBlock washoutBlock;

	public double kg = 1.0/*constant*/, kc = 0.065, vrmax = 6.25, vrmin = -5.14;
	   @AnControllerField(
	      type=CMLFieldEnum.StaticBlock,
	      input="this.kaDelayBlock.y",
	      y0="mach.efd"  )
	   // extend the GainBlock to reuse its functionality
	   public ICMLStaticBlock gainCustomBlock = new GainBlock() {
		  @Override
		  public boolean initStateY0(double y0) {
			  // at the initial point, set the gain block gain
			  super.k = kg;
			  return super.initStateY0(y0);
		  }

		  @Override
		  public double getY() {
		  	//judge whether ifd <=0
		  	Machine mach = getMachine();
		    DStabBus dbus = mach.getDStabBus();
		  	double ifd = mach.calculateIfd(dbus, MachineIfdBase.EXCITER);
		  	if(ifd<=0){
		  		vrmin = 0;
		  	}
		  	//restrict the output
				if(super.getY() > calLimit(vrmax)) {
				  return calLimit(vrmax);
			  }else if(super.getY() < calLimit(vrmin)) {
				  return calLimit(vrmin);
			  }else {
				  return super.getY();
			  }

		  }

		  private double calLimit(double vrlimit) {
			  	Machine mach = getMachine();
		      DStabBus dbus = mach.getDStabBus();
		      double vt = mach.getVdq(dbus).abs();
		      //double ifd = mach.calculateIfd(dbus);
		      double ifd_Exc_pu=mach.calculateIfd(dbus, MachineIfdBase.EXCITER);
		      //System.out.println(mach.getDStabBus().getId()+",FVkv0 exc based IFD ="+ifd_Exc_pu+", ifd="+mach.calculateIfd(dbus, MachineIfdBase.MACHINE));
		      return vt * vrlimit - kc * ifd_Exc_pu;
		  }
	   };

    // UI Editor panel
    private static NBFVkv0ExciterEditPanel _editPanel = new NBFVkv0ExciterEditPanel();

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public FVkv0Exciter() {
	this("id", "name", "caty");
        this.setName("BPA FV(KV=0) Type Excitor");
        this.setCategory("BPA");
    }

     /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter category
     */
    public FVkv0Exciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
        _data = new FVkv0ExciterData();
    }

/*
 * Part-3: Define and init the data object
 * =======================================
 */

    /**
     * Get the plugin data object
     *
     * @return the data object
     */
    public FVkv0ExciterData getData() {
        return (FVkv0ExciterData)_data;
    }

    /**
     *  Init the controller states using the data object
     *
     *  @param bus the bus object where the machine object is connected
     *  @param mach the machine object of this controller object
     *  @param msg the SessionMsg object
     */
    @Override
    public boolean initStates(DStabBus bus, Machine mach) {
        // pass the plugin data object values to the controller
        this.k = getData().getK();
        this.t1 = getData().getT1();
        this.t2 = getData().getT2();
        this.t3 = getData().getT3();
        this.t4 = getData().getT4();
        this.ka = getData().getKa();
        this.ta = getData().getTa();
        this.vamax = getData().getVamax();
        this.vamin = getData().getVamin();
        this.vrmax = getData().getVrmax();
        this.vrmin = getData().getVrmin();
        this.kc = getData().getKc();
        this.kf = getData().getKf();
        this.tf = getData().getTf();
        // always add the following statement
        return super.initStates(bus, mach);
    }

/*
 * Part-4: Define the pluin data object edtior
 * ===========================================
 */

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

/*
 * do not modify the following part
 */
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
