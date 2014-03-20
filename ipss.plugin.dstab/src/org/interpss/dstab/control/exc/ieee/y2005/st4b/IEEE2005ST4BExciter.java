package org.interpss.dstab.control.exc.ieee.y2005.st4b;

import java.lang.reflect.Field;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.FilterControlBlock;
import org.interpss.dstab.control.cml.block.GainBlock;
import org.interpss.dstab.control.cml.block.PIControlBlock;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Vector_xy;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.block.ICMLStaticBlock;
import com.interpss.dstab.controller.block.adapt.CMLStaticBlockAdapter;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.funcImpl.DStabFunction;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;


@AnController(
		   input="mach.vt",
		   output="this.customBlock.y",
		   refPoint="this.vrPIBlock.u0  - pss.vs  + this.trDelayBlock.y",
		   display= {})
public class IEEE2005ST4BExciter  extends AnnotateExciter{
	public double k1 = 1.0;/*constant*/
	
	/*
	 * Part-1: Define the blocks
	 * ==============================
	 */
	
	// transducer block
	 public double tr = 0.04;
     @AnControllerField(
          type= CMLFieldEnum.ControlBlock,
          input="mach.vt",
          parameter={"type.NoLimit", "this.k1", "this.tr"},
          y0="mach.vt",//debug = true,
         initOrderNumber=-1 
          )
       DelayControlBlock trDelayBlock;
	
	   //kpr kir-- PI  block
	   public double Kpr =1, Kir=1, vrmax =99, vrmin =-99;
	   @AnControllerField(
			   type =CMLFieldEnum.ControlBlock,
			   input="this.refPoint - this.trDelayBlock.y + pss.vs",
			   parameter={"type.NonWindup", "this.Kpr", "this.Kir","this.vrmax","this.vrmin"},
			   y0="this.taDelayBlock.u0"
			   )
	   PIControlBlock vrPIBlock;

