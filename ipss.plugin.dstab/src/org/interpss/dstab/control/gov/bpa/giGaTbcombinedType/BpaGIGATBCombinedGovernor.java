package org.interpss.dstab.control.gov.bpa.giGaTbcombinedType;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NonWindup;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.IntegrationControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.dstab.controller.AnnotateGovernor;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
import com.interpss.dstab.controller.block.ICMLControlBlock;
import com.interpss.dstab.controller.block.ICMLFunction;
import com.interpss.dstab.controller.block.ICMLStaticBlock;
import com.interpss.dstab.controller.block.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.block.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.block.adapt.CMLStaticBlockAdapter;
import com.interpss.dstab.datatype.CMLFieldEnum;

/**
 * This type includes three parts: regulator,servo and turbine, 
 * normally saved in three parts separately, namely the GI/I+, GA/A+ and TB model in BPA
 * 
 * @author Tony Huang
 * date: 05/04/2011
 */

	@AnController(
			   input="mach.speed-1",
			   output="(1+this.lamba)*this.fhp*this.tchDelayBlock.y+(this.fip-this.lambda*this.fhp)*this.trhDelayBlock.y+this.flp*this.tcoDelayBlock.y",
			   refPoint="thiscustomLoadControlBlock.u0 + this.delayBlock.y",
			   display= {"str.Pm,this.output"})
	public class BpaGIGATBCombinedGovernor extends AnnotateGovernor{

		/*
		 * 1. regulator 
		 *   -freqDeadBandFunc
		 *   -delayBlock
		 *   -customLoadControlBlock(shuntPID)
		 *   -customPresserControlBlock(shuntPID)
		 *   -gainBlock
		 *  
		 */
		
		//1.1 freqDeadBandFunc
		public double freqDeadBand=0.002;
		@AnFunctionField( input="mach.speed-1" )
		public ICMLFunction freqDeadBandFunc = new CMLFunctionAdapter() {//TODO a function at the beginning of the main frame, shall the control blocks be assigned init order number?
		    public double eval(double[] dAry) { 
		    	if(Math.abs(dAry[0])<0.5*freqDeadBand) return 0;
		    	else return dAry[0];
		    }          
		};
		
		//1.2 delayBlock
		public double k=16.7, t1=0.02;
		@AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.freqDeadBandFunc.y",
	            parameter={"type.NoLimit", "this.k", "this.t1"},
	            y0="this.freqDeadBandFunc.y0"	)//TODO not sure
	    DelayControlBlock delayBlock;
		
		//1.3 customLoadControlBlock(shuntPID)
		public double kp1=1, kd1=0.02,ki1=0.05,intgMax=99,intgMin=-99,pidMax=105,pidMin=-5;boolean loadSwitchOff=true;
		@AnControllerField(
	            type= CMLFieldEnum.ControlBlock,
	            input="this.delayBlock.y",
	            y0="this.customPresserControlBlock.u0"	)//TODO not sure
		public ICMLControlBlock customLoadControlBlock = new CMLControlBlockAdapter() {
			private IntegrationControlBlock intgBlock = new IntegrationControlBlock(
                    NonWindup, ki1, intgMax, intgMin);
			private WashoutControlBlock difBlock = new WashoutControlBlock(
                    1, kd1);
		       @Override
				public boolean initStateY0(double y0) {//TODO not sure what need to be implemented in this method.
			        if(y0>pidMax||y0<pidMin) {
			        	ipssLogger.warning("CustomBlock init problem: y0 > pidMax or y0 < pidMin");
			            return false;
			         }
			        return true;	
			       }
			       @Override
				public double getU0(){
			         return this.u;
			       }  
			       @Override
				public void eulerStep1(double u, double dt){
			         intgBlock.eulerStep1(u, dt);
			         difBlock.eulerStep1(u, dt);
			       }
			       @Override
				public void eulerStep2(double u, double dt){
			    	   intgBlock.eulerStep2(u, dt);
				       difBlock.eulerStep2(u, dt);
			       }
			       @Override
				public double getY(){
				      double y=this.intgBlock.getY()+this.difBlock.getY()+kp1*this.u;
				      if(y>pidMax) y=pidMax;
				      else if(y<pidMin) y=pidMin;
				      return y;
			       }
			       @Override
				public double getStateX() {
			           return delayBlock.getStateX()+difBlock.getStateX();
			       }
			   };			
			
           //1.4 customPresserControlBlock
			   //TODO not consider presser switch state yet
			   public double kp2=1, kd2=0.02,ki2=0.05,intg2Max=99,intg2Min=-99,pid2Max=105,pid2Min=-5;boolean presserSwitchOff=true;
				@AnControllerField(
			            type= CMLFieldEnum.ControlBlock,
			            input="this.delayBlock.y",
			            y0="this.gainBlock.u0"	)
				public ICMLControlBlock customPresserControlBlock = new CMLControlBlockAdapter() {
					private IntegrationControlBlock intgBlock = new IntegrationControlBlock(
		                    NonWindup, ki2, intg2Max, intg2Min);
					private WashoutControlBlock difBlock = new WashoutControlBlock(
		                    1, kd2);
				       @Override
						public boolean initStateY0(double y0) {//TODO not sure what need to be implemented in this method.
					        if(y0>pid2Max||y0<pid2Min) {
					        	ipssLogger.warning("CustomBlock init problem: y0 > pidMax or y0 < pidMin");
					            return false;
					         }
					        return true;	
					       }
					       @Override
						public double getU0(){
					         return this.u;
					       }  
					       @Override
						public void eulerStep1(double u, double dt){
					         intgBlock.eulerStep1(u, dt);
					         difBlock.eulerStep1(u, dt);
					       }
					       @Override
						public void eulerStep2(double u, double dt){
					    	   intgBlock.eulerStep2(u, dt);
						       difBlock.eulerStep2(u, dt);
					       }
					       @Override
						public double getY(){
						      double y=this.intgBlock.getY()+this.difBlock.getY()+kp2*this.u;
						      if(y>pid2Max) y=pid2Max;
						      else if(y<pid2Min) y=pid2Min;
						      return y;
					       }
					       @Override
						public double getStateX() {
					           return delayBlock.getStateX()+difBlock.getStateX();
					       }
					   };
		 //1.5 GainBlock
		   public double k1 = 1.0,con_Max=106,con_Min=-106;
		    @AnControllerField(
			 type= CMLFieldEnum.StaticBlock,
			 input="this.customPresserControlBlock.y",
			 parameter={"type.Limit", "this.k1", "this.con_Max","this.con_Min"},
			 y0="elechydrControlBlock.u0"	)
	      GainBlock gainBlock;					   
					  
		
		/*
		 * 2. servo
		 *   -elecHydrControlBlock(shuntPID)
		 *   -velLimitBlock
		 *   -rateLimitBlock
		 *   -servoMotorBlock
		 *   -timeDelayBlock
		 *   
		 *  
		 */
		    
		//2.1 elecHydrControlBlock
		    public double kp=1,kd=0.02,ki=0.05,intg3Max=99,intg3Min=-99,pid3Max=105,pid3Min=-5;
			@AnControllerField(
		            type= CMLFieldEnum.ControlBlock,
		            input="this.gainBlock.y-lvdtBlock.y",
		            y0="this.gainBlock.u0"	)
			public ICMLControlBlock elecHydrControlBlock = new CMLControlBlockAdapter() {
				private IntegrationControlBlock intgBlock = new IntegrationControlBlock(
	                    NonWindup, ki, intg3Max, intg3Min);
				private WashoutControlBlock difBlock = new WashoutControlBlock(
	                    1, kd);
			       @Override
					public boolean initStateY0(double y0) {//TODO not sure what need to be implemented in this method.
				        if(y0>pid3Max||y0<pid3Min) {
				        	ipssLogger.warning("CustomBlock init problem: y0 > pidMax or y0 < pidMin");
				            return false;
				         }
				        return true;	
				       }
				       @Override
					public double getU0(){
				         return this.u;
				       }  
				       @Override
					public void eulerStep1(double u, double dt){
				         intgBlock.eulerStep1(u, dt);
				         difBlock.eulerStep1(u, dt);
				       }
				       @Override
					public void eulerStep2(double u, double dt){
				    	   intgBlock.eulerStep2(u, dt);
					       difBlock.eulerStep2(u, dt);
				       }
				       @Override
					public double getY(){
					      double y=this.intgBlock.getY()+this.difBlock.getY()+kp2*this.u;
					      if(y>pid3Max) y=pid3Max;
					      else if(y<pid3Min) y=pid3Min;
					      return y;
				       }
				       @Override
					public double getStateX() {
				           return delayBlock.getStateX()+difBlock.getStateX();
				       }
		   };  
		   
		   //2.2 velLimitBlock
		
		   public double kVEL = 1.0,VELOpen=2.34,VELClose=-0.53;
		    @AnControllerField(
			 type= CMLFieldEnum.StaticBlock,
			 input="this.customPresserControlBlock.y",
			 parameter={"type.Limit", "this.kVEL", "this.VELOpen","this.VELClose"},
			 y0="rateLimitBlock.u0"	)
	      GainBlock velLimitBlock;
		   
		   //2.3 rateLimitBlock
			public double risingLimit=0.25 /*1/Topen*/,fallingLimit=-2.336 /*-1/Tclose*/;
			@AnControllerField(
		            type= CMLFieldEnum.StaticBlock,
		            input="this.velLimitBlock.y",
		            y0="this.servoMotorBlock.u0"	)
			public ICMLStaticBlock rateLimitBlock = new CMLStaticBlockAdapter() {//TODO a function at the beginning of the main frame, shall the control blocks be assigned init order number?
			    double y_last=0;
			    double deltaY=0;
			    double rate=0;
			    boolean rateLimitViolated=false;
			    LimitType rateLimit=new LimitType(risingLimit,fallingLimit);
			    @Override
				public boolean initStateY0(double y0) {//TODO not sure what need to be implemented in this method.
			        this.u=y0;
			        return true;	
			       }
			       @Override
				public double getU0(){
			         return this.u;
			       }  
			       @Override
				public void eulerStep1(double u, double dt){
                     this.u=u;
                     this.rate=(this.u-y_last)/dt;
                     rateLimitViolated=rateLimit.isViolated(rate);
                     if(rateLimitViolated){
                    	 this.rate=rateLimit.limit(this.rate);
                    	 deltaY=this.rate*dt;
                     }

			       }
			       @Override
				public void eulerStep2(double u, double dt){
			    	   this.u=u;
			    	   rate=(this.u-y_last)/dt;
	                     rateLimitViolated=rateLimit.isViolated(rate);
	                     if(rateLimitViolated){
	                    	 this.rate=rateLimit.limit(this.rate);
	                    	 deltaY=this.rate*dt;
	                     }
			       }
			       @Override
				public double getY(){
				      double y=0;
				      if(rateLimitViolated) y=y_last+deltaY;
				      else y=this.u;
				      y_last=y;
				      return y;
			       }    
			};
		   
		   
		   //2.4 servoMotorBlock
		   public double one = 1.0, pmax = 0.2, pmin = -0.2;
		   @AnControllerField(
		           type= CMLFieldEnum.ControlBlock,
		           input= "rateLimitBlock.y",
		           parameter={"type.NonWindup", "this.one", "this.pmax", "this.pmin"},
		           y0="tchDelayBlock.u0")
		   IntegrationControlBlock servoMotorBlock;
		   
		   //2.4 lvdtBlock
			public double kf=1.0, t2=0.02;
			@AnControllerField(
		            type= CMLFieldEnum.ControlBlock,
		            input="this.servoMotorBlock.y",
		            parameter={"type.NoLimit", "this.kf", "this.t2"},
		            feedback=true)//TODO not sure whether the y0 need to be set?
		    DelayControlBlock lvdtBlock;
			
		   //2.5 timeDelayBlock -- not used in most of the time
		   
		   
		
		/*
		 * 3. turbine 
		 *   -tchDelayBlock
		 *   -trhDelayBlock
		 *   -tcoDelayBlock
		 *   -lambdaBlock (this could be eliminated by modifying the model)
		 *   -sumBlock
		 *   
		 *  
		 */
		   
		
		public double fhp=0.3,fip=0.3,flp=0.4,lambda=0.4;
		
		//3.1 tchDelayBlock
		public double kch=1.0, tch=0.02;
		@AnControllerField(
		            type= CMLFieldEnum.ControlBlock,
		            input="this.servoMotorBlock.y",
		            parameter={"type.NoLimit", "this.kch", "this.tch"},
		            y0="this.trhDelayBlock.u0")
		DelayControlBlock tchDelayBlock;
		
		//3.2 trhDelayBlock
		 
		public double krh=1.0, trh=1.2;
		@AnControllerField(
		            type= CMLFieldEnum.ControlBlock,
		            input="this.servoMotorBlock.y",
		            parameter={"type.NoLimit", "this.krh", "this.trh"},
		            y0="this.tcoDelayBlock.u0")
		DelayControlBlock trhDelayBlock;
		
		//3.3 tcoDelayBlock
		 
		public double kco=1.0, tco=1.2,factor=1.0/(fhp+fip+flp);
		@AnControllerField(
		            type= CMLFieldEnum.ControlBlock,
		            input="this.servoMotorBlock.y",
		            parameter={"type.NoLimit", "this.kco", "this.tco"},
		            y0="this.factor*mach.pm")
		DelayControlBlock tcoDelayBlock;
		
		
		
		 /**
	     * Default Constructor
	     *
	     */
	    public BpaGIGATBCombinedGovernor() {
			this("id", "name", "caty");
	        this.setName("Bpa GI+GA+TB combined type Governor");
	        this.setCategory("BPA");
	    }
	    
		
	    public BpaGIGATBCombinedGovernor(String id, String name, String caty) {
	    	 super(id, name, caty);
	         // _data is defined in the parent class. However init it here is a MUST
	         _data = new BpaGIGATBCombinedData();
		}
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
