package org.interpss.dstab.relay;

import java.util.List;
import java.util.Map;
import java.util.Hashtable;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.relay.IRelayModel;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;

/** Accepted-step line relay with explicit transfer trips; monitoring alone never opens a branch. */
public final class Tiocr1RelayModel extends DynamicBusDeviceImpl implements IRelayModel, ICMLStateProvider, IntegrationStateAccess {
    private static final IntegrationStateMonitor.Snapshot NO_DIFFERENTIAL_RATES =
            new IntegrationStateMonitor.Snapshot(IntegrationStateMonitor.Stage.NOT_EVALUATED, Map.of(), Map.of());
    private final AclfBranch monitored;
    private final boolean fromEnd, operate;
    private final AclfBranch[] transfers;
    private final Tiocr1Data curve;
    private final BaseDStabBus<?,?> owner,loadHost;
    private final BaseDStabNetwork<?,?> ownerNetwork;
    private double position, breakerTimer;
    private double intervalCurrent=Double.NaN;
    private boolean pickedUp, operated;
    private long networkActionVersion;
    /** Accepted relay coordinates immediately before a physical network action. */
    public record ActionSnapshot(long revision,double current,double position,double breakerTimer,boolean operatedLatch) { }
    private ActionSnapshot lastActionSnapshot;
    private final LoadPort loadPort;
    private final Hashtable<String,Object> output=new Hashtable<>();
    private static final class LoadPort extends AbstractStagedLoadSheddingRelayModel {
        LoadPort(String id,BaseDStabBus<?,?> bus,String loadId,double fraction,boolean interpretSymbolAsWildcard) {
            super(id,bus,loadId,new StagedLoadSheddingRelayData(List.of(
                    new LoadSheddingStage(0,0,0,fraction),new LoadSheddingStage(0,0,0,0),
                    new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0),new LoadSheddingStage(0,0,0,0)),false),interpretSymbolAsWildcard);
        }
        @Override protected double monitoredValue() { return 0; }
        @Override protected boolean violates(LoadSheddingStage stage,double value) { return false; }
        void trigger() { applyStage(0); }
        boolean canTrigger() { return canApplyStage(0); }
    }

    public Tiocr1RelayModel(AclfBranch branch, boolean fromEnd, int slot, boolean operate,
            BaseDStabBus<?,?> host, String loadId, AclfBranch[] transfers, Tiocr1Data curve) {
        this(branch,fromEnd,slot,operate,host,loadId,transfers,curve,true);
    }

    /** Attach an exact load identity, including literal symbolic IDs. */
    public static Tiocr1RelayModel forExactLoad(AclfBranch branch, boolean fromEnd, int slot, boolean operate,
            BaseDStabBus<?,?> host, String loadId, AclfBranch[] transfers, Tiocr1Data curve) {
        return new Tiocr1RelayModel(branch,fromEnd,slot,operate,host,loadId,transfers,curve,false);
    }

    private Tiocr1RelayModel(AclfBranch branch, boolean fromEnd, int slot, boolean operate,
            BaseDStabBus<?,?> host, String loadId, AclfBranch[] transfers, Tiocr1Data curve,
            boolean interpretSymbolAsWildcard) {
        if(slot<1 || slot>2 || transfers==null || transfers.length!=3) throw new IllegalArgumentException("Invalid relay slot/targets");
        monitored=java.util.Objects.requireNonNull(branch);this.fromEnd=fromEnd;this.operate=operate;
        owner=(BaseDStabBus<?,?>)(fromEnd?branch.getFromAclfBus():branch.getToAclfBus());
        ownerNetwork=(BaseDStabNetwork<?,?>)java.util.Objects.requireNonNull(owner.getNetwork());
        loadHost=loadId==null?null:java.util.Objects.requireNonNull(host);
        if(!belongsToOwnerNetwork(branch)||loadHost!=null&&!busBelongsToOwnerNetwork(loadHost))
            throw new IllegalArgumentException("Relay terminal and load target must belong to one network");
        for(var transfer:transfers)if(transfer!=null&&!belongsToOwnerNetwork(transfer))
            throw new IllegalArgumentException("Transfer-trip target must belong to the monitored network");
        java.util.Objects.requireNonNull(curve);
        this.transfers=transfers.clone();this.curve=curve;
        String id="TIOCR1_"+branch.getId()+"_"+(fromEnd?"F":"T")+"_"+slot;
        setId(id);setName("TIOCR1");setExtendedDeviceId(id);setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        // Motion is owned by the monitored terminal, independently of the remote load port.
        setDStabBus(owner);owner.getDynamicBusDeviceList().add(this);
        output.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,id);
        loadPort=loadId==null?null:new LoadPort(id+"_LOAD",host,loadId,curve.loadFraction(),interpretSymbolAsWildcard);
    }
    private boolean busBelongsToOwnerNetwork(BaseDStabBus<?,?> bus) {
        return bus!=null&&bus.getNetwork()==ownerNetwork&&ownerNetwork.getBus(bus.getId())==bus;
    }
    private boolean belongsToOwnerNetwork(AclfBranch branch) {
        return branch.getFromAclfBus()!=null&&branch.getToAclfBus()!=null
                &&busBelongsToOwnerNetwork((BaseDStabBus<?,?>)branch.getFromAclfBus())
                &&busBelongsToOwnerNetwork((BaseDStabBus<?,?>)branch.getToAclfBus())
                &&ownerNetwork.getBranch(branch.getId())==branch;
    }
    private boolean attachmentIntact() {
        if(getDStabBus()!=owner||!busBelongsToOwnerNetwork(owner)
                ||(fromEnd?monitored.getFromAclfBus():monitored.getToAclfBus())!=owner
                ||!belongsToOwnerNetwork(monitored)
                ||loadPort!=null&&(loadPort.getDStabBus()!=loadHost||!busBelongsToOwnerNetwork(loadHost)))return false;
        for(var target:transfers)if(target!=null&&!belongsToOwnerNetwork(target))return false;
        return true;
    }
    @Override public boolean initStates(BaseDStabBus<?,?> bus) { return bus==owner&&attachmentIntact()&&Double.isFinite(currentMagnitude())&&reset(); }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag) {
        if(!attachmentIntact()||!Double.isFinite(dt)||dt<0)return false;
        if(!operated&&dt>0&&flag==0) {
            double current=currentMagnitude();
            if(!Double.isFinite(current))return false;
            intervalCurrent=current;
        }
        return true;
    }
    @Override public boolean updateAttributes(boolean changed) {return attachmentIntact();}
    @Override public Object getOutputObject() {return Complex.ZERO;}
    @Override public Hashtable<String,Object> getStates(Object ignored) {output.putAll(getNamedStates());return output;}
    @Override public boolean action(double time) {return true;}
    @Override public boolean isActionTime(double time) {return false;}
    public double currentMagnitude() {
        if(!monitored.isActive())return 0;
        var vf=monitored.getFromAclfBus().getVoltage();var vt=monitored.getToAclfBus().getVoltage();
        var yf=fromEnd?monitored.yff():monitored.ytf();
        var yt=fromEnd?monitored.yft():monitored.ytt();
        double real=(yf.getReal()*vf.getReal()-yf.getImaginary()*vf.getImaginary())
                +(yt.getReal()*vt.getReal()-yt.getImaginary()*vt.getImaginary());
        double imag=(yf.getReal()*vf.getImaginary()+yf.getImaginary()*vf.getReal())
                +(yt.getReal()*vt.getImaginary()+yt.getImaginary()*vt.getReal());
        return Math.hypot(real,imag);
    }
    @Override public boolean afterStep(double dt) {
        if(!attachmentIntact()||!Double.isFinite(dt)||dt<0)return false;
        if(dt==0)return true;
        if(operated){intervalCurrent=Double.NaN;return true;}
        if(operate && loadPort!=null && !loadPort.canTrigger())return false;
        double current=Double.isNaN(intervalCurrent)?currentMagnitude():intervalCurrent;
        intervalCurrent=Double.NaN;
        if(!pickedUp) {
            double rate=curve.rate(current);
            position=Double.isInfinite(rate)?0:Math.max(0,Math.min(1,position+dt*rate));
            if(position<1-1e-12)return true;
            pickedUp=true; // Breaker timer starts at pickup, without counting this interval twice.
            // A positive delay cannot operate in the pickup interval itself.
            if(curve.breakerTime()>0)return true;
        } else breakerTimer+=dt;
        // The source selects the nearest accepted endpoint after pickup. At
        // coarse steps this can be one interval earlier than a ceiling rule.
        if(breakerTimer+.5*dt+1e-12<curve.breakerTime())return true;
        operated=true;
        if(!operate)return true;
        double actionCurrent=currentMagnitude(),actionPosition=position,actionBreakerTimer=breakerTimer;
        boolean actionOperated=operated;
        var network=ownerNetwork;
        boolean networkChanged=false;
        for(var branch:transfers) if(branch!=null&&branch.isActive()) {
            // Remove only this branch stamp, preserving active fault and load-relay stamps.
            int f=branch.getFromAclfBus().getSortNumber(),t=branch.getToAclfBus().getSortNumber();
            network.getYMatrix().addToA(branch.yff().negate(),f,f);
            network.getYMatrix().addToA(branch.yft().negate(),f,t);
            network.getYMatrix().addToA(branch.ytf().negate(),t,f);
            network.getYMatrix().addToA(branch.ytt().negate(),t,t);
            branch.setStatus(false);network.setYMatrixDirty(true);
            networkChanged=true;
        }
        if(loadPort!=null) {
            double previousFraction=loadPort.getShedFraction();
            loadPort.trigger();
            networkChanged|=loadPort.getShedFraction()!=previousFraction;
        }
        if(networkChanged) {
            networkActionVersion++;
            lastActionSnapshot=new ActionSnapshot(networkActionVersion,actionCurrent,actionPosition,actionBreakerTimer,actionOperated);
        }
        // A remote action can leave this relay's monitored branch energized.
        // Only opening that branch clears its moving-element coordinate.
        if(!monitored.isActive())position=0;
        return true;
    }
    @Override public boolean reset() { position=breakerTimer=0;intervalCurrent=Double.NaN;pickedUp=operated=false;lastActionSnapshot=null;if(loadPort!=null)loadPort.reset();return true; }
    @Override public Map<String,Double> getNamedStates() {
        return Map.of("Relay position",position,"Breaker timer",breakerTimer,
                "Pickup",pickedUp?1.:0.,"Operated",operated?1.:0.,"Shed fraction",getShedFraction());
    }
    public double getShedFraction() {return loadPort==null?0:loadPort.getShedFraction();}
    public double getPosition() { return position; }
    public double getBreakerTimer() { return breakerTimer; }
    public boolean isOperated() { return operated; }
    /** Monotonic accepted physical-action revision; monitoring and reads leave it unchanged. */
    public long getNetworkActionVersion() { return networkActionVersion; }
    /** Null before an action or after reinitialization; observation does not advance protection. */
    public ActionSnapshot getLastActionSnapshot() { return lastActionSnapshot; }
    /** Exact accepted-step devices supported without creating trial ODE coordinates. */
    public static boolean isAcceptedStepDevice(Object device) {
        return device instanceof Tiocr1RelayModel || device != null && device.getClass() == LoadPort.class;
    }
    /** Relay motion and breaker timing belong to accepted steps, not trial ODE coordinates. */
    @Override public Map<String,Double> getIntegrationStates() { return Map.of(); }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() { return NO_DIFFERENTIAL_RATES; }
    @Override public Map<String,Double> getIntegrationHistories() { return getNamedStates(); }
}
