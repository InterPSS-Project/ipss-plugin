package org.interpss.dstab.hvdc;

import java.util.Hashtable;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.dstab.control.util.IntegrationStepAware;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.NetworkIterationAware;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Explicit typed attachment with optional transactional protection; not a complete CDC6T import path. */
public final class Cdc6tNormalTerminalPorts {
    private final BaseDStabBus<?,?> rectifier,inverter;
    private final Cdc6tNormalRuntime runtime;
    private final Cdc6tProtectionCoordinator protection;
    private final double initialVi,initialCurrent,initialVr;
    private Complex initialRectifierInjection,initialInverterInjection;
    private boolean initialInjectionPending,initialNetworkPending;
    private final double[] currents=new double[4];
    private final Port rectifierPort,inverterPort;
    private final double[] electrical=new double[8];
    private boolean coordinatorTrial,networkTrial,commitNetworkTrial,staggeredEndpointPending,boundaryTrial,predictorNetworkTrial;
    private double staggeredEndpoint,acceptedDcVoltage,trialDcVoltage,protectionTrialTime;
    private long inputDiscontinuityVersion;
    private Cdc6tNormalRuntime.OperatingMode acceptedMode=Cdc6tNormalRuntime.OperatingMode.NORMAL;
    private java.util.function.Consumer<Cdc6tNetworkTransition> transitionObserver;
    private java.util.ArrayList<Cdc6tNetworkTransition> transitions;
    private Cdc6tNetworkTransition.Boundary pendingTransitionLeft;
    private Cdc6tNormalRuntime.OperatingMode pendingBeforeMode,pendingAfterMode;
    private double[] transitionStates;

