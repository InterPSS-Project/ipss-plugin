/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.bpa.ec;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.SeFunction;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/*
 * Part-1: Define your controller using CML as usual
 * =================================================
 */
@AnController(
   input="this.refPoint - mach.vt + pss.vs - this.washoutBlock.y",
   output="this.gainBlock.y",
   refPoint="this.kaDelayBlock.u - pss.vs + mach.vt + this.washoutBlock.y",
   display= { }
)

public class BpaEcTypeExciter extends AnnotateExciter {
	//kaDelayBlock----Ka/(1+sTa)   
	public double ka = 80.0, ta = 0.02;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.refPoint + pss.vs - mach.vt - this.washoutBlock.y",
	      parameter={"type.NoLimit", "this.ka", "this.ta"},
	      y0="this.ka1DelayBlock.u0"	)
	   DelayControlBlock kaDelayBlock;

		   //ka1DelayBlock----1/(1+sTa1) limited
		   public double ka1 = 1.0/*constant*/, ta1 = 0.0,   vrmax = 5, vrmin = -5.0;
		   @AnControllerField(
		      type= CMLFieldEnum.ControlBlock,
		      input="this.kaDelayBlock.y",
		      parameter={"type.Limit", "this.ka1", "this.ta1", "this.vrmax", "this.vrmin"},
		      y0="this.delayBlock.u0 + this.seFunc.y"	)
		   DelayControlBlock ka1DelayBlock;

			 //delayBlock----1/(Ke+sTe)	
		   public double ke = 1.0,ke1 = 1/ke, te = 0.8, te_ke = te/ke ;
		   @AnControllerField(
		      type= CMLFieldEnum.ControlBlock,
		      input="this.ka1DelayBlock.y - this.seFunc.y",
		      parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
		      y0="this.gainBlock.u0"	)
		   DelayControlBlock delayBlock;

		   //seFunc----Se
		   public double e1 = 1.0, se_e1 = 0.36, e2 = 0.75, se_e2 = 0.13;
		   @AnFunctionField(
		      input= {"this.delayBlock.y"},
		      parameter={"this.e1", "this.se_e1", "this.e2", "this.se_e2"}	)
		   SeFunction seFunc;
		   
		   //gainBlock----set EFDmax and EFDmin
		   public double one = 1.0/*constant*/,efdmax=5.0, efdmin = 0.0;
		   @AnControllerField(
		      type= CMLFieldEnum.StaticBlock,
		      input="this.delayBlock.y",
		      parameter={"type.Limit", "this.one", "this.efdmax", "this.efdmin"},
	        y0="mach.efd"	)
		   GainBlock gainBlock;

		   //keGainBlockBlock----Ke	
			public double t = 0.0;
	    	@AnControllerField(
	        type= CMLFieldEnum.ControlBlock,
	        input="this.delayBlock.y",
	        parameter={"type.NoLimit", "this.ke", "this.t"},
	        feedback=true)
	   		DelayControlBlock keGainBlock;
		   
		   //washoutBlock----sKf/(1+sTf)
		   public double kf = 0.03, tf = 1.0, k = kf/tf;
		   @AnControllerField(
		      type= CMLFieldEnum.ControlBlock,
		      input="this.keGainBlock.y+this.seFunc.y",
		      parameter={"type.NoLimit", "this.k", "this.tf"},
		      feedback = true	)
		   WashoutControlBlock washoutBlock;

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public BpaEcTypeExciter() {
	this("id", "name", "caty");
        this.setName("BPA EC type Exciter");
        this.setCategory("BPA");
    }

     /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter category
     */
    public BpaEcTypeExciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
        _data = new BpaEcTypeExciterData();
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
    public BpaEcTypeExciterData getData() {
        return (BpaEcTypeExciterData)_data;
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
        this.ka = getData().getKa();
        this.ta = getData().getTa();
        this.ta1 = getData().getTa1();
        this.e1 = getData().getE1();
        this.se_e1= getData().getSe_e1();
        this.e2=  getData().getE2();
        this.te = getData().getTe();
        this.se_e2 = getData().getSe_e2();
        this.ke = getData().getKe();
        this.te = getData().getTe();
        this.kf = getData().getKf();
        this.tf = getData().getTf();
        this.efdmax=getData().getEfdmax();
        this.efdmin=getData().getEfdmin();
        this.vrmax=getData().getVrMax();
        this.vrmin=getData().getVrMin();
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
//    // UI Editor panel
//    private static NBECExciterEditPanel _editPanel = new NBECExciterEditPanel();

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
