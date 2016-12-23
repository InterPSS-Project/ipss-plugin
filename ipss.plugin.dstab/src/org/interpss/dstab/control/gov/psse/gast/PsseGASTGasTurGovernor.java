package org.interpss.dstab.control.gov.psse.gast;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.IntegrationControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.LowValueExpFunction;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateGovernor;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
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
 * date: 02/02/2014
 */
@AnController(
		   input="mach.speed-1.0",
		   output="this.t2DelayBlock.y - this.Dturb*mach.speed +this.Dturb",
		   refPoint="mach.pm",
		   display= {})
public class PsseGASTGasTurGovernor extends AnnotateGovernor{
	public static double k=1;
	public double loadLimit =1.0;
	public double Dturb = 0.0;
	
	//1.1 rGainBlock	
	public double R=0.05,k1=1/R; 
	@AnControllerField(
        type= CMLFieldEnum.StaticBlock,
        input="mach.speed - 1.0",
        parameter={"type.NoLimit", "this.k1"},
        //y0="this.refPoint - this.lValueGate.u0"
        initOrderNumber =-1
        )
GainBlock rGainBlock;

	//1.2 low value gain
	@AnFunctionField(
			type = CMLFieldEnum.FunctionExpression,
			input ={"this.refPoint - this.rGainBlock.y","this.loadLimit + this.ktGainBlock.y"}
			)
	LowValueExpFunction lValueGate;
			
	
	//1.3 t1 Delay
	public double t1=0.4, vmin = -0.05, vmax = 1.0	;// input as PU unit;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.lValueGate.y",
        parameter={"type.NonWindup", "this.k", "this.t1","this.vmax","this.vmin"},
        y0="this.t2DelayBlock.u0",	
        initOrderNumber =1)
DelayControlBlock t1DelayBlock;


	//1.4 t2 delay
	public double t2 = 0.1;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.t1DelayBlock.y",
        parameter={"type.NoLimit", "this.k", "this.t2"},
        y0="mach.pm")
DelayControlBlock t2DelayBlock;

	
	//1.5 t3 delay
	public double t3 = 3.0;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.t2DelayBlock.y",
        parameter={"type.NoLimit", "this.k", "this.t3"},
        feedback=true)
DelayControlBlock t3DelayBlock;
	
	//TODO should this be treated as a feedback
	//1.6 Kt gain
	public double kt = 2.0;
	@AnControllerField(
	        type= CMLFieldEnum.StaticBlock,
	        input="this.loadLimit-this.t3DelayBlock.y",
	        parameter={"type.NoLimit", "this.kt"},
	        feedback=true)
	GainBlock ktGainBlock;


	    
	    public PsseGASTGasTurGovernor() {
	        this.setName("PsseGASTGasTurGovernor");
	        this.setCategory("PSSE");
	    }
	    
	    /**
	     * Constructor
	     *
	     * @param id excitor id
	     * @param name excitor name
	     */
	    public PsseGASTGasTurGovernor(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. However init it here is a MUST
	        _data = new PsseGASTGasTurGovernorData();
	    }
	    
	    /**
	     * Get the excitor data
	     *
	     * @return the data object
	     */
	    public PsseGASTGasTurGovernorData getData() {
	        return (PsseGASTGasTurGovernorData)_data;
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
            this.kt = getData().getKt();
            this.loadLimit = getData().getLoadLimit();
            this.R= getData().getR();
            this.Dturb =getData().getDturb();
            this.vmax = getData().getVMax();
            this.vmin = getData().getVMin();
	        k1=1/R;
	   
	        
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
