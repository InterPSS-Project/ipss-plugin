package org.interpss.threePhase.dynamic.model.impl;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.Bus3Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.device.DynamicBusDeviceType;
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
	
	V芒鈧劉stall is calculated to determine the voltage level at which there is an intersection between the stall power characteristic and the transition characteristic used for V < 0.86:
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
		
		private double p=0,q=0;  // load pq values without considering load characterisitc factors
		private double P0 = 0.0,Q0 =0.0; // initial load exponential characteristic factors
		private  double pac =0.0,qac = 0.0; // actual load pq after considering load characterisitc factors
		// there are 3 stages: 0 - running; 1 -  stall ; 2- a fraction of motors restart
		private int stage = 0;
		private double powerFactor = 0.97;
		
		
		
		//stalling setting
		private double Vstall = 0.6;
		private double Rstall = 0.1140; //0.1240;
		private double Xstall = 0.1040; // 0.1140;
		private double Tstall = 0.033;
		
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

		
		// restart
		private double Frst = 0.0; // 0.2 
		private double Vrst = 0.9;
		private double Trst = 0.4;
		
		// real and reactive power frequency sensitivity
		private double CmpKpf = 1.0;
		private double CmpKqf = -3.3;
		
		
		//UVRelay
		private double fuvr = 0.0;
		private double vtr1 = 0.0;
		private double ttr1 = 999.0;
		private double vtr2 = 0.0;
		private double ttr2 = 999.0;
		
		//Contractor setting
		private double	Vc1off = 0.5;
		private double	Vc2off = 0.4;
		private double	Vc1on  = 0.6;
		private double	Vc2on  = 0.5;
		
		//Thermal relay setting 
		// Based on  "WECC air conditioner motor model test report", Page.75
		private double	Tth =  8;
		private double	Th1t = 1.3;
		private double	Th2t = 4.3;
		

		
		//Affiliated control component
		//UV Relay
		
		//Contractor
		
		//Thermal relays
		// the tripping characterisic is modeled as y = Ax +B for x within {Th1t, Th2t}
		
		private double thEqnA = -1.0/3;
		private double thEqnB = 1.433;  // default value
		
		private double temperature = 0.0d;
		private double remainFraction = 1.0;		// Remained fraction on-line after thermal tripping
				
		// Timers for relays and internal controls
		
		private double UVRelayTimer1 = 0.0;
		private double UVRelayTimer2 = 0.0;
		
		//Timers for stalling and recovery 
		private double acStallTimer = 0.0;
		private double acRestartTimer = 0.0;


		private Complex loadPQFactor = null;
		
		private double pfactor = 0.;
		private double qfactor = 0.;
		private double pfactorStall = 0.;
		private double qfactorStall = 0.;
		
		private boolean disableInternalStallControl = false;
		
		private Hashtable<String, Object> states = null;
		private static final String OUT_SYMBOL_P ="ACMotorP";
		private static final String OUT_SYMBOL_Q ="ACMotorQ";
		private static final String OUT_SYMBOL_VT ="ACMotorVt";
		private static final String OUT_SYMBOL_STATE ="ACMotorState";
		private static final String OUT_SYMBOL_RemainFraction ="ACMotorRemainFraction";
		private String extended_device_Id = "";
		
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
		public SinglePhaseACMotor(Bus3Phase bus,String Id){
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
	        
			pac = phaseTotalLoad.getReal()*this.loadPercent/100.0d;
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
				// phase mva base is the 1/3 of the system 3phaes mva base
				double calcMva = this.pac*this.getDStabBus().getNetwork().getBaseMva()/3.0d/this.loadFactor;
				this.setMvaBase(calcMva);
			}
			
			
			
			//Check whether a compensation is needed. If yes, calculate the compensation shuntY

			// if bus.loadQ < ld1pac.q, then compShuntB = ld1pac.q-bus.loadQ
			if(qac >phaseTotalLoad.getImaginary()){
				 double v = this.getBusPhaseVoltage().abs();
				 double b = (qac - phaseTotalLoad.getImaginary())/v/v;
				 this.compensateShuntY = new Complex(0,b);
			}
			
			
			// update the Vstall and Vbrk if necessary
			//Vstall(adj) = Vstall*(1+LFadj*(CompLF-1))
			//Vbrk(adj) = Vbrk*(1+LFadj*(CompLF-1))
			
		   // Calcuate the P0 and Q0 at stage 0
			P0 = 1 - Kp1*Math.pow((1-Vbrk),Np1);
			Q0 = Math.sqrt(1 - this.powerFactor*this.powerFactor)/this.powerFactor - 
					this.Kq1*Math.pow((1.0-Vbrk),Nq1);
			
			Complex loadPQFactor = calcLoadCharacterFactor();
			p = pac/loadPQFactor.getReal();
			q = qac/loadPQFactor.getImaginary();
			
			
			//calculate the thermal protection equation coefficient
			if(Th1t >0 && Th2t>Th1t){
			   thEqnA  = -1/(Th2t-Th1t);
			   thEqnB  = Th2t/(Th2t-Th1t);
			}
			else{
				thEqnA = 0.0;
				thEqnB = 0.0;
			}
				
			this.equivY = this.getEquivY();
			
			extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getParentBus().getId()+"_phase"+this.getPhase();
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
			if(!this.disableInternalStallControl){
				if(acStallTimer>=Tstall){
					stage = 1;
				}
			}
			
			// switch to restart stage
			if (stage == 1  && Frst>0.0 && acRestartTimer >= Trst)
				stage = 2;
			
			// check whether the ac motor is stalled or not
			double v = this.getBusPhaseVoltage().abs();
			
			if(v<=this.Vstall){
				acStallTimer += dt;
			}
			else{
				acStallTimer = 0.0;
			}
			

			// update restart counter
			if(stage == 1 && v>this.Vrst && Frst>0.0){
				acRestartTimer +=dt; 
			}
			else
				acRestartTimer = 0.0;
			
		
			
			// thermal overload protection
			/*
			 * When the motor is stalled, the temperature of the motor is computed by
				integrating I^2 R through the thermal time constant Tth.  If the temperature reaches Th2t, all of the load is
				tripped.   If the temperature is between  Th1t and  Th2t, a linear fraction of the load is tripped.   The
				restartable and non-restartable portions of the load are computed separately.  The fractions of the Frst
				and 1-Frst parts of the load that have not been tripped by the thermal protection is output as fthA and fthB, respectively.	
			 */
				
			if(stage ==1){
				
				
				// Iac_pu =  Vt/Zstall
				Complex zstall = new Complex(this.Rstall,this.Xstall);
				double Iac_pu = this.getBusPhaseVoltage().divide(zstall).abs();
				
				//dTemp =  (Ic*Ic*Rstall- Temp)/Tth
				double dTemp =  (Iac_pu* Iac_pu*this.Rstall)/this.Tth;
				
				this.temperature += dTemp*dt;
				
				if(this.temperature>=this.Th1t){
					if(this.thEqnA<0.0){
					    this.remainFraction = this.temperature*this.thEqnA+this.thEqnB;
					   
					    if( this.remainFraction <0) this.remainFraction = 0.0;
					    
					  
					    // stage = -1, means it is tripped;
					    if(this.remainFraction <=0.0) stage = -1;
					}
				}
			}
				
				
			// contractor
			/*
			 *  Contactor--If the voltage drops to below Vc2off, all of the load is tripped; if the voltage is between
				Vc1off and Vc2off, a linear fraction of the load is tripped.  If the voltage later recovers to above Vc2on, all
				of the motor is reconnected; if the voltage recovers to between Vc2on and Vc1on, a linear fraction of the
				load is reconnected.  The fraction of the load that has not been tripped by the contactor is output as fcon.
			 */
			
			
			//TODO the compensation current is only update once in order to solve the convergence issue.
			
			  calculateCompensateCurInj();
			
			//loadPQFactor = calcLoadCharacterFactor();
			
			return flag;
		}
		
		
		private Complex calcLoadCharacterFactor(){
			// this can be replaced by a protected method getBusVoltageMag(), which is applicable to both pos-seq and single-phase
			double v = getBusPhaseVoltage().abs();
			// exponential factor

			if(v>this.Vbrk  & stage == 0){
				pfactor  = P0 +Kp1*Math.pow((v-Vbrk),Np1);
				qfactor  = Q0 +Kq1*Math.pow((v-Vbrk),Nq1);
		    }
			//TODO remove the constraint v> Vstall &
			else if( v<Vbrk & v> Vstall & stage == 0){
				pfactor = P0 +Kp2*Math.pow((Vbrk-v),Np2);
				qfactor = Q0 +Kq2*Math.pow((Vbrk-v),Nq2);
				pfactorStall = pfactor;
				qfactorStall = qfactor;
			}
			//TODO how about v<= Vstall? modeled as a constant impedance Zstall??
			else if(v<= Vstall & stage == 0){
				
				//TODO Need to be updated
				pfactor = pfactorStall*(v/Vstall)*(v/Vstall);
				qfactor = qfactorStall*(v/Vstall)*(v/Vstall);		
		    }
			
			// consider the frequency dependence
			if(pfactor !=0.0 ||qfactor!=0){
				double dFreq = getDStabBus().getFreq()-1.0;
				pfactor = pfactor*(1+CmpKpf*dFreq);
				qfactor = qfactor*(1+CmpKqf*dFreq/Math.sqrt(1-powerFactor*powerFactor));
			}
			
			return new Complex(pfactor,qfactor);
		}
		
		

		
		private double getMotor2SysMVARatio(){
			double ratio = this.remainFraction*this.mva*3.0d/this.getDStabBus().getNetwork().getBaseMva();
		    return ratio;
		}
		
	
		@Override
		public Complex getNortonCurInj() {
			
			if(this.nortonCurrInj == null) calculateCompensateCurInj();
			
	        Complex v = this.getBusPhaseVoltage();
	        
			
		    if(stage == 1 && this.remainFraction <1.0){
				 this.nortonCurrInj = this.getEquivY().multiply(v).multiply(1-this.remainFraction);
			}
			else if(stage ==-1){
				 this.nortonCurrInj = this.getEquivY().multiply(v);
			}
		    
		    
		    //System.out.println(this.extended_device_Id+", compensate current ="+ this.compensateCurrInj);
		    
			return this.nortonCurrInj;
			
		}
		
		private void calculateCompensateCurInj(){
           this.nortonCurrInj = new Complex(0.0d,0.0d);
           Complex v = this.getBusPhaseVoltage();
			double vmag = v.abs();
			
			// when loadPQFactor = 0, it means the AC is stalled, thus no compensation current
			if(stage !=1 && stage != -1) {

				loadPQFactor = calcLoadCharacterFactor();
					
				// p+jq is pu based on system
				Complex pq = new Complex(p*loadPQFactor.getReal(),q*loadPQFactor.getImaginary());
				pac = pq.getReal();
				qac =pq.getImaginary();
				
				
				
				Complex compPower = pq.subtract(this.getEquivY().multiply(vmag*vmag).conjugate());
				
				// I = -conj( (p+j*q - conj(v^2*this.equivY))/v)
				
				   this.nortonCurrInj= compPower.divide(v).conjugate().multiply(-1.0d);
			}
			
			
			// considering the tripping after stalling
			
			// Move to the get getCompensateCurInj(), such that this will be updated each iteration
//			else if(stage == 1 && this.remainFraction <1.0){
//				 this.compensateCurrInj = this.getEquivY().multiply(v).multiply((1-this.remainFraction)*-1.0);
//			}
//			else if(stage ==-1){
//				 this.compensateCurrInj = this.getEquivY().multiply(v).multiply(-1.0);
//			}
			

			
			//if(this.connectPhase == Phase.A)
			//System.out.println("AC motor -"+this.getId()+"@"+this.getDStabBus().getId()+", Phase - "+this.connectPhase+", dyn current injection: "+this.currInj);
		}
	
		
		@Override
		public Object getOutputObject() {
			
		     return this.getNortonCurInj();
		}
		
		//TODO
		@Override
		public boolean updateAttributes(boolean netChange) {
			return true;
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
			if(this.stage ==1){
				double vt = this.getBusPhaseVoltage().abs();
				Complex PQ =this.getEquivY().conjugate().multiply(vt*vt*this.remainFraction);
				this.pac = PQ.getReal();
				this.qac = PQ.getImaginary();
			}
			else if (stage ==-1) pac = 0.0;
				
			return pac;
		}

		public void setPac(double pac) {
			this.pac = pac;
		}

		public double getQac() {
			if(this.stage ==1){
				double vt = this.getBusPhaseVoltage().abs();
				Complex PQ =this.getEquivY().conjugate().multiply(vt*vt*this.remainFraction);
				this.pac = PQ.getReal();
				this.qac = PQ.getImaginary();
			}
			else if (stage ==-1) qac = 0.0;
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
			return fuvr;
		}

		public void setFuvr(double fuvr) {
			this.fuvr = fuvr;
		}

		public double getVtr1() {
			return vtr1;
		}

		public void setVtr1(double vtr1) {
			this.vtr1 = vtr1;
		}

		public double getTtr1() {
			return ttr1;
		}

		public void setTtr1(double ttr1) {
			this.ttr1 = ttr1;
		}

		public double getVtr2() {
			return vtr2;
		}

		public void setVtr2(double vtr2) {
			this.vtr2 = vtr2;
		}

		public double getTtr2() {
			return ttr2;
		}

		public void setTtr2(double ttr2) {
			this.ttr2 = ttr2;
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

		@Override
		public double getAccumulatedLoadChangeFactor() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public void setAccumulatedLoadChangeFactor(double value) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public Complex getCompensateCurInj() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setCompensateCurInj(Complex value) {
			// TODO Auto-generated method stub
			
		}
}
