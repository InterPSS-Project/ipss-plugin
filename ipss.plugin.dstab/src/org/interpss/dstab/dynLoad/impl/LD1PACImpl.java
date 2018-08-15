/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad.impl;

import java.util.Hashtable;

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
import org.interpss.dstab.dynLoad.LD1PAC;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;

/**
 * An implementation of the model object '<em><b>LD1PAC</b></em>'.
 */
public class LD1PACImpl extends DynLoadModelImpl implements LD1PAC {
	/**
	 * The default value of the '{@link #getStage() <em>Stage</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getStage()
	 * @generated
	 * @ordered
	 */
	protected static final int STAGE_EDEFAULT = 0;

	/**
	 * The cached value of the '{@link #getStage() <em>Stage</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getStage()
	 * @generated
	 * @ordered
	 */
	protected int stage = STAGE_EDEFAULT;

	/**
	 * The default value of the '{@link #getP() <em>P</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP()
	 * @generated
	 * @ordered
	 */
	protected static final double P_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getP() <em>P</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP()
	 * @generated
	 * @ordered
	 */
	protected double p = P_EDEFAULT;

	/**
	 * The default value of the '{@link #getQ() <em>Q</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ()
	 * @generated
	 * @ordered
	 */
	protected static final double Q_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQ() <em>Q</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ()
	 * @generated
	 * @ordered
	 */
	protected double q = Q_EDEFAULT;

	/**
	 * The default value of the '{@link #getP0() <em>P0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP0()
	 * @generated
	 * @ordered
	 */
	protected static final double P0_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getP0() <em>P0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getP0()
	 * @generated
	 * @ordered
	 */
	protected double p0 = P0_EDEFAULT;

	/**
	 * The default value of the '{@link #getQ0() <em>Q0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ0()
	 * @generated
	 * @ordered
	 */
	protected static final double Q0_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQ0() <em>Q0</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQ0()
	 * @generated
	 * @ordered
	 */
	protected double q0 = Q0_EDEFAULT;

	/**
	 * The default value of the '{@link #getPac() <em>Pac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPac()
	 * @generated
	 * @ordered
	 */
	protected static final double PAC_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getPac() <em>Pac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPac()
	 * @generated
	 * @ordered
	 */
	protected double pac = PAC_EDEFAULT;

	/**
	 * The default value of the '{@link #getQac() <em>Qac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQac()
	 * @generated
	 * @ordered
	 */
	protected static final double QAC_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQac() <em>Qac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQac()
	 * @generated
	 * @ordered
	 */
	protected double qac = QAC_EDEFAULT;

	/**
	 * The default value of the '{@link #getPowerFactor() <em>Power Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPowerFactor()
	 * @generated
	 * @ordered
	 */
	protected static final double POWER_FACTOR_EDEFAULT = 0.97;

	/**
	 * The cached value of the '{@link #getPowerFactor() <em>Power Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPowerFactor()
	 * @generated
	 * @ordered
	 */
	protected double powerFactor = POWER_FACTOR_EDEFAULT;

	/**
	 * The default value of the '{@link #getVstall() <em>Vstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVstall()
	 * @generated
	 * @ordered
	 */
	protected static final double VSTALL_EDEFAULT = 0.6;

	/**
	 * The cached value of the '{@link #getVstall() <em>Vstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVstall()
	 * @generated
	 * @ordered
	 */
	protected double vstall = VSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getRstall() <em>Rstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRstall()
	 * @generated
	 * @ordered
	 */
	protected static final double RSTALL_EDEFAULT = 0.124;

	/**
	 * The cached value of the '{@link #getRstall() <em>Rstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRstall()
	 * @generated
	 * @ordered
	 */
	protected double rstall = RSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getXstall() <em>Xstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXstall()
	 * @generated
	 * @ordered
	 */
	protected static final double XSTALL_EDEFAULT = 0.114;

	/**
	 * The cached value of the '{@link #getXstall() <em>Xstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXstall()
	 * @generated
	 * @ordered
	 */
	protected double xstall = XSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getTstall() <em>Tstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTstall()
	 * @generated
	 * @ordered
	 */
	protected static final double TSTALL_EDEFAULT = 0.033;

	/**
	 * The cached value of the '{@link #getTstall() <em>Tstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTstall()
	 * @generated
	 * @ordered
	 */
	protected double tstall = TSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getLFadj() <em>LFadj</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLFadj()
	 * @generated
	 * @ordered
	 */
	protected static final double LFADJ_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getLFadj() <em>LFadj</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLFadj()
	 * @generated
	 * @ordered
	 */
	protected double lFadj = LFADJ_EDEFAULT;

