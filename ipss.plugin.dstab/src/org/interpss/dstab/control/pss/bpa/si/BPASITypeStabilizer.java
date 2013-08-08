/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.pss.bpa.si;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.FilterNthOrderBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateStabilizer;
import com.interpss.dstab.controller.annotate.AbstractChildAnnotateController;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/*
 * Part-1: Define your controller using CML as usual
 * =================================================
 */
@AnController(
        input="mach.speed",
        output="this.filterBlock3.y",
        refPoint="0.0",
        display= {"str.speed,mach.speed",
       					  "str.pe,mach.pe"        }
)

public class BPASITypeStabilizer extends AnnotateStabilizer {
    public double trw = 0.0, t5 = 5, t6 = 5.0, t7 = 5.0;
    @AnControllerField(
            type= CMLFieldEnum.Controller,
            input="mach.speed",
            y0="0.0",
            initOrderNumber=-3	)
    public CustomExciter1 customBlock1 = new CustomExciter1(1.0, trw, t5, t6, t7);

    public double kr = 1.0, trp = 0.0, tw = 0.652, tw1 = 5.0, tw2 = 5.0;
    @AnControllerField(
            type= CMLFieldEnum.Controller,
            input="mach.pe",
            y0="0.0",
            initOrderNumber=-4	)
    public CustomExciter2 customBlock2 = new CustomExciter2(kr, trp, tw, tw1, tw2);

    public double one = 1.0/*constant*/, t10 = 0.1, ks = 1.0;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.customBlock1.y + this.ks*this.customBlock2.y",
            parameter={"type.NoLimit", "this.one", "this.t10"},
            y0="this.filterNthBlock.u0",
            initOrderNumber=-2	)
    DelayControlBlock delayBlock;

    public double t9 = 0.2, t12 = 0.1;
    public int m = 4, n = 1;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.filterNthBlock.y",
            parameter={"this.t9", "this.t12", "this.m", "this.n"},
            y0="this.filterBlock1.u0 - this.refPoint + this.customBlock2.y",
            initOrderNumber=-1	)
    FilterNthOrderBlock filterNthBlock;

    public double kp = 3.0, t1 = 0.2, t2 = 0.03;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.refPoint + this.filterNthBlock.y - this.customBlock2.y",
            parameter={"type.NoLimit", "this.kp", "this.t1", "this.t2"},
            y0="this.filterBlock2.u0"	)
    FilterControlBlock filterBlock1;

    public double t13 = 0.0, t14 = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.filterBlock1.y",
            parameter={"type.NoLimit", "this.one", "this.t13", "this.t14"},
            y0="this.filterBlock3.u0"	)
    FilterControlBlock filterBlock2;

    public double t3 = 0.32, t4 = 0.03, vsmax = 0.1, vsmin = -0.1;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.filterBlock2.y",
            parameter={"type.Limit", "this.one", "this.t3", "this.t4", "this.vsmax", "this.vsmin"},
            y0="pss.vs"	)
    FilterControlBlock filterBlock3;

@AnController(
   output="this.delayBlock.y",
   refPoint="0.0"
)
class CustomExciter1 extends AbstractChildAnnotateController {
    public CustomExciter1(double kr, double tr, double tw, double tw1,  double tw2) {
        super();
        this.tw1 = tw1;
        this.tw2 = tw2;
        this.kr = kr;
        this.tr = tr;
        this.tw = tw;
    }

	 public double one = 1.0, tw2 = 0.05;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.input - this.refPoint",
            parameter={"type.NoLimit", "this.one", "this.tw2"},
            y0="this.washoutBlock2.u0"	)
    WashoutControlBlock washoutBlock1;

	 public double tw = 0.05, tw1 = 0.05, k = tw/tw1;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.washoutBlock1.y",
            parameter={"type.NoLimit", "this.k", "this.tw1"},
            y0="this.delayBlock.u0"	)
    WashoutControlBlock washoutBlock2;

	 public double kr = 1.0, tr = 0.05;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.washoutBlock2.y",
            parameter={"type.NoLimit", "this.kr", "this.tr"},
            y0="this.output"	)
    DelayControlBlock delayBlock;

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

@AnController(
   output="this.delayBlock.y",
   refPoint="0.0"
)
class CustomExciter2 extends AbstractChildAnnotateController {
    public CustomExciter2(double kr, double tr, double tw, double tw1,  double tw2) {
        super();
        this.tw1 = tw1;
        this.tw2 = tw2;
        this.kr = kr;
        this.tr = tr;
        this.tw = tw;
    }

	 public double one = 1.0, tw2 = 0.05;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.input - this.refPoint",
            parameter={"type.NoLimit", "this.one", "this.tw2"},
            y0="this.delayBlock2.u0"	)
    WashoutControlBlock washoutBlock1;

	 public double tw = 0.05, tw1 = 0.05, k = tw/tw1;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.washoutBlock1.y",
            parameter={"type.NoLimit", "this.tw", "this.tw1"},
            y0="this.delayBlock.u0"	)
    DelayControlBlock delayBlock2;

	 public double kr = 1.0, tr = 0.05;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.delayBlock2.y",
            parameter={"type.NoLimit", "this.kr", "this.tr"},
            y0="this.output"	)
    DelayControlBlock delayBlock;

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


    // UI Editor panel
    private static NBBpaSITypeStabilizerEditPanel _editPanel = new NBBpaSITypeStabilizerEditPanel();

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
	public BPASITypeStabilizer() {
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
	public BPASITypeStabilizer(final String id, final String name, final String caty) {
            super(id, name, caty);
            // _data is defined in the parent class. However init it here is a MUST
            _data = new BpaSITypeStabilizerData();
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
    public BpaSITypeStabilizerData getData() {
        return (BpaSITypeStabilizerData)_data;
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
        this.trw = getData().getTrw();
        this.t5 = getData().getT5();
        this.t6 = getData().getT6();
        this.t7 = getData().getT7();
        this.kr = getData().getKr();
        this.trp = getData().getTrp();
        this.tw = getData().getTw();
        this.tw1 = getData().getTw1();
        this.tw2 = getData().getTw2();
        this.t10 = getData().getT10();
        this.ks = getData().getKs();
        this.t9 = getData().getT9();
        this.t12 = getData().getT12();
        this.kp = getData().getKp();
        this.t1 = getData().getT1();
        this.t2 = getData().getT2();
        this.t13 = getData().getT13();
        this.t14 = getData().getT14();
        this.t3 = getData().getT3();
        this.t4 = getData().getT4();
        this.vsmax = getData().getVsMax();
        this.vsmin = getData().getVsMin();
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
