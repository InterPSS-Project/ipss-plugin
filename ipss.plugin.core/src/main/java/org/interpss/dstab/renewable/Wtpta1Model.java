package org.interpss.dstab.renewable;

import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;

/** WECC WTPT_A pitch controller with PI, angle, and angle-rate limits. */
public final class Wtpta1Model implements IntegrationStateAccess, com.interpss.dstab.controller.cml.ICMLStateProvider {
    private static final double EPS = 1.0e-9;

    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Speed Integral", "Compensation Integral", "Pitch");
    private Stage observationStage = Stage.NOT_EVALUATED;
    private final Wtpta1Data data;
    private final Wtptb1Model nativeKernel;
    private final IntegrationStateMonitor nativeMonitor;
    private double speedReference;
    private double speedIntegral;
    private double compensationIntegral;
    private double pitch;
    private PitchState predictorStart;
    private PitchDerivative predictorDerivative;
    private final TwoStepHistory twoStep = new TwoStepHistory(3);
    void setSinglePrecisionStates(boolean enabled) { if(nativeKernel!=null)nativeKernel.setSinglePrecisionStates(enabled);else twoStep.setSinglePrecision(enabled); }
    public void rebaseNextTwoStep() { if(nativeKernel!=null)nativeKernel.rebaseNextTwoStep();else twoStep.rebaseNextStep(); }

    public Wtpta1Model(Wtpta1Data data) {
        this(data,null);
    }
    private Wtpta1Model(Wtpta1Data data,Wtptb1Model kernel) {
        this.data=java.util.Objects.requireNonNull(data);nativeKernel=kernel;
        nativeMonitor=kernel==null?null:new IntegrationStateMonitor("Pitch","Speed Integral","Compensation Integral");
        if(kernel!=null)kernel.setIntegrationObserver(nativeMonitor);
    }

    /** Exact ten-value native record with state-bounded PI storage and moving input ports. */
    public static Wtpta1Model fromNativeRecord(Wtpta1Data data) { return new Wtpta1Model(data,Wtptb1Model.forPitchA(data)); }
    public boolean isNativeRecordConvention() { return nativeKernel!=null; }

    /** Explicit native speed-reference input, independent of the plant power reference. */
    public void stepWithReference(double dt,double pOrder,double pReference,double speed,double reference,int flag) {
        if(nativeKernel==null)throw new IllegalStateException("Explicit native input ports require the native-record convention");
        nativeKernel.step(dt,pOrder,pReference,speed,reference,flag);speedReference=reference;
    }
    public void stepWithReference(double dt,double pOrder,double pReference,double speed,double reference) {
        stepWithReference(dt,pOrder,pReference,speed,reference,0);stepWithReference(dt,pOrder,pReference,speed,reference,1);
    }
    /** Opt-in explicit history method; an input event must rebase its next positive step. */
    public void stepTwoStepWithReference(double dt,double pOrder,double pReference,double speed,double reference) {
        if(nativeKernel==null)throw new IllegalStateException("Explicit native input ports require the native-record convention");
        nativeKernel.stepTwoStep(dt,pOrder,pReference,speed,reference);speedReference=reference;
    }
    public double[] getNativeStates() {
        if(nativeKernel==null)throw new IllegalStateException("Native state order requires the native-record convention");
        return nativeKernel.getNativeStates();
    }

    public void initialize(double theta0, double speed) {
        if(nativeKernel!=null) {
            if(!Double.isFinite(speed)||speed<=0)throw new IllegalArgumentException("WTPTA1 initial speed must be finite and positive");
            nativeKernel.initialize(theta0);speedReference=speed;return;
        }
        stateMonitor.reset();
        twoStep.reset();
        predictorStart = null;
        predictorDerivative = null;
        speedReference = speed;
        speedIntegral = Repca1Model.limit(theta0, data.thetaMin(), data.thetaMax());
        compensationIntegral = 0.0;
        pitch = speedIntegral;
    }

