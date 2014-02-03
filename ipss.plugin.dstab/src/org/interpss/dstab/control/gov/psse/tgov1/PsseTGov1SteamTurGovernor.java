package org.interpss.dstab.control.gov.psse.tgov1;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.IntegrationControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.LowValueExpFunction;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateGovernor;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * This model is corresponding to the PSSE TGOV1 type steam-turbine governor
 * 
 * 
 * @author Tony Huang
 * date: 02/02/2014
 */
@AnController(
		   input="mach.speed-1.0",
		   output="this.t2t3FilterBlock.y - this.dtGainBlock.y",
		   refPoint="this.rGainBlock.u0 + mach.speed - 1.0",
		   display= {})
public class PsseTGov1SteamTurGovernor extends AnnotateGovernor{
	public double k=1,
		loadLimit =1.0;
	
	
	//1.1 rGainBlock	
	public double R=0.05,k1=1/R; 
	@AnControllerField(
        type= CMLFieldEnum.StaticBlock,
        input="this.refPoint-mach.speed + 1.0",
        parameter={"type.NoLimit", "this.k1"},
        y0="this.t1DelayBlock.u0"	)
GainBlock rGainBlock;

			
	
	//1.2 t1 Delay
	public double t1=0.4, vmin = -0.05, vmax = 1.0	;// input as PU unit;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.rGainBlock.y",
        parameter={"type.NonWindup", "this.k", "this.t1","this.vmax","this.vmin"},
        y0="this.t2t3FilterBlock.u0"	)
DelayControlBlock t1DelayBlock;


	//1.3 t2 t3 filter block
	public double t2 = 2, t3 =8;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.t1DelayBlock.y",
        parameter={"type.NoLimit", "this.K", "this.t2","this.t3"},
        y0="mach.pm")
FilterControlBlock t2t3FilterBlock;

	

    //1.7 Dturb
	public double dt =0.0;
	@AnControllerField(
	        type= CMLFieldEnum.StaticBlock,
	        input="mach.speed - 1.0",
	        parameter={"type.NoLimit", "this.dt"},
	        y0="this.t2t3FilterBlock.y - mach.pm")
	GainBlock dtGainBlock;
	
	    
	    public PsseTGov1SteamTurGovernor() {
	        this.setName("Ieee1981Type3HydroGovernor");
	        this.setCategory("InterPSS");
	    }
	    
	    /**
	     * Constructor
	     *
	     * @param id excitor id
	     * @param name excitor name
	     */
	    public PsseTGov1SteamTurGovernor(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. However init it here is a MUST
	        _data = new PsseTGov1SteamTurGovernorData();
	    }
	    
	    /**
	     * Get the excitor data
	     *
	     * @return the data object
	     */
	    public PsseTGov1SteamTurGovernorData getData() {
	        return (PsseTGov1SteamTurGovernorData)_data;
	    }
	    
	    /**
	     *  Init the controller states
	     *
	     *  @param msg the SessionMsg object
	     */
	    @Override
		public boolean initStates(DStabBus bus, Machine mach) {
	        this.t1 = getData().getT1();
	        this.t2 = getData().getT2();
	        this.t3 = getData().getT3();
            this.R= getData().getR();
            this.dt =getData().getDt();
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
