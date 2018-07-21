/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad.impl;

import org.interpss.dstab.dynLoad.DynLoadVFreqDependentModel;

import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Dyn Load VFreq Dependent Model</b></em>'.
 */
public class DynLoadVFreqDependentModelImpl extends DynLoadModelImpl implements DynLoadVFreqDependentModel {
	protected double ra = 0.0;

	/**
	 * The default value of the '{@link #getPf() <em>Pf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPf()
	 * @generated
	 * @ordered
	 */
	protected static final double PF_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getPf() <em>Pf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPf()
	 * @generated
	 * @ordered
	 */
	protected double pf = PF_EDEFAULT;

	/**
	 * The default value of the '{@link #getP1Exp() <em>P1 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP1Exp()
	 * @generated
	 * @ordered
	 */
	protected static final double P1_EXP_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getP1Exp() <em>P1 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP1Exp()
	 * @generated
	 * @ordered
	 */
	protected double p1Exp = P1_EXP_EDEFAULT;

	/**
	 * The default value of the '{@link #getP1Coeff() <em>P1 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP1Coeff()
	 * @generated
	 * @ordered
	 */
	protected static final double P1_COEFF_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getP1Coeff() <em>P1 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP1Coeff()
	 * @generated
	 * @ordered
	 */
	protected double p1Coeff = P1_COEFF_EDEFAULT;

	/**
	 * The default value of the '{@link #getP2Exp() <em>P2 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP2Exp()
	 * @generated
	 * @ordered
	 */
	protected static final double P2_EXP_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getP2Exp() <em>P2 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP2Exp()
	 * @generated
	 * @ordered
	 */
	protected double p2Exp = P2_EXP_EDEFAULT;

	/**
	 * The default value of the '{@link #getP2Coeff() <em>P2 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP2Coeff()
	 * @generated
	 * @ordered
	 */
	protected static final double P2_COEFF_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getP2Coeff() <em>P2 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP2Coeff()
	 * @generated
	 * @ordered
	 */
	protected double p2Coeff = P2_COEFF_EDEFAULT;

	/**
	 * The default value of the '{@link #getPFreq() <em>PFreq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPFreq()
	 * @generated
	 * @ordered
	 */
	protected static final double PFREQ_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getPFreq() <em>PFreq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPFreq()
	 * @generated
	 * @ordered
	 */
	protected double pFreq = PFREQ_EDEFAULT;

	/**
	 * The default value of the '{@link #getQ1Exp() <em>Q1 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ1Exp()
	 * @generated
	 * @ordered
	 */
	protected static final double Q1_EXP_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQ1Exp() <em>Q1 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ1Exp()
	 * @generated
	 * @ordered
	 */
	protected double q1Exp = Q1_EXP_EDEFAULT;

	/**
	 * The default value of the '{@link #getQ1Coeff() <em>Q1 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ1Coeff()
	 * @generated
	 * @ordered
	 */
	protected static final double Q1_COEFF_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQ1Coeff() <em>Q1 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ1Coeff()
	 * @generated
	 * @ordered
	 */
	protected double q1Coeff = Q1_COEFF_EDEFAULT;

	/**
	 * The default value of the '{@link #getQ2Exp() <em>Q2 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ2Exp()
	 * @generated
	 * @ordered
	 */
	protected static final double Q2_EXP_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQ2Exp() <em>Q2 Exp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ2Exp()
	 * @generated
	 * @ordered
	 */
	protected double q2Exp = Q2_EXP_EDEFAULT;

	/**
	 * The default value of the '{@link #getQ2Coeff() <em>Q2 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ2Coeff()
	 * @generated
	 * @ordered
	 */
	protected static final double Q2_COEFF_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQ2Coeff() <em>Q2 Coeff</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ2Coeff()
	 * @generated
	 * @ordered
	 */
	protected double q2Coeff = Q2_COEFF_EDEFAULT;

	protected static final double QFREQ_EDEFAULT = 0.0;

	protected double qFreq = QFREQ_EDEFAULT;

	public DynLoadVFreqDependentModelImpl() {
		super();
	}

	public double getRa() {
		return ra;
	}

	public void setRa(double newRa) {
		ra = newRa;
	}

	public double getPf() {
		return pf;
	}

	public void setPf(double newPf) {
		pf = newPf;
	}

	public double getP1Exp() {
		return p1Exp;
	}

	public void setP1Exp(double newP1Exp) {
		p1Exp = newP1Exp;
	}

	public double getP1Coeff() {
		return p1Coeff;
	}

	public void setP1Coeff(double newP1Coeff) {
		p1Coeff = newP1Coeff;
	}

	public double getP2Exp() {
		return p2Exp;
	}

	public void setP2Exp(double newP2Exp) {
		p2Exp = newP2Exp;
	}

	public double getP2Coeff() {
		return p2Coeff;
	}

	public void setP2Coeff(double newP2Coeff) {
		p2Coeff = newP2Coeff;
	}

	public double getPFreq() {
		return pFreq;
	}

	public void setPFreq(double newPFreq) {
		pFreq = newPFreq;
	}

	public double getQ1Exp() {
		return q1Exp;
	}

	public void setQ1Exp(double newQ1Exp) {
		q1Exp = newQ1Exp;
	}

	public double getQ1Coeff() {
		return q1Coeff;
	}

	public void setQ1Coeff(double newQ1Coeff) {
		q1Coeff = newQ1Coeff;
	}

	public double getQ2Exp() {
		return q2Exp;
	}

	public void setQ2Exp(double newQ2Exp) {
		q2Exp = newQ2Exp;
	}

	public double getQ2Coeff() {
		return q2Coeff;
	}

	public void setQ2Coeff(double newQ2Coeff) {
		q2Coeff = newQ2Coeff;
	}

	public double getQFreq() {
		return qFreq;
	}

	public void setQFreq(double newQFreq) {
		qFreq = newQFreq;
	}

	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (ra: ");
		result.append(ra);
		result.append(", pf: ");
		result.append(pf);
		result.append(", p1Exp: ");
		result.append(p1Exp);
		result.append(", p1Coeff: ");
		result.append(p1Coeff);
		result.append(", p2Exp: ");
		result.append(p2Exp);
		result.append(", p2Coeff: ");
		result.append(p2Coeff);
		result.append(", pFreq: ");
		result.append(pFreq);
		result.append(", q1Exp: ");
		result.append(q1Exp);
		result.append(", q1Coeff: ");
		result.append(q1Coeff);
		result.append(", q2Exp: ");
		result.append(q2Exp);
		result.append(", q2Coeff: ");
		result.append(q2Coeff);
		result.append(", qFreq: ");
		result.append(qFreq);
		result.append(')');
		return result.toString();
	}

	@Override
	public boolean changeLoad(double factor) {
		return false;
	}

} //DynLoadVFreqDependentModelImpl
