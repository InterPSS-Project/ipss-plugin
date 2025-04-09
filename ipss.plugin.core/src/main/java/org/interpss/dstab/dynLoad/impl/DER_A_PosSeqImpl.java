package org.interpss.dstab.dynLoad.impl;


import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Hashtable;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.DER_A_PosSeq;
import org.interpss.numeric.datatype.ComplexFunc;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;


/**
 * An implementation of the model object '<em><b>DER_A</b></em>' in positive sequence.
  */
public class DER_A_PosSeqImpl extends DynamicBusDeviceImpl implements DER_A_PosSeq {
	
	private DStabGen parentGen = null;
	
	// Initialization 
	
	private Complex  genPQInit = null;  // based on the positive sequence
	private Complex  posSeqGenPQ = null;
	private Complex  nortonCurSource = null;
	private Complex  Ipq_pos = null;
	private double   Pref = 0.5;
	private double   PdeviceBase = 10.0;
	private double   currSimuTime = 0.0;
	
	// outputs for getObject
	private Hashtable<String, Object> states = null;
	private static final String OUT_SYMBOL_V="PVGenVt";
	private static final String OUT_SYMBOL_P ="PVGenP";
	private static final String OUT_SYMBOL_Q ="PVGenQ";
	private static final String OUT_SYMBOL_Q_V ="PVGenVoltVar";
	private static final String OUT_SYMBOL_IQ ="PVGenIq";
	private static final String OUT_SYMBOL_VFRAC ="PVGenVFrac";	
	private static final String OUT_SYMBOL_FREQ ="PVGenF";
	private static final String OUT_SYMBOL_P_PI ="PVGenPowerPI";
	private static final String OUT_SYMBOL_DPORD ="PVGenDPord";
	private static final String OUT_SYMBOL_PORD ="PVGenPord";	
	private static final String OUT_SYMBOL_IP ="PVGenIp";
	private  String extended_device_Id= "";
	public String csvFile = "DER_A_modelStates.csv";
	public boolean append = true;
	public boolean writeDataToFile = false;
	private boolean isDebugMode = false;
	private boolean outputModelInternalStates = false;
	
	// System states
	private double v_meas = 1; 
	private double p_meas = 0.5;
	private double volt_var_output = 0;
	private double Iq = 0;
	private double f_meas = 1;
	private double power_PI = 1;
	private double dPord = 0.5;
	private double Pord = 0.5;
	private double Ip = 1;
	
	// system state slopes for modified euler
	private double dv_dt = 0;
	private double dp_dt = 0;
	private double dvv_dt = 0;
	private double dip_dt = 0;
	private double diq_dt = 0;
	private double df_dt = 0;
	private double dPI_dt = 0;
	private double ddP_dt = 0;
	private double dPord_dt = 0;
	
	
	// PARAMETERS
	private double Trv = 0.02; // transducer time constant for voltage measurement
	private double Trf = 0.02; // transducer time constant for freq measurement
	private double dbd1 = -0.12; // lower voltage deadband
	private double dbd2 = 0.1; // upper voltage deadband
	private double Kqv = 5; // Porp control for voltage
	private double Vref = 0; // reference voltage
	private double Tp = 0.02; // transducer time constant
	private double Tiq = 0.02; // Q control time constant
	private double Ddn = 20; // freq control droop gain (down side)
	private double Dup = 20; // freq control droop gain (up side)
	private double fdbd1 = -0.0006; // lower freq deadband
	private double fdbd2 = 0.0006; // up freq deadband
	private double femax = 99; // freq control max error
	private double femin = -99; // freq  control min error
	private double dPmax = 99; // max power output rate
	private double dPmin = -99; // min power output rate
	private double TPord = 5; // power order time constant
	private double Imax = 1.2; // max current output
	private double Vl0 = 0.44; // voltage for low voltage cut out
	private double Vl1 = 0.44 + 0.08; // low voltage cut out starts
	private double Vh0 = 1.2; // min power output
	private double Vh1 = 1.12; // max power output
	private double Tvl0 = 0.16; // min power output
	private double Tvl1 = 0.16; // min power output
	private double Tvh0 = 0.16; // min power output
	private double Tvh1 = 0.16; // min power output
	private double Vrfrac = 0.6; // fraction of device that recovers 
	private double fltrp = 58.5; // low frequency cut out
	private double fhtrp = 61.2; // high frequency cut out
	private double tfl = 0.16; // low frequency cut out timer
	private double tfh = 0.16; // high frequency cut out timer
	private double tg = 0.02; // current control time constant
	private double rrpwr = 2.0; // power rise ramp
	private double Rup = this.rrpwr;
	private double Rdown = -1*this.rrpwr;
	private double Tv = 0.02; // time constant of voltage cut out
	private double Kpg = 0.1; // active power control P
	private double Kig = 10.0; // active power control I
	private double Xe = 0.25; // gen eff reactance
	private double Vpr = 0.3; // voltage blow freq trip is disabled
	private double Iqh1 = 1.0; // Max limit of Iq
	private double Iql1 = -1.0; // Min limit of Iq
	private double Pmax = 1.2;
	private double Pmin = 0;
	
	// variables within model
	private double Qref = 0;
	private double Iqmax = 1;
	private double Iqmin = -1;
	private double Ipmax = 1.2;
	private double Ipmin = 0;
	private double Iqcmd = 0;
	private double Ipcmd = 1;
	private double power_integral = 0;
	private double dt = 0.005;
	public double  vt = 1;
	public double freqPU = 1;
	private double thetaPF = 0;
	private double QVInput = 0;
	private double f_error = 0;
	private double vMult= 1;
	private int f_relay = 1;
	private Complex posSeqIdIq = new Complex(1,0);
	private Complex internalVoltage = null;
	
	// flags
	private int PfFlag = 1; 
	private int FreqFlag = 1;
	private int PQFlag = 0;
	private int TypeFlag = 1;
	private int VtripFlag = 1;
	private int FtripFlag = 1;
	
