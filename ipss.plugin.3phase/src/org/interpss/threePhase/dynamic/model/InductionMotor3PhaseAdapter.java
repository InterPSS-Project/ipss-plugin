package org.interpss.threePhase.dynamic.model;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.Bus3Phase;

import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.dstab.dynLoad.InductionMotor;


/**
 *  Currently the modeling is focusing on three-phase balanced and almost balanced condition;
 *  When significant unbalanced condition is considered, negative sequence effect 
 *  should be considered. The negative sequence rotor transient is not considered, while 
 *  its contribution to the network and to the torque are considered. When interfaced
 *  with the 3 phase network model, the negative sequence is represented by fixed impedance (Ra+X').
 *  The difference between the actual value (depends on the slip) and this fixed value should
 *  be compensated by negative sequence current injection. 
 *  
 *  Also, the slip needs to be updated according to dSLIP_dt = (Tm-Tep+Ten)/2H
 *  
 * @author Qiuhua
 *
 */
public class InductionMotor3PhaseAdapter extends DynLoadModel3Phase {
    
	private InductionMotor indMotor = null;
	
	private Complex3x3 Zabc = null;
	private Complex3x3 Yabc = null;
	private Complex3x3 Z120 = null;
	
	public InductionMotor3PhaseAdapter(){
		
	}
	
    public InductionMotor3PhaseAdapter(InductionMotor motor){
		this.indMotor = motor;
		this.parentBus = (Bus3Phase) motor.getDStabBus();
		
		this.loadPercent = this.indMotor.getLoadPercent();
		this.indMotor.setLoadPercent(-100); // such that the load percent is not used, used the initLoadPQ instead
	}
	
	@Override
	public void setZabc(Complex3x3 ZAbc) {
		throw new UnsupportedOperationException();
		
	}

	@Override
	public void setZ120(Complex z1, Complex z2, Complex z0) {
		throw new UnsupportedOperationException();
		
	}

	@Override
	public Complex3x3 getZabc(boolean machineMVABase) {
		
		//converted from Yabc
		return getYabc(machineMVABase).inv();
		
	}

	@Override
	public Complex3x3 getYabc(boolean machineMVABase) {
		
		double sysMVABase = this.indMotor.getDStabBus().getNetwork().getBaseMva();
		double zMultiFactor=sysMVABase/this.indMotor.getMvaBase(); // convert z from motor base to system base
        
		if(Yabc == null){
	        // y1 = y2 = equivYSysBase, y0 = 0.0;
			
			Complex y1 = this.indMotor.getPosSeqEquivY();
			
			// Yabc on machine base
			Yabc = new Complex3x3(y1,y1,new Complex(0)).ToAbc().multiply(zMultiFactor);  
		}
		
		if(machineMVABase)
			 return Yabc;
		else
		   return Yabc.multiply(1.0/zMultiFactor);
	}

	@Override
	public Complex3x1 getPower3Phase(UnitType unit) {
		//Power = VABC*conj(equivYABC*VABC - IgenABC)

		Complex3x1 Vabc = ((Bus3Phase)this.indMotor.getDStabBus()).get3PhaseVotlages();
		Complex3x1 Iabc = getIinj2Network3Phase();
		Complex3x1 Pabc = Vabc.multiply(Iabc.conjugate());

		return Pabc;
		
	}

	@Override
	public Complex3x1 getISource3Phase() {
		Complex ipos = (Complex) this.indMotor.getOutputObject();
		
		//TODO negative sequence compensation under unbalanced condition
		return Complex3x1.z12_to_abc(new Complex3x1(new Complex(0,0),ipos, new Complex(0,0)));
		
	}

	@Override
	public Complex3x1 getIinj2Network3Phase() {
		Complex3x1 iInj =getISource3Phase();
		Complex3x1 Vabc = ((Bus3Phase)this.indMotor.getDStabBus()).get3PhaseVotlages();
		
		iInj = iInj.subtract(getYabc(false).multiply(Vabc));
		return iInj;
	}

	@Override
	public boolean initStates() {
		//TODO here assuming balanced
		Complex initMotorLoad = this.getParentBus().get3PhaseTotalLoad().a_0.multiply(this.loadPercent/100.0);
		this.getInductionMotor().setInitLoadPQ(initMotorLoad);
		
		boolean flag = this.getInductionMotor().initStates();
		
		// processing the initLoadPQ3Phase
		Complex initLoad = this.getInductionMotor().getInitLoadPQ();
		
		//TODO
		//assume 3 phase balanced
		this.setInitLoadPQ3Phase(new Complex3x1(initLoad,initLoad,initLoad));
	
		return flag;
	}
	
	
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method) {
		return this.getInductionMotor().nextStep( dt, method);
	}
	
	@Override
	public Object getOutputObject() {
		
		return getISource3Phase() ;
	}

	
	public void setInductionMotor(InductionMotor indMotor) {
		this.indMotor = indMotor;
		
	}

	
	public InductionMotor getInductionMotor() {
		
		return this.indMotor;
	}

	@Override
	public void setLoadPercent(double LdPercent){
		this.loadPercent = LdPercent;
		this.indMotor.setLoadPercent(-100); // such that the load percent is not used, used the initLoadPQ instead
	}
	
	  @Override
	public Hashtable<String, Object> getStates(Object ref) {
		  
		  return this.indMotor.getStates(ref);
	}
	 

	@Override
	public boolean updateAttributes(boolean netChange) {
		return this.getInductionMotor().updateAttributes(netChange);
	}
	
	@Override public String getExtendedDeviceId(){
		return this.indMotor.getExtendedDeviceId();
	}


	@Override
	public double getMVABase(){
		return this.indMotor.getMvaBase();

	}

}
