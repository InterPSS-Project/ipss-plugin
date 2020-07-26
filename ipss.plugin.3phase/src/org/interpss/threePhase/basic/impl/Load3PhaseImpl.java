package org.interpss.threePhase.basic.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.DStab3PBus;
import org.interpss.threePhase.basic.Load3Phase;

import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class Load3PhaseImpl extends Load1PhaseImpl implements Load3Phase {

	LoadConnectionType loadConnectType = LoadConnectionType.THREE_PHASE_WYE; // by default three-phase wye;
	double nominalKV = 0;
	Complex3x1 ph3Load = new Complex3x1();
	Complex3x3 equivYabc = new Complex3x3();
	
	PhaseCode  ph = PhaseCode.ABC;

	
	private  Complex3x1 equivCurInj = null;
	
	@Override
	public Complex3x3 getEquivYabc() {
		Complex3x1 vabc = ((DStab3PBus)this.getParentBus()).get3PhaseVotlages();
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
	public Complex3x1 getInit3PhaseLoad() {
		return ph3Load;
	}

	@Override
	public Complex3x1 get3PhaseLoad(Complex3x1 vabc) {
		Complex3x1 loadPQ = ph3Load;
		
//      Complex3x1 vabc =((Bus3Phase)this.getParentBus()).get3PhaseVotlages();
      
		if(this.code ==AclfLoadCode.NON_LOAD){
			code = AclfLoadCode.CONST_P; // by default constant PQ
		}
		
		switch (this.loadConnectType){
		  case THREE_PHASE_WYE:
			  if(this.code==AclfLoadCode.CONST_P){
				  // default 
			  }
			  else if(this.code==AclfLoadCode.CONST_I){
				  loadPQ.a_0 = ph3Load.a_0.multiply(vabc.a_0.abs());
				  loadPQ.b_1 = ph3Load.b_1.multiply(vabc.b_1.abs());
				  loadPQ.c_2 = ph3Load.c_2.multiply(vabc.c_2.abs());
			  }
			  else if(this.code==AclfLoadCode.CONST_Z){
				  double va = vabc.a_0.abs();
				  double vb = vabc.b_1.abs();
				  double vc = vabc.c_2.abs();
				  loadPQ.a_0 = ph3Load.a_0.multiply(va*va);
				  loadPQ.b_1 = ph3Load.b_1.multiply(vb*vb);
				  loadPQ.c_2 = ph3Load.c_2.multiply(vc*vc);
			  }
			  else{
				  throw new Error("Load model type not supported yet!! Bus, load id,model type, phases: "
				          +this.getParentBus().getId()+","+this.getId()+","+this.code+","+this.ph);
			  }
			  break;
			  
			  
		  case THREE_PHASE_DELTA:
			  throw new Error("Connection type not supported yet!! Bus, load id,connectType, phases: "
			          +this.getParentBus().getId()+","+this.getId()+","+this.loadConnectType+","+this.ph);
			  
		  default:
			  
			   throw new Error("Connection type and phases are not consisent!! Bus, load id,connectType, phases: "
		          +this.getParentBus().getId()+","+this.getId()+","+this.loadConnectType+","+this.ph);
			   
		}
		
		return loadPQ;
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
Complex3x1 loadPQ = new Complex3x1();
		
		if(this.code ==AclfLoadCode.NON_LOAD){
			code = AclfLoadCode.CONST_P; // by default constant PQ
		}
		
		if(vabc.absMax()<0.001) // too low voltage, current equals to zero
			return new Complex3x1();
		
		switch (this.loadConnectType){
		  case THREE_PHASE_WYE:
			  if(this.code==AclfLoadCode.CONST_P){
				  // default 
				  double va = vabc.a_0.abs();
				  double vb = vabc.b_1.abs();
				  double vc = vabc.c_2.abs();
				  
				  if(va>this.Vminpu)
				     loadPQ.a_0 = ph3Load.a_0;
				  else
					  loadPQ.a_0 = ph3Load.a_0.multiply(va*va);
				  
				  if(vb>this.Vminpu)
					     loadPQ.b_1 = ph3Load.b_1;
					  else
						  loadPQ.b_1 = ph3Load.b_1.multiply(vb*vb);
				  
				  if(vc>this.Vminpu)
					     loadPQ.c_2 = ph3Load.c_2;
					  else
						  loadPQ.c_2 = ph3Load.c_2.multiply(vc*vc);
				  
			  }
			  else if(this.code==AclfLoadCode.CONST_I){
				  double va = vabc.a_0.abs();
				  double vb = vabc.b_1.abs();
				  double vc = vabc.c_2.abs();
				  
				  loadPQ.a_0 = ph3Load.a_0.multiply(va);
				  loadPQ.b_1 = ph3Load.b_1.multiply(vb);
				  loadPQ.c_2 = ph3Load.c_2.multiply(vc);
			  }
			  else if(this.code==AclfLoadCode.CONST_Z){
				  double va = vabc.a_0.abs();
				  double vb = vabc.b_1.abs();
				  double vc = vabc.c_2.abs();
				  loadPQ.a_0 = ph3Load.a_0.multiply(va*va);
				  loadPQ.b_1 = ph3Load.b_1.multiply(vb*vb);
				  loadPQ.c_2 = ph3Load.c_2.multiply(vc*vc);
			  }
			  else{
				  throw new Error("Load model type not supported yet!! Bus, load id,model type, phases: "
				          +this.getParentBus().getId()+","+this.getId()+","+this.code+","+this.ph);
			  }
			  break;
			  
			  
		  case THREE_PHASE_DELTA:
			  throw new Error("Connection type not supported yet!! Bus, load id,connectType, phases: "
			          +this.getParentBus().getId()+","+this.getId()+","+this.loadConnectType+","+this.ph);
			  
		  default:
			  
			   throw new Error("Connection type and phases are not consisent!! Bus, load id,connectType, phases: "
		          +this.getParentBus().getId()+","+this.getId()+","+this.loadConnectType+","+this.ph);
			   
		}
		
		
		
		return equivCurInj = loadPQ.divide(vabc).conjugate().multiply(-1.0);
	}
	
	@Override
	public Complex getInit3PhaseTotalLoad() {
		
		return ph3Load.a_0.add(ph3Load.b_1).add(ph3Load.c_2);
	}

}
