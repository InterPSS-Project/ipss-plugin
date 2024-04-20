package org.interpss.threePhase.dynamic.model.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.DynamicModel3Phase;

import com.interpss.core.net.impl.NameTagImpl;
import com.interpss.dstab.DStabGen;

public class DStabGen3PhaseAdapterImpl extends DynamicModel3Phase implements DStabGen3PhaseAdapter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private  DStabGen dynGen =null;
	private Complex3x3   zAbc = null;
	private Complex3x1   puPowerAbc = null;
	
	@Override
	public DStabGen getGen() {
		
		return this.dynGen;
	}

	@Override
	public void setGen(DStabGen gen) {
		this.dynGen = gen;
		
	}

	
	@Override
	public void setZabc(Complex3x3 genZAbc) {
		Complex3x3 genZ120 = genZAbc.To120();
		this.dynGen.setPosGenZ(genZ120.aa);
		this.dynGen.setNegGenZ(genZ120.bb);
		this.dynGen.setZeroGenZ(genZ120.cc);
		
	}

	@Override
	public void setZ120(Complex z1, Complex z2, Complex z0) {
		this.dynGen.setPosGenZ(z1);
		this.dynGen.setNegGenZ(z2);
		this.dynGen.setZeroGenZ(z0);
		
	}


	@Override
	public Complex3x3 getZabc(boolean machineMVABase) {
		this.zAbc = null;
		if(this.dynGen.getPosGenZ()!=null && this.dynGen.getZeroGenZ()!=null){
			this.zAbc = new Complex3x3(this.dynGen.getPosGenZ(),this.dynGen.getNegGenZ(),this.dynGen.getZeroGenZ()).ToAbc();
		}
		if(!machineMVABase) 
			  return this.zAbc.multiply(this.dynGen.getZMultiFactor());
		return this.zAbc;
	}
	
	

	@Override
	public Complex3x3 getYabc(boolean machineMVABase) {
		return getZabc(machineMVABase).inv();
	}
	
	@Override
	public Complex3x1 getPower3Phase(UnitType unit) {
		
		//Power = VABC*conj(IgenABC-YgenABC*VABC)
		// pu on system mva base
		Complex3x1 Vabc = ((DStab3PBus)this.dynGen.getParentBus()).get3PhaseVotlages();
		Complex3x1 IinjABC =  getISource3Phase().subtract(getYabc(false).multiply(Vabc)); 
		this.puPowerAbc.a_0 = Vabc.a_0.multiply(IinjABC.a_0.conjugate());
		this.puPowerAbc.b_1 = Vabc.b_1.multiply(IinjABC.b_1.conjugate());
		this.puPowerAbc.c_2 = Vabc.c_2.multiply(IinjABC.c_2.conjugate());
		
		double MVABASE =   this.dynGen.getParentBus().getNetwork().getBaseMva();
		switch(unit){
		 case PU: return this.puPowerAbc;
		 case mVA: 
			 return this.puPowerAbc.multiply(MVABASE);
		 case kVA: return this.puPowerAbc.multiply(MVABASE *1000.0);
		 default: try {
				throw new Exception("The unit should be PU, mVA or kVA");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return this.puPowerAbc;
	}
	
	

	@Override
	public Complex3x1 getISource3Phase() {
		 // Complex3x1(a0,b1,c2)
		Complex ipos = (Complex) this.dynGen.getDynamicGenDevice().getOutputObject();
		return Complex3x1.z12_to_abc(new Complex3x1(new Complex(0,0),ipos, new Complex(0,0)));
	}
	
	public Complex3x1 getIinj2Network3Phase(){
		Complex3x1 iInj =getISource3Phase();
		Complex3x1 Vabc = ((DStab3PBus)this.dynGen.getParentBus()).get3PhaseVotlages();
		
		
		iInj = iInj.subtract(getYabc(false).multiply(Vabc));
		return iInj;
	}


	@Override
	public Object getOutputObject() {
	     return this.getISource3Phase();
	}

	@Override
	public String getScripts() {
		
		throw new UnsupportedOperationException();
	}

	@Override
	public void setScripts(String value) {
		throw new UnsupportedOperationException();
		
	}
}