    public Cdc6tNormalTerminalPorts(String id,BaseDStabBus<?,?> rectifier,BaseDStabBus<?,?> inverter,
            Cdc6tNormalRuntime runtime,double initialVi,double initialCurrent,double initialVr) {
        this(id,rectifier,inverter,runtime,initialVi,initialCurrent,initialVr,null);
    }
    public Cdc6tNormalTerminalPorts(String id,BaseDStabBus<?,?> rectifier,BaseDStabBus<?,?> inverter,
            Cdc6tNormalRuntime runtime,double initialVi,double initialCurrent,double initialVr,Cdc6tProtectionCoordinator protection) {
        this.rectifier=java.util.Objects.requireNonNull(rectifier);this.inverter=java.util.Objects.requireNonNull(inverter);
        this.runtime=java.util.Objects.requireNonNull(runtime);
        this.protection=protection;
        if(rectifier==inverter||rectifier.getNetwork()==null||rectifier.getNetwork()!=inverter.getNetwork())
            throw new IllegalArgumentException("Distinct terminals in one network required");
        if(id==null||id.isBlank())throw new IllegalArgumentException("Named link required");
        for(double value:new double[]{initialVi,initialCurrent,initialVr})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Finite nonnegative solved DC boundary required");
        this.initialVi=initialVi;this.initialCurrent=initialCurrent;this.initialVr=initialVr;
        acceptedDcVoltage=initialVi;
        rectifierPort=new Port(id+":rectifier",rectifier,true);inverterPort=new Port(id+":inverter",inverter,false);
    }
    /** Explicit registration; callers must not separately convert static external DC power into load. */
    public void attach() {
        if(rectifier.getDynamicBusDeviceList().contains(rectifierPort)||inverter.getDynamicBusDeviceList().contains(inverterPort))
            throw new IllegalStateException("Terminal pair already attached");
        for(var device:rectifier.getDynamicBusDeviceList())if(rectifierPort.getId().equals(device.getId()))throw new IllegalArgumentException("Duplicate rectifier port");
        for(var device:inverter.getDynamicBusDeviceList())if(inverterPort.getId().equals(device.getId()))throw new IllegalArgumentException("Duplicate inverter port");
        // EMF maintains inverse list membership when the bus reference is assigned.
        rectifierPort.setDStabBus(rectifier);inverterPort.setDStabBus(inverter);
    }
    public Port rectifierPort(){return rectifierPort;}
    public Port inverterPort(){return inverterPort;}
    /** Optional event recording; disabled by default and allocates snapshots only
     * when a transition occurs. Configure outside a network/integration trial.
     * Notifications occur after successful accepted-history commit. A recorder
     * exception propagates after that commit, like an output-export failure. */
    public void setTransitionObserver(java.util.function.Consumer<Cdc6tNetworkTransition> observer){
        if(boundaryTrial||coordinatorTrial)throw new IllegalStateException("Configure transition recording outside a trial");
        transitionObserver=observer;
        if(observer!=null&&transitions==null){transitions=new java.util.ArrayList<>(2);transitionStates=new double[3];}
        clearTransitions();
    }
    private void clearTransitions(){pendingTransitionLeft=null;if(transitions!=null)transitions.clear();}
    private Cdc6tNetworkTransition.Boundary transitionBoundary(){
        runtime.readConvergedBoundaryState(transitionStates,0);
        var vr=rectifier.getVoltage();var vi=inverter.getVoltage();
        return new Cdc6tNetworkTransition.Boundary(vr.getReal(),vr.getImaginary(),vi.getReal(),vi.getImaginary(),
                transitionStates[0],transitionStates[1],transitionStates[2],electrical[0],electrical[1],electrical[2],
                electrical[3],electrical[4],electrical[5],electrical[6],electrical[7]);
    }
    /** Preserve the solved static injection while the solver captures representation
     * compensation. The dynamic control change is applied at the initial endpoint. */
    void prepareInitialControlTransfer(Complex rectifierPower,Complex inverterPower) {
        if(rectifierPort.getDStabBus()!=null||inverterPort.getDStabBus()!=null)
            throw new IllegalStateException("Prepare initial control transfer before attachment");
        initialRectifierInjection=rectifierPower.divide(rectifier.getVoltage()).conjugate().negate();
        initialInverterInjection=inverterPower.divide(inverter.getVoltage()).conjugate().negate();
    }
    private void refresh() {
        if(!rectifierPort.attachmentIntact())throw new IllegalStateException("Both original DC terminal ports must remain attached");
        Complex vr=rectifier.getVoltage(),vi=inverter.getVoltage();
        if(protection==null)runtime.evaluateBoundary(vr.getReal(),vr.getImaginary(),vi.getReal(),vi.getImaginary(),0,currents,0);
        else {
            var mode=protection.operatingMode();
            boolean recovery=mode==Cdc6tNormalRuntime.OperatingMode.RECOVERING;
            runtime.evaluateBoundary(vr.getReal(),vr.getImaginary(),vi.getReal(),vi.getImaginary(),0,mode,
                    recovery?protection.voltageFactor():1,recovery?protection.currentFactor():1,currents,0);
        }
    }
    public final class Port extends DynamicBusDeviceImpl implements StaggeredEulerParticipant,NetworkIterationAware,IntegrationStepAware,org.interpss.dstab.control.util.InitialEndpointAction,com.interpss.dstab.device.ConvergedNetworkAction {
        private final boolean owner;
        private Port(String id,BaseDStabBus<?,?> bus,boolean owner) {
            this.owner=owner;setId(id);setExtendedDeviceId(id);setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        }
        public boolean isStateOwner(){return owner;}
        @Override public void configureIntegrationStep(double outerStep){if(owner)runtime.configureIntegrationStep(outerStep);}
        public boolean attachmentIntact() {
            return rectifierPort.getDStabBus()==rectifier&&inverterPort.getDStabBus()==inverter
                &&rectifier.getDynamicBusDeviceList().contains(rectifierPort)&&inverter.getDynamicBusDeviceList().contains(inverterPort);
        }
        public boolean isOperational(){return rectifier.isActive()&&inverter.isActive()&&rectifierPort.isActive()&&inverterPort.isActive();}
        @Override public boolean initStates(BaseDStabBus<?,?> bus) {
            if(bus!=getDStabBus()||!attachmentIntact())return false;
            if(protection!=null){if(coordinatorTrial)protection.discardTrial();protection.reset();}
            coordinatorTrial=false;networkTrial=false;boundaryTrial=false;staggeredEndpointPending=false;acceptedDcVoltage=initialVi;
            inputDiscontinuityVersion=0;acceptedMode=Cdc6tNormalRuntime.OperatingMode.NORMAL;
            clearTransitions();
            initialInjectionPending=initialRectifierInjection!=null;initialNetworkPending=false;
            runtime.initialize(initialVi,initialCurrent,initialVr);return true;
        }
        @Override public boolean acceptInitialEndpoint() {
            if(!owner||!initialInjectionPending)return false;
            initialInjectionPending=false;initialNetworkPending=true;return true;
        }
        @Override public void acceptInitialNetworkEndpoint() {
            if(!owner||!initialNetworkPending)return;
            refresh();runtime.readBoundary(electrical,0);
            runtime.initializeFromBoundary();
            acceptedDcVoltage=electrical[7];initialNetworkPending=false;
        }
        @Override public void beginNetworkSolution(NetworkIterationAware.Context context){
            if(!owner||!isOperational())return;
            if(boundaryTrial)throw new IllegalStateException("Nested CDC6T network trial");
            boundaryTrial=true;
            clearTransitions();
            predictorNetworkTrial="PREDICTOR".equals(context.stage());
            // Initial settling has no simulation-clock history; callers may
            // still choose negative-time startup after initialization returns.
            if(protection==null||initialNetworkPending)return;
            boolean directEndpoint="DIRECT".equals(context.stage())&&staggeredEndpointPending;
            if("DIRECT".equals(context.stage())&&!directEndpoint)return; // Initialization/direct probes cannot commit a clock.
            if(!directEndpoint&&!"INITIAL".equals(context.stage())&&!"PREDICTOR".equals(context.stage())&&!"CORRECTOR".equals(context.stage()))
                throw new IllegalArgumentException("Unknown CDC6T network stage: "+context.stage());
            double evaluationTime=directEndpoint?staggeredEndpoint:context.evaluationTime();
            if(coordinatorTrial&&"CORRECTOR".equals(context.stage())){
                if(evaluationTime!=protectionTrialTime){
                    protection.discardTrial();coordinatorTrial=false;
                    throw new IllegalStateException("Corrector must finish the pending CDC6T endpoint");
                }
                // Retain continuous recovery factors predicted at this endpoint.
                // Discrete protection is first observed after the differential corrector,
                // so its right-hand input cannot leak into the interval just completed.
                networkTrial=true;commitNetworkTrial=true;return;
            }
            if(coordinatorTrial){protection.discardTrial();coordinatorTrial=false;}
            protection.beginTrial(evaluationTime);protectionTrialTime=evaluationTime;
            coordinatorTrial=true;networkTrial=true;commitNetworkTrial=!"PREDICTOR".equals(context.stage());
            trialDcVoltage=acceptedDcVoltage;
        }
        @Override public void beforeNetworkIteration(int iteration){
            if(!owner)return;
            if(initialNetworkPending){refresh();runtime.initializeFromBoundary();return;}
            if(!networkTrial)return;
            // Protection acts on the freshly converged left-hand boundary, never
            // on the preceding endpoint or an unconverged voltage iterate.
            refresh();runtime.readBoundary(electrical,0);trialDcVoltage=electrical[7];
        }
        @Override public boolean applyConvergedNetworkAction(){
            if(!owner||!networkTrial||initialNetworkPending||predictorNetworkTrial)return false;
            refresh();runtime.readBoundary(electrical,0);trialDcVoltage=electrical[7];
            Cdc6tNetworkTransition.Boundary sharedBoundary=null;
            if(pendingTransitionLeft!=null){
                sharedBoundary=transitionBoundary();
                transitions.add(new Cdc6tNetworkTransition(protectionTrialTime,pendingBeforeMode,pendingAfterMode,pendingTransitionLeft,sharedBoundary));
                pendingTransitionLeft=null;
            }
            var before=protection.operatingMode();
            double voltageFactor=protection.voltageFactor(),currentFactor=protection.currentFactor();
            protection.observe(rectifier.getVoltageMag(),inverter.getVoltageMag(),trialDcVoltage);
            var after=protection.operatingMode();
            boolean changed=before!=after||voltageFactor!=protection.voltageFactor()||currentFactor!=protection.currentFactor();
            if(changed&&transitionObserver!=null){
                pendingTransitionLeft=sharedBoundary!=null?sharedBoundary:transitionBoundary();
                pendingBeforeMode=before;pendingAfterMode=after;
            }
            return changed;
        }
        @Override public void afterNetworkConvergence(){
            if(!owner||!boundaryTrial)return;
            refresh();runtime.readBoundary(electrical,0);trialDcVoltage=electrical[7];
        }
        @Override public void endNetworkSolution(boolean converged){
            if(!owner||!boundaryTrial)return;
            try{
                if(converged)runtime.acceptAlgebraicMeasurements();
                if(!networkTrial)return;
                if(!converged){protection.discardTrial();coordinatorTrial=false;}
                else if(commitNetworkTrial){
                    protection.acceptTrial();coordinatorTrial=false;acceptedDcVoltage=trialDcVoltage;
                    var mode=protection.operatingMode();
                    if(mode!=acceptedMode){inputDiscontinuityVersion++;acceptedMode=mode;}
                    if(transitionObserver!=null)for(var transition:transitions)transitionObserver.accept(transition);
                }
                // A successful predictor retains provisional history through the same endpoint's
                // corrector. A failed corrector discards it; a different solve starts a fresh trial.
            } finally {clearTransitions();boundaryTrial=false;networkTrial=false;if(commitNetworkTrial)staggeredEndpointPending=false;}
        }
        @Override public Object getOutputObject() {
            if(!isOperational())return Complex.ZERO;
            if(initialInjectionPending)return owner?initialRectifierInjection:initialInverterInjection;
            refresh();int offset=owner?0:2;return new Complex(currents[offset],currents[offset+1]);
        }
        @Override public boolean nextStep(double dt,DynamicSimuMethod method,int stage) {
            if(!owner||!isOperational())return true;
            requireInitialEndpoint();
            if(method!=DynamicSimuMethod.MODIFIED_EULER)throw new IllegalArgumentException("Use modified Euler or the shared staggered participant");
            if(stage==0&&dt>0)runtime.sampleCommandMeasurements();
            refresh();runtime.step(dt,stage);return true;
        }
        @Override public boolean afterStep(double dt){return true;}
        @Override public boolean updateAttributes(boolean changed){return attachmentIntact();}
        @Override public Hashtable<String,Object> getStates(Object reference) {
            var result=new Hashtable<String,Object>();
            result.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
            if(owner)result.putAll(runtime.getIntegrationStates());return result;
        }
        @Override public int staggeredStateCount(){return owner?3:0;}
        @Override public long staggeredInputDiscontinuityVersion(){return owner?inputDiscontinuityVersion:0;}
        @Override public void readStaggeredState(double[] values,int offset){if(owner)runtime.readStaggeredState(values,offset);else java.util.Objects.checkFromIndexSize(offset,0,values.length);}
        @Override public void evaluateStaggered(double h,double[] values,int offset){if(owner){requireInitialEndpoint();refresh();runtime.evaluateStaggered(h,values,offset);}else java.util.Objects.checkFromIndexSize(offset,0,values.length);}
        private void requireInitialEndpoint(){
            if(initialInjectionPending||initialNetworkPending)
                throw new IllegalStateException("Settle the initial CDC6T control transfer before integrating states");
        }
        @Override public void applyStaggeredState(double[] values,int offset,double h){if(owner)runtime.applyStaggeredState(values,offset,h);else java.util.Objects.checkFromIndexSize(offset,0,values.length);}
        @Override public void completeStaggeredStep(double h,double endpointTime){
            if(!owner||!isOperational())return;
            if(!Double.isFinite(endpointTime)||!Double.isFinite(h)||h<0)throw new IllegalArgumentException("Finite CDC6T endpoint and nonnegative interval required");
            if(h>0)runtime.sampleCommandMeasurements();
            if(protection==null)return;
            staggeredEndpoint=endpointTime;staggeredEndpointPending=true;
        }
    }
}
