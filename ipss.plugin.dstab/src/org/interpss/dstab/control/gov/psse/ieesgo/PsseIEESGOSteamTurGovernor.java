package org.interpss.dstab.control.gov.psse.ieesgo;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.AnnotateGovernor;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.block.GainBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * This model is corresponding to the PSSE GAST type gas-turbine governor
 * 
 * The load reference, [VAR(L)], is set equal to shaft power PMECH, when the model is 
 * initialized during activity STRT

  The load-limited feedback path only controls fuel flow to 
  the gas turbine through the low valve gate, when its output is lower than the original load 
   reference
 * 
 * @author Tony Huang
 * date: 02/03/2014
 */
@AnController(
		   input="mach.speed-1.0",
		   output="this.k20*this.t4DelayBlock.y + this.k30*this.t5DelayBlock.y + this.t6DelayBlock.y ",
		   refPoint="this.plimitBlock.u0 + this.t3DelayBlock.y",
		   display= {})
public class PsseIEESGOSteamTurGovernor extends AnnotateGovernor{
	public double k=1;
	
	
	//1.1 T1, T2 led-lag block
	public double t1 =0, t2 =0;
	@AnControllerField(
	        type= CMLFieldEnum.ControlBlock,
	        input="mach.speed - 1.0",
	        parameter={"type.NoLimit", "this.k","this.t2","this.t1"},
	        y0="this.t3DelayBlock.u0"	)
	FilterControlBlock filterBlock;
	
	
	//1.2 T3 governor delay block, including the K1, gov droop
	public double t3=0.4, k1=20;// input as PU unit;
	@AnControllerField(
		    type= CMLFieldEnum.ControlBlock,
		    input="this.filterBlock.y",
		    parameter={"type.NoLimit", "this.k1", "this.t3"},
		    y0="this.refPoint - this.plimitBlock.u0"	)
  DelayControlBlock t3DelayBlock;
	
	
	//1.3 plimit GainBlock	
	public double pmax =1.0,pmin=0; 
	@AnControllerField(
        type= CMLFieldEnum.StaticBlock,
        input="this.refPoint - this.t3DelayBlock.y ",
        parameter={"type.Limit", "this.k","this.pmax", "this.pmin"},
        y0="this.t4DelayBlock.u0"	)
GainBlock plimitBlock;

	
	//1.4 t4 Delay
	public double t4=0.4;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.plimitBlock.y",
        parameter={"type.NoLimit", "this.k", "this.t4"},
        y0="mach.pm"	)
DelayControlBlock t4DelayBlock;


	//1.5 t5 delay
	public double t5 = 0.1, k2 = 0.5;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.t4DelayBlock.y",
        parameter={"type.NoLimit", "this.k2", "this.t5"},
        y0="this.k2*mach.pm")
DelayControlBlock t5DelayBlock;

	
	//1.6 t6 delay
	public double t6 = 3.0, k3 =1.0;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.t5DelayBlock.y",
        parameter={"type.NoLimit", "this.k3", "this.t6"},
        y0="this.k2*this.k3*mach.pm")
DelayControlBlock t6DelayBlock;
	
	//TODO should this be treated as a feedback
	//1.7  1-k2 gain
	public double k20 = 1-k2;
//	@AnControllerField(
//	        type= CMLFieldEnum.StaticBlock,
//	        input="this.t4DelayBlock.y",
//	        parameter={"type.NoLimit", "this.k20"},
//	        y0 = "this.k20*mach.pm")
//	GainBlock k2GainBlock;

    //1.8  1-k3 gain
	public double k30 =1-k3;
//	@AnControllerField(
//	        type= CMLFieldEnum.StaticBlock,
//	        input="this.t5DelayBlock.y",
//	        parameter={"type.NoLimit", "this.k30"},
//	        y0="this.k30*mach.pm")
//	GainBlock k3GainBlock;
	
	    
	    public PsseIEESGOSteamTurGovernor() {
	        this.setName("PsseIEESGOSteamTurGovernor");
	        this.setCategory("PSSE");
	    }
	    
	    /**
	     * Constructor
	     *
	     * @param id excitor id
	     * @param name excitor name
	     */
	    public PsseIEESGOSteamTurGovernor(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. However init it here is a MUST
	        _data = new PsseIEESGOSteamTurGovernorData();
	    }
	    
	    /**
	     * Get the excitor data
	     *
	     * @return the data object
	     */
	    public PsseIEESGOSteamTurGovernorData getData() {
	        return (PsseIEESGOSteamTurGovernorData)_data;
	    }
	    
	    /**
	     *  Init the controller states
	     *
	     *  @param msg the SessionMsg object
	     */
	    @Override
		public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
	        this.t1 = getData().getT1();
	        this.t2 = getData().getT2();
	        this.t3 = getData().getT3();
            this.k1 = getData().getK1();
           
            this.pmax = getData().getPmax();
            this.pmin = getData().getPmin();
            
            this.t4 = getData().getT4();
            this.t5 = getData().getT5();
            this.t6 = getData().getT6();
            this.k2 = getData().getK2();
            this.k3 = getData().getK3();
	        this.k20 =1-k2;
	        this.k30 =1-k3;
	   
	        
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
