package org.interpss.threePhase.dynamic.model.impl;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
/**
 * This dynamic model of single phase AC motor must be used under the condition that the
 * loads in the power flow data is input as Load3phase in the threePhaseLoadList.
 *
 * Reference: WECC Dynamic Composite Load Model (CMPLDW) Specifications published 01-27-2015
 * The compressor motor model is divided into two parts:
	Motor A : Those compressors that cann't restart soon after stalling
	Motor B : Those compressors that can restart soon after stalling

	The motors are represented by algebraic equations, as follows:
		If V > 0.86:
		P  =  Po * (1 + df )
		Q  =  [Qo + 6 * (V -0.86) ^2 ] * (1 - 3.3 * df )
		If V < 0.86 and V > Vstal
		P  =  [Po +  12 * (0.86- V)^3.2 ] * (1 +df )
		Q  =  [Qo + 11 * (0.86 -V)^ 2.5 ] * (1 - 3.3 * df )
		If V < Vstall:
			P = Gstall * V * V
			Q = - Bstall * V * V
		If V < Vstall for t > Tstall, motor stays in stalled state.

		For restartable motor, if V > Vrst for t > Trst, the motor restarts.

	Initialization calculations:
		Qo = Po * tan ( acos(CompPF)  ) - 6 * (1-0.86) ^2

	Vâ€™stall is calculated to determine the voltage level at which there is an intersection between the stall power characteristic and the transition characteristic used for V < 0.86:
			for ( V = 0.4; V < Vstall; V += 0.01 )
				{
				pst = Gstall * V2
				p_comp = Po + 12 * (0.86- V) 3.2
				if ( p_comp < pst )
					{
					Vstall = V
					break
					}
				}

 *
 * @author Qiuhua
 *
 */
public class SinglePhaseACMotor extends DynLoadModel1Phase {



	   //Model data

		private double p=0,q=0;  // load pq values without considering load characteristic factors
		private double p0 = 0.0,q0 =0.0; // initial load exponential characteristic factors
		private  double pac =0.0,qac = 0.0; // actual load pq after considering load characteristic factors


		private double 	pac_a = 0.0, qac_a = 0.0, pac_b = 0.0, qac_b = 0.0;

		// there are 3 stages: 0 - running; 1 -  stall ; 2- a fraction of motors restart
		private int stage = 0;
		private double powerFactor = 0.97;



		//stalling setting
		private double Vstall = 0.6;
		private double Rstall = 0.1140; //0.1240;
		private double Xstall = 0.1040; // 0.1140;
		private double Tstall = 0.033;
		private double Gstall = 0.0, Bstall = 0.0;
		private Complex Ystall = null;

		private double LFadj = 0.0;

		private double Kp1 = 0.0;
		private double Np1 = 1.0;

		private double Kq1 = 6.0;
		private double Nq1 = 2.0;

		private double Kp2 = 12.0;
		private double Np2 = 3.2;


		private double Kq2 = 11.0;
		private double Nq2 = 2.5;

		//breaking point
		private double Vbrk =0.86;
		// cross point of two curves for the run stage 2 and the stall stage
		private double Vstallbrk = 0.0;

		// restart
		private double Frst = 0.0; // no motor can restart
		private double Vrst = 0.9;
		private double Trst = 0.4; // it task the ac 0.4 second to restart of stalling

		// real and reactive power frequency sensitivity
		private double CmpKpf = 1.0;
		private double CmpKqf = -3.3;


		//UVRelay
		private double Fuvr = 0.0;
		private double UVtr1 = 0.0;
		private double Ttr1 = 999.0;
		private double UVtr2 = 0.0;
		private double Ttr2 = 999.0;

		//Contractor setting
		private double	Vc1off = 0.5;
		private double	Vc2off = 0.4;
		private double	Vc1on  = 0.65;
		private double	Vc2on  = 0.55;

		//Thermal relay setting
		// Based on  "WECC air conditioner motor model test report", Page.75
		private double	Tth =  15;
		private double	Th1t = 0.7;
		private double	Th2t = 1.9;



