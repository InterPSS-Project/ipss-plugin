package org.interpss.threePhase.basic.dstab.impl;



import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseXfrAptr;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;

import com.interpss.core.threephase.Static3PXformer;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.impl.DStabBranchImpl;

public class Dstab3PBranchImpl extends DStabBranchImpl implements DStab3PBranch {

	private Complex3x3 Zabc =null;
	private Complex3x3 Yabc =null;
	private Complex3x3 fromShuntYabc =null;
	private Complex3x3 toShuntYabc =null;
	private Complex3x1 currInjAtFromBus = null;
	private Complex3x1 currInjAtToBus = null;

	private Complex3x3 toBusVabc2FromBusVabcMatrix = null;
	private Complex3x3 toBusIabc2FromBusVabcMatrix = null;
	private Complex3x3 toBusVabc2FromBusIabcMatrix = null;
	private Complex3x3 toBusIabc2FromBusIabcMatrix = null;
	private Complex3x3 fromBusVabc2ToBusVabcMatrix = null;
	private Complex3x3 toBusIabc2ToBusVabcMatrix = null;
	private Complex3x3 phaseXfrFromBusVabc2ToBusVabcMatrix = null;
	private Complex3x3 phaseXfrToBusVabc2FromBusVabcMatrix = null;
	private Complex3x3 phaseXfrIabc2VabcMatrix = null;

	private static final double z0_to_z1_ratio = 2.5;

	private PhaseCode  ph = PhaseCode.ABC;

	private double baseKVA = 100000; //100 MVA
	private double[] fromTurnRatioABC = null;
	private double[] toTurnRatioABC = null;

	@Override
	public void setZabc(Complex3x3 Zabc) {
		this.Zabc = Zabc;
		this.Yabc = null;
		invalidateMatrixCache();
		if(this.isXfr() && Zabc != null && Zabc.aa != null && Zabc.aa.abs() > 0.0) {
			this.setZ(Zabc.aa);
		}
	}

	@Override
	public void setZabc(Complex Z1, Complex Z2, Complex Z0) {
		this.Zabc= new Complex3x3(Z1,Z2,Z0).ToAbc();

	}

	@Override
	public Complex3x3 getZabc() {
		// if Zabc is not set, initialize it from the three-sequence impedances
		if(Zabc ==null){
			if(this.isLine()){
				if(this.getZ0()!=null && this.getZ0().abs()>0) {
					setZabc(getAdjustedZ(),getAdjustedZ(),getZ0());
				} else {
					setZabc(getAdjustedZ(),getAdjustedZ(),getAdjustedZ().multiply(z0_to_z1_ratio));
				}
			}
			else{
				if(this.getAdjustedZ()!=null && this.getAdjustedZ().abs()>0){
					Complex3x3 Zabc = new Complex3x3();
					Zabc.aa = this.getAdjustedZ();
					Zabc.bb = this.getAdjustedZ();
					Zabc.cc = this.getAdjustedZ();
					setZabc(Zabc);
				}
			}

		}

		return this.Zabc;
	}

