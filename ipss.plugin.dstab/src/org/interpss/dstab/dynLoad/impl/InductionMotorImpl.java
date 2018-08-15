/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad.impl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.impl.ENotificationImpl;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.dynLoad.DStabDynamicLoadPackage;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.MotorProtectionControl;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;


/**
 * An implementation of the model object '<em><b>Induction Motor</b></em>'.
  */
public class InductionMotorImpl extends DynLoadModelImpl implements InductionMotor {
	public static final double SLIPMIN = 0.002;
	public static final double SLIPMAX = 0.05;	
	
	/**
	 * The default value of the '{@link #getRa() <em>Ra</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRa()
	 * @generated
	 * @ordered
	 */
	protected static final double RA_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getRa() <em>Ra</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRa()
	 * @generated
	 * @ordered
	 */
	protected double ra = RA_EDEFAULT;

	/**
	 * The default value of the '{@link #getXl() <em>Xl</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXl()
	 * @generated
	 * @ordered
	 */
	protected static final double XL_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getXl() <em>Xl</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXl()
	 * @generated
	 * @ordered
	 */
	protected double xl = XL_EDEFAULT;

	/**
	 * The default value of the '{@link #getXm() <em>Xm</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXm()
	 * @generated
	 * @ordered
	 */
	protected static final double XM_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getXm() <em>Xm</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXm()
	 * @generated
	 * @ordered
	 */
	protected double xm = XM_EDEFAULT;

	/**
	 * The default value of the '{@link #getRr1() <em>Rr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRr1()
	 * @generated
	 * @ordered
	 */
	protected static final double RR1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getRr1() <em>Rr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRr1()
	 * @generated
	 * @ordered
	 */
	protected double rr1 = RR1_EDEFAULT;

	/**
	 * The default value of the '{@link #getXr1() <em>Xr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXr1()
	 * @generated
	 * @ordered
	 */
	protected static final double XR1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getXr1() <em>Xr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXr1()
	 * @generated
	 * @ordered
	 */
	protected double xr1 = XR1_EDEFAULT;

	/**
	 * The default value of the '{@link #getRr2() <em>Rr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRr2()
	 * @generated
	 * @ordered
	 */
	protected static final double RR2_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getRr2() <em>Rr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRr2()
	 * @generated
	 * @ordered
	 */
	protected double rr2 = RR2_EDEFAULT;

	/**
	 * The default value of the '{@link #getXr2() <em>Xr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXr2()
	 * @generated
	 * @ordered
	 */
	protected static final double XR2_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getXr2() <em>Xr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXr2()
	 * @generated
	 * @ordered
	 */
	protected double xr2 = XR2_EDEFAULT;

	/**
	 * The default value of the '{@link #getH() <em>H</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getH()
	 * @generated
	 * @ordered
	 */
	protected static final double H_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getH() <em>H</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getH()
	 * @generated
	 * @ordered
	 */
	protected double h = H_EDEFAULT;

	/**
	 * The default value of the '{@link #getD() <em>D</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getD()
	 * @generated
	 * @ordered
	 */
	protected static final double D_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getD() <em>D</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getD()
	 * @generated
	 * @ordered
	 */
	protected double d = D_EDEFAULT;

	/**
	 * The default value of the '{@link #getXs() <em>Xs</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXs()
	 * @generated
	 * @ordered
	 */
	protected static final double XS_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getXs() <em>Xs</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXs()
	 * @generated
	 * @ordered
	 */
	protected double xs = XS_EDEFAULT;

	/**
	 * The default value of the '{@link #getXp() <em>Xp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXp()
	 * @generated
	 * @ordered
	 */
	protected static final double XP_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getXp() <em>Xp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXp()
	 * @generated
	 * @ordered
	 */
	protected double xp = XP_EDEFAULT;

	/**
	 * The default value of the '{@link #getXpp() <em>Xpp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXpp()
	 * @generated
	 * @ordered
	 */
	protected static final double XPP_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getXpp() <em>Xpp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXpp()
	 * @generated
	 * @ordered
	 */
	protected double xpp = XPP_EDEFAULT;

	/**
	 * The default value of the '{@link #getTp0() <em>Tp0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTp0()
	 * @generated
	 * @ordered
	 */
	protected static final double TP0_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getTp0() <em>Tp0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTp0()
	 * @generated
	 * @ordered
	 */
	protected double tp0 = TP0_EDEFAULT;

	/**
	 * The default value of the '{@link #getTpp0() <em>Tpp0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTpp0()
	 * @generated
	 * @ordered
	 */
	protected static final double TPP0_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getTpp0() <em>Tpp0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTpp0()
	 * @generated
	 * @ordered
	 */
	protected double tpp0 = TPP0_EDEFAULT;

	/**
	 * The default value of the '{@link #getRatedVoltage() <em>Rated Voltage</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRatedVoltage()
	 * @generated
	 * @ordered
	 */
	protected static final double RATED_VOLTAGE_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getRatedVoltage() <em>Rated Voltage</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRatedVoltage()
	 * @generated
	 * @ordered
	 */
	protected double ratedVoltage = RATED_VOLTAGE_EDEFAULT;

	/**
	 * The default value of the '{@link #getSlip() <em>Slip</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSlip()
	 * @generated
	 * @ordered
	 */
	protected static final double SLIP_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getSlip() <em>Slip</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSlip()
	 * @generated
	 * @ordered
	 */
	protected double slip = SLIP_EDEFAULT;

	/**
	 * The default value of the '{@link #getW() <em>W</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getW()
	 * @generated
	 * @ordered
	 */
	protected static final double W_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getW() <em>W</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getW()
	 * @generated
	 * @ordered
	 */
	protected double w = W_EDEFAULT;

	/**
	 * The default value of the '{@link #getMotorLoadP() <em>Motor Load P</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorLoadP()
	 * @generated
	 * @ordered
	 */
	protected static final double MOTOR_LOAD_P_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getMotorLoadP() <em>Motor Load P</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorLoadP()
	 * @generated
	 * @ordered
	 */
	protected double motorLoadP = MOTOR_LOAD_P_EDEFAULT;

	/**
	 * The default value of the '{@link #getMotorLoadQ() <em>Motor Load Q</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorLoadQ()
	 * @generated
	 * @ordered
	 */
	protected static final double MOTOR_LOAD_Q_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getMotorLoadQ() <em>Motor Load Q</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorLoadQ()
	 * @generated
	 * @ordered
	 */
	protected double motorLoadQ = MOTOR_LOAD_Q_EDEFAULT;

	/**
	 * The default value of the '{@link #getTe() <em>Te</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTe()
	 * @generated
	 * @ordered
	 */
	protected static final double TE_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getTe() <em>Te</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTe()
	 * @generated
	 * @ordered
	 */
	protected double te = TE_EDEFAULT;

	/**
	 * The default value of the '{@link #getTm() <em>Tm</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTm()
	 * @generated
	 * @ordered
	 */
	protected static final double TM_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getTm() <em>Tm</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTm()
	 * @generated
	 * @ordered
	 */
	protected double tm = TM_EDEFAULT;

	/**
	 * The default value of the '{@link #getTm0() <em>Tm0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTm0()
	 * @generated
	 * @ordered
	 */
	protected static final double TM0_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getTm0() <em>Tm0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTm0()
	 * @generated
	 * @ordered
	 */
	protected double tm0 = TM0_EDEFAULT;

	/**
	 * The default value of the '{@link #getA() <em>A</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getA()
	 * @generated
	 * @ordered
	 */
	protected static final double A_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getA() <em>A</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getA()
	 * @generated
	 * @ordered
	 */
	protected double a = A_EDEFAULT;

