package org.interpss.dstab.algo;

import java.util.*;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import org.interpss.dstab.control.gov.wecc.urgs3t.Urgs3tGovernor;
import org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1Governor;
import org.interpss.dstab.control.util.DisconnectedStateParticipant;

/** Prepared, opt-in retained-coordinate lifecycle. Physical outputs remain disconnected. */
public final class MachineReactivationLifecycle {
    private final List<Entry> entries = new ArrayList<>();
    private boolean pending;
    private DynamicSimuAlgorithm algorithm;
    private double lastSampleTime = Double.NaN;
    private static final class Entry {
        final BaseDStabBus<?,?> bus; final DStabGen gen; final RoundRotorMachineImpl machine;
        final DisconnectedStateParticipant governor;
        final double[] last, previous, start, trial;
        boolean connected = true, firstInactive = true, trialInactive;
        double reactivationTime=Double.NaN;
        com.interpss.core.acsc.BaseAcscBus.SequenceEquivalentState savedSequenceCache;
        Entry(BaseDStabBus<?,?> b,DStabGen g,RoundRotorMachineImpl m,DisconnectedStateParticipant c) {
            bus=b;gen=g;machine=m;governor=c;
            int size=6+c.disconnectedStateCount();
            last=new double[size];previous=new double[size];start=new double[size];trial=new double[size];
        }
        void checkOwner() {
            if(gen.getDynamicGenDevice()!=machine||machine.getParentGen()!=gen||machine.getDStabBus()!=bus
                    ||bus.getContributeGen(gen.getId())!=gen||machine.getGovernor()!=governor
                    ||machine.getGovernor().getMachine()!=machine
                    ||machine.getExciter()!=null||machine.getStabilizer()!=null)
                throw new IllegalStateException("Prepared hot-reactivation ownership changed");
        }
        boolean active(){return bus.isActive()&&gen.isActive()&&machine.isActive();}
        void read(double[] v) {
            v[0]=machine.getSpeed();v[1]=machine.getAngle();v[2]=machine.getEq1();
            v[3]=machine.getPsikd();v[4]=machine.getPsikq();v[5]=machine.getEd1();
            governor.readDisconnectedState(v,6);
        }
        void write(double[] v) {
            governor.writeDisconnectedState(v,6);
            machine.setSpeed(v[0]);machine.setAngle(v[1]);machine.setEq1(v[2]);
            machine.setPsikd(v[3]);machine.setPsikq(v[4]);machine.setEd1(v[5]);
        }
        void sample() {
            // Sample an active accepted endpoint, restoring numerical scratch afterward.
            var mechanical=machine.captureMechanicalIntegrationState();
            var electrical=machine.captureRoundElectricalState();
            double pe=machine.getPe(),pm=machine.getPm(),efd=machine.getEfd();read(start);
            try {
                machine.nextStepMechanical(0,DynamicSimuMethod.MODIFIED_EULER,bus.getNetwork(),0);
                machine.nextStepElectrical(0,DynamicSimuMethod.MODIFIED_EULER,bus.getNetwork(),0);
                var m=machine.captureMechanicalIntegrationState();var e=machine.captureRoundElectricalState();
                trial[0]=m.speedDerivative();trial[1]=m.angleDerivative();trial[2]=e.dx3Eq1();
                trial[3]=e.dx4Psid1();trial[4]=e.dx5Psiq1();trial[5]=e.dx6Ed1();
                governor.readConnectedRates(trial,6);
                for(double rate:trial)if(!Double.isFinite(rate))throw new IllegalStateException("Nonfinite accepted derivative");
            } finally {
                machine.restoreMechanicalIntegrationState(mechanical);machine.restoreRoundElectricalState(electrical);
                write(start);machine.setPe(pe);machine.setPm(pm);machine.setEfd(efd);
            }
        }
    }
    private static void validateConsumers(DynamicSimuAlgorithm algorithm,Set<String> requested) {
        if(!algorithm.getNetwork().isPositiveSeqDataOnly())throw new IllegalArgumentException("Mixed-sequence hot reactivation is not qualified");

        for(var bus:algorithm.getNetwork().getBusList())if(bus.isActive()) {
            for(var device:bus.getDynamicBusDeviceList())if(device.isActive())
                throw new IllegalArgumentException("Composed bus-device reactivation inputs are not qualified");
            for(var gen:bus.getContributeGenList())if(gen.isActive()) {
                var device=gen.getDynamicGenDevice();if(device!=null&&requested.contains(device.getId()))continue;
                if(!(device instanceof com.interpss.dstab.mach.impl.EConstMachineImpl machine)
                        ||machine.getGovernor()!=null||machine.getExciter()!=null||machine.getStabilizer()!=null)
                    throw new IllegalArgumentException("Only controller-free classical surviving sources are qualified");
            }
        }
        for(var branch:algorithm.getNetwork().getBranchList())if(branch.isActive())
            for(var device:branch.getDynamicBranchDeviceList())if(device.isActive())
                throw new IllegalArgumentException("Composed branch-device reactivation inputs are not qualified");
    }
    public void prepare(DynamicSimuAlgorithm algorithm,Set<String> requested) {
        if(requested.isEmpty()){entries.clear();pending=false;lastSampleTime=Double.NaN;return;}
        validateConsumers(algorithm,requested);
        List<Entry> prepared=new ArrayList<>();Set<String> found=new HashSet<>();
        for(var bus:algorithm.getNetwork().getBusList())for(var gen:bus.getContributeGenList()) {
            var device=gen.getDynamicGenDevice();if(device==null||!requested.contains(device.getId()))continue;
            if(!(device instanceof RoundRotorMachineImpl machine)||!bus.isActive()||!gen.isActive()||!machine.isActive()
                    ||machine.getExciter()!=null||machine.getStabilizer()!=null
                    ||!(machine.getGovernor() instanceof DisconnectedStateParticipant governor)
                    ||!governor.supportsDisconnectedStateLifecycle())
                throw new IllegalArgumentException("Unsupported requested hot-reactivation unit: "+device.getId());
            if(!found.add(device.getId()))throw new IllegalArgumentException("Duplicate requested machine identity: "+device.getId());
            // Reject custom buses without an exact cache transaction before any event/status mutation.
            var cache=bus.captureSequenceEquivalentState();bus.restoreSequenceEquivalentState(cache);
            prepared.add(new Entry(bus,gen,machine,governor));
        }
        if(!found.equals(requested))throw new IllegalArgumentException("Hot-reactivation unit is missing or unprepared");
        validateGroup(prepared);
        this.algorithm=algorithm;entries.clear();entries.addAll(prepared);pending=false;lastSampleTime=Double.NaN;
    }
    private static void validateGroup(List<Entry> prepared) {
        if(prepared.size()==1&&prepared.get(0).governor instanceof Urgs3tGovernor)return;
        if(prepared.size()==2) {
            var owner=Wsieg1Governor.sharedOwner(prepared.get(0).governor);
            if(owner!=null&&Wsieg1Governor.sharedOwner(prepared.get(1).governor)==owner
                    &&prepared.stream().anyMatch(e->e.machine==owner.getMachine())
                    &&prepared.stream().anyMatch(e->e.machine==owner.getSecondaryMachine()))return;
        }
        throw new IllegalArgumentException("Qualified lifecycle requires one retained host or one complete shared-turbine pair");
    }
    public boolean enabled(){return !entries.isEmpty();}
    public boolean owns(Object model){for(var e:entries)if(e.machine==model||e.governor==model)return true;return false;}
    public void sampleBeforeEvents(double time) {
        if(entries.isEmpty())return;
        requireBoundary();if(!Double.isFinite(time)||Double.isFinite(lastSampleTime)&&time<lastSampleTime)
            throw new IllegalArgumentException("Accepted time must be finite and ordered");
        for(var e:entries)e.checkOwner();
        if(time==lastSampleTime)return;
        for(var e:entries)if(e.active())e.sample();
        for(var e:entries)if(e.active()) {
            System.arraycopy(e.last,0,e.previous,0,e.last.length);System.arraycopy(e.trial,0,e.last,0,e.last.length);
            if(!Double.isFinite(lastSampleTime))System.arraycopy(e.last,0,e.previous,0,e.last.length);
        }
        lastSampleTime=time;
    }
    public void acceptStatusChanges() {
        requireBoundary();for(var e:entries)e.checkOwner();
        for(var e:entries)if(e.connected&&!e.active()) {
            e.governor.acceptHostDisconnection();e.machine.setEfd(0);e.machine.setPm(0);
            e.connected=false;e.firstInactive=true;
        } else if(!e.connected&&e.active())throw new IllegalStateException("Use explicit hot-reactivation API");
    }
    public void begin(double dt) {
        if(entries.isEmpty())return;
        requireBoundary();if(!Double.isFinite(dt)||dt<0)throw new IllegalArgumentException("Invalid inactive interval");
        for(var e:entries)e.checkOwner();
        if(dt==0)return;
        for(var e:entries) {
            e.trialInactive=!e.active();if(!e.trialInactive)continue;e.read(e.start);
            for(int i=0;i<e.trial.length;i++) {
                e.trial[i]=e.start[i]+dt*(e.firstInactive?1.5*e.last[i]-.5*e.previous[i]:e.last[i]);
                if(!Double.isFinite(e.trial[i]))throw new IllegalArgumentException("Nonfinite disconnected trial");
            }
        }
        pending=true;
        try {for(var e:entries)if(e.trialInactive)e.write(e.trial);}
        catch(RuntimeException failure){rollback();throw failure;}
    }
    public void commit(){if(!pending)return;for(var e:entries)if(e.trialInactive)e.firstInactive=false;pending=false;}
    public void rollback(){if(!pending)return;for(var e:entries)if(e.trialInactive)e.write(e.start);pending=false;}
    public void requireBoundary(){if(pending)throw new IllegalStateException("Inactive integration trial is pending");}
    public void readHeldRates(Object model,double[] target,int offset) {
        for(var e:entries)if(e.machine==model||e.governor==model) {
            System.arraycopy(e.last,e.machine==model?0:6,target,offset,e.machine==model?6:e.governor.disconnectedStateCount());return;
        }
        throw new IllegalArgumentException("Unprepared retained-rate owner");
    }
    public void validateReactivationRequest(String id,double time) {
        requireBoundary();
        if(!Double.isFinite(time)||Double.isFinite(lastSampleTime)&&time<lastSampleTime)
            throw new IllegalArgumentException("Invalid reactivation time");
        Set<String> ids=new HashSet<>();for(var e:entries){e.checkOwner();ids.add(e.machine.getId());}
        validateConsumers(algorithm,ids);validateGroup(entries);
        if(entries.size()==2&&entries.stream().noneMatch(Entry::active))
            throw new IllegalStateException("Simultaneous shared-shaft reactivation is not qualified");
        for(var e:entries)if(e.machine.getId().equals(id)) {
            if(e.gen.getDynamicGenDevice()!=e.machine||e.machine.getGovernor()!=e.governor
                    ||e.machine.getExciter()!=null||e.machine.getStabilizer()!=null)
                throw new IllegalStateException("Prepared reactivation ownership changed");
            if(e.connected&&e.reactivationTime!=time)throw new IllegalStateException("Machine is already connected");
            if(!e.connected&&(e.gen.isActive()||e.machine.isActive()))throw new IllegalStateException("Inconsistent disconnected owner status");
            if(!e.bus.isActive())throw new IllegalStateException("Reactivation bus is inactive");
            return;
        }
        throw new IllegalArgumentException("Unprepared reactivation unit: "+id);
    }
    public boolean alreadyReactivated(String id,double time) {
        for(var e:entries)if(e.machine.getId().equals(id))return e.connected&&e.reactivationTime==time;
        return false;
    }
    public RoundRotorMachineImpl beginReactivation(String id,double time) {
        requireBoundary();if(!Double.isFinite(time)||time!=lastSampleTime)
            throw new IllegalArgumentException("Reactivation requires the current accepted boundary");
        for(var e:entries)if(e.machine.getId().equals(id)) {
            if(e.connected)return null;
            if(!e.bus.isActive()||e.gen.isActive()||e.machine.isActive())
                throw new IllegalStateException("Reactivation requires a disconnected prepared unit at an active bus");
            var rawGeneratorY=org.apache.commons.math3.complex.Complex.ONE.divide(e.gen.getPosGenZ().multiply(e.gen.getZMultiFactor()));
            e.savedSequenceCache=e.bus.captureSequenceEquivalentState();
            // Preserve accepted converted loads. Matrix construction separately replaces raw source Y by machine Norton Y.
            e.bus.applyPositiveSequenceShuntAdmittanceChange(rawGeneratorY);
            e.gen.setStatus(true);e.machine.setStatus(true);return e.machine;
        }
        throw new IllegalArgumentException("Unprepared reactivation unit: "+id);
    }
    public void finishReactivation(Object machine,boolean accepted) {
        for(var e:entries)if(e.machine==machine) {
            if(accepted) {e.governor.publishHostReactivation();e.machine.setPm(e.machine.getGovernor().getOutput(e.machine));e.connected=true;e.reactivationTime=lastSampleTime;}
            else {e.gen.setStatus(false);e.machine.setStatus(false);e.bus.restoreSequenceEquivalentState(e.savedSequenceCache);}
            return;
        }
    }
}