	@Override
	public Complex3x3 getBranchYabc() {
		double zeroTolerance  = 1.0E-8;
		int dim = 3;
		boolean hasPhaseA = true;
	    boolean hasPhaseB = true;
		boolean hasPhaseC = true;
		if(this.Yabc ==null){
			Complex3x3 zabc = getZabc();

			if(zabc.aa.abs() > 0.0 && zabc.bb.abs() > 0.0 && zabc.cc.abs() > 0.0){
			    Yabc= getZabc().inv();
			}
			else{
				if(zabc.aa.abs() <zeroTolerance) {
					hasPhaseA = false;
					dim = dim-1;
				}
				if(zabc.bb.abs() <zeroTolerance) {
					hasPhaseB = false;
					dim = dim-1;
				}
				if(zabc.cc.abs() <zeroTolerance) {
					hasPhaseC = false;
					dim = dim-1;
				}

				if(dim == 0){
					throw new Error(" The branch Yii diagonal elements are zero! # "+this.getId());
				}
				else if(dim ==1){
					if( hasPhaseA) {
						Complex yphase = new Complex(1,0).divide(zabc.aa);
						this.Yabc = new Complex3x3(yphase, new Complex(0,0),new Complex(0,0));
					}
					else if( hasPhaseB) {
						Complex yphase = new Complex(1,0).divide(zabc.bb);
						this.Yabc = new Complex3x3( new Complex(0,0),yphase,new Complex(0,0));
					}
					else{
						Complex yphase = new Complex(1,0).divide(zabc.cc);
						this.Yabc = new Complex3x3( new Complex(0,0),new Complex(0,0),yphase);
					}

				}
				else{ // dim =2

					/*
					 * for a 2x2 Matrix the Inverse is: swap the positions of a and d,
					 * put negatives in front of b and c, and divide everything by the determinant (ad-bc).
					 */
					if(!hasPhaseA) {
						// phase B and C
						Complex[][] zbc = new Complex[2][2];
						zbc[0][0] = zabc.bb;
						zbc[0][1] = zabc.bc;
						zbc[1][0] = zabc.cb;
						zbc[1][1] = zabc.cc;

						Complex[][] ybc = inv2x2Matrix(zbc);

						this.Yabc = new Complex3x3();

						this.Yabc.bb = ybc[0][0];
						this.Yabc.bc = ybc[0][1];
						this.Yabc.cb = ybc[1][0];
						this.Yabc.cc = ybc[1][1];

					}
					else if(!hasPhaseB){

						// phase A and C
						Complex[][] zac = new Complex[2][2];
						zac[0][0] = zabc.aa;
						zac[0][1] = zabc.ac;
						zac[1][0] = zabc.ca;
						zac[1][1] = zabc.cc;

						Complex[][] yac = inv2x2Matrix(zac);

						this.Yabc = new Complex3x3();

						this.Yabc.aa = yac[0][0];
						this.Yabc.ac = yac[0][1];
						this.Yabc.ca = yac[1][0];
						this.Yabc.cc = yac[1][1];

					}
					else{
						// phases A and B
						Complex[][] zab = new Complex[2][2];
						zab[0][0] = zabc.aa;
						zab[0][1] = zabc.ab;
						zab[1][0] = zabc.ba;
						zab[1][1] = zabc.bb;

						Complex[][] yab = inv2x2Matrix(zab);

						this.Yabc = new Complex3x3();

						this.Yabc.aa = yab[0][0];
						this.Yabc.ab = yab[0][1];
						this.Yabc.ba = yab[1][0];
						this.Yabc.bb = yab[1][1];

					}

				}

			}
		}
		return this.Yabc;
	}

	private Complex[][]  inv2x2Matrix(Complex[][] m2x2){
		Complex[][] inv = new Complex[2][2];
		// [ a  b]
		// [ c  d]
		// det = ad-bc
		// inv = 1/det*[d -b; -c a]
		Complex det =  m2x2[0][0].multiply(m2x2[1][1]).subtract(m2x2[0][1].multiply(m2x2[1][0]));
		if(det.abs()>0.0){
			inv[0][0] = m2x2[1][1].divide(det);
			inv[1][1] = m2x2[0][0].divide(det);
			inv[0][1] = m2x2[0][1].divide(det).multiply(-1.0);
			inv[1][0] = m2x2[1][0].divide(det).multiply(-1.0);
		} else {
			inv = null;
		}
		return inv;
	}

	@Override
	public Complex3x3 getFromShuntYabc() {
		// if fromShuntY is not provided, get it from the sequence network
		if(this.fromShuntYabc ==null){

			//Ys = (2*Y1+Y0)/3
			Complex Ys = (this.getHShuntY().multiply(2).add(new Complex(0,this.getHB0()))).divide(3);

			//Ym = (Y1-Y0)/3

			Complex Ym = (this.getHShuntY().subtract(new Complex(0,this.getHB0()))).divide(3);

			this.fromShuntYabc = new Complex3x3(Ys, Ym.multiply(-1));

		}

		return this.fromShuntYabc;
	}

	@Override
	public Complex3x3 getToShuntYabc() {

		// if toShuntY is not provided, get it from the sequence network
		if(this.toShuntYabc ==null){

			//Ys = (2*Y1+Y0)/3
			Complex Ys = (this.getHShuntY().multiply(2).add(new Complex(0,this.getHB0()))).divide(3);

			//Ym = (Y1-Y0)/3

			Complex Ym = (this.getHShuntY().subtract(new Complex(0,this.getHB0()))).divide(3);

			this.toShuntYabc = new Complex3x3(Ys, Ym.multiply(-1));

		}

		return this.toShuntYabc;
	}

	@Override
	public void setFromShuntYabc(Complex3x3 fYabc) {
		this.fromShuntYabc = fYabc;

	}

	@Override
	public void setToShuntYabc(Complex3x3 tYabc) {
		this.toShuntYabc = tYabc;

	}

