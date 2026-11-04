package org.interpss.dstab.hvdc;

import java.util.Hashtable;
import java.util.Objects;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.InitialEndpointAction;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.ConvergedNetworkAction;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.NetworkIterationAware;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Explicit two-terminal attachment to the default and staggered network lifecycle.
 * One owner advances the shared states; the companion only publishes injection.
 * Preparation and source import remain separate. Failed solves restore this
 * model's transaction, not other devices or the whole network's integration history.
 */
public final class Chvdc2TerminalPorts {
    private final Chvdc2NetworkPreparation.Binding binding;
    private final Chvdc2CoupledRuntime runtime;
    private final BaseDStabBus<?,?> rectifier,inverter;
    private final BaseDStabNetwork<?,?> network;
    private final String linkName;
    private final Port rectifierPort,inverterPort;
    private final double[] currents=new double[4];
    private Complex rectifierOutput=Complex.ZERO,inverterOutput=Complex.ZERO;
    private Complex savedRectifierOutput,savedInverterOutput;
    private boolean transaction,networkTrial,predictorTrial,clockedTrial,integrationPending,sharedIntegration,
            sharedEndpointPending,predictorConverged,initialAccepted,actionDiscontinuity;
    private boolean ramlTransitioned,commutationTransitioned;
    private double evaluationTime,sharedEndpoint,interval;
    private long sequence,savedSequence;