	/**
	 * The default value of the '{@link #getKp1() <em>Kp1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp1()
	 * @generated
	 * @ordered
	 */
	protected static final double KP1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getKp1() <em>Kp1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp1()
	 * @generated
	 * @ordered
	 */
	protected double kp1 = KP1_EDEFAULT;

	/**
	 * The default value of the '{@link #getNp1() <em>Np1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp1()
	 * @generated
	 * @ordered
	 */
	protected static final double NP1_EDEFAULT = 1.0;

	/**
	 * The cached value of the '{@link #getNp1() <em>Np1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp1()
	 * @generated
	 * @ordered
	 */
	protected double np1 = NP1_EDEFAULT;

	/**
	 * The default value of the '{@link #getKq1() <em>Kq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq1()
	 * @generated
	 * @ordered
	 */
	protected static final double KQ1_EDEFAULT = 6.0;

	/**
	 * The cached value of the '{@link #getKq1() <em>Kq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq1()
	 * @generated
	 * @ordered
	 */
	protected double kq1 = KQ1_EDEFAULT;

	/**
	 * The default value of the '{@link #getNq1() <em>Nq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq1()
	 * @generated
	 * @ordered
	 */
	protected static final double NQ1_EDEFAULT = 2.0;

	/**
	 * The cached value of the '{@link #getNq1() <em>Nq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq1()
	 * @generated
	 * @ordered
	 */
	protected double nq1 = NQ1_EDEFAULT;

	/**
	 * The default value of the '{@link #getKp2() <em>Kp2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp2()
	 * @generated
	 * @ordered
	 */
	protected static final double KP2_EDEFAULT = 12.0;

	/**
	 * The cached value of the '{@link #getKp2() <em>Kp2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp2()
	 * @generated
	 * @ordered
	 */
	protected double kp2 = KP2_EDEFAULT;

	/**
	 * The default value of the '{@link #getNp2() <em>Np2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp2()
	 * @generated
	 * @ordered
	 */
	protected static final double NP2_EDEFAULT = 3.2;

	/**
	 * The cached value of the '{@link #getNp2() <em>Np2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp2()
	 * @generated
	 * @ordered
	 */
	protected double np2 = NP2_EDEFAULT;

	/**
	 * The default value of the '{@link #getKq2() <em>Kq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq2()
	 * @generated
	 * @ordered
	 */
	protected static final double KQ2_EDEFAULT = 11.0;

	/**
	 * The cached value of the '{@link #getKq2() <em>Kq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq2()
	 * @generated
	 * @ordered
	 */
	protected double kq2 = KQ2_EDEFAULT;

	/**
	 * The default value of the '{@link #getNq2() <em>Nq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq2()
	 * @generated
	 * @ordered
	 */
	protected static final double NQ2_EDEFAULT = 2.5;

	/**
	 * The cached value of the '{@link #getNq2() <em>Nq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq2()
	 * @generated
	 * @ordered
	 */
	protected double nq2 = NQ2_EDEFAULT;

	/**
	 * The default value of the '{@link #getVbrk() <em>Vbrk</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVbrk()
	 * @generated
	 * @ordered
	 */
	protected static final double VBRK_EDEFAULT = 0.86;

	/**
	 * The cached value of the '{@link #getVbrk() <em>Vbrk</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVbrk()
	 * @generated
	 * @ordered
	 */
	protected double vbrk = VBRK_EDEFAULT;

	/**
	 * The default value of the '{@link #getFrst() <em>Frst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFrst()
	 * @generated
	 * @ordered
	 */
	protected static final double FRST_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getFrst() <em>Frst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFrst()
	 * @generated
	 * @ordered
	 */
	protected double frst = FRST_EDEFAULT;

	/**
	 * The default value of the '{@link #getVrst() <em>Vrst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVrst()
	 * @generated
	 * @ordered
	 */
	protected static final double VRST_EDEFAULT = 0.9;

	/**
	 * The cached value of the '{@link #getVrst() <em>Vrst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVrst()
	 * @generated
	 * @ordered
	 */
	protected double vrst = VRST_EDEFAULT;

	/**
	 * The default value of the '{@link #getTrst() <em>Trst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTrst()
	 * @generated
	 * @ordered
	 */
	protected static final double TRST_EDEFAULT = 0.4;

