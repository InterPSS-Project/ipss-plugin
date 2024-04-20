package org.interpss.dstab.dynLoad.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.DynLoadCMPLDW;
import org.interpss.dstab.dynLoad.DynLoadVFreqDependentModel;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.LD1PAC;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabObjectFactory;
//import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.dynLoad.DStabDynamicLoadFactory;
import com.interpss.dstab.dynLoad.DistNetworkEquivalentModel;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;


/**
 *  Initialization process:
		1.	Get total load P & Q, system bus V from power flow
		2.	Add low-side bus and load bus to Ymatrix
		3.	Add xfmr and feeder to Y matrix
		4.	Compute low-side bus voltage with tap = 1.
		5.	Adjust LTC tap to put compensated voltage at midpoint of Vmin, Vmax
		6.	Compute low-side and load bus voltages. (If load bus voltage is < 0.95, reduce Rfdr and Xfdr to bring it above 0.95.)
		7.	Initialize motor models and static load models 鈥� obtain total load component Q
		8.	Set Bf1 [= Fb*Bf] and Bf2 [=(1-Fb)*Bf] to match total load Q
		9.	If Bf < 0. (inductive), reduce Bss to make Bf = 0.
		10.	If (Fb > 0. or Bss changed) iterate steps 6,7,8,9 to convergence on load bus voltage.


	Calculations during normal running:
	-	source mode: (before network solution)
    	Use low-side voltage, load voltage, and frequency from previous network solution
	    Compute current injection at load bus from motor and static load models.
	    If LTC tap has changed, compute current injections at system and low-side buses to reflect tap change.
	-   network mode:  (iteration with network solution)
	    Update current injection at load bus from motor and static load models based on change in load bus voltage.
	    algebra mode:  (after network solution)
	-	Check for tripping conditions and modify models as required
	    rate mode:  (diff. equation update)
	-	Update derivatives of state variables in motor models

 *
 */

public class DynLoadCMPLDWImpl extends DynLoadModelImpl implements DynLoadCMPLDW {
	
	
	protected  DistNetworkEquivalentModel distEquiv = null;
	protected  InductionMotor             indMotorA = null;
	protected  InductionMotor             indMotorB = null;
	protected  InductionMotor             indMotorC = null;
	protected  LD1PAC                     ac1PMotor = null;
	protected  DynLoadVFreqDependentModel staticLoad = null;
	
	protected BaseDStabBus<?,?> lowBus  = null;
	protected BaseDStabBus<?,?> loadBus  = null;
	
	protected BaseDStabBus<?,?> parentBus  = null;  
	
	protected DStabBranch distXfr = null;
	protected DStabBranch distFdr = null;
	
	protected String groupId = "";
			
	protected Complex totalLoad = null;
	
	protected double fMotorA = 0.0;
	protected double fMotorB = 0.0;
	protected double fMotorC = 0.0;
	protected double fMotorD = 0.0;
	protected double fEle = 0.0;
	
	protected int motorAType = 0;
	protected int motorBType = 0;
	protected int motorCType = 0;
	protected int motorDType = 0;
	
	protected double Pdg = 0.0;
	protected double Qdg = 0.0;
	protected double mvaBase = 0.0;

	
	/*
	private int lowBusNumStartIdx =900000; 
	private int loadBusNumStartIdx =1000000;
	*/
	private String lowBusId = "_lowBus";
	private String loadBusId = "_loadBus";
	
	private double VloadBusMin = 0.95;
	// if this is true, when volt_loadbbus<0.95 during initializing the model, adjust the xfr tap first before trying to reduce the equivalent feeder impedance
	// otherwise, adjust the impedance first
	private boolean initModelAdjustTapFirst = false; 
	
	
	public DynLoadCMPLDWImpl(){
		
	}
	