	// for getting around afterStep not being called by the solver
	private boolean callAfterStepManually = false;
	private boolean voltTripEnabled = true;
	
	// voltage trip, used in voltageTripLogic()
	private double timeBelowVl1 = 0;
	private double timeAboveVh1 = 0;
	private double timeAboveVh0 = 0;
	private double timeBelowVl0 = 0;
	private boolean activeTimerVl1 = false;
	private boolean activeTimerVh1 = false;
	private boolean activeFracLow = false;
	private boolean activeFracHigh = false;
	private boolean activeTimerVl0 = false;
	private boolean activeTimerVh0 = false;
	private boolean activeTripLow = false;
	private boolean activeTripHigh = false;
	private double Vmin = this.Vl1;
	private double Vmax = this.Vh1;
	
	// frequency trip, used in freqTripLogic()
	private double timeAboveFhtrp = 0;
	private double timeBelowFltrp = 0;
	private boolean activeTimerFhtrp = false;
	private boolean activeTimerFltrp = false;
			
			
			
	public DER_A_PosSeqImpl() {
		super();
		this.states = new Hashtable<>();
	}
	
	public DER_A_PosSeqImpl(DStabGen dStabGen, BaseDStabBus<?, ?> parentBus, String id) {
		//System.out.println("constructor called");
		if (dStabGen != null) {
			this.parentGen = dStabGen;
			dStabGen.setDynamicGenDevice(this);
		}

		this.Xe = this.parentGen.getPosGenZ().getImaginary();
		
		// When setDstabBus is called, it will set the parent bus, and this model will be added to the DynamicBusDevice list
		setDStabBus(parentBus); 

		this.setId(id);
		
		states = new Hashtable<>();
	}
	
	// ----------------------------------------------------------------------------------------
	 
	// ---------------- Initialize States -----------------------------------------------------
	
	public boolean initStates(BaseDStabBus abus){
		
		 if(this.getPosSeqGenPQ() == null)
			 return false;
		 double systemMVABase = this.getDStabBus().getNetwork().getBaseMva();
		 this.genPQInit = new Complex((this.posSeqGenPQ.getReal() * systemMVABase),(this.posSeqGenPQ.getImaginary() * systemMVABase));
		 this.PdeviceBase = this.parentGen.getMvaBase(); // device base MVA
		 
		 
		 this.Pref = this.genPQInit.getReal() / this.PdeviceBase; // device per unit pref
		 this.Qref = this.genPQInit.getImaginary() / this.PdeviceBase; // device per unit Qref
		 //TODO: this is not correct
		 //setPosSeqGenPQ(new Complex(this.Pref, this.Qref));

		 this.vt = this.getDStabBus().getVoltageMag();
		 //this.vt = this.getParentGen().getParentBus().getVoltageMag();
		 this.Vref = this.vt;
		 this.v_meas = this.vt;
		 this.p_meas = this.Pref;
		 this.volt_var_output = 0;
		 this.f_meas = 1;
		 this.freqPU  =1;
		 this.power_PI = this.Pref;
		 this.power_integral = this.Pref;
	     this.dPord = this.Pref;
		 this.Pord = this.Pref;
		 
		 //this.Pref = this.genPQInit.getReal();
		 this.Ip = this.Pref / this.v_meas;
		 this.Ipcmd = this.Ip;
		 //this.Ipmax = this.Ipmax * this.Ip;
		 
		 this.Iq = this.Qref / this.v_meas;
		 this.Iqcmd = this.Iq;
		 this.currSimuTime = 0;

		 if (writeDataToFile) {
			 String[] headers = {"Time", "Ip", "Iq", "P", "Q", 
					 "Q/V_control", "Freq", "v_meas", "vMult", 
					 "P_PI_control", "dPord", "Pord", "Pref", "p_meas",
					 "Full Trip", "Frac Trip"};
			 try {
		            writeCsv(csvFile, headers, false, null);
		        } catch (IOException e) {
		            e.printStackTrace();
		     }
		 }
		 //TODO: this is not correct
		 //this.nortonCurSource = new Complex(this.Pref/this.v_meas, 0);
		 
		 
		 //this.Pmax = this.getParentGen().getMvaBase() / 100; // assumes system base is 100 MVA
		 //this.Pmax *= (this.getParentGen().getMvaBase() / 100);
		 this.Imax = this.Pmax / this.v_meas;
		 this.Ipmax = this.Imax;
		 this.Ipmin = 0;
		 this.Iqmin = -1* this.Imax;
		 this.Iqmax = this.Imax;
		 this.Iql1 = this.Iqmin;
		 this.Iqh1 = this.Iqmax;

		extended_device_Id = "DER_A_"+this.getId()+"@"+this.getDStabBus().getId();
		this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, extended_device_Id);
		this.setExtendedDeviceId(extended_device_Id);
		 
		 
		 if (this.outputModelInternalStates) {
			 System.out.println("Initialized bus ID: " + extended_device_Id);
			 System.out.println("Xe init: " + this.Xe);
			 System.out.println("Vref init: " + this.Vref);
			 System.out.println("IqMax init: " + this.Iqmax);
			 System.out.println("IpMax init: " + this.Ipmax);
			 System.out.println("Iq init: " + this.Iq);
			 System.out.println("Ip init: " + this.Ip);
			 System.out.println("Pord init: " + this.Pord);
			 System.out.println("Pref init: " + this.Pref);
			 System.out.println("Pmax init: " + this.Pmax);
			 System.out.println("PdeviceBase: " + this.PdeviceBase);
		 }
		 
