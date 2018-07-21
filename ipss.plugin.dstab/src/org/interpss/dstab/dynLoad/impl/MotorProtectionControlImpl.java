/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad.impl;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.EObjectImpl;

import com.interpss.core.common.curve.PieceWiseCurve;
import com.interpss.dstab.dynLoad.DStabDynamicLoadPackage;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.MotorProtectionControl;

/**
 * An implementation of the model object '<em><b>Motor Protection Control</b></em>'.
 */
public class MotorProtectionControlImpl extends EObjectImpl implements MotorProtectionControl {
	/**
	 * The default value of the '{@link #getMotorFraction() <em>Motor Fraction</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorFraction()
	 * @generated
	 * @ordered
	 */
	protected static final double MOTOR_FRACTION_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getMotorFraction() <em>Motor Fraction</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorFraction()
	 * @generated
	 * @ordered
	 */
	protected double motorFraction = MOTOR_FRACTION_EDEFAULT;

	/**
	 * The default value of the '{@link #isTripStatus() <em>Trip Status</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isTripStatus()
	 * @generated
	 * @ordered
	 */
	protected static final boolean TRIP_STATUS_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isTripStatus() <em>Trip Status</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isTripStatus()
	 * @generated
	 * @ordered
	 */
	protected boolean tripStatus = TRIP_STATUS_EDEFAULT;

	/**
	 * The default value of the '{@link #isReconnectStatus() <em>Reconnect Status</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isReconnectStatus()
	 * @generated
	 * @ordered
	 */
	protected static final boolean RECONNECT_STATUS_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isReconnectStatus() <em>Reconnect Status</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isReconnectStatus()
	 * @generated
	 * @ordered
	 */
	protected boolean reconnectStatus = RECONNECT_STATUS_EDEFAULT;

	/**
	 * The default value of the '{@link #getTripTimer() <em>Trip Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTripTimer()
	 * @generated
	 * @ordered
	 */
	protected static final double TRIP_TIMER_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getTripTimer() <em>Trip Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTripTimer()
	 * @generated
	 * @ordered
	 */
	protected double tripTimer = TRIP_TIMER_EDEFAULT;

	/**
	 * The cached value of the '{@link #getMotor() <em>Motor</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotor()
	 * @generated
	 * @ordered
	 */
	protected InductionMotor motor;

	/**
	 * The cached value of the '{@link #getTripVoltTimeCurve() <em>Trip Volt Time Curve</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTripVoltTimeCurve()
	 * @generated
	 * @ordered
	 */
	protected PieceWiseCurve tripVoltTimeCurve;

	/**
	 * The cached value of the '{@link #getReconnectVoltTimeCurve() <em>Reconnect Volt Time Curve</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getReconnectVoltTimeCurve()
	 * @generated
	 * @ordered
	 */
	protected PieceWiseCurve reconnectVoltTimeCurve;

	public MotorProtectionControlImpl() {
		super();
	}

	public double getMotorFraction() {
		return motorFraction;
	}

	public void setMotorFraction(double newMotorFraction) {
		motorFraction = newMotorFraction;
	}

	public boolean isTripStatus() {
		return tripStatus;
	}

	public void setTripStatus(boolean newTripStatus) {
		tripStatus = newTripStatus;
	}

	public boolean isReconnectStatus() {
		return reconnectStatus;
	}

	public void setReconnectStatus(boolean newReconnectStatus) {
		reconnectStatus = newReconnectStatus;
	}

	public double getTripTimer() {
		return tripTimer;
	}

	public void setTripTimer(double newTripTimer) {
		tripTimer = newTripTimer;
	}

	public InductionMotor getMotor() {
		return motor;
	}

	public InductionMotor basicGetMotor() {
		return motor;
	}

	public void setMotor(InductionMotor newMotor) {
		motor = newMotor;
	}

	public PieceWiseCurve getTripVoltTimeCurve() {
		return tripVoltTimeCurve;
	}

	public PieceWiseCurve basicGetTripVoltTimeCurve() {
		return tripVoltTimeCurve;
	}

	public void setTripVoltTimeCurve(PieceWiseCurve newTripVoltTimeCurve) {
		tripVoltTimeCurve = newTripVoltTimeCurve;
	}

	public PieceWiseCurve getReconnectVoltTimeCurve() {
		return reconnectVoltTimeCurve;
	}

	public PieceWiseCurve basicGetReconnectVoltTimeCurve() {
		return reconnectVoltTimeCurve;
	}

	public void setReconnectVoltTimeCurve(PieceWiseCurve newReconnectVoltTimeCurve) {
		reconnectVoltTimeCurve = newReconnectVoltTimeCurve;
	}

	public void checkTripAndReconnectAction(double vt, double dt) {
		throw new UnsupportedOperationException();
	}

	public void checkTripAction(double vt, double dt) {
		throw new UnsupportedOperationException();
	}

	public void checkReconnectAction(double vt, double dt) {
		throw new UnsupportedOperationException();
	}

	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (motorFraction: ");
		result.append(motorFraction);
		result.append(", tripStatus: ");
		result.append(tripStatus);
		result.append(", reconnectStatus: ");
		result.append(reconnectStatus);
		result.append(", tripTimer: ");
		result.append(tripTimer);
		result.append(')');
		return result.toString();
	}

} //MotorProtectionControlImpl
