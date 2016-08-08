package org.interpss.dstab.load.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.load.DynLoadCMPLDW;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.dynLoad.DistNetworkEquivalentModel;
import com.interpss.dstab.dynLoad.DynLoadVFreqDependentModel;
import com.interpss.dstab.dynLoad.InductionMotor;
import com.interpss.dstab.dynLoad.LD1PAC;
import com.interpss.dstab.dynLoad.impl.DynLoadVFreqDependentModelImpl;
import com.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import com.interpss.dstab.dynLoad.impl.LD1PACImpl;


/**
 * •	Initialization process:
		1.	Get total load P & Q, system bus V from power flow
		2.	Add low-side bus and load bus to Ymatrix
		3.	Add xfmr and feeder to Y matrix
		4.	Compute low-side bus voltage with tap = 1.
		5.	Adjust LTC tap to put compensated voltage at midpoint of Vmin, Vmax
		6.	Compute low-side and load bus voltages. (If load bus voltage is < 0.95, reduce Rfdr and Xfdr to bring it above 0.95.)
		7.	Initialize motor models and static load models – obtain total load component Q
		8.	Set Bf1 [= Fb*Bf] and Bf2 [=(1-Fb)*Bf] to match total load Q
		9.	If Bf < 0. (inductive), reduce Bss to make Bf = 0.
		10.	If (Fb > 0. or Bss changed) iterate steps 6,7,8,9 to convergence on load bus voltage.


	Calculations during normal running:
	•	sorc mode: (before network solution)
		Use low-side voltage, load voltage, and frequency from previous network solution
		Compute current injection at load bus from motor and static load models.
		If LTC tap has changed, compute current injections at system and low-side buses to reflect tap change.
	•	netw mode:  (iteration with network solution)
		Update current injection at load bus from motor and static load models based on change in load bus voltage.
	•	alge mode:  (after network solution)
	-	Check for tripping conditions and modify models as required
	•	rate mode:  (diff. equation update)
	-	Update derivatives of state variables in motor models

 * @author Qiuhua
 *
 */

public class DynLoadCMPLDWImpl extends DynamicBusDeviceImpl implements DynLoadCMPLDW{
	
	
	protected String groupId = "";
	protected  DistNetworkEquivalentModel distEquiv = null;
	protected  InductionMotor             indMotorA = null;
	protected  InductionMotor             indMotorB = null;
	protected  InductionMotor             indMotorC = null;
	protected  LD1PAC                     ac1PMotor = null;
	protected  DynLoadVFreqDependentModel staticLoad = null;
	
	protected DStabBus lowBus  = null;
	protected DStabBus loadBus  = null;
	protected DStabBus parentBus  = null;
	
	protected DStabBranch distXfr = null;
	protected DStabBranch distFdr = null;
	
	protected Complex totalLoad = null;
	
	protected double fMotorA = 0.0;
	protected double fMotorB = 0.0;
	protected double fMotorC = 0.0;
	protected double fMotorD = 0.0;
	
	protected int motorAType = 0;
	protected int motorBType = 0;
	protected int motorCType = 0;
	protected int motorDType = 0;
	
//	protected double fElec = 0.0;
//	protected double pfElec = 0.0;
//	protected double vd1 = 0.0;
//	protected double vd2 = 0.0;
	
	protected double Pdg = 0.0;
	protected double Qdg = 0.0;
	
	private double mvaBase = 100.0;
	private int lowBusNumStartIdx =900000; 
	private int loadBusNumStartIdx =1000000;
	private String lowBusId = "_lowBus";
	private String loadBusId = "_loadBus";
	
	
	
	public DynLoadCMPLDWImpl(){
		
	}
	
    public DynLoadCMPLDWImpl(String referenceId){
		this.groupId = referenceId;
		this.distEquiv = new  DistNetworkEquivalentModel();
		this.indMotorA = new  InductionMotorImpl();
		this.indMotorB = new  InductionMotorImpl();
		this.indMotorC = new  InductionMotorImpl();
		this.ac1PMotor = new  LD1PACImpl();
		this.staticLoad= new  DynLoadVFreqDependentModelImpl();
		
	}
    