	/**
	 * The default value of the '{@link #getB() <em>B</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getB()
	 * @generated
	 * @ordered
	 */
	protected static final double B_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getB() <em>B</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getB()
	 * @generated
	 * @ordered
	 */
	protected double b = B_EDEFAULT;

	/**
	 * The default value of the '{@link #getC() <em>C</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getC()
	 * @generated
	 * @ordered
	 */
	protected static final double C_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getC() <em>C</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getC()
	 * @generated
	 * @ordered
	 */
	protected double c = C_EDEFAULT;

	/**
	 * The default value of the '{@link #getCurd() <em>Curd</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCurd()
	 * @generated
	 * @ordered
	 */
	protected static final double CURD_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getCurd() <em>Curd</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCurd()
	 * @generated
	 * @ordered
	 */
	protected double curd = CURD_EDEFAULT;

	/**
	 * The default value of the '{@link #getCurq() <em>Curq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCurq()
	 * @generated
	 * @ordered
	 */
	protected static final double CURQ_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getCurq() <em>Curq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCurq()
	 * @generated
	 * @ordered
	 */
	protected double curq = CURQ_EDEFAULT;

	/**
	 * The default value of the '{@link #getEpd() <em>Epd</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEpd()
	 * @generated
	 * @ordered
	 */
	protected static final double EPD_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getEpd() <em>Epd</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEpd()
	 * @generated
	 * @ordered
	 */
	protected double epd = EPD_EDEFAULT;

	/**
	 * The default value of the '{@link #getEpq() <em>Epq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEpq()
	 * @generated
	 * @ordered
	 */
	protected static final double EPQ_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getEpq() <em>Epq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEpq()
	 * @generated
	 * @ordered
	 */
	protected double epq = EPQ_EDEFAULT;

	/**
	 * The default value of the '{@link #getEppd() <em>Eppd</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEppd()
	 * @generated
	 * @ordered
	 */
	protected static final double EPPD_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getEppd() <em>Eppd</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEppd()
	 * @generated
	 * @ordered
	 */
	protected double eppd = EPPD_EDEFAULT;

	/**
	 * The default value of the '{@link #getEppq() <em>Eppq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEppq()
	 * @generated
	 * @ordered
	 */
	protected static final double EPPQ_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getEppq() <em>Eppq</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEppq()
	 * @generated
	 * @ordered
	 */
	protected double eppq = EPPQ_EDEFAULT;

	/**
	 * The default value of the '{@link #getMotorTotalLoad() <em>Motor Total Load</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorTotalLoad()
	 * @generated
	 * @ordered
	 */
	protected static final Complex MOTOR_TOTAL_LOAD_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getMotorTotalLoad() <em>Motor Total Load</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorTotalLoad()
	 * @generated
	 * @ordered
	 */
	protected Complex motorTotalLoad = MOTOR_TOTAL_LOAD_EDEFAULT;

	/**
	 * The default value of the '{@link #getMotorPower() <em>Motor Power</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorPower()
	 * @generated
	 * @ordered
	 */
	protected static final Complex MOTOR_POWER_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getMotorPower() <em>Motor Power</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getMotorPower()
	 * @generated
	 * @ordered
	 */
	protected Complex motorPower = MOTOR_POWER_EDEFAULT;

	/**
	 * The default value of the '{@link #getEp() <em>Ep</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEp()
	 * @generated
	 * @ordered
	 */
	protected static final Complex EP_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getEp() <em>Ep</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEp()
	 * @generated
	 * @ordered
	 */
	protected Complex ep = EP_EDEFAULT;

	/**
	 * The default value of the '{@link #getEpp() <em>Epp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEpp()
	 * @generated
	 * @ordered
	 */
	protected static final Complex EPP_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getEpp() <em>Epp</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEpp()
	 * @generated
	 * @ordered
	 */
	protected Complex epp = EPP_EDEFAULT;

	/**
	 * The default value of the '{@link #getZpMotorBase() <em>Zp Motor Base</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getZpMotorBase()
	 * @generated
	 * @ordered
	 */
	protected static final Complex ZP_MOTOR_BASE_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getZpMotorBase() <em>Zp Motor Base</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getZpMotorBase()
	 * @generated
	 * @ordered
	 */
	protected Complex zpMotorBase = ZP_MOTOR_BASE_EDEFAULT;

	/**
	 * The default value of the '{@link #getZppMotorBase() <em>Zpp Motor Base</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getZppMotorBase()
	 * @generated
	 * @ordered
	 */
	protected static final Complex ZPP_MOTOR_BASE_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getZppMotorBase() <em>Zpp Motor Base</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getZppMotorBase()
	 * @generated
	 * @ordered
	 */
	protected Complex zppMotorBase = ZPP_MOTOR_BASE_EDEFAULT;

	/**
	 * The default value of the '{@link #getEquivYSysBase() <em>Equiv YSys Base</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEquivYSysBase()
	 * @generated
	 * @ordered
	 */
	protected static final Complex EQUIV_YSYS_BASE_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getEquivYSysBase() <em>Equiv YSys Base</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getEquivYSysBase()
	 * @generated
	 * @ordered
	 */
	protected Complex equivYSysBase = EQUIV_YSYS_BASE_EDEFAULT;

	/**
	 * The default value of the '{@link #getNortonCurrInj() <em>Norton Curr Inj</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNortonCurrInj()
	 * @generated
	 * @ordered
	 */
	protected static final Complex NORTON_CURR_INJ_EDEFAULT = null;
	/**
	 * The cached value of the '{@link #getNortonCurrInj() <em>Norton Curr Inj</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNortonCurrInj()
	 * @generated
	 * @ordered
	 */
	protected Complex nortonCurrInj = NORTON_CURR_INJ_EDEFAULT;
	/**
	 * The default value of the '{@link #getNortonCurrInj() <em>Norton Curr Inj</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNortonCurrInj()
	 * @generated
	 * @ordered
	 */
	
	/**
	 * The default value of the '{@link #getIMotor() <em>IMotor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getIMotor()
	 * @generated
	 * @ordered
	 */
	protected static final Complex IMOTOR_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getIMotor() <em>IMotor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getIMotor()
	 * @generated
	 * @ordered
	 */
	protected Complex iMotor = IMOTOR_EDEFAULT;

	/**
	 * The default value of the '{@link #getZMultiFactor() <em>ZMulti Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getZMultiFactor()
	 * @generated
	 * @ordered
	 */
	protected static final double ZMULTI_FACTOR_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getZMultiFactor() <em>ZMulti Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getZMultiFactor()
	 * @generated
	 * @ordered
	 */
	protected double zMultiFactor = ZMULTI_FACTOR_EDEFAULT;

	/**
	 * The default value of the '{@link #getIMultiFactor() <em>IMulti Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getIMultiFactor()
	 * @generated
	 * @ordered
	 */
	protected static final double IMULTI_FACTOR_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getIMultiFactor() <em>IMulti Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getIMultiFactor()
	 * @generated
	 * @ordered
	 */
	protected double iMultiFactor = IMULTI_FACTOR_EDEFAULT;

	/**
	 * The default value of the '{@link #getVMultiFactor() <em>VMulti Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVMultiFactor()
	 * @generated
	 * @ordered
	 */
	protected static final double VMULTI_FACTOR_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getVMultiFactor() <em>VMulti Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVMultiFactor()
	 * @generated
	 * @ordered
	 */
	protected double vMultiFactor = VMULTI_FACTOR_EDEFAULT;

	/**
	 * The default value of the '{@link #getAcc() <em>Acc</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcc()
	 * @generated
	 * @ordered
	 */
	protected static final double ACC_EDEFAULT = 0.5;

