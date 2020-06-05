package org.interpss.threePhase.basic.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.Bus3Phase;
import org.interpss.threePhase.basic.Gen3Phase;

import com.interpss.dstab.GeneratorType;
import com.interpss.dstab.impl.DStabGenImpl;

/**
 *  NOTE: 
 *  for synchronous machine based generators, total generation is equally divided among three-phases;
 *  
 *  for inverter-based generators, total generation is equal to the positive sequence part power.
 *	
 * 
 * @author Qiuhua
 *
 */
public class Gen3PhaseImpl extends DStabGenImpl implements Gen3Phase {
	
	private Complex3x3   zAbc = null;
	private Complex3x3   yAbc = null;
	private Complex3x1   puPowerAbc = null;
	
	private Bus3Phase parentBus3P = null;

	private Complex3x1 igen3Ph = null;
	
	private GeneratorType    genType = GeneratorType.INVERTER_BASED; // inverter-based by default 
	
	
	


	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Override
	public void setZabc(Complex3x3 genZAbc) {
		this.zAbc = genZAbc;
		
	}

	@Override
	public void setZabc(Complex z1, Complex z2, Complex z0) {
		 this.zAbc = new Complex3x3(z1,z2,z0).ToAbc();
		
	}


	@Override
	public Complex3x3 getZabc(boolean machineMVABase) {
		if(this.zAbc == null){
			if(this.getPosGenZ()!=null && this.getZeroGenZ()!=null){
				setZabc(this.getPosGenZ(),this.getNegGenZ(),this.getZeroGenZ());
			}
			else
				return null;
		}
		if(!machineMVABase) 
			  return this.zAbc.multiply(this.getZMultiFactor());
		return this.zAbc;
	}
	
	

	@Override
	public Complex3x3 getYabc(boolean machineMVABase) {
		
	     if(yAbc==null && getZabc(true)!=null)
			 yAbc=getZabc(true).inv();
		
	     if(yAbc!=null) 
	    	   if(machineMVABase)
				  return yAbc;
	    	   else
	    		  return this.yAbc.multiply(1/this.getZMultiFactor());
	           
		return null;
	}
	
	

	@Override
	public Complex3x1 getPower3Phase(UnitType unit) {
		switch(unit){
		 case PU: return this.puPowerAbc;
		 case mVA: return this.puPowerAbc.multiply(this.getMvaBase()/3.0);
		 case kVA: return this.puPowerAbc.multiply(this.getMvaBase()*1000.0/3.0);
		 default: try {
				throw new Exception("The unit should be PU, mVA or kVA");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return this.puPowerAbc;
	}
	
	@Override
	public void setPower3Phase(Complex3x1 genPQ,UnitType unit) {
		
		switch(unit){
		 case PU:   this.puPowerAbc =   genPQ; break;
		 case mVA:  this.puPowerAbc =   genPQ.multiply(3.0/this.getMvaBase()); break;
		 case kVA:  this.puPowerAbc =   genPQ.multiply(3.0/1000.0/this.getMvaBase());break;
		 default: try {
				throw new Exception("The unit should be PU, mVA or kVA");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		
	}
	

	public Bus3Phase getParentBus(){
		if(super.getParentBus() instanceof Bus3Phase)
		     return (Bus3Phase) super.getParentBus();
		else
			try {
				throw new Exception("The parent bus is not a Bus3Phase");
			} catch (Exception e) {
				
				e.printStackTrace();
			}
			return null;
	}

	@Override
	public Complex3x1 getPowerflowEquivCurrInj() {
		
		Complex3x1 currInj = new Complex3x1();
		
		
		// calculate the equivalent current injection based on the GenType
		if(this.getGenType().equals(GeneratorType.SYNCH_MACHINE)){
			currInj = this.getPower3Phase(UnitType.PU).divide(this.getParentBus().get3PhaseVotlages()).conjugate();
		}
		else if(this.getGenType().equals(GeneratorType.INVERTER_BASED)){
			
			//TODO here assuming the inverter based DG only produces positive sequence current. negative sequence current is based on 
			//negative sequence impedance of the generator
			
			//step-1 calculate the total generation
			Complex genPQ = this.getGen();
			Complex ipos = new Complex(0,0);
			Complex ineg = new Complex(0,0);
			Complex izero = new Complex(0,0);
			if(genPQ == null || genPQ.abs()==0.0){
			
			}
			else{
			
			//step-2 obtain the terminal positive and negative sequence voltages
			Complex v1 = this.getParentBus().getThreeSeqVoltage().b_1;
			Complex v2 = this.getParentBus().getThreeSeqVoltage().c_2;
			Complex v0 = this.getParentBus().getThreeSeqVoltage().a_0;
			
			//step-3 calculate the desired positive sequence current injection
			
			ipos = genPQ.divide(v1).conjugate(); 
			
			//step-4 calculate the negative sequence current injection
			Complex3x3 zabc = getZabc(false);
//			if(zabc !=null && zabc.abs()>0){
//				Complex z2 = zabc.To120().bb;
//				ineg = v2.divide(z2).multiply(-1.0);
//			}
			
			
			
			//step-5 combine both the positive- and negative-sequence currents and transform it to a three-phase current injection
			currInj = new Complex3x1(new Complex(0,0),ipos,ineg).toABC();
			
			}
				
		}
		
		return currInj;
	}

	@Override
	public GeneratorType getGenType() {
		
		return this.genType;
	}

	@Override
	public void setGenType(GeneratorType type) {
		this.genType = type;
		
	}
	


	
}

	

