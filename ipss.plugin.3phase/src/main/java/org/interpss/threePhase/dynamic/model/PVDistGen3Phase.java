package org.interpss.threePhase.dynamic.model;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.threePhase.basic.dstab.DStab3PGen;

import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;

/**
 * PV Distributed generation dynamic model.
 * Currently it supports constant PQ mode with converter current limit considered.
 *
 * The effect of negative sequence is modeled as constant negative sequence impedance.
 * @author Qiuhua Huang
 *
 */
public class PVDistGen3Phase extends DynGenModel3Phase{

	private Complex  genPQInit = null;  // based on the positive sequence
	private Complex  posSeqGenPQ = null;
	private Complex  nortonCurSource = null;
	private double   currLimit = 9999;
	private Complex  Ipq_pos = null;


	private double TR = 0.02 ;
	private double vtmeasured = 0;

	// under and over voltage protection
	private double underVoltTripStart = -1.0; // below this voltage, generation starts to trip
	private double underVoltTripAll = -1.0;	// below  this voltage, all generation are tripped
	private double overVoltTripStart = 99.0; // below this voltage, generation starts to trip
	private double overVoltTripAll = 99.0;	// below  this voltage, all generation are tripped

	//TODO control model, constant PQ or Volt/Var control
	private double underFreqTripStart = -1.0; // below this voltage, generation starts to trip
	private double underFreqTripAll = -1.0;	// below  this voltage, all generation are tripped
	private double overFreqTripStart = 99.0; // below this voltage, generation starts to trip
	private double overFreqTripAll = 99.0;	// below  this voltage, all generation are tripped

	private Hashtable<String, Object> states = null;
	private static final String OUT_SYMBOL_P ="PVGenP";
	private static final String OUT_SYMBOL_Q ="PVGenQ";
	private static final String OUT_SYMBOL_V ="PVGenVt";
	private static final String OUT_SYMBOL_I ="PVGenIt";
	private static final String OUT_SYMBOL_IP ="PVGenIp";
	private static final String OUT_SYMBOL_IQ ="PVGenIq";
	private  String extended_device_Id= "";

	private double dVtm_dt = 0, dVtm_dt_1 = 0;

	public PVDistGen3Phase(){
		states = new Hashtable<>();
	}

	public PVDistGen3Phase(DStab3PGen gen) {
		this.parentGen = gen;
		gen.setDynamicGenDevice(this);

		states = new Hashtable<>();
	}

	public double getCurrLimit(){
		return currLimit;
	}

	public void   setCurrLimit(double Ilimit){
		this.currLimit = Ilimit;
	}


	 // obtain the initial positive sequence power flow from the power flow result
	 public void setPosSeqGenPQ(Complex genPQ){
		 this.posSeqGenPQ = genPQ;
	 }


	 public Complex getPosSeqGenPQ(){
		 // if the positive sequence genPQ is not set, then use the phase A genPQ, assuming that all three-phase are the same
		 if(this.posSeqGenPQ == null) {
			 this.posSeqGenPQ = this.getParentGen().getGen();
		 }

		 return this.posSeqGenPQ;
	 }

	 @Override
	public boolean initStates(BaseDStabBus abus){
		 if(this.getPosSeqGenPQ() == null) {
			return false;
		}
		 this.genPQInit = new Complex(this.posSeqGenPQ.getReal(),this.posSeqGenPQ.getImaginary());
		 this.vtmeasured = getPosSeqVt().abs();

		 extended_device_Id = "PVGen3Phase_"+this.getId()+"@"+this.getParentGen().getParentBus().getId();
		 this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, extended_device_Id);

		 if(this.currLimit>99) {
			this.currLimit = this.genPQInit.abs()*2.0;
		}


