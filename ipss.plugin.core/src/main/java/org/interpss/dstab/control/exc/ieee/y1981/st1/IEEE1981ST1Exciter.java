/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.interpss.dstab.control.exc.ieee.y1981.st1;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.field.func.HighValueFunction;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/*
 * Part-1: Define your controller using CML as usual
 * =================================================
 */
@AnController(
		   input="mach.vt",
		   output="this.gainCustomBlock.y",
		   refPoint="this.gainBlock.u0 - pss.vs * this.vosError - this.vuel * this.uelError + this.trDelayBlock.y + this.washoutBlock.y",
		   display= {}//,
		   //debug = true
)

public class IEEE1981ST1Exciter extends AnnotateExciter {
    @Override public java.util.function.DoubleSupplier bindCompiledParameter(String name)
            throws com.interpss.common.exp.InterpssException {
        return getClass() == IEEE1981ST1Exciter.class
                ? bindPublicDoubleParameter(name) : super.bindCompiledParameter(name);
    }
	private boolean exst1FieldVoltageLimits;
    private boolean initializingFeedbackTap;
	public double k1 = 1.0;/*constant*/
	
	/*
	 * Part-1: Define the blocks
	 * ==============================
	 */
	// transducer block
	 public double tr = 0.02;
     @AnControllerField(
          type= CMLFieldEnum.ControlBlock,
          input="mach.vt",
          parameter={"type.NoLimit", "this.k1", "this.tr"},
          y0="mach.vt",//debug = true,
          initOrderNumber=-1 
          )
     DelayControlBlock trDelayBlock;
	
	
	   //gainBlock----kg1 = 1.0 uses for set the limits
	   public double vimax = 5.30, vimin = -5.11;
	   @AnControllerField(
		   type= CMLFieldEnum.StaticBlock,
		   input="this.refPoint - this.trDelayBlock.y + pss.vs * this.vosError + this.vuel * this.uelError - this.washoutBlock.y",
		   parameter={"type.Limit", "this.k1", "this.vimax", "this.vimin"},
		   y0="this.filterBlock.u0"	)
	   GainBlock gainBlock;

	   /** UEL high-value gate 1, selected by UEL=2. */
	   @AnFunctionField(
		   type=CMLFieldEnum.Function,
		   input={"this.gainBlock.y", "this.vuelGate1"})
	   HighValueFunction uelGate1Function;

