/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.pss.bpa.sp;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateStabilizer;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.block.ICMLStaticBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/*
 * Part-1: Define your controller using CML as usual
 * =================================================
 */
@AnController(
        input="mach.pm - mach.pe",
        output="this.gainCustomBlock.y",
        refPoint="0.0",
        display= {"str.speed,mach.speed",
       		"str.pe,mach.pe"        }
)

public class BPASPTypeStabilizer extends AnnotateStabilizer {
	//1.1 kqsDelayBlock
	public double kqs = 0.02, tqs = 0.03;
	@AnControllerField(
		type= CMLFieldEnum.ControlBlock,
		input="mach.pm - mach.pe - this.refPoint",
		parameter={"type.NoLimit", "this.kqs", "this.tqs"},
		y0="this.tqWashoutBlock.u0"	)
	DelayControlBlock kqsDelayBlock;	
	  
	//1.2 tqWashoutBlock
	public double one = 1.0/*constant*/, tq = 2.0;
	@AnControllerField(
		type= CMLFieldEnum.ControlBlock,
		input="this.kqsDelayBlock.y",
		parameter={"type.NoLimit", "this.one", "this.tq"},
		y0="this.tq1FilterBlock.u0"	)
	WashoutControlBlock tqWashoutBlock;		  
	  
	//1.3 tq1FilterBlock
	public double tq11 = 0.05, tq1 = 0.35;
	@AnControllerField(
		type= CMLFieldEnum.ControlBlock,
		input="this.tqWashoutBlock.y",
		parameter={"type.NoLimit", "this.one", "this.tq11", "this.tq1"},
		y0="this.tq2FilterBlock.u0"	)
	FilterControlBlock tq1FilterBlock;		 	  
	  
	//1.4 tq2FilterBlock
	public double tq21 = 0.0, tq2 = 0.0;
	@AnControllerField(
		type= CMLFieldEnum.ControlBlock,
		input="this.tq1FilterBlock.y",
		parameter={"type.NoLimit", "this.one", "this.tq21", "this.tq2"},
		y0="this.tq3FilterBlock.u0"	)
	FilterControlBlock tq2FilterBlock;	
	  
	//1.5 tq3FilterBlock
	public double tq31 = 0.0, tq3 = 0.0;
	@AnControllerField(
		type= CMLFieldEnum.ControlBlock,
		input="this.tq2FilterBlock.y",
		parameter={"type.NoLimit", "this.one", "this.tq31", "this.tq3"},
		y0="this.gainBlock.u0"	)
	FilterControlBlock tq3FilterBlock;	
	  
	//1.6 gainBlock
	public double vsmax = 0.1, vsmin = -vsmax;
	@AnControllerField(
	  	type= CMLFieldEnum.StaticBlock,
	  	input="this.tq3FilterBlock.y",
	  	parameter={"type.Limit", "this.one", "this.vsmax", "this.vsmin"},
	  	y0="this.gainCustomBlock.u0"  )
	GainBlock gainBlock;
	
	//1.7gainCustomBlock
		public double kg = 1.0/*constant*/, Vcutoff = 0.0, vt0 = 1.0/*constant*/;
	   @AnControllerField(
	      type=CMLFieldEnum.StaticBlock,
	      input="this.gainBlock.y",
	      y0="pss.vs"  )
	   public ICMLStaticBlock gainCustomBlock = new GainBlock() {
		  @Override
		  public boolean initStateY0(double y0) {
			  // at the initial point, set the gain block gain
			  super.k = kg;
			  return super.initStateY0(y0);
		  }

		  @Override
		  public double getY() {
		  	Machine mach = getMachine();
		  	DStabBus dbus = mach.getDStabBus();
		    double vt = mach.getVdq(dbus).abs();
				double deltaVt = vt0 - vt;
		  	//restrict the output
				if(Vcutoff<=0) {
				  return super.getY();
			  }else {
				  if(Math.abs(deltaVt)<=Vcutoff){
				  	return super.getY();
				  }else{
				  	return 0.0;
				  }
			  }
		  }		  
	   };


    // UI Editor panel
// TODO    private static NBBpaSPTypeStabilizerEditPanel _editPanel = new NBBpaSPTypeStabilizerEditPanel();

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
	public BPASPTypeStabilizer() {
            this.setName("BpaSITypePSS");
            this.setCategory("BPA");
	}

     /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter category
     */
	public BPASPTypeStabilizer(final String id, final String name, final String caty) {
            super(id, name, caty);
            // _data is defined in the parent class. However init it here is a MUST
            _data = new BpaSPTypeStabilizerData();
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
    public BpaSPTypeStabilizerData getData() {
        return (BpaSPTypeStabilizerData)_data;
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
        this.tqs = getData().getTqs();
        this.tq = getData().getTq();
        this.tq11 = getData().getTq11();
        this.tq1 = getData().getTq1();
        this.tq21 = getData().getTq21();
        this.tq2 = getData().getTq2();
        this.tq31 = getData().getTq31();
        this.tq3 = getData().getTq3();
        this.vsmax = getData().getVsmax();
        this.Vcutoff = getData().getVcutoff();
        this.vsmin = getData().getVsmin();
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
