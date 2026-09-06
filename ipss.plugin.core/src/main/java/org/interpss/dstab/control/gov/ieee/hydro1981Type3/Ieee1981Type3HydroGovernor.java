package org.interpss.dstab.control.gov.ieee.hydro1981Type3;

import java.lang.reflect.Field;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnnotateGovernor;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/**
 * IEEE 1981 Type-3/PSS/E IEEEG3 hydro turbine governor.
 *
 * <p>The annotated fields are retained for source and UI compatibility with
 * the original InterPSS controller. Runtime integration uses the equivalent
 * explicit state equations because the legacy generated CML path did not
 * preserve the documented permanent-droop equilibrium.</p>
 * 
 * @author Tony Huang
 * date: 9/25/2013
 */
@AnController(
		   input="mach.speed-1.0",
		   output="this.wFilterBlock.y",
		   refPoint="this.delayBlock.u0 + this.sigmaGainBlock.y + this.washoutBlock.y + this.gainBlock.y",
		   display= {})
public class Ieee1981Type3HydroGovernor extends AnnotateGovernor implements IntegrationStepAware {
	private static final double EPS = 1.0e-12;
	private double integrationStep;
	private double minimumTimeConstantMultiplier = 1.0;
	private State state = State.zero();
	private State oldState = State.zero();
	private Derivatives oldDerivatives = Derivatives.zero();
	private double reference;
	private boolean initialized;
	public double pmin = 0.0;
	public double pmax = 1.0	;// input as PU unit
	public double sigma=0.05;//permanent speed droop coefficient

	//1.1 GainBlock	
	public double k1=1.0; 
	@AnControllerField(
        type= CMLFieldEnum.StaticBlock,
        input="mach.speed - 1.0",
        parameter={"type.NoLimit", "this.k1"},
        y0="this.refPoint - this.delayBlock.u0 - this.sigmaGainBlock.y - this.washoutBlock.y"	)
public GainBlock gainBlock;

	//1.2 delayBlock
	public double tg=0.25, k_tg=1/tg, tp=0.04, velClose=0.2, velOpen=0.3;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.refPoint - this.sigmaGainBlock.y - this.washoutBlock.y - this.gainBlock.y",
        parameter={"type.NonWindup", "this.k_tg", "this.tp","this.velOpen","this.velClose"},
        y0="this.intBlock.u0"	)
public DelayControlBlock delayBlock;

	//1.3 intBlock
	public double k_it=1.0/*constant*/ ;
		@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.delayBlock.y",
        parameter={"type.Limit", "this.k_it", "this.pmax","this.pmin"},
        y0="this.wFilterBlock.u0"	)
public IntegrationControlBlock intBlock;
	
	//1.4 sigmaGainBlock -- permanent droop compensation
	public double t = 0.0;
	@AnControllerField(
        type= CMLFieldEnum.ControlBlock,
        input="this.intBlock.y",
        parameter={"type.NoLimit", "this.sigma", "this.t"},
        feedback=true)
public DelayControlBlock sigmaGainBlock;

//1.5 washoutBlock
public double delta = 0.5, tr =5.0;
	@AnControllerField(
  			type= CMLFieldEnum.ControlBlock,
  			input= "this.intBlock.y",
   			parameter={"type.NoLimit", "this.delta", "this.tr"},
  			feedback=true)
public WashoutControlBlock washoutBlock;

	//1.6 wFilterBlock
 public double a23 = 1.0, a11 = 0.5, a13 = 1.0, a21 =1.5, tw=1.0, t1 = (a11-a13*a21/a23)*tw,tw_2=a11*tw;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.intBlock.y",
            parameter={"type.NoLimit", "this.a23", "this.t1", "this.tw_2"},
            y0="mach.pm")