	   //filterBlock----(1+sTc)/(1+sTb)
	   public double tc = 1.0, tb = 6.67;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   // Retain the direct block dependency for CML initialization ordering.
		   input="this.gainBlock.y - this.gainBlock.y + this.uelGate1Function.y",
		   parameter={"type.NoLimit", "this.k1", "this.tc", "this.tb"},
		   y0="this.filterBlock1.u0"  )
	   FilterControlBlock filterBlock;

	   // second compensating lead-lag, (1+sTc1)/(1+sTb1)
	   public double tc1 = 0.0, tb1 = 0.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.filterBlock.y",
		   parameter={"type.NoLimit", "this.k1", "this.tc1", "this.tb1"},
		   y0="this.kaDelayBlock.u0"  )
	   FilterControlBlock filterBlock1;

	   //kaDelayBlock----Ka/(1+sTa) with limits
	   public double ka = 300.0, ta = 0.01, vamax = 999.0, vamin = -999.0;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.filterBlock1.y",
		   parameter={"type.NonWindup", "this.ka", "this.ta", "this.vamax", "this.vamin"},
		   y0="this.gainCustomBlock.u0 - pss.vs * this.vosOutput"  )
	   DelayControlBlock kaDelayBlock;
	   
	   
	   //washoutBlock----sKf/(1+sTf)
	   public double kf = 1, tf = 0.01, k = kf/tf;
	   @AnControllerField(
	      type= CMLFieldEnum.ControlBlock,
	      input="this.rateFeedbackInputBlock.y",
	      parameter={"type.NoLimit", "this.k", "this.tf"},
	      feedback = true	)
	   WashoutControlBlock washoutBlock;

    /** ESST1A feedback is taken after UEL/OEL, before the final field ceiling. */
    @AnControllerField(
        type=CMLFieldEnum.StaticBlock,
        // Retain the established output dependency for initialization ordering.
        input="this.gainCustomBlock.y",
        parameter={"type.NoLimit", "this.k1"},
        y0="mach.efd")
    public ICMLStaticBlock rateFeedbackInputBlock = new GainBlock() {
        @Override public double getY() {
            return exst1FieldVoltageLimits || initializingFeedbackTap
                    ? super.getY() : getPreFieldVoltageSignal();
        }
    };
	   /*
	   public double vrmax = 5.30, vrmin = -5.11,kc =0;
	   @AnControllerField(
		   type= CMLFieldEnum.StaticBlock,
		   input="this.kaDelayBlock.y",
		   parameter={"type.Limit", "this.k1", "this.vrmax", "this.vrmin"},
		   y0="mach.efd",
		   debug = true)
	   GainBlock gainCustomBlock;
	   */
   
	public double kg = 1.0, kc = 0.0, vrmax = 5.30, vrmin = -5.11;
	public double klr = 0.0, ilr = 0.0;
	/** External UEL signal; zero is the absent value for error summation. */
	public double vuel = 0.0;
	/** Nonbinding input for gate 1 unless UEL=2. */
	public double vuelGate1 = Double.NEGATIVE_INFINITY;
	/** External OEL ceiling; positive infinity denotes no connected OEL. */
	public double voel = Double.POSITIVE_INFINITY;
	public double uelError = 1.0;
	public double vosError = 1.0, vosOutput = 0.0;
	public int uel = 1, vos = 1;
	   @AnControllerField(
	      type=CMLFieldEnum.StaticBlock,
	      input="this.kaDelayBlock.y + pss.vs * this.vosOutput",
	      y0="mach.efd"  )
	   // extend the GainBlock to reuse its functionality
	   public ICMLStaticBlock gainCustomBlock = new GainBlock() {
		  @Override
		  public boolean initStateY0(double y0) {
			  // at the initial point, set the gain block gain
			  super.k = kg;
			  return super.initStateY0(y0 + fieldCurrentLimiter());
		  }

		  @Override
		  public double getY() {
			  double vmax = calUpperLimit();
			  double vmin = calLowerLimit();
			  double y = super.getY() - fieldCurrentLimiter();
			  // PowerWorld/WECC ESST1A: UEL=3 is the second high-value gate,
			  // followed by the OEL low-value gate and the terminal-voltage limits.
			  if (uel == 3) {
				  y = Math.max(y, vuel);
			  }
			  y = Math.min(y, voel);
			  //System.out.println("Efd max, min, y ="+vmax+","+vmin+","+y);
			  if(y > vmax) {
				  return vmax;
			  }else if(y < vmin) {
				  return vmin;
			  }else {
				  return y;
			  }

		  }

		  private double calUpperLimit() {
			  return getFieldVoltageUpperLimit();
		  }

		  private double calLowerLimit() {
			  return getFieldVoltageLowerLimit();
		  }

		  private double fieldCurrentLimiter() {
			  double ifd = observedFieldCurrent();
			  return Math.max(0.0, klr * (ifd - ilr));
		  }
	   };
	   
	   


    // UI Editor panel
  //  private static NBIEEE1981ST1ExciterEditPanel _editPanel = new NBIEEE1981ST1ExciterEditPanel();

/*
 * Part-2: Define the contructors
 * ==============================
 */

    /**
     * Default Constructor
     *
     */
    public IEEE1981ST1Exciter() {
	this("id", "name", "caty");
        this.setName("IEEE1981ST1");
        this.setCategory("IEEE");
    }

     /**
     * Constructor
     *
     * @param id exciter id
     * @param name exciter name
     * @param caty exciter category
     */
    public IEEE1981ST1Exciter(String id, String name, String caty) {
        super(id, name, caty);
        // _data is defined in the parent class. your need to initialize with
        // the correct type, the data object to be edited
        _data = new IEEE1981ST1ExciterData();
    }