		//Affiliated control component
		//UV Relay and Contractor

		private double kuv =1.0, kcon = 1.0, fcon_trip = 0.0;

		private boolean isContractorActioned = false;



		//Thermal relays
		// the tripping characterisic is modeled as y = thermalEqnCoeff1*x +thermalEqnCoeff2 for x within {Th1t, Th2t}
		// here x is the internal temperature, y is the output for determining thermal tripping
		// when x>Th1t, starting trippinng, when x>Th2t, all will be tripped

		private double thermalEqnCoeff1 = -1.0/3;
		private double thermalEqnCoeff2 = 1.433;  // default value

		private double temperature = 0.0d;

		private double remainFraction = 1.0;		// Remained fraction on-line after thermal tripping

		// internally modeled as two motors, i.e., motor A and B
		private int statusA =1, statusB = 1;

		protected double fcon  = 1.0;  // fraction not tripped by the contractor

		protected double fthA = 1.0;  // fraction not tripped by the motor A thermal protection

		protected double fthB = 1.0;  // fraction not tripped by the motor B thermal protection

		protected double tempA = 0.0; // Temperature of the motor A

		protected double tempB = 0.0; // Temperature of the motor B

		protected double tv = 0.05;   // voltage measurement time constant

		protected double complf = 1.0;

		// Timers for relays and internal controls

		private double UVRelayTimer1 = 0.0;
		private double UVRelayTimer2 = 0.0;

		//Timers for stalling and recovery
		private double acStallTimer = 0.0;
		private double acRestartTimer = 0.0;

		private double timestep = 0.0;

		private boolean disableInternalStallControl = false;



		private double I_CONV_FACTOR_M2S = 0.0;


		// internal variables for integration
		private double vt_measured = 0.0;
		private double vt_measured_old = 0.0;

		private double tempA_old = 0.0;
		private double tempB_old = 0.0;

		private double dv_dt0 = 0.0;
		private double dv_dt1 = 0.0;

		private double dThA_dt0 = 0.0;
		private double dThB_dt0 = 0.0;
		private double dThA_dt1 = 0.0;
		private double dThB_dt1 = 0.0;



		private Hashtable<String, Object> states = null;
		private static final String OUT_SYMBOL_P ="ACMotorP";
		private static final String OUT_SYMBOL_Q ="ACMotorQ";
		private static final String OUT_SYMBOL_VT ="ACMotorVt";
		private static final String OUT_SYMBOL_STATE ="ACMotorState";
		private static final String OUT_SYMBOL_RemainFraction ="ACMotorRemainFraction";

		private Complex PQmotor;
		private Complex equivYpq;
		private Complex nortonCurInj;

		private boolean debugMode = false;


		public SinglePhaseACMotor(){
			states = new Hashtable<>();

		}

		public SinglePhaseACMotor(String Id){
			this();
			this.id = Id;
		}

		/**
		 * create an instance of SinglePhaseACMotor
		 * @param bus
		 * @param Id
		 */
		public SinglePhaseACMotor(DStab3PBus bus,String Id){
			this(Id);
			this.setDStabBus(bus);


		}


