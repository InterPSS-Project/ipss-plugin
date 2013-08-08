/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.bpa.fq;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.LowValueFunction;
import org.interpss.dstab.control.cml.func.SeFunction;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
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

public class FQExciter extends AnnotateExciter {
	   //kvFilterBlock----K(1+sT1)/(Kv+sT2)
	   public double kv = 1.0, k = 22.0, t1 = 1.0, t2 = 4.0, kv1 = k/kv, t21 = t2/kv, va1max= 100, va1min=-100;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.refPoint - mach.vt + pss.vs",
	      parameter={"type.NonWindup", "this.kv1", "this.t1", "this.t21", "this.va1max", "this.va1min"},
	      y0="this.filterBlock.u0"	)
	   FilterControlBlock kvFilterBlock;

	   //filterBlock----(1+sT3)/(1+sT4)
	   public double k1 = 1.0/*constant*/, t3 = 1.0, t4 = 1.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.kvFilterBlock.y ",
		   parameter={"type.NoLimit", "this.k1", "this.t3", "this.t4"},
		   y0="this.kaDelayBlock.u0 + this.washoutBlock.y"  )
	   FilterControlBlock filterBlock;

	   //kaDelayBlock----Ka/(1+sTa) with limits
	   public double ka = 10.0, ta = 0.01, vamax = 10.0, vamin = -10.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.filterBlock.y - this.washoutBlock.y",
		   parameter={"type.NonWindup", "this.ka", "this.ta", "this.vamax", "this.vamin"},
		   y0="this.khGainBlock.y + this.lvFunc.u0"  )
	   DelayControlBlock kaDelayBlock;	   

	   @AnFunctionField(
		       type= CMLFieldEnum.Function,
		       input= {"this.kaDelayBlock.y", "this.kliGainBlock.y"})
	   LowValueFunction lvFunc;
	   
	   //kbDelayBlock----Kb/(1+sT5)
	   public double kb = 10.0, t5 = 0.01, vrmax=100, vrmin=-100;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.kaDelayBlock.y - this.kaDelayBlock.y + this.lvFunc.y",
		   parameter={"type.Limit", "this.kb", "this.t5", "this.vrmax", "this.vrmin"},
		   y0="this.delayBlock.u0 + this.seFunc.y + this.kdGainBlock.y"  )
	   DelayControlBlock kbDelayBlock;
	   
	   //delayBlock----1/(Ke+sTe)	
	   public double ke = 2.0, ke1 = 1/ke, te = 2.0, te_ke = te/ke ;
	   @AnControllerField(
	     type= CMLFieldEnum.ControlBlock,
	     input="this.kbDelayBlock.y - this.seFunc.y - this.kdGainBlock.y",
	     parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
	     y0="this.infexCustomBlock.u0"	)
	   DelayControlBlock delayBlock;
	   
	   //seFunc----Se
	   public double e1 = 3.0, se_e1 = 1.0, e2 = 0.75*e1, se_e2 = 0.50;
	   @AnFunctionField(
	      input= {"this.delayBlock.y"},
	      parameter={"this.e1", "this.se_e1", "this.e2", "this.se_e2"}	)
	   SeFunction seFunc;

	   //kdGainBlock----Kd
	   public double kd = 10.0, td = 0.0/*constant*/;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="mach.ifd",
		   parameter={"type.NoLimit", "this.kd", "this.td"},
		   y0="this.kbDelayBlock.y - this.seFunc.y - this.delayBlock.u0",
		   initOrderNumber=7  )
	   DelayControlBlock kdGainBlock;
	   
	   //infexCustomBlock----In+Fex
		public double kg = 1.0/*constant*/, kc = 0.14, efdmax = 5.30;
		   @AnControllerField(
		      type=CMLFieldEnum.StaticBlock,
		      input="this.delayBlock.y",
		      y0="mach.efd"  )
		   // extend the GainBlock to reuse its functionality   
		   public ICMLStaticBlock infexCustomBlock = new GainBlock() {
			  @Override
			  public boolean initStateY0(double y0) {
				  // at the initial point, set the gain block gain
				  super.k = kg;
				  return super.initStateY0(y0);
			  }

			  @Override
			  public double getY() {
			  	double fex = calculateFex();
					if(fex < 0) 
						fex = 0;
					double output = this.getU0()*fex;
					if(output >efdmax)
						return efdmax;
					else
						return output;
				  
			  }
			  
			  private double calculateIn() {
				  	Machine mach = getMachine();
			      DStabBus dbus = mach.getDStabBus();
			      double ifd_Exc_pu=mach.calculateIfd(dbus, MachineIfdBase.EXCITER);
			      double ve = this.getU0();
			      return kc * ifd_Exc_pu / ve;
			  }
			  
			  private double calculateFex() {
			  		double in = calculateIn();
			  		if(in <=0.51)
			  			return 1-0.58*in;
			  		if(in>0.51&&in<0.715)
			  			return -0.865*(in+0.00826)*(in+0.00826)+0.93233;
			  		else
			  			return 1.68-1.714*in;
			  }
		   };

		   //washoutBlock----sKf/(1+sTf)
		   public double kf = 0, tf = 100, kf1 = kf/tf /*kf1= Kf/Tf*/;
		   @AnControllerField(
			   type=CMLFieldEnum.ControlBlock,
			   input="this.kdGainBlock.y + this.seFunc.y",
			   parameter={"type.NoLimit", "this.kf1", "this.tf"},
			   feedback=true  )
		   WashoutControlBlock washoutBlock;

		   //khGainBlock----Kh
		   public double kh = 10.0, th = 0.0/*constant*/;
		   @AnControllerField(
			   type=CMLFieldEnum.ControlBlock,
			   input="this.kdGainBlock.y + this.seFunc.y",
			   parameter={"type.NoLimit", "this.kh", "this.th"},
			   feedback=true  )
		   DelayControlBlock khGainBlock;

		   //kliGainBlock----Kli
		   public double kli = 10.0, tli = 0.0/*constant*/, vli = 1.0;
		   @AnControllerField(
			   type=CMLFieldEnum.ControlBlock,
			   input="this.kdGainBlock.y + this.seFunc.y + this.vli",
			   parameter={"type.NoLimit", "this.kli", "this.tli"},
			   feedback=true  )
		   DelayControlBlock kliGainBlock;

    // UI Editor panel
//    private static NBFQExciterEditPanel _editPanel = new NBFVkv0ExciterEditPanel();

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public FQExciter() {
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
    public FQExciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
//        _data = new FVkv0ExciterData();
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
    public FQExciterData getData() {
        return null; //(FVkv0ExciterData)_data;
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
        //this.kf = getData().getKf();
        //this.tf = getData().getTf();
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
        //_editPanel.init(this);
        return null; //_editPanel;
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