	/**
	 * The cached value of the '{@link #getTrst() <em>Trst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTrst()
	 * @generated
	 * @ordered
	 */
	protected double trst = TRST_EDEFAULT;

	/**
	 * The default value of the '{@link #getCmpKpf() <em>Cmp Kpf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKpf()
	 * @generated
	 * @ordered
	 */
	protected static final double CMP_KPF_EDEFAULT = 1.0;

	/**
	 * The cached value of the '{@link #getCmpKpf() <em>Cmp Kpf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKpf()
	 * @generated
	 * @ordered
	 */
	protected double cmpKpf = CMP_KPF_EDEFAULT;

	/**
	 * The default value of the '{@link #getCmpKqf() <em>Cmp Kqf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKqf()
	 * @generated
	 * @ordered
	 */
	protected static final double CMP_KQF_EDEFAULT = -3.3;

	/**
	 * The cached value of the '{@link #getCmpKqf() <em>Cmp Kqf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKqf()
	 * @generated
	 * @ordered
	 */
	protected double cmpKqf = CMP_KQF_EDEFAULT;

	/**
	 * The default value of the '{@link #getFuvr() <em>Fuvr</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFuvr()
	 * @generated
	 * @ordered
	 */
	protected static final double FUVR_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getFuvr() <em>Fuvr</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFuvr()
	 * @generated
	 * @ordered
	 */
	protected double fuvr = FUVR_EDEFAULT;

	/**
	 * The default value of the '{@link #getVtr1() <em>Vtr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr1()
	 * @generated
	 * @ordered
	 */
	protected static final double VTR1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getVtr1() <em>Vtr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr1()
	 * @generated
	 * @ordered
	 */
	protected double vtr1 = VTR1_EDEFAULT;

	/**
	 * The default value of the '{@link #getTtr1() <em>Ttr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr1()
	 * @generated
	 * @ordered
	 */
	protected static final double TTR1_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTtr1() <em>Ttr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr1()
	 * @generated
	 * @ordered
	 */
	protected double ttr1 = TTR1_EDEFAULT;

	/**
	 * The default value of the '{@link #getVtr2() <em>Vtr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr2()
	 * @generated
	 * @ordered
	 */
	protected static final double VTR2_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getVtr2() <em>Vtr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr2()
	 * @generated
	 * @ordered
	 */
	protected double vtr2 = VTR2_EDEFAULT;

	/**
	 * The default value of the '{@link #getTtr2() <em>Ttr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr2()
	 * @generated
	 * @ordered
	 */
	protected static final double TTR2_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTtr2() <em>Ttr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr2()
	 * @generated
	 * @ordered
	 */
	protected double ttr2 = TTR2_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc1off() <em>Vc1off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1off()
	 * @generated
	 * @ordered
	 */
	protected static final double VC1OFF_EDEFAULT = 0.5;

	/**
	 * The cached value of the '{@link #getVc1off() <em>Vc1off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1off()
	 * @generated
	 * @ordered
	 */
	protected double vc1off = VC1OFF_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc2off() <em>Vc2off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2off()
	 * @generated
	 * @ordered
	 */
	protected static final double VC2OFF_EDEFAULT = 0.4;

	/**
	 * The cached value of the '{@link #getVc2off() <em>Vc2off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2off()
	 * @generated
	 * @ordered
	 */
	protected double vc2off = VC2OFF_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc1on() <em>Vc1on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1on()
	 * @generated
	 * @ordered
	 */
	protected static final double VC1ON_EDEFAULT = 0.6;

	/**
	 * The cached value of the '{@link #getVc1on() <em>Vc1on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1on()
	 * @generated
	 * @ordered
	 */
	protected double vc1on = VC1ON_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc2on() <em>Vc2on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2on()
	 * @generated
	 * @ordered
	 */
	protected static final double VC2ON_EDEFAULT = 0.5;

	/**
	 * The cached value of the '{@link #getVc2on() <em>Vc2on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2on()
	 * @generated
	 * @ordered
	 */
	protected double vc2on = VC2ON_EDEFAULT;

	/**
	 * The default value of the '{@link #getTth() <em>Tth</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTth()
	 * @generated
	 * @ordered
	 */
	protected static final double TTH_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTth() <em>Tth</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTth()
	 * @generated
	 * @ordered
	 */
	protected double tth = TTH_EDEFAULT;

