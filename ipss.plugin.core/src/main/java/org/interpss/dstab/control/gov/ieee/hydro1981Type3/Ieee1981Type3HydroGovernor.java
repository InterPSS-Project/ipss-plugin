package org.interpss.dstab.control.gov.ieee.hydro1981Type3;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateGovernor;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * This model is corresponding to the 1981 type 3 hydro turbine 
 * 
 * @author Tony Huang
 * date: 9/25/2013
 */
@AnController(
		   input="mach.speed-1.0",
		   output="this.wFilterBlock.y",
		   refPoint="this.delayBlock.u0 + this.sigmaGainBlock.y + this.washoutBlock.y + this.gainBlock.y",
		   display= {})
public class Ieee1981Type3HydroGovernor extends AnnotateGovernor{
	public double pmin = 0.0;
	public double pmax = 1.0	;// input as PU unit
	public double sigma=0.05;//permanent speed droop coefficient

	//1.1 GainBlock	
	public double k1=1.0; 
	@AnControllerField(
        type= CMLFieldEnum.StaticBlock,
        input="mach.speed - 1.0",
        parameter={"type.NoLimit", "this.k1"},
        y0="this.refPoint - this.delayBlock.u0 - this.sigmaGainBlock.y - this.washoutBlock.y"	)
GainBlock gainBlock;

	//1.2 delayBlock
	public double tg=0.25, k_tg=1/tg, tp=0.04, velClose=0.2, velOpen=0.3;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.refPoint - this.sigmaGainBlock.y - this.washoutBlock.y - this.gainBlock.y",
        parameter={"type.NonWindup", "this.k_tg", "this.tp","this.velOpen","this.velClose"},
        y0="this.intBlock.u0"	)
DelayControlBlock delayBlock;

	//1.3 intBlock
	public double k_it=1.0/*constant*/ ;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.delayBlock.y",
        parameter={"type.Limit", "this.k_it", "this.pmax","this.pmin"},
        y0="this.wFilterBlock.u0"	)
IntegrationControlBlock intBlock;
	
	//1.4 sigmaGainBlock -- permanent droop compensation
	public double t = 0.0;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.intBlock.y",
        parameter={"type.NoLimit", "this.sigma", "this.t"},
        feedback=true)
DelayControlBlock sigmaGainBlock;

//1.5 washoutBlock
public double delta = 0.5, tr =5.0;
	@AnControllerField(
  			type= CMLFieldEnum.ControlBlock,
  			input= "this.intBlock.y",
   			parameter={"type.NoLimit", "this.delta", "this.tr"},
  			feedback=true)
WashoutControlBlock washoutBlock;

	//1.6 wFilterBlock
 public double a23 = 1.0, a11 = 0.5, a13 = 1.0, a21 =1.5, tw=1.0, t1 = (a11-a13*a21/a23)*tw,tw_2=a11*tw;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.intBlock.y",
            parameter={"type.NoLimit", "this.a23", "this.t1", "this.tw_2"},
            y0="mach.pm")
FilterControlBlock wFilterBlock;

	    
	    public Ieee1981Type3HydroGovernor() {
	        this.setName("Ieee1981Type3HydroGovernor");
	        this.setCategory("InterPSS");
	    }
	    
	    /**
	     * Constructor
	     *
	     * @param id excitor id
	     * @param name excitor name
	     */
	    public Ieee1981Type3HydroGovernor(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. However init it here is a MUST
	        _data = new Ieee1981Type3HydroGovernorData();
	    }
	    
	    /**
	     * Get the excitor data
	     *
	     * @return the data object
	     */
	    public Ieee1981Type3HydroGovernorData getData() {
	        return (Ieee1981Type3HydroGovernorData)_data;
	    }
	    
	    /**
	     *  Init the controller states
	     *
	     *  @param msg the SessionMsg object
	     */
	    @Override
		public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
	        this.tg = getData().getTg();
	        this.tp = getData().getTp();
	        this.tr = getData().getTr();
	        this.pmax = getData().getPmax();
	        this.pmin = getData().getPmin();
	        this.velClose=getData().getVelClose();
	        this.velOpen=getData().getVelOpen();
	        this.tw = getData().getTw();
	        this.sigma=getData().getSigma(); 
	        this.delta=getData().getDelta();
	        this.a11 = getData().getA11();
	        this.a13 = getData().getA13();
	        this.a21 = getData().getA21();
	        this.a23 = getData().getA23();
	        
	        k_tg=1/tg;
	        t1 = (a11-a13*a21/a23)*tw;
	        tw_2=a11*tw;
	        
	        return super.initStates(bus, mach);
	    }
	    
	    
	
	
    @Override
	public AnController getAnController() {
    	return getClass().getAnnotation(AnController.class);  
    }
    
    @Override
	public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);
    }
    @Override
	public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);
    }

}
