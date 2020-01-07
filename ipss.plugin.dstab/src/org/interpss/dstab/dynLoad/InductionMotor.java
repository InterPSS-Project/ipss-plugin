/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad;

import java.util.List;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.dynLoad.DynLoadModel;

/**
 * A representation of the model object '<em><b>Induction Motor</b></em>'.
 */
public interface InductionMotor extends DynLoadModel {

	/**
	 * Returns the value of the '<em><b>Ra</b></em>' attribute.
	 */
	double getRa();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getRa <em>Ra</em>}' attribute.
	 */
	void setRa(double value);

	/**
	 * Returns the value of the '<em><b>Xl</b></em>' attribute.
	 */
	double getXl();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getXl <em>Xl</em>}' attribute.
	 */
	void setXl(double value);

	/**
	 * Returns the value of the '<em><b>Xm</b></em>' attribute.
	 */
	double getXm();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getXm <em>Xm</em>}' attribute.
	 */
	void setXm(double value);

	/**
	 * Returns the value of the '<em><b>Rr1</b></em>' attribute.
	 */
	double getRr1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getRr1 <em>Rr1</em>}' attribute.
	 */
	void setRr1(double value);

	/**
	 * Returns the value of the '<em><b>Xr1</b></em>' attribute.
	 */
	double getXr1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getXr1 <em>Xr1</em>}' attribute.
	 */
	void setXr1(double value);

	/**
	 * Returns the value of the '<em><b>Rr2</b></em>' attribute.
	 */
	double getRr2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getRr2 <em>Rr2</em>}' attribute.
	 */
	void setRr2(double value);

	/**
	 * Returns the value of the '<em><b>Xr2</b></em>' attribute.
	 */
	double getXr2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getXr2 <em>Xr2</em>}' attribute.
	 */
	void setXr2(double value);

	/**
	 * Returns the value of the '<em><b>H</b></em>' attribute.
	 */
	double getH();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getH <em>H</em>}' attribute.
	 */
	void setH(double value);

	/**
	 * Returns the value of the '<em><b>D</b></em>' attribute.
	 */
	double getD();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getD <em>D</em>}' attribute.
	 */
	void setD(double value);

	/**
	 * Returns the value of the '<em><b>Xs</b></em>' attribute.
	 */
	double getXs();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getXs <em>Xs</em>}' attribute.
	 */
	void setXs(double value);

	/**
	 * Returns the value of the '<em><b>Xp</b></em>' attribute.
	 */
	double getXp();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getXp <em>Xp</em>}' attribute.
	 */
	void setXp(double value);

	/**
	 * Returns the value of the '<em><b>Xpp</b></em>' attribute.
	 */
	double getXpp();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getXpp <em>Xpp</em>}' attribute.
	 */
	void setXpp(double value);

	/**
	 * Returns the value of the '<em><b>Tp0</b></em>' attribute.
	 */
	double getTp0();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getTp0 <em>Tp0</em>}' attribute.
	 */
	void setTp0(double value);

	/**
	 * Returns the value of the '<em><b>Tpp0</b></em>' attribute.
	 */
	double getTpp0();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getTpp0 <em>Tpp0</em>}' attribute.
	 */
	void setTpp0(double value);

	/**
	 * Returns the value of the '<em><b>Rated Voltage</b></em>' attribute.
	 */
	double getRatedVoltage();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getRatedVoltage <em>Rated Voltage</em>}' attribute.
	 */
	void setRatedVoltage(double value);

	/**
	 * Returns the value of the '<em><b>Slip</b></em>' attribute.
	 */
	double getSlip();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getSlip <em>Slip</em>}' attribute.
	 */
	void setSlip(double value);

	/**
	 * Returns the value of the '<em><b>W</b></em>' attribute.
	 */
	double getW();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getW <em>W</em>}' attribute.
	 */
	void setW(double value);

	/**
	 * Returns the value of the '<em><b>Motor Load P</b></em>' attribute.
	 */
	double getMotorLoadP();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getMotorLoadP <em>Motor Load P</em>}' attribute.
	 */
	void setMotorLoadP(double value);

	/**
	 * Returns the value of the '<em><b>Motor Load Q</b></em>' attribute.
	 */
	double getMotorLoadQ();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getMotorLoadQ <em>Motor Load Q</em>}' attribute.
	 */
	void setMotorLoadQ(double value);

	/**
	 * Returns the value of the '<em><b>Te</b></em>' attribute.
	 */
	double getTe();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getTe <em>Te</em>}' attribute.
	 */
	void setTe(double value);

	/**
	 * Returns the value of the '<em><b>Tm</b></em>' attribute.
	 */
	double getTm();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getTm <em>Tm</em>}' attribute.
	 */
	void setTm(double value);

	/**
	 * Returns the value of the '<em><b>Tm0</b></em>' attribute.
	 */
	double getTm0();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getTm0 <em>Tm0</em>}' attribute.
	 */
	void setTm0(double value);

	/**
	 * Returns the value of the '<em><b>A</b></em>' attribute.
	 */
	double getA();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getA <em>A</em>}' attribute.
	 */
	void setA(double value);

	/**
	 * Returns the value of the '<em><b>B</b></em>' attribute.
	 */
	double getB();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getB <em>B</em>}' attribute.
	 */
	void setB(double value);

	/**
	 * Returns the value of the '<em><b>C</b></em>' attribute.
	 */
	double getC();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getC <em>C</em>}' attribute.
	 */
	void setC(double value);

	/**
	 * Returns the value of the '<em><b>Curd</b></em>' attribute.
	 */
	double getCurd();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getCurd <em>Curd</em>}' attribute.
	 */
	void setCurd(double value);

	/**
	 * Returns the value of the '<em><b>Curq</b></em>' attribute.
	 */
	double getCurq();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getCurq <em>Curq</em>}' attribute.
	 */
	void setCurq(double value);

	/**
	 * Returns the value of the '<em><b>Epd</b></em>' attribute.
	 */
	double getEpd();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getEpd <em>Epd</em>}' attribute.
	 */
	void setEpd(double value);

	/**
	 * Returns the value of the '<em><b>Epq</b></em>' attribute.
	 */
	double getEpq();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getEpq <em>Epq</em>}' attribute.
	 */
	void setEpq(double value);

	/**
	 * Returns the value of the '<em><b>Eppd</b></em>' attribute.
	 */
	double getEppd();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getEppd <em>Eppd</em>}' attribute.
	 */
	void setEppd(double value);

	/**
	 * Returns the value of the '<em><b>Eppq</b></em>' attribute.
	 */
	double getEppq();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getEppq <em>Eppq</em>}' attribute.
	 */
	void setEppq(double value);

	/**
	 * Returns the value of the '<em><b>Motor Total Load</b></em>' attribute.
	 */
	Complex getMotorTotalLoad();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getMotorTotalLoad <em>Motor Total Load</em>}' attribute.
	 */
	void setMotorTotalLoad(Complex value);

	/**
	 * Returns the value of the '<em><b>Motor Power</b></em>' attribute.
	 */
	Complex getMotorPower();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getMotorPower <em>Motor Power</em>}' attribute.
	 */
	void setMotorPower(Complex value);

	/**
	 * Returns the value of the '<em><b>Ep</b></em>' attribute.
	 */
	Complex getEp();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getEp <em>Ep</em>}' attribute.
	 */
	void setEp(Complex value);

	/**
	 * Returns the value of the '<em><b>Epp</b></em>' attribute.
	 */
	Complex getEpp();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getEpp <em>Epp</em>}' attribute.
	 */
	void setEpp(Complex value);

	/**
	 * Returns the value of the '<em><b>Zp Motor Base</b></em>' attribute.
	 */
	Complex getZpMotorBase();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getZpMotorBase <em>Zp Motor Base</em>}' attribute.
	 */
	void setZpMotorBase(Complex value);

	/**
	 * Returns the value of the '<em><b>Zpp Motor Base</b></em>' attribute.
	 */
	Complex getZppMotorBase();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getZppMotorBase <em>Zpp Motor Base</em>}' attribute.
	 */
	void setZppMotorBase(Complex value);

	/**
	 * Returns the value of the '<em><b>Equiv YSys Base</b></em>' attribute.
	 */
	Complex getEquivYSysBase();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getEquivYSysBase <em>Equiv YSys Base</em>}' attribute.
	 */
	void setEquivYSysBase(Complex value);

	/**
	 * Returns the value of the '<em><b>Norton Curr Inj</b></em>' attribute.
	 */
	Complex getNortonCurrInj();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getNortonCurrInj <em>Norton Curr Inj</em>}' attribute.
	 */
	void setNortonCurrInj(Complex value);

	/**
	 * Returns the value of the '<em><b>IMotor</b></em>' attribute.
	 */
	Complex getIMotor();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getIMotor <em>IMotor</em>}' attribute.
	 */
	void setIMotor(Complex value);

	/**
	 * Returns the value of the '<em><b>ZMulti Factor</b></em>' attribute.
	 */
	double getZMultiFactor();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getZMultiFactor <em>ZMulti Factor</em>}' attribute.
	 */
	void setZMultiFactor(double value);

	/**
	 * Returns the value of the '<em><b>IMulti Factor</b></em>' attribute.
	 */
	double getIMultiFactor();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getIMultiFactor <em>IMulti Factor</em>}' attribute.
	 */
	void setIMultiFactor(double value);

	/**
	 * Returns the value of the '<em><b>VMulti Factor</b></em>' attribute.
	 */
	double getVMultiFactor();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getVMultiFactor <em>VMulti Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>VMulti Factor</em>' attribute.
	 * @see #getVMultiFactor()
	 * @generated
	 */
	void setVMultiFactor(double value);

	/**
	 * Returns the value of the '<em><b>Acc</b></em>' attribute.
	 */
	double getAcc();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getAcc <em>Acc</em>}' attribute.
	 */
	void setAcc(double value);

	/**
	 * Returns the value of the '<em><b>Sub Step N</b></em>' attribute.
	 */
	int getSubStepN();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getSubStepN <em>Sub Step N</em>}' attribute.
	 */
	void setSubStepN(int value);

	/**
	 * Returns the value of the '<em><b>Wdelt</b></em>' attribute.
	 */
	double getWdelt();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#getWdelt <em>Wdelt</em>}' attribute.
	 */
	void setWdelt(double value);

	/**
	 * Returns the value of the '<em><b>Two Axis Model</b></em>' attribute.
	 */
	boolean isTwoAxisModel();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.InductionMotor#isTwoAxisModel <em>Two Axis Model</em>}' attribute.
	 */
	void setTwoAxisModel(boolean value);

	/**
	 * set motor loading factor, in the range of [0,1]
	 * @generated NOT
	 */
	void setLoadFactor (double loadingFactor);
	
	/**
	 * get motor loading factor
	 * @generated NOT
	 */
	double getLoadFactor();
	
	
	public double getVtr1() ;

	public void setVtr1(double vtr1);

	public double getTtr1() ;

	public void setTtr1(double ttr1) ;
	
	public double getFtr1() ;

	public void setFtr1(double ftr1) ;

	public double getVrc1() ;

	public void setVrc1(double vrc1) ;

	public double getTrc1() ;

	public void setTrc1(double trc1) ;

	public double getVtr2();

	public void setVtr2(double vtr2) ;

	public double getTtr2() ;

	public void setTtr2(double ftr2) ;
	
	public double getFtr2() ;

	public void setFtr2(double ftr2) ;
	

	public double getVrc2() ;

	public void setVrc2(double vrc2) ;

	public double getTrc2() ;

	public void setTrc2(double trc2);
	/**
	 * Get the fraction of motors not tripped by the low voltage protections, including both the first and second level protections
	 * @return
	 */
	double getFuv();
	
	List<MotorProtectionControl> getProtectionControlList();


} // InductionMotor