	/**
	 * The default value of the '{@link #getTh1t() <em>Th1t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh1t()
	 * @generated
	 * @ordered
	 */
	protected static final double TH1T_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTh1t() <em>Th1t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh1t()
	 * @generated
	 * @ordered
	 */
	protected double th1t = TH1T_EDEFAULT;

	/**
	 * The default value of the '{@link #getTh2t() <em>Th2t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh2t()
	 * @generated
	 * @ordered
	 */
	protected static final double TH2T_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTh2t() <em>Th2t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh2t()
	 * @generated
	 * @ordered
	 */
	protected double th2t = TH2T_EDEFAULT;

	/**
	 * The default value of the '{@link #getUVRelayTimer1() <em>UV Relay Timer1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer1()
	 * @generated
	 * @ordered
	 */
	protected static final double UV_RELAY_TIMER1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getUVRelayTimer1() <em>UV Relay Timer1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer1()
	 * @generated
	 * @ordered
	 */
	protected double uVRelayTimer1 = UV_RELAY_TIMER1_EDEFAULT;

	/**
	 * The default value of the '{@link #getUVRelayTimer2() <em>UV Relay Timer2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer2()
	 * @generated
	 * @ordered
	 */
	protected static final double UV_RELAY_TIMER2_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getUVRelayTimer2() <em>UV Relay Timer2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer2()
	 * @generated
	 * @ordered
	 */
	protected double uVRelayTimer2 = UV_RELAY_TIMER2_EDEFAULT;

	/**
	 * The default value of the '{@link #getAcStallTimer() <em>Ac Stall Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcStallTimer()
	 * @generated
	 * @ordered
	 */
	protected static final double AC_STALL_TIMER_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getAcStallTimer() <em>Ac Stall Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcStallTimer()
	 * @generated
	 * @ordered
	 */
	protected double acStallTimer = AC_STALL_TIMER_EDEFAULT;

	/**
	 * The default value of the '{@link #getAcRestartTimer() <em>Ac Restart Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcRestartTimer()
	 * @generated
	 * @ordered
	 */
	protected static final double AC_RESTART_TIMER_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getAcRestartTimer() <em>Ac Restart Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcRestartTimer()
	 * @generated
	 * @ordered
	 */
	protected double acRestartTimer = AC_RESTART_TIMER_EDEFAULT;
	
	/**
	 * The default value of the '{@link #getTv() <em>Tv</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTv()
	 * @generated
	 * @ordered
	 */
	protected static final double TV_EDEFAULT = 0.02;

	/**
	 * The cached value of the '{@link #getTv() <em>Tv</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTv()
	 * @generated
	 * @ordered
	 */
	protected double tv = TV_EDEFAULT;

	private Hashtable<String, Object> states = null;
	private static final String OUT_SYMBOL_P ="ACMotorP";
	private static final String OUT_SYMBOL_Q ="ACMotorQ";
	private static final String OUT_SYMBOL_VT ="ACMotorVt";
	private static final String OUT_SYMBOL_STATE ="ACMotorState";
	private String extended_device_Id = "";

	public LD1PACImpl() {
		super();
		this.states = new Hashtable<>();
	}
	
	public LD1PACImpl(BaseDStabBus<?,?> dstabBus, String id) {
		this();
		this.id = id;
		
		this.setDStabBus(dstabBus);
		dstabBus.getDynLoadModelList().add(this);
	}

	

	public int getStage() {
		return stage;
	}

	public void setStage(int newStage) {
		stage = newStage;
	}

	public double getP() {
		return p;
	}

	public void setP(double newP) {
		p = newP;
	}

	public double getQ() {
		return q;
	}

	public void setQ(double newQ) {
		q = newQ;
	}

	public double getP0() {
		return p0;
	}

	public void setP0(double newP0) {
		p0 = newP0;
	}

	public double getQ0() {
		return q0;
	}

	public void setQ0(double newQ0) {
		q0 = newQ0;
	}

	public double getPac() {
		return pac;
	}

	public void setPac(double newPac) {
		pac = newPac;
	}

	public double getQac() {
		return qac;
	}

	public void setQac(double newQac) {
		qac = newQac;
	}

	public double getPowerFactor() {
		return powerFactor;
	}

	public void setPowerFactor(double newPowerFactor) {
		powerFactor = newPowerFactor;
	}

	public double getVstall() {
		return vstall;
	}

	public void setVstall(double newVstall) {
		vstall = newVstall;
	}

