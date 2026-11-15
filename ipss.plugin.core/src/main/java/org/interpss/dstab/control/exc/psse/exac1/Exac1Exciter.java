package org.interpss.dstab.control.exc.psse.exac1;

import java.lang.reflect.Field;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.wrapper.BaseFieldAnWrapper;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** PSS/E/PowerWorld EXAC1 rotating AC exciter with loaded-rectifier output. */
@AnController(input="mach.vt", output="this.rectifier.y",
        refPoint="this.leadLag.u0+this.transducer.y+this.washout.y-pss.vs", display={})
public class Exac1Exciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS = 1.0e-12;
    private final Exac1Data data;
    public double one=1.0, tr, tb, tc, ka, ta, vrmax, vrmin, integratorGain;
    public double kf, tf, washoutGain, kc, kd, ke, e1, se1, e2, se2, spdmlt;
    private double integrationStep, minimumTimeConstantMultiplier = 1.0;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.vt",
            parameter={"type.NoLimit", "this.one", "this.tr"}, y0="mach.vt", initOrderNumber=-1)
    public DelayControlBlock transducer;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint+pss.vs-this.transducer.y-this.washout.y",
            parameter={"type.NoLimit", "this.one", "this.tc", "this.tb"}, y0="this.regulator.u0")
    public FilterControlBlock leadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag.y",
            parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
            y0="this.fieldIntegrator.u0+this.vfe.y")
    public DelayControlBlock regulator;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.regulator.y-this.vfe.y",
            parameter={"type.NoLimit", "this.integratorGain"}, y0="this.rectifier.u0")
    public IntegrationControlBlock fieldIntegrator;

    @AnFunctionField(input={"this.fieldIntegrator.y"})
    public ICMLFunction saturation = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) { return saturationAt(values[0]); }
    };

    @AnFunctionField(input={"this.fieldIntegrator.y"})
    public ICMLFunction vfe = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) {
            // KD and the rectifier KC both use exciter-base field current.
            // The general CML mach.ifd signal is on the machine field base.
            double ifd = exciterIfd();
            return values[0] * (ke + saturationAt(values[0])) + kd * ifd;
        }
    };

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.vfe.y",
            parameter={"type.NoLimit", "this.washoutGain", "this.tf"}, feedback=true)
    public WashoutControlBlock washout;

    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="this.fieldIntegrator.y", y0="mach.efd")
    public ICMLStaticBlock rectifier = new CMLStaticBlockAdapter() {
        @Override public boolean initStateY0(double y0) {
            double ifd = exciterIfd();
            this.u = solveInternalVoltage(y0, kc * ifd);
            return Double.isFinite(this.u);
        }
        @Override public double getU0() { return this.u; }
        @Override public void eulerStep1(double u, double dt) { this.u = u; }
        @Override public void eulerStep2(double u, double dt) { this.u = u; }
        @Override public double getY() {
            double efd = this.u * rectifierFactor(kc * exciterIfd() / Math.max(this.u, 1.0e-12));
            return spdmlt != 0.0 ? efd * getMachine().getSpeed() : efd;
        }
    };

    public Exac1Exciter(String id, Exac1Data data, Machine machine) {
        this(id,"EXAC1",data,machine);
    }

    protected Exac1Exciter(String id,String name,Exac1Data data,Machine machine) {
        super(id,name,"PSS/E");
        this.data=data; this._data=data; setMachine(machine);
    }
    public Exac1Data getData() { return data; }

    @Override public void configureIntegrationStep(double seconds) {
        configureIntegrationStep(seconds, 1.0);
    }

    public void configureIntegrationStep(double seconds, double multiplier) {
        integrationStep = seconds;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        tr=correctedOptionalTime(data.getTr()); tb=correctedOptionalTime(data.getTb());
        tc=data.getTc(); ka=data.getKa(); ta=correctedOptionalTime(data.getTa());
        kc=data.getKc(); kd=data.getKd(); ke=data.getKe(); kf=data.getKf();
        tf=correctedRequiredTime(data.getTf());
        e1=data.getE1(); se1=data.getSe1(); e2=data.getE2(); se2=data.getSe2(); spdmlt=data.getSpdmlt();
        double te=correctedRequiredTime(data.getTe());
        if (te <= EPS || tf <= EPS || tr < 0 || tb < 0 || ta < 0 || tc < 0
                || kc < 0 || !finiteParameters(te)) return false;
        integratorGain=1.0/te;
        // CML's washout is K*T*s/(1+s*T); EXAC1 requires Kf*s/(1+s*Tf).
        washoutGain=kf/tf;
        double ifd=exciterIfd();
        double ve0=solveInternalVoltage(machine.getEfd(), kc*ifd);
        double vr0=ve0*(ke+saturationAt(ve0))+kd*ifd;
        vrmax=Math.max(Math.max(data.getVrmax(),data.getVrmin()),vr0);
        vrmin=Math.min(Math.min(data.getVrmax(),data.getVrmin()),vr0);
        return super.initStates(bus,machine);
    }

    private double minimumTime() {
        return minimumTimeConstantMultiplier * integrationStep;
    }

    private double correctedOptionalTime(double value) {
        double minimum = minimumTime();
        if (value > 0 && value < .5 * minimum) return 0;
        if (value > .5 * minimum && value < minimum) return minimum;
        return value;
    }

    private double correctedRequiredTime(double value) {
        double minimum = minimumTime();
        return value > 0 && value < minimum ? minimum : value;
    }

    private boolean finiteParameters(double te) {
        double[] values={tr,tb,tc,ka,ta,data.getVrmax(),data.getVrmin(),te,
                kf,tf,kc,kd,ke,e1,se1,e2,se2,spdmlt};
        for(double value:values) if(!Double.isFinite(value)) return false;
        return true;
    }

    private double saturationAt(double ve) {
        // Use the absolute fitted intercept for the cutoff and retain the
        // signed intercept in the quadratic above it.
        return saturation(ve, e1, se1, e2, se2, true);
    }

    private double exciterIfd() {
        double ifd=getMachine().calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(ifd) ? ifd : 0.0;
    }

    /** Five PSS/E states: sensed ET, lead-lag, VR, VE, and washout low-pass state. */
    public double[] getStateSnapshot() {
        return new double[]{runtimeBlock("transducer").getStateX(),
                getLeadLagLowPassState(), runtimeBlock("regulator").getStateX(),
                runtimeBlock("fieldIntegrator").getStateX(), getWashoutLowPassState()};
    }

    private double getLeadLagLowPassState() {
        if (Math.abs(tb) <= EPS) return 0.0;
        double dynamicGain=1.0-tc/tb;
        return Math.abs(dynamicGain) > EPS ? runtimeBlock("leadLag").getStateX()/dynamicGain : 0.0;
    }

    private double getWashoutLowPassState() {
        return Math.abs(washoutGain) > EPS ? runtimeBlock("washout").getStateX()/washoutGain : 0.0;
    }

    /** Runtime inputs for the five state blocks, in the same documented order. */
    public double[] getStateInputSnapshot() {
        return new double[]{runtimeBlock("transducer").getU(), runtimeBlock("leadLag").getU(),
                runtimeBlock("regulator").getU(), runtimeBlock("fieldIntegrator").getU(),
                runtimeBlock("washout").getU()};
    }

    public double getRegulatorOutput() { return runtimeBlock("regulator").getY(); }

    /** PowerWorld/IEEE diagram state VE, before the loaded-rectifier output. */
    public double getInternalFieldVoltage() { return runtimeBlock("fieldIntegrator").getY(); }

    /** PowerWorld/IEEE diagram state for the filtered terminal voltage. */
    public double getSensedVoltage() { return runtimeBlock("transducer").getY(); }

    /** PowerWorld/IEEE diagram VLL signal at the lead-lag output. */
    public double getLeadLagOutput() { return runtimeBlock("leadLag").getY(); }

    /** PowerWorld/IEEE diagram VF rate-feedback signal. */
    public double getRateFeedback() { return runtimeBlock("washout").getY(); }

    private ICMLControlBlock runtimeBlock(String name) {
        for (BaseFieldAnWrapper<?> wrapper : getFieldWrapperList()) {
            if (wrapper.getFieldName().equals(name) && wrapper.getField() instanceof ICMLControlBlock block)
                return block;
        }
        throw new IllegalStateException("EXAC1 CML block is not initialized: " + name);
    }

    public static double rectifierFactor(double in) {
        if (in <= 0.0) return 1.0;
        if (in <= .433) return 1.0-.577*in;
        if (in < .75) return Math.sqrt(Math.max(0.0,.75-in*in));
        if (in <= 1.0) return 1.732*(1.0-in);
        return 0.0;
    }

    public static double solveInternalVoltage(double efd, double loadedCurrent) {
        if (loadedCurrent <= 0.0 || efd <= 0.0) return efd;
        double low=loadedCurrent, high=Math.max(efd+loadedCurrent,2.0*loadedCurrent);
        while (high*rectifierFactor(loadedCurrent/high) < efd) high*=2.0;
        for (int i=0;i<80;i++) {
            double mid=.5*(low+high);
            if (mid*rectifierFactor(loadedCurrent/mid) < efd) low=mid; else high=mid;
        }
        return .5*(low+high);
    }

    /**
     * Finds the highest admissible field between the lower field limit and an
     * overshooting trial. The caller supplies a nondecreasing feedback curve.
     */
    public static double projectFieldFeedbackCeiling(double trialField, double minimumField,
            double feedbackLimit, java.util.function.DoubleUnaryOperator feedback) {
        if (feedback.applyAsDouble(minimumField) > feedbackLimit) return minimumField;
        double low = minimumField;
        double high = trialField;
        for (int iteration = 0; iteration < 48 && high - low > 1.0e-12; iteration++) {
            double midpoint = low + (high - low) * 0.5;
            if (feedback.applyAsDouble(midpoint) <= feedbackLimit) low = midpoint;
            else high = midpoint;
        }
        return low;
    }

    public static double saturation(double ve,double e1,double se1,double e2,double se2) {
        return saturation(ve,e1,se1,e2,se2,false);
    }

    /**
     * Evaluate the fitted quadratic with an explicitly selected cutoff convention.
     * The historical five-argument path retains its signed-A cutoff and arithmetic.
     * Some independently qualified excitation models instead suppress saturation
     * through |A| when the fitted intercept is negative. Two synthetic negative-A
     * component profiles, a positive-A control and repeated reference captures
     * distinguish this cutoff from disabling saturation or changing the fit.
     *
     * Only the cutoff uses the absolute value: the fitted A remains signed in
     * B*(ve-A)^2/ve and in B's normalization. A negative A therefore permits a
     * discontinuous nonzero onset at |A|; smoothing that jump or replacing A in
     * the quadratic would change the qualified model. Callers must opt in only
     * for their own qualified model convention; other excitation dialects are
     * intentionally unaffected. Nonpositive/degenerate source handling is shared.
     *
     * @param absoluteThreshold select |A| rather than A for the zero-output cutoff
     */
    public static double saturation(double ve,double e1,double se1,double e2,double se2,
            boolean absoluteThreshold) {
        if (e1<=0||e2<=0||se1<=0||se2<=0) return 0.0;
        double ratio=Math.sqrt(e2*se2/(e1*se1));
        if (!Double.isFinite(ratio)||Math.abs(1-ratio)<1e-12) return 0.0;
        double a=(e2-e1*ratio)/(1-ratio);
        if (ve<=(absoluteThreshold?Math.abs(a):a)||ve<=0) return 0.0;
        double d=(e1-a)*(e1-a);
        return d>0 ? e1*se1*(ve-a)*(ve-a)/(d*ve) : 0.0;
    }

    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