	@Override
	public Complex3x3 getYffabc() {
		Complex3x3 yff = null;
		if(hasPhaseTurnRatio()){
			yff= phaseTransformerYffabc();
		}
		else if(!isXfr()){
			yff= this.getBranchYabc();


			if(this.getFromShuntYabc()!=null) {
				yff = yff.add(this.getFromShuntYabc());
			}
		}else{
			if(this.ph != PhaseCode.ABC) {
				return singlePhaseTransformerY(true, true);
			}
			Static3PXformer ph3Xformer = this.to3PXformer();
			yff = phaseMaskedTransformerY(ph3Xformer.getYffabc());
		}

	    return yff;
	}

	@Override
	public Complex3x3 getYttabc() {
		Complex3x3 ytt = null;
		if(hasPhaseTurnRatio()){
			ytt= phaseTransformerYttabc();
		}
		else if(!isXfr()){
			ytt = this.getBranchYabc();
			if(this.getToShuntYabc()!=null) {
				ytt = ytt.add(this.getToShuntYabc());
			}
		}
		else{
			if(this.ph != PhaseCode.ABC) {
				return singlePhaseTransformerY(false, false);
			}
			Static3PXformer ph3Xformer = this.to3PXformer();
			ytt = phaseMaskedTransformerY(ph3Xformer.getYttabc());
		}

		return ytt;
	}

	@Override
	public Complex3x3 getYftabc() {
		Complex3x3 yft = null;
		if(hasPhaseTurnRatio()){
			yft= phaseTransformerYftabc();
		}
		else if(!isXfr()) {
			yft = this.getBranchYabc().multiply(-1);
		} else{
			if(this.ph != PhaseCode.ABC) {
				return singlePhaseTransformerY(true, false).multiply(-1.0);
			}
			Static3PXformer ph3Xformer = this.to3PXformer();
			yft = phaseMaskedTransformerY(ph3Xformer.getYftabc());
		}

		return yft;
	}

	@Override
	public Complex3x3 getYtfabc() {
		Complex3x3 ytf = null;
		if(hasPhaseTurnRatio()){
			ytf= phaseTransformerYtfabc();
		}
		else if(!isXfr()) {
			ytf = this.getBranchYabc().multiply(-1);
		} else{
			if(this.ph != PhaseCode.ABC) {
				return singlePhaseTransformerY(false, true).multiply(-1.0);
			}
			Static3PXformer ph3Xformer = this.to3PXformer();
			ytf = phaseMaskedTransformerY(ph3Xformer.getYtfabc());
		}

		return ytf;
	}

	@Override
	public Complex3x1 getCurrentAbcAtFromSide() {

		return this.currInjAtFromBus;
	}

	@Override
	public Complex3x1 getCurrentAbcAtToSide() {

		return this.currInjAtToBus;
	}

	@Override
	public Static3PXformer to3PXformer() {

		return threePhaseXfrAptr.apply(this);
	}

	@Override
	public Complex3x3 getToBusVabc2FromBusVabcMatrix() {
		if(hasPhaseTurnRatio()) {
			if(this.phaseXfrToBusVabc2FromBusVabcMatrix == null) {
				this.phaseXfrToBusVabc2FromBusVabcMatrix = ratioMatrix(this.fromTurnRatioABC, this.toTurnRatioABC);
			}
			return this.phaseXfrToBusVabc2FromBusVabcMatrix;
		}

		if(this.toBusVabc2FromBusVabcMatrix == null){
		    Complex3x3 U = Complex3x3.createUnitMatrix();
		    this.toBusVabc2FromBusVabcMatrix = U.add(this.getZabc().multiply(this.getToShuntYabc()));
		}
		return this.toBusVabc2FromBusVabcMatrix;
	}

	@Override
	public Complex3x3 getToBusIabc2FromBusVabcMatrix() {
		if(hasPhaseTurnRatio()) {
			return phaseTransformerIabc2VabcMatrix();
		}

		return this.toBusIabc2FromBusVabcMatrix = this.getZabc();
	}

	@Override
	public Complex3x3 getToBusVabc2FromBusIabcMatrix() {
		 if(hasPhaseTurnRatio()) {
			 return new Complex3x3();
		 }
		 if(this.toBusVabc2FromBusIabcMatrix ==null){
			 if(this.getFromShuntY()!=null && this.getToShuntY()!=null){
				 Complex3x3 shuntYabc = this.getFromShuntYabc().add(this.getToShuntYabc());
				 this.toBusVabc2FromBusIabcMatrix = shuntYabc.multiply(this.Zabc).multiply(shuntYabc);
				 this.toBusVabc2FromBusIabcMatrix = this.toBusVabc2FromBusIabcMatrix.multiply(1/4).add(shuntYabc);
			 } else {
				this.toBusVabc2FromBusIabcMatrix = new Complex3x3();
			}
		 }
		return this.toBusVabc2FromBusIabcMatrix;
	}

