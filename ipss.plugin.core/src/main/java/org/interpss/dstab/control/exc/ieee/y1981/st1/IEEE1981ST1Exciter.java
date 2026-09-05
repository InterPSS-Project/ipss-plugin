/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.ieee.y1981.st1;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/*
 * Part-1: Define your controller using CML as usual
 * =================================================
 */
@AnController(
		   input="mach.vt",
		   output="this.gainCustomBlock.y",
		   refPoint="this.gainBlock.u0 - pss.vs * this.vosError + this.trDelayBlock.y + this.washoutBlock.y",
		   display= {}//,
		   //debug = true
)

public class IEEE1981ST1Exciter extends AnnotateExciter {
	public double k1 = 1.0;/*constant*/
	
	/*
	 * Part-1: Define the blocks
	 * ==============================
	 */
	// transducer block
	 public double tr = 0.02;
     @AnControllerField(
          type= CMLFieldEnum.ControlBlock,
          input="mach.vt",
          parameter={"type.NoLimit", "this.k1", "this.tr"},
          y0="mach.vt",//debug = true,
          initOrderNumber=-1 
          )
     DelayControlBlock trDelayBlock;
	
	
	   //gainBlock----kg1 = 1.0 uses for set the limits
	   public double vimax = 5.30, vimin = -5.11;
	   @AnControllerField(
		   type= CMLFieldEnum.StaticBlock,
		   input="this.refPoint - this.trDelayBlock.y + pss.vs * this.vosError - this.washoutBlock.y",
		   parameter={"type.Limit", "this.k1", "this.vimax", "this.vimin"},
		   y0="this.filterBlock.u0"	)
	   GainBlock gainBlock;