    public Chvdc2TerminalPorts(Chvdc2NetworkPreparation.Binding binding) {
        this.binding=Objects.requireNonNull(binding);runtime=Objects.requireNonNull(binding.runtime());
        rectifier=(BaseDStabBus<?,?>)binding.owner().getRectifier().getBus();
        inverter=(BaseDStabBus<?,?>)binding.owner().getInverter().getBus();
        network=(BaseDStabNetwork<?,?>)rectifier.getNetwork();linkName=binding.owner().getName();
        rectifierPort=new Port(binding.owner().getName()+":rectifier",true);
        inverterPort=new Port(binding.owner().getName()+":inverter",false);
    }
    /** Register before algorithm initialization. Do not separately net the static
     * link into loads: its solved transfer is replaced by these terminal currents. */
    public void attach() {
        if(rectifierPort.getDStabBus()!=null||inverterPort.getDStabBus()!=null)
            throw new IllegalStateException("Terminal pair already attached");
        if(!ownerIntact())throw new IllegalStateException("Prepared static link ownership changed; reprepare before attachment");
        for(var d:rectifier.getDynamicBusDeviceList())if(d instanceof Chvdc2TerminalPorts.Port p&&p.owns(binding.owner()))
            throw new IllegalArgumentException("Static link already has a terminal runtime");
        for(var d:rectifier.getDynamicBusDeviceList())if(rectifierPort.getId().equals(d.getId()))throw new IllegalArgumentException("Duplicate rectifier port");
        for(var d:inverter.getDynamicBusDeviceList())if(inverterPort.getId().equals(d.getId()))throw new IllegalArgumentException("Duplicate inverter port");
        rectifierPort.setDStabBus(rectifier);
        try{inverterPort.setDStabBus(inverter);}catch(RuntimeException failure){rectifierPort.setDStabBus(null);throw failure;}
    }
    public Port rectifierPort(){return rectifierPort;}
    public Port inverterPort(){return inverterPort;}
    private boolean ownerIntact(){
        return network!=null&&rectifier.getNetwork()==network&&inverter.getNetwork()==network
                &&binding.owner().getNetwork()==network&&network.getSpecialBranchList().contains(binding.owner())
                &&Objects.equals(linkName,binding.owner().getName())
                &&binding.owner().getFromBus()==rectifier&&binding.owner().getToBus()==inverter
                &&binding.owner().getRectifier()!=null&&binding.owner().getInverter()!=null
                &&binding.owner().getRectifier().getBus()==rectifier&&binding.owner().getInverter().getBus()==inverter;
    }
    private void bind() {
        if(!rectifierPort.attachmentIntact())throw new IllegalStateException("Both original terminal ports must remain attached");
        var r=rectifier.getVoltage();var i=inverter.getVoltage();
        runtime.setBoundary(r.getReal(),r.getImaginary(),i.getReal(),i.getImaginary(),binding.operatingPoint().currentAmperes(),0);
    }
    /** Called once by the owner at each network iteration, not by output getters. */
    private void publish() {
        bind();runtime.evaluateInjections(currents,0);
        rectifierOutput=new Complex(currents[0],currents[1]);inverterOutput=new Complex(currents[2],currents[3]);
    }
    private void beginTransaction() {
        if(transaction)throw new IllegalStateException("A terminal transaction is already open");
        runtime.beginTransaction();transaction=true;savedSequence=sequence;
        ramlTransitioned=false;commutationTransitioned=false;
        savedRectifierOutput=rectifierOutput;savedInverterOutput=inverterOutput;
    }
    private void rollback() {
        if(transaction){runtime.rollbackTransaction();transaction=false;sequence=savedSequence;
            rectifierOutput=savedRectifierOutput;inverterOutput=savedInverterOutput;}
        integrationPending=false;sharedIntegration=false;sharedEndpointPending=false;predictorConverged=false;
    }
    private void commit() {runtime.commitTransaction();transaction=false;}
    private void requireReady() {
        if(!initialAccepted)throw new IllegalStateException("Accept the initial electrical endpoint before integration");
    }
    public final class Port extends DynamicBusDeviceImpl implements StaggeredEulerParticipant,NetworkIterationAware,
            ConvergedNetworkAction,IntegrationStepAware,InitialEndpointAction,IntegrationStateAccess {
        private final boolean owner;
        private Port(String id,boolean owner){this.owner=owner;setId(id);setExtendedDeviceId(id);setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);}
        public boolean isStateOwner(){return owner;}
        private boolean owns(Object link){return binding.owner()==link;}
        public boolean attachmentIntact(){
            return ownerIntact()&&rectifierPort.getDStabBus()==rectifier&&inverterPort.getDStabBus()==inverter
                    &&rectifier.getDynamicBusDeviceList().contains(rectifierPort)&&inverter.getDynamicBusDeviceList().contains(inverterPort)
                    &&binding.owner().getRectifier().getBus()==rectifier&&binding.owner().getInverter().getBus()==inverter;
        }
        public boolean isOperational(){return binding.owner().isActive()&&rectifier.isActive()&&inverter.isActive()&&rectifierPort.isActive()&&inverterPort.isActive();}
        @Override public void configureIntegrationStep(double h){
            if(!Double.isFinite(h)||h!=binding.networkIntervalSeconds())
                throw new IllegalArgumentException("Reprepare CHVDC2 for the algorithm's outer integration interval");
        }
        @Override public boolean initStates(BaseDStabBus<?,?> bus){
            if(bus!=getDStabBus()||!attachmentIntact())return false;
            if(owner){
                rollback();networkTrial=false;initialAccepted=false;sequence=0;
                bind();runtime.initialize(binding.operatingPoint().rectifierVolts(),binding.operatingPoint().inverterVolts(),binding.operatingPoint().initialTimeSeconds());
                publish();
            }
            return true;
        }
        @Override public boolean acceptInitialEndpoint(){
            if(!owner||initialAccepted||!isOperational())return false;
            beginTransaction();
            try {
                bind();long before=runtime.staggeredInputDiscontinuityVersion();
                runtime.acceptBoundary(binding.operatingPoint().initialTimeSeconds(),sequence++);
                boolean changed=before!=runtime.staggeredInputDiscontinuityVersion();
                if(changed)publish();commit();initialAccepted=true;return changed;
            }catch(RuntimeException failure){rollback();throw failure;}
        }
        @Override public void beginNetworkSolution(Context context){
            if(!owner||!isOperational())return;
            if(networkTrial)throw new IllegalStateException("Nested CHVDC2 network solve");
            predictorTrial="PREDICTOR".equals(context.stage());
            boolean corrector="CORRECTOR".equals(context.stage()),direct="DIRECT".equals(context.stage());
            if(!predictorTrial&&!corrector&&!direct&&!"INITIAL".equals(context.stage()))throw new IllegalArgumentException("Unknown network stage");
            if(predictorTrial&&(!integrationPending||sharedIntegration)||corrector&&(!integrationPending||sharedIntegration||!predictorConverged))
                throw new IllegalStateException("Network stage must match the CHVDC2 integration stage");
            if(!predictorTrial&&!corrector&&integrationPending&&!(direct&&sharedEndpointPending))
                throw new IllegalStateException("Finish the pending integration endpoint first");
            if(!transaction)beginTransaction();networkTrial=true;actionDiscontinuity=false;
            // An ordinary INITIAL solve revisits the previous accepted endpoint.
            // Refresh live injections without accepting the electrical angle twice.
            clockedTrial=predictorTrial||corrector||sharedEndpointPending
                    ||!direct&&(!initialAccepted||context.externalEventBoundary());
            evaluationTime=direct&&sharedEndpointPending?sharedEndpoint:context.evaluationTime();
        }
        @Override public void beforeNetworkIteration(int iteration){if(owner&&networkTrial)publish();}
        @Override public boolean applyConvergedNetworkAction(){
            actionDiscontinuity=false;if(!owner||!networkTrial||!clockedTrial)return false;
            bind();long before=runtime.staggeredInputDiscontinuityVersion();
            boolean shorted=runtime.inverterShorted(),raml=runtime.ramlActive();
            if(predictorTrial)runtime.evaluatePredictorProtection(evaluationTime,sequence++,!ramlTransitioned,!commutationTransitioned);
            else runtime.evaluateProtectionBoundary(evaluationTime,sequence++,!ramlTransitioned,!commutationTransitioned);
            ramlTransitioned|=raml!=runtime.ramlActive();commutationTransitioned|=shorted!=runtime.inverterShorted();
            actionDiscontinuity=before!=runtime.staggeredInputDiscontinuityVersion();
            // A new protection regime must be solved before publication; its
            // electrical angle is still the previous accepted boundary's angle.
            return actionDiscontinuity||shorted!=runtime.inverterShorted();
        }
        @Override public boolean requiresIntegrationInputDiscontinuity(){return owner&&actionDiscontinuity;}
        @Override public void afterNetworkConvergence(){if(owner&&networkTrial)publish();}
        @Override public void endNetworkSolution(boolean converged){
            if(!owner||!networkTrial)return;
            try {
                if(!converged){rollback();return;}
                if(predictorTrial){predictorConverged=true;return;}
                if(clockedTrial){bind();runtime.acceptNetworkBoundary(evaluationTime,sequence++);}
                // Do not recalculate outputs after accepting the next electrical angle.
                commit();integrationPending=false;sharedIntegration=false;sharedEndpointPending=false;predictorConverged=false;
            }catch(RuntimeException failure){rollback();throw failure;}
            finally{networkTrial=false;}
        }
        @Override public Object getOutputObject(){return !isOperational()?Complex.ZERO:owner?rectifierOutput:inverterOutput;}
        @Override public boolean nextStep(double h,DynamicSimuMethod method,int stage){
            if(!owner||!isOperational())return true;requireReady();
            if(method!=DynamicSimuMethod.MODIFIED_EULER||stage<0||stage>1||!Double.isFinite(h)||h<0||h>binding.networkIntervalSeconds())
                throw new IllegalArgumentException("Matching modified-Euler stages within the prepared outer interval required");
            if(h==0)return true;
            if(stage==0&&(integrationPending||networkTrial)||stage==1&&(!integrationPending||sharedIntegration||!predictorConverged||h!=interval))
                throw new IllegalStateException("Matching predictor/network/corrector lifecycle required");
            if(stage==0){beginTransaction();integrationPending=true;interval=h;}
            try{bind();runtime.step(h,stage);return true;}catch(RuntimeException failure){rollback();throw failure;}
        }
        @Override public boolean afterStep(double h){return true;}
        @Override public boolean updateAttributes(boolean changed){return attachmentIntact();}
        @Override public Hashtable<String,Object> getStates(Object reference){
            var result=new Hashtable<String,Object>();result.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
            if(owner)result.putAll(runtime.getIntegrationStates());return result;
        }
        @Override public Map<String,Double> getIntegrationStates(){return owner?runtime.getIntegrationStates():Map.of();}
        @Override public Map<String,Double> getIntegrationHistories(){return owner?runtime.getIntegrationHistories():Map.of();}
        @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){
            return owner?runtime.getStateDerivativeSnapshot():new IntegrationStateMonitor.Snapshot(
                    IntegrationStateMonitor.Stage.NOT_EVALUATED,Map.of(),Map.of());
        }
        @Override public int staggeredStateCount(){return owner?runtime.staggeredStateCount():0;}
        // The shared solver evaluates our rates after endpoint acceptance. Use that
        // settled left endpoint at a network event, not the older integration slope.
        @Override public boolean useAcceptedEndpointRateAtNetworkDiscontinuity(){return owner;}
        @Override public long staggeredInputDiscontinuityVersion(){return owner?runtime.staggeredInputDiscontinuityVersion():0;}
        @Override public boolean readStaggeredInputDiscontinuityLeftRates(double[] values,int offset){
            if(owner)return runtime.readStaggeredInputDiscontinuityLeftRates(values,offset);
            Objects.checkFromIndexSize(offset,0,values.length);return false;
        }
        @Override public void readStaggeredState(double[] values,int offset){if(owner)runtime.readStaggeredState(values,offset);else Objects.checkFromIndexSize(offset,0,values.length);}
        @Override public void evaluateStaggered(double h,double[] values,int offset){
            if(owner){requireReady();bind();runtime.evaluateStaggered(h,values,offset);}else Objects.checkFromIndexSize(offset,0,values.length);
        }
        @Override public void applyStaggeredState(double[] values,int offset,double h){
            if(!owner){Objects.checkFromIndexSize(offset,0,values.length);return;}
            requireReady();
            if(!Double.isFinite(h)||h<0||h>binding.networkIntervalSeconds()||networkTrial||integrationPending&&!sharedIntegration)
                throw new IllegalStateException("Shared state proposal requires an idle prepared integration owner");
            if(h>0&&!transaction){beginTransaction();integrationPending=true;sharedIntegration=true;interval=h;}
            try{runtime.applyStaggeredState(values,offset,h);}catch(RuntimeException failure){rollback();throw failure;}
        }
        @Override public void completeStaggeredStep(double h,double endpointTime){
            if(!owner||!isOperational())return;
            if(!Double.isFinite(endpointTime)||!Double.isFinite(h)||h<0||h>binding.networkIntervalSeconds()
                    ||h>0&&(!integrationPending||!sharedIntegration||h!=interval||sharedEndpointPending))
                throw new IllegalStateException("Complete the matching shared integration proposal once");
            if(h==0)return;
            try{runtime.completeStaggeredStep(h);sharedEndpoint=endpointTime;sharedEndpointPending=true;}
            catch(RuntimeException failure){rollback();throw failure;}
        }
    }
}