	public double getRstall() {
		return rstall;
	}

	public void setRstall(double newRstall) {
		rstall = newRstall;
	}

	public double getXstall() {
		return xstall;
	}

	public void setXstall(double newXstall) {
		xstall = newXstall;
	}

	public double getTstall() {
		return tstall;
	}

	public void setTstall(double newTstall) {
		tstall = newTstall;
	}

	public double getLFadj() {
		return lFadj;
	}

	public void setLFadj(double newLFadj) {
		lFadj = newLFadj;
	}

	public double getKp1() {
		return kp1;
	}

	public void setKp1(double newKp1) {
		kp1 = newKp1;
	}

	public double getNp1() {
		return np1;
	}

	public void setNp1(double newNp1) {
		np1 = newNp1;
	}

	public double getKq1() {
		return kq1;
	}

	public void setKq1(double newKq1) {
		kq1 = newKq1;
	}

	public double getNq1() {
		return nq1;
	}

	public void setNq1(double newNq1) {
		nq1 = newNq1;
	}

	public double getKp2() {
		return kp2;
	}

	public void setKp2(double newKp2) {
		kp2 = newKp2;
	}

	public double getNp2() {
		return np2;
	}

	public void setNp2(double newNp2) {
		np2 = newNp2;
	}

	public double getKq2() {
		return kq2;
	}

	public void setKq2(double newKq2) {
		kq2 = newKq2;
	}

	public double getNq2() {
		return nq2;
	}

	public void setNq2(double newNq2) {
		nq2 = newNq2;
	}

	public double getVbrk() {
		return vbrk;
	}

	public void setVbrk(double newVbrk) {
		vbrk = newVbrk;
	}

	public double getFrst() {
		return frst;
	}

	public void setFrst(double newFrst) {
		frst = newFrst;
	}

	public double getVrst() {
		return vrst;
	}

	public void setVrst(double newVrst) {
		vrst = newVrst;
	}

	public double getTrst() {
		return trst;
	}

	public void setTrst(double newTrst) {
		trst = newTrst;
	}

	public double getCmpKpf() {
		return cmpKpf;
	}

	public void setCmpKpf(double newCmpKpf) {
		cmpKpf = newCmpKpf;
	}

	public double getCmpKqf() {
		return cmpKqf;
	}

	public void setCmpKqf(double newCmpKqf) {
		cmpKqf = newCmpKqf;
	}

	public double getFuvr() {
		return fuvr;
	}

	public void setFuvr(double newFuvr) {
		fuvr = newFuvr;
	}

	public double getVtr1() {
		return vtr1;
	}

	public void setVtr1(double newVtr1) {
		vtr1 = newVtr1;
	}

	public double getTtr1() {
		return ttr1;
	}

	public void setTtr1(double newTtr1) {
		ttr1 = newTtr1;
	}

	public double getVtr2() {
		return vtr2;
	}

	public void setVtr2(double newVtr2) {
		vtr2 = newVtr2;
	}

	public double getTtr2() {
		return ttr2;
	}

	public void setTtr2(double newTtr2) {
		ttr2 = newTtr2;
	}

	public double getVc1off() {
		return vc1off;
	}

	public void setVc1off(double newVc1off) {
		vc1off = newVc1off;
	}

	public double getVc2off() {
		return vc2off;
	}

	public void setVc2off(double newVc2off) {
		vc2off = newVc2off;
	}

	public double getVc1on() {
		return vc1on;
	}

	public void setVc1on(double newVc1on) {
		vc1on = newVc1on;
	}

	public double getVc2on() {
		return vc2on;
	}

	public void setVc2on(double newVc2on) {
		vc2on = newVc2on;
	}

	public double getTth() {
		return tth;
	}

	public void setTth(double newTth) {
		tth = newTth;
	}

	public double getTh1t() {
		return th1t;
	}

	public void setTh1t(double newTh1t) {
		th1t = newTh1t;
	}

	public double getTh2t() {
		return th2t;
	}

	public void setTh2t(double newTh2t) {
		th2t = newTh2t;
	}

	public double getUVRelayTimer1() {
		return uVRelayTimer1;
	}

	public void setUVRelayTimer1(double newUVRelayTimer1) {
		uVRelayTimer1 = newUVRelayTimer1;
	}

	public double getUVRelayTimer2() {
		return uVRelayTimer2;
	}

	public void setUVRelayTimer2(double newUVRelayTimer2) {
		uVRelayTimer2 = newUVRelayTimer2;
	}