public FilterControlBlock wFilterBlock;

	    
	    public Ieee1981Type3HydroGovernor() {
	        this.setName("Ieee1981Type3HydroGovernor");
	        this.setCategory("InterPSS");
	    }
	    
	    /**
	     * Constructor
	     *
	     * @param id excitor id
	     * @param name excitor name
	     */
	    public Ieee1981Type3HydroGovernor(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. However init it here is a MUST
	        _data = new Ieee1981Type3HydroGovernorData();
	    }

	@Override
	public void configureIntegrationStep(double timeStepSec) {
		configureIntegrationStep(timeStepSec, 1.0);
	}

	public void configureIntegrationStep(double timeStepSec, double multiplier) {
		this.integrationStep = timeStepSec;
		this.minimumTimeConstantMultiplier = multiplier;
	}
	    
	    /**
	     * Get the excitor data
	     *
	     * @return the data object
	     */
	    public Ieee1981Type3HydroGovernorData getData() {
	        return (Ieee1981Type3HydroGovernorData)_data;
	    }
	    
	    /**
	     *  Init the controller states
	     *
	     *  @param msg the SessionMsg object
	     */
	    @Override
		public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
	        this.tg = correctedMinimum(getData().getTg());
	        this.tp = correctedPilotValve(getData().getTp());
	        this.tr = correctedMinimum(getData().getTr());
	        this.tw = correctedMinimum(getData().getTw());
	        if (tg <= EPS || tp < 0.0 || tr <= EPS || tw <= EPS
	                || getData().getA11() <= EPS || getData().getA23() <= EPS) {
	            return false;
	        }
	        double rawOpen = getData().getVelOpen();
	        double rawClose = getData().getVelClose();
	        if (rawOpen < rawClose) {
	            double swap = rawOpen; rawOpen = rawClose; rawClose = swap;
	        }
	        this.velOpen = Math.abs(rawOpen);
	        this.velClose = -Math.abs(rawClose);
	        double rawMax = Math.max(getData().getPmax(), getData().getPmin());
	        double rawMin = Math.min(getData().getPmax(), getData().getPmin());
	        this.sigma=getData().getSigma();
	        this.delta=getData().getDelta();
	        this.a11 = getData().getA11();
	        this.a13 = getData().getA13();
	        this.a21 = getData().getA21();
	        this.a23 = getData().getA23();
	        double initialGate = mach.getPm() / a23;
	        this.pmax = Math.max(rawMax, initialGate);
	        this.pmin = Math.min(rawMin, initialGate);
	        
	        k_tg=1/tg;
	        t1 = (a11-a13*a21/a23)*tw;
	        tw_2=a11*tw;
	        
	        double gate0 = initialGate;
	        state = new State(0.0, gate0, gate0, gate0);
	        oldState = state;
	        reference = sigma * gate0;
	        initialized = true;
	        return true;
	    }

	private double minimumResolvedTimeConstant() {
		return minimumTimeConstantMultiplier * integrationStep;
	}

	private double correctedMinimum(double value) {
		double minimum = minimumResolvedTimeConstant();
		return value > 0.0 && value < minimum ? minimum : value;
	}

	private double correctedPilotValve(double value) {
		double minimum = minimumResolvedTimeConstant();
		if (value > 0.0 && value < 0.25 * minimum) return 0.0;
		if (value > 0.25 * minimum && value < 0.5 * minimum) return 0.5 * minimum;
		return value;
	}

	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
		if (method != DynamicSimuMethod.MODIFIED_EULER) {
			throw new InterpssRuntimeException("IEEEG3 supports MODIFIED_EULER only");
		}
		if (!initialized) return false;
		if (flag == 0) {
			oldState = state;
			oldDerivatives = derivatives(oldState, mach);
			state = constrain(oldState.plus(oldDerivatives, dt));
		} else if (flag == 1) {
			Derivatives corrected = derivatives(state, mach);
			state = constrain(oldState.plusAverage(oldDerivatives, corrected, dt));
		} else {
			throw new InterpssRuntimeException("IEEEG3 invalid integration flag: " + flag);
		}
		return true;
	}

	private Derivatives derivatives(State s, Machine mach) {
		double temporaryDroop = delta * (s.gate - s.transientLag);
		double error = reference - (mach.getSpeed() - 1.0) - sigma * s.gate - temporaryDroop;
		double servo = tp <= EPS ? error / tg : s.servo;
		double dServo = tp <= EPS ? 0.0 : (error / tg - s.servo) / tp;
		double dGate = Math.max(velClose, Math.min(velOpen, servo));
		if ((s.gate >= pmax && dGate > 0.0) || (s.gate <= pmin && dGate < 0.0)) dGate = 0.0;
		double dTransientLag = (s.gate - s.transientLag) / tr;
		double dTurbineLag = (s.gate - s.turbineLag) / tw_2;
		return new Derivatives(dServo, dGate, dTransientLag, dTurbineLag);
	}

	private State constrain(State s) {
		double gate = Math.max(pmin, Math.min(pmax, s.gate));
		return new State(s.servo, gate, s.transientLag, s.turbineLag);
	}

	private double effectiveServo(State s, Machine mach) {
		if (tp > EPS) return s.servo;
		double temporaryDroop = delta * (s.gate - s.transientLag);
		return (reference - (mach.getSpeed() - 1.0) - sigma * s.gate - temporaryDroop) / tg;
	}

	@Override
	public double getOutput(Machine mach) {
		return getMechanicalPower();
	}

	@Override
	public void setRefPoint(double value) {
		reference = sigma * value / a23;
	}

	public double getServoPosition() { return effectiveServo(state, getMachine()); }
	public double getGatePosition() { return state.gate; }
	public double getPermanentDroopFeedback() { return sigma * state.gate; }
	public double getTemporaryDroopFeedback() { return delta * (state.gate - state.transientLag); }
	public double getMechanicalPower() {
		double leadRatio = t1 / tw_2;
		return a23 * (leadRatio * state.gate + (1.0 - leadRatio) * state.turbineLag);
	}

	private record State(double servo, double gate, double transientLag, double turbineLag) {
		static State zero() { return new State(0.0, 0.0, 0.0, 0.0); }
		State plus(Derivatives d, double dt) {
			return new State(servo + dt * d.servo, gate + dt * d.gate,
					transientLag + dt * d.transientLag, turbineLag + dt * d.turbineLag);
		}
		State plusAverage(Derivatives a, Derivatives b, double dt) {
			return new State(servo + .5 * dt * (a.servo + b.servo),
					gate + .5 * dt * (a.gate + b.gate),
					transientLag + .5 * dt * (a.transientLag + b.transientLag),
					turbineLag + .5 * dt * (a.turbineLag + b.turbineLag));
		}
	}

	private record Derivatives(double servo, double gate, double transientLag, double turbineLag) {
		static Derivatives zero() { return new Derivatives(0.0, 0.0, 0.0, 0.0); }
	}
	    
	    
	
	
    @Override
	public AnController getAnController() {
    	return getClass().getAnnotation(AnController.class);  
    }
    
    @Override
	public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);
    }
    @Override
	public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);
    }

}
