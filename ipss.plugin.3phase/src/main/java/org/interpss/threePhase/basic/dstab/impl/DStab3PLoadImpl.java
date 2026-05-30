package org.interpss.threePhase.basic.dstab.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;

import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class DStab3PLoadImpl extends DStab1PLoadImpl implements DStab3PLoad {

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
			  if(this.code==AclfLoadCode.CONST_P){
				  // default
			  }
			  else if(this.code==AclfLoadCode.CONST_I){
				  loadPQ.a_0 = ph3Load.a_0.multiply(vabc.a_0.subtract(vabc.b_1).abs()/Math.sqrt(3.0));
				  loadPQ.b_1 = ph3Load.b_1.multiply(vabc.b_1.subtract(vabc.c_2).abs()/Math.sqrt(3.0));
				  loadPQ.c_2 = ph3Load.c_2.multiply(vabc.c_2.subtract(vabc.a_0).abs()/Math.sqrt(3.0));
			  }
			  else if(this.code==AclfLoadCode.CONST_Z){
				  double vab = vabc.a_0.subtract(vabc.b_1).abs()/Math.sqrt(3.0);
				  double vbc = vabc.b_1.subtract(vabc.c_2).abs()/Math.sqrt(3.0);
				  double vca = vabc.c_2.subtract(vabc.a_0).abs()/Math.sqrt(3.0);
				  loadPQ.a_0 = ph3Load.a_0.multiply(vab*vab);
				  loadPQ.b_1 = ph3Load.b_1.multiply(vbc*vbc);
				  loadPQ.c_2 = ph3Load.c_2.multiply(vca*vca);
			  }
			  else{
				  throw new Error("Load model type not supported yet!! Bus, load id,model type, phases: "
				          +this.getParentBus().getId()+","+this.getId()+","+this.code+","+this.ph);
			  }
			  break;

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

		if(vabc.absMax()<0.001) { // too low voltage, current equals to zero
			return new Complex3x1();
		}

		switch (this.loadConnectType){
		  case THREE_PHASE_WYE:
			  if(this.code==AclfLoadCode.CONST_P){
				  // default
				  double va = vabc.a_0.abs();
				  double vb = vabc.b_1.abs();
				  double vc = vabc.c_2.abs();

				  if(va>this.Vminpu) {
					loadPQ.a_0 = ph3Load.a_0;
				} else {
					loadPQ.a_0 = ph3Load.a_0.multiply(va*va);
				}

				  if(vb>this.Vminpu) {
					loadPQ.b_1 = ph3Load.b_1;
				} else {
					loadPQ.b_1 = ph3Load.b_1.multiply(vb*vb);
				}

				  if(vc>this.Vminpu) {
					loadPQ.c_2 = ph3Load.c_2;
				} else {
					loadPQ.c_2 = ph3Load.c_2.multiply(vc*vc);
				}

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
			  Complex vab = vabc.a_0.subtract(vabc.b_1);
			  Complex vbc = vabc.b_1.subtract(vabc.c_2);
			  Complex vca = vabc.c_2.subtract(vabc.a_0);
			  if(this.code==AclfLoadCode.CONST_P){
				  loadPQ.a_0 = deltaLoad(ph3Load.a_0, vab);
				  loadPQ.b_1 = deltaLoad(ph3Load.b_1, vbc);
				  loadPQ.c_2 = deltaLoad(ph3Load.c_2, vca);
			  }
			  else if(this.code==AclfLoadCode.CONST_I){
				  loadPQ.a_0 = ph3Load.a_0.multiply(vab.abs()/Math.sqrt(3.0));
				  loadPQ.b_1 = ph3Load.b_1.multiply(vbc.abs()/Math.sqrt(3.0));
				  loadPQ.c_2 = ph3Load.c_2.multiply(vca.abs()/Math.sqrt(3.0));
			  }
			  else if(this.code==AclfLoadCode.CONST_Z){
				  double vabMag = vab.abs()/Math.sqrt(3.0);
				  double vbcMag = vbc.abs()/Math.sqrt(3.0);
				  double vcaMag = vca.abs()/Math.sqrt(3.0);
				  loadPQ.a_0 = ph3Load.a_0.multiply(vabMag*vabMag);
				  loadPQ.b_1 = ph3Load.b_1.multiply(vbcMag*vbcMag);
				  loadPQ.c_2 = ph3Load.c_2.multiply(vcaMag*vcaMag);
			  }
			  else{
				  throw new Error("Load model type not supported yet!! Bus, load id,model type, phases: "
				          +this.getParentBus().getId()+","+this.getId()+","+this.code+","+this.ph);
			  }
			  break;

		  default:

			   throw new Error("Connection type and phases are not consisent!! Bus, load id,connectType, phases: "
		          +this.getParentBus().getId()+","+this.getId()+","+this.loadConnectType+","+this.ph);

		}



		equivCurInj = new Complex3x1();
		if(this.loadConnectType == LoadConnectionType.THREE_PHASE_DELTA) {
			Complex iab = loadCurrent(loadPQ.a_0, vabc.a_0.subtract(vabc.b_1));
			Complex ibc = loadCurrent(loadPQ.b_1, vabc.b_1.subtract(vabc.c_2));
			Complex ica = loadCurrent(loadPQ.c_2, vabc.c_2.subtract(vabc.a_0));
			equivCurInj.a_0 = iab.multiply(-1.0).add(ica);
			equivCurInj.b_1 = ibc.multiply(-1.0).add(iab);
			equivCurInj.c_2 = ica.multiply(-1.0).add(ibc);
		}
		else {
			if(vabc.a_0.abs() > this.Vminpu) {
				equivCurInj.a_0 = loadPQ.a_0.divide(vabc.a_0).conjugate().multiply(-1.0);
			}
			if(vabc.b_1.abs() > this.Vminpu) {
				equivCurInj.b_1 = loadPQ.b_1.divide(vabc.b_1).conjugate().multiply(-1.0);
			}
			if(vabc.c_2.abs() > this.Vminpu) {
				equivCurInj.c_2 = loadPQ.c_2.divide(vabc.c_2).conjugate().multiply(-1.0);
			}
		}
		return equivCurInj;
	}

	private Complex deltaLoad(Complex nominalLoad, Complex lineVoltage) {
		double vmag = lineVoltage.abs()/Math.sqrt(3.0);
		if(vmag > this.Vminpu) {
			return nominalLoad;
		}
		return nominalLoad.multiply(vmag*vmag);
	}

	private Complex loadCurrent(Complex load, Complex voltage) {
		if(voltage.abs() < 0.001) {
			return Complex.ZERO;
		}
		return load.divide(voltage).conjugate();
	}

	@Override
	public Complex getInit3PhaseTotalLoad() {

		return ph3Load.a_0.add(ph3Load.b_1).add(ph3Load.c_2);
	}

}