	/**
	 * The cached value of the '{@link #getAcc() <em>Acc</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcc()
	 * @generated
	 * @ordered
	 */
	protected double acc = ACC_EDEFAULT;

	/**
	 * The default value of the '{@link #getSubStepN() <em>Sub Step N</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSubStepN()
	 * @generated
	 * @ordered
	 */
	protected static final int SUB_STEP_N_EDEFAULT = 10;

	/**
	 * The cached value of the '{@link #getSubStepN() <em>Sub Step N</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSubStepN()
	 * @generated
	 * @ordered
	 */
	protected int subStepN = SUB_STEP_N_EDEFAULT;

	/**
	 * The default value of the '{@link #getWdelt() <em>Wdelt</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getWdelt()
	 * @generated
	 * @ordered
	 */
	protected static final double WDELT_EDEFAULT = 0.8;

	/**
	 * The cached value of the '{@link #getWdelt() <em>Wdelt</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getWdelt()
	 * @generated
	 * @ordered
	 */
	protected double wdelt = WDELT_EDEFAULT;

	/**
	 * The default value of the '{@link #isTwoAxisModel() <em>Two Axis Model</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isTwoAxisModel()
	 * @generated
	 * @ordered
	 */
	protected static final boolean TWO_AXIS_MODEL_EDEFAULT = true;

	/**
	 * The cached value of the '{@link #isTwoAxisModel() <em>Two Axis Model</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isTwoAxisModel()
	 * @generated
	 * @ordered
	 */
	protected boolean twoAxisModel = TWO_AXIS_MODEL_EDEFAULT;
	
	/**
	 * @generated NOT
	 * @ordered
	 */
	protected static final double LOAD_FACTOR_EDEFAULT = 0.8;
	
	/**
	 * @generated NOT
	 * @ordered
	 */
	protected double loadFactor = LOAD_FACTOR_EDEFAULT;
	
	/**
	 * @generated NOT
	 * @ordered
	 */
	protected Hashtable<String, Object> states = null;
	
	/*
	 * Two levels of undervoltage tripping are represented with the following input parameters:
		Vtr1x 	ï¿? First U/V Trip V (pu)
		Ttr1x 	ï¿? First U/V Trip delay  time (sec)
		Ftr1x 	ï¿? First U/V Trip fraction
		Vrc1x 	ï¿? First U/V reconnection V (pu)
		Trc1x 	ï¿? First U/V reconnection delay time (sec)
		Vtr2x 	ï¿? Second U/V Trip V (pu)
		Ttr2x 	ï¿? Second U/V Trip delay time (sec)
		Ftr2x 	ï¿? Second U/V Trip fraction
		Vrc2x 	ï¿? Second U/V reconnection V (pu)
		Trc2x 	ï¿? Second U/V reconnection delay time (sec)
		
		***The fractions tripped by each level are cumulative. (both are independent parts)

	 */
	
	/**
	 * First low voltage protection
	 */
	protected double Vtr1 = 0.0;
	protected double Ttr1 = 9999.0;
	protected double Ftr1 = 0.0; // first low voltage trip fraction
	
	/**
	 * First low voltage reconnection level
	 */
	protected double Vrc1 = 9999.0;
	protected double Trc1 = 9999.0;
	

	
	/**
	 * Second low voltage protection
	 */
	protected double Vtr2 = 0.0;
	protected double Ttr2 = 9999.0;
	protected double Ftr2 = 0.0;
	
	/**
	 * Second low voltage reconnection level
	 */
	protected double Vrc2 = 9999.0;
	protected double Trc2 = 9999.0;
	
	protected double lvProtectionTimer1 = 0.0;
	protected double lvReconnectTimer1  = 0.0;
	protected double lvProtectionTimer2 = 0.0;
	protected double lvReconnectTimer2  = 0.0;
	
	protected boolean lvProtection1Applied = false;
	protected boolean lvProtection2Applied = false;
	protected boolean lvReconnect1Applied = false;
	protected boolean lvReconnect2Applied = false;
	
	protected double Fuv =1.0; // Fraction not tripped by internal low voltage relays of the motor
	
	protected double Fonline = 1.0; // the total on line fraction ( reference to initial Power) after considering change by  internal protective devices  and/or external load shedding relay models and/or load change events
	
	private double timestep = 0.0;
	
	private List<MotorProtectionControl> motorProtectionList = null;
	
	private static final String OUT_SYMBOL_P ="IndMotorP";
	private static final String OUT_SYMBOL_Q ="IndMotorQ";
	private static final String OUT_SYMBOL_V ="IndMotorVt";
	private static final String OUT_SYMBOL_I ="IndMotorIt";
	private static final String OUT_SYMBOL_TE ="IndMotorTe";	
	private static final String OUT_SYMBOL_TM ="IndMotorTm";
	private static final String OUT_SYMBOL_SLIP ="IndMotorSlip";
	private static final String OUT_SYMBOL_FUV ="IndMotorFuv";
	//private String   extended_device_Id = "";
	
	public InductionMotorImpl() {
		super();
		this.states = new Hashtable<>();
		
	}

	public InductionMotorImpl(BaseDStabBus bus1, String id) {
		super();
		this.states = new Hashtable<>();
		this.setDStabBus(bus1);
		bus1.addDynamicLoadModel(this);
		this.setId(id);
	}

	public double getRa() {
		return ra;
	}

	public void setRa(double newRa) {
		ra = newRa;
	}

	public double getXl() {
		return xl;
	}

	public void setXl(double newXl) {
		xl = newXl;
	}

	public double getXm() {
		return xm;
	}

	public void setXm(double newXm) {
		xm = newXm;
	}

	public double getRr1() {
		return rr1;
	}

	public void setRr1(double newRr1) {
		rr1 = newRr1;
	}

	public double getXr1() {
		return xr1;
	}

	public void setXr1(double newXr1) {
		xr1 = newXr1;
	}

	public double getRr2() {
		return rr2;
	}

	public void setRr2(double newRr2) {
		rr2 = newRr2;
	}

	public double getXr2() {
		return xr2;
	}

	public void setXr2(double newXr2) {
		xr2 = newXr2;
	}

	public double getH() {
		return h;
	}

	public void setH(double newH) {
		h = newH;
	}

	public double getD() {
		return d;
	}

	public void setD(double newD) {
		d = newD;
	}

	public double getXs() {
		return xs;
	}

	public void setXs(double newXs) {
		xs = newXs;
	}

	public double getXp() {
		return xp;
	}

	public void setXp(double newXp) {
		xp = newXp;
	}

	public double getXpp() {
		return xpp;
	}

	public void setXpp(double newXpp) {
		xpp = newXpp;
	}

	public double getTp0() {
		return tp0;
	}

	public void setTp0(double newTp0) {
		tp0 = newTp0;
	}

	public double getTpp0() {
		return tpp0;
	}

	public void setTpp0(double newTpp0) {
		tpp0 = newTpp0;
	}

	public double getVtr1() {
		return Vtr1;
	}

	public void setVtr1(double vtr1) {
		Vtr1 = vtr1;
	}

	public double getTtr1() {
		return Ttr1;
	}

	public void setTtr1(double ttr1) {
		Ttr1 = ttr1;
	}
	
	public double getFtr1() {
		return Ftr1;
	}

	public void setFtr1(double ftr1) {
		Ftr1 = ftr1;
	}

	public double getVrc1() {
		return Vrc1;
	}

	public void setVrc1(double vrc1) {
		Vrc1 = vrc1;
	}

	public double getTrc1() {
		return Trc1;
	}

	public void setTrc1(double trc1) {
		Trc1 = trc1;
	}

