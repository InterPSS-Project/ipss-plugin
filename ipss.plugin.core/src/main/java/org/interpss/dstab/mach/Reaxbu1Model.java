package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Two-axis lag translating plant-level auxiliary commands to a wind controller. */
public final class Reaxbu1Model implements ICMLStateProvider,org.interpss.dstab.control.util.IntegrationStateAccess,
        org.interpss.dstab.control.util.StaggeredEulerParticipant {
    private static final double EPS = 1.0e-10;
    private final Reaxbu1Data data;
    private final String modelName;
    private double reactiveState, activeState, reactiveReference, activeReference;
    private double initialReactiveOutput, initialActiveOutput;
    private double reactiveExternal, activeExternal;
    private double externalScale=1;
    private Plntbu1Model plantController;
    private State oldState;
    private Derivative predictor;
    private boolean initialized;
    private final org.interpss.dstab.control.util.IntegrationStateMonitor monitor=new org.interpss.dstab.control.util.IntegrationStateMonitor(
            "Measurement lag (reactive part)","Measurement lag (real part)");
    private org.interpss.dstab.control.util.IntegrationStateMonitor.Stage stage=org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.NOT_EVALUATED;

    public Reaxbu1Model(String modelName, Reaxbu1Data data) {
        this.modelName = modelName;
        this.data = data;
    }
    public void initialize(double reactiveExternal, double activeExternal) {
        initialize(reactiveExternal,activeExternal,1);
    }
    public void initialize(double reactiveExternal,double activeExternal,double systemToMachineBase) {
        initializeOperatingPoint(reactiveExternal,activeExternal,0,0,systemToMachineBase);
    }
    /** Initialize absolute references for a generic electrical-controller host. */
    public void initializeOutputs(double reactiveOutput,double activeOutput) {
        initializeOutputs(reactiveOutput,activeOutput,1);
    }
    public void initializeOutputs(double reactiveOutput,double activeOutput,double systemToMachineBase) {
        if(plantController!=null&&!plantController.reinitialize())throw new IllegalStateException("PLNTBU1 could not reinitialize");
        initializeOperatingPoint(0,0,reactiveOutput,activeOutput,systemToMachineBase);
    }
    private void initializeOperatingPoint(double reactiveExternal,double activeExternal,double reactiveOutput,double activeOutput,double scale) {
        if(!Double.isFinite(reactiveOutput)||!Double.isFinite(activeOutput))throw new IllegalArgumentException("Finite auxiliary operating point required");
        if(!Double.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Positive auxiliary base conversion required");
        externalScale=scale;
        if (plantController != null) {
            if (!plantController.initialize()) {
                throw new IllegalStateException("PLNTBU1 could not initialize");
            }
            reactiveExternal = plantController.getReactiveOutput();
            activeExternal = plantController.getActiveOutput();
        }
        setExternalSignals(reactiveExternal, activeExternal);
        initialReactiveOutput=reactiveOutput;initialActiveOutput=activeOutput;
        // Nonzero-weight references keep their existing input-side units.
        // For an unconnected axis there is no inverse reference: expose zero
        // and retain the host operating point independently of plant signals.
        reactiveReference = data.reactiveGain()==0 ? 0
                : reactiveOutput/data.reactiveGain()-externalScale*reactiveExternal;
        activeReference = data.activeGain()==0 ? 0
                : activeOutput/data.activeGain()-externalScale*activeExternal;
        reactiveState=reactiveOutput;activeState=activeOutput;
        oldState=null;predictor=null;
        initialized = true;
        stage=org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.INITIALIZATION;derivatives(state());
    }
    public void step(double dt, int flag) {
        if (!initialized || !Double.isFinite(dt) || dt <= 0.0 || (flag != 0 && flag != 1))
            throw new IllegalArgumentException("invalid REAXB step boundary");
        if (plantController != null) {
            if (!plantController.step(dt, flag)) {
                throw new IllegalStateException("PLNTBU1 step failed");
            }
            setExternalSignals(plantController.getReactiveOutput(),
                    plantController.getActiveOutput());
        }
        if (flag == 0) {
            stage=org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.PREDICTOR;
            oldState = state(); predictor = derivatives(oldState);
            apply(advance(oldState, predictor, dt));
        } else {
            stage=org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.CORRECTOR;
            if (oldState == null || predictor == null)
                throw new IllegalStateException("corrector without predictor");
            apply(advance(oldState, predictor.add(derivatives(state())), .5*dt));
            oldState = null; predictor = null;
        }
    }
    @Override public int staggeredStateCount(){return 2;}
    @Override public void readStaggeredState(double[] values,int offset){
        if(!initialized)throw new IllegalStateException("Initialize auxiliary control first");
        values[offset]=reactiveState;values[offset+1]=activeState;
    }
    @Override public void evaluateStaggered(double stepSec,double[] values,int offset){
        if(!initialized||oldState!=null)throw new IllegalStateException("Auxiliary state is not an accepted endpoint");
        if(plantController!=null)setExternalSignals(plantController.getReactiveOutput(),plantController.getActiveOutput());
        stage=org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.STAGGERED;
        Derivative d=derivatives(state());values[offset]=d.reactive();values[offset+1]=d.active();
    }
    @Override public void applyStaggeredState(double[] values,int offset,double stepSec){
        if(!initialized||oldState!=null)throw new IllegalStateException("Auxiliary state is not an accepted endpoint");
        if(plantController!=null)setExternalSignals(plantController.getReactiveOutput(),plantController.getActiveOutput());
        apply(new State(values[offset],values[offset+1]));
    }
    private Derivative derivatives(State state) {
        double reactiveTarget = reactiveTarget();
        double activeTarget = activeTarget();
        Derivative d=data.measurementTime()<=EPS?new Derivative(0,0):new Derivative((reactiveTarget-state.reactive)/data.measurementTime(),
                (activeTarget-state.active)/data.measurementTime());
        monitor.begin(stage);monitor.record(0,state.reactive,d.reactive);monitor.record(1,state.active,d.active);return d;
    }
    private void apply(State state) {
        reactiveState = clamp(data.measurementTime()<=EPS
                        ? reactiveTarget():state.reactive,
                data.reactiveMinimum(),data.reactiveMaximum());
        activeState = clamp(data.measurementTime()<=EPS
                        ? activeTarget():state.active,
                data.activeMinimum(),data.activeMaximum());
    }
    private double reactiveTarget() {
        return data.reactiveGain()==0 ? initialReactiveOutput
                : data.reactiveGain()*(reactiveReference+externalScale*reactiveExternal);
    }
    private double activeTarget() {
        return data.activeGain()==0 ? initialActiveOutput
                : data.activeGain()*(activeReference+externalScale*activeExternal);
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
    public double getReactiveReference(){return reactiveReference;}
    public double getActiveReference(){return activeReference;}
    public Plntbu1Model getPlantController(){return plantController;}
    public void setPlantController(Plntbu1Model value){plantController=value;}
    @Override public Map<String,Double> getIntegrationStates(){return getNamedStates();}
    @Override public org.interpss.dstab.control.util.IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){
        var own=monitor.snapshot();var x=new LinkedHashMap<>(own.evaluationStates());var dx=new LinkedHashMap<>(own.rates());
        if(plantController!=null){var p=plantController.getStateDerivativeSnapshot();x.putAll(p.evaluationStates());dx.putAll(p.rates());}
        return new org.interpss.dstab.control.util.IntegrationStateMonitor.Snapshot(own.stage(),x,dx);
    }
    @Override public Map<String,Double> getNamedStates(){
        if (!initialized) return Map.of();
        Map<String,Double> states=new LinkedHashMap<>();
        states.put("Measurement lag (reactive part)",reactiveState);
        states.put("Measurement lag (real part)",activeState);
        if(plantController!=null) states.putAll(plantController.getNamedStates());
        return Map.copyOf(states);
    }
    private State state(){return new State(reactiveState,activeState);}
    private static State advance(State s,Derivative d,double dt){return new State(s.reactive+dt*d.reactive,s.active+dt*d.active);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private record State(double reactive,double active){}
    private record Derivative(double reactive,double active){Derivative add(Derivative o){return new Derivative(reactive+o.reactive,active+o.active);}}
}