	@Override
	public Complex3x3 getToBusIabc2FromBusIabcMatrix() {
		       if(this.toBusIabc2FromBusIabcMatrix == null) {
				this.toBusIabc2FromBusIabcMatrix = getToBusVabc2FromBusVabcMatrix();
			}
		return this.toBusIabc2FromBusIabcMatrix;
	}

	@Override
	public Complex3x3 getFromBusVabc2ToBusVabcMatrix() {
		if(hasPhaseTurnRatio()) {
			if(this.phaseXfrFromBusVabc2ToBusVabcMatrix == null) {
				this.phaseXfrFromBusVabc2ToBusVabcMatrix = ratioMatrix(this.toTurnRatioABC, this.fromTurnRatioABC);
			}
			return this.phaseXfrFromBusVabc2ToBusVabcMatrix;
		}
		if(this.fromBusVabc2ToBusVabcMatrix==null){
			this.fromBusVabc2ToBusVabcMatrix = this.getToBusVabc2FromBusVabcMatrix().inv();
		}
		return this.fromBusVabc2ToBusVabcMatrix;
	}

	@Override
	public Complex3x3 getToBusIabc2ToBusVabcMatrix() {
		if(hasPhaseTurnRatio()) {
			return phaseTransformerIabc2VabcMatrix();
		}
		if(this.toBusIabc2ToBusVabcMatrix==null) {
			this.toBusIabc2ToBusVabcMatrix = this.getToBusVabc2FromBusVabcMatrix().inv()
					.multiply(getToBusIabc2FromBusVabcMatrix());
		}
		return this.toBusIabc2ToBusVabcMatrix;
	}

	@Override
	public void setCurrentAbcAtFromSide(Complex3x1 IabcFromBus) {
		this.currInjAtFromBus = IabcFromBus;

	}

	@Override
	public void setCurrentAbcAtToSide(Complex3x1 IabcToBus) {
		this.currInjAtToBus = IabcToBus;

	}

	@Override
	public void setPhaseCode(PhaseCode phCode) {
		this.ph=phCode;

	}

	@Override
	public PhaseCode getPhaseCode() {

		return this.ph;
	}

	@Override
	public void setXfrRatedKVA(double kva1) {
		this.baseKVA = kva1;

	}

	@Override
	public double getXfrRatedKVA() {
		return this.baseKVA;

	}

	@Override
	public void setFromTurnRatioABC(double phaseA, double phaseB, double phaseC) {
		this.fromTurnRatioABC = new double[] {phaseA, phaseB, phaseC};
		invalidateMatrixCache();
	}

	@Override
	public void setToTurnRatioABC(double phaseA, double phaseB, double phaseC) {
		this.toTurnRatioABC = new double[] {phaseA, phaseB, phaseC};
		invalidateMatrixCache();
	}

	@Override
	public double[] getFromTurnRatioABC() {
		return this.fromTurnRatioABC == null ? null : this.fromTurnRatioABC.clone();
	}

	@Override
	public double[] getToTurnRatioABC() {
		return this.toTurnRatioABC == null ? null : this.toTurnRatioABC.clone();
	}

	@Override
	public boolean hasPhaseTurnRatio() {
		return this.fromTurnRatioABC != null && this.toTurnRatioABC != null;
	}

	private void invalidateMatrixCache() {
		this.toBusVabc2FromBusVabcMatrix = null;
		this.toBusIabc2FromBusVabcMatrix = null;
		this.toBusVabc2FromBusIabcMatrix = null;
		this.toBusIabc2FromBusIabcMatrix = null;
		this.fromBusVabc2ToBusVabcMatrix = null;
		this.toBusIabc2ToBusVabcMatrix = null;
		this.phaseXfrFromBusVabc2ToBusVabcMatrix = null;
		this.phaseXfrToBusVabc2FromBusVabcMatrix = null;
		this.phaseXfrIabc2VabcMatrix = null;
		this.Yabc = null;
	}

	private Complex3x3 phaseTransformerYffabc() {
		return phaseTransformerY(true, true);
	}

