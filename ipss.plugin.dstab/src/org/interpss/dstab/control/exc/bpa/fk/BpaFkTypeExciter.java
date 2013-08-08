/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.bpa.fk;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
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
		   input="this.refPoint - mach.vt + pss.vs-this.kfWashoutBlock.y",
		   output="this.gainCustomBlock.y",
		   refPoint="this.gainBlock.u0 - pss.vs + mach.vt + this.kfWashoutBlock.y",
		   display= {}
)

public class BpaFkTypeExciter extends AnnotateExciter {
	   //gainBlock----kg1 = 1.0 uses for set the limits
	   public double kg1 = 1.0/*constant*/, vimax = 5.30, vimin = -5.11;
	   @AnControllerField(
		   type= CMLFieldEnum.StaticBlock,
		   input="this.refPoint - mach.vt + pss.vs-this.kfWashoutBlock.y",
		   parameter={"type.Limit", "this.kg1", "this.vimax", "this.vimin"},
		   y0="this.filterBlock.u0"	)
	   GainBlock gainBlock;

	   //filterBlock----(1+sTc)/(1+sTb)
	   public double k2 = 1.0/*constant*/, tc = 1.0, tb = 6.67;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.gainBlock.y",
		   parameter={"type.NoLimit", "this.k2", "this.tc", "this.tb"},
		   y0="this.kaDelayBlock.u0"  )
	   FilterControlBlock filterBlock;

	   //kaDelayBlock----Ka/(1+sTa)
	   public double ka = 300.0, ta = 0.01;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.filterBlock.y",
		   parameter={"type.NoLimit", "this.ka", "this.ta"},
		   y0="this.gainCustomBlock.u0"  )
	   DelayControlBlock kaDelayBlock;
	   
	   //kfWashoutBlock sKf/1+sTf

	   public double kf = 0.0001, tf = 100.0, kf1 = kf/tf;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.kaDelayBlock.y",
		   parameter={"type.NoLimit", "this.kf1", "this.tf"},
		   feedback=true  )
	   WashoutControlBlock kfWashoutBlock;

	public double kg = 1.0/*constant*/, kc = 0.14, vrmax = 5.30, vrmin = -5.11;
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
		      double ifd_Exc_pu=mach.calculateIfd(dbus, MachineIfdBase.EXCITER);
		      return vt * vrlimit - kc * ifd_Exc_pu;
		  }
	   };

    // UI Editor panel
    private static NBBpaFkTypeExciterEditPanel _editPanel = new NBBpaFkTypeExciterEditPanel();

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public BpaFkTypeExciter() {
	this("id", "name", "caty");
        this.setName("BPAFKExcitor");
        this.setCategory("BPA");
    }

     /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter category
     */
    public BpaFkTypeExciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
        _data = new BpaFkTypeExciterData();
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
    public BpaFkTypeExciterData getData() {
        return (BpaFkTypeExciterData)_data;
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
        this.vimax = getData().getVimax();
        this.vimin = getData().getVimin();
        this.tc = getData().getTc();
        this.tb = getData().getTb();
        this.ka = getData().getKa();
        this.ta = getData().getTa();
        this.vrmax = getData().getVrmax();
        this.vrmin = getData().getVrmin();
        this.kc = getData().getKc();
        this.kf=getData().getKf();
        this.tf=getData().getTf();

        
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
