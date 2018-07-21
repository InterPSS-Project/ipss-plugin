/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad;

import org.eclipse.emf.ecore.EObject;

import com.interpss.core.common.curve.PieceWiseCurve;

/**
 * A representation of the model object '<em><b>Motor Protection Control</b></em>'.
 */
public interface MotorProtectionControl extends EObject {
	/**
	 * Returns the value of the '<em><b>Motor Fraction</b></em>' attribute.
	 */
	double getMotorFraction();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.MotorProtectionControl#getMotorFraction <em>Motor Fraction</em>}' attribute.
	 */
	void setMotorFraction(double value);

	/**
	 * Returns the value of the '<em><b>Trip Status</b></em>' attribute.
	 */
	boolean isTripStatus();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.MotorProtectionControl#isTripStatus <em>Trip Status</em>}' attribute.
	 */
	void setTripStatus(boolean value);

	/**
	 * Returns the value of the '<em><b>Reconnect Status</b></em>' attribute.
	 */
	boolean isReconnectStatus();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.MotorProtectionControl#isReconnectStatus <em>Reconnect Status</em>}' attribute.
	 */
	void setReconnectStatus(boolean value);

	/**
	 * Returns the value of the '<em><b>Trip Timer</b></em>' attribute.
	 */
	double getTripTimer();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.MotorProtectionControl#getTripTimer <em>Trip Timer</em>}' attribute.
	 */
	void setTripTimer(double value);

	/**
	 * Returns the value of the '<em><b>Motor</b></em>' reference.
	 */
	InductionMotor getMotor();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.MotorProtectionControl#getMotor <em>Motor</em>}' reference.
	 */
	void setMotor(InductionMotor value);

	/**
	 * Returns the value of the '<em><b>Trip Volt Time Curve</b></em>' reference.
	 */
	PieceWiseCurve getTripVoltTimeCurve();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.MotorProtectionControl#getTripVoltTimeCurve <em>Trip Volt Time Curve</em>}' reference.
	 */
	void setTripVoltTimeCurve(PieceWiseCurve value);

	/**
	 * Returns the value of the '<em><b>Reconnect Volt Time Curve</b></em>' reference.
	 */
	PieceWiseCurve getReconnectVoltTimeCurve();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.MotorProtectionControl#getReconnectVoltTimeCurve <em>Reconnect Volt Time Curve</em>}' reference.
	 */
	void setReconnectVoltTimeCurve(PieceWiseCurve value);

	/**
	 */
	void checkTripAndReconnectAction(double vt, double dt);

	/**
	 */
	void checkTripAction(double vt, double dt);

	/**
	 */
	void checkReconnectAction(double vt, double dt);

} // MotorProtectionControl
