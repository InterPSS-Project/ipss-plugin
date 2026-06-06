package org.interpss.threePhase.basic.dstab.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBus;

import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.impl.DStabLoadImpl;

public class DStab1PLoadImpl extends DStabLoadImpl implements DStab1PLoad {


	LoadConnectionType loadConnectType = LoadConnectionType.SINGLE_PHASE_WYE; // by default three-phase wye;
	double nominalKV = 0;
	double Vminpu = 0.85; // pu
	double Vmaxpu = 1.1; // pu
	private int openDssLoadModel = 0;
	private double cvrWatts = 1.0;
	private double cvrVars = 2.0;
	private double[] zipv = null;

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
		if(isOpenDssVoltageModel()) {
			return openDssLoadAtVoltage(this.loadCP, vmag);
		}
		if (this.code == AclfLoadCode.CONST_P){
			return loadAtVoltage(this.loadCP, Complex.ZERO, Complex.ZERO, vmag);
		}
		else if (this.code == AclfLoadCode.CONST_I) {
			return loadAtVoltage(Complex.ZERO, this.loadCI, Complex.ZERO, vmag);
		} else if (this.code == AclfLoadCode.CONST_Z) {
			return loadAtVoltage(Complex.ZERO, Complex.ZERO, this.loadCZ, vmag);
		} else {
			return loadAtVoltage(this.loadCP, this.loadCI, this.loadCZ, vmag);
		}
	}

	protected Complex loadAtVoltage(Complex constP, Complex constI, Complex constZ, double vmag) {
		if (vmag < this.Vminpu) {
			return loadAtTransitionVoltage(constP, constI, constZ, this.Vminpu)
					.multiply(matchedImpedanceScale(vmag, this.Vminpu));
		}
		if (vmag > this.Vmaxpu) {
			return loadAtTransitionVoltage(constP, constI, constZ, this.Vmaxpu)
					.multiply(matchedImpedanceScale(vmag, this.Vmaxpu));
		}
		return loadAtTransitionVoltage(constP, constI, constZ, vmag);
	}

	private Complex loadAtTransitionVoltage(Complex constP, Complex constI, Complex constZ, double vmag) {
		return constP.add(constI.multiply(vmag)).add(constZ.multiply(vmag * vmag));
	}

	protected Complex cvrLoadAtVoltage(Complex nominalLoad, double vmag) {
		if (vmag < this.Vminpu) {
			return cvrLoadAtTransitionVoltage(nominalLoad, this.Vminpu)
					.multiply(matchedImpedanceScale(vmag, this.Vminpu));
		}
		if (vmag > this.Vmaxpu) {
			return cvrLoadAtTransitionVoltage(nominalLoad, this.Vmaxpu)
					.multiply(matchedImpedanceScale(vmag, this.Vmaxpu));
		}
		return cvrLoadAtTransitionVoltage(nominalLoad, vmag);
	}

	private Complex cvrLoadAtTransitionVoltage(Complex nominalLoad, double vmag) {
		return new Complex(nominalLoad.getReal() * Math.pow(vmag, this.cvrWatts),
				nominalLoad.getImaginary() * Math.pow(vmag, this.cvrVars));
	}

	protected boolean isOpenDssVoltageModel() {
		return this.openDssLoadModel == 3 || this.openDssLoadModel == 4
				|| this.openDssLoadModel == 6 || this.openDssLoadModel == 7
				|| this.openDssLoadModel == 8;
	}

	protected Complex openDssLoadAtVoltage(Complex nominalLoad, double vmag) {
		if(this.openDssLoadModel == 8) {
			return zipvLoadAtVoltage(nominalLoad, vmag);
		}
		if (vmag < this.Vminpu) {
			return openDssLoadAtTransitionVoltage(nominalLoad, this.Vminpu)
					.multiply(matchedImpedanceScale(vmag, this.Vminpu));
		}
		if (vmag > this.Vmaxpu) {
			return openDssLoadAtTransitionVoltage(nominalLoad, this.Vmaxpu)
					.multiply(matchedImpedanceScale(vmag, this.Vmaxpu));
		}
		return openDssLoadAtTransitionVoltage(nominalLoad, vmag);
	}

	private Complex openDssLoadAtTransitionVoltage(Complex nominalLoad, double vmag) {
		switch(this.openDssLoadModel) {
		case 3:
		case 7:
			return new Complex(nominalLoad.getReal(),
					nominalLoad.getImaginary() * vmag * vmag);
		case 4:
			return cvrLoadAtTransitionVoltage(nominalLoad, vmag);
		case 6:
			return nominalLoad;
		default:
			return nominalLoad;
		}
	}

	private Complex zipvLoadAtVoltage(Complex nominalLoad, double vmag) {
		if(this.zipv == null || this.zipv.length < 7) {
			return nominalLoad;
		}
		if(vmag < this.zipv[6]) {
			return Complex.ZERO;
		}
		double pScale = this.zipv[0] * vmag * vmag + this.zipv[1] * vmag + this.zipv[2];
		double qScale = this.zipv[3] * vmag * vmag + this.zipv[4] * vmag + this.zipv[5];
		return new Complex(nominalLoad.getReal() * pScale, nominalLoad.getImaginary() * qScale);
	}

	private double matchedImpedanceScale(double vmag, double transitionVoltage) {
		if (transitionVoltage <= 0.0) {
			return vmag * vmag;
		}
		double ratio = vmag / transitionVoltage;
		return ratio * ratio;
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

	@Override
	public void setOpenDssModel4(boolean enabled, double cvrWatts, double cvrVars) {
		if(enabled) {
			setOpenDssLoadModel(4, cvrWatts, cvrVars, null);
		}
		else {
			this.openDssLoadModel = 0;
			this.zipv = null;
		}
	}

	@Override
	public boolean isOpenDssModel4() {
		return this.openDssLoadModel == 4;
	}

	@Override
	public void setOpenDssLoadModel(int modelType, double cvrWatts, double cvrVars, double[] zipv) {
		this.openDssLoadModel = modelType;
		this.cvrWatts = cvrWatts;
		this.cvrVars = cvrVars;
		this.zipv = zipv == null ? null : zipv.clone();
	}

	@Override
	public int getOpenDssLoadModel() {
		return this.openDssLoadModel;
	}

	@Override
	public double[] getOpenDssZipv() {
		return this.zipv == null ? null : this.zipv.clone();
	}

	@Override
	public double getCvrWatts() {
		return this.cvrWatts;
	}

	@Override
	public double getCvrVars() {
		return this.cvrVars;
	}

}