	public double getVtr2() {
		return Vtr2;
	}

	public void setVtr2(double vtr2) {
		Vtr2 = vtr2;
	}

	public double getTtr2() {
		return Ttr2;
	}

	public void setTtr2(double ttr2) {
		Ttr2 = ttr2;
	}
	
	public double getFtr2() {
		return Ftr2;
	}

	public void setFtr2(double ftr2) {
		Ftr2 = ftr2;
	}

	public double getVrc2() {
		return Vrc2;
	}

	public void setVrc2(double vrc2) {
		Vrc2 = vrc2;
	}

	public double getTrc2() {
		return Trc2;
	}

	public void setTrc2(double trc2) {
		Trc2 = trc2;
	}
	/**
	 * Get the fraction of motors not tripped by the low voltage protections, including both the first and second level protections
	 * @return
	 */
	public double getFuv(){
		return this.Fuv;
	}

	public double getRatedVoltage() {
		if(this.ratedVoltage ==0.0)
			this.ratedVoltage = this.getDStabBus().getBaseVoltage(); 
		return ratedVoltage;
	}

	public void setRatedVoltage(double newRatedVoltage) {
		ratedVoltage = newRatedVoltage;
	}

	public double getSlip() {
		return slip;
	}

	public void setSlip(double newSlip) {
		slip = newSlip;
	}

	public double getW() {
		return w;
	}

	public void setW(double newW) {
		w = newW;
	}

	public double getMotorLoadP() {
		return motorLoadP;
	}

	public void setMotorLoadP(double newMotorLoadP) {
		motorLoadP = newMotorLoadP;
	}

	public double getMotorLoadQ() {
		return motorLoadQ;
	}

	public void setMotorLoadQ(double newMotorLoadQ) {
		motorLoadQ = newMotorLoadQ;
	}

	public double getTe() {
		return te;
	}

	public void setTe(double newTe) {
		te = newTe;
	}

	public double getTm() {
		return tm;
	}

	public void setTm(double newTm) {
		tm = newTm;
	}

	public double getTm0() {
		return tm0;
	}

	public void setTm0(double newTm0) {
		tm0 = newTm0;
	}

	public double getA() {
		return a;
	}

	public void setA(double newA) {
		a = newA;
	}

	public double getB() {
		return b;
	}

	public void setB(double newB) {
		b = newB;
	}

	public double getC() {
		return c;
	}

	public void setC(double newC) {
		c = newC;
	}

	public double getCurd() {
		return curd;
	}

	public void setCurd(double newCurd) {
		curd = newCurd;
	}

	public double getCurq() {
		return curq;
	}

	public void setCurq(double newCurq) {
		curq = newCurq;
	}

	public double getEpd() {
		return epd;
	}

	public void setEpd(double newEpd) {
		epd = newEpd;
	}

	public double getEpq() {
		return epq;
	}

	public void setEpq(double newEpq) {
		epq = newEpq;
	}

	public double getEppd() {
		return eppd;
	}

	public void setEppd(double newEppd) {
		eppd = newEppd;
	}

	public double getEppq() {
		return eppq;
	}

	public void setEppq(double newEppq) {
		eppq = newEppq;
	}

	public Complex getMotorTotalLoad() {
		return motorTotalLoad;
	}

	public void setMotorTotalLoad(Complex newMotorTotalLoad) {
		motorTotalLoad = newMotorTotalLoad;
	}

	public Complex getMotorPower() {
		return motorPower;
	}

	public void setMotorPower(Complex newMotorPower) {
		motorPower = newMotorPower;
	}

	public Complex getEp() {
		return ep;
	}

	public void setEp(Complex newEp) {
		ep = newEp;
	}

	public Complex getEpp() {
		return epp;
	}

	public void setEpp(Complex newEpp) {
		epp = newEpp;
	}

	public Complex getZpMotorBase() {
		return zpMotorBase;
	}

	public void setZpMotorBase(Complex newZpMotorBase) {
		zpMotorBase = newZpMotorBase;
	}

	public Complex getZppMotorBase() {
		return zppMotorBase;
	}

	public void setZppMotorBase(Complex newZppMotorBase) {
		zppMotorBase = newZppMotorBase;
	}

	public Complex getEquivYSysBase() {
		return equivYSysBase;
	}

	public void setEquivYSysBase(Complex newEquivYSysBase) {
		equivYSysBase = newEquivYSysBase;
	}

	public Complex getNortonCurrInj() {
		return nortonCurrInj;
	}

	public void setNortonCurrInj(Complex newNortonCurrInj) {
		nortonCurrInj = newNortonCurrInj;
	}

	public Complex getIMotor() {
		return iMotor;
	}

	public void setIMotor(Complex newIMotor) {
		iMotor = newIMotor;
	}

	public double getZMultiFactor() {
		return zMultiFactor;
	}

	public void setZMultiFactor(double newZMultiFactor) {
		zMultiFactor = newZMultiFactor;
	}

	public double getIMultiFactor() {
		return iMultiFactor;
	}

	public void setIMultiFactor(double newIMultiFactor) {
		iMultiFactor = newIMultiFactor;
	}

	public double getVMultiFactor() {
		return vMultiFactor;
	}

	public void setVMultiFactor(double newVMultiFactor) {
		vMultiFactor = newVMultiFactor;
	}

	public double getAcc() {
		return acc;
	}

	public void setAcc(double newAcc) {
		acc = newAcc;
	}

	public int getSubStepN() {
		return subStepN;
	}

	public void setSubStepN(int newSubStepN) {
		subStepN = newSubStepN;
	}

	public double getWdelt() {
		return wdelt;
	}

	public void setWdelt(double newWdelt) {
		wdelt = newWdelt;
	}

	public boolean isTwoAxisModel() {
		return twoAxisModel;
	}

	public void setTwoAxisModel(boolean newTwoAxisModel) {
		twoAxisModel = newTwoAxisModel;
	}
	