/*
 * Part-3: Define and init the data object
 * =======================================
 */

    /**
     * Get the plugin data object
     *
     * @return the data object
     */
    public IEEE1981ST1ExciterData getData() {
        return (IEEE1981ST1ExciterData)_data;
    }

    /**
     *  Init the controller states using the data object
     *
     *  @param bus the bus object where the machine object is connected
     *  @param mach the machine object of this controller object
     *  @param msg the SessionMsg object
     */
    @Override
    public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
        // pass the plugin data object values to the controller
		this.uel = getData().getUel();
		this.vos = getData().getVos();
		this.uelError = this.uel < 2 ? 1.0 : 0.0;
		this.vuelGate1 = this.uel == 2 ? this.vuel : Double.NEGATIVE_INFINITY;
		this.vosError = this.vos == 1 ? 1.0 : 0.0;
		this.vosOutput = this.vos == 2 ? 1.0 : 0.0;
		this.tr = getData().getTr();
        this.vimax = getData().getVimax();
        this.vimin = getData().getVimin();
        this.tc = getData().getTc();
        this.tb = getData().getTb();
		this.tc1 = getData().getTc1();
		this.tb1 = getData().getTb1();
        this.ka = getData().getKa();
        this.ta = getData().getTa();
		this.vamax = getData().getVamax();
		this.vamin = getData().getVamin();
        this.vrmax = getData().getVrmax();
        this.vrmin = getData().getVrmin();
        this.kc = getData().getKc();
		this.exst1FieldVoltageLimits = getData().getFieldVoltageLimitConvention()
				== IEEE1981ST1ExciterData.FieldVoltageLimitConvention.EXST1;
        this.kf = getData().getKf();
        this.tf = getData().getTf();
		this.klr = getData().getKlr();
		this.ilr = getData().getIlr();

		if (this.vimax < this.vimin) {
			double swap = this.vimax; this.vimax = this.vimin; this.vimin = swap;
		}
		if (this.vamax < this.vamin) {
			double swap = this.vamax; this.vamax = this.vamin; this.vamin = swap;
		}
		if (this.vrmax < this.vrmin) {
			double swap = this.vrmax; this.vrmax = this.vrmin; this.vrmin = swap;
		}
		double ifd0 = mach.calculateIfd(MachineIfdBase.EXCITER);
		double lr0 = Math.max(0.0, this.klr * (ifd0 - this.ilr));
		double va0 = mach.getEfd() + lr0;
		this.vamax = Math.max(this.vamax, va0);
		this.vamin = Math.min(this.vamin, va0);
		double vi0 = Math.abs(this.ka) > 1.0e-9 ? va0 / this.ka : va0;
		this.vimax = Math.max(this.vimax, vi0);
		this.vimin = Math.min(this.vimin, vi0);
		double vt0 = bus.getVoltageMag();
		if (vt0 > 0.0) {
			this.vrmax = Math.max(this.vrmax, (mach.getEfd() + this.kc * ifd0) / vt0);
			this.vrmin = Math.min(this.vrmin,
					(mach.getEfd() + (exst1FieldVoltageLimits ? this.kc * ifd0 : 0.0)) / vt0);
		}
        
		this.k = this.tf == 0.0 ? 0.0 : this.kf / this.tf;
        // always add the following statement
        // The regulator is solved backwards from EFD. During this pass use
        // the declared y0, not an as-yet uninitialized upstream state.
        initializingFeedbackTap=true;
        try { return super.initStates(bus, mach); }
        finally { initializingFeedbackTap=false; }
    }

	/** Set the external under-excitation limiter signal. */
	public void setVuel(double value) {
		this.vuel = value;
		this.vuelGate1 = this.uel == 2 ? value : Double.NEGATIVE_INFINITY;
	}

	/** Set the external over-excitation limiter ceiling. */
	public void setVoel(double value) {
		this.voel = value;
	}

	/** Disconnect the external over-excitation limiter. */
	public void clearVoel() {
		this.voel = Double.POSITIVE_INFINITY;
	}

	public double getVoltageErrorOutput() {
		return signal("this.gainBlock.y");
	}

	public double getUelGate1Output() {
		return signal("this.uelGate1Function.y");
	}

	public double getRegulatorOutput() {
		return signal("this.kaDelayBlock.y");
	}

	public double getPostRegulatorSignal() {
		double pss = getMachine().getStabilizer() == null
				? 0.0 : getMachine().getStabilizer().getOutput(getMachine());
		return getRegulatorOutput() + pss * this.vosOutput;
	}

	/** Whether the CML runtime blocks have completed initialization. */
	public boolean hasInitializedBlocks() {
		return getFieldWrapperList() != null && !getFieldWrapperList().isEmpty();
	}

    /**
     * Read-only ESST1A five-state observation in its published allocation order.
     * Lead-lag and washout storage are time-scaled at observation only; the existing generic
     * named-state API and integration coordinates remain unchanged.
     * Zero-time transducer/regulator observations use the algebraic output,
     * since their dormant integration storage does not follow the input.
     */
    public Map<String,Double> getNativeStateSnapshot() {
        if(!hasInitializedBlocks() || exst1FieldVoltageLimits)
            throw new IllegalStateException("Five-state observation requires initialized ESST1A blocks");
        // CML may keep live blocks in runtime wrappers rather than assigning
        // the symbolic Java fields. Observe the registered runtime blocks.
        var blocks=getNamedStateBlocks();
        var first=(FilterControlBlock)blocks.get("filterBlock");
        var second=(FilterControlBlock)blocks.get("filterBlock1");
        var transducer=(DelayControlBlock)blocks.get("trDelayBlock");
        var regulator=(DelayControlBlock)blocks.get("kaDelayBlock");
        Map<String,Double> states=new LinkedHashMap<>();
        states.put("Measured voltage",transducer.getT()>0.0
                ? transducer.getStateX() : getSensedVoltage());
        states.put("First lead-lag",first.getT2()*first.getStateX());
        states.put("Second lead-lag",second.getT2()*second.getStateX());
        states.put("Regulator output",regulator.getT()>0.0
                ? regulator.getStateX() : getRegulatorOutput());
        var washout=(WashoutControlBlock)blocks.get("washoutBlock");
        states.put("Feedback",washout.getT()*washout.getStateX());
        return Collections.unmodifiableMap(states);
    }

	/** Terminal-voltage transducer output. */
	public double getSensedVoltage() { return signal("this.trDelayBlock.y"); }

	/** Voltage-error sum before the input limiter. */
	public double getVoltageErrorInput() {
		return getRefPoint() - getSensedVoltage()
				+ stabilizerSignal() * this.vosError + this.vuel * this.uelError
				- getRateFeedback();
	}

	/** Voltage-error signal after Vi limits and the first UEL high-value gate. */
	public double getLimitedVoltageError() { return signal("this.uelGate1Function.y"); }

	/** Output of the first lead-lag compensator. */
	public double getLeadLagOutput() { return signal("this.filterBlock.y"); }

	/** Output of the second lead-lag compensator. */
	public double getLeadLag1Output() { return signal("this.filterBlock1.y"); }

	/** Regulator output after subtracting the field-current limiter and adding VOS=2. */
	public double getPostFieldCurrentLimiterSignal() {
		return getRegulatorOutput() - fieldCurrentLimiterSignal()
				+ stabilizerSignal() * this.vosOutput;
	}

	/** Signal after the UEL=3 high-value and OEL low-value gates, before EFD limits. */
	public double getPreFieldVoltageSignal() {
		double value = getPostFieldCurrentLimiterSignal();
		if (uel == 3) value = Math.max(value, vuel);
		return Math.min(value, voel);
	}

	/**
	 * Field output after a local state advance and before refreshing algebraic
	 * block inputs. The zero-Ta regulator retains its last evaluated input at
	 * this stage; a dynamic regulator exposes its newly advanced state.
	 */
	public double getStaggeredFieldVoltage() {
		double value = kg * (getRegulatorOutput() + stabilizerSignal() * vosOutput)
				- fieldCurrentLimiterSignal();
		if (uel == 3) value = Math.max(value, vuel);
		value = Math.min(value, voel);
		double upper = getFieldVoltageUpperLimit();
		double lower = getFieldVoltageLowerLimit();
		if (value > upper) return upper;
		if (value < lower) return lower;
		return value;
	}

	/** Rate-feedback washout output. */
	public double getRateFeedback() { return signal("this.washoutBlock.y"); }

	/** PTI/PowerWorld EFD upper bound, including terminal voltage and field current. */
	public double getFieldVoltageUpperLimit() {
		Machine mach = getMachine();
		return mach.getDStabBus().getVoltageMag() * vrmax
				- kc * observedFieldCurrent();
	}

	/** Source-model lower bound: EXST1 also subtracts Kc*Ifd here. */
	public double getFieldVoltageLowerLimit() {
		Machine mach = getMachine();
		return mach.getDStabBus().getVoltageMag() * vrmin
				- (exst1FieldVoltageLimits ? kc * observedFieldCurrent() : 0.0);
	}

	private double stabilizerSignal() {
		return getMachine().getStabilizer() == null
				? 0.0 : getMachine().getStabilizer().getOutput(getMachine());
	}

	private double fieldCurrentLimiterSignal() {
		double ifd = observedFieldCurrent();
		return Math.max(0.0, this.klr * (ifd - this.ilr));
	}

    private int machineObservationDepth;
    private boolean fieldCurrentObserved;
    private double observedCurrent;
    public void beginFixedMachineObservation() {
        if(machineObservationDepth++==0)fieldCurrentObserved=false;
    }
    public void endFixedMachineObservation() {
        if(machineObservationDepth<=0)throw new IllegalStateException("Unbalanced machine observation");
        if(--machineObservationDepth==0)fieldCurrentObserved=false;
    }
    private double observedFieldCurrent() {
        if(machineObservationDepth==0)return getMachine().calculateIfd(MachineIfdBase.EXCITER);
        if(!fieldCurrentObserved) {
            observedCurrent=getMachine().calculateIfd(MachineIfdBase.EXCITER);fieldCurrentObserved=true;
        }
        return observedCurrent;
    }
    @Override public boolean eulerStep(boolean predictor,double dt,Machine machine) {
        if(directEvaluation==null)return super.eulerStep(predictor,dt,machine);
        beginFixedMachineObservation();
        try {
            if(predictor&&dt==0.) {directEvaluation.evaluate();return true;}
            return super.eulerStep(predictor,dt,machine);
        }
        finally {endFixedMachineObservation();}
    }
    private St1DirectEvaluation directEvaluation;
    public void enableDirectEvaluationExperiment() {
        if (getClass()!=IEEE1981ST1Exciter.class) throw new IllegalStateException("Exact ST1 class required");
        directEvaluation=new St1DirectEvaluation(this);
    }
    /** Advance the bound local states while the caller holds physical inputs fixed. */
    public double advanceDirectLocalStore(double dt,boolean discontinuity,boolean wasActive,
            boolean cachePss,double[] history,double[] start,double[] rates) {
        beginFixedMachineObservation();
        try {return directEvaluation.advanceLocalStore(dt,discontinuity,wasActive,cachePss,history,start,rates);}
        finally {endFixedMachineObservation();}
    }
    public long getPrimitiveLocalAdvanceCount() {
        return directEvaluation==null?0:directEvaluation.primitiveLocalAdvanceCount;
    }
    public void evaluateDirectExperiment() {
        beginFixedMachineObservation();
        try {directEvaluation.evaluate();}finally {endFixedMachineObservation();}
    }
	private double signal(String fieldName) {
        if (directEvaluation!=null) return directEvaluation.signal(fieldName);
		try {
			return getFieldVaule(fieldName);
		} catch (Exception ex) {
			throw new IllegalStateException("Cannot read ESST1A signal " + fieldName, ex);
		}
	}

/*
 * Part-4: Define the pluin data object edtior
 * ===========================================
 */

    /**
     * Get the editor panel for controller data editing
     *
     * @return the editor panel object
     */
//    @Override
//    public Object getEditPanel() {
//        _editPanel.init(this);
//        return _editPanel;
//    }

/*
 * do not modify the following part
 */
    @Override
	public AnController getAnController() {
    	return getClass().getAnnotation(AnController.class);  }
    @Override
	public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);   }
    @Override
	public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);    }
}