	public DynLoadCMPLDWImpl(String referenceId, BaseDStabBus abus){
		this.groupId = referenceId;
		this.distEquiv = DStabDynamicLoadFactory.eINSTANCE.createDistNetworkEquivalentModel();
		this.indMotorA = new  InductionMotorImpl();
		this.indMotorB = new  InductionMotorImpl();
		this.indMotorC = new  InductionMotorImpl();
		this.ac1PMotor = new  LD1PACImpl();
		this.staticLoad= new  DynLoadVFreqDependentModelImpl();
		
		this.setDStabBus(abus);
		
	}
	
  
    public boolean initStates(){
    	boolean initflag = true;
    	
		this.totalLoad = new Complex(this.getDStabBus().getLoadP(), this.getDStabBus().getLoadQ()); // or getInitLoad()
		
		// set parentBus to nonLoad, and remove all the loads
		this.getDStabBus().setLoadCode(AclfLoadCode.NON_LOAD);
		this.getDStabBus().setLoadP(0.0);
		this.getDStabBus().setLoadQ(0.0);
		if(this.getDStabBus().getContributeLoadList().size()>0){
			this.getDStabBus().getContributeLoadList().clear();
		}
    	
    	// note: convert MVA base
    	this.mvaBase = this.getMvaBase();
    	
    	double systemMVABase = this.getDStabBus().getNetwork().getBaseMva();
    	
    	/*
    	 *  1) MVABase> 0 means DistEquivMVABase= MVABase
			2) MVABase< 0 means DistEquivMVABase= Abs(NetMW/MVABase)
			3) MVABase= 0 means DistEquivMVABase= Abs(NetMW/0.8)
			
			Note: This is a function of NetMW, so that means MW of the distributed generation
    	 */
    	
    	double netMW = this.totalLoad.getReal()*systemMVABase;
    	
    	if(this.mvaBase > 0){ // exact mva
    		
    	}	
    	else if(this.mvaBase<0){
    		this.mvaBase = Math.abs(netMW/this.mvaBase);
    	}
    	else{
    		this.mvaBase = Math.abs(netMW/0.8);
    	}
    	
    	this.getDistEquivalent().setMva(this.mvaBase);
    	
    	//1. create the low-voltage bus and the load bus
  
    	
    		//add the bus connected to the low voltage side of the distribution transformer
			lowBus  = DStabObjectFactory.createDStabBus(this.getDStabBus().getId().concat(lowBusId), 
					                                        (DStabilityNetwork) this.getDStabBus().getNetwork()).get();
			
			lowBus.setBaseVoltage(1.0); // affacts dynamic model u/i/z multiplier calculation
			
			
			//set SortNumber
			lowBus.setSortNumber(this.getDStabBus().getNetwork().getNoBus()-1);
			
			//set area and zone info
			lowBus.setArea(this.getDStabBus().getArea());
			
			lowBus.setZone(this.getDStabBus().getZone());
			
			
			//add the load bus
			loadBus  = DStabObjectFactory.createDStabBus(this.getDStabBus().getId().concat(loadBusId), 
                    (BaseDStabNetwork)this.getDStabBus().getNetwork()).get();
			
			loadBus.setBaseVoltage(1.0);
			
			loadBus.setLoadCode(AclfLoadCode.CONST_P); // must set the load code
			
			//set SortNumber
			loadBus.setSortNumber(this.getDStabBus().getNetwork().getNoBus()-1);
			
			//set area and zone info
			loadBus.setArea(this.getDStabBus().getArea());
			
			loadBus.setZone(this.getDStabBus().getZone());
    	
    	//2. create the xfr and feeder
    	
        //map xfr data, need to convert data to system base, if mva_base is not system mva.
    	
    	
  	    try {
			distXfr = DStabObjectFactory.createDStabBranch(this.getDStabBus().getId(), lowBus.getId(), (DStabilityNetwork) this.getDStabBus().getNetwork());
			distXfr.setBranchCode(AclfBranchCode.XFORMER);
		} catch (InterpssException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
  	    //distXfr.setFromBus(parentBus);
  	    //distXfr.setToBus(lowBus);
  	    
  	    
  	    double  mvaConvFactor = systemMVABase/this.mvaBase;
  	    
  	    double Xxf = this.getDistEquivalent().getXXf();
  	    
  	    distXfr.setZ(new Complex(0, Xxf*mvaConvFactor));
  	    
  	    double Tfixls =this.getDistEquivalent().getTFixLS();
  	    double Tfixhs =this.getDistEquivalent().getTFixHS();
  	    double tap = 1.0;
  	    double tap_step = this.getDistEquivalent().getStep();
  	    double Tmin = this.getDistEquivalent().getTMin();
  	    double Tmax = this.getDistEquivalent().getTMax();
  	    
  	    double Vmin = this.getDistEquivalent().getVMin();
	    double Vmax = this.getDistEquivalent().getVMax();
	    
  	
  	  	//map distribution feeder data, need to convert data to system base, if mva_base is not system mva.
    	
    	try {
			distFdr = DStabObjectFactory.createDStabBranch(lowBus.getId(), loadBus.getId(), (DStabilityNetwork) this.getDStabBus().getNetwork());
		} catch (InterpssException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	
  	    //distFdr.setFromBus(lowBus);
  	    //distFdr.setToBus(loadBus);
  	    
  	    distFdr.setZ(new Complex(this.getDistEquivalent().getRFdr(),this.getDistEquivalent().getXFdr()).multiply(mvaConvFactor));
  	    
  	   
  	    
  	    // add Bss as a shunt at low bus
  	   double Bss = this.getDistEquivalent().getBSubStation()/mvaConvFactor;
  	   lowBus.setShuntY(new Complex(0,Bss));
  	    
    	
  
  	    // check if the feeder MVABase = system mvabase
  	    double fdrMVABase = this.distEquiv.getMva();
  	    
  	    Complex VtransBus = this.getDStabBus().getVoltage();
  	    double  Vmag_trans = VtransBus.abs();
  	    
  	    Complex ItransBus = this.totalLoad.divide(VtransBus).conjugate();
    	
    	//3. calculate tap = 1.0 such that Vmag_lowBus = (Vmin + Vmax)/2
    	
  	     double Xxfr_pu = distXfr.getZ().getImaginary()*Tfixhs*Tfixhs;
  	     
  	     double Vmag_lowBus = (Vmin + Vmax)/2;
  	     
  	     double Pld = this.totalLoad.getReal();
  	     double Qld = this.totalLoad.getImaginary();
  	     
  	     // Tap = sqrt((Vlf_mag*V2_mag)^2/((Qlf_pu*Xxfr_pu-Vlf_mag^2)^2+(Xxfr_pu*Plf_pu)^2))
  	     
  	     tap = Math.sqrt(Math.pow(Vmag_trans*Vmag_lowBus,2)/(Math.pow((Qld*Xxfr_pu-Vmag_trans*Vmag_trans),2)+Math.pow((Xxfr_pu*Pld),2)));
  	    	
  	     //check the validity of the tap
  	     
  	     boolean tapReachLimit = false;
  	     
  	     if(tap<Tmin){
  	    	 tap = Tmin;
  	    	 tapReachLimit = true;
  	     }
  	     else if(tap > Tmax){
  	    	 tap = Tmax;
  	    	 tapReachLimit = true;
  	     }
  	     
  	     if(!tapReachLimit){
  	    	 long tapNum = Math.round((tap-1)/tap_step);
  	    	 tap = 1+tapNum*tap_step;
  	     }
  	     
  	     
  	    distXfr.setFromTurnRatio(1.0);
  	    distXfr.setToTurnRatio(tap);
  	    
  	    double tap0 = tap;
        
        //4. forward step network solution to obtain the voltage at low and load buses 
  	     
  	     // Vhs - voltage at the high voltage side of the transformer
  	     // Vhs = Vlf- i*Xxfr_pu*Tfixhs^2*Ilf_pu
  	     Complex Vhs =  VtransBus.subtract(this.distXfr.getZ().multiply(Math.pow(Tfixhs, 2)).multiply(ItransBus));
         
  	     
  	     // VlowBus - voltage at the low voltage side of the transformer
  	     
  	     Complex VlowBus = Vhs.multiply(Tfixls*tap/Tfixhs);
  	     this.lowBus.setVoltage(VlowBus);
  	     
  	     // ITolowBus - current following from the xfr into the low bus
  	     
  	     Complex ItolowBus = ItransBus.multiply(Tfixhs/tap/Tfixls);
  	     
  	     // IBss - Bss charging current
  	     
  	     Complex IBss = VlowBus.multiply(lowBus.getShuntY());
  	     
  	     // ItoloadBus - current flowing into the load bus
  	     
  	     Complex ItoloadBus = ItolowBus.subtract(IBss);
  	     
  	     // VloadBus - voltage at the load bus
  	     Complex VloadBus = VlowBus.subtract(this.distFdr.getZ().multiply(ItoloadBus));
  	     
  	     // check if the Vload bus larger than 0.95 pu
  	     
  	     if(VloadBus.abs() <VloadBusMin){
  	    	 
  	    	 // this is the default setting, adjusting the equivalent feeder impedance first;
  	    	 if(!this.initModelAdjustTapFirst){
	  	    	 VloadBus = reduceZfdrToIncreaseVloadBus(VlowBus, ItoloadBus, VloadBus);
  	    	 }
  	    	 
  	        // this is an improved initialization approach, adjusting the Tap first, then adjust the feeder impedance if necessary
  	    	 else{
  	    		 if(tap <Tmax){
  	    			long tapNumMax = Math.round((Tmax-1)/tap_step);
  	    			
  	    			tap = 1+tapNumMax*tap_step;
  	    			
  	    			distXfr.setToTurnRatio(tap);
  	    	        
  	    	        //4. forward step network solution to obtain the voltage at low and load buses 
  	    	  	     
  	    	  	     // Vhs - voltage at the high voltage side of the transformer
  	    	  	     // Vhs = Vlf- i*Xxfr_pu*Tfixhs^2*Ilf_pu
  	    	  	     Vhs =  VtransBus.subtract(this.distXfr.getZ().multiply(Math.pow(Tfixhs, 2)).multiply(ItransBus));
  	    	         
  	    	  	     
  	    	  	     // VlowBus - voltage at the low voltage side of the transformer
  	    	  	     
  	    	  	     VlowBus = Vhs.multiply(Tfixls*tap/Tfixhs);
  	    	  	    
  	    	  	     
  	    	  	     // ITolowBus - current following from the xfr into the low bus
  	    	  	     
  	    	  	     ItolowBus = ItransBus.multiply(Tfixhs/tap/Tfixls);
  	    	  	     
  	    	  	     // IBss - Bss charging current
  	    	  	     
  	    	  	     IBss = VlowBus.multiply(lowBus.getShuntY());
  	    	  	     
  	    	  	     // ItoloadBus - current flowing into the load bus
  	    	  	     
  	    	  	     ItoloadBus = ItolowBus.subtract(IBss);
  	    	  	     
  	    	  	     // VloadBus - voltage at the load bus
  	    	  	     VloadBus = VlowBus.subtract(this.distFdr.getZ().multiply(ItoloadBus));
  	    	  	    
  	    	  	     if(VloadBus.abs()>=VloadBusMin){
  	    	  	    	 // there are some possibilities to further adjust the Tap
  	    	  	    	 //long newTapNumUp = tapNumMax;
  	    	  	    	 long newTapNumLow = Math.round((tap0-1)/tap_step);
  	    	  	    	 for(long newTapNum =tapNumMax;newTapNum>= newTapNumLow; newTapNum--){
  	    	  	    		 
  	    	  	    		
	  	    	  	    	tap = 1+newTapNum*tap_step;
	  	  	    			
	  	  	    			distXfr.setToTurnRatio(tap);
	  	  	    	        
	  	  	    	        //4. forward step network solution to obtain the voltage at low and load buses 
	  	  	    	  	     
	  	  	    	  	     // Vhs - voltage at the high voltage side of the transformer
	  	  	    	  	     // Vhs = Vlf- i*Xxfr_pu*Tfixhs^2*Ilf_pu
	  	  	    	  	     Vhs =  VtransBus.subtract(this.distXfr.getZ().multiply(Math.pow(Tfixhs, 2)).multiply(ItransBus));
	  	  	    	         
	  	  	    	  	     
	  	  	    	  	     // VlowBus - voltage at the low voltage side of the transformer
	  	  	    	  	     
	  	  	    	  	     VlowBus = Vhs.multiply(Tfixls*tap/Tfixhs);
	  	  	    	  	    
	  	  	    	  	     
	  	  	    	  	     // ITolowBus - current following from the xfr into the low bus
	  	  	    	  	     
	  	  	    	  	     ItolowBus = ItransBus.multiply(Tfixhs/tap/Tfixls);
	  	  	    	  	     
	  	  	    	  	     // IBss - Bss charging current
	  	  	    	  	     
	  	  	    	  	     IBss = VlowBus.multiply(lowBus.getShuntY());
	  	  	    	  	     
	  	  	    	  	     // ItoloadBus - current flowing into the load bus
	  	  	    	  	     
	  	  	    	  	     ItoloadBus = ItolowBus.subtract(IBss);
	  	  	    	  	     
	  	  	    	  	     // VloadBus - voltage at the load bus
	  	  	    	  	     VloadBus = VlowBus.subtract(this.distFdr.getZ().multiply(ItoloadBus));
	  	  	    	  	     
	  	  	    	  	     // update the upper and lower bounds
	  	  	    	  	     if(VloadBus.abs()<VloadBusMin){
	  	  	    	  	            newTapNum = newTapNum+1;
			  	  	    	  	    tap = 1+newTapNum*tap_step;
			  	  	    			
			  	  	    			distXfr.setToTurnRatio(tap);
			  	  	    	        
			  	  	    	        //4. forward step network solution to obtain the voltage at low and load buses 
			  	  	    	  	     
			  	  	    	  	     // Vhs - voltage at the high voltage side of the transformer
			  	  	    	  	     // Vhs = Vlf- i*Xxfr_pu*Tfixhs^2*Ilf_pu
			  	  	    	  	     Vhs =  VtransBus.subtract(this.distXfr.getZ().multiply(Math.pow(Tfixhs, 2)).multiply(ItransBus));
			  	  	    	         
			  	  	    	  	     
			  	  	    	  	     // VlowBus - voltage at the low voltage side of the transformer
			  	  	    	  	     
			  	  	    	  	     VlowBus = Vhs.multiply(Tfixls*tap/Tfixhs);
			  	  	    	  	    
			  	  	    	  	     
			  	  	    	  	     // ITolowBus - current following from the xfr into the low bus
			  	  	    	  	     
			  	  	    	  	     ItolowBus = ItransBus.multiply(Tfixhs/tap/Tfixls);
			  	  	    	  	     
			  	  	    	  	     // IBss - Bss charging current
			  	  	    	  	     
			  	  	    	  	     IBss = VlowBus.multiply(lowBus.getShuntY());
			  	  	    	  	     
			  	  	    	  	     // ItoloadBus - current flowing into the load bus
			  	  	    	  	     
			  	  	    	  	     ItoloadBus = ItolowBus.subtract(IBss);
			  	  	    	  	     
			  	  	    	  	     // VloadBus - voltage at the load bus
			  	  	    	  	     VloadBus = VlowBus.subtract(this.distFdr.getZ().multiply(ItoloadBus));
	  	  	    	  	            
			  	  	    	  	     break;
	  	  	    	  	     }
	  	  	    	  	    
	  	  	    	  	    	 
  	    	  	    		 
  	    	  	    	 } // end-of-while-loop
  	    	  	    	 
  	    	  	        this.lowBus.setVoltage(VlowBus);
  	    	  	    	 
  	    	  	     } 
  	    	  	     
  	    	  	     else{//Vloadbus<VloadbusMin even when tap at the maximum position, have to reduce the equivalent feeder impedance to increase the load bus voltage
  	    	  	    	 
  	    	  	    	VloadBus = reduceZfdrToIncreaseVloadBus(VlowBus, ItoloadBus, VloadBus);
  	    	  	     }
  	    	  	    
  	    			
  	    		 }
  	    		 else{ // the tap is already at its maximum point, no further adjustment is available
  	    			 
  	    			VloadBus = reduceZfdrToIncreaseVloadBus(VlowBus, ItoloadBus, VloadBus);
  	    		 }
  	    	 }
  	     }
  	     
  	    //System.out.println("VloadBus = "+VloadBus.abs());
  	    this.loadBus.setVoltage(VloadBus);
  	     
  	    // PQLoadBus - the total load at the load bus
  	    Complex PQLoadBus = VloadBus.multiply(ItoloadBus.conjugate());
  	    
  	    this.loadBus.setLoadP(PQLoadBus.getReal());
  	    this.loadBus.setLoadQ(PQLoadBus.getImaginary());
  	    this.loadBus.setInitLoad(PQLoadBus);
    	
    	//5. connect dynamic load models to the load bus
    	// initialize the dynamic models, obtain the reactive power Qi after initialization
    	// NOTE: the load percentages, the loading factor for calculating the mvaBase should be input by the model data mapper
    	
    	
/*
    	if(this.fMotorA > 0){
    		if(this.motorAType == 3){
    			this.indMotorA.setDStabBus(loadBus);
    			this.indMotorA.setId(this.getId()+"_A");
    			loadBus.addDynamicLoadModel(indMotorA);
    			this.indMotorA.setLoadPercent(fMotorA*100.0);
    			//this.indMotorA.setLoadFactor(loadingFactor); should be set during the data input stage
    		    //TODO only a temporal solution
    			this.indMotorA.setTpp0(0.0);
    			
    			this.indMotorA.initStates();
    		}
    		else{
    			 IpssLogger.ipssLogger.severe(" The motor A should be a three-phase induction motor. Type = "+this.motorAType);
    		}
    	}
    	else{
    		this.indMotorA = null;
    	}
    	
    	if(this.fMotorB > 0){
    		if(this.motorBType == 3){
    			this.indMotorB.setDStabBus(loadBus);
    			this.indMotorB.setId(this.getId()+"_B");
    			loadBus.addDynamicLoadModel(indMotorB);
    			this.indMotorB.setLoadPercent(fMotorB*100.0);
    			
    			 //TODO only a temporal solution
    			this.indMotorB.setTpp0(0.0);
    			
    			this.indMotorB.initStates();
    		}
    		else{
    			 IpssLogger.ipssLogger.severe(" The motor B should be a three-phase induction motor. Type = "+this.motorBType);
    		}
    	}
    	else{
    		this.indMotorB = null;
    	}
  	    
    	
    	if(this.fMotorC > 0){
    		if(this.motorCType == 3){
    			this.indMotorC.setDStabBus(loadBus);
    			this.indMotorC.setId(this.getId()+"_C");
    			loadBus.addDynamicLoadModel(indMotorC);
    			this.indMotorC.setLoadPercent(fMotorC*100.0);
    			
    			 //TODO only a temporal solution
    			this.indMotorC.setTpp0(0.0);
    			
    			this.indMotorC.initStates();
    		}
    		else{
    			 IpssLogger.ipssLogger.severe(" The motor C should be a three-phase induction motor. Type = "+this.motorCType);
    		}
    	}
    	else{
    		this.indMotorC = null;
    	}
  */  	
    	if(this.fMotorD > 0){
    		if(this.motorDType == 1){
    			this.ac1PMotor.setDStabBus(loadBus);
    			this.ac1PMotor.setId(this.getId());
    			loadBus.addDynamicLoadModel(ac1PMotor);
    			this.ac1PMotor.setLoadPercent(this.fMotorD*100.0);
    			
    			 //TODO only a temporal solution
    			//this.indMotorD.setTpp0(0.0);
    			
    			this.ac1PMotor.initStates();
    		}
    		else{
    			 IpssLogger.ipssLogger.severe(" The motor D should be a 1-phase air conditioner motor. Type = "+this.motorDType);
    		}
    	}
    	else{
    		this.ac1PMotor = null;
    	}
    	
    	//7. calculate the reactive power deficiency, dQ = Qint - sum{Qi}.
    	double dynLoadTotalQ = 0.0;
    	if(this.indMotorA!=null){
    		dynLoadTotalQ = this.indMotorA.getInitLoadPQ().getImaginary();  // this.indMotorA.getMotorLoadQ() returns power in motor mvabase
    	}
    	
    	if(this.indMotorB!=null){
    		dynLoadTotalQ = dynLoadTotalQ + this.indMotorB.getInitLoadPQ().getImaginary();
    	}
    	
    	if(this.indMotorC!=null){
    		dynLoadTotalQ = dynLoadTotalQ + this.indMotorC.getInitLoadPQ().getImaginary();
    	}
    	
    	if(this.ac1PMotor!=null){
    		dynLoadTotalQ = dynLoadTotalQ + this.ac1PMotor.getInitLoadPQ().getImaginary();
    	}
    	
    	
    	//8. Add capacitor bank at load bus side to compensate dQ, i.e., Bfdr = abs(dQ);
    	
    	//TODO since the remaining loads are represented as constant impedance, it is not necessary to calculate the compensation here
    	// if the remaining loads are as Volt/freq depedent loads, then the compensation should be calculated, and the dstabBus initialization process needs to be updated.
    	
//    	double compensateQ = dynLoadTotalQ - PQLoadBus.getImaginary();
//    	double shuntB = compensateQ/VloadBus.abs()/VloadBus.abs();  
//    	
//    	this.loadBus.setShuntY(new Complex(0, shuntB));
    	
  	    
		return initflag ;
    	
    }

	private Complex reduceZfdrToIncreaseVloadBus(Complex VlowBus, Complex ItoloadBus, Complex VloadBus) {
		if(VlowBus.abs()>= VloadBusMin){
			
			 Complex Zfdr = this.distFdr.getZ();
			 
			 double final_ratio =1.0;
			 for (double ratio = 1.0;ratio>=1.0E-2; ratio-=0.01){
			
				Complex newZ =Zfdr.multiply(ratio);
				VloadBus = VlowBus.subtract(newZ.multiply(ItoloadBus));
				
				
				if(VloadBus.abs()>VloadBusMin){
					final_ratio =ratio;
					break;
				}
				
			
			 }
			 
			 
			 this.distFdr.setZ(Zfdr.multiply(final_ratio));
			 
		 }
		 else{
			 IpssLogger.ipssLogger.severe("The calculated voltage of load bus connected to " + this.getDStabBus().getId()+ " is too low (< 0.95, and voltage at the low bus is :"+VlowBus.abs());
		 }
		return VloadBus;
	}
	
	// TODO
	// power electronic 
	// PV based DG model

	@Override
	public void setGroupId(String group_id) {
		this.groupId = group_id;
		
	}

	@Override
	public String getGroupId() {
		
		return this.groupId;
	}

	@Override
	public DistNetworkEquivalentModel getDistEquivalent() {
			
		return this.distEquiv;
	}

	@Override
	public InductionMotor getInductionMotorA() {
		
		return this.indMotorA;
	}

	@Override
	public InductionMotor getInductionMotorB() {
		return this.indMotorB;
	}

	@Override
	public InductionMotor getInductionMotorC() {
		
		return this.indMotorC;
	}

	@Override
	public LD1PAC get1PhaseACMotor() {
		
		return this.ac1PMotor;
	}

	public DynLoadVFreqDependentModel getStaticLoadModel() {
		return this.staticLoad;
	}

	@Override
	public BaseDStabBus<?,?> getLowVoltBus() {
		
		return this.lowBus;
	}

	@Override
	public BaseDStabBus<?,?> getLoadBus() {
		
		return this.loadBus;
	}

	@Override
	public double getFmA() {
		
		return this.fMotorA;
	}

	@Override
	public double getFmB() {
		
		return this.fMotorB;
	}

	@Override
	public double getFmC() {
		
		return this.fMotorC;
	}

	@Override
	public double getFmD() {
		
		return this.fMotorD;
	}

	@Override
	public double getFel() {
		
		return this.fEle;
	}

	@Override
	public void setFmA(double motorAFraction) {
		this.fMotorA=motorAFraction;
		
	}

	@Override
	public void setFmB(double motorBFraction) {
		this.fMotorB = motorBFraction;
		
	}

	@Override
	public void setFmC(double motorCFraction) {
		this.fMotorC = motorCFraction;
		
	}

	@Override
	public void setFmD(double motorDFraction) {
		this.fMotorD = motorDFraction;
		
	}

	@Override
	public void setFel(double electronicLoadFraction) {
		this.fEle = electronicLoadFraction;
		
	}

	@Override
	public int getMotorTypeA() {
		
		return this.motorAType;
	}

	@Override
	public int getMotorTypeB() {
		
		return this.motorBType;
	}

	@Override
	public int getMotorTypeC() {
		
		return this.motorCType;
	}

	@Override
	public int getMotorTypeD() {
		
		return this.motorDType;
	}

	@Override
	public void setMotorTypeA(int motorTypeA) {
		
		this.motorAType = motorTypeA;
		
	}

	@Override
	public void setMotorTypeB(int motorTypeB) {
		this.motorBType = motorTypeB;
		
	}

	@Override
	public void setMotorTypeC(int motorTypeC) {
		this.motorCType = motorTypeC;
		
	}

	@Override
	public void setMotorTypeD(int motorTypeD) {
		this.motorDType = motorTypeD;
		
	}

    // override this function to avoid adding the CMPLDW model to the dynamicBusDevice and load list.
	@Override
	public void setDStabBus(BaseDStabBus<?, ?> newBus) {
		this.parentBus = newBus;
		
	}
	
	@Override
	public BaseDStabBus<?, ?> getDStabBus() {
		return this.parentBus;
	}
	

}