		 return true;
	 }

	 public Complex getInitGenPQ(){
		 return this.genPQInit;
	 }

	 // for dynamic simulation, in the nextStep, update the this.vtmeasured
     public boolean nextStep(double dt, DynamicSimuMethod method, int flag){
    	 //TODO for the simplified inverter-based PV generation, the only dynamic components is the terminal voltage measurement



    	if(method==DynamicSimuMethod.MODIFIED_EULER) {
    		if(flag ==0) {
    			 dVtm_dt = (getPosSeqVt().abs()-this.vtmeasured)/this.TR;

    	    	 this.vtmeasured = this.vtmeasured + dVtm_dt*dt;
    		}
    		else if (flag ==1) {
    			dVtm_dt_1 = (getPosSeqVt().abs()-this.vtmeasured)/this.TR;

    			this.vtmeasured += 0.5 *(dVtm_dt_1-dVtm_dt)*dt;
    		}
    	}
    	else {
    		throw new Error("Only the Modified Euler method is supported. ");
    	}
    	 return true;
     }

	 @Override
	 public boolean afterStep(double dt){
		 return true;
	 }

     private Complex getPosSeqVt(){
    	 Complex vt = ((BaseAcscBus)this.getParentGen().getParentBus()).getThreeSeqVoltage().b_1;
    	 return vt;
     }

     // calculate the positive current Injection
     // The logic of the following implementation  is based on the "WECC specifications for modeling distributed generation
     // in power flow and dynamics" report
     private Complex calcPosSeqCurInjection(){

    	 double freq = ((BaseDStabBus)this.getParentGen().getParentBus()).getFreq();


    	 //calculate Idq with reference to the terminal voltage angle
    	 double Ip_ord1 = this.getInitGenPQ().getReal()/this.vtmeasured;

    	 double Iq_ord1 = this.getInitGenPQ().getImaginary()/this.vtmeasured;

    	 //
    	 double Ia_order = Math.sqrt(Ip_ord1*Ip_ord1+Iq_ord1*Iq_ord1);


    	 double Ip_prod = Ip_ord1;
    	 double Iq_prod = Iq_ord1;

    	 if(Ia_order > this.currLimit){

    		 double ratio = this.currLimit/Ia_order;

    	     Ip_prod = Ip_ord1*ratio;
    	     Iq_prod = Iq_ord1*ratio;
    	 }


    	 // applied the protection to the Idq value

    	 double fvl = getUnderValueProtectionOutput(this.underVoltTripAll,this.underVoltTripStart,this.vtmeasured);

    	 double fvh = getOverValueProtectionOutput(this.overVoltTripStart,this.overVoltTripAll,this.vtmeasured);


	     double ffl = getUnderValueProtectionOutput(this.underFreqTripAll,this.underFreqTripStart,freq);

    	 double ffh = getOverValueProtectionOutput(this.overFreqTripStart,this.overFreqTripAll, freq);

    	 double protectCutRatio = fvl*fvh*ffl*ffh;

    	 Ip_prod =  Ip_prod*protectCutRatio ;
    	 Iq_prod =  Iq_prod*protectCutRatio ;

         this.Ipq_pos = new Complex(Ip_prod,Iq_prod);



    	 //transfer Idq to Ir_x based on network reference frame;

    	 // |IR|    | cos(Theta)  -sin(Theta)|  |IP|
    	 // |IX|  = | sin(Theta)  cos(Theta) |  |IQ|



    	 double vtAng = ComplexFunc.arg(getPosSeqVt());
    	 double Ir =  Ip_prod*Math.cos(vtAng)-Iq_prod*Math.sin(vtAng);
    	 double Ix =  Ip_prod*Math.sin(vtAng)-Iq_prod*Math.cos(vtAng);

    	 Complex effectiveCurrInj = new Complex(Ir,Ix);

    	 posSeqGenPQ = getPosSeqVt().multiply(effectiveCurrInj.conjugate());

    	 Complex compensateCurrent = new Complex(0,0);
    	 //TODO consider the positive sequence power drawn by the equivalent Ypos at the terminal
    	 if(this.getParentGen().getPosGenZ()!=null && this.getParentGen().getPosGenZ().abs()>0){
    		 Complex zpos = this.getParentGen().getPosGenZ().multiply(this.getParentGen().getZMultiFactor());
    		 compensateCurrent = getPosSeqVt().divide(zpos);
    	 }


    	 nortonCurSource = effectiveCurrInj.add(compensateCurrent);

         return nortonCurSource;

     }

     @Override
		public Hashtable<String, Object> getStates(Object ref) {


			states.put(OUT_SYMBOL_P, this.posSeqGenPQ.getReal());
			states.put(OUT_SYMBOL_Q, this.posSeqGenPQ.getImaginary());
			states.put(OUT_SYMBOL_V, this.getPosSeqVt().abs());
			states.put(OUT_SYMBOL_I, this.getPosSeqIpq().abs());
			states.put(OUT_SYMBOL_IP, this.getPosSeqIpq().getReal());
			states.put(OUT_SYMBOL_IQ, this.getPosSeqIpq().getImaginary());
			return this.states;
		}

     @Override
     public boolean updateAttributes(boolean netChange) {
    	 double vt = getPosSeqVt().abs();

    	 Complex genPQ = this.Ipq_pos.multiply(vt);
    	 //update the positive sequence generation output.
    	 this.setPosSeqGenPQ(genPQ);

    	 //TODO consider other attributes

    	 return true;
     }



     private double getUnderValueProtectionOutput(double lowValue, double upValue, double input){
    	 // check the values
    	 if(lowValue >=upValue){
    		 return 1.0;
    	 }

    	 if(input <=lowValue) {
			return 0;
		} else if(input >=upValue) {
			return 1;
		} else{
    		 return (input-lowValue)/(upValue-lowValue);
    	 }
     }

     private double getOverValueProtectionOutput(double lowValue, double upValue, double input){
    	// check the values
    	 if(lowValue >=upValue){
    		 return 1.0;
    	 }

    	 if(input <=lowValue) {
			return 1;
		} else if(input >=upValue) {
			return 0;
		} else{
    		 return (upValue-input)/(upValue-lowValue);
    	 }
     }



	 // set the positive sequence equivalent current injection as the output object

     @Override
	public Object getOutputObject(){

         this.calcPosSeqCurInjection();
    	 return this.nortonCurSource;
     }

     public Complex getPosSeqIpq() {
    	 if(Ipq_pos==null) {
			calcPosSeqCurInjection();
		}
 		return Ipq_pos;
 	}

    public Complex  getPosSeqnortonCurSource(){
    	return this.nortonCurSource;
    }

    public void    setPosSeqNortonCurSource( Complex Igen){
    	this.nortonCurSource = Igen;
    }

 	public void setPosSeqIpq(Complex ipq_pos) {
 		Ipq_pos = ipq_pos;
 	}

 	public double getUnderVoltTripStart() {
 		return underVoltTripStart;
 	}

 	public void setUnderVoltTripStart(double underVoltTripStart) {
 		this.underVoltTripStart = underVoltTripStart;
 	}

 	public double getUnderVoltTripAll() {
 		return underVoltTripAll;
 	}

 	public void setUnderVoltTripAll(double underVoltTripAll) {
 		this.underVoltTripAll = underVoltTripAll;
 	}

 	public double getOverVoltTripStart() {
 		return overVoltTripStart;
 	}

 	public void setOverVoltTripStart(double overVoltTripStart) {
 		this.overVoltTripStart = overVoltTripStart;
 	}

 	public double getOverVoltTripAll() {
 		return overVoltTripAll;
 	}

 	public void setOverVoltTripAll(double overVoltTripAll) {
 		this.overVoltTripAll = overVoltTripAll;
 	}

 	public double getUnderFreqTripStart() {
 		return underFreqTripStart;
 	}

 	public void setUnderFreqTripStart(double underFreqTripStart) {
 		this.underFreqTripStart = underFreqTripStart;
 	}

 	public double getUnderFreqTripAll() {
 		return underFreqTripAll;
 	}

 	public void setUnderFreqTripAll(double underFreqTripAll) {
 		this.underFreqTripAll = underFreqTripAll;
 	}

 	public double getOverFreqTripStart() {
 		return overFreqTripStart;
 	}

 	public void setOverFreqTripStart(double overFreqTripStart) {
 		this.overFreqTripStart = overFreqTripStart;
 	}

 	public double getOverFreqTripAll() {
 		return overFreqTripAll;
 	}

 	public void setOverFreqTripAll(double overFreqTripAll) {
 		this.overFreqTripAll = overFreqTripAll;
 	}


}