    public void step(double dt, double pOrder, double pReference, double speed) {
        if(nativeKernel!=null){stepWithReference(dt,pOrder,pReference,speed,speedReference);return;}
        PitchState observed = state();
        double powerError = pOrder - pReference;
        double compensationError = powerError;
        double speedError = data.kcc() * powerError + speed - speedReference;
        compensationIntegral = Repca1Model.integrateWithAntiWindup(compensationIntegral,
                data.kic(), compensationError, dt, data.kpc(),
                data.thetaMin(), data.thetaMax(), false);
        speedIntegral = Repca1Model.integrateWithAntiWindup(speedIntegral,
                data.kiw(), speedError, dt, data.kpw(),
                data.thetaMin(), data.thetaMax(), false);
        double compensation = Repca1Model.limit(
                data.kpc() * compensationError + compensationIntegral,
                data.thetaMin(), data.thetaMax());
        double speedPath = Repca1Model.limit(data.kpw() * speedError + speedIntegral,
                data.thetaMin(), data.thetaMax());
        double target = Repca1Model.limit(compensation + speedPath,
                data.thetaMin(), data.thetaMax());
        double derivative = data.tp() <= EPS ? (target - pitch) / Math.max(dt, EPS)
                : (target - pitch) / data.tp();
        derivative = Repca1Model.limit(derivative, data.dThetaMin(), data.dThetaMax());
        pitch = Repca1Model.limit(pitch + dt * derivative, data.thetaMin(), data.thetaMax());
        if (dt > 0.0) {
            observationStage = Stage.APPLIED_STEP;
            retainRates(observed, new PitchDerivative((speedIntegral-observed.speedIntegral())/dt,
                    (compensationIntegral-observed.compensationIntegral())/dt,
                    (pitch-observed.pitch())/dt));
        }
    }

