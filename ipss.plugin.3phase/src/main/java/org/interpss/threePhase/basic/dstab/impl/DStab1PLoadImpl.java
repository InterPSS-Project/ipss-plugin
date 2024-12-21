package org.interpss.threePhase.basic.dstab.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBus;

import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.impl.DStabLoadImpl;

public class DStab1PLoadImpl extends DStabLoadImpl implements DStab1PLoad {


	LoadConnectionType loadConnectType = LoadConnectionType.SINGLE_PHASE_WYE; // by default three-phase wye;
	double nominalKV = 0;
	double Vminpu = 0.85; // pu
	double Vmaxpu = 1.1; // pu

	PhaseCode  ph = PhaseCode.A;  //connected phase(s)

	Complex3x3 equivYabc = new Complex3x3();

	Complex3x1 equivCurInj = null;


	@Override
	public void setPhaseCode(PhaseCode phCode) {
		this.ph = phCode;

	}

	@Override
	public PhaseCode getPhaseCode() {

		return this.ph;
	}

	@Override
	public Complex3x3 getEquivYabc() {

		return this.equivYabc;
	}

	@Override
	public Complex getLoad(double vmag) {
		if (this.code == AclfLoadCode.CONST_P){
			if(vmag>this.Vminpu) {
				return this.loadCP;
			} else {
				return this.loadCP.multiply(vmag*vmag);
			}
		}
		else if (this.code == AclfLoadCode.CONST_I) {
			if(vmag>this.Vminpu) {
				return this.loadCI.multiply(vmag);
			} else {
				return this.loadCI.multiply(vmag*vmag);
			}
		} else if (this.code == AclfLoadCode.CONST_Z) {
			return this.loadCZ.multiply(vmag*vmag);
		} else {
			if(vmag>this.Vminpu) {
				return this.loadCP.add(this.loadCI.multiply(vmag)).add(loadCZ.multiply(vmag*vmag));
			} else {
				return (this.loadCP.add(this.loadCI).add(loadCZ)).multiply(vmag*vmag);
			}
		}
	}

	@Override
	public Complex3x1 getEquivCurrInj(Complex3x1 vabc) {
		equivCurInj  = new Complex3x1();

		DStab3PBus bus = ((DStab3PBus)this.getParentBus());

//		if(bus.getId().equals("113")){
//			System.out.println("Debug bus equiv Current calculation");
//		}
		Complex vt = null;
		double vmag=1.0;

		if(vabc.absMax()<0.001) { // too low voltage, current equals to zero
			return equivCurInj;
		}

		switch (this.loadConnectType){
		  case SINGLE_PHASE_WYE:
			   if(this.ph==PhaseCode.A){
				   vt = vabc.a_0;
				   vmag = vt.abs();
				   equivCurInj.a_0=this.getLoad(vmag).divide(vt).conjugate().multiply(-1);
			   }
			   else if(this.ph==PhaseCode.B){

				   vt = vabc.b_1;
				   vmag = vt.abs();
				   equivCurInj.b_1=this.getLoad(vmag).divide(vt).conjugate().multiply(-1);
			   }
			   else if(this.ph==PhaseCode.C){

				   vt = vabc.c_2;
				   vmag = vt.abs();
				   equivCurInj.c_2=this.getLoad(vmag).divide(vt).conjugate().multiply(-1);

			   }
			   else{
				   throw new Error("Connection type and phases are not consisent!! Bus, load id,connectType, phases: "
			          +bus.getId()+","+this.getId()+","+this.loadConnectType+","+this.ph);
			   }


			   break;

			   /*
			    * phase1
			    *   0-------
			    *           |
			    *          load
			    * phase2    |
			    *   0-------
			    */
		  case SINGLE_PHASE_DELTA:
			   if(this.ph==PhaseCode.AB){
				   vt=vabc.a_0.subtract(vabc.b_1);
				   vmag = vt.abs()/Math.sqrt(3); // the per unit is based on  L-L Volt
				   equivCurInj.a_0=this.getLoad(vmag).divide(vt).conjugate().multiply(-1);
				   equivCurInj.b_1=equivCurInj.a_0.multiply(-1);

			   }
			   else if(this.ph==PhaseCode.BC){
				   vt = vabc.b_1.subtract(vabc.c_2);
				   vmag = vt.abs()/Math.sqrt(3);// the per unit is based on  L-L Volt
				   equivCurInj.b_1=this.getLoad(vmag).divide(vt).conjugate().multiply(-1);
				   equivCurInj.c_2=equivCurInj.b_1.multiply(-1);
			   }
			   else if(this.ph==PhaseCode.AC){
				   vt = vabc.a_0.subtract(vabc.c_2);
				   vmag = vt.abs()/Math.sqrt(3);// the per unit is based on  L-L Volt
				   equivCurInj.a_0=this.getLoad(vmag).divide(vt).conjugate().multiply(-1);
				   equivCurInj.c_2=equivCurInj.a_0.multiply(-1);
			   }
			   else{
				   throw new Error("Connection type and phases are not consisent!! Bus, load id,connectType, phases: "
			          +bus.getId()+","+this.getId()+","+this.loadConnectType+","+this.ph);
			   }

			   break;

		  default:
			  throw new Error("No supported load connection type:"+this.loadConnectType);

		}


		return equivCurInj;
	}

//	@Override
//	public void setLoadModelType(DistLoadType loadModelType) {
//		this.loadType = loadModelType;
//
//	}
//
//	@Override
//	public DistLoadType getLoadModelType() {
//
//		return this.loadType;
//	}

	@Override
	public void setLoadConnectionType(LoadConnectionType loadConnectType) {
		this.loadConnectType = loadConnectType;

	}

	@Override
	public LoadConnectionType getLoadConnectionType() {

		return this.loadConnectType;
	}

	@Override
	public void setNominalKV(double ratedkV) {
		this.nominalKV = ratedkV;

	}

	@Override
	public double getNominalKV() {

		return this.nominalKV;
	}

	@Override
	public void setVminpu(double newVminpu) {
		this.Vminpu = newVminpu;

	}

	@Override
	public void setVmaxpu(double newVmaxpu) {
		this.Vmaxpu = newVmaxpu;

	}

	@Override
	public double getVminpu() {

		return this.Vminpu;
	}

	@Override
	public double getVmaxpu() {

		return this.Vmaxpu;
	}

}
