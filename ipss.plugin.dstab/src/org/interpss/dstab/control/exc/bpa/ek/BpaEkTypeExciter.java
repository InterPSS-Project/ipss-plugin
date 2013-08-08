/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.bpa.ek;

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
		   input="this.refPoint - mach.vt + pss.vs - this.fbBlock.y",
		   output="this.gainBlock.y",
		   refPoint="this.kaDelayBlock.u0 - pss.vs + mach.vt + this.fbBlock.y",
		   display= {} 
)

public class BpaEkTypeExciter extends AnnotateExciter {
    public double se_e1=0.860,semax =se_e1 , efdmax = 5.5, ke = 1.0;

    //kaDelayBlock----Ka/(1+sTa)
    public double ka = 40.0, ta = 0.05;
    @AnControllerField(
	    type= CMLFieldEnum.ControlBlock,
	    input="this.refPoint + pss.vs - mach.vt - this.fbBlock.y",
	    parameter={"type.NoLimit", "this.ka", "this.ta"},
	    y0="this.ka1DelayBlock.u0"	)
    DelayControlBlock kaDelayBlock;


    //ka1DelayBlock----1/(1+sTa1) limited
    public double ka1 = 1.0/*constant*/, ta1 = 0.02,  vrmax = (semax+ke)*efdmax, vrmin = -vrmax;
    @AnControllerField(
	    type= CMLFieldEnum.ControlBlock,
	    input="this.kaDelayBlock.y",
	    parameter={"type.NonWindup", "this.ka1", "this.ta1", "this.vrmax", "this.vrmin"},
	    y0="this.delayBlock.u0 + this.seFunc.y"	)
    DelayControlBlock ka1DelayBlock;

    //delayBlock----1/(Ke+sTe)
    public double ke1 = 1/ke, te = 2.0, te_ke = te/ke ;
    @AnControllerField(
	    type= CMLFieldEnum.ControlBlock,
	    input="this.ka1DelayBlock.y - this.seFunc.y",
	    parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
	    y0="mach.efd"	)
    DelayControlBlock delayBlock;
    
    //gainBlock ---efdmax limit
    public double efdmin=0 ;
    @AnControllerField(
	    type= CMLFieldEnum.ControlBlock,
	    input="this.delayBlock.y",
	    parameter={"type.Limit", "this.efdmax", "this.efdmin"},
	    y0="mach.efd"	)
    GainBlock gainBlock;

    //seFunc----Se
    public double e1 = efdmax,  e2 = 0.75*efdmax, se_e2 = 0.50;
    @AnFunctionField(
	    input= {"this.delayBlock.y"},
	    parameter={"this.e1", "this.se_e1", "this.e2", "this.se_e2"}	)
    SeFunction seFunc;

    //washoutBlock----sKf/(1+sTf)
    public double kf = 0.03, tf = 0.350, k = kf/tf;
    @AnControllerField(
	    type= CMLFieldEnum.ControlBlock,
	    input="this.delayBlock.y",
	    parameter={"type.NoLimit", "this.k", "this.tf"},
	    feedback = true	)
    WashoutControlBlock fbBlock;

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public BpaEkTypeExciter() {
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
    public BpaEkTypeExciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
        _data = new BpaEkTypeExciterData();
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
    public BpaEkTypeExciterData getData() {
        return (BpaEkTypeExciterData)_data;
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
        this.se_e1 = getData().getSe_e1();
        this.efdmax = getData().getEfdmax();
        this.ke = getData().getKe();
        this.te = getData().getTe();
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
    // UI Editor panel
    private static NBBpaEkTypeExciterEditPanel _editPanel = new NBBpaEkTypeExciterEditPanel();

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