		 return true;
	}
	
	// ----------------------------------------------------------------------------------------
	
	// -------------------- Calculate Internal Voltage ----------------------------------------
	
	@Override
	public boolean calcIpIq(int flag) {
		
		this.vt = this.getDStabBus().getVoltageMag(); // grab terminal voltage
		
		if (flag == 0) { // predictor
			this.dv_dt = finddYdX(this.vt, this.v_meas, this.Trv);
	    	this.v_meas = this.v_meas + this.dv_dt * this.dt;
		} else { // corrector
			double d_dt_2 = finddYdX(this.vt, this.v_meas, this.Trv);
			this.v_meas = this.v_meas + (0.5 * (d_dt_2 - this.dv_dt)) * this.dt;
		}
		
		//this.v_meas = firstOrderTF(this.vt, this.v_meas, this.Trv, this.dt);
		
		// compute Iqv from difference between reference and measured
		double Iqv = (deadband((this.Vref - this.v_meas), this.dbd1, this.dbd2)) * this.Kqv;
		if (this.outputModelInternalStates) {
			System.out.println("vref: " + this.Vref);
			System.out.println("vmeas: " + this.v_meas);
			System.out.println("dbd1: " + this.dbd1);
			System.out.println("Iqv: " + Iqv);
		}

		if (Iqv > this.Iqh1) {
			Iqv = this.Iqh1;
		} else if (Iqv < this.Iql1) {
			Iqv = this.Iql1;
		}
		
		if (flag == 0) { // predictor
			this.dp_dt = finddYdX(this.Pord, this.p_meas, this.Tp);
			this.p_meas = this.p_meas + this.dp_dt * this.dt;
		} else { // corrector
			double d_dt_2 = finddYdX(this.Pord, this.p_meas, this.Tp);
			this.p_meas = this.p_meas + (0.5 * (d_dt_2 - this.dp_dt)) * this.dt;
		}

		//this.p_meas = firstOrderTF(this.Pord, this.p_meas, this.Tp, this.dt);
		
		// compute q from given set PF
		double q_withPF = Math.tan(this.thetaPF) * this.p_meas;
		if (PfFlag == 0) {
			q_withPF = this.Qref;
		}
		this.QVInput = q_withPF / this.v_meas;
		
		if (flag == 0) { // predictor
			this.dvv_dt = finddYdX(this.QVInput, this.volt_var_output, this.Tiq);
			this.volt_var_output = this.volt_var_output + this.dvv_dt * this.dt;
		} else { // corrector
			double d_dt_2 = finddYdX(this.QVInput, this.volt_var_output, this.Tiq);
			this.volt_var_output = this.volt_var_output + (0.5 * (d_dt_2 - this.dvv_dt)) * this.dt;
		}
		//this.volt_var_output = firstOrderTF(this.QVInput, this.volt_var_output, this.Tiq, this.dt);
		
		// compute Iq command from volt/var control
		this.Iqcmd = Iqv + this.volt_var_output;
		
		if (this.Iqcmd > this.Iqmax) {
			this.Iqcmd = this.Iqmax;
		} else if (this.Iqcmd < this.Iqmin) {
			this.Iqcmd = this.Iqmin;
		}
		
		if (flag == 0) { // predictor
			this.df_dt = finddYdX(this.freqPU, this.f_meas, this.Trf);
			this.f_meas = this.f_meas + this.df_dt * this.dt;
		} else { // corrector
			double d_dt_2 = finddYdX(this.freqPU, this.f_meas, this.Trf);
			this.f_meas = this.f_meas + (0.5 * (d_dt_2 - this.df_dt)) * this.dt;
		}
		//this.f_meas = firstOrderTF(this.freqPU, this.f_meas, this.Trf, this.dt);
		// droop for frequency/watt control
		double fDeadbandOut = deadband((1 - this.f_meas), this.fdbd1, this.fdbd2);
		double droop = (fDeadbandOut * this.Ddn) + (fDeadbandOut * this.Dup);
		if ((fDeadbandOut * this.Ddn) > 0) {
			droop = fDeadbandOut * this.Dup;
		} else if ((fDeadbandOut * this.Dup) < 0) {
			droop = fDeadbandOut * this.Ddn;
		}
		//System.out.println("droop: " + droop);
		this.f_error = droop - this.p_meas + this.Pref;
		//System.out.println("f_error: " + this.f_error);
		
		if (this.f_error > this.femax) {
			this.f_error = this.femax;
		} else if (this.f_error < this.femin) {
			this.f_error = this.femin;
		}
		
		// THE BOTTOM TWO WERE SWITCHED
		this.power_PI = (this.f_error * this.Kpg) + this.power_integral;
		 if (this.power_PI > this.Pmax) {
			 this.power_PI = this.Pmax;
		 } else if (this.power_PI < this.Pmin) {
			 this.power_PI = this.Pmin;
		 }
		 
		//this.power_PI = PI_Control(this.f_error, this.power_PI, this.Kpg, this.Kig, this.dt);
		if (flag == 0) {
			 this.dPI_dt = finddYdX(this.power_PI, this.power_integral, this.Kpg / this.Kig);
		     this.power_integral = this.power_integral + this.dPI_dt * this.dt;
		 } else if (flag == 1) {
			 double d_dt_2 = finddYdX(this.power_PI, this.power_integral, this.Kpg / this.Kig);
			 this.power_integral = this.power_integral + (0.5 * (d_dt_2 - this.dPI_dt)) * this.dt;
		 }
		 
		//System.out.println("P_PI: " + this.power_PI);
		// below implements rate limit for dP, based on powerworld implementation
		double inp = 0;
		if (this.FreqFlag == 0) {
			inp = this.Pref;
    		//this.dPord = rate_limiter(this.Pref, this.dPord, this.dPmax, this.dPmin, this.dt);
    	} else {
    		inp = this.power_PI;
    		//this.dPord = rate_limiter(this.power_PI, this.dPord, this.dPmax, this.dPmin, this.dt);
    	}

		this.dPord = rate_limiter(inp, this.dPord, this.dPmax, this.dPmin, this.dt);
		
		if (flag == 0) { // predictor
			this.dPord_dt = finddYdX(this.dPord, this.Pord, this.TPord);
			this.Pord = this.Pord + this.dPord_dt * this.dt;
		} else { // corrector
			double d_dt_2 = finddYdX(this.dPord, this.Pord, this.TPord);
			this.Pord = this.Pord + (0.5 * (d_dt_2 - this.dPord_dt)) * this.dt;
		}
		//this.Pord = firstOrderTF(this.dPord, this.Pord, this.TPord, this.dt);
		if (this.Pord > this.Pmax) {
			this.Pord = this.Pmax;
		} else if (this.Pord < this.Pmin) {
			this.Pord = this.Pmin;
		}
		// compute Ip command based off ordered power and terminal voltage
		this.Ipcmd = this.Pord / this.v_meas;

		if (this.Ipcmd > this.Ipmax) {
			this.Ipcmd = this.Ipmax;
		} else if (this.Ipcmd < this.Ipmin) {
			this.Ipcmd = this.Ipmin;
		}
		
		double IpcmdAdj = this.Ipcmd * (this.vMult * this.f_relay); // adjust based on freq and volt trip
		double IqcmdAdj = this.Iqcmd * (this.vMult * this.f_relay); // adjust based on freq and volt trip
		double Ip_prev = this.Ip;
		//double IpcmdPrev = this.Ipcmd;
		if (flag == 0) { // predictor
			this.dip_dt = finddYdX(IpcmdAdj, this.Ip, this.tg);
			this.Ip = this.Ip + this.dip_dt * this.dt;
			
			this.diq_dt = finddYdX(IqcmdAdj, this.Iq, this.tg);
			this.Iq = this.Iq + this.diq_dt * this.dt;
		} else { // corrector
			double dIp_dt_2 = finddYdX(IpcmdAdj, this.Ip, this.tg);
			this.Ip = this.Ip + (0.5 * (dIp_dt_2 - this.dip_dt)) * this.dt;
			
			double dIq_dt_2 = finddYdX(IqcmdAdj, this.Iq, this.tg);
			this.Iq = this.Iq + (0.5 * (dIq_dt_2 - this.diq_dt)) * this.dt;
		}
		this.Ip = rate_limiter(this.Ip, Ip_prev, this.Rup, this.Rdown, this.dt);
		//this.Ip = rate_limiter(firstOrderTF(IpcmdAdj, this.Ip, this.tg, this.dt), this.Ip, this.Rup, this.Rdown, this.dt);
		//System.out.println("Ip:    " + this.Ip + '\n');
		//this.Iq = firstOrderTF(IqcmdAdj, this.Iq, this.tg, this.dt);
		
		setPosSeqIpq(new Complex(this.Ip, this.Iq));
		
		return true;
	}
	
	// ----------------------------------------------------------------------------------------
	
	// -------------------- Calculate Internal Voltage ----------------------------------------
	
	private boolean updateInternalVoltage() {
		
		//this.Xe = this.getParentGen().getPosGenZ().multiply(this.getParentGen().getZMultiFactor()).getImaginary();
		
		double vtAng = ComplexFunc.arg(this.getDStabBus().getVoltage()); // get angle of bus V
		// calculate system internal voltage in dq
		double Eq = getPosSeqVt().getImaginary() + (this.Ip * this.Xe);
		//System.out.println(this.Ip);
		double Ed = getPosSeqVt().getReal() - (this.Iq * this.Xe);

		// transform to system 
    	double Er =  Ed*Math.cos(vtAng) - Eq*Math.sin(vtAng);
    	double Ex =  Ed*Math.sin(vtAng) + Eq*Math.cos(vtAng);

		this.internalVoltage = new Complex(Er, Ex);
		
		return true;
	}
	
	// ----------------------------------------------------------------------------------------
	
	// -------------------- Calculate Norton Current Source -----------------------------------
	
	private boolean calcNortonEquivalent() {
		//System.out.println("calcNortonEquivalent called, time = " + (Math.floor(getCurrSimuTime() * 1000) / 1000));
		//System.out.println("int voltage: " + this.internalVoltage);
		//double Ifact = (this.genPQInit.getReal() * this.genPQInit.getReal()) / (this.Pref * 100);
		double sysAdjIp = this.Ip * (this.PdeviceBase / 100); // assuming system MVA base of 100 MVA
		double sysAdjIq = this.Iq * (this.PdeviceBase / 100); // assuming system MVA base of 100 MVA
		double vtAng = ComplexFunc.arg(this.getDStabBus().getVoltage()); // get angle of bus V
		double Ir = (sysAdjIp*Math.cos(vtAng) - sysAdjIq*Math.sin(vtAng));// * this.IdeviceBase; // out of device per unit
		double Ix = (sysAdjIp*Math.sin(vtAng) - sysAdjIq*Math.cos(vtAng));// * this.IdeviceBase; // out of device per unit
		Complex effectiveCurrInj = new Complex(Ir, Ix);

		//Note: need to convert to system base

		//consider the positive sequence power drawn by the equivalent Ypos at the terminal
		// Todo: this is not correct
		//Complex compensateCurrent = getPosSeqVt().divide(new Complex(0, this.Xe));

		Complex zpos = this.getParentGen().getPosGenZ().multiply(this.getParentGen().getZMultiFactor());
		Complex compensateCurrent = getPosSeqVt().divide(zpos);
    		 
    	this.nortonCurSource = effectiveCurrInj.add(compensateCurrent);
    	setNortonCurSource(this.nortonCurSource);
    	
//    	if (isDebugMode) {
//    		System.out.println("norton current source:" + this.nortonCurSource);
//    		System.out.println("voltage angle: " + vtAng);
//    		System.out.println("voltage mag: " + this.getDStabBus().getVoltageMag());
//    	}

		setPosSeqGenPQ(getPosSeqVt().multiply(effectiveCurrInj.conjugate()));
		
    	return true;
	}
	
	
	// ----------------------------------------------------------------------------------------
	
	// --------------------- frequency trip logic ---------------------------------------------
     
	private void freqTripLogic() { // output freq_filt to go to frequency relay model
		
		
		if (this.FtripFlag == 0) { // turn off frequency relay
			this.f_relay = 1;
		} else {
			if (this.f_relay == 0) {
				return;
			}
			
		    double f_meas_Hz = 1 * 60;
			if (this.getDStabBus().getVoltageMag() > this.Vpr) { 
				f_meas_Hz = this.f_meas * 60;
		    }

			if (f_meas_Hz >= this.fhtrp){
				if (!this.activeTimerFhtrp) {
					this.timeAboveFhtrp = getCurrSimuTime();
				}
				this.activeTimerFhtrp = true;
			} else {
				this.activeTimerFhtrp = false;
				this.timeAboveFhtrp = 0;
			}
			
			if (f_meas_Hz <= this.fltrp){
				if (!this.activeTimerFltrp) {
					this.timeBelowFltrp = getCurrSimuTime();
				}
				this.activeTimerFltrp = true;
			} else {
				this.activeTimerFltrp = false;
				this.timeBelowFltrp = 0;
			}
			
			if (this.activeTimerFhtrp && ((getCurrSimuTime() - this.timeAboveFhtrp) > this.tfh)) {
				this.f_relay = 0; // trip model
			} else if (this.activeTimerFltrp && ((getCurrSimuTime() - this.timeBelowFltrp) > this.tfl)) {
				this.f_relay = 0; // trip model
			} else {
				this.f_relay = 1;
			}
			
			//this.f_relay = 1;
		}
		
    	 
	}
	 
	// ----------------------------------------------------------------------------------------
	
	// --------------------- voltage trip logic -----------------------------------------------
	 
	 private void voltageTripLogic() { // referenced from psuedocode on PowerWorld's DER_A documentation
		 
		 double vlMult = 1;
			double vhMult = 1;
			 
			 
			if (this.v_meas >= this.Vl1) {
				this.activeTimerVl1 = false;
			} else if (!activeTimerVl1) {
				this.activeTimerVl1 = true;
				this.timeBelowVl1 = getCurrSimuTime();
			}
			 
			if (this.v_meas >= this.Vl0) {
				this.activeTimerVl0 = false;
			} else if (!activeTimerVl0) {
				this.activeTimerVl0 = true;
				this.timeBelowVl0 = getCurrSimuTime();
			}
			 
			if (this.v_meas <= this.Vh1) {
				this.activeTimerVh1 = false;
			} else if (!activeTimerVh1) {
				this.activeTimerVh1 = true;
				this.timeAboveVh1 = getCurrSimuTime();
			}
			 
			if (this.v_meas <= this.Vh0) {
				this.activeTimerVh0 = false;
			} else if (!activeTimerVh0) {
				this.activeTimerVh0 = true;
				this.timeAboveVh0 = getCurrSimuTime();
			}
			 
			if (!this.activeFracLow && this.activeTimerVl1 && ((getCurrSimuTime() - this.timeBelowVl1) >= this.Tvl1)) {
				this.activeFracLow = true;
			}
			if (!this.activeFracHigh && this.activeTimerVh1 && ((getCurrSimuTime() - this.timeAboveVh1) >= this.Tvh1)) { 
				this.activeFracHigh = true;
			}
			 
			if (!this.activeTripLow && this.activeTimerVl0 && ((getCurrSimuTime() - this.timeBelowVl0) >= this.Tvl0)) { 
				this.activeTripLow = true;
			}
			if (!this.activeTripHigh && this.activeTimerVh0 && ((getCurrSimuTime() - this.timeAboveVh0) >= this.Tvh0)) { 
				this.activeTripHigh = true;
			}
			 
			if ((this.Vmin > this.v_meas) && this.activeFracLow) {
				this.Vmin = this.v_meas;
				if (this.Vmin < this.Vl0) {
					this.Vmin = this.Vl0;
				}
			}
			if ((this.Vmax < this.v_meas) && this.activeFracHigh) {
				this.Vmax = this.v_meas;
				if (this.Vmax < this.Vh0) {
					this.Vmax = this.Vh0;
				}
			}
			 
			if ((this.v_meas <= this.Vl0) || this.activeTripLow) {
				vlMult = 0;
			} else if ((this.v_meas <= this.Vl1) && (this.v_meas > this.Vmin) && (this.activeFracLow)) {
				vlMult = ((this.Vmin - this.Vl0) + this.Vrfrac * (this.v_meas - this.Vmin))/ (this.Vl1 - this.Vl0); 
			} else if (this.v_meas <= this.Vl1) {
				vlMult = (this.v_meas - this.Vl0) / (this.Vl1 - this.Vl0);
			} else if (!this.activeFracLow) {
				vlMult = 1;
			} else {
				vlMult = ((this.Vmin - this.Vl0) + this.Vrfrac * (this.Vl1 - this.Vmin)) / (this.Vl1 - this.Vl0); 
			}
			 
			if ((this.v_meas >= this.Vh0) || this.activeTripHigh) {
				vhMult = 0;
			} else if ((this.v_meas >= this.Vh1) && (this.v_meas < this.Vmax) && (this.activeFracHigh)) {
				vhMult = ((this.Vmax - this.Vh0) + this.Vrfrac * (this.v_meas - this.Vmax))/ (this.Vh1 - this.Vh0); 
			} else if (this.v_meas >= this.Vh1) {
				vhMult = (this.v_meas - this.Vh0) / (this.Vh1 - this.Vh0);
			} else if (!this.activeFracHigh) {
				vhMult = 1;
			} else {
				vhMult = ((this.Vmax - this.Vh0) + this.Vrfrac * (this.Vh1 - this.Vmax)) / (this.Vh1 - this.Vh0); 
			}
			if (this.outputModelInternalStates) {
				System.out.println("vl0: " + this.Vl0);
				System.out.println("vl1: " + this.Vl1);
				System.out.println("vlMult: " + vlMult);
				//System.out.println("vhMult: " + vhMult);
			}
			double vMult = vhMult * vlMult;
			if (this.VtripFlag == 1) {
				this.vMult = firstOrderTF(vMult, this.vMult, this.Tv, this.dt);
			} else {
				this.vMult = firstOrderTF(1, this.vMult, this.Tv, this.dt);
			}
		 
			if (!this.voltTripEnabled) {
				this.vMult = 1.0;
			}
	
	}
	 
	// ----------------------------------------------------------------------------------------
	 
	// --------------------- filter used in the model -----------------------------------------
	 
	// i.e. 1 / (1 + s*Tau), using runge kutta
	private double firstOrderTF(double inputVal, double previousOutput, double time_const, double deltaTime) {
		
		double k1 = finddYdX(inputVal, previousOutput, time_const);
		double k2 = finddYdX(inputVal, previousOutput + (0.5 * deltaTime * k1), time_const);
		double k3 = finddYdX(inputVal, previousOutput + (0.5 * deltaTime * k2), time_const);
		double k4 = finddYdX(inputVal, previousOutput + (deltaTime * k3), time_const);
		
		previousOutput = previousOutput + ((deltaTime / 6) * (k1 + (2 * k2)+ (2 * k3)+ k4));

        return previousOutput;
		 
	}


	private double finddYdX(double input, double currOut, double time_const) {
		return (input - currOut) / time_const;
	}
	
	// i.e 1/ Ts
	private double integrator(double inputVal, double previousOutput, double time_const, double deltaTime) {
		//System.out.println("inputVal: " + inputVal);
		//System.out.println("prevOut: " + previousOutput);
		double outputVal = previousOutput + (inputVal * deltaTime);
		//System.out.println("outputVal: " + outputVal);
		return outputVal;
	}
	
	// ----------------------------------------------------------------------------------------
	
	// --------------------- deadband used in the model ---------------------------------------
	 
	 private double deadband(double inputVal, double deadband_low, double deadband_high) {
		 double outputVal = 0;
		 if (inputVal < deadband_low) {
			 outputVal = inputVal - deadband_low;
		 } else if (inputVal > deadband_high) {
			 outputVal = inputVal - deadband_high;
		 }
		 return outputVal;
	 }
	 
	// ----------------------------------------------------------------------------------------
	 
	// --------------------- PI control used in the model -------------------------------------
	 
	 private double PI_Control(double inputVal, double previousOutput, double Kp, double Ki, double deltaTime) {
		 
		 this.power_integral = firstOrderTF(previousOutput, this.power_integral, Kp/Ki, deltaTime);
		 double outputVal = (inputVal * Kp) + this.power_integral;
		 if (outputVal > this.Pmax) {
			 outputVal = this.Pmax;
		 } else if (outputVal < this.Pmin) {
			 outputVal = this.Pmin;
		 }
		 
		 return outputVal;
	 }
	  
	// ----------------------------------------------------------------------------------------
	 
	// --------------------- rate limiter used in the model -----------------------------------
	 
	 private double rate_limiter(double inputVal, double previousOutput, double dmax, double dmin, double deltaTime) {
		 double diff = inputVal - previousOutput;
		 
		 if (diff > (dmax * deltaTime)) {
			 diff = dmax * deltaTime;
		 } else if (diff < (dmin * deltaTime)) {
			 diff = dmin * deltaTime;
		 }
		 
		 previousOutput += diff;
		 
		 return previousOutput;
	 }
	 
	// ----------------------------------------------------------------------------------------
	 
	// --------------------- update Id,q based on flag ----------------------------------------
		
	private void updateIdIqMax() {
    	//System.out.println("time: " + getCurrSimuTime());
    	//System.out.println("Ipcmd: " + this.Ipcmd);
    	//System.out.println("Iqcmd: " + this.Iqcmd);
    	if (this.PQFlag == 0) {
    		
    		this.Iqmax = this.Imax;
    		this.Ipmax = Math.sqrt((this.Imax*this.Imax) - (this.Iqcmd*this.Iqcmd));
    		
    	} else {
    		
    		this.Ipmax = this.Imax;
    		this.Iqmax = Math.sqrt((this.Imax*this.Imax) - (this.Ipcmd*this.Ipcmd));
    		
    	}
    	 
    	if (this.TypeFlag == 0) {
    		
    		this.Ipmin = -1*this.Ipmax;
    		
    	} else {
    		
    		this.Ipmin = 0;
    		
    	}
    	//System.out.println("Updated Ipmax: " + this.Ipmax);
    	//System.out.println("UpdatedIqmax: " + this.Iqmax);
    	 
    }
	
	// ----------------------------------------------------------------------------------------
     
	// --------------------- Output for state monitor  ----------------------------------------
     @Override
	 public Hashtable<String, Object> getStates(Object ref) {
    	 //System.out.println("getStates called, time = " + (Math.floor(getCurrSimuTime() * 1000) / 1000));
		 states.put(OUT_SYMBOL_V, this.v_meas);
		 states.put(OUT_SYMBOL_P, getPosSeqGenPQ().getReal());
		 states.put(OUT_SYMBOL_Q, getPosSeqGenPQ().getImaginary());
		 states.put(OUT_SYMBOL_Q_V, this.volt_var_output);
		 states.put(OUT_SYMBOL_IQ, this.Iq);
		 states.put(OUT_SYMBOL_VFRAC, this.Vrfrac);
		 states.put(OUT_SYMBOL_FREQ, this.f_meas);
		 states.put(OUT_SYMBOL_P_PI, this.power_PI);
		 states.put(OUT_SYMBOL_DPORD, this.dPord);
		 states.put(OUT_SYMBOL_PORD, this.Pord);
		 states.put(OUT_SYMBOL_IP, this.Ip);
		 //this.nextStep(this.dt, null);
		 //System.out.println("Time: " + (Math.floor(getCurrSimuTime() * 1000) / 1000) + '\n');
		 if ((writeDataToFile)){
			 double[] dataRow = {(Math.floor(getCurrSimuTime() * 10000) / 10000), (Math.floor(this.Ip * 10000) / 10000),
					 			(Math.floor(this.Iq * 10000) / 10000), (Math.floor(getPosSeqGenPQ().getReal() * 10000) / 10000),
					 			(Math.floor(getPosSeqGenPQ().getImaginary() * 10000) / 10000), (Math.floor(this.volt_var_output * 10000) / 10000),
					 			(Math.floor(this.f_meas * 10000) / 10000), (Math.floor(this.v_meas * 10000) / 10000),
					 			(Math.floor(this.vMult * 10000) / 10000), (Math.floor(this.power_PI * 10000) / 10000), (Math.floor(this.dPord * 10000) / 10000),
					 			(Math.floor(this.Pord * 10000) / 10000), (Math.floor(this.Pref * 10000) / 10000),
					 			(Math.floor(this.p_meas * 10000) / 10000), (this.activeTripLow ? 1:0), (this.activeFracLow ? 1:0)};
			 try {
		            writeCsv(csvFile, null, true, dataRow);
		        } catch (IOException e) {
		            e.printStackTrace();
		     }
		 }
		 //System.out.println("ext dev: " +extended_device_Id);
		 if (this.outputModelInternalStates) {
			 System.out.println(this.extended_device_Id);
			 System.out.println("Time: " + (Math.floor(getCurrSimuTime() * 10000) / 10000) + " Ip: " + (Math.floor(this.Ip * 10000) / 10000));
			 System.out.println("            Iq: " + (Math.floor(this.Iq * 10000) / 10000));
			 System.out.println("            P: " + (Math.floor(getPosSeqGenPQ().getReal() * 10000) / 10000));
			 System.out.println("            Q: " + (Math.floor(getPosSeqGenPQ().getImaginary() * 10000) / 10000));
			 System.out.println("            Q/V: " + (Math.floor(this.volt_var_output * 10000) / 10000));
			 //System.out.println("            Iqv: " + this.Iqv);
			 System.out.println("            F_meas: " + (Math.floor(this.f_meas * 10000) / 10000));
			 System.out.println("            V: " + (Math.floor(this.v_meas * 10000) / 10000));
			 System.out.println("            VMult: " + (Math.floor(this.vMult * 10000) / 10000));
			 System.out.println("            P_PI: " + (Math.floor(this.power_PI * 10000) / 10000));
			 System.out.println("            dPord: " + (Math.floor(this.dPord * 10000) / 10000));
			 System.out.println("            Pord: " + (Math.floor(this.Pord * 10000) / 10000));
			 System.out.println("            Pref: " + (Math.floor(this.Pref * 10000) / 10000));
			 System.out.println("            f_error: " + this.f_error);
			 System.out.println("            Ipmax: " + this.Ipmax);
			 System.out.println("            Iqmax: " + this.Iqmax);
			 System.out.println("            Iqcmd: " + this.Iqcmd);
			 System.out.println("            Ipcmd: " + this.Ipcmd);
			 System.out.println("            f_relay: " + this.f_relay);
			 System.out.println("            Pmax: " + (Math.floor(this.Pmax * 10000) / 10000));
			 System.out.println("            Pmin: " + (Math.floor(this.Pmin * 10000) / 10000));
//			 System.out.println("            P_int: " + (Math.floor(this.power_integral * 10000) / 10000));
		 }
		 
		 if (this.callAfterStepManually) {
    		 this.afterStep(this.dt);
    	 }
		 
		 
		 return this.states;
	 }
     
  // ----------------------------------------------------------------------------------------
     
  // --------------------- Enable/disable volt/var and freq/watt  ---------------------------
     
 	public void enableVoltControl() {
 		// parameters specified by NERC, 
 		// "Reliability Guideline: Parameterization of the DER_A Model for Aggregate DER"
 		this.Trv = 0.02;
 		this.Kqv = 5;
 		this.dbd1 = -0.12;
 		this.dbd2 = 0.05;
 		this.Iqh1 = 1;
 		this.Iql1 = -1;
 	}
 	
 	public void disableVoltControl() {
 		// turns off volt/var control, reference NERC reliability guideline
 		this.Kqv = 0;
 		this.dbd1 = -99;
 		this.dbd2 = 99;
 	}
 	
 	public void enablePowerFreqControl() {
 		// parameters specified by NERC, 
 		// "Reliability Guideline: Parameterization of the DER_A Model for Aggregate DER"
 		this.FreqFlag = 1; // set to 1
 		this.Dup = 20;
 		this.Ddn = 20;
 		this.fdbd1 = -0.0006;
 		this.fdbd2 = 0.0006;
 		this.TPord = 5;
 		this.Kpg = 0.1;
 		this.Kig = 10.0;
 		this.femax = 99;
 		this.femin = -99;
 	}
 	
 	public void disablePowerFreqControl() {
 		this.FreqFlag = 0;
 		this.Dup = 0;
 		this.Ddn = 0;
 		this.TPord = 0.02; // small value when turned off
 		this.femax = 0;
 		this.femin = 0;
 		//this.Kpg = 0;
 		//this.Kig = 0;
 	}
 	
 // ----------------------------------------------------------------------------------------
 	
 // --------------------- send norton current source to network  ---------------------------
    @Override 
 	public Object getOutputObject(){
    	if (this.isDebugMode) {
    		System.out.println("getOutputObject called, time = " + (Math.floor(getCurrSimuTime() * 1000) / 1000));
    	}
    	updateInternalVoltage();
    	calcNortonEquivalent();
    	return getNortonCurSource();
    }
    
 // ----------------------------------------------------------------------------------------
     
 // --------------------- compute states at next time step ---------------------------------
    
    @Override
	public boolean nextStep(double dt, DynamicSimuMethod method, int flag){
    	 
    	 if (flag == 0) {
    		 if (this.isDebugMode) {
	    		 System.out.println("nextStep called, flag = 0");
		     }
    		 this.dt = dt;
	    	 
	    	 calcIpIq(flag);
	    	 
    	 } else if (flag == 1) {
    		 if (this.isDebugMode) {
	    		 System.out.println("nextStep called, flag = 1");
		     }
	    	 //System.out.println("calculating internal states for new time step t = " + (Math.floor(getCurrSimuTime() * 1000) / 1000));
	    	 this.dt = dt;
	    	 
	    	 calcIpIq(flag);
	    	 updateIdIqMax();
	    	 
    	 }
    	 
    	 return true;
    }
    
 // ----------------------------------------------------------------------------------------
    
 // --------------------- compute states at next time step ---------------------------------
    
    @Override
	public boolean afterStep(double dt){

    	if (this.isDebugMode) {
    		 System.out.println("afterStep called, time = " + (Math.floor(getCurrSimuTime() * 1000) / 1000));
	     }
    	incrementCurrSimuTime(dt);
    	voltageTripLogic();
    	freqTripLogic();
    	updateIdIqMax();
    	this.update_rrpwr(this.Ip);
    	
    	return true;
    }
    
 // ----------------------------------------------------------------------------------------
    
 // --------------------- calculate internal voltage given updated time step ---------------
    
    @Override
    public boolean updateAttributes(boolean netChange) {

    	 if (this.isDebugMode) {
    		 System.out.println("updateAttributes called, time = " + (Math.floor(getCurrSimuTime() * 1000) / 1000));
	     }
    	 
    	 this.vt = this.getDStabBus().getVoltageMag();;
    	 this.freqPU = ((BaseDStabBus)this.getParentGen().getParentBus()).getFreq();
    	 //this.freqPU = 1;
    	 this.Ipq_pos = new Complex(this.Ip, this.Iq);
    	 
    	 Complex genPQ = this.Ipq_pos.multiply(this.vt);
    	 //update the positive sequence generation output. 
		 //Note: this is already done in the getOutputOject() method
    	 //this.setPosSeqGenPQ(genPQ.multiply(this.PdeviceBase/this.));
    	 this.update_rrpwr(this.Ip);
    	 
    	

    	 
    	 return true;
    }
    
 // ----------------------------------------------------------------------------------------
    
 // --------------------- getters/setters --------------------------------------------------
    
    private void update_rrpwr(double Ip) {
    	if (Ip >= 0) {
    		this.Rup = this.rrpwr;
    	} else {
    		this.Rup = 10e9;
    	}
    	if (Ip <= 0) {
    		this.Rdown = -1*this.rrpwr;
    	} else {
    		this.Rdown = -10e9;
    	}
    }
    
    private void incrementCurrSimuTime(double deltaTime) {
 		this.currSimuTime += deltaTime;
 		//System.out.println("time updated, now = " + (Math.floor(getCurrSimuTime() * 1000) / 1000));
 	}
    
    public Complex getPosSeqIpq() {
 		 return this.posSeqIdIq;
 	 }
     
     public Complex  getPosSeqnortonCurSource(){
    	 return this.nortonCurSource;
     }
    
     public void    setPosSeqNortonCurSource( Complex Igen){
    	 this.nortonCurSource = Igen;
     }
     public void setNortonCurSource(Complex Igen) {
    	 this.nortonCurSource = Igen;
     }
     
     public Complex getNortonCurSource() {
    	 return this.nortonCurSource;
     }

 	 public void setPosSeqIpq(Complex ipq_pos) {
 		 Ipq_pos = ipq_pos;
 	 }
 	 
 	private Complex getPosSeqVt(){
    	 Complex vt = this.getDStabBus().getVoltage();;
    	 return vt;
     }
 	
 	private double getCurrSimuTime() {
 		return this.currSimuTime;
 	}
	@Override
	public void setPosSeqGenPQ(Complex genPQ){
		this.posSeqGenPQ = genPQ;
	}
		
	public Complex getPosSeqGenPQ(){
		if(this.posSeqGenPQ == null) {
			this.posSeqGenPQ = this.getParentGen().getGen();
		}

		return this.posSeqGenPQ;
	}
	public void setPowerFactor(double pf) {
		this.thetaPF = pf;
	}
	
	public void setDebugMode(boolean set) {
		this.isDebugMode = set;
	}
	
	public double getPowerFactor() {
		return this.thetaPF;
	}
	
	public Complex getInitGenPQ(){
		return this.genPQInit;
	}
		
	public void setVoltageRecoveryFrac(double frac) {
 		this.Vrfrac = frac;
 	}
		
	public void setCallAfterStepManually(boolean bool) {
		this.callAfterStepManually = bool;
	}
	
	public void setVh0(double vh0) {
		this.Vh0 = vh0;
	}
	
	public void setVh1(double vh1) {
		this.Vh1 = vh1;
	}
	
	public void setVl0(double vl0) {
		this.Vl0 = vl0;
	}
	
	public void setVl1(double vl1) {
		this.Vl1 = vl1;
	}
	
	public void setTvl0(double time) {
		this.Tvl0 = time;
	}
	
	public void setTvl1(double time) {
		this.Tvl1 = time;
	}
	public static void writeCsv(String fileName, String[] headers, boolean append, double[] dataRow) throws IOException {
		// Use a PrintWriter wrapped around a FileWriter
		//System.out.println("Writing?");
		try (FileWriter fileWriter = new FileWriter(fileName, append);
				PrintWriter printWriter = new PrintWriter(fileWriter)) {

			if (dataRow == null) {
				printWriter.println(String.join(",", headers));
			} else {
				String[] dataRowAsStrings = Arrays.stream(dataRow)
				.mapToObj(Double::toString)
				.toArray(String[]::new);

				// Write data row
				printWriter.println(String.join(",", dataRowAsStrings));
			}
		}
	}
	
	public void writeDataToFile(boolean tf) {
		this.writeDataToFile = tf;
	}
	
	public void outputInternalStatesDuringSim(boolean tf) {
		this.outputModelInternalStates = tf;
	}
	
	public void setFreqFlag(int flag) {
		this.FreqFlag = flag;
	}
	public void setFreqTripFlag(int flag) {
		this.FtripFlag = flag;
	}
	
	public void enableVoltageTripping(boolean tf) {
		this.voltTripEnabled = tf;
	}
	
	public void setOutputCSVFilename(String filename) {
		this.csvFile = filename;
	}

	@Override
	public DStabGen getParentGen() {
		return this.parentGen;
	}

	@Override
	public void setParentGen(DStabGen gen) {
		this.parentGen = gen;
	}
	



} 
