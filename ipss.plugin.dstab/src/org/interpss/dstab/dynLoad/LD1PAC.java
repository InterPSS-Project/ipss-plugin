/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad;

import com.interpss.dstab.dynLoad.DynLoadModel;

/**
 * A representation of the model object '<em><b>LD1PAC</b></em>'.
 */
public interface LD1PAC extends DynLoadModel {
	/**
	 * Returns the value of the '<em><b>Stage</b></em>' attribute.
	 */
	int getStage();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getStage <em>Stage</em>}' attribute.
	 */
	void setStage(int value);

	/**
	 * Returns the value of the '<em><b>P</b></em>' attribute.
	 */
	double getP();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getP <em>P</em>}' attribute.
	 */
	void setP(double value);

	/**
	 * Returns the value of the '<em><b>Q</b></em>' attribute.
	 */
	double getQ();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getQ <em>Q</em>}' attribute.
	 */
	void setQ(double value);

	/**
	 * Returns the value of the '<em><b>P0</b></em>' attribute.
	 */
	double getP0();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getP0 <em>P0</em>}' attribute.
	 */
	void setP0(double value);

	/**
	 * Returns the value of the '<em><b>Q0</b></em>' attribute.
	 */
	double getQ0();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getQ0 <em>Q0</em>}' attribute.
	 */
	void setQ0(double value);

	/**
	 * Returns the value of the '<em><b>Pac</b></em>' attribute.
	 */
	double getPac();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getPac <em>Pac</em>}' attribute.
	 */
	void setPac(double value);

	/**
	 * Returns the value of the '<em><b>Qac</b></em>' attribute.
	 */
	double getQac();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getQac <em>Qac</em>}' attribute.
	 */
	void setQac(double value);

	/**
	 * Returns the value of the '<em><b>Power Factor</b></em>' attribute.
	 */
	double getPowerFactor();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getPowerFactor <em>Power Factor</em>}' attribute.
	 */
	void setPowerFactor(double value);

	/**
	 * Returns the value of the '<em><b>Vstall</b></em>' attribute.
	 */
	double getVstall();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVstall <em>Vstall</em>}' attribute.
	 */
	void setVstall(double value);

	/**
	 * Returns the value of the '<em><b>Rstall</b></em>' attribute.
	 */
	double getRstall();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getRstall <em>Rstall</em>}' attribute.
	 */
	void setRstall(double value);

	/**
	 * Returns the value of the '<em><b>Xstall</b></em>' attribute.
	 * The default value is <code>"0.1140"</code>.
	 */
	double getXstall();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getXstall <em>Xstall</em>}' attribute.
	 */
	void setXstall(double value);

	/**
	 * Returns the value of the '<em><b>Tstall</b></em>' attribute.
	 * The default value is <code>"0.033"</code>.
	 */
	double getTstall();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTstall <em>Tstall</em>}' attribute.
	 */
	void setTstall(double value);

	/**
	 * Returns the value of the '<em><b>LFadj</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getLFadj();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getLFadj <em>LFadj</em>}' attribute.
	 */
	void setLFadj(double value);

	/**
	 * Returns the value of the '<em><b>Kp1</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getKp1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getKp1 <em>Kp1</em>}' attribute.
	 */
	void setKp1(double value);

	/**
	 * Returns the value of the '<em><b>Np1</b></em>' attribute.
	 * The default value is <code>"1.0"</code>.
	 */
	double getNp1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getNp1 <em>Np1</em>}' attribute.
	 */
	void setNp1(double value);

	/**
	 * Returns the value of the '<em><b>Kq1</b></em>' attribute.
	 * The default value is <code>"6.0"</code>.
	 */
	double getKq1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getKq1 <em>Kq1</em>}' attribute.
	 */
	void setKq1(double value);

	/**
	 * Returns the value of the '<em><b>Nq1</b></em>' attribute.
	 * The default value is <code>"2.0"</code>.
	 */
	double getNq1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getNq1 <em>Nq1</em>}' attribute.
	 */
	void setNq1(double value);

	/**
	 * Returns the value of the '<em><b>Kp2</b></em>' attribute.
	 * The default value is <code>"12.0"</code>.
	 */
	double getKp2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getKp2 <em>Kp2</em>}' attribute.
	 */
	void setKp2(double value);

	/**
	 * Returns the value of the '<em><b>Np2</b></em>' attribute.
	 * The default value is <code>"3.2"</code>.
	 */
	double getNp2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getNp2 <em>Np2</em>}' attribute.
	 */
	void setNp2(double value);

	/**
	 * Returns the value of the '<em><b>Kq2</b></em>' attribute.
	 * The default value is <code>"11.0"</code>.
	 */
	double getKq2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getKq2 <em>Kq2</em>}' attribute.
	 */
	void setKq2(double value);

	/**
	 * Returns the value of the '<em><b>Nq2</b></em>' attribute.
	 * The default value is <code>"2.5"</code>.
	 */
	double getNq2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getNq2 <em>Nq2</em>}' attribute.
	 */
	void setNq2(double value);

	/**
	 * Returns the value of the '<em><b>Vbrk</b></em>' attribute.
	 * The default value is <code>"0.86"</code>.
	 */
	double getVbrk();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVbrk <em>Vbrk</em>}' attribute.
	 */
	void setVbrk(double value);

	/**
	 * Returns the value of the '<em><b>Frst</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getFrst();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getFrst <em>Frst</em>}' attribute.
	 */
	void setFrst(double value);

	/**
	 * Returns the value of the '<em><b>Vrst</b></em>' attribute.
	 * The default value is <code>"0.9"</code>.
	 */
	double getVrst();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVrst <em>Vrst</em>}' attribute.
	 */
	void setVrst(double value);

	/**
	 * Returns the value of the '<em><b>Trst</b></em>' attribute.
	 * The default value is <code>"0.4"</code>.
	 */
	double getTrst();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTrst <em>Trst</em>}' attribute.
	 */
	void setTrst(double value);

	/**
	 * Returns the value of the '<em><b>Cmp Kpf</b></em>' attribute.
	 * The default value is <code>"1.0"</code>.
	 */
	double getCmpKpf();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getCmpKpf <em>Cmp Kpf</em>}' attribute.
	 */
	void setCmpKpf(double value);

	/**
	 * Returns the value of the '<em><b>Cmp Kqf</b></em>' attribute.
	 * The default value is <code>"-3.3"</code>.
	 */
	double getCmpKqf();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getCmpKqf <em>Cmp Kqf</em>}' attribute.
	 */
	void setCmpKqf(double value);

	/**
	 * Returns the value of the '<em><b>Fuvr</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getFuvr();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getFuvr <em>Fuvr</em>}' attribute.
	 */
	void setFuvr(double value);

	/**
	 * Returns the value of the '<em><b>Vtr1</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getVtr1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVtr1 <em>Vtr1</em>}' attribute.
	 */
	void setVtr1(double value);

	/**
	 * Returns the value of the '<em><b>Ttr1</b></em>' attribute.
	 * The default value is <code>"999.0"</code>.
	 */
	double getTtr1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTtr1 <em>Ttr1</em>}' attribute.
	 */
	void setTtr1(double value);

	/**
	 * Returns the value of the '<em><b>Vtr2</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getVtr2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVtr2 <em>Vtr2</em>}' attribute.
	 */
	void setVtr2(double value);

	/**
	 * Returns the value of the '<em><b>Ttr2</b></em>' attribute.
	 * The default value is <code>"999.0"</code>.
	 */
	double getTtr2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTtr2 <em>Ttr2</em>}' attribute.
	 */
	void setTtr2(double value);

	/**
	 * Returns the value of the '<em><b>Vc1off</b></em>' attribute.
	 * The default value is <code>"0.5"</code>.
	 */
	double getVc1off();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVc1off <em>Vc1off</em>}' attribute.
	 */
	void setVc1off(double value);

	/**
	 * Returns the value of the '<em><b>Vc2off</b></em>' attribute.
	 * The default value is <code>"0.4"</code>.
	 */
	double getVc2off();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVc2off <em>Vc2off</em>}' attribute.
	 */
	void setVc2off(double value);

	/**
	 * Returns the value of the '<em><b>Vc1on</b></em>' attribute.
	 * The default value is <code>"0.6"</code>.
	 */
	double getVc1on();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVc1on <em>Vc1on</em>}' attribute.
	 */
	void setVc1on(double value);

	/**
	 * Returns the value of the '<em><b>Vc2on</b></em>' attribute.
	 * The default value is <code>"0.5"</code>.
	 */
	double getVc2on();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getVc2on <em>Vc2on</em>}' attribute.
	 */
	void setVc2on(double value);

	/**
	 * Returns the value of the '<em><b>Tth</b></em>' attribute.
	 * The default value is <code>"999.0"</code>.
	 */
	double getTth();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTth <em>Tth</em>}' attribute.
	 */
	void setTth(double value);

	/**
	 * Returns the value of the '<em><b>Th1t</b></em>' attribute.
	 * The default value is <code>"999.0"</code>.
	 */
	double getTh1t();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTh1t <em>Th1t</em>}' attribute.
	 */
	void setTh1t(double value);

	/**
	 * Returns the value of the '<em><b>Th2t</b></em>' attribute.
	 * The default value is <code>"999.0"</code>.
	 */
	double getTh2t();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTh2t <em>Th2t</em>}' attribute.
	 */
	void setTh2t(double value);

	/**
	 * Returns the value of the '<em><b>UV Relay Timer1</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getUVRelayTimer1();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getUVRelayTimer1 <em>UV Relay Timer1</em>}' attribute.
	 */
	void setUVRelayTimer1(double value);

	/**
	 * Returns the value of the '<em><b>UV Relay Timer2</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getUVRelayTimer2();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getUVRelayTimer2 <em>UV Relay Timer2</em>}' attribute.
	 */
	void setUVRelayTimer2(double value);

	/**
	 * Returns the value of the '<em><b>Ac Stall Timer</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getAcStallTimer();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getAcStallTimer <em>Ac Stall Timer</em>}' attribute.
	 */
	void setAcStallTimer(double value);

	/**
	 * Returns the value of the '<em><b>Ac Restart Timer</b></em>' attribute.
	 * The default value is <code>"0.0"</code>.
	 */
	double getAcRestartTimer();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getAcRestartTimer <em>Ac Restart Timer</em>}' attribute.
	 */
	void setAcRestartTimer(double value);
	
	/**
	 * Returns the value of the '<em><b>Tv</b></em>' attribute.
	 * The default value is <code>"0.02"</code>.
	 */
	double getTv();

	/**
	 * Sets the value of the '{@link com.interpss.dstab.dynLoad.LD1PAC#getTv <em>Tv</em>}' attribute.
	 */
	void setTv(double value);

} // LD1PAC
