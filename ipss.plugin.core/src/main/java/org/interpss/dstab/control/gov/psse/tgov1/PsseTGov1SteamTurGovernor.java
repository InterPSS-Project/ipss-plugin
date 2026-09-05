package org.interpss.dstab.control.gov.psse.tgov1;

import java.lang.reflect.Field;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateGovernor;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;

/**
 * This model is corresponding to the PSSE TGOV1 type steam-turbine governor
 * 
 * 
 * @author Tony Huang
 * date: 02/02/2014
 */
@AnController(
		   input="mach.speed-1.0",
		   output="this.ratingScale*this.t2t3FilterBlock.y"
		           + "-this.ratingScale*this.dt*mach.speed+this.ratingScale*this.dt",
		   refPoint="this.rGainBlock.u0",
		   display= {})
public class PsseTGov1SteamTurGovernor extends AnnotateGovernor{
	public double k=1,
		loadLimit =1.0;
	public double dt =0.0;
	public double dbH = 0.0, dbL = 0.0;
	public double ratingScale = 1.0, invRatingScale = 1.0;

	@AnControllerField(
	        type=CMLFieldEnum.StaticBlock,
	        input="mach.speed-1.0",
	        y0="0.0", initOrderNumber=1)
	public AsymmetricDeadbandBlock speedDeadbandBlock = new AsymmetricDeadbandBlock();
	
	//1.1 rGainBlock	
	public double R=0.05,k1=1/R; 
	@AnControllerField(
        type= CMLFieldEnum.StaticBlock,
	        input="this.refPoint-this.speedDeadbandBlock.y",
	        parameter={"type.NoLimit", "this.k1"},
	        y0="this.t1DelayBlock.u0", initOrderNumber=2	)
public GainBlock rGainBlock;

			
	
	//1.2 t1 Delay
	public double t1=0.4, vmin = -0.05, vmax = 1.0	;// input as PU unit;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.rGainBlock.y",
        parameter={"type.NonWindup", "this.k", "this.t1","this.vmax","this.vmin"},
	        y0="this.t2t3FilterBlock.u0", initOrderNumber=3	)
public DelayControlBlock t1DelayBlock;


	//1.3 t2 t3 filter block
	public double t2 = 2, t3 =8;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.t1DelayBlock.y",
        parameter={"type.NoLimit", "this.k", "this.t2","this.t3"},
	        y0="this.invRatingScale*mach.pm", initOrderNumber=4)
public FilterControlBlock t2t3FilterBlock;


	    
	    public PsseTGov1SteamTurGovernor() {
	        this.setName("PsseTGOV1SteamTurGovernor");
	        this.setCategory("PSSE");
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
		public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
	        this.t1 = getData().getT1();
	        this.t2 = getData().getT2();
	        this.t3 = getData().getT3();
            this.R= getData().getR();
            this.dt =getData().getDt();
			this.dbH = getData().getDbH();
			this.dbL = getData().getDbL();
			this.speedDeadbandBlock.setThresholds(dbH, dbL);
			double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
			this.ratingScale = getData().getTrate() > 1.0e-9 && machineMva > 1.0e-9
					? getData().getTrate() / machineMva : 1.0;
			this.invRatingScale = 1.0 / ratingScale;
			double rawMax = getData().getvMax();
			double rawMin = getData().getvMin();
			if (rawMax < rawMin) {
				double swap = rawMax;
				rawMax = rawMin;
				rawMin = swap;
			}
			double initialValve = mach.getPm() * invRatingScale;
			this.vmax = Math.max(rawMax, initialValve);
			this.vmin = Math.min(rawMin, initialValve);
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
