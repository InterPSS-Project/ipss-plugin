package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WTPTB1 pitch controller with explicit native PSS/E 36.7 zero-time bypass.
 * PSS/E 36.7 Model Library section 24.4: state-bounded PI integrators, separately
 * clipped PI outputs and directional pitch-angle cross anti-windup. Speed
 * reference is a time-varying torque-controller input, not a frozen initial value.
 */
public final class Wtptb1Model {
    private final Wtptb1Data originalData;
    private final Parameters data;
    private final boolean clipPiOutputs;
    private com.interpss.dstab.controller.runtime.IntegrationStateMonitor observer;
    private com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage observationStage;
    // Native state order: output lag, speed integral, compensation integral.
    private final double[] state = new double[3];
    private final TwoStepHistory twoStep = new TwoStepHistory(3);
    void rebaseNextTwoStep() { twoStep.rebaseNextStep(); }
    private double[] start, firstRate;
    private boolean initialized;

    public Wtptb1Model(Wtptb1Data data) {
        originalData=Objects.requireNonNull(data);this.data=Parameters.of(data);clipPiOutputs=true;
    }

    /** Native revision A bounds integral storage, not the complete PI outputs. */
    private Wtptb1Model(Wtpta1Data data) {
        originalData=null;this.data=Parameters.of(Objects.requireNonNull(data));clipPiOutputs=false;
        if(data.dThetaMax()<0||data.dThetaMin()>0)
            throw new IllegalArgumentException("WTPTA1 native rate limits must include zero");
    }
    static Wtptb1Model forPitchA(Wtpta1Data data) { return new Wtptb1Model(data); }
    void setIntegrationObserver(com.interpss.dstab.controller.runtime.IntegrationStateMonitor monitor) { observer=monitor; }

    private record Parameters(double kiw,double kpw,double kic,double kpc,double kcc,double tp,
            double thetaMax,double thetaMin,double rateMax,double rateMin,double compensationMax,
            double compensationMin,double speedMax,double speedMin) {
        static Parameters of(Wtptb1Data d){return new Parameters(d.kiw(),d.kpw(),d.kic(),d.kpc(),d.kcc(),d.tp(),d.thetaMax(),d.thetaMin(),d.rateMax(),d.rateMin(),d.compensationMax(),d.compensationMin(),d.speedMax(),d.speedMin());}
        static Parameters of(Wtpta1Data d){return new Parameters(d.kiw(),d.kpw(),d.kic(),d.kpc(),d.kcc(),d.tp(),d.thetaMax(),d.thetaMin(),d.dThetaMax(),d.dThetaMin(),d.thetaMax(),d.thetaMin(),d.thetaMax(),d.thetaMin());}
    }

    /** Balanced zero-error initialization; invalid initial limits are not silently expanded. */
    public void initialize(double initialPitch) {
        if (!Double.isFinite(initialPitch) || initialPitch < data.thetaMin()
                || initialPitch > data.thetaMax() || initialPitch < data.speedMin()
                || initialPitch > data.speedMax() || data.compensationMin() > 0
                || data.compensationMax() < 0)
            throw new IllegalArgumentException("WTPTB1 equilibrium is outside the declared state limits");
        state[0] = state[1] = initialPitch;
        state[2] = 0;
        start = firstRate = null;
        twoStep.reset();
        initialized = true;
        if(observer!=null)observer.reset();
    }

    /** Complete modified Euler for constant input signals over the step. */
    public void step(double dt, double pOrder, double pReference,
            double turbineSpeed, double speedReference) {
        step(dt, pOrder, pReference, turbineSpeed, speedReference, 0);
        step(dt, pOrder, pReference, turbineSpeed, speedReference, 1);
    }

    /** Explicit predictor/corrector, accepting distinct old/predicted input endpoints. */
    public void step(double dt, double pOrder, double pReference,
            double turbineSpeed, double speedReference, int flag) {
        if (!initialized) throw new IllegalStateException("WTPTB1 must be initialized");
        for (double value : new double[] {dt, pOrder, pReference, turbineSpeed, speedReference})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("WTPTB1 inputs must be finite");
        if (dt < 0 || flag < 0 || flag > 1)
            throw new IllegalArgumentException("WTPTB1 requires dt >= 0 and stage 0 or 1");
        refreshAlgebraicPitch(pOrder,pReference,turbineSpeed,speedReference);
        if (dt == 0) return;
        observationStage=flag==0?com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.PREDICTOR
                :com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.CORRECTOR;
        if (flag == 0) {
            start = state.clone();
            firstRate = rates(pOrder, pReference, turbineSpeed, speedReference);
            for (int i = 0; i < 3; i++) state[i] = start[i] + dt * firstRate[i];
        } else {
            if (start == null) throw new IllegalStateException("WTPTB1 corrector without predictor");
            double[] lastRate = rates(pOrder, pReference, turbineSpeed, speedReference);
            for (int i = 0; i < 3; i++) state[i] = start[i] + .5 * dt * (firstRate[i] + lastRate[i]);
            start = firstRate = null;
        }
        constrainStates();
        refreshAlgebraicPitch(pOrder,pReference,turbineSpeed,speedReference);
    }

    void setSinglePrecisionStates(boolean enabled) { twoStep.setSinglePrecision(enabled); }