	public boolean initStates() {
		boolean initSucceed = true;
		
		
		// calculate the operation parameter Xp,Tp0 if physical parameters are provided
		
		 double w0 = 2*Math.PI*this.getDStabBus().getNetwork().getFrequency();
		
		 //consider two ways of parameter input
		 // 1. physical 
		if(xl>0.0 && xm >0.0 && xr1 >0 && rr1>0 && xp==0.0 && tp0==0.0){
			xs = xl+xm;
			xp = xl+xm*xr1/(xm+xr1);
			tp0 = (xr1+xm)/w0/rr1;
			
			if(xr2>0.0 && rr2>0.0){
				xpp = xl+xr1*xr2*xm/(xr1*xr2+xr1*xm+xr2*xm);
				tpp0 = (xr2+xr1*xm/(xr1+xm))/w0/rr2;
			}
		}
		
		//if the input is in the format of performance data, the performance data needs to be converted to physical data
		if(this.xr1==0.0 && this.xp>0.0){
			//if Xl is not defined, assuming xl = 0.06 pu
			if(xl ==0.0) xl = 0.06;
			
			if(xpp> 0 && xl>xpp){
				xl = 0.8*xpp;
			}
			
			if(tpp0 ==0.0 || xpp ==0.0 || xpp ==xp){
				twoAxisModel = false;
				xpp = xp;
			}
			
			
			xm = xs - xl;
			
			double xm1 = xp - xl;
			
			this.xr1 = 1/(1/xm1-1/xm);
			this.rr1 = (this.xr1+this.xm)/w0/tp0;
			
			if(twoAxisModel){
			    double xm2 = xpp-xl;
			    this.xr2 = 1/(1/xm2 - 1/xm - 1/this.xr1);
			    this.rr2 = (this.xr2+xm1)/w0/tpp0;
			}
		
		}
		
		if(xp==0.0 && tp0==0.0){
			throw new Error("Error in the input motor data is not complete or not properly provided! Motor ID #"+this.curd);
		}
		
		/*
		 *  Note: The input R, X parameters of the motor are on the motor MVA base. 
		 *        Necessary conversion is needed during the calculation
		 */
		

		
		//TODO the initLoad is the total load at the bus ,include constant Z and I load
		//In the future, this may need to be update to consider the constant P load only
		
		// if assigned initial loadPQ directly
		if(this.getLoadPercent()<=0 && this.getInitLoadPQ().getReal()>0){
			this.motorLoadP = this.getInitLoadPQ().getReal();
			this.motorTotalLoad = this.getInitLoadPQ();
		}	
		else{	
			
	        Complex busTotalLoad = this.getDStabBus().getInitLoad();
			this.motorLoadP = busTotalLoad.getReal()*this.loadPercent/100.0d;
			
			//total load on system base
			this.motorTotalLoad = busTotalLoad.multiply(this.loadPercent/100.0d); 
		}
		
		
		double sysMVABase = this.getDStabBus().getNetwork().getBaseMva();
		
		if(this.getMvaBase() ==0.0){
			this.setMvaBase( this.motorLoadP*sysMVABase/this.getLoadFactor());
		}
		
		
		// z, i, v multiFactors will be used later, so calculate them first 
		// when the motor mva based parameters multiply the corresponding multiFactor, they become ones on system base
		
		// this process requires MVABase info, so it has to be placed after MVABase calculation
		calMultiFactors();
		
		//convert to motor base, since all the motor parameters are on motor base
		this.motorLoadP = this.motorLoadP*sysMVABase/this.getMvaBase(); 
		
		double P = motorLoadP;
		double V = this.getDStabBus().getVoltageMag();
        
		Complex equivYMotorBase = null; 
		
	
		
		// 1. calculate the slip based on the real power 
		// the following process is based on the BPA document, for one rotor axis only
		if(((tpp0==tp0 || tpp0 <=0.0) &&this.xp>0) || (this.xr2==0 && this.rr2==0 && this.xr1>0)){
			
			// fix bad data
			if((xpp> 0 && xl>xpp) || (xl==0.0 && xpp>0)){
				xl = 0.8*xpp;
			}
			
			// it is a one-axis motor model
			twoAxisModel = false;
			
			double XRM = this.xr1 + this.xm;
			double XLM = xl + xm;
			double Xc = xl*xr1+xl*xm+xm*xr1;
			
			double A1 = ra*ra+XLM*XLM;
			double B1 = 2*ra*xm*xm;
			double C1 = Xc*Xc+ra*ra*XRM*XRM;
			
			
			double A = A1*P/V/V-ra;
			double B = B1*P/V/V-xm*xm;
			double C = C1*P/V/V-ra*XRM*XRM;
			
			if(B*B-4*A*C<0) {
				throw new Error("Error in the input motor data causing error in the initial slip calculation");
			}
			double R = (-B+Math.sqrt(B*B-4*A*C))/(2*A);
			slip =rr1/R;
			
			this.motorLoadQ = V*V*(XLM*R*R+Xc*XRM)/(A1*R*R+B1*R+C1);
			
			// Y = S*/V^2;
			equivYMotorBase = new Complex(this.motorLoadP,-this.motorLoadQ).divide(V*V); 
			
		}
		//TODO
		// two-axis rotor motor
		else{
			
			// for two axis motor, use predict and correction approach to update the slip;
			slip = 0.005;
			double slip_upper = 0.05;
			double slip_lower = 0.001;
			double slip_step = 0.005;
			double Pcalc_last = 0 ;
			for(int i =0;i<100;i++){
			 	
			  Complex Zr1 = (new Complex(rr1/slip,xr1));
			  Complex Zr2 = (new Complex(rr2/slip,xr2));
			  Complex Yr1 = new Complex(1.0,0).divide(Zr1);
			  Complex Yr2 = new Complex(1.0,0).divide(Zr2);
			  Complex Ym = new Complex(1.0,0).divide(new Complex(0,xm));
			  Complex Yeq1 = Ym.add(Yr1).add(Yr2);
			  Complex Zeq = new Complex(ra,xl).add(new Complex(1.0,0).divide(Yeq1));
			  Complex Yeq = new Complex(1.0,0).divide(Zeq);
			  
			  //calculate the Pe 
			  double Pcalc = V*V*Yeq.getReal();
			  
			  if(Math.abs(Pcalc-P)>1.0E-6){
				 
				 
				  if (P>Pcalc && slip>slip_lower && slip<slip_upper){   // slip too small
					  slip_lower = slip;
					  slip =(slip_lower+slip_upper)/2;
					  
				  }
				  else if(Pcalc>P && slip>slip_lower && slip<slip_upper){ //slip too large
				     slip_upper = slip;
				     slip =(slip_lower+slip_upper)/2;
				  }
				  
				  if(slip>SLIPMAX && Pcalc<P ){ // slip too large
					  slip = SLIPMAX/2;
				  }
				  
			  }
			  else{
				  // slip is successfully initialized;
				  equivYMotorBase = Yeq;
				  this.motorLoadQ = -V*V*Yeq.getImaginary();
				  
				  break;
			  }
			
			  if(i>=100){
				  throw new Error("Slip is not properly selected after 100 trials, Motor Id #"+this.id);
				}
			}
			
		}
		
		// check slip range
		 if(slip<SLIPMIN || slip>SLIPMAX){
		  
			IpssLogger.getLogger().warning("The calculated slip is out of normal range [0.01,0.05], please check the data");
		 }
		 
		 // motor
		 this.motorPower = new Complex(this.motorLoadP, this.motorLoadQ );
		 this.setLoadPQ(this.motorPower);
		 
		 // set the init loadPQ in pu on system base
		 this.setInitLoadPQ(this.motorPower.multiply(this.getMvaBase()/sysMVABase));
		 
		
		// 2. Calculate the total current drawn from the system based on LoadPQ of the machine
	
//	     Complex ItotalMotorBase = this.getDStabBus().getVoltage().multiply(equivYMotorBase);
		
		// 3a. calculate the current that actually flows into the induction machine, excluding the effect of compensation Y
		
	    iMotor = this.getDStabBus().getVoltage().multiply(equivYMotorBase);
	  
		
		// 3b. calculate the necessary var compensation, capacitive is positive
	    
	    double compensateVar = this.motorLoadQ*this.getMvaBase()/sysMVABase - this.motorTotalLoad.getImaginary();
	     
		
		// 4. first calculate E' (and E'' if exists)  and get the rotor angle, then calculate E'd, E'q (and E''d, E''q if exists)  
	    
	    zpMotorBase = new Complex(ra,xp);
	    
	    ep = this.getDStabBus().getVoltage().subtract(zpMotorBase.multiply(iMotor));
	    
	    // calculate the the Norton equivalent
	    if(twoAxisModel){
	    	//TODO this needs to be verified, refer to PSAT manual
	    	ep = iMotor.multiply(xs-xp).divide(new Complex(w0*tp0*slip,-1));
	    	
//	    	double ir = iMotor.getReal();
//	    	double im = iMotor.getImaginary();
//	    	double epr = (w0*tp0*slip*(xs-xp)*ir - (xs-xp)*im)/(1 + Math.pow(w0*tp0*slip, 2));
//	    	double epm = (w0*tp0*slip*(xs-xp)*im + (xs-xp)*ir)/(1 + Math.pow(w0*tp0*slip, 2));
//	    	
//	    	ep = new Complex(epr,epm);
	    	
	      	zppMotorBase = new Complex(ra,xpp);
	        epp = this.getDStabBus().getVoltage().subtract(zppMotorBase.multiply(iMotor));
	    	
	    	Complex ZppSysBase = new Complex(ra,xpp).multiply(this.zMultiFactor);
	    	this.equivYSysBase = new Complex(1.0,0).divide(ZppSysBase);
	    	
	    	this.nortonCurInj = epp.multiply(equivYSysBase);
	    }
	    else{
	    	Complex ZpSysBase = zpMotorBase.multiply(this.zMultiFactor);
	    	this.equivYSysBase = new Complex(1.0,0).divide(ZpSysBase);
	    	
	    	this.nortonCurInj = ep.multiply(equivYSysBase);
	    }
	    	
 
	     // d-axis is so chosen that the negative direction coincide with the phase a axis at t = 0, and its displacement from a 
        // axis at any time is Wst (Ws is the synchronous speed).
   
	    
	    //The following implementation is based on the eqn. (4-16) on Yixin Ni's text  book AND psat manual
	   
//	    Complex temp = ep.subtract(iMotor.multiply(new Complex(0,xs-xp))).divide(tp0);
//	    Complex dEp = new Complex(0,-w0*slip).multiply(ep).subtract(temp);
	    
	    double Ir = iMotor.getReal();
	    double Im = iMotor.getImaginary();
	    
	    double Epr = ep.getReal();
	    double Epm = ep.getImaginary();
	    
	    double dEpr = w0*slip*Epm-(Epr+(xs-xp)*Im)/tp0;
	    double dEpm = -w0*slip*Epr-(Epm-(xs-xp)*Ir)/tp0;
	    
	    
	    if(dEpr>1.0E-4 || dEpm >1.0E-4){
	    	IpssLogger.getLogger().severe("Initialization error, the dEp is not zero. dEp (re,im)= "+dEpr+","+dEpm);
	    	return  initSucceed = false;
	    }
	    
	    // electrical torque : Te = Re{E'Imotor*}
	    te = Epr*Ir+Epm*Im;
	    
	    if(twoAxisModel){

		    double Eppr = epp.getReal();
		    double Eppm = epp.getImaginary();
		    
		    double dEppr1 = -w0*slip*(Epm-Eppm)+dEpr+(Epr-Eppr-(xp-xpp)*Im)/tpp0;
//		    double dEppm = w0*slip*(Epr-Eppr)+dEpm+(Epm-Eppm+(xp-xpp)*Ir)/tpp0;
//		    
		    double dEppr2 = -w0*slip*(Epm-Eppm)+dEpr+(Epr-Eppm-(xp-xpp)*Im)/tpp0;
//		    double dEppm = w0*slip*(Epr-Eppr)+dEpm+(Epm-Eppr+(xp-xpp)*Ir)/tpp0;
		    
		    double dEppr3 = -w0*slip*(Epm-Eppm)+dEpr-(Epr-Eppm-(xp-xpp)*Im)/tpp0;
//		    double dEppm = w0*slip*(Epr-Eppr)+dEpm-(Epm-Eppr+(xp-xpp)*Ir)/tpp0;
               
		    double dEppr = -w0*slip*(Epm-Eppm)+dEpr-(Epr-Eppr-(xp-xpp)*Im)/tpp0;
		    System.out.println("dEppr ="+dEppr); 
		    System.out.println("w0*slip*(Epm-Eppm) ="+w0*slip*(Epm-Eppm));
		    
		    double dEppm = w0*slip*(Epr-Eppr)+dEpm-(Epm-Eppm+(xp-xpp)*Ir)/tpp0;		    
		    System.out.println("w0*slip*(Epr-Eppr) ="+w0*slip*(Epr-Eppr));
		    
		    te = Eppr*Ir+Eppm*Im;
		    
		    if(dEppr>1.0E-4 || dEppm >1.0E-4){
		    	IpssLogger.getLogger().severe("Initialization error, the dEpp is not zero. dEpp (re,im)= "+dEppr+","+dEppm);
		    	return initSucceed = false;
		    }
	    }
	    
	    
	    
	    // initialization stage, Tm = Te;
	    tm = te; 
	    w = 1- slip;
	    //Tm = (a+bw+cw^2)T0
	    if(b == 0.0 && c == 0.0) a = 1.0;
	    tm0 = tm/(a+b*w+c*w*w);
	    
	    // extended Id;
	    extendedDeviceId = "IndMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,  extendedDeviceId);
	    
	    return initSucceed;
		
	}
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method) {
		// if the motor is out-of-service or tripped
		if(this.Fuv<=0.0 || this.Fonline <=0.0){
			this.ep = new Complex(0.0);
			this.epp = new Complex(0.0);
			this.te = 0.0;
			
			w =1-slip;
		    this.tm = (a + b*w + c*w*w)*tm0;
		    
		    double dSLIP_dt = (tm-te)/(2*this.h);
		    slip += dSLIP_dt*dt;
		    
		    if(this.slip >= 1.0)
		    	this.slip = 1.0;
			
			return true;
		}
		
		// if it is in-service
		boolean enableSubStepIntegration = false;
		this.timestep = dt;
		double tstep =dt;
		
		if(twoAxisModel){
			if(this.tpp0<dt*4 || w <0.8) {
				enableSubStepIntegration = true;
				tstep = dt/subStepN;
			}
			
		}
		else{
			if(this.tp0<dt*4 || w <0.8) {
				enableSubStepIntegration = true;
				tstep = dt/subStepN;
			}

		}
		
		// rotor electric part dynamic 
	
	     double w0 = 2*Math.PI*this.getDStabBus().getNetwork().getFrequency();
	    
	     Complex vt = this.getDStabBus().getVoltage();
	    
	     if(twoAxisModel){
		     iMotor = vt.subtract(epp).divide(zppMotorBase);
	     }
	     else
	    	 iMotor = vt.subtract(ep).divide(zpMotorBase);
	     
    	 double Ir = iMotor.getReal();
 	     double Im = iMotor.getImaginary();
 	     
 	    
 	    if(!enableSubStepIntegration){
 	       integrationSubStep(dt, w0, Ir, Im);
 	    }
 	    else{
 	    	for(int i=0; i<subStepN;i++){
 	    		 integrationSubStep(tstep, w0, Ir, Im);
 	    	}
 	    }

 	
		return true;
	}
	
	private void integrationSubStep(double dt, double w0, double Ir, double Im) {
		double Epr = ep.getReal();
	    double Epm = ep.getImaginary();
	    
	    Complex dEp_dt1 = calc_Ep_predict_step( w0, Epr, Epm, Im, Ir);
	    
	    Complex dEp1 = dEp_dt1.multiply(dt);
	    double dEpr1 = dEp1.getReal();
	    double dEpm1 = dEp1.getImaginary();
	    
	    
	    
	    //TODO need to renew IMotor?????/
	    
        Complex dEp_dt2 = calc_Ep_corrective_step( w0, Epr+dEpr1, Epm+dEpm1, Im, Ir);
        
        
	    Complex dEp =dEp_dt1.add(dEp_dt2).multiply(dt/2.0);
	    
	    ep = ep.add(dEp);
	    
	    
	    
	    if(twoAxisModel){
	    
	 	     
	 	    double Eppr = epp.getReal();
		    double Eppm = epp.getImaginary();
		    
		    double dEpr = w0*slip*Epm-(Epr+(xs-xp)*Im)/tp0;
	 	    double dEpm = -w0*slip*Epr-(Epm-(xs-xp)*Ir)/tp0;
	    	
	 	    Complex dEpp_dt1 = calc_Epp_predict_step(w0, Epr, Epm, Eppr, Eppm, dEpr, dEpm, Im, Ir);
	 	    Complex dEpp1 = dEpp_dt1.multiply(dt);
	 	   
	 	    double dEppr1 = dEpp1.getReal();
		    double dEppm1 = dEpp1.getImaginary();
		    
		    Complex dEpp_dt2 = calc_Epp_corrective_step(w0, Epr+dEpr1, Epm+dEpm1, Eppr+dEppr1, Eppm+dEppm1, dEpr1, dEpm1, Im, Ir);
		    
		    Complex dEpp =dEpp_dt1.add(dEpp_dt2).multiply(dt/2.0);
		    
		    epp = epp.add(dEpp);
		    // Re{E''I*}
		    te = epp.multiply(new Complex(Ir,-Im)).getReal();
		    
	    }
	    else{
	  	  
	    	te = ep.multiply(new Complex(Ir,-Im)).getReal();

	    }
	 
	    
	    //Rotor mechanical part dynamic -- slip 
	    
	    //Tm = (a + bw + cw^2)Tm0, w is speed
	    
	    w =1-slip;
	    tm = (a + b*w + c*w*w)*tm0;
	    
	    //dSLIP/dt = (Tm-Te)/2H
	    double dSLIP_dt = (tm-te)/(2*this.h);
	    slip += dSLIP_dt*dt;
	    
	    //System.out.println(this.extended_device_Id+" slip ="+slip);
	}
	
	
	private Complex calc_Epp_predict_step(double w0, double Epr, double Epm, double Eppr, double Eppm, double dEpr, double dEpm, double Im, double Ir){
		double dEppr = -w0*slip*(Epm-Eppm)+dEpr+(Epr-Eppr-(xp-xpp)*Im)/tpp0;
	    double dEppm = w0*slip*(Epr-Eppr)+dEpm+(Epm-Eppm+(xp-xpp)*Ir)/tpp0;
	    return new Complex(dEppr,dEppm);
	}
	
    private Complex calc_Epp_corrective_step(double w0, double Epr, double Epm, double Eppr, double Eppm, double dEpr, double dEpm, double Im, double Ir){
    	double dEppr = -w0*slip*(Epm-Eppm)+dEpr+(Epr-Eppr-(xp-xpp)*Im)/tpp0;
	    double dEppm = w0*slip*(Epr-Eppr)+dEpm+(Epm-Eppm+(xp-xpp)*Ir)/tpp0;
	    return new Complex(dEppr,dEppm);
	}
	
    private Complex calc_Ep_predict_step(double w0, double Epr, double Epm, double Im, double Ir){
    	 double dEpr = w0*slip*Epm-(Epr+(xs-xp)*Im)/tp0;
 	     double dEpm = -w0*slip*Epr-(Epm-(xs-xp)*Ir)/tp0;
 	     
 	     return new Complex(dEpr,dEpm);
	}
    
    private Complex calc_Ep_corrective_step(double w0, double Epr, double Epm, double Im, double Ir){
   	 double dEpr = w0*slip*Epm-(Epr+(xs-xp)*Im)/tp0;
	     double dEpm = -w0*slip*Epr-(Epm-(xs-xp)*Ir)/tp0;
	     
	     return new Complex(dEpr,dEpm);
	}
	
	@Override
	public Complex getPosSeqEquivY() {
		if(equivYSysBase == null){
			if(this.twoAxisModel){
				 Complex Zpp = new Complex(ra,xpp).multiply(this.zMultiFactor);
				 return equivYSysBase= new Complex(1.0,0).divide(Zpp);
				 
			}
			else{
				Complex Zp = new Complex(ra,xp).multiply(this.zMultiFactor);
				 return equivYSysBase= new Complex(1.0,0).divide(Zp);
			}
		}
		else
			return equivYSysBase;
		
	}
	
	
	@Override
	public Object getOutputObject() {
		
		 Complex vt = this.getDStabBus().getVoltage();
		 
		 Complex iEquivYSysBase = vt.multiply(equivYSysBase);
	    // update the Norton current injection
 	    
 	   if(twoAxisModel){
 		  this.nortonCurInj = epp.multiply(equivYSysBase);
 	   }
 	   else{
 		  this.nortonCurInj = ep.multiply(equivYSysBase);
 	   }
 	    
 	  
 	    
 	   // consider the tripping by protection
 	   //NOTE: To avoid update the Ymatrix, the motor equivalent Norton admittance remains <equivYSysBase>, 
 	   // The norton Current injection needs to compensate for this.
 	   
// 	   if(this.Fuv<1.0)
// 	     this.nortonCurInj = this.nortonCurInj.multiply(this.Fuv).add(iEquivYSysBase.multiply(1-this.Fuv));
 	   
 	   // 3/5/2018 consider the total on-line fraction 
 	  if(this.Fonline!=1.0)
  	     this.nortonCurInj = this.nortonCurInj.multiply(this.Fonline).add(iEquivYSysBase.multiply(1-this.Fonline));
 		  
 	   //System.out.println("Fuv, Inorton = "+this.Fuv+", "+this.nortonCurInj.toString());
	   
 	   return this.nortonCurInj;
		
		
	}
	
	/**
	 * Mainly for check the 
	 * @return
	 */
	private boolean post_solution_step_process(){
		boolean flag = true;
		
		double vt = this.getDStabBus().getVoltage().abs();
		
		if(this.lvProtectionTimer1>=this.Ttr1 && !this.lvProtection1Applied ){
			this.Fuv = this.Fuv -this.Ftr1;
			this.lvProtection1Applied = true;
		}
		
		if(this.lvProtectionTimer2>=this.Ttr2 && !this.lvProtection2Applied ){
			this.Fuv = this.Fuv -this.Ftr2;
			this.lvProtection2Applied = true;
		}
		if(this.lvReconnectTimer1>=this.Trc1 && this.lvProtection1Applied && !this.lvReconnect1Applied){
			this.Fuv = this.Fuv +this.Ftr1;
			// update it for the next voltage dip, if any
			this.lvProtection1Applied = false;
			this.lvReconnect1Applied = true;
		}
		if(this.lvReconnectTimer2>=this.Trc2  && this.lvProtection2Applied && !this.lvReconnect2Applied){
			this.Fuv = this.Fuv +this.Ftr2;
			// update it for the next voltage dip, if any
			this.lvProtection2Applied = false;
			this.lvReconnect2Applied = true;
		}
		
		if(vt<this.Vtr1 && !this.lvProtection1Applied ){
			this.lvProtectionTimer1 += this.timestep;
		}
		else if (vt>=this.Vtr1){
			this.lvProtectionTimer1 = 0.0;
			//this.lvProtection1Applied = false;
		}
		
			
		if(vt<this.Vtr2 && !this.lvProtection2Applied){
			this.lvProtectionTimer2 += this.timestep;
		}
		else if (vt>this.Vtr2){
			this.lvProtectionTimer2 = 0.0;
			//this.lvProtection2Applied = false;
		}
		
		
		if(vt>this.Vrc1){
			this.lvReconnectTimer1 +=this.timestep;
		}
		else{
			this.lvReconnectTimer1 = 0.0;
		}
			
		
		if(vt>this.Vrc2){
			this.lvReconnectTimer2 +=this.timestep;
		}
		else
			this.lvReconnectTimer2 = 0.0;
		
		
		
		
		boolean singleMotorTrip = false;
		boolean singleMotorReconnect = false;
		
		if(this.getProtectionControlList().size()>0){
			for(MotorProtectionControl mpc: this.getProtectionControlList()){
				mpc.checkTripAndReconnectAction(vt, this.timestep);
				
				// if any protection or control trip the motor, then it is totally tripped
				if(mpc.isTripStatus()){
					singleMotorTrip = true;
					break;
				}
				
				if(!mpc.isTripStatus() && mpc.isReconnectStatus()){
					singleMotorReconnect = true;
//					singleMotorTrip = false;
				}
				else{
					singleMotorReconnect = false;
				}
			}
			// for single motor, only need to check whether it is tripped or not, since there are only two protection operating modes, i.e., tripped or not tripped
			if(singleMotorTrip){
				this.Fuv = 0.0;
				
			}
			else
				this.Fuv = 1.0;
			
		}
		
		//update the total on-line fraction
		this.Fonline = this.Fonline*this.Fuv;

		
		return flag;
	}
	
	@Override
	public boolean updateAttributes(boolean netChange) {
		post_solution_step_process();
		
		if(this.Fonline>0.0){
			Complex vt = this.getDStabBus().getVoltage();
			 
			Complex iEquivYSysBase = vt.multiply(equivYSysBase);
			
			  //Calculate the actual current following into the motor. the current direction is positive when following into the motor, which is also reflected by the "subtract"
		 	  // current on system base 
		 	Complex IInj2Motor = iEquivYSysBase.subtract(this.nortonCurInj); 
			this.motorPower = vt.multiply(IInj2Motor.divide(this.iMultiFactor).conjugate()); // power on motor base
			this.loadPQ = this.motorPower;
		}
		else{
			this.motorPower = new Complex(0.0);
			this.loadPQ = this.motorPower;
		}
		
		//TODO update the attributes
		
		return true;
	}
	
	@Override
	public Hashtable<String, Object> getStates(Object ref) {
		this.states.put(this.OUT_SYMBOL_P, this.motorPower.getReal());
		this.states.put(this.OUT_SYMBOL_Q, this.motorPower.getImaginary());
		this.states.put(this.OUT_SYMBOL_V, this.getDStabBus().getVoltageMag());
		this.states.put(this.OUT_SYMBOL_I, this.iMotor.abs());
		this.states.put(this.OUT_SYMBOL_SLIP,this.slip);
		this.states.put(this.OUT_SYMBOL_TE, this.te);
		this.states.put(this.OUT_SYMBOL_TM, this.tm);
		this.states.put(this.OUT_SYMBOL_FUV,this.Fuv);
		
		return this.states;
	}
	
	
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated NOT
	 */
	public void calMultiFactors() {
		// multiplying factors to transfer from machine base to system base.
		double busBaseV = this.getDStabBus().getBaseVoltage(); 
		double sysMVABase = this.getDStabBus().getNetwork().getBaseMva();
		zMultiFactor=getRatedVoltage()*getRatedVoltage()/busBaseV/busBaseV/getMvaBase()*sysMVABase;
		iMultiFactor=getMvaBase()/sysMVABase*busBaseV/getRatedVoltage();
		vMultiFactor=busBaseV/getRatedVoltage();
	}
	
	
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated NOT
	 */
	@Override
	public boolean changeLoad(double percentageFactor){
		
		if (percentageFactor < -1.0){
			IpssLogger.getLogger().severe(" percentageFactor < -1.0, this change will not be applied");
			return false;
		}
		if (this.accumulatedLoadChangeFactor <= -1.0 &&  percentageFactor < 0.0)
			IpssLogger.getLogger().severe( "this.accumulatedLoadChangeFactor<=-1.0 and percentageFactor < 0.0, this change will not be applied");
		
		this.accumulatedLoadChangeFactor = this.accumulatedLoadChangeFactor + percentageFactor;
		
		if (this.accumulatedLoadChangeFactor < -1.0){
			IpssLogger.getLogger().severe( "the accumulatedLoadChangeFactor is less than -1.0 after this change, so the accumulatedLoadChangeFactor is reset to -1.0");
			this.accumulatedLoadChangeFactor = -1.0;
		}
		
		this.Fonline = 1.0 + this.accumulatedLoadChangeFactor;
		
		//also consider the load internal protection effects
		this.Fonline = this.Fonline*this.Fuv;
		
		IpssLogger.getLogger().info("this.accumulatedLoadChangeFactor = "+ this.accumulatedLoadChangeFactor +", Fonline = "+this.Fonline);
		
		return true;
	}

	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (ra: ");
		result.append(ra);
		result.append(", xl: ");
		result.append(xl);
		result.append(", xm: ");
		result.append(xm);
		result.append(", rr1: ");
		result.append(rr1);
		result.append(", xr1: ");
		result.append(xr1);
		result.append(", rr2: ");
		result.append(rr2);
		result.append(", xr2: ");
		result.append(xr2);
		result.append(", h: ");
		result.append(h);
		result.append(", d: ");
		result.append(d);
		result.append(", xs: ");
		result.append(xs);
		result.append(", xp: ");
		result.append(xp);
		result.append(", xpp: ");
		result.append(xpp);
		result.append(", tp0: ");
		result.append(tp0);
		result.append(", tpp0: ");
		result.append(tpp0);
		result.append(", ratedVoltage: ");
		result.append(ratedVoltage);
		result.append(", slip: ");
		result.append(slip);
		result.append(", w: ");
		result.append(w);
		result.append(", motorLoadP: ");
		result.append(motorLoadP);
		result.append(", motorLoadQ: ");
		result.append(motorLoadQ);
		result.append(", te: ");
		result.append(te);
		result.append(", tm: ");
		result.append(tm);
		result.append(", tm0: ");
		result.append(tm0);
		result.append(", a: ");
		result.append(a);
		result.append(", b: ");
		result.append(b);
		result.append(", c: ");
		result.append(c);
		result.append(", curd: ");
		result.append(curd);
		result.append(", curq: ");
		result.append(curq);
		result.append(", epd: ");
		result.append(epd);
		result.append(", epq: ");
		result.append(epq);
		result.append(", eppd: ");
		result.append(eppd);
		result.append(", eppq: ");
		result.append(eppq);
		result.append(", motorTotalLoad: ");
		result.append(motorTotalLoad);
		result.append(", motorPower: ");
		result.append(motorPower);
		result.append(", ep: ");
		result.append(ep);
		result.append(", epp: ");
		result.append(epp);
		result.append(", zpMotorBase: ");
		result.append(zpMotorBase);
		result.append(", zppMotorBase: ");
		result.append(zppMotorBase);
		result.append(", equivYSysBase: ");
		result.append(equivYSysBase);
		result.append(", nortonCurrInj: ");
		result.append(nortonCurrInj);
		result.append(", iMotor: ");
		result.append(iMotor);
		result.append(", zMultiFactor: ");
		result.append(zMultiFactor);
		result.append(", iMultiFactor: ");
		result.append(iMultiFactor);
		result.append(", vMultiFactor: ");
		result.append(vMultiFactor);
		result.append(", acc: ");
		result.append(acc);
		result.append(", subStepN: ");
		result.append(subStepN);
		result.append(", wdelt: ");
		result.append(wdelt);
		result.append(", twoAxisModel: ");
		result.append(twoAxisModel);
		result.append(')');
		return result.toString();
	}

	@Override
	public void setLoadFactor(double loadingFactor) {
		this.loadFactor = loadingFactor;
		
	}

	@Override
	public double getLoadFactor() {
		
		return this.loadFactor ;
	}

	@Override
	public List<MotorProtectionControl> getProtectionControlList() {
		if(this.motorProtectionList==null)
			this.motorProtectionList = new ArrayList<>();
		return this.motorProtectionList;
	}
	
	@Override public String getExtendedDeviceId(){
		if(this.extendedDeviceId == null || this.extendedDeviceId.equals("")) // extended Id;
			extendedDeviceId= "IndMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		return this.extendedDeviceId;
	}

    



} //InductionMotorImpl