	   //filterBlock----(1+sTc)/(1+sTb)
	   public double tc = 1.0, tb = 6.67;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.gainBlock.y",
		   parameter={"type.NoLimit", "this.k1", "this.tc", "this.tb"},
		   y0="this.filterBlock1.u0"  )
	   FilterControlBlock filterBlock;

	   // second compensating lead-lag, (1+sTc1)/(1+sTb1)
	   public double tc1 = 0.0, tb1 = 0.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.filterBlock.y",
		   parameter={"type.NoLimit", "this.k1", "this.tc1", "this.tb1"},
		   y0="this.kaDelayBlock.u0"  )
	   FilterControlBlock filterBlock1;

	   //kaDelayBlock----Ka/(1+sTa) with limits
	   public double ka = 300.0, ta = 0.01, vamax = 999.0, vamin = -999.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.filterBlock1.y",
		   parameter={"type.NonWindup", "this.ka", "this.ta", "this.vamax", "this.vamin"},
		   y0="this.gainCustomBlock.u0"  )
	   DelayControlBlock kaDelayBlock;
	   
	   
	   //washoutBlock----sKf/(1+sTf)
	   public double kf = 1, tf = 0.01, k = kf/tf;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.gainCustomBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;
	   /*
	   public double vrmax = 5.30, vrmin = -5.11,kc =0;
	   @AnControllerField(
		   type= CMLFieldEnum.StaticBlock,
		   input="this.kaDelayBlock.y",
		   parameter={"type.Limit", "this.k1", "this.vrmax", "this.vrmin"},
		   y0="mach.efd",
		   debug = true)
	   GainBlock gainCustomBlock;
	   */
   
	public double kg = 1.0, kc = 0.0, vrmax = 5.30, vrmin = -5.11;
	public double klr = 0.0, ilr = 0.0;
	public double vosError = 1.0, vosOutput = 0.0;
	public int uel = 1, vos = 1;
	   @AnControllerField(
	      type=CMLFieldEnum.StaticBlock,
	      input="this.kaDelayBlock.y + pss.vs * this.vosOutput",
	      y0="mach.efd"  )
	   // extend the GainBlock to reuse its functionality
	   public ICMLStaticBlock gainCustomBlock = new GainBlock() {
		  @Override
		  public boolean initStateY0(double y0) {
			  // at the initial point, set the gain block gain
			  super.k = kg;
			  return super.initStateY0(y0 + fieldCurrentLimiter());
		  }

		  @Override
		  public double getY() {
			  double vmax = calUpperLimit();
			  double vmin = calLowerLimit();
			  double y = super.getY() - fieldCurrentLimiter();
			  //System.out.println("Efd max, min, y ="+vmax+","+vmin+","+y);
			  if(y > vmax) {
				  return vmax;
			  }else if(y < vmin) {
				  return vmin;
			  }else {
				  return y;
			  }

		  }

		  private double calUpperLimit() {
			  	Machine mach = getMachine();
			  	BaseDStabBus<?,?> dbus = mach.getDStabBus();
			  double vt = dbus.getVoltageMag();
		     // double ifd = mach.calculateIfd(dbus);
			  double ifd_Exc_pu = mach.calculateIfd(MachineIfdBase.EXCITER);
		     // System.out.println(mach.getDStabBus().getId()+", exc based IFD ="+ifd_Exc_pu+", ifd="+mach.calculateIfd(dbus));
			  return vt * vrmax - kc * ifd_Exc_pu;
		     // return vt * vrlimit - kc * ifd;
		  }

		  private double calLowerLimit() {
			  return getMachine().getDStabBus().getVoltageMag() * vrmin;
		  }

		  private double fieldCurrentLimiter() {
			  double ifd = getMachine().calculateIfd(MachineIfdBase.EXCITER);
			  return Math.max(0.0, klr * (ifd - ilr));
		  }
	   };
	   
	   


    // UI Editor panel
  //  private static NBIEEE1981ST1ExciterEditPanel _editPanel = new NBIEEE1981ST1ExciterEditPanel();

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public IEEE1981ST1Exciter() {
	this("id", "name", "caty");
        this.setName("IEEE1981ST1");
        this.setCategory("IEEE");
    }

     /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter category
     */
    public IEEE1981ST1Exciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
        _data = new IEEE1981ST1ExciterData();
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
    public IEEE1981ST1ExciterData getData() {
        return (IEEE1981ST1ExciterData)_data;
    }

    /**
     *  Init the controller states using the data object
     *
     *  @param bus the bus object where the machine object is connected
     *  @param mach the machine object of this controller object
     *  @param msg the SessionMsg object
     */
    @Override
    public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
        // pass the plugin data object values to the controller
		this.uel = getData().getUel();
		this.vos = getData().getVos();
		this.vosError = this.vos == 1 ? 1.0 : 0.0;
		this.vosOutput = this.vos == 2 ? 1.0 : 0.0;
		this.tr = getData().getTr();
        this.vimax = getData().getVimax();
        this.vimin = getData().getVimin();
        this.tc = getData().getTc();
        this.tb = getData().getTb();
		this.tc1 = getData().getTc1();
		this.tb1 = getData().getTb1();
        this.ka = getData().getKa();
        this.ta = getData().getTa();
		this.vamax = getData().getVamax();
		this.vamin = getData().getVamin();
        this.vrmax = getData().getVrmax();
        this.vrmin = getData().getVrmin();
        this.kc = getData().getKc();
        this.kf = getData().getKf();
        this.tf = getData().getTf();
		this.klr = getData().getKlr();
		this.ilr = getData().getIlr();

		if (this.vimax < this.vimin) {
			double swap = this.vimax; this.vimax = this.vimin; this.vimin = swap;
		}
		if (this.vamax < this.vamin) {
			double swap = this.vamax; this.vamax = this.vamin; this.vamin = swap;
		}
		if (this.vrmax < this.vrmin) {
			double swap = this.vrmax; this.vrmax = this.vrmin; this.vrmin = swap;
		}
		double ifd0 = mach.calculateIfd(MachineIfdBase.EXCITER);
		double lr0 = Math.max(0.0, this.klr * (ifd0 - this.ilr));
		double va0 = mach.getEfd() + lr0;
		this.vamax = Math.max(this.vamax, va0);
		this.vamin = Math.min(this.vamin, va0);
		double vi0 = Math.abs(this.ka) > 1.0e-9 ? va0 / this.ka : va0;
		this.vimax = Math.max(this.vimax, vi0);
		this.vimin = Math.min(this.vimin, vi0);
		double vt0 = bus.getVoltageMag();
		if (vt0 > 0.0) {
			this.vrmax = Math.max(this.vrmax, (mach.getEfd() + this.kc * ifd0) / vt0);
			this.vrmin = Math.min(this.vrmin, mach.getEfd() / vt0);
		}
        
		this.k = this.tf == 0.0 ? 0.0 : this.kf / this.tf;
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
//    @Override
//    public Object getEditPanel() {
//        _editPanel.init(this);
//        return _editPanel;
//    }

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