    /** Separate opt-in explicit two-step path; a zero-duration refresh consumes no history. */
    void stepTwoStep(double dt,double pOrder,double pReference,double speed,double reference) {
        if (!initialized) throw new IllegalStateException("WTPTB1 must be initialized");
        for(double value:new double[]{dt,pOrder,pReference,speed,reference})
            if(!Double.isFinite(value))throw new IllegalArgumentException("WTPTB1 inputs must be finite");
        if(dt<0)throw new IllegalArgumentException("WTPTB1 requires dt >= 0");
        refreshAlgebraicPitch(pOrder,pReference,speed,reference);
        if(dt==0)return;
        observationStage=com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.TWO_STEP;
        double[] derivative=rates(pOrder,pReference,speed,reference);
        twoStep.begin(dt);
        for(int i=data.tp()==0?1:0;i<3;i++) {
            boolean constrained=state[0]<=data.thetaMin() || state[0]>=data.thetaMax()
                    || i==1 && (state[1]<=data.speedMin() || state[1]>=data.speedMax())
                    || i==2 && (state[2]<=data.compensationMin() || state[2]>=data.compensationMax());
            boolean ownBound=i==0&&(state[0]<=data.thetaMin()||state[0]>=data.thetaMax())
                    ||i==1&&(state[1]<=data.speedMin()||state[1]>=data.speedMax())
                    ||i==2&&(state[2]<=data.compensationMin()||state[2]>=data.compensationMax());
            // Revision A's angle cross-freeze zeros the PI derivative, but
            // retains its STORE recurrence. The first zero rate removes the
            // preceding half-step; anchoring at STATE would retain an offset.
            state[i]=derivative[i]==0 && constrained && (clipPiOutputs||ownBound)?twoStep.hold(i,state[i])
                    :twoStep.advance(i,state[i],derivative[i]);
        }
        constrainStates();
        refreshAlgebraicPitch(pOrder,pReference,speed,reference);
        if(observer!=null)twoStep.observeRates(observer);
    }

    private void constrainStates() {
        state[0] = clip(state[0], data.thetaMin(), data.thetaMax());
        state[1] = clip(state[1], data.speedMin(), data.speedMax());
        state[2] = clip(state[2], data.compensationMin(), data.compensationMax());
        for (double value : state) if (!Double.isFinite(value))
            throw new IllegalStateException("Nonfinite WTPTB1 state");
    }

    private double[] rates(double pOrder, double pReference, double speed, double reference) {
        double powerError = pOrder - pReference;
        double speedError = speed - reference + data.kcc() * powerError;
        double speedRate = boundedRate(state[1], data.kiw() * speedError, data.speedMin(), data.speedMax());
        double compensationRate = boundedRate(state[2], data.kic() * powerError,
                data.compensationMin(), data.compensationMax());
        // The integrator's own limit depends on its state, not PI output.
        // Even with clipped output it may integrate until the state bound.
        double speedOutput = speedOutput(speedError);
        double compensationOutput = compensationOutput(powerError);
        // Do not clamp the summed command before the lag: the final angle
        // constraint acts on the output state and its outward derivative.
        double pitchRate = data.tp()==0?0:clip((speedOutput + compensationOutput - state[0]) / data.tp(),
                data.rateMin(), data.rateMax());
        pitchRate = boundedRate(state[0], pitchRate, data.thetaMin(), data.thetaMax());
        speedRate = boundedRate(state[0], speedRate, data.thetaMin(), data.thetaMax());
        compensationRate = boundedRate(state[0], compensationRate, data.thetaMin(), data.thetaMax());
        double[] result={pitchRate, speedRate, compensationRate};
        if(observer!=null){observer.begin(observationStage);for(int i=0;i<3;i++)observer.record(i,state[i],result[i]);}
        return result;
    }

    private void refreshAlgebraicPitch(double pOrder,double pReference,double speed,double reference) {
        if(data.tp()!=0)return;
        double error=pOrder-pReference;
        // Native Tp=0 eliminates the lag and its rate limiter, while retaining
        // angle and PI limits. Cold native traces obey this algebraic identity
        // to float precision even when the observed slew exceeds RThetaMax.
        state[0]=clip(speedOutput(speed-reference+data.kcc()*error)+compensationOutput(error),data.thetaMin(),data.thetaMax());
    }

    private double speedOutput(double error) {
        double value=state[1]+data.kpw()*error;
        return clipPiOutputs?clip(value,data.speedMin(),data.speedMax()):value;
    }
    private double compensationOutput(double error) {
        double value=state[2]+data.kpc()*error;
        return clipPiOutputs?clip(value,data.compensationMin(),data.compensationMax()):value;
    }

    private static double boundedRate(double state, double rate, double minimum, double maximum) {
        return state >= maximum && rate > 0 || state <= minimum && rate < 0 ? 0 : rate;
    }
    private static double clip(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    public Wtptb1Data getData() {
        if(originalData==null)throw new IllegalStateException("Revision-A kernel has no revision-B input record");
        return originalData;
    }
    public double getPitch() { return state[0]; }
    public double[] getNativeStates() { return state.clone(); }
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Pitch Angle", state[0]);
        named.put("Pitch Control Integral", state[1]);
        named.put("Pitch Compensation Integral", state[2]);
        return Collections.unmodifiableMap(named);
    }
}
