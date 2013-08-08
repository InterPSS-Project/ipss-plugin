/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.ieee.y1981.dc1;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
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
   output="this.delayBlock.y",
   refPoint="this.filterBlock.u - pss.vs + mach.vt + this.washoutBlock.y",
   display= {  }
)

public class IEEE1981DC1Exciter extends AnnotateExciter {
	   //filterBlock----(1+sTc)/(1+sTb)
	   public double k1 = 1.0/*constant*/, tc = 52.73, tb = 21.84;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.refPoint - mach.vt + pss.vs - this.washoutBlock.y",
		   parameter={"type.NoLimit", "this.k1", "this.tc", "this.tb"},
		   y0="this.kaDelayBlock.u0"  )
	   FilterControlBlock filterBlock;

	   public double ka = 39.35, ta = 0.02, vrmax = 6.0, vrmin = -6.0;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.filterBlock.y",
	      parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
	      y0="this.delayBlock.u0 + this.seFunc.y"	)
	   DelayControlBlock kaDelayBlock;


	   public double ke = 1.0, ke1 = 1/ke, te = 0.6, te_ke = te/ke ;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.kaDelayBlock.y - this.seFunc.y",
	      parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
	      y0="mach.efd"	)
	   DelayControlBlock delayBlock;

	   //seFunc----Se
	   public double efd1 = 6.0, e1 = efd1, se_e1 = 1.0, e2 = 0.75 * efd1, se_e2 = 0.05;
	   @AnFunctionField(
	      input= {"this.delayBlock.y"},
	      parameter={"this.e1", "this.se_e1", "this.e2", "this.se_e2"}	)
	   SeFunction seFunc;

	   //washoutBlock----sKf/(1+sTf)
	   public double kf = 0.0001, tf = 1.0, k = kf/tf;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.delayBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;

	// UI Editor panel
	    private static NBIEEE1981DC1ExciterEditPanel _editPanel = new NBIEEE1981DC1ExciterEditPanel();  

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public IEEE1981DC1Exciter() {
	    this("id", "name", "caty");
        this.setName("SimpleExcitor");
        this.setCategory("InterPSS");
    }

     /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter category
     */
    public IEEE1981DC1Exciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
        _data = new IEEE1981DC1ExciterData();
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
    public IEEE1981DC1ExciterData getData() {
        return (IEEE1981DC1ExciterData)_data;
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
        this.tc = getData().getTc();
        this.tb = getData().getTb();
        this.vrmax = getData().getVrmax();
        this.vrmin = getData().getVrmin();
        this.ke = getData().getKe();
        this.te = getData().getTe();
        this.e1 = getData().getE1();
        this.e2 = getData().getE2();
        this.se_e1 = getData().getSe_e1();
        this.se_e2 = getData().getSe_e2();
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
