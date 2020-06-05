package org.interpss.threePhase.basic.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.Bus3Phase;
import org.interpss.threePhase.basic.Load3Phase;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.impl.DStabLoadImpl;

public class Load3PhaseImpl extends DStabLoadImpl implements Load3Phase {

	Complex3x1 ph3Load = new Complex3x1();
	Complex3x3 equivYabc = new Complex3x3();
	
	//TODO
	// ADD load connection type, either wye- or delta-connected 
	
	private  Complex3x1 equivCurInj = null;
	
	@Override
	public Complex3x3 getEquivYabc() {
		Complex3x1 vabc = ((Bus3Phase)this.getParentBus()).get3PhaseVotlages();
		double va = vabc.a_0.abs();
		double vb = vabc.b_1.abs();
		double vc = vabc.c_2.abs();
		equivYabc.aa = ph3Load.a_0.conjugate().divide(va*va);
		equivYabc.bb = ph3Load.b_1.conjugate().divide(vb*vb);
		equivYabc.cc = ph3Load.c_2.conjugate().divide(vc*vc);
		
		return equivYabc;
	}

	@Override
	public Complex getPhaseLoad(PhaseCode phase) {
		
		switch(phase){
			case A: return ph3Load.a_0;
			case B: return ph3Load.b_1;
			case C: return ph3Load.c_2;
		}
		
		return null;
	}

	@Override
	public void setPhaseLoad(Complex phaseLoad, PhaseCode phase) {
		switch(phase){
		case A:  ph3Load.a_0 =phaseLoad;
		case B:  ph3Load.b_1 =phaseLoad;
		case C:  ph3Load.c_2 =phaseLoad;
	}

	}

	@Override
	public Complex3x1 get3PhaseLoad() {
		return ph3Load;
	}

	@Override
	public void set3PhaseLoad(Complex3x1 threePhaseLoad) {
		
		 ph3Load = threePhaseLoad;
	}

	@Override
	public void initEquivYabc(Complex y1, Complex y2, Complex y0) {
		throw new UnsupportedOperationException();
		
	}

	@Override
	public Complex3x1 getEquivCurrInj(Complex3x1 vabc) {
		return equivCurInj = ph3Load.divide(vabc).conjugate().multiply(-1.0);
	}

}
