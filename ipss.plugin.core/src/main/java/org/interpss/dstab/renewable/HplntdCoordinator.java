package org.interpss.dstab.renewable;

import java.util.*;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Shared typed bus-plant composition. One plant/lag-vector advance per solver
 * stage, independent of downstream traversal order. Main import and physical
 * switching are separate responsibilities; unsupported host modes fail early. */
public final class HplntdCoordinator implements ICMLStateProvider {
    public record Binding(Regca1Model converter,ReaxdData data) {}
    public static final class Port {
        private final HplntdCoordinator owner;
        private final Regca1Model converter;
        private final Reeca1Model electrical;
        private final ReaxdData data;
        private final HplntdAggregation.Key key;
        private final double mva;
        private double p0,q0,publishedP,publishedQ;
        private boolean initialized;
        private ReaxdKernel lag;
        private ReaxdKernel.Input heldInput=new ReaxdKernel.Input(0,0);
        private Port(HplntdCoordinator owner,Binding b){
            this.owner=owner;converter=b.converter();data=b.data();electrical=converter.getReeca1Controller();
            key=new HplntdAggregation.Key(converter.getDStabBus().getId(),converter.getParentGen().getId());mva=converter.getParentGen().getMvaBase();
        }
        public HplntdCoordinator owner(){return owner;}
        public Regca1Model converter(){return converter;}
        public ReaxdData data(){return data;}
        public void initialize(double p,double q){owner.initialize(this,p,q);}
        public void step(double dt,int stage){owner.step(this,dt,stage);}
        public double activeIncrement(){return publishedP-p0;}
        public double reactiveIncrement(){return publishedQ-q0;}
        public double[] states(){if(lag==null)throw new IllegalStateException("Initialize the hybrid group first");return lag.states();}
        private boolean active(){return converter.getDStabBus().isActive()&&converter.getParentGen().isActive();}
        private boolean eligible(){return active()&&converter.getDStabBus().isGenPV();}
    }
    private enum Phase { IDLE,PREDICTED,CORRECTED }
    private final HplntdData data;
    private final HplntdNetworkBoundary boundary;
    private final List<Port> ports;
    private HplntdAggregation aggregation;
    private HplntdAggregation.Snapshot snapshot;
    private HplntdKernel kernel;
    private Phase phase=Phase.IDLE;
    private Set<Port> expected=Set.of();
    private final Set<Port> seen0=new HashSet<>(),seen1=new HashSet<>();
    private double pendingStep;
    private long predictors,correctors;
    private HplntdCoordinator(HplntdData data,BaseDStabBus<?,?> plantBus,List<Binding> bindings){
        this.data=Objects.requireNonNull(data);boundary=new HplntdNetworkBoundary(data,plantBus);
        if(data.frequencyFlag()!=1||data.reactiveFlag()!=1||data.switchingFlag()!=0||data.value(HplntdData.Parameter.KI)<=0||data.value(HplntdData.Parameter.KIG)<=0||boundary.ignored())
            throw new IllegalArgumentException("Typed hybrid composition requires enabled positive-integral loops, a measurement branch and disabled automatic switching");
        if(bindings.isEmpty())throw new IllegalArgumentException("Hybrid downstream devices required");
        var list=new ArrayList<Port>();var keys=new HashSet<HplntdAggregation.Key>();
        for(var b:bindings){
            Objects.requireNonNull(b);var c=Objects.requireNonNull(b.converter());var d=Objects.requireNonNull(b.data());var e=c.getReeca1Controller();
            if(c.getDStabBus().getNetwork()!=plantBus.getNetwork()||c.getParentGen().getDynamicGenDevice()!=c||e==null||e.getPlantController()!=null||e.getRepccController()!=null||e.getHybridController()!=null||e.getWeakGridController()!=null||e.getWindControlStack()!=null)
                throw new IllegalArgumentException("One unused REGCA1/REECA1 reference port on this network required per hybrid unit");
            if(c.getIntegrationScheme()!=Regca1Model.IntegrationScheme.MODIFIED_EULER||!c.getDStabBus().isGenPV()||!c.getDStabBus().isActive()||!c.getParentGen().isActive()||!Double.isFinite(c.getParentGen().getMvaBase())||c.getParentGen().getMvaBase()<=0)
                throw new IllegalArgumentException("Typed hybrid units require active PV hosts, positive MVA and staged modified Euler");
            if(e.getData().pfFlag()!=0||e.getData().qFlag()==1&&e.getData().vFlag()==0)
                throw new IllegalArgumentException("Hybrid downstream controller must receive reactive-power commands");
            if(!d.plantModel().equals(HplntdData.NATIVE_MODEL)||!("Bus"+d.plantBus()).equals(plantBus.getId())||!Arrays.equals(d.relativeVariables(),new int[]{37,38,39,42,43,44,45})||d.value(ReaxdData.Parameter.KZ)<0||d.value(ReaxdData.Parameter.KW)<0||d.value(ReaxdData.Parameter.KZ)==0&&d.value(ReaxdData.Parameter.KW)==0)
                throw new IllegalArgumentException("Typed hybrid units require the exact default relative links and nonnegative participation with a nonzero axis");
            var p=new Port(this,b);if(!keys.add(p.key))throw new IllegalArgumentException("Duplicate hybrid downstream identity "+p.key);list.add(p);
        }
        list.sort(Comparator.comparing((Port p)->p.key.bus()).thenComparing(p->p.key.generator()));ports=List.copyOf(list);
    }
    /** Serial preflight checks every port before attaching any of them. */
    public static HplntdCoordinator attach(HplntdData data,BaseDStabBus<?,?> bus,List<Binding> bindings){
        var group=new HplntdCoordinator(data,bus,bindings);for(var p:group.ports)p.electrical.setHybridController(p);return group;
    }
    private void initialize(Port p,double active,double reactive){
        if(kernel!=null){kernel=null;aggregation=null;snapshot=null;phase=Phase.IDLE;seen0.clear();seen1.clear();predictors=correctors=0;for(var member:ports){member.initialized=false;member.lag=null;member.heldInput=new ReaxdKernel.Input(0,0);}}
        if(p.initialized)throw new IllegalStateException("Duplicate initialization before all hybrid units are ready");
        var lag=new ReaxdKernel(p.data);lag.initialize(active,reactive);p.lag=lag;p.p0=p.publishedP=active;p.q0=p.publishedQ=reactive;p.initialized=true;
        if(ports.stream().allMatch(member->member.initialized)){
            var next=new HplntdAggregation(data.value(HplntdData.Parameter.PLANT_MVA),devices());var s=next.refresh(devices());
            var in=boundary.measure(s.plantMva()).control();var k=new HplntdKernel(data);
            k.initialize(new HplntdInitialization.Measurements(in.regulatedVoltage(),in.branchCurrent(),in.active(),in.reactive(),s.initialActive(),s.initialReactive()));
            aggregation=next;snapshot=s;kernel=k;
        }
    }
    private List<HplntdAggregation.Device> devices(){
        var result=new ArrayList<HplntdAggregation.Device>();for(var p:ports){
            if(p.converter.getParentGen().getDynamicGenDevice()!=p.converter||p.converter.getActiveElectricalController()!=p.electrical||p.electrical.getHybridController()!=p||p.converter.getParentGen().getMvaBase()!=p.mva)
                throw new IllegalStateException("Hybrid device ownership or MVA changed; rebuild/reinitialize the group");
            result.add(new HplntdAggregation.Device(p.key,p.mva,p.data.value(ReaxdData.Parameter.KZ),p.data.value(ReaxdData.Parameter.KW),p.p0*p.mva,p.q0*p.mva,p.eligible()));
        }return result;
    }
    private Set<Port> activePorts(){var result=new HashSet<Port>();for(var p:ports)if(p.active())result.add(p);return Set.copyOf(result);}
    private void step(Port port,double dt,int stage){
        if(!Double.isFinite(dt)||dt<0||stage<0||stage>1)throw new IllegalArgumentException("Hybrid step requires finite dt >= 0 and stage zero/one");
        if(kernel==null){if(dt==0)return;throw new IllegalStateException("Initialize all hybrid units before advancing");}
        if(!port.active())return;
        if(dt==0)return; // Network observation never integrates the shared group.
        if(stage==0){
            if(phase==Phase.CORRECTED){if(!seen1.equals(expected))throw new IllegalStateException("Missing hybrid unit corrector callback");phase=Phase.IDLE;}
            if(phase==Phase.IDLE){expected=activePorts();seen0.clear();seen1.clear();pendingStep=dt;snapshot=aggregation.refresh(devices());if(snapshot.ignored())throw new IllegalStateException("All participating hybrid units are absent; reinitialize before resuming");advance(dt,0);phase=Phase.PREDICTED;}
            if(phase!=Phase.PREDICTED||dt!=pendingStep||!expected.equals(activePorts())||!seen0.add(port))throw new IllegalStateException("Invalid or repeated hybrid predictor callback");
        }else{
            if(phase==Phase.IDLE||dt!=pendingStep||!seen0.equals(expected)||!expected.equals(activePorts()))throw new IllegalStateException("All matching hybrid predictors are required before correction");
            if(phase==Phase.PREDICTED){advance(dt,1);phase=Phase.CORRECTED;}
            if(!seen1.add(port))throw new IllegalStateException("Repeated hybrid corrector callback");
        }
    }
    private void advance(double dt,int stage){
        var in=boundary.measure(snapshot.plantMva()).control();kernel.step(dt,in,stage);var output=kernel.output(in);
        for(var p:ports){var dispatch=snapshot.dispatch(p.key,output.active(),output.reactive());if(dispatch.enabled())p.heldInput=ReaxdKernel.Input.from(dispatch);p.lag.step(dt,p.heldInput,stage);if(dispatch.enabled()){var command=p.lag.output();p.publishedP=command.active();p.publishedQ=command.reactive();}}
        if(stage==0)predictors++;else correctors++;
    }
    public List<Port> ports(){return ports;}
    public HplntdKernel kernel(){if(kernel==null)throw new IllegalStateException("Initialize the hybrid group first");return kernel;}
    public HplntdAggregation.Snapshot snapshot(){kernel();return snapshot;}
    public long predictorCount(){return predictors;}
    public long correctorCount(){return correctors;}
    @Override public Map<String,Double> getNamedStates(){
        if(kernel==null)return Map.of();var states=new LinkedHashMap<>(kernel.getNamedStates());for(var p:ports)p.lag.getNamedStates().forEach((name,value)->states.put(p.key.bus()+"/"+p.key.generator()+"."+name,value));return Collections.unmodifiableMap(states);
    }
}
