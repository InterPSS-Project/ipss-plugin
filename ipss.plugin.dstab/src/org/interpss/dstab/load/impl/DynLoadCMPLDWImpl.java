package org.interpss.dstab.load.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.load.DynLoadCMPLDW;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
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
	
	protected DStabBranch distXfr = null;
	protected DStabBranch distFdr = null;
	
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
    	
  	    distXfr = DStabObjectFactory.createDStabBranch();
  	    distXfr.setFromBus(abus);
  	    distXfr.setToBus(lowBus);
  	    
  	    double  mvaConvFactor = systemMVABase/this.mvaBase;
  	    
  	    double Xxf = this.getDistEquivalent().getXxf();
  	    distXfr.setZ(new Complex(0, Xxf*mvaConvFactor));
  	    
  	    //TODO map xfr data, need to convert data to system base, if mva_base is not system mva.
    	
    	distFdr = DStabObjectFactory.createDStabBranch();
    	
    	
  	    distFdr.setFromBus(lowBus);
  	    distFdr.setToBus(loadBus);
  	    
  	    distFdr.setZ(new Complex(this.getDistEquivalent().getRfdr(),this.getDistEquivalent().getXfdr()).multiply(mvaConvFactor));
  	    
  	   
  	    
  	    // add Bss as a shunt at low bus
  	   double Bss = this.getDistEquivalent().getBss()/mvaConvFactor;
  	   lowBus.setShuntY(new Complex(0,Bss));
  	    
    	
    	//TODO map distribution feeder data, need to convert data to system base, if mva_base is not system mva.
    	
  	    // check if the feeder MVABase = system mvabase
  	    double fdrMVABase = this.distEquiv.getMva();
  	    
  	    
  	    
    	
    	//3. set tap = 1.0, use BFS solution technique to find out the total load at the end of load bus
    	// Pint = P*-Rfdr_pu*S^2)
    	// Qint = Q- (Xxfr+Xfdr)*S^2+Bss
    	
  	    
      	// obtain the total load at the parent bus Pload +jQload , set the parent bus as NON_LOAD

    	// iterate the determine the Pload +jQload at the load bus to make sure the total load at system bus is P0+jQ0
  	    
  	    
    	
    	//4. create dynamic load models and connect them to the load bus
    	
    	
    	//5. initialize the dynamic models, obtain the reactive power Qi after initialization
    	
    	
    	
    	//6. calculate the reactive power deficiency, dQ = Qint - sum{Qi}.
    	
    	
    	//7. Add capacitor bank at load bus side to compensate dQ, i.e., Bfdr = abs(dQ);
    	
    	
    	
  	    
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DStabBus getLoadBus() {
		// TODO Auto-generated method stub
		return null;
	}

}