		@Override
		public boolean initStates() {
	       boolean flag = true;


			//TODO the initLoad is the total load at the bus ,include constant Z and I load
			//In the future, this may need to be update to consider the constant P load only

	        //TODO 11/19/2015 need to add three-phase InitLoad to differentiate phase loads


	        Complex phaseTotalLoad = null;
	        Complex3x1 totalLoad3Phase = this.getParentBus().get3PhaseTotalLoad();
	        if(totalLoad3Phase.abs()>0.0){
	        	switch(this.getPhase()){
	        		case A: phaseTotalLoad = totalLoad3Phase.a_0; break;
	        		case B: phaseTotalLoad = totalLoad3Phase.b_1; break;
	        		case C: phaseTotalLoad = totalLoad3Phase.c_2; break;
	        	}

	        }
	        else{ // TODO assuming three-phase balanced
	        	phaseTotalLoad = this.getDStabBus().getInitLoad();

	        }

	        this.stage = 1; // initialized to running state, 0 means stall

			this.Gstall = this.Rstall/(this.Rstall*this.Rstall + this.Xstall*this.Xstall);
			this.Bstall = -this.Xstall/(this.Rstall*this.Rstall + this.Xstall*this.Xstall);

			this.Ystall = new Complex(this.Gstall, this.Bstall);

			pac = phaseTotalLoad.getReal()*this.loadPercent/100.0d; // pu on system base
			qac = pac*Math.tan(Math.acos(this.powerFactor));


			// pac and qac is the initial power
			this.setInitLoadPQ(new Complex(pac,qac));
			this.setLoadPQ(new Complex(pac,qac));

			double mVABase1Phase = this.getDStabBus().getNetwork().getBaseMva()/3.0;

			// if mva is not defined and loading factor is available
			if(this.getMvaBase()==0.0){
				if(this.loadFactor >0 && this.loadFactor<=1.0) {
					IpssLogger.getLogger().fine("AC motor MVABase will be calculated based on load factor");
				} else {
					this.loadFactor = 1.0;
				}
				// single-phase mva base is the 1/3 of the system 3-phae mva base
				double calcMva = this.pac*mVABase1Phase/this.loadFactor;
				this.setMvaBase(calcMva);
			}

			// multiply factor 3 -- becuase this is for single phase, and the single-phase mvabase is 1/3 of the three-phase system MVAbase
			this.I_CONV_FACTOR_M2S = this.getMvaBase()/ mVABase1Phase;



			double vt = this.getBusPhaseVoltage().abs();
			this.vt_measured = vt;
			this.vt_measured_old = vt;

			//Check whether a compensation is needed. If yes, calculate the compensation shuntY
			// if bus.loadQ < ld1pac.q, then compShuntB = ld1pac.q-bus.loadQ
			if(qac >phaseTotalLoad.getImaginary()){

				 double b = (qac - phaseTotalLoad.getImaginary())/vt/vt;
				 this.compensateShuntY = new Complex(0,b);
			}

			this.equivYpq = new Complex(pac,qac).conjugate().divide(vt*vt); // on motor MVABase


			// update the Vstall and Vbrk if necessary
			this.Vstall= this.Vstall*(1+this.LFadj*(this.loadFactor-1));
			this.Vbrk = this.Vbrk*(1+this.LFadj*(this.loadFactor-1));

			// motor P and Q on motor mvabase
			double pac_mbase = this.pac* mVABase1Phase/this.getMvaBase();
			double qac_mbase = this.qac* mVABase1Phase/this.getMvaBase();

			Complex Sac_motor_pu = new Complex (pac_mbase, qac_mbase);
			double i_motor = Sac_motor_pu.abs()/vt;

			this.tempA = i_motor*i_motor*this.Rstall;
			this.tempB = this.tempA;

			this.tempA_old = tempA;
			this.tempB_old = tempB;

			// initialize the parts A and B of the motor
			this.pac_a = pac_mbase;
			this.pac_b = pac_mbase;

			this.qac_a = qac_mbase;
			this.qac_b = qac_mbase;


		   // Calculate the P0 and Q0 at stage 0
			this.p0 = pac_mbase - this.Kp1*Math.pow((vt-this.Vbrk),this.Np1);
			this.q0 = qac_mbase - this.Kq1*Math.pow((vt-this.Vbrk),this.Nq1);



			double pst = 0.0, pac_calc = 0.0;
			for (double v = 0.4; v<Vbrk; v+=0.0001){
				pst = this.Gstall*v*v;
				pac_calc = this.p0 + Kp1*Math.pow((v-Vbrk),this.Np1);

				if(pac_calc<pst){
					this.Vstallbrk = v;
					break;
				}

			}

			if(this.Vstallbrk>this.Vstall){
				this.Vstallbrk = this.Vstall;
			}


			//calculate the thermal protection equation coefficient
			if(Th1t >0 && Th2t>Th1t){
			   thermalEqnCoeff1  = -1/(Th2t-Th1t);
			   thermalEqnCoeff2  = Th2t/(Th2t-Th1t);
			}
			else{
				thermalEqnCoeff1 = 0.0;
				thermalEqnCoeff2 = 0.0;
			}

			this.equivY = this.getEquivY(); // On system mva base, not motor mva base

			extendedDeviceId = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, extendedDeviceId);