	   //taDelayBlock----Ka/(1+sTa) with limits
	   public double ka = 1.0, ta = 0.01;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.vrPIBlock.y",
		   parameter={"type.NoLimit", "this.ka", "this.ta"},
		   y0="this.vmPIBlock.u0 + this.kgGainBlock.y"//,
		   //debug = true
		   )//initOrderNumber = 3
	   DelayControlBlock taDelayBlock;
	
	
     //KG feedback, gain with upper limit
	   
	   public double  kg =1.0;
	   @AnControllerField(
		   type= CMLFieldEnum.StaticBlock,
		   input="this.customBlock.y",
		   parameter={"type.NoLimit", "this.kg"},//, "this.tg"
		   feedback = true//,
		   //debug=true
		   )
	   GainBlock kgGainBlock;
	   
	 /*
	  *NOTE: VOEL LVGate block is omitted 
	  */
	   
	  //KPM KIM---PI CONTROL NON-Windup limits
	  public double Kpm = 10.0, Kim = 0.01,vmmax = 9,vmmin = -9.0;
	  @AnControllerField(
			   type =CMLFieldEnum.ControlBlock,
			   input="this.taDelayBlock.y - this.kgGainBlock.y",
			   parameter={"type.NonWindup", "this.Kpm", "this.Kim","this.vmmax","this.vmmin"},
			   y0="this.customBlock.u0"
			   )
	   PIControlBlock vmPIBlock;
	   
	   
	   public double kc = 1.0, kp = 2.0, ki = 1.0, vbmax = 10.0, angKp_deg =0.0, xl =1.0;
	   @AnControllerField(
	      type= CMLFieldEnum.StaticBlock,
	      input= "this.vmPIBlock.y", 
	      y0="mach.efd"
	      )
	   public ICMLStaticBlock customBlock = new CMLStaticBlockAdapter() {
	      private LimitType limit = new LimitType(vbmax, 0.0);
	      private double VB = 0.0;
	     
	      @Override
	      public boolean initStateY0(double y0) {
	    	  VB = calcVB(calcVe());
	    	  if(VB ==0.0){
	    		  IpssLogger.getLogger().severe("Error: VB of IEEE 2005 ST4B exciter is 0 for initialization, @ "+getMachine().getId() );
	    	      return false;
	    	  }
	          this.u = y0/VB;
	          //System.out.println("Y0, VB, u ="+y0+","+VB+","+u);
	          return true;
	      }
	      @Override
	      public double getU0(){
	    	  return this.u;
	      }
	      
	     
	      @Override
	      public void eulerStep1(double u, double dt) {
	         this.u = u;
	      }
	      @Override
	      public void eulerStep2(double u, double dt) {
	         this.u = u;
	      }
	      @Override
	      public double getY() {
	         VB = calcVB(calcVe());
	         return this.u * VB;
	      }
	      private double calcVe(){
	    	  double angleKp = Math.toRadians(angKp_deg);
			   Complex kpCplx = new Complex( kp*Math.cos(angleKp), 
			            kp*Math.sin(angleKp));
			   double ve = 1.0;
			   Machine mach =(Machine) eInternalContainer();
			 
			   Complex vt = mach.getParentGen().getParentBus().getVoltage();
			   Complex it = mach.getIxy();
			   // ve = |kp*vt_ + j*(ki+kp_*xl)*it_|
			   ve = (vt.multiply(kp).add(new Complex(0,1).multiply((kpCplx.multiply(xl).add(ki)).multiply(it)))).abs();
			  
			  // System.out.println("ve ="+ve);
			   return ve;
	    	  
	      }
	      
	      private double calcVB(double ve){
	    	 // if(getMachine()!=null)
	    	  VB = ve*fexFunc(ve, getMachine().calculateIfd(MachineIfdBase.EXCITER));
	    	  return VB = this.limit.limit(VB);
	    	  
	      }
	      private double fexFunc (double ve, double ifd){
	    	  double In = kc *ifd/ve;
	  		if (In <= 0.0)
	  			return 1.0;
	  		else if (In > 0.0 && In <= 0.433)
	  			return 1.0 - 0.5777 * In;
	  		else if (In > 0.433 && In < 0.75)
	  			return Math.sqrt(0.75 - In * In);
	  		else if (In >= 0.75 && In <= 1.0)
	  			return 1.732 * (1.0 - In);
	  		else
	  			return 0.0; // In > 1.0
	      }
	   };
	   

	 

	/*
	 * Part-2: Define the contructors
	 * ==============================
	 */

	    /**
	     * Default Constructor
	     *
	     */
	    public IEEE2005ST4BExciter() {
		this("id", "name", "caty");
	        this.setName("IEEE2005ST4B");
	        this.setCategory("IEEE");
	    }

	     /**
	     * Constructor
	     *
	     * @param id exciter id
	     * @param name exciter name
	     * @param caty exciter category
	     */
	    public IEEE2005ST4BExciter(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. your need to initialize with
	        // the correct type, the data object to be edited
	        _data = new IEEE2005ST4BExciterData();
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
	    public IEEE2005ST4BExciterData getData() {
	        return (IEEE2005ST4BExciterData)_data;
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
	    	this.tr = getData().getTr();
	    	
	        this.Kpr = getData().getKpr();
	        this.Kir = getData().getKir();
	        this.ka  = 1;
	        this.ta  = getData().getTa();
	        this.vrmax = getData().getVrmax();
	        this.vrmin = getData().getVrmin();
	        
	        this.Kpm  = getData().getKpm();
	        this.Kim  = getData().getKim();
	        this.vmmax = getData().getVmmax();
	        this.vmmin = getData().getVmmin();
	        
	        this.kg   = getData().getKg();
	        this.kp = getData().getKp();
	        this.ki = getData().getKi();
	        this.angKp_deg = getData().getAngKp();
	        this.xl    =getData().getXl();
	        
	        this.kc = getData().getKc();
	        
	        this.vbmax = getData().getVbmax();
	        
	        this.k1 =1.0;
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
//	    @Override
//	    public Object getEditPanel() {
//	        _editPanel.init(this);
//	        return _editPanel;
//	    }

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
