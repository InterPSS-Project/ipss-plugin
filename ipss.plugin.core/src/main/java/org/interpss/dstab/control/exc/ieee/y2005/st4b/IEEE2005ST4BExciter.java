package org.interpss.dstab.control.exc.ieee.y2005.st4b;

import java.lang.reflect.Field;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.control.exc.UnderExcitationLimiterTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter;
import org.interpss.dstab.control.util.PhysicalStepTrial;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.func.LowValueFunction;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;


@AnController(
		   input="mach.vt",
		   output="this.customBlock.y",
		   refPoint="this.vrPIBlock.u0 - pss.vs + this.trDelayBlock.y - this.vuel",
		   display= {})
public class IEEE2005ST4BExciter extends AnnotateExciter
        implements IntegrationStepAware, UnderExcitationLimiterTarget, PhysicalStepTrial {
    private static final Logger log = LoggerFactory.getLogger(IEEE2005ST4BExciter.class);
	public double k1 = 1.0;/*constant*/
	/** External UEL contribution; zero is the nonbinding/absent value. */
	public double vuel = 0.0;
	/** External OEL ceiling; positive infinity is the nonbinding/absent value. */
	public double voel = Double.POSITIVE_INFINITY;

	/*
	 * Part-1: Define the blocks
	 * ==============================
	 */

	// transducer block
	 public double tr = 0.04;
     @AnControllerField(
          type= CMLFieldEnum.ControlBlock,
          input="mach.vt",
          parameter={"type.NoLimit", "this.k1", "this.tr"},
          y0="mach.vt",//debug = true,
         initOrderNumber=-1
          )
       DelayControlBlock trDelayBlock;

	   //kpr kir-- PI  block
	   public double Kpr =1, Kir=1, vrmax =99, vrmin =-99;
	   public double integralStateLimits = 0.0;
	   @AnControllerField(
			   type =CMLFieldEnum.ControlBlock,
			   input="this.refPoint - this.trDelayBlock.y + pss.vs + this.vuel",
			   parameter={"this.Kpr", "this.Kir","this.vrmax","this.vrmin", "this.integralStateLimits"},
			   y0="this.taDelayBlock.u0"
			   )
	   Esst4bPIControlBlock vrPIBlock;

	   //taDelayBlock----Ka/(1+sTa) with limits
	   public double ka = 1.0, ta = 0.01;
	   @AnControllerField(
		   type=CMLFieldEnum.ControlBlock,
		   input="this.vrPIBlock.y",
		   parameter={"type.NoLimit", "this.ka", "this.ta"},
		   y0="this.vmPIBlock.u0 + this.kgGainBlock.y"//,
		   //debug = true
		   )//initOrderNumber = 3
	   DelayControlBlock taDelayBlock;


     //KG feedback, gain with upper limit

	   public double kg = 1.0, vgmax = Double.POSITIVE_INFINITY, vgmin = -9999.0;
	   @AnControllerField(
		   type= CMLFieldEnum.StaticBlock,
		   input="this.customBlock.y",
		   parameter={"type.Limit", "this.kg", "this.vgmax", "this.vgmin"},
		   feedback = true//,
		   //debug=true
		   )
	   GainBlock kgGainBlock;

	  //KPM KIM---PI CONTROL NON-Windup limits
	  public double Kpm = 10.0, Kim = 0.01,vmmax = 9,vmmin = -9.0;
	  @AnControllerField(
			   type =CMLFieldEnum.ControlBlock,
			   input="this.taDelayBlock.y - this.kgGainBlock.y",
			   parameter={"this.Kpm", "this.Kim","this.vmmax","this.vmmin", "this.integralStateLimits"},
			   y0="this.customBlock.u0"
			   )
	   Esst4bPIControlBlock vmPIBlock;

	   // IEEE ST4B over-excitation limiter low-value gate.
	   @AnFunctionField(
		   type=CMLFieldEnum.Function,
		   input={"this.vmPIBlock.y", "this.voel"},
		   y0="this.customBlock.u0")
	   LowValueFunction voelGate;


	   public double kc = 1.0, kp = 2.0, ki = 1.0, vbmax = 10.0, angKp_deg =0.0, xl =1.0;
	   @AnControllerField(
	      type= CMLFieldEnum.StaticBlock,
	      // Preserve the explicit PI dependency for the CML initialization graph;
	      // the cancelling terms do not alter the runtime low-value-gate signal.
	      input= "this.vmPIBlock.y - this.vmPIBlock.y + this.voelGate.y",
	      y0="mach.efd"
	      )
	   public ICMLStaticBlock customBlock = new CMLStaticBlockAdapter() {
	      private double VB = 0.0;

	      @Override
	      public boolean initStateY0(double y0) {
              VB = calcBridgeVoltage(getMachine());
              if(VB ==0.0){
                  log.error("Error: VB of IEEE 2005 ST4B exciter is 0 for initialization, @ "+getMachine().getId());
                  return false;
              }
	          this.u = y0/VB;
	          //System.out.println("Y0, VB, u ="+y0+","+VB+","+u);
	          return true;
	      }
	      @Override
	      public double getU0(){
              return this.u;
	      }


	      @Override
	      public void eulerStep1(double u, double dt) {
	         this.u = u;
	      }
	      @Override
	      public void eulerStep2(double u, double dt) {
	         this.u = u;
	      }
	      @Override
	      public double getY() {
             if(bridgeScopeDepth>0) {
                 if(!bridgeCached) {fixedBridge=calcBridgeVoltage(getMachine());bridgeCached=true;}
                 VB=fixedBridge;
             } else VB=calcBridgeVoltage(getMachine());
             return this.u*VB;
	      }

	   };




	/*
	 * Part-2: Define the contructors
	 * ==============================
	 */

	    /**
	     * Default Constructor
	     *
	     */
	    public IEEE2005ST4BExciter() {
		this("id", "name", "caty");
	        this.setName("IEEE2005ST4B");
	        this.setCategory("IEEE");
	    }

	     /**
	     * Constructor
	     *
	     * @param id exciter id
	     * @param name exciter name
	     * @param caty exciter category
	     */
	    public IEEE2005ST4BExciter(String id, String name, String caty) {
	        super(id, name, caty);
	        // _data is defined in the parent class. your need to initialize with
	        // the correct type, the data object to be edited
	        _data = new IEEE2005ST4BExciterData();
	    }

    private St4DirectEvaluation directEvaluation;
    /** Release bindings before reinitializing or changing the graph. */
    public void clearDirectEvaluation(){directEvaluation=null;}
    private int bridgeScopeDepth;
    private boolean bridgeCached;
    private double fixedBridge;
    public void enableDirectEvaluationExperiment() {
        if(getClass()!=IEEE2005ST4BExciter.class)throw new IllegalStateException("Exact ST4 required");
        directEvaluation=new St4DirectEvaluation(this);
    }
    /**
     * Evaluate a zero-time predictor and its output in one fixed-input scope.
     * The caller must hold any attached standard stabilizer output fixed until
     * this method returns. All state and derivative work is retained. Custom
     * or observed devices and unsettled feedback use the ordinary output path.
     */
    public double evaluateFixedInputEndpoint(Machine machine,boolean fixedPss) {
        if(directEvaluation==null || !directEvaluation.endpointEligible(machine,fixedPss)) {
            if(!nextStep(0.,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,machine,0))
                throw new IllegalStateException("Exciter endpoint evaluation failed");
            return getOutput(machine);
        }
        if(directEvaluation.scopedEndpoint)throw new IllegalStateException("Nested fixed endpoint");
        beginBridgeScope();directEvaluation.scopedEndpoint=true;directEvaluation.preparedStable=false;
        try {
            if(!nextStep(0.,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,machine,0))
                throw new IllegalStateException("Exciter endpoint evaluation failed");
            return directEvaluation.preparedEndpointOutput();
        } finally {
            directEvaluation.scopedEndpoint=false;directEvaluation.preparedStable=false;endBridgeScope();
        }
    }
    /** Number of settled endpoint outputs reused since this graph was built. */
    public long getEndpointOutputReuseCount(){return directEvaluation==null?0:directEvaluation.endpointOutputReuses;}
    private void beginBridgeScope() {if(bridgeScopeDepth++==0)bridgeCached=false;}
    private void endBridgeScope() {if(--bridgeScopeDepth==0)bridgeCached=false;}
    private DelayControlBlock feedbackTr,feedbackTa;
    private Esst4bPIControlBlock feedbackVr,feedbackVm;
    private GainBlock feedbackKg;

    @Override public void invalidateRuntimePlan() {
        clearDirectEvaluation();
        feedbackTr=null;feedbackTa=null;feedbackVr=null;feedbackVm=null;feedbackKg=null;
        super.invalidateRuntimePlan();
    }
    private void bindInnerFeedback() {
        // Nonpublic annotated Java fields can remain null: the initialized
        // blocks live in the CML wrappers. Bind those actual storage objects.
        for(var wrapper:getFieldWrapperList())switch(wrapper.getFieldName()) {
            case "trDelayBlock" -> feedbackTr=(DelayControlBlock)wrapper.getField();
            case "taDelayBlock" -> feedbackTa=(DelayControlBlock)wrapper.getField();
            case "vrPIBlock" -> feedbackVr=(Esst4bPIControlBlock)wrapper.getField();
            case "vmPIBlock" -> feedbackVm=(Esst4bPIControlBlock)wrapper.getField();
            case "kgGainBlock" -> feedbackKg=(GainBlock)wrapper.getField();
            default -> { }
        }
    }
    /** Solve the proportional inner feedback at frozen integral coordinates.
     * A forward sweep has slope -VB*Kpm*Kg and need not be contractive. The
     * actual clipped scalar residual is monotone for nonnegative gains, so
     * bracketing between the physical output limits gives its unique root.
     * Input refreshes never advance state or overwrite predictor derivatives. */
    boolean refreshInnerFeedback(Machine machine) {
        if(feedbackTr==null && getClass()==IEEE2005ST4BExciter.class)bindInnerFeedback();
        if(getClass()!=IEEE2005ST4BExciter.class || feedbackTr==null || feedbackTa==null
                || feedbackVr==null || feedbackVm==null || feedbackKg==null || feedbackVm.getKp()<0 || feedbackKg.getK()<0)return false;
        double terminal=machine instanceof com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider provider
                ?provider.getCmlMachineVoltage():getDStabBus().getVoltageMag()/machine.getVMultiFactor();
        feedbackTr.setStageInput(terminal);
        double stabilizer=machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);
        feedbackVr.setStageInput(getRefPoint()-feedbackTr.getY()+stabilizer+vuel);
        feedbackTa.setStageInput(feedbackVr.getY());
        double amplifier=feedbackTa.getY();
        double savedInput=customBlock.getU();customBlock.eulerStep2(1,0);
        double bridge=customBlock.getY();customBlock.eulerStep2(savedInput,0);
        if(!Double.isFinite(bridge)||bridge<0) {
            log.error("ST4 bridge unavailable: id={}, bridge={}, Ifd={}, Efd={}, current={}",getId(),bridge,
                    machine.calculateIfd(MachineIfdBase.EXCITER),machine.getEfd(),machine.getIxy());return false;
        }
        double output=solveFieldOutput(amplifier,bridge);
        if(Double.isNaN(output))return false;
        feedbackKg.eulerStep2(output,0);
        feedbackVm.setStageInput(amplifier-feedbackKg.getY());
        customBlock.eulerStep2(Math.min(feedbackVm.getY(),voel),0);
        boolean valid=Double.isFinite(customBlock.getY());
        if(!valid)log.error("ST4 root unavailable: id={}, amplifier={}, integral={}, vr={}, ta={}, bridge={}",
                getId(),amplifier,feedbackVm.getStateX(),feedbackVr.getStateX(),feedbackTa.getStateX(),bridge);
        return valid;
    }

    /**
     * Evaluate the algebraic field output at a solved network boundary while
     * holding the transducer, regulator and stabilizer states at their accepted
     * values. The caller supplies system-base terminal phasors, the compensated
     * machine-base voltage magnitude, and the exciter-base field current.
     * This observation leaves all stage inputs and integration history intact.
     * Returns NaN when the initialized feedback graph cannot be observed.
     */
    public double observeFieldVoltage(Complex terminalVoltage, Complex terminalCurrent,
            double compensatedVoltage, double fieldCurrent, double stabilizerOutput) {
        return observeEventFieldVoltage(terminalVoltage, terminalCurrent, compensatedVoltage,
                fieldCurrent, stabilizerOutput, Double.NaN);
    }

    /**
     * Observe the post-switch field voltage at the same timestamp as the supplied
     * pre-switch field voltage. With a dynamic amplifier, the native event
     * boundary holds field feedback at that accepted pre-switch value while
     * reevaluating the bridge from the post-switch electrical inputs.
     */
    public double observeEventFieldVoltage(Complex terminalVoltage, Complex terminalCurrent,
            double compensatedVoltage, double fieldCurrent, double stabilizerOutput,
            double preEventFieldVoltage) {
        if(getClass()!=IEEE2005ST4BExciter.class || feedbackTr==null || feedbackTa==null
                || feedbackVr==null || feedbackVm==null || feedbackKg==null
                || feedbackVm.getKp()<0 || feedbackKg.getK()<0)return Double.NaN;
        double sensed = feedbackTr.getT() > 0 ? feedbackTr.getStateX()
                : feedbackTr.getK() * compensatedVoltage;
        double error = getRefPoint() - sensed + stabilizerOutput + vuel;
        double regulator = feedbackVr.getLimit().limit(feedbackVr.getStateX() + feedbackVr.getKp() * error);
        double amplifier = feedbackTa.getT() > 0 ? feedbackTa.getStateX() : feedbackTa.getK() * regulator;
        double sourceVoltage = calcCompoundSourceVoltage(getMachine(), terminalVoltage, terminalCurrent);
        double bridge = calcBridgeVoltage(sourceVoltage, fieldCurrent);
        if (feedbackTa.getT() > 0.0 && Double.isFinite(preEventFieldVoltage)) {
            double feedback = feedbackKg.getLimit().limit(feedbackKg.getK() * preEventFieldVoltage);
            double modulation = feedbackVm.getLimit().limit(feedbackVm.getStateX()
                    + feedbackVm.getKp() * (amplifier - feedback));
            return bridge * Math.min(modulation, voel);
        }
        return solveFieldOutput(amplifier, bridge);
    }

    private double solveFieldOutput(double amplifier,double bridge) {
        double pGain=feedbackVm.getKp(),fieldGain=feedbackKg.getK();
        double min=feedbackVm.getLimit().getMin(),max=feedbackVm.getLimit().getMax();
        double gmin=feedbackKg.getLimit().getMin(),gmax=feedbackKg.getLimit().getMax();
        double lower=bridge*Math.min(min,voel),upper=bridge*Math.min(max,voel);
        if(!Double.isFinite(lower)||!Double.isFinite(upper)||lower>upper)return Double.NaN;
        double root=Double.NaN;
        // The three feedback segments are affine. Check their closed roots
        // against the complete clipped equation before using the bracketed
        // fallback at a rounded active-boundary intersection.
        for(int segment=0;segment<3;segment++) {
            double slope=segment==0?fieldGain:0,offset=segment==0?0:segment==1?gmin:gmax;
            double candidate=bridge*(feedbackVm.getStateX()+pGain*(amplifier-offset))/(1+bridge*pGain*slope);
            candidate=Math.max(lower,Math.min(upper,candidate));
            double feedback=Math.max(gmin,Math.min(gmax,fieldGain*candidate));
            double regulator=Math.max(min,Math.min(max,feedbackVm.getStateX()+pGain*(amplifier-feedback)));
            if(Double.isFinite(candidate)&&Math.abs(candidate-bridge*Math.min(regulator,voel))<=1e-14*(1+Math.abs(candidate))) {
                root=candidate;break;
            }
        }
        if(Double.isNaN(root))for(int iteration=0;iteration<80;iteration++) {
            double middle=lower+.5*(upper-lower);
            double feedback=Math.max(gmin,Math.min(gmax,fieldGain*middle));
            double regulator=Math.max(min,Math.min(max,feedbackVm.getStateX()+pGain*(amplifier-feedback)));
            double residual=middle-bridge*Math.min(regulator,voel);
            if(residual>0)upper=middle;else lower=middle;
            if(upper-lower<=1e-14*(1+Math.abs(middle)))break;
        }
        return Double.isFinite(root)?root:lower+.5*(upper-lower);
    }

    private LocalStaggeredStep localStaggeredStep;

    /**
     * Bind the native rate-history path to the final initialized blocks. The
     * qualified path has an algebraic sensor, a dynamic amplifier and no field
     * feedback or external limiter contribution. A dynamic inner PI uses
     * the same accepted-boundary rate history as the outer PI.
     */
    public boolean prepareLocalStaggeredIntegration() {
        if (getClass() != IEEE2005ST4BExciter.class) return false;
        bindInnerFeedback();
        if (feedbackTr == null || feedbackTa == null || feedbackVr == null
                || feedbackVm == null || feedbackKg == null || integrationStep <= 0.
                || feedbackTr.getT() != 0. || feedbackTa.getT() <= 0.
                || feedbackKg.getK() != 0.
                || (feedbackVm.getKi() != 0. && integralStateLimits != 1.0)
                || feedbackVm.getKp() < 0. || vuel != 0. || voel != Double.POSITIVE_INFINITY)
            return false;
        localStaggeredStep = new LocalStaggeredStep();
        return true;
    }

    @Override public boolean requiresPhysicalStepTrial() { return localStaggeredStep != null; }

    public void captureLocalStaggeredPreEvent(double time) {
        var step = localStaggeredStep;
        if (step == null || step.pending) throw new IllegalStateException("Invalid ESST4B pre-event boundary");
        if (step.eventTime == time) return;
        if (!refreshInnerFeedback(getMachine())) throw new IllegalStateException("ESST4B pre-event input unavailable");
        step.leftVrRate = regulatorRate();
        step.leftTaRate = amplifierRate();
        step.leftVmRate = innerRegulatorRate();
        step.eventTime = time;
        step.eventConfirmed = false;
    }

    public void confirmLocalStaggeredPostEvent(double time) {
        var step = localStaggeredStep;
        // A host may become active during the event and have no left sample.
        // Its first live step then starts without event-rate history.
        if (step != null && step.eventTime == time) step.eventConfirmed = true;
    }

    @Override public void beginPhysicalStepTrial(double time, double dt) {
        var step = localStaggeredStep;
        if (step == null || step.pending || !Double.isFinite(time) || !Double.isFinite(dt) || dt <= 0.)
            throw new IllegalStateException("Invalid ESST4B physical interval");
        step.save();
        step.dt = dt;
        step.end = time + dt;
        step.contiguous = step.acceptedEnd == time;
        step.useEvent = step.eventConfirmed && step.eventTime == time;
        // Native histories are sampled on the nominal grid. For a very short
        // event fragment use ordinary integration for that event segment.
        step.segment = step.useEvent ? dt >= .99 * integrationStep : step.staggeredSegment;
        step.staggered = step.segment && vuel == 0. && voel == Double.POSITIVE_INFINITY;
        step.pending = true;
    }

    @Override public void publishPhysicalStepTrial() {
        // The machine's usual exciter call publishes the predictor field voltage.
    }

    @Override public void validatePhysicalStepAcceptance() {
        var step = localStaggeredStep;
        if (step == null || !step.pending || !step.corrected)
            throw new IllegalStateException("ESST4B did not finish its physical interval");
    }

    @Override public void acceptPhysicalStepTrial() {
        var step = localStaggeredStep;
        step.acceptedEnd = step.end;
        step.previousDt = step.dt;
        step.staggeredSegment = step.segment;
        step.historyReady = step.staggered;
        step.previousVrRate = step.trialVrRate;
        step.previousTaRate = step.trialTaRate;
        step.previousVmRate = step.trialVmRate;
        if (step.useEvent) step.eventConfirmed = false;
        step.clearTrial();
    }

    @Override public void discardPhysicalStepTrial() {
        var step = localStaggeredStep;
        if (step == null || !step.pending) return;
        step.restore();
        step.clearTrial();
    }

    private double regulatorRate() {
        return feedbackVr.isIntegratorFrozen() ? 0. : feedbackVr.getKi() * feedbackVr.getU();
    }

    private double amplifierRate() {
        return (feedbackTa.getK() * feedbackVr.getY() - feedbackTa.getStateX()) / feedbackTa.getT();
    }

    private double innerRegulatorRate() {
        return feedbackVm.isIntegratorFrozen() ? 0. : feedbackVm.getKi() * feedbackVm.getU();
    }

    private void advanceStaggeredPredictor(LocalStaggeredStep step) {
        double vrRate = regulatorRate();
        double taRate = amplifierRate();
        double vmRate = innerRegulatorRate();
        if (step.useEvent) {
            vrRate = .5 * (vrRate + step.leftVrRate);
            taRate = .5 * (taRate + step.leftTaRate);
            vmRate = .5 * (vmRate + step.leftVmRate);
        }
        double ratio = step.historyReady && step.contiguous ? step.dt / step.previousDt : 0.;
        double weight = ratio == 0. ? 0. : step.useEvent ? .5 : ratio <= 2. ? .5 * ratio : 0.;
        feedbackVr.setStateX(feedbackVr.projectIntegralState(feedbackVr.getStateX()
                + step.dt * (vrRate + weight * (vrRate - step.previousVrRate))));
        feedbackTa.setStateX(feedbackTa.getStateX()
                + step.dt * (taRate + weight * (taRate - step.previousTaRate)));
        if (feedbackVm.getKi() != 0.)
            feedbackVm.setStateX(feedbackVm.projectIntegralState(feedbackVm.getStateX()
                    + step.dt * (vmRate + weight * (vmRate - step.previousVmRate))));
        step.trialVmRate = vmRate;
        step.trialVrRate = vrRate;
        step.trialTaRate = taRate;
    }

    private final class LocalStaggeredStep {
        final CMLControlBlock1stOrderAdapter[] blocks = {feedbackTr, feedbackVr, feedbackTa, feedbackVm};
        final double[] saved = new double[blocks.length * 3];
        double savedField, savedBridgeInput, savedGainInput;
        double acceptedEnd = Double.NaN, eventTime = Double.NaN, previousDt;
        double previousVrRate, previousTaRate, leftVrRate, leftTaRate;
        double previousVmRate, leftVmRate;
        double dt, end, trialVrRate, trialTaRate, trialVmRate;
        boolean staggeredSegment = true, historyReady, eventConfirmed;
        boolean pending, predicted, corrected, contiguous, useEvent, segment, staggered;

        void save() {
            for (int i = 0; i < blocks.length; i++) {
                var block = blocks[i];
                saved[3 * i] = block.getU();
                saved[3 * i + 1] = block.getStateX();
                saved[3 * i + 2] = block.getDX_dt();
            }
            savedBridgeInput = customBlock.getU();
            savedGainInput = feedbackKg.getU();
            savedField = getMachine().getEfd();
        }

        void restore() {
            for (int i = 0; i < blocks.length; i++)
                blocks[i].restoreIntegrationState(saved[3 * i], saved[3 * i + 1], saved[3 * i + 2]);
            customBlock.eulerStep2(savedBridgeInput, 0.);
            feedbackKg.eulerStep2(savedGainInput, 0.);
            getMachine().setEfd(savedField);
        }

        void clearTrial() {
            pending = predicted = corrected = false;
            trialVrRate = trialTaRate = trialVmRate = 0.;
        }
    }

    @Override public boolean eulerStep(boolean predictor,double dt,Machine machine) {
        beginBridgeScope();
        try {
            boolean refreshed = refreshInnerFeedback(machine);
            var step = localStaggeredStep;
            if (dt > 0. && step != null && step.pending) {
                if (dt != step.dt || predictor == step.predicted || step.corrected)
                    throw new IllegalStateException("Unexpected ESST4B integration stage");
                if (step.staggered) {
                    if (!refreshed) return false;
                    if (predictor) advanceStaggeredPredictor(step);
                    if (!refreshInnerFeedback(machine)) return false;
                    step.predicted = true;
                    step.corrected = !predictor;
                    return true;
                }
            }
            boolean accepted=directEvaluation==null?super.eulerStep(predictor,dt,machine):directEvaluation.step(predictor,dt);
            refreshInnerFeedback(machine);
            if (accepted && dt > 0. && step != null && step.pending) {
                step.predicted = true;
                step.corrected = !predictor;
            }
            return accepted;
        } finally{endBridgeScope();}
    }
    @Override public double getOutput(Machine machine) {
        beginBridgeScope();
        try {
            if(refreshInnerFeedback(machine))return customBlock.getY();
            return directEvaluation==null?super.getOutput(machine):directEvaluation.output();
        } finally{endBridgeScope();}
    }
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        this.integrationStep = timeStepSec;
        this.minimumTimeConstantMultiplier = multiplier;
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
	    public IEEE2005ST4BExciterData getData() {
	        return (IEEE2005ST4BExciterData)_data;
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
            boolean rebuildDirectEvaluation = directEvaluation != null;
            localStaggeredStep = null;
            directEvaluation = null;
            bridgeScopeDepth = 0;
            bridgeCached = false;
	        // pass the plugin data object values to the controller
        this.tr = correctedBypassTimeConstant(getData().getTr());

	        this.Kpr = getData().getKpr();
	        this.Kir = getData().getKir();
	        this.integralStateLimits = getData().isIntegralStateLimits() ? 1.0 : 0.0;
	        // PSS/E/PowerWorld validation keeps a zero-integral PI path usable by
	        // supplying the documented proportional-gain defaults. Keep the
	        // imported data object unchanged and correct only the runtime fields.
	        if (Math.abs(this.Kpr) <= 1.0e-9 && Math.abs(this.Kir) <= 1.0e-9) {
	           this.Kpr = 40.0;
	        }
	        this.ka  = 1;
	        this.ta  = correctedBypassTimeConstant(getData().getTa());
	        this.vrmax = getData().getVrmax();
	        this.vrmin = getData().getVrmin();

	        this.Kpm  = getData().getKpm();
	        this.Kim  = getData().getKim();
	        if (Math.abs(this.Kpm) <= 1.0e-9 && Math.abs(this.Kim) <= 1.0e-9) {
	           this.Kpm = 1.0;
	        }
	        this.vmmax = getData().getVmmax();
	        this.vmmin = getData().getVmmin();

	        this.kg   = getData().getKg();
	        this.vgmax = getData().getVgmax();
	        this.kp = getData().getKp();
	        this.ki = getData().getKi();
	        this.angKp_deg = getData().getAngKp();
	        this.xl    =getData().getXl();

	        this.kc = getData().getKc();

	        this.vbmax = getData().getVbmax();

	        if (this.vrmax < this.vrmin) {
	           double swap = this.vrmax; this.vrmax = this.vrmin; this.vrmin = swap;
	        }
	        if (this.vmmax < this.vmmin) {
	           double swap = this.vmmax; this.vmmax = this.vmmin; this.vmmin = swap;
	        }
	        double vb0 = calcBridgeVoltage(mach);
	        if (vb0 > 0.0 && Math.abs(this.Kpm) > 1.0e-9) {
	           double vm0 = mach.getEfd() / vb0;
	           double vg0 = Math.min(this.vgmax, this.kg * mach.getEfd());
	           double vr0 = vm0 / this.Kpm + vg0;
	           this.vmmax = Math.max(this.vmmax, vm0);
	           this.vmmin = Math.min(this.vmmin, vm0);
	           this.vrmax = Math.max(this.vrmax, vr0);
	           this.vrmin = Math.min(this.vrmin, vr0);
	        }

	        this.k1 =1.0;
	        // always add the following statement
	        boolean initialized = super.initStates(bus, mach);
            if(initialized)bindInnerFeedback();
            if (initialized && rebuildDirectEvaluation) enableDirectEvaluationExperiment();
            return initialized;
	    }

    private double correctedBypassTimeConstant(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

	    private double calcBridgeVoltage(Machine machine) {
	       double sourceVoltage = calcCompoundSourceVoltage(machine,
	             machine.getDStabBus().getVoltage(), machine.getIxy());
	       return calcBridgeVoltage(sourceVoltage, machine.calculateIfd(MachineIfdBase.EXCITER));
	    }

	    private double calcCompoundSourceVoltage(Machine machine, Complex vt, Complex systemCurrent) {
	       // The current-compounding gain multiplies machine-base terminal current.
	       // Network phasors use system-base current, which can overstate rectifier
	       // supply and mask loss of excitation voltage during fault recovery.
	       Complex it = systemCurrent.divide(machine.getIMultiFactor());
	       double angle = Math.toRadians(angKp_deg);
	       Complex kpCplx = new Complex(kp * Math.cos(angle), kp * Math.sin(angle));
	       return vt.multiply(kpCplx).add(new Complex(0, 1)
	             .multiply((kpCplx.multiply(xl).add(ki)).multiply(it))).abs();
	    }

	    private double calcBridgeVoltage(double ve, double ifd) {
	       if (ve <= 0.0) return 0.0;
	       double in = kc * ifd / ve;
	       double fex;
	       if (in <= 0.0) fex = 1.0;
	       else if (in <= 0.433) fex = 1.0 - 0.577 * in;
	       else if (in < 0.75) fex = Math.sqrt(0.75 - in * in);
	       else if (in <= 1.0) fex = 1.732 * (1.0 - in);
	       else fex = 0.0;
	       return Math.max(0.0, Math.min(vbmax, ve * fex));
	    }

	    /** Runtime Vr upper limit after normalization and initialization expansion. */
	    public double getEffectiveVrmax() { return vrmax; }

        /** Runtime transducer time constant after PowerWorld step-size correction. */
        public double getEffectiveTr() { return tr; }

        /** Runtime regulator time constant after PowerWorld step-size correction. */
        public double getEffectiveTa() { return ta; }

	    /** Runtime Vr lower limit after normalization and initialization expansion. */
	    public double getEffectiveVrmin() { return vrmin; }

	    /** Runtime Vm upper limit after normalization and initialization expansion. */
	    public double getEffectiveVmmax() { return vmmax; }

	    /** Runtime Vm lower limit after normalization and initialization expansion. */
	    public double getEffectiveVmmin() { return vmmin; }

	    /** Outer PI output Vr for model diagnostics and reference-trace comparison. */
	    public double getVoltageRegulatorOutput() { return diagnosticFieldValue("this.vrPIBlock.y"); }

        /** Outer PI integral state. */
        public double getVoltageRegulatorIntegrator() {
            return diagnosticFieldValue("this.vrPIBlock.state");
        }

        /** Whether the CML runtime blocks have completed initialization. */
        public boolean hasInitializedBlocks() {
            return getFieldWrapperList() != null && !getFieldWrapperList().isEmpty();
        }

        /** Regulator delay output Va. */
        public double getRegulatorDelayOutput() { return diagnosticFieldValue("this.taDelayBlock.y"); }

	    /** Inner PI output Vm before the OEL low-value gate. */
	    public double getFieldVoltageRegulatorOutput() {
	       return diagnosticFieldValue("this.vmPIBlock.y");
	    }

        /** Inner PI integral state. */
        public double getFieldVoltageRegulatorIntegrator() {
            return diagnosticFieldValue("this.vmPIBlock.state");
        }

        /** Terminal-voltage transducer output. */
        public double getSensedVoltage() { return diagnosticFieldValue("this.trDelayBlock.y"); }

	    /** Limited Kg*Efd feedback signal. */
	    public double getExcitationFeedback() { return diagnosticFieldValue("this.kgGainBlock.y"); }

	    /** Rectifier bridge voltage VB after FEX and VbMax. */
	    public double getBridgeVoltage() {
	       return calcBridgeVoltage(getMachine());
	    }

	    /** Set the external under-excitation limiter contribution at the Vref sum. */
	    public void setVuel(double value) { this.vuel = value; }

	    /** Set the external over-excitation limiter ceiling at the inner-loop LV gate. */
	    public void setVoel(double value) { this.voel = value; }

	    /** Make the external over-excitation limiter nonbinding. */
	    public void clearVoel() { this.voel = Double.POSITIVE_INFINITY; }

	    private double diagnosticFieldValue(String fieldName) {
	       try {
	          return getFieldVaule(fieldName);
	       } catch (Exception e) {
	          throw new IllegalStateException("Unable to read ESST4B field " + fieldName, e);
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
//	    @Override
//	    public Object getEditPanel() {
//	        _editPanel.init(this);
//	        return _editPanel;
//	    }

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