	public double getAcStallTimer() {
		return acStallTimer;
	}

	public void setAcStallTimer(double newAcStallTimer) {
		acStallTimer = newAcStallTimer;
	}

	public double getAcRestartTimer() {
		return acRestartTimer;
	}

	public void setAcRestartTimer(double newAcRestartTimer) {
		acRestartTimer = newAcRestartTimer;
	}

	public double getTv() {
		return tv;
	}

	public void setTv(double newTv) {
		tv = newTv;
	}

	@Override
	public boolean initStates() {
       boolean flag = true;
		
		//TODO the initLoad is the toal load at the bus ,include constant Z and I load
		//In the future, this may need to be update to consider the constant P load only
        Complex busTotalLoad = this.getDStabBus().getInitLoad();
		pac = busTotalLoad.getReal()*this.loadPercent/100.0d;
		qac = pac*Math.tan(Math.acos(this.powerFactor));
		
		
		// pac and qac is the initial power
		this.setInitLoadPQ(new Complex(pac,qac));
		this.setLoadPQ(new Complex(pac,qac));
		
		// if mva is not defined and loading factor is available
		if(this.getMvaBase()==0.0){
			if(this.loadFactor >0 && this.loadFactor<=1.0)
                    IpssLogger.getLogger().fine("AC motor MVABase will be calculated based on load factor");
			else 
				this.loadFactor = 1.0;
			
			this.setMvaBase(this.pac*this.getDStabBus().getNetwork().getBaseMva()/this.loadFactor);
		}
		
		
		//Check whether a compensation is needed. If yes, calculate the compensation shuntY

		// if bus.loadQ < ld1pac.q, then compShuntB = ld1pac.q-bus.loadQ
		if(qac >busTotalLoad.getImaginary()){
			 double v = this.getDStabBus().getVoltageMag();
			 double b = (qac - busTotalLoad.getImaginary())/v/v;
			 this.compensateShuntY = new Complex(0,b);
		}
		
		
		// update the Vstall and Vbrk if necessary
		//Vstall(adj) = Vstall*(1+LFadj*(CompLF-1))
		//Vbrk(adj) = Vbrk*(1+LFadj*(CompLF-1))
		
	   // Calcuate the P0 and Q0 at stage 0
		p0 = 1 - kp1*Math.pow((1-vbrk),np1);
		q0 = Math.sqrt(1 - this.powerFactor*this.powerFactor)/this.powerFactor - 
				this.kq1*Math.pow((1.0-vbrk),nq1);
		
		Complex loadPQFactor = calcLoadCharacterFactor();
		p = pac/loadPQFactor.getReal();
		q = qac/loadPQFactor.getImaginary();
		
		extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, extended_device_Id);
		