    @Override
    public boolean initStates(DStabBus abus){
    	boolean initflag = true;
    	
    	parentBus = this.getDStabBus();
		this.totalLoad = parentBus.getLoadPQ(); // or getInitLoad()
    	
    	// note: convert MVA base
    	this.mvaBase = this.getDistEquivalent().getMva();
    	
    	double systemMVABase = abus.getNetwork().getBaseMva();
    	
    	/*
    	 *  1) MVABase> 0 means DistEquivMVABase= MVABase
			2) MVABase< 0 means DistEquivMVABase= Abs(NetMW/MVABase)
			3) MVABase= 0 means DistEquivMVABase= Abs(NetMW/0.8)
			
			Note: This is a function of NetMW, so that means MW –DistMWof the distributed generation
    	 */
    	
    	double netMW = abus.getLoadP()*systemMVABase ;
    	
    	if(this.mvaBase > 0){ // exact mva
    		
    	}	
    	else if(this.mvaBase<0){
    		this.mvaBase = Math.abs(netMW/this.mvaBase);
    	}
    	else{
    		this.mvaBase = Math.abs(netMW/0.8);
    	}
    	
    	
    	
    	//1. create the low-voltage bus and the load bus
  
    	
    	try {
			lowBus  = DStabObjectFactory.createDStabBus(abus.getId().concat(lowBusId), 
					                                        (DStabilityNetwork) abus.getNetwork());
			
			loadBus  = DStabObjectFactory.createDStabBus(abus.getId().concat(loadBusId), 
                    (DStabilityNetwork) abus.getNetwork());
			
		} catch (InterpssException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	
    	//2. create the xfr and feeder
    	
        //map xfr data, need to convert data to system base, if mva_base is not system mva.
    	
    	
  	    distXfr = DStabObjectFactory.createDStabBranch();
  	    distXfr.setFromBus(abus);
  	    distXfr.setToBus(lowBus);
  	    
  	    double  mvaConvFactor = systemMVABase/this.mvaBase;
  	    
  	    double Xxf = this.getDistEquivalent().getXxf();
  	    
  	    distXfr.setZ(new Complex(0, Xxf*mvaConvFactor));
  	    
  	    double Tfixls =this.getDistEquivalent().getTfixLS();
  	    double Tfixhs =this.getDistEquivalent().getTfixHS();
  	    double tap = 1.0;
  	    double tap_step = this.getDistEquivalent().getStep();
  	    double Tmin = this.getDistEquivalent().getTmin();
  	    double Tmax = this.getDistEquivalent().getTmax();
  	    
  	    double Vmin = this.getDistEquivalent().getVmin();
	    double Vmax = this.getDistEquivalent().getVmax();
	    
  	
  	  	//map distribution feeder data, need to convert data to system base, if mva_base is not system mva.
    	
    	distFdr = DStabObjectFactory.createDStabBranch();
    	
    	
  	    distFdr.setFromBus(lowBus);
  	    distFdr.setToBus(loadBus);
  	    
  	    distFdr.setZ(new Complex(this.getDistEquivalent().getRfdr(),this.getDistEquivalent().getXfdr()).multiply(mvaConvFactor));
  	    
  	   
  	    
  	    // add Bss as a shunt at low bus
  	   double Bss = this.getDistEquivalent().getBss()/mvaConvFactor;
  	   lowBus.setShuntY(new Complex(0,Bss));
  	    
    	
  
  	    // check if the feeder MVABase = system mvabase
  	    double fdrMVABase = this.distEquiv.getMva();
  	    
  	    Complex VtransBus = this.parentBus.getVoltage();
  	    double  Vmag_trans = VtransBus.abs();
  	    
  	    Complex ItransBus = this.totalLoad.divide(VtransBus).conjugate();
    	
    	//3. calculate tap = 1.0 such that Vmag_lowBus = (Vmin + Vmax)/2
    	
  	     double Xxfr_pu = distFdr.getZ().getReal()*Tfixhs*Tfixhs;
  	     
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
  	     
        
        //4. forward step network solution to obtain the voltage at low and load buses 
  	     
  	     // Vhs - voltage at the high voltage side of the transformer
  	     // Vhs = Vlf- i*Xxfr_pu*Tfixhs^2*Ilf_pu
  	     Complex Vhs =  VtransBus.subtract(this.distXfr.getZ().multiply(Math.pow(Tfixhs, 2)).multiply(ItransBus));
         
  	     
  	     // VlowBus - voltage at the low voltage side of the transformer
  	     
  	     Complex VlowBus = VtransBus.multiply(Tfixls*tap/Tfixhs);
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
  	     if(VloadBus.abs() <0.95){
  	    	 if(VlowBus.abs()> 0.95){
  	    		 
  	    		 while (VloadBus.abs() < 0.95){ // TODO this approach can be updated to find VloadBus = 0.95
	  	    		Complex newZ = this.distFdr.getZ().multiply(0.5);
	  	    		VloadBus = VlowBus.subtract(newZ.multiply(ItoloadBus));
	  	    		this.distFdr.setZ(newZ);
  	    		 }
  	    		 
  	    	 }
  	    	 else{
  	    		 IpssLogger.ipssLogger.severe("The calculated voltage of load bus connected to " + this.parentBus.getId()+ " is too low (< 0.95, and voltage at the low bus is :"+VlowBus.abs());
  	    	 }
  	     }
  	     
  	     this.loadBus.setVoltage(VloadBus);
  	     
  	    // PQLoadBus - the total load at the load bus
  	    Complex PQLoadBus = VloadBus.multiply(ItoloadBus.conjugate());
  	    
  	    this.loadBus.setLoadPQ(PQLoadBus);
    	
    	//5. connect dynamic load models to the load bus
    	// initialize the dynamic models, obtain the reactive power Qi after initialization
    	// NOTE: the load percentages, the loading factor for calculating the mvaBase should be input by the model data mapper
    	
    	

    	if(this.fMotorA > 0){
    		if(this.motorAType == 3){
    			this.indMotorA.setDStabBus(loadBus);
    			loadBus.addDynamicLoadModel(indMotorA);
    			this.indMotorA.setLoadPercent(fMotorA);
    			//this.indMotorA.setLoadFactor(loadingFactor); should be set during the data input stage
    			
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
    			loadBus.addDynamicLoadModel(indMotorB);
    			
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
    			loadBus.addDynamicLoadModel(indMotorC);
    			
    			this.indMotorC.initStates();
    		}
    		else{
    			 IpssLogger.ipssLogger.severe(" The motor C should be a three-phase induction motor. Type = "+this.motorCType);
    		}
    	}
    	else{
    		this.indMotorC = null;
    	}
    	
    	if(this.fMotorD > 0){
    		if(this.motorDType == 1){
    			this.ac1PMotor.setDStabBus(loadBus);
    			loadBus.addDynamicLoadModel(ac1PMotor);
    			
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
    		dynLoadTotalQ = this.indMotorA.getMotorLoadQ();
    	}
    	
    	if(this.indMotorB!=null){
    		dynLoadTotalQ = dynLoadTotalQ + this.indMotorB.getMotorLoadQ();
    	}
    	
    	if(this.indMotorC!=null){
    		dynLoadTotalQ = dynLoadTotalQ + this.indMotorC.getMotorLoadQ();
    	}
    	
    	if(this.ac1PMotor!=null){
    		dynLoadTotalQ = dynLoadTotalQ + this.ac1PMotor.getInitLoadPQ().getImaginary();
    	}
    	
    	
    	//8. Add capacitor bank at load bus side to compensate dQ, i.e., Bfdr = abs(dQ);
    	double compensateQ = dynLoadTotalQ - PQLoadBus.getImaginary();
    	double shuntB = compensateQ/VloadBus.abs()/VloadBus.abs();  
    	
    	this.loadBus.setShuntY(new Complex(0, shuntB));
    	
  	    
		return initflag ;
    	
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
		// TODO Auto-generated method stub
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

	@Override
	public DynLoadVFreqDependentModel getStaticLoadModel() {
		
		return this.staticLoad;
	}

	@Override
	public DStabBus getLowVoltBus() {
		
		return this.lowBus;
	}

	@Override
	public DStabBus getLoadBus() {
		
		return this.loadBus;
	}

}
