/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad.impl;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.LD1PAC;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
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
	 * The default value of the '{@link #getLoadFactor() <em>Load Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLoadFactor()
	 * @generated
	 * @ordered
	 */
	protected static final double LOAD_FACTOR_EDEFAULT = 0.85;

	/**
	 * The cached value of the '{@link #getLoadFactor() <em>Load Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLoadFactor()
	 * @generated
	 * @ordered
	 */
	protected double loadFactor = LOAD_FACTOR_EDEFAULT;

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
	protected static final double UVTR1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getVtr1() <em>Vtr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr1()
	 * @generated
	 * @ordered
	 */
	protected double uvtr1 = UVTR1_EDEFAULT;

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
	protected static final double UVTR2_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getVtr2() <em>Vtr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr2()
	 * @generated
	 * @ordered
	 */
	protected double uvtr2 = UVTR2_EDEFAULT;

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

	protected double fcon  = 1.0;  // fraction not tripped by the contractor
	
	// the aggregate motor model is represnted as two types of motors, A - non-restartable, B - restartable after voltage recovery
	
	protected double fthA = 1.0;  // fraction not tripped by the motor A thermal protection
	
	protected double fthB = 1.0;  // fraction not tripped by the motor B thermal protection
	
	protected double tempA = 0.0; // Temperature of the motor A
	
	protected double tempB = 0.0; // Temperature of the motor B
	
	
	protected double complf = 1.0;
	
	private double Gstall = 0.0, Bstall = 0.0;
	private Complex YStall = null;
	
	private Complex equivYpq = null;
	
	private Complex PQmotor = null;
	
	private double vstallbrk = 0.0;
	private double 	pac_a = 0.0, qac_a = 0.0, pac_b = 0.0, qac_b = 0.0;
	private double kuv =1.0, kcon = 1.0, fcon_trip = 0.0;
	private boolean isContractorActioned = false;
	private double I_CONV_FACTOR_M2S = 0.0;
	private double vt_measured = 0.0;

	// thermal protection
	// the tripping characterisic is modeled as y = thermalEqnCoeff1*x +thermalEqnCoeff2 for x within {Th1t, Th2t}
	// here x is the internal temperature, y is the output for determining thermal tripping
	// when x>Th1t, starting trippinng, when x>Th2t, all will be tripped
	
	private double thermalEqnCoeff1 = -1.0/3;
	private double thermalEqnCoeff2 = 1.433;  // default value
	
	private int statusA =1, statusB = 1; // the operating status of the part A and B of the motor; 0 - stall, 1- running
	private double timestep = 0.0;
	
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

	

	@Override
	public boolean initStates() {
		boolean flag = true;
        this.stage = 1; // initialized to running state
        
		this.Gstall = this.rstall/(this.rstall*this.rstall + this.xstall*this.xstall);
		this.Bstall = -this.xstall/(this.rstall*this.rstall + this.xstall*this.xstall);
		
		this.YStall = new Complex(this.Gstall, this.Bstall);
		
		//TODO the initLoad is the total load at the bus ,include constant Z and I load
		//In the future, this may need to be update to consider the constant P load only
        Complex busTotalLoad = this.getDStabBus().getInitLoad();
        this.pac = busTotalLoad.getReal()*this.loadPercent/100.0d; // pu on system base
        this.qac = this.pac*Math.tan(Math.acos(this.powerFactor));
		
		
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
		
		this.I_CONV_FACTOR_M2S = this.getMvaBase()/this.getDStabBus().getNetwork().getBaseMva();
				
		//Check whether a compensation is needed. If yes, calculate the compensation shuntY

		// if bus.loadQ < ld1pac.q, then compShuntB = ld1pac.q-bus.loadQ
		double vt = this.getDStabBus().getVoltageMag();
		
		this.vt_measured = vt;
		
		if(this.qac >busTotalLoad.getImaginary()){
			
			 double b = (this.qac - busTotalLoad.getImaginary())/vt/vt;
			 this.compensateShuntY = new Complex(0,b);
		}
		
		
		Complex Sac = new Complex (pac, qac);
		double i_motor = Sac.abs()/vt;
		
		this.tempA = i_motor*i_motor*this.rstall;
		this.tempB = this.tempA;
		
		
		// update the Vstall and Vbrk if necessary
		this.vstall= this.vstall*(1+this.lFadj*(this.loadFactor-1));
		this.vbrk = this.vbrk*(1+this.lFadj*(this.loadFactor-1));
		
		// motor P and Q on motor mvabase
		double pac_mbase = this.pac*this.getDStabBus().getNetwork().getBaseMva()/this.getMvaBase();
		double qac_mbase = this.qac*this.getDStabBus().getNetwork().getBaseMva()/this.getMvaBase();
		
		// initialize the parts A and B of the motor
		this.pac_a = pac_mbase; 
		this.pac_b = pac_mbase;
		
		this.qac_a = qac_mbase; 
		this.qac_b = qac_mbase;
		
		
	   // Calculate the P0 and Q0 at stage 0
		this.p0 = pac_mbase - kp1*Math.pow((vt-vbrk),np1);
		this.q0 = qac_mbase - this.kq1*Math.pow((vt-vbrk),nq1);
		
		
		
		double pst = 0.0, pac_calc = 0.0;
		for (double v = 0.4; v<vbrk; v+=0.0001){
			pst = this.Gstall*v*v;
			pac_calc = this.p0 + kp1*Math.pow((v-vbrk),np1);
			
			if(pac_calc<pst){
				this.vstallbrk = v;
				break;
			}
			
		}
		
		if(this.vstallbrk>this.vstall){
			this.vstallbrk = this.vstall;
		}
		
		
		if(this.th1t >0 && this.th2t>this.th1t){
			this.thermalEqnCoeff1 = -1/(this.th2t - this.th1t);
			this.thermalEqnCoeff2 = this.th2t /(this.th2t - this.th1t);
		}
		
//		Complex loadPQFactor = calcLoadCharacterFactor();
//		
//		this.p = this.pac/loadPQFactor.getReal();
//		this.q = this.qac/loadPQFactor.getImaginary();
		
		this.PQmotor = new Complex(pac_mbase,qac_mbase);
		
		this.equivYpq = this.PQmotor.conjugate().divide(vt*vt);
		
		
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
		
		// check the protection actions and update the status of AC motor accordingly
		timestep = dt;
		
		// check whether the ac motor is stalled or not
		double vmag = this.getDStabBus().getVoltageMag();
		
		
	    // voltage measurements
		
		double dv_dt0 = (vmag-this.vt_measured)/this.tv;
		
		double vt_measured1 = this.vt_measured+dv_dt0*dt;
		
		double dv_dt1 = (vmag-vt_measured1)/this.tv;
		
		this.vt_measured = this.vt_measured+ (dv_dt0+dv_dt1)*0.5d*dt;
		
		// thermal overload protection
		/*
		 * When the motor is stalled, the temperature of the motor is computed by
			integrating I^2 R through the thermal time constant Tth.  If the temperature reaches Th2t, all of the load is
			tripped.   If the temperature is between  Th1t and  Th2t, a linear fraction of the load is tripped.   The termperature
			 of the A and B portions of the load are computed separately.  The fractions of the A
			and B parts of the load that have not been tripped by the thermal protection is output as fthA and fthB, respectively.	
		 */
		
		Complex vt = this.getDStabBus().getVoltage();
		
		Complex ImotorA_pu = new Complex(0.0);
		Complex ImotorB_pu = new Complex(0.0);
		
		if(this.statusA ==0)
			 ImotorA_pu = vt.multiply(this.YStall);
		else{
			if(vmag>this.vstallbrk)
				ImotorA_pu = new Complex(pac_a, qac_a).divide(vt).conjugate();
			else
				ImotorA_pu = vt.multiply(this.YStall);
		}
			
		
		if(this.statusB ==0)
			 ImotorB_pu = vt.multiply(this.YStall);
		else{
			if(vmag>this.vstallbrk)
			    ImotorB_pu = new Complex(pac_b, qac_b).divide(vt).conjugate();
			else
			    ImotorB_pu = vt.multiply(this.YStall);
		}
		
		double dThA_dt0 = (Math.pow(ImotorA_pu.abs(),2)*this.rstall - this.tempA)/this.tth;
		
		double dThB_dt0 = (Math.pow(ImotorB_pu.abs(),2)*this.rstall - this.tempB)/this.tth;
			
	    double tempA1 = this.tempA+ dThA_dt0*dt;
	    double tempB1 = this.tempB+ dThB_dt0*dt;
	    
		double dThA_dt1 = (Math.pow(ImotorA_pu.abs(),2)*this.rstall - tempA1)/this.tth;
		
		double dThB_dt1 = (Math.pow(ImotorB_pu.abs(),2)*this.rstall - tempB1)/this.tth;
		
		this.tempA = this.tempA + (dThA_dt0+dThA_dt1)*0.5d*dt;
		this.tempB = this.tempB + (dThB_dt0+dThB_dt1)*0.5d*dt;
		
		return flag;
	}
	
	/**
	 *  to check whether AC motor stalls, restarts and the actions of the protections
      %  this should be perform after the network solution step
	 */
	private boolean post_process_step(double dt){
		boolean flag = true;
		/*
		 % UV Relay
         % Two levels of undervoltage load shedding can be represented: If the voltage drops
         % below uvtr1 for ttr1 seconds, the fraction ?fuvtr? of the load is tripped; If the voltage drops below uvtr2
         % for ttr2 seconds, the fraction ?fuvr? of the load is tripped
		*/
		
		if(this.vt_measured <this.getUVtr1() && this.fuvr >0.0){
			this.uVRelayTimer1 = this.uVRelayTimer1 +dt;
		}
		else
			this.uVRelayTimer1 = 0.0;
		
		if(this.vt_measured <this.getUVtr2() && this.fuvr >0.0){
			this.uVRelayTimer2 = this.uVRelayTimer2 +dt;
		}
		else
			this.uVRelayTimer2 = 0.0;
		
		// trip the portion with under voltage relays
		if(this.uVRelayTimer1> this.ttr1){
			this.kuv = 1.0 - this.fuvr;
		}
		
		if(this.uVRelayTimer2> this.ttr2){
			this.kuv = 1.0 - this.fuvr;
		}
		
		/*
        % contractor
        %  Contactor -- If the voltage drops to below Vc2off, all of the load is tripped; if the voltage is between
        % Vc1off and Vc2off, a linear fraction of the load is tripped. If the voltage later recovers to above Vc2on, all
        % of the motor is reconnected; if the voltage recovers to between Vc2on and Vc1on, a linear fraction of the
        % load is reconnected.
        */
		
		if(this.vt_measured < this.vc2off){
			this.kcon  = 0.0;
			this.fcon_trip = 1.0;
		}
		else if(this.vt_measured>=this.vc2off && this.vt_measured <this.vc1off){
			this.kcon = (this.vt_measured - this.vc2off)/(this.vc1off- this.vc2off);
			this.fcon_trip = 1.0 - this.kcon;
			
		}
		
		
		if(this.vt_measured >=this.vc1on){
			this.kcon = 1.0;
		}
		else if(this.vt_measured < this.vc1on && this.vt_measured >= this.vc2on){
			double Frecv = (this.vt_measured - this.vc2on)/(this.vc1on- this.vc2on);
			this.kcon  = 1 - this.fcon_trip*(1-Frecv);
		}
		
		
		/*
		 * Thermal protection 
		 */
		
		// update the timer for thermal protection
		
		double vmag = this.getDStabBus().getVoltage().abs();
		
		if (this.statusA ==1){
			if(vmag < this.vstall){
				this.acStallTimer = this.acStallTimer+ dt;
			}
			else
				this.acStallTimer = 0.0;
		}
		
		
		if (this.statusB ==0){
			if(vmag > this.vrst){
				this.acRestartTimer = this.acRestartTimer+ dt;
			}
			else
				this.acRestartTimer = 0.0;
		}
		
		/*
		 *  % update the status of the motor. transition from running to
            % stalling is the same for the equivalent Motor A and B
		 */
		
		if(this.acStallTimer > this.tstall && this.statusA ==1){
			this.statusA = 0;
			this.statusB = 0;
			this.stage = 0;
		}
		
		// considering AC restarting 
		if(this.frst >0.0 && this.statusB ==0 && this.acRestartTimer > this.trst){
			this.statusB = 1;
			this.stage = 2;
		}
		
		
		/*
		  % check whether AC motor will be trip next step, and what will be
          % the remaining fraction;
		 */
		
		if(this.thermalEqnCoeff1< 0.0){
			if(this.tempA > this.th1t){
				if(this.statusA == 0){
					this.fthA = this.tempA*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;
					
					if(this.fthA<0.0)
						this.fthA = 0.0;
				}
			}
			
			if(this.tempB > this.th1t){
				if(this.statusB == 0){
					this.fthB = this.tempB*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;
					
					if(this.fthB<0.0)
						this.fthB = 0.0;
				}
			}
		}
		
		
		// Calculate the AC motor power

		// call this method to update "this.PQmotor"
		calculateMotorPower();
		
		if(vmag>1.0E-8)
		    this.equivYpq = this.PQmotor.conjugate().divide(vmag*vmag); // on motor MVABase
		else {
			this.equivYpq = new Complex(1.0E4,1.0E4); // a large value
			
		}
		return flag;
	}
	
	/**
	 * calculate the motor power, return value is pu on motor mvabase;
	 * @return
	 */
	private Complex calculateMotorPower(){
		
		// Calculate the AC motor power
		double freq = this.getDStabBus().getFreq();
		double vmag = this.getDStabBus().getVoltageMag();
		if(this.statusA == 1){
			if(vmag >= this.vbrk){
				this.pac_a  = (p0 +kp1*Math.pow((vmag-vbrk),np1))*(1+this.cmpKpf*(freq-1.0));
				this.qac_a  = (q0 +kq1*Math.pow((vmag-vbrk),nq1))*(1+this.cmpKqf*(freq-1.0));
				
			}
			else if(vmag <this.vbrk && vmag > this.vstallbrk){
				this.pac_a = (p0 +kp2*Math.pow((vbrk-vmag),np2))*(1+this.cmpKpf*(freq-1.0));
				this.qac_a = (q0 +kq2*Math.pow((vbrk-vmag),nq2))*(1+this.cmpKqf*(freq-1.0));
			}
			else{
				this.pac_a =this.Gstall*vmag*vmag;
				this.qac_a =-this.Bstall*vmag*vmag;
			}
			
		}
		// stall
		else {
			this.pac_a =this.Gstall*vmag*vmag;
			this.qac_a =-this.Bstall*vmag*vmag;
		}
		
		if(this.frst> 0.0){
			
			if(this.statusB == 1){
				if(vmag >= this.vbrk){
					this.pac_b  = (p0 +kp1*Math.pow((vmag-vbrk),np1))*(1+this.cmpKpf*(freq-1.0));
					this.qac_b  = (q0 +kq1*Math.pow((vmag-vbrk),nq1))*(1+this.cmpKqf*(freq-1.0));
					
				}
				else if(vmag <this.vbrk && vmag > this.vstallbrk){
					this.pac_b = (p0 +kp2*Math.pow((vbrk-vmag),np2))*(1+this.cmpKpf*(freq-1.0));
					this.qac_b = (q0 +kq2*Math.pow((vbrk-vmag),nq2))*(1+this.cmpKqf*(freq-1.0));
				}
				else{
					this.pac_b =this.Gstall*vmag*vmag;
					this.qac_b =-this.Bstall*vmag*vmag;
				}
				
			}
			// stall
			else {
				this.pac_b =this.Gstall*vmag*vmag;
				this.qac_b =-this.Bstall*vmag*vmag;
			}
		}
		
		// ac motor total power on motor base
		
		double Pmotor = this.pac_a*(1-this.frst)*this.fthA+this.pac_b*this.frst*this.fthB;
		double Qmotor = this.qac_a*(1-this.frst)*this.fthA+this.qac_b*this.frst*this.fthB;
		
		// consider the UV Relay,contractor as well as load change
		
		Pmotor = this.kuv*this.kcon*( 1.0 + this.accumulatedLoadChangeFactor)*Pmotor;
		Qmotor = this.kuv*this.kcon*( 1.0 + this.accumulatedLoadChangeFactor)*Qmotor;
		
		this.PQmotor = new Complex(Pmotor, Qmotor);
		
		this.setLoadPQ(this.PQmotor.multiply(getPowerConvFactorM2S()));
		
		this.pac = Pmotor*getPowerConvFactorM2S();       // both are on system base
		this.qac = Qmotor*getPowerConvFactorM2S();
		
		return this.PQmotor;
	}
	
	/**
	 * calculation the factor for converting current from motor mvabase to system vabase
	 * @return
	 */
	private double getCurrConvFactorM2S(){
		return this.I_CONV_FACTOR_M2S = this.getMvaBase()/this.getDStabBus().getNetwork().getBaseMva();
	}
	
	/**
	 * calculation the factor for converting current from motor mvabase to system vabase
	 * @return
	 */
	private double getPowerConvFactorM2S(){
		return this.getMvaBase()/this.getDStabBus().getNetwork().getBaseMva();
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
		Complex vt = this.getDStabBus().getVoltage();
		
		Complex Imotor_systembase = null;
		if(vt.abs()>this.vbrk){
			// call this method to update "this.PQmotor"
			calculateMotorPower();
			Imotor_systembase = this.PQmotor.divide(vt).conjugate().multiply(this.I_CONV_FACTOR_M2S);
		}
		else
			Imotor_systembase =  this.equivYpq.multiply(vt).multiply(this.I_CONV_FACTOR_M2S);
		
			
		if(this.equivY==null) getPosSeqEquivY();
		
		this.nortonCurInj = this.equivY.multiply(vt).subtract(Imotor_systembase);
		
		 
	 	  if(this.nortonCurInj.isNaN()) {
	 		  System.out.println("Vt, Power, load_change_fraction = "+vt.toString()+", "+this.PQmotor.toString()+", "+this.accumulatedLoadChangeFactor);
	 		  throw new Error("Current injection is NaN @"+extendedDeviceId);
	 	  }
				
		return this.nortonCurInj;
	}
	
	@Override
	public Object getOutputObject() {
	     return this.getNortonCurInj();
	}
	

	@Override
	public boolean updateAttributes(boolean netChange) {
		return post_process_step(this.timestep);
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
		result.append(uvtr1);
		result.append(", ttr1: ");
		result.append(ttr1);
		result.append(", vtr2: ");
		result.append(uvtr2);
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
	


	public int getStage() {
		return stage;
	}

	public void setStage(int newStage) {
		stage = newStage;
	}
	

	public double getLoadFactor() {
		return loadFactor;
	}


	public void setLoadFactor(double newLoadFactor) {
		double oldLoadFactor = loadFactor;
		loadFactor = newLoadFactor;
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

	public double getUVtr1() {
		return uvtr1;
	}

	public void setUVtr1(double newVtr1) {
		uvtr1 = newVtr1;
	}

	public double getTtr1() {
		return ttr1;
	}

	public void setTtr1(double newTtr1) {
		ttr1 = newTtr1;
	}

	public double getUVtr2() {
		return uvtr2;
	}

	public void setUVtr2(double newUVtr2) {
		uvtr2 = newUVtr2;
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

} //LD1PACImpl