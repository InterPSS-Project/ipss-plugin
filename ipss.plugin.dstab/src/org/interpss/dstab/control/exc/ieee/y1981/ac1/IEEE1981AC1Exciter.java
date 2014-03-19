package org.interpss.dstab.control.exc.ieee.y1981.ac1;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.IntegrationControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.FexComboFunction;
import org.interpss.dstab.control.cml.func.SeFunction;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;


@AnController(
		   input="mach.vt",
		   output="this.teIntBlock.y",// * this.fexFunc.y
		   refPoint="this.filterBlock.u0 + this.washoutBlock.y - pss.vs  + this.trDelayBlock.y",
		   display= {},
		   debug = true
)
public class IEEE1981AC1Exciter extends AnnotateExciter {
	public double k1 = 1.0;/*constant*/
	public double ke = 1.0 , kd =1.0;
	/*
	 * Part-1: Define the blocks
	 * ==============================
	 */
	// transducer block
	 public double tr = 0.04;
     @AnControllerField(
          type= CMLFieldEnum.ControlBlock,
          input="mach.vt",
          parameter={"type.NoLimit", "this.k1", "this.tr"},
          y0="mach.vt",//debug = true,
          initOrderNumber=-1 
          )
     DelayControlBlock trDelayBlock;
	
	
	
	//filterBlock----(1+sTc)/(1+sTb)
	   public double tc = 52.73, tb = 21.84;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.refPoint  + pss.vs  - this.trDelayBlock.y- this.washoutBlock.y",
		   parameter={"type.NoLimit", "this.k1", "this.tc", "this.tb"},
		   y0="this.taDelayBlock.u0"  )
	   FilterControlBlock filterBlock;

	   public double ka = 39.35, ta = 0.02, vrmax = 6.0, vrmin = -6.0;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.filterBlock.y",
	      parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
	      y0="this.delayBlock.u0 + this.seFunc.y"	)
	   DelayControlBlock taDelayBlock;

	   
	   public double te = 0.6, kint = 1/te, veMax = 9999, veMin = 0 ;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.taDelayBlock.y - this.VFEBlock.y",
	      parameter={"type.NonWindup", "this.kint","this.veMax","this.veMin"},
	      y0="mach.efd / this.fexFunc.y"	)
	   IntegrationControlBlock teIntBlock;
       
	   @AnControllerField(
	      type= CMLFieldEnum.StaticBlock,
	      input="this.teIntBlock.y*this.ke + this.teIntBlock.y*this.seFunc.y + this.kd*mach.ifd",
	      parameter={"type.NoLimit", "this.k1"},
	      feedback = true	)
	   GainBlock VFEBlock;
      
	   //seFunc----Se
	   public double efd1 = 6.0, e1 = efd1, se_e1 = 0.1, e2 = 0.75 * efd1, se_e2 = 0.05;
	   @AnFunctionField(
	      input= {"this.teIntBlock.y"},
	      parameter={"this.e1", "this.se_e1", "this.e2", "this.se_e2"}	)
	   SeFunction seFunc;

	   //washoutBlock----sKf/(1+sTf)
	   public double kf = 0.0001, tf = 1.0, k = kf/tf;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.VFEBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;
	
	   
	   public double kc = 0.5;
	   @AnFunctionField(
	      input= {"this.teIntBlock.y","mach.ifd"},
	      parameter={"this.kc"}	)
	   FexComboFunction fexFunc;
	
	 /*
	 * Part-2: Define the contructors
	 * ==============================
	 */

	    /**
	     * Default Constructor
	     *
	     */
	    public IEEE1981AC1Exciter() {
		    this("id", "name", "caty");
	        this.setName("IEEE1981AC1");
	        this.setCategory("IEEE");
	    }

	     /**
	     * Constructor
	     *
	     * @param id exciter id
	     * @param name exciter name
	     * @param caty exciter category
	     */
	    public IEEE1981AC1Exciter(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. your need to initialize with
	        // the correct type, the data object to be edited
	        _data = new IEEE1981AC1ExciterData();
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
	    public IEEE1981AC1ExciterData getData() {
	        return (IEEE1981AC1ExciterData)_data;
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
	    	this.tr = getData().getTr();
	        this.ka = getData().getKa();
	        this.ta = getData().getTa();
	        this.tc = getData().getTc();
	        this.tb = getData().getTb();
	        this.vrmax = getData().getVrmax();
	        this.vrmin = getData().getVrmin();
	        this.ke = getData().getKe();
	        this.te = getData().getTe();
	        
	        this.kc =getData().getKc();
	        this.kd =getData().getKd();
	        
	        this.e1 = getData().getE1();
	        this.e2 = getData().getE2();
	        this.se_e1 = getData().getSe_e1();
	        this.se_e2 = getData().getSe_e2();
	        this.kf = getData().getKf();
	        this.tf = getData().getTf();
	        
	        //init the value internally used.
	        this.k1 = 1.0;
	        this.k = kf/tf;
	        this.kint = 1.0/te;
	        veMax = 9999; 
	        veMin = 0;
	        // always add the following statement
	        return super.initStates(bus, mach);
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