		return flag;
	}
	
	/**
	 * The thermal protection heating increase is modeled as 
	 * differential equation, thus it must be represented with the nextStep();
	 * 
	 * The stall timer as well as the recovery timer are also counted and updated in this method
	 */
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method) {
		boolean flag = true;
		
		// stage update 
		if(acStallTimer>=tstall){
			stage = 1;
		}
		
		// switch to restart stage
		if (stage == 1  && frst>0.0 && acRestartTimer >= trst)
			stage = 2;
		
		// check whether the ac motor is stalled or not
		double v = this.getDStabBus().getVoltageMag();
		
		if(v<=this.vstall){
			acStallTimer += dt;
		}
		else{
			acStallTimer = 0.0;
		}
		

		// update restart counter
		if(stage == 1 && v>this.vrst && frst>0.0){
			acRestartTimer +=dt; 
		}
		else
			acRestartTimer = 0.0;
		
	
		
		// thermal overload protection
		/*
		 * When the motor is stalled, the ��temperature�� of the motor is computed by
			integrating I^2 R through the thermal time constant Tth.  If the temperature reaches Th2t, all of the load is
			tripped.   If the temperature is between  Th1t and  Th2t, a linear fraction of the load is tripped.   The
			��termperatures�� of the ��A�� and ��B�� portions of the load are computed separately.  The fractions of the ��A��
			and ��B�� parts of the load that have not been tripped by the thermal protection is output as �� fthA�� and
			��fthB��, respectively.	
		 */
			
			
		// contractor
		/*
		 *  Contactor �C If the voltage drops to below Vc2off, all of the load is tripped; if the voltage is between
			Vc1off and Vc2off, a linear fraction of the load is tripped.  If the voltage later recovers to above Vc2on, all
			of the motor is reconnected; if the voltage recovers to between Vc2on and Vc1on, a linear fraction of the
			load is reconnected.  The fraction of the load that has not been tripped by the contactor is output as ��fcon��.
		 */
		
		
		//TODO the compensation current is only update once in order to solve the convergence issue.
		
		calculateCompensateCurInj();
		
		return flag;
	}
	
	private void calculateCompensateCurInj() {
        this.nortonCurInj = new Complex(0.0d,0.0d);
		
			// when loadPQFactor = 0, it means the AC is stalled, thus no compensation current
			if(stage !=1) {

				Complex loadPQFactor = calcLoadCharacterFactor();
				// p+jq is pu based on system
				Complex pq = new Complex(p*loadPQFactor.getReal(),q*loadPQFactor.getImaginary());
				
				//System.out.println("computed AC power ="+pq.toString());
				
				//TODO replace the pos-seq voltage with phase voltage
				Complex v = this.getDStabBus().getVoltage();
				double vmag = v.abs();
				
				// power needs to be compensated through current injection
				Complex compPower = pq.subtract(this.equivY.multiply(vmag*vmag).conjugate());
				
				// I = -conj( (p+j*q - conj(v^2*this.equivY))/v)
				
				// situation where the load bus voltage is very low has been considered in calcLoadCharacterFactor();
//				if(vmag<0.4)
//					 this.currInj = new Complex(0.0);
//				else
				   this.nortonCurInj= compPower.divide(v).conjugate().multiply(-1.0d);
			}
		
	}
	
	@Override
	public Complex getPosSeqEquivY() {
		Complex zstall = new Complex(this.rstall,this.xstall);
		Complex y = new Complex(1.0,0).divide(zstall);
		this.equivY = y.multiply(getMvaBase()/this.getDStabBus().getNetwork().getBaseMva());
		
		return this.equivY;
	}
	
	@Override
	public Complex getNortonCurInj() {
		if(this.nortonCurInj == null) 
			calculateCompensateCurInj();
		//System.out.println("AC motor -"+this.getId()+"@"+this.getDStabBus().getId()+" dyn current injection: "+this.compensateCurInj);
		
		return this.nortonCurInj;
	}
	
	@Override
	public Object getOutputObject() {
	     return this.getNortonCurInj();
	}
	
	//The following calculation is  based on the AC motor model specification in PSS/E doc
	private Complex calcLoadCharacterFactor(){
		
		double v = this.getDStabBus().getVoltage().abs();
		// exponential factor

		double pfactor = 0.0, qfactor = 0.0, pfactorStall = 0.0, qfactorStall = 0.0;
		
		if(v>this.vbrk  & stage == 0){
			pfactor  = p0 +kp1*Math.pow((v-vbrk),np1);
			qfactor  = q0 +kq1*Math.pow((v-vbrk),nq1);
	    }
		//TODO change from "v<Vbrk & v> Vstall & stage == 0 "to  "v<Vbrk & v> Vc1off & stage ==0"
		else if( v<vbrk & v> vstall & stage == 0){ //
			pfactor = p0 +kp2*Math.pow((vbrk-v),np2);
			qfactor = q0 +kq2*Math.pow((vbrk-v),nq2);
			pfactorStall = pfactor;
			qfactorStall = qfactor;
		}
		// when v<= Vstall and stage = 0, the same pq factor, which is calculated in the last step
		// before v<Vstall, will be used
		
		if(v<=vstall & stage == 0){
			pfactor = pfactorStall*(v/vstall)*(v/vstall);
			qfactor = qfactorStall*(v/vstall)*(v/vstall);
		}
		
		// consider the frequency dependence
		if(pfactor !=0.0 ||qfactor!=0){
			double dFreq = getDStabBus().getFreq()-1.0;
			pfactor = pfactor*(1+cmpKpf*dFreq);
			qfactor = qfactor*(1+cmpKqf*dFreq/Math.sqrt(1-powerFactor*powerFactor));
		}
		
       //System.out.println("Voltage, PQFactor :"+v+","+ pfactor+","+qfactor);
		
		return new Complex(pfactor,qfactor);
	}
    
	@Override
	public Complex getLoadPQ() {
		Complex loadPQFactor = calcLoadCharacterFactor();
		double v = this.getDStabBus().getVoltageMag();
		// when loadPQFactor = 0, it means the AC is stalled, thus no compensation current
		if(this.stage == 1) 
			  this.loadPQ =this.getPosSeqEquivY().multiply( v*v).conjugate();
		else
			this.loadPQ = new Complex(p*loadPQFactor.getReal(),q*loadPQFactor.getImaginary());
		
		return this.loadPQ;
		
	}
	
	
	@Override
	public Hashtable<String, Object> getStates(Object ref) {
		states.put(OUT_SYMBOL_P, this.getPac());
		states.put(OUT_SYMBOL_Q, this.getQac());
		states.put(OUT_SYMBOL_VT, this.getDStabBus().getVoltage().abs());
		states.put(OUT_SYMBOL_STATE, stage==1?0:1);
		return this.states;
	}

	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (stage: ");
		result.append(stage);
		result.append(", p: ");
		result.append(p);
		result.append(", q: ");
		result.append(q);
		result.append(", p0: ");
		result.append(p0);
		result.append(", q0: ");
		result.append(q0);
		result.append(", pac: ");
		result.append(pac);
		result.append(", qac: ");
		result.append(qac);
		result.append(", powerFactor: ");
		result.append(powerFactor);
		result.append(", vstall: ");
		result.append(vstall);
		result.append(", rstall: ");
		result.append(rstall);
		result.append(", xstall: ");
		result.append(xstall);
		result.append(", tstall: ");
		result.append(tstall);
		result.append(", lFadj: ");
		result.append(lFadj);
		result.append(", kp1: ");
		result.append(kp1);
		result.append(", np1: ");
		result.append(np1);
		result.append(", kq1: ");
		result.append(kq1);
		result.append(", nq1: ");
		result.append(nq1);
		result.append(", kp2: ");
		result.append(kp2);
		result.append(", np2: ");
		result.append(np2);
		result.append(", kq2: ");
		result.append(kq2);
		result.append(", nq2: ");
		result.append(nq2);
		result.append(", vbrk: ");
		result.append(vbrk);
		result.append(", frst: ");
		result.append(frst);
		result.append(", vrst: ");
		result.append(vrst);
		result.append(", trst: ");
		result.append(trst);
		result.append(", cmpKpf: ");
		result.append(cmpKpf);
		result.append(", cmpKqf: ");
		result.append(cmpKqf);
		result.append(", fuvr: ");
		result.append(fuvr);
		result.append(", vtr1: ");
		result.append(vtr1);
		result.append(", ttr1: ");
		result.append(ttr1);
		result.append(", vtr2: ");
		result.append(vtr2);
		result.append(", ttr2: ");
		result.append(ttr2);
		result.append(", vc1off: ");
		result.append(vc1off);
		result.append(", vc2off: ");
		result.append(vc2off);
		result.append(", vc1on: ");
		result.append(vc1on);
		result.append(", vc2on: ");
		result.append(vc2on);
		result.append(", tth: ");
		result.append(tth);
		result.append(", th1t: ");
		result.append(th1t);
		result.append(", th2t: ");
		result.append(th2t);
		result.append(", uVRelayTimer1: ");
		result.append(uVRelayTimer1);
		result.append(", uVRelayTimer2: ");
		result.append(uVRelayTimer2);
		result.append(", acStallTimer: ");
		result.append(acStallTimer);
		result.append(", acRestartTimer: ");
		result.append(acRestartTimer);
		result.append(", tv: ");
		result.append(tv);
		result.append(')');
		return result.toString();
	}
	
	@Override
	public boolean changeLoad(double factor) {
		if (factor < -1.0){
			IpssLogger.getLogger().severe(" percentageFactor < -1.0, this change will not be applied");
			return false;
		}
		if (this.accumulatedLoadChangeFactor <= -1.0 &&  factor < 0.0)
			IpssLogger.getLogger().severe( "this.accumulatedLoadChangeFactor<=-1.0 and percentageFactor < 0.0, this change will not be applied");
		
		this.accumulatedLoadChangeFactor = this.accumulatedLoadChangeFactor + factor;
		
		if (this.accumulatedLoadChangeFactor < -1.0){
			IpssLogger.getLogger().severe( "the accumulatedLoadChangeFactor is less than -1.0 after this change, so the accumulatedLoadChangeFactor is reset to -1.0");
			this.accumulatedLoadChangeFactor = -1.0;
		}
		IpssLogger.getLogger().info("accumulated Load Change Factor = "+ this.accumulatedLoadChangeFactor);
		
		return true;
	}
} //LD1PACImpl