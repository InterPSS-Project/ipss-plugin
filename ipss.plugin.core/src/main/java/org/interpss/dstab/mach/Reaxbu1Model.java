package org.interpss.dstab.mach;

import java.util.Map;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Two-axis lag translating plant-level auxiliary commands to a wind controller. */
public final class Reaxbu1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private final Reaxbu1Data data;
    private final String modelName;
    private double reactiveState, activeState, reactiveReference, activeReference;
    private double reactiveExternal, activeExternal;
    private State oldState;
    private Derivative predictor;
    private boolean initialized;

    public Reaxbu1Model(String modelName, Reaxbu1Data data) {
        this.modelName = modelName;
        this.data = data;
    }
    public void initialize(double reactiveExternal, double activeExternal) {
        setExternalSignals(reactiveExternal, activeExternal);
        reactiveReference = -reactiveExternal;
        activeReference = -activeExternal;
        reactiveState = activeState = 0.0;
        initialized = true;
    }
    public void step(double dt, int flag) {
        if (!initialized || !Double.isFinite(dt) || dt <= 0.0 || (flag != 0 && flag != 1))
            throw new IllegalArgumentException("invalid REAXB step boundary");
        if (flag == 0) {
            oldState = state(); predictor = derivatives(oldState);
            apply(advance(oldState, predictor, dt));
        } else {
            if (oldState == null || predictor == null)
                throw new IllegalStateException("corrector without predictor");
            apply(advance(oldState, predictor.add(derivatives(state())), .5*dt));
            oldState = null; predictor = null;
        }
    }
    private Derivative derivatives(State state) {
        double reactiveTarget = data.reactiveGain()*(reactiveReference+reactiveExternal);
        double activeTarget = data.activeGain()*(activeReference+activeExternal);
        if (data.measurementTime() <= EPS) return new Derivative(0,0);
        return new Derivative((reactiveTarget-state.reactive)/data.measurementTime(),
                (activeTarget-state.active)/data.measurementTime());
    }
    private void apply(State state) {
        reactiveState = clamp(data.measurementTime()<=EPS
                        ? data.reactiveGain()*(reactiveReference+reactiveExternal):state.reactive,
                data.reactiveMinimum(),data.reactiveMaximum());
        activeState = clamp(data.measurementTime()<=EPS
                        ? data.activeGain()*(activeReference+activeExternal):state.active,
                data.activeMinimum(),data.activeMaximum());
    }
    public void setExternalSignals(double reactive, double active) {
        if (!Double.isFinite(reactive)||!Double.isFinite(active))
            throw new IllegalArgumentException("REAXB inputs must be finite");
        reactiveExternal=reactive;activeExternal=active;
    }
    public void setReferences(double reactive, double active) {
        if (!Double.isFinite(reactive)||!Double.isFinite(active))
            throw new IllegalArgumentException("REAXB references must be finite");
        reactiveReference=reactive;activeReference=active;
    }
    public Reaxbu1Data getData(){return data;}
    public String getModelName(){return modelName;}
    public double getReactiveOutput(){return reactiveState;}
    public double getActiveOutput(){return activeState;}
    @Override public Map<String,Double> getNamedStates(){return initialized?Map.of(
            "Measurement lag (reactive part)",reactiveState,
            "Measurement lag (real part)",activeState):Map.of();}
    private State state(){return new State(reactiveState,activeState);}
    private static State advance(State s,Derivative d,double dt){return new State(s.reactive+dt*d.reactive,s.active+dt*d.active);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private record State(double reactive,double active){}
    private record Derivative(double reactive,double active){Derivative add(Derivative o){return new Derivative(reactive+o.reactive,active+o.active);}}
}