	private Complex3x3 phaseMaskedTransformerY(Complex3x3 yabc) {
		if(this.ph == PhaseCode.ABC) {
			return yabc;
		}
		Complex3x3 masked = new Complex3x3();
		if(this.ph == PhaseCode.A) {
			masked.aa = yabc.aa;
		}
		else if(this.ph == PhaseCode.B) {
			masked.bb = yabc.bb;
		}
		else if(this.ph == PhaseCode.C) {
			masked.cc = yabc.cc;
		}
		return masked;
	}

	private Complex3x3 singlePhaseTransformerY(boolean firstFrom, boolean secondFrom) {
		Complex y = new Complex(1.0, 0.0).divide(this.getAdjustedZ());
		double firstRatio = firstFrom ? this.getFromTurnRatio() : this.getToTurnRatio();
		double secondRatio = secondFrom ? this.getFromTurnRatio() : this.getToTurnRatio();
		Complex phaseY = y.divide(firstRatio * secondRatio);
		Complex3x3 stamped = new Complex3x3();
		if(this.ph == PhaseCode.A) {
			stamped.aa = phaseY;
		}
		else if(this.ph == PhaseCode.B) {
			stamped.bb = phaseY;
		}
		else if(this.ph == PhaseCode.C) {
			stamped.cc = phaseY;
		}
		return stamped;
	}

	private Complex3x3 phaseTransformerYttabc() {
		return phaseTransformerY(false, false);
	}

	private Complex3x3 phaseTransformerYftabc() {
		return phaseTransformerY(true, false).multiply(-1.0);
	}

	private Complex3x3 phaseTransformerYtfabc() {
		return phaseTransformerY(false, true).multiply(-1.0);
	}

	private Complex3x3 phaseTransformerY(boolean fromSide, boolean fromSideAgain) {
		Complex3x3 yabc = getBranchYabc();
		Complex3x3 y = new Complex3x3();
		y.aa = scaledPhaseY(yabc.aa, 0, fromSide, fromSideAgain);
		y.bb = scaledPhaseY(yabc.bb, 1, fromSide, fromSideAgain);
		y.cc = scaledPhaseY(yabc.cc, 2, fromSide, fromSideAgain);
		return y;
	}

	private Complex scaledPhaseY(Complex phaseY, int phase, boolean firstFrom, boolean secondFrom) {
		double firstRatio = firstFrom ? this.fromTurnRatioABC[phase] : this.toTurnRatioABC[phase];
		double secondRatio = secondFrom ? this.fromTurnRatioABC[phase] : this.toTurnRatioABC[phase];
		return phaseY.divide(firstRatio * secondRatio);
	}

	private Complex3x3 phaseTransformerIabc2VabcMatrix() {
		if(this.phaseXfrIabc2VabcMatrix == null) {
			Complex3x3 zabc = getZabc();
			this.phaseXfrIabc2VabcMatrix = new Complex3x3();
			this.phaseXfrIabc2VabcMatrix.aa = zabc.aa.divide(this.toTurnRatioABC[0]);
			this.phaseXfrIabc2VabcMatrix.bb = zabc.bb.divide(this.toTurnRatioABC[1]);
			this.phaseXfrIabc2VabcMatrix.cc = zabc.cc.divide(this.toTurnRatioABC[2]);
		}
		return this.phaseXfrIabc2VabcMatrix;
	}

	private Complex3x3 ratioMatrix(double[] numerator, double[] denominator) {
		Complex3x3 matrix = new Complex3x3();
		matrix.aa = new Complex(numerator[0] / denominator[0], 0.0);
		matrix.bb = new Complex(numerator[1] / denominator[1], 0.0);
		matrix.cc = new Complex(numerator[2] / denominator[2], 0.0);
		return matrix;
	}


	@Override
	public Complex3x1 calc3PhaseCurrentFrom2To() {
		Complex3x1 vabc_f = ((DStab3PBus)this.getFromBus()).get3PhaseVotlages();
		Complex3x1 vabc_t = ((DStab3PBus)this.getToBus()).get3PhaseVotlages();

		Complex3x1 Iabc = this.getYffabc().multiply(vabc_f).add(this.getYftabc().multiply(vabc_t));

		return  Iabc;
	}

	@Override
	public Complex3x1 calc3PhaseCurrentTo2From() {
		Complex3x1 vabc_f = ((DStab3PBus)this.getFromBus()).get3PhaseVotlages();
		Complex3x1 vabc_t = ((DStab3PBus)this.getToBus()).get3PhaseVotlages();

		Complex3x1 Iabc = this.getYttabc().multiply(vabc_t).add(this.getYtfabc().multiply(vabc_f));
		return  Iabc;
	}

}
