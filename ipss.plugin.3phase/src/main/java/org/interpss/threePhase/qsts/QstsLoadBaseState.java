package org.interpss.threePhase.qsts;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.LoadConnectionType;

public class QstsLoadBaseState {
	private final Object load;
	private final AclfLoad3Phase phaseLoad;
	private AclfLoadCode code;
	private LoadConnectionType connectionType;
	private double nominalKV;
	private Complex loadCP;
	private Complex loadCI;
	private Complex loadCZ;
	private Complex3x1 threePhaseLoad;
	private double appliedPMultiplier = Double.NaN;
	private double appliedQMultiplier = Double.NaN;

	public QstsLoadBaseState(Object load) {
		if(load == null) {
			throw new IllegalArgumentException("QSTS load base state requires a load");
		}
		if(!(load instanceof AclfLoad3Phase)) {
			throw new IllegalArgumentException("QSTS load base state requires AclfLoad3Phase");
		}
		this.load = load;
		this.phaseLoad = (AclfLoad3Phase) load;
		refreshFromLoad();
	}

	public Object getLoad() {
		return load;
	}

	public String getLoadId() {
		return phaseLoad.getId();
	}

	public void refreshFromLoad() {
		this.code = phaseLoad.getCode();
		this.connectionType = phaseLoad.getLoadConnectionType();
		this.nominalKV = phaseLoad.getNominalKV();
		this.threePhaseLoad = copy(phaseLoad.getInit3PhaseLoad());
		this.loadCP = load instanceof AclfLoad ? copy(((AclfLoad) load).getLoadCP()) : copy(phaseLoad.getLoadCP());
		this.loadCI = load instanceof AclfLoad ? copy(((AclfLoad) load).getLoadCI()) : copy(phaseLoad.getLoadCI());
		this.loadCZ = load instanceof AclfLoad ? copy(((AclfLoad) load).getLoadCZ()) : copy(phaseLoad.getLoadCZ());
		if(this.loadCP == null && this.threePhaseLoad != null) {
			this.loadCP = total(this.threePhaseLoad);
		}
		resetAppliedMultiplier();
	}

	public void restore() {
		phaseLoad.setCode(code);
		if(load instanceof AclfLoad) {
			((AclfLoad) load).setCode(code);
		}
		phaseLoad.setLoadConnectionType(connectionType);
		phaseLoad.setNominalKV(nominalKV);
		if(hasNonConstantPowerComponents() && load instanceof AclfLoad) {
			((AclfLoad) load).setLoadCP(copy(loadCP));
			((AclfLoad) load).setLoadCI(copy(loadCI));
			((AclfLoad) load).setLoadCZ(copy(loadCZ));
		}
		if(hasNonConstantPowerComponents()) {
			phaseLoad.setLoadCP(copy(loadCP));
			phaseLoad.setLoadCI(copy(loadCI));
			phaseLoad.setLoadCZ(copy(loadCZ));
		}
		else if(threePhaseLoad != null) {
			phaseLoad.set3PhaseLoad(copy(threePhaseLoad));
		}
		resetAppliedMultiplier();
	}

	public boolean applyMultiplier(double pMultiplier, double qMultiplier) {
		if(sameMultiplier(pMultiplier, qMultiplier)) {
			return false;
		}
		this.appliedPMultiplier = pMultiplier;
		this.appliedQMultiplier = qMultiplier;
		if(!hasNonConstantPowerComponents() && threePhaseLoad != null) {
			phaseLoad.set3PhaseLoad(scale(threePhaseLoad, pMultiplier, qMultiplier));
			return true;
		}
		if(load instanceof AclfLoad) {
			((AclfLoad) load).setLoadCP(scale(loadCP, pMultiplier, qMultiplier));
			((AclfLoad) load).setLoadCI(scale(loadCI, pMultiplier, qMultiplier));
			((AclfLoad) load).setLoadCZ(scale(loadCZ, pMultiplier, qMultiplier));
		}
		else {
			phaseLoad.setLoadCP(scale(loadCP, pMultiplier, qMultiplier));
			phaseLoad.setLoadCI(scale(loadCI, pMultiplier, qMultiplier));
			phaseLoad.setLoadCZ(scale(loadCZ, pMultiplier, qMultiplier));
		}
		return true;
	}

	public void applyFixedPointNortonMultiplier(double pMultiplier, double qMultiplier) {
		if(threePhaseLoad != null) {
			phaseLoad.setFixedPointNortonLoad(scale(threePhaseLoad, pMultiplier, qMultiplier));
		}
	}

	public Complex getLoadCP() {
		return copy(loadCP);
	}

	public Complex getLoadCI() {
		return copy(loadCI);
	}

	public Complex getLoadCZ() {
		return copy(loadCZ);
	}

	public Complex3x1 getThreePhaseLoad() {
		return copy(threePhaseLoad);
	}

	private boolean hasNonConstantPowerComponents() {
		return nonZero(loadCI) || nonZero(loadCZ);
	}

	private boolean sameMultiplier(double pMultiplier, double qMultiplier) {
		return Double.isFinite(this.appliedPMultiplier)
				&& Double.isFinite(this.appliedQMultiplier)
				&& Math.abs(this.appliedPMultiplier - pMultiplier) <= 1.0e-12
				&& Math.abs(this.appliedQMultiplier - qMultiplier) <= 1.0e-12;
	}

	private void resetAppliedMultiplier() {
		this.appliedPMultiplier = Double.NaN;
		this.appliedQMultiplier = Double.NaN;
	}

	private static boolean nonZero(Complex value) {
		return value != null && value.abs() > 1.0e-12;
	}

	private static Complex scale(Complex value, double pMultiplier, double qMultiplier) {
		if(value == null) {
			return null;
		}
		return new Complex(value.getReal() * pMultiplier, value.getImaginary() * qMultiplier);
	}

	private static Complex3x1 scale(Complex3x1 value, double pMultiplier, double qMultiplier) {
		return new Complex3x1(scale(value.a_0, pMultiplier, qMultiplier),
				scale(value.b_1, pMultiplier, qMultiplier), scale(value.c_2, pMultiplier, qMultiplier));
	}

	private static Complex copy(Complex value) {
		return value == null ? null : new Complex(value.getReal(), value.getImaginary());
	}

	private static Complex3x1 copy(Complex3x1 value) {
		return value == null ? null : new Complex3x1(copy(value.a_0), copy(value.b_1), copy(value.c_2));
	}

	private static Complex total(Complex3x1 value) {
		if(value == null) {
			return Complex.ZERO;
		}
		return add(add(value.a_0, value.b_1), value.c_2);
	}

	private static Complex add(Complex left, Complex right) {
		Complex a = left == null ? Complex.ZERO : left;
		Complex b = right == null ? Complex.ZERO : right;
		return a.add(b);
	}

}