			return flag;
		}

		@Override public String getExtendedDeviceId(){
			extendedDeviceId = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
			return this.extendedDeviceId;
		}



		/**
		 * The thermal protection heating increase is modeled as
		 * differential equation, thus it must be represented with the nextStep();
		 *
		 * The stall timer as well as the recovery timer are also counted and updated in this method
		 */
		@Override
		public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
			boolean boolFlag = true;

			// check the protection actions and update the status of AC motor accordingly
			timestep = dt;

			Complex vt = this.getBusPhaseVoltage();
			double vmag = vt.abs();



			// thermal overload protection
			/*
			 * When the motor is stalled, the temperature of the motor is computed by
				integrating I^2 R through the thermal time constant Tth.  If the temperature reaches Th2t, all of the load is
				tripped.   If the temperature is between  Th1t and  Th2t, a linear fraction of the load is tripped.   The termperature
				 of the A and B portions of the load are computed separately.  The fractions of the A
				and B parts of the load that have not been tripped by the thermal protection is output as fthA and fthB, respectively.
			 */


			// motor stator current in motor mva base
			Complex ImotorA_pu = new Complex(0.0);
			Complex ImotorB_pu = new Complex(0.0);

			if(this.statusA ==0) {
				ImotorA_pu = vt.multiply(this.Ystall);
			} else{
				//Updated 10/14/2017 to fix the bug with the voltage is close to zero;
				if(vt.abs()>this.Vstallbrk) {
					ImotorA_pu = new Complex(pac_a, qac_a).divide(vt).conjugate();
				} else {
					ImotorA_pu = vt.multiply(this.Ystall);
				}
			}

			if(this.statusB ==0) {
				ImotorB_pu = vt.multiply(this.Ystall);
			} else{
				if(vt.abs()>this.Vstallbrk) {
					ImotorB_pu = new Complex(pac_b, qac_b).divide(vt).conjugate();
				} else {
					ImotorB_pu = vt.multiply(this.Ystall);
				}
			}


			if(method==DynamicSimuMethod.MODIFIED_EULER) {

				if(flag == 0) {
			       // voltage measurements

					dv_dt0 = (vmag-this.vt_measured_old)/this.tv;

					this.vt_measured = this.vt_measured_old+ dv_dt0*dt;

					// temperature

				    dThA_dt0 = (Math.pow(ImotorA_pu.abs(),2)*this.Rstall - this.tempA)/this.Tth;

					dThB_dt0 = (Math.pow(ImotorB_pu.abs(),2)*this.Rstall - this.tempB)/this.Tth;

					this.tempA = this.tempA_old+ dThA_dt0*dt;
					this.tempB = this.tempB_old+ dThB_dt0*dt;
				}
				else if (flag ==1) {

				   dv_dt1 = (vmag-vt_measured)/this.tv;

				   this.vt_measured = this.vt_measured_old + (dv_dt0 + dv_dt1)*0.5*dt;

				   dThA_dt1 = (Math.pow(ImotorA_pu.abs(),2)*this.Rstall - this.tempA)/this.Tth;

				   dThB_dt1 = (Math.pow(ImotorB_pu.abs(),2)*this.Rstall - this.tempB)/this.Tth;

				   this.tempA = this.tempA_old + (dThA_dt0+dThA_dt1)*0.5*dt;
				   this.tempB = this.tempB_old + (dThB_dt0+dThB_dt1)*0.5*dt;

				   this.vt_measured_old = this.vt_measured;

				   this.tempA_old = this.tempA;
				   this.tempB_old = this.tempB;
				}

			}
			else {
				throw new Error("Only the Modified Euler method is supported. ");
			}
			return boolFlag;
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
	         % below uvtr1 for ttr1 seconds, the fraction "fuvtr" of the load is tripped; If the voltage drops below uvtr2
	         % for ttr2 seconds, the fraction "fuvr" of the load is tripped
			*/

			if(this.vt_measured <this.getUVtr1() && this.Fuvr >0.0){
				this.UVRelayTimer1 = this.UVRelayTimer1 +dt;
			} else {
				this.UVRelayTimer1 = 0.0;
			}

			if(this.vt_measured <this.getUVtr2() && this.Fuvr >0.0){
				this.UVRelayTimer2 = this.UVRelayTimer2 +dt;
			} else {
				this.UVRelayTimer2 = 0.0;
			}

			// trip the portion with under voltage relays
			if(this.UVRelayTimer1> this.Ttr1){
				this.kuv = 1.0 - this.Fuvr;
			}

			if(this.UVRelayTimer2> this.Ttr2){
				this.kuv = 1.0 - this.Fuvr;
			}

			/*
	        %  contractor
	        %  Contactor – If the voltage drops to below Vc2off, all of the load is tripped; if the voltage is between
	        % Vc1off and Vc2off, a linear fraction of the load is tripped. If the voltage later recovers to above Vc2on, all
	        % of the motor is reconnected; if the voltage recovers to between Vc2on and Vc1on, a linear fraction of the
	        % load is reconnected.
	        */

			if(this.vt_measured < this.Vc2off){
				this.kcon  = 0.0;
				this.fcon_trip = 1.0;
			}
			else if(this.vt_measured>=this.Vc2off && this.vt_measured <this.Vc1off){
				this.kcon = (this.vt_measured - this.Vc2off)/(this.Vc1off- this.Vc2off);
				this.fcon_trip = 1.0 - this.kcon;

			}


			if(this.vt_measured >=this.Vc1on){
				this.kcon = 1.0;
			}
			else if(this.vt_measured < this.Vc1on && this.vt_measured >= this.Vc2on){
				double Frecv = (this.vt_measured - this.Vc2on)/(this.Vc1on- this.Vc2on);
				this.kcon  = 1 - this.fcon_trip*(1-Frecv);
			}


			/*
			 * Thermal protection
			 */

			// update the timer for thermal protection

			double vmag = this.getBusPhaseVoltage().abs();

			if (this.statusA ==1){
				if(vmag < this.Vstall){
					this.acStallTimer = this.acStallTimer+ dt;
				} else {
					this.acStallTimer = 0.0;
				}
			}


			if (this.statusB ==0){
				if(vmag > this.Vrst){
					this.acRestartTimer = this.acRestartTimer+ dt;
				} else {
					this.acRestartTimer = 0.0;
				}
			}

			/*
			 *  % update the status of the motor. transition from running to
	            % stalling is the same for the equivalent Motor A and B
			 */

			if(this.acStallTimer > this.Tstall && this.statusA ==1){
				this.statusA = 0;
				this.statusB = 0;
				this.stage = 0;
			}

			// considering AC restarting
			if(this.Frst >0.0 && this.statusB ==0 && this.acRestartTimer > this.Trst){
				this.statusB = 1;
				this.stage = 2;
			}


			/*
			  % check whether AC motor will be trip next step, and what will be
	          % the remaining fraction;
			 */

			if(this.thermalEqnCoeff1< 0.0){
				if(this.tempA > this.Th1t){
					if(this.statusA == 0){
						this.fthA = this.tempA*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;

						if(this.fthA<0.0) {
							this.fthA = 0.0;
						}
					}
				}

				if(this.tempB > this.Th1t){
					if(this.statusB == 0){
						this.fthB = this.tempB*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;

						if(this.fthB<0.0) {
							this.fthB = 0.0;
						}
					}
				}
			}

			if(debugMode){
			   System.out.println("next step: motor, fthA, fthB = "+this.extendedDeviceId+","+this.fthA+","+this.fthB );
			   System.out.println("next step: motor, thEqnA, tempA, tempB = "+this.extendedDeviceId+","+thermalEqnCoeff1+","+this.tempA+","+this.tempB );
			}
			// Calculate the AC motor power

			// call this method to update "this.PQmotor"
			calculateMotorPower();

			// update the equivalent admittance for calculating AC power
			this.equivYpq = this.PQmotor.conjugate().divide(vmag*vmag); // on motor MVABase

			return flag;
		}

		/**
		 * calculate the motor power, return value is pu on motor mvabase;
		 * @return
		 */
		private Complex calculateMotorPower(){

			// Calculate the AC motor power
			double freq = this.getDStabBus().getFreq();
			double vmag = this.getBusPhaseVoltage().abs();

			// running mode
			if(this.statusA == 1){
				if(vmag >= this.Vbrk){
					this.pac_a  = (p0 +Kp1*Math.pow((vmag-this.Vbrk),this.Np1))*(1+this.CmpKpf*(freq-1.0));
					this.qac_a  = (q0 +Kq1*Math.pow((vmag-this.Vbrk),this.Nq1))*(1+this.CmpKqf*(freq-1.0));

				}
				else if(vmag <this.Vbrk && vmag > this.Vstallbrk){
					this.pac_a = (p0 +Kp2*Math.pow((this.Vbrk-vmag),this.Np2))*(1+this.CmpKpf*(freq-1.0));
					this.qac_a = (q0 +Kq2*Math.pow((this.Vbrk-vmag),this.Np2))*(1+this.CmpKqf*(freq-1.0));
				}
				else{
					this.pac_a =this.Gstall*vmag*vmag;
					this.qac_a =-this.Bstall*vmag*vmag;
				}

			}
			// stall mode
			else {
				this.pac_a =this.Gstall*vmag*vmag;
				this.qac_a =-this.Bstall*vmag*vmag;
			}

			if(this.Frst> 0.0){

				if(this.statusB == 1){
					if(vmag >= this.Vbrk){
						this.pac_b  = (p0 +Kp1*Math.pow((vmag-Vbrk),Np1))*(1+this.CmpKpf*(freq-1.0));
						this.qac_b  = (q0 +Kq1*Math.pow((vmag-Vbrk),Nq1))*(1+this.CmpKqf*(freq-1.0));

					}
					else if(vmag <this.Vbrk && vmag > this.Vstallbrk){
						this.pac_b = (p0 +Kp2*Math.pow((Vbrk-vmag),Np2))*(1+this.CmpKpf*(freq-1.0));
						this.qac_b = (q0 +Kq2*Math.pow((Vbrk-vmag),Nq2))*(1+this.CmpKqf*(freq-1.0));
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

			double Pmotor = this.pac_a*(1-this.Frst)*this.fthA+this.pac_b*this.Frst*this.fthB;
			double Qmotor = this.qac_a*(1-this.Frst)*this.fthA+this.qac_b*this.Frst*this.fthB;

			// consider the UR Relay and contractor status
			//Pmotor = this.kuv*this.kcon*Pmotor;
			//Qmotor = this.kuv*this.kcon*Qmotor;

			Pmotor = this.kuv*this.kcon*( 1.0 + this.accumulatedLoadChangeFactor)*Pmotor;
			Qmotor = this.kuv*this.kcon*( 1.0 + this.accumulatedLoadChangeFactor)*Qmotor;

			this.PQmotor = new Complex(Pmotor, Qmotor); // on motor base

			this.pac = Pmotor*getPowerConvFactorM2S(); // converted to system base
			this.qac = Qmotor*getPowerConvFactorM2S();

			if(debugMode){
				System.out.println(extendedDeviceId+" statusA, pac_a,pac_b,kuv, kcon= "+this.statusA+","+this.pac_a+","+this.qac_a+","+this.kuv+","+this.kcon);
				System.out.println(extendedDeviceId+" power on system base = "+this.getLoadPQ().toString());
			}
			return this.PQmotor;
		}

		/**
		 * calculation the factor for converting current from motor mvabase to system vabase
		 * @return
		 */
		private double getPowerConvFactorM2S(){
			return this.getMvaBase()/this.getDStabBus().getNetwork().getBaseMva()*3.0;
		}

		@Override
		public Complex getNortonCurInj() {


	        Complex vt = this.getBusPhaseVoltage();

	        Complex Imotor_systembase = null;
			if(vt.abs()>this.Vbrk){
				// call this method to update "this.PQmotor"
				calculateMotorPower();
				Imotor_systembase = this.PQmotor.divide(vt).conjugate().multiply(this.I_CONV_FACTOR_M2S);
			} else {
				Imotor_systembase =  this.equivYpq.multiply(vt).multiply(this.I_CONV_FACTOR_M2S);
			}




			this.nortonCurInj = getEquivY().multiply(vt).subtract(Imotor_systembase);

			return this.nortonCurInj;

		}

		@Override
		public boolean changeLoad(double factor) {
			if (factor < -1.0){
				IpssLogger.getLogger().severe(" percentageFactor < -1.0, this change will not be applied");
				return false;
			}
			if (this.accumulatedLoadChangeFactor <= -1.0 &&  factor < 0.0) {
				IpssLogger.getLogger().severe( "this.accumulatedLoadChangeFactor<=-1.0 and percentageFactor < 0.0, this change will not be applied");
			}

			this.accumulatedLoadChangeFactor = this.accumulatedLoadChangeFactor + factor;

			if (this.accumulatedLoadChangeFactor < -1.0){
				IpssLogger.getLogger().severe( "the accumulatedLoadChangeFactor is less than -1.0 after this change, so the accumulatedLoadChangeFactor is reset to -1.0");
				this.accumulatedLoadChangeFactor = -1.0;
			}
			IpssLogger.getLogger().info("accumulated Load Change Factor = "+ this.accumulatedLoadChangeFactor);

			return true;
		}

		@Override
		public Object getOutputObject() {

		     return this.getNortonCurInj();
		}

		// This must be overrided to implement the post processing step after network solution converges at each time step
		@Override
		public boolean updateAttributes(boolean netChange) {
			return post_process_step(this.timestep);
		}

		@Override
		public Hashtable<String, Object> getStates(Object ref) {
			states.put(OUT_SYMBOL_P, this.getPac());
			states.put(OUT_SYMBOL_Q, this.getQac());
			states.put(OUT_SYMBOL_VT, this.getBusPhaseVoltage().abs());
			states.put(OUT_SYMBOL_STATE, stage==1?0:(stage ==-1? -1:1));
			states.put(OUT_SYMBOL_RemainFraction, this.remainFraction);
			return this.states;
		}

		@Override
		public Complex getEquivY() {
			if(this.equivY == null){
				Complex zstall = new Complex(this.Rstall,this.Xstall);
				Complex y = new Complex(1.0,0).divide(zstall);
				this.equivY = y.multiply(this.mva/this.getDStabBus().getNetwork().getBaseMva()*3.0d);
			}
			return this.equivY;
		}



		@Override
		public Complex getLoadPQ() {


			return this.loadPQ = new Complex(getPac(),getQac());

		}


		@Override
		public String getScripts() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setScripts(String value) {
			throw new UnsupportedOperationException();

		}


		public double getPac() {


			return pac;
		}

		public void setPac(double pac) {
			this.pac = pac;
		}

		public double getQac() {

			return qac;
		}

		public void setQac(double qac) {
			this.qac = qac;
		}

		public int getStage() {
			return stage;
		}

		public void setStage(int stage) {
			this.stage = stage;
		}

		public double getPowerFactor() {
			return powerFactor;
		}

		public void setPowerFactor(double powerFactor) {
			this.powerFactor = powerFactor;
		}

		public double getVstall() {
			return Vstall;
		}

		public void setVstall(double vstall) {
			Vstall = vstall;
		}

		public double getRstall() {
			return Rstall;
		}

		public void setRstall(double rstall) {
			Rstall = rstall;
		}

		public double getXstall() {
			return Xstall;
		}

		public void setXstall(double xstall) {
			Xstall = xstall;
		}

		public double getTstall() {
			return Tstall;
		}

		public void setTstall(double tstall) {
			Tstall = tstall;
		}

		public double getLFadj() {
			return LFadj;
		}

		public void setLFadj(double lFadj) {
			LFadj = lFadj;
		}

		public double getKp1() {
			return Kp1;
		}

		public void setKp1(double kp1) {
			Kp1 = kp1;
		}

		public double getNp1() {
			return Np1;
		}

		public void setNp1(double np1) {
			Np1 = np1;
		}

		public double getKq1() {
			return Kq1;
		}

		public void setKq1(double kq1) {
			Kq1 = kq1;
		}

		public double getNq1() {
			return Nq1;
		}

		public void setNq1(double nq1) {
			Nq1 = nq1;
		}

		public double getKp2() {
			return Kp2;
		}

		public void setKp2(double kp2) {
			Kp2 = kp2;
		}

		public double getNp2() {
			return Np2;
		}

		public void setNp2(double np2) {
			Np2 = np2;
		}

		public double getKq2() {
			return Kq2;
		}

		public void setKq2(double kq2) {
			Kq2 = kq2;
		}

		public double getNq2() {
			return Nq2;
		}

		public void setNq2(double nq2) {
			Nq2 = nq2;
		}

		public double getVbrk() {
			return Vbrk;
		}

		public void setVbrk(double vbrk) {
			Vbrk = vbrk;
		}

		public double getFrst() {
			return Frst;
		}

		public void setFrst(double frst) {
			Frst = frst;
		}

		public double getVrst() {
			return Vrst;
		}

		public void setVrst(double vrst) {
			Vrst = vrst;
		}

		public double getTrst() {
			return Trst;
		}

		public void setTrst(double trst) {
			Trst = trst;
		}

		public double getCmpKpf() {
			return CmpKpf;
		}

		public void setCmpKpf(double cmpKpf) {
			CmpKpf = cmpKpf;
		}

		public double getCmpKqf() {
			return CmpKqf;
		}

		public void setCmpKqf(double cmpKqf) {
			CmpKqf = cmpKqf;
		}

		public double getFuvr() {
			return Fuvr;
		}

		public void setFuvr(double fuvr) {
			this.Fuvr = fuvr;
		}

		public double getUVtr1() {
			return UVtr1;
		}

		public void setUVtr1(double vtr1) {
			this.UVtr1 = vtr1;
		}

		public double getTtr1() {
			return Ttr1;
		}

		public void setTtr1(double ttr1) {
			this.Ttr1 = ttr1;
		}

		public double getUVtr2() {
			return UVtr2;
		}

		public void setUVtr2(double vtr2) {
			this.UVtr2 = vtr2;
		}

		public double getTtr2() {
			return Ttr2;
		}

		public void setTtr2(double ttr2) {
			this.Ttr2 = ttr2;
		}

		public double getVc1off() {
			return Vc1off;
		}

		public void setVc1off(double vc1off) {
			Vc1off = vc1off;
		}

		public double getVc2off() {
			return Vc2off;
		}

		public void setVc2off(double vc2off) {
			Vc2off = vc2off;
		}

		public double getVc1on() {
			return Vc1on;
		}

		public void setVc1on(double vc1on) {
			Vc1on = vc1on;
		}

		public double getVc2on() {
			return Vc2on;
		}

		public void setVc2on(double vc2on) {
			Vc2on = vc2on;
		}

		public double getTth() {
			return Tth;
		}

		public void setTth(double tth) {
			Tth = tth;
		}

		public double getTh1t() {
			return Th1t;
		}

		public void setTh1t(double th1t) {
			Th1t = th1t;
		}

		public double getTh2t() {
			return Th2t;
		}

		public void setTh2t(double th2t) {
			Th2t = th2t;
		}

		public double getUVRelayTimer1() {
			return UVRelayTimer1;
		}

		public void setUVRelayTimer1(double uVRelayTimer1) {
			UVRelayTimer1 = uVRelayTimer1;
		}

		public double getUVRelayTimer2() {
			return UVRelayTimer2;
		}

		public void setUVRelayTimer2(double uVRelayTimer2) {
			UVRelayTimer2 = uVRelayTimer2;
		}



	    public void disableInternalStallControl(boolean disableStallControl){
	    	this.disableInternalStallControl = disableStallControl;
	    }










}