package org.interpss.dstab.control.gov.bpa.hydro;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.IntegrationControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateGovernor;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * This model is corresponding to the GH type hydro turbine (a combination of speed governing and hydro turbine)
 * 
 * @author Tony Huang
 * date: 05/05/2011
 */
@AnController(
		   input="mach.speed-1.0",
		   output="this.wFilterBlock.y",
		   refPoint="this.delayBlock.u0 + this.rGainBlock.y + this.washoutBlock.y + this.gainBlock.y",
		   display= {})
public class BpaGHTypeHydroGovernor extends AnnotateGovernor{
	public double pmin = 0.0;
	public double pmax = 1.0	;// input as PU unit
	public double r = 0.05;
	public double epsilon=999D;//the corresponding deadBand block NOT implemented yet

	//1.1 GainBlock	
	public double k1=pmax; 
	@AnControllerField(
        type= CMLFieldEnum.StaticBlock,
        input="mach.speed - 1.0",
        parameter={"type.NoLimit", "this.k1"},
        y0="this.refPoint - this.delayBlock.u0 - this.rGainBlock.y - this.washoutBlock.y"	)//*this.r
GainBlock gainBlock;

	//1.2 delayBlock
	public double tg=0.25, k_tg=1/tg, tp=0.04, velClose=0.2, velOpen=0.3, pup = velOpen*pmax, pdown = -velClose*pmax;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.refPoint - this.rGainBlock.y - this.washoutBlock.y - this.gainBlock.y",
        parameter={"type.NonWindup", "this.k_tg", "this.tp","this.pup","this.pdown"},
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
	
	//1.4 rGainBlock
	public double t = 0.0;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.intBlock.y",
        parameter={"type.NoLimit", "this.r", "this.t"},
        feedback=true)
DelayControlBlock rGainBlock;

//1.5 washoutBlock
public double delta = 0.5, td =5.0;
	@AnControllerField(
  			type= CMLFieldEnum.ControlBlock,
  			input= "this.intBlock.y",
   			parameter={"type.NoLimit", "this.delta", "this.td"},
  			feedback=true)
WashoutControlBlock washoutBlock;

	//1.6 wFilterBlock
 public double ktw = 1.0/*constant*/, tw=1.0,tw_2=0.5*tw, t1 = -tw;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.intBlock.y",
            parameter={"type.NoLimit", "this.ktw", "this.t1", "this.tw_2"},
            y0="mach.pm")
FilterControlBlock wFilterBlock;

	    
	    public BpaGHTypeHydroGovernor() {
	        this.setName("bpaGHTypeGovernor");
	        this.setCategory("BPA");
	    }
	    
	    /**
	     * Constructor
	     *
	     * @param id excitor id
	     * @param name excitor name
	     */
	    public BpaGHTypeHydroGovernor(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. However init it here is a MUST
	        _data = new BPAGHTypeGovernorData();
	    }
	    
	    /**
	     * Get the excitor data
	     *
	     * @return the data object
	     */
	    public BPAGHTypeGovernorData getData() {
	        return (BPAGHTypeGovernorData)_data;
	    }
	    
	    /**
	     *  Init the controller states
	     *
	     *  @param msg the SessionMsg object
	     */
	    @Override
		public boolean initStates(DStabBus bus, Machine mach) {
	        this.r = getData().getR();
	        this.tg = getData().getTg();
	        this.tp = getData().getTp();
	        this.td = getData().getTd();
	        this.pmax = getData().getPmax();
	        this.pmin=0;
	        this.velClose=getData().getVelClose();
	        this.velOpen=getData().getVelOpen();
	        this.tw = getData().getTw();
	        this.epsilon=getData().getEpsilon(); 
	        this.delta=getData().getDelta();
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