    /**
     * Advances one modified-Euler stage for the two PI paths and pitch servo.
     * The complete-step API remains unchanged until the renewable-stack
     * coordinator can switch every dependent model to staged execution at once.
     */
    public void step(double dt, double pOrder, double pReference, double speed,
            int flag) {
        if(nativeKernel!=null){stepWithReference(dt,pOrder,pReference,speed,speedReference,flag);return;}
        if (!Double.isFinite(dt) || dt < 0.0 || !Double.isFinite(pOrder)
                || !Double.isFinite(pReference) || !Double.isFinite(speed)) {
            throw new IllegalArgumentException(
                    "WTPTA1 staged inputs must be finite and dt must be non-negative");
        }
        observationStage = flag == 0 ? Stage.PREDICTOR : Stage.CORRECTOR;
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("WTPTA1 integration flag must be 0 or 1");
        }
        if (dt <= 0.0) return;
        if (flag == 0) {
            predictorStart = state();
            predictorDerivative = derivatives(predictorStart, dt, pOrder,
                    pReference, speed);
            apply(add(predictorStart, predictorDerivative, dt));
        } else {
            if (predictorStart == null || predictorDerivative == null) {
                throw new IllegalStateException("WTPTA1 corrector called without predictor");
            }
            PitchDerivative corrected = derivatives(state(), dt, pOrder,
                    pReference, speed);
            apply(correct(predictorStart, predictorDerivative, corrected, dt));
            predictorStart = null;
            predictorDerivative = null;
        }
    }

    private PitchDerivative derivatives(PitchState state, double dt,
            double pOrder, double pReference, double speed) {
        double powerError = pOrder - pReference;
        double speedError = data.kcc() * powerError + speed - speedReference;
        double compensationRate = integralRate(state.compensationIntegral(),
                data.kic(), powerError, data.kpc());
        double speedRate = integralRate(state.speedIntegral(), data.kiw(),
                speedError, data.kpw());
        double compensation = Repca1Model.limit(
                data.kpc() * powerError + state.compensationIntegral(),
                data.thetaMin(), data.thetaMax());
        double speedPath = Repca1Model.limit(
                data.kpw() * speedError + state.speedIntegral(),
                data.thetaMin(), data.thetaMax());
        double target = Repca1Model.limit(compensation + speedPath,
                data.thetaMin(), data.thetaMax());
        double pitchRate = data.tp() <= EPS
                ? (target - state.pitch()) / dt : (target - state.pitch()) / data.tp();
        pitchRate = Repca1Model.limit(pitchRate, data.dThetaMin(), data.dThetaMax());
        if ((state.pitch() >= data.thetaMax() && pitchRate > 0.0)
                || (state.pitch() <= data.thetaMin() && pitchRate < 0.0)) {
            pitchRate = 0.0;
        }
        PitchDerivative rate = new PitchDerivative(speedRate, compensationRate, pitchRate);
        retainRates(state, rate);
        return rate;
    }

    void stepTwoStep(double dt, double pOrder, double pReference, double speed) {
        if(nativeKernel!=null){stepTwoStepWithReference(dt,pOrder,pReference,speed,speedReference);return;}
        observationStage = Stage.TWO_STEP;
        if (dt <= 0.0) return;
        PitchState start = state();
        PitchDerivative rate = derivatives(start, dt, pOrder, pReference, speed);
        twoStep.begin(dt);
        apply(new PitchState(
                twoStep.advance(0, start.speedIntegral(), rate.speedIntegral()),
                twoStep.advance(1, start.compensationIntegral(), rate.compensationIntegral()),
                twoStep.advance(2, start.pitch(), rate.pitch())));
        twoStep.observeRates(stateMonitor);
    }

    private double integralRate(double integral, double gain, double error,
            double proportionalGain) {
        double output = proportionalGain * error + integral;
        if ((output >= data.thetaMax() && error > 0.0)
                || (output <= data.thetaMin() && error < 0.0)) return 0.0;
        return gain * error;
    }


    @Override public java.util.Map<String, Double> getIntegrationStates() {
        if(nativeKernel!=null) {
            var x=nativeKernel.getNativeStates();
            return data.tp()==0?java.util.Map.of("Speed Integral",x[1],"Compensation Integral",x[2])
                    :java.util.Map.of("Speed Integral",x[1],"Compensation Integral",x[2],"Pitch",x[0]);
        }
        return java.util.Map.of("Speed Integral", speedIntegral,
                "Compensation Integral", compensationIntegral,
                "Pitch", pitch);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {
        if(nativeKernel!=null) {
            var snapshot=nativeMonitor.snapshot();if(data.tp()!=0||snapshot.rates().isEmpty())return snapshot;
            return new IntegrationStateMonitor.Snapshot(snapshot.stage(),java.util.Map.of("Speed Integral",snapshot.evaluationStates().get("Speed Integral"),"Compensation Integral",snapshot.evaluationStates().get("Compensation Integral")),
                    java.util.Map.of("Speed Integral",snapshot.rates().get("Speed Integral"),"Compensation Integral",snapshot.rates().get("Compensation Integral")));
        }
        return stateMonitor.snapshot();
    }
    @Override public java.util.Map<String, Double> getNamedStates() {
        if(nativeKernel!=null) {var x=nativeKernel.getNativeStates();return java.util.Map.of("Speed Integral",x[1],"Compensation Integral",x[2],"Pitch",x[0]);}
        return getIntegrationStates();
    }
    private void retainRates(PitchState s, PitchDerivative d) {
        stateMonitor.begin(observationStage);
        stateMonitor.record(0, s.speedIntegral(), d.speedIntegral());
        stateMonitor.record(1, s.compensationIntegral(), d.compensationIntegral());
        stateMonitor.record(2, s.pitch(), d.pitch());
    }
    private PitchState state() {
        return new PitchState(speedIntegral, compensationIntegral, pitch);
    }

    private void apply(PitchState state) {
        speedIntegral = state.speedIntegral();
        compensationIntegral = state.compensationIntegral();
        pitch = Repca1Model.limit(state.pitch(), data.thetaMin(), data.thetaMax());
    }

    private static PitchState add(PitchState state, PitchDerivative derivative,
            double dt) {
        return new PitchState(
                state.speedIntegral() + dt * derivative.speedIntegral(),
                state.compensationIntegral() + dt * derivative.compensationIntegral(),
                state.pitch() + dt * derivative.pitch());
    }

    private static PitchState correct(PitchState start, PitchDerivative first,
            PitchDerivative second, double dt) {
        return new PitchState(
                start.speedIntegral() + .5 * dt
                        * (first.speedIntegral() + second.speedIntegral()),
                start.compensationIntegral() + .5 * dt
                        * (first.compensationIntegral() + second.compensationIntegral()),
                start.pitch() + .5 * dt * (first.pitch() + second.pitch()));
    }

    public Wtpta1Data getData() { return data; }
    public double getPitch() { return nativeKernel!=null?nativeKernel.getPitch():pitch; }
    public double getSpeedReference() { return speedReference; }
    public double getSpeedIntegral() { return nativeKernel!=null?nativeKernel.getNativeStates()[1]:speedIntegral; }
    public double getCompensationIntegral() { return nativeKernel!=null?nativeKernel.getNativeStates()[2]:compensationIntegral; }

    private record PitchState(double speedIntegral, double compensationIntegral,
            double pitch) { }

    private record PitchDerivative(double speedIntegral, double compensationIntegral,
            double pitch) { }
}
