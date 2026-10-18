package org.interpss.dstab.renewable;

import java.util.*;

/** Hybrid plant membership, base conversion and weighted command dispatch.
 * This is not the thirteen-state controller or the downstream two-state lag.
 * Initial reference coordinates stay fixed when live membership changes. */
public final class HplntdAggregation {
    public record Key(String bus,String generator) {
        public Key { if(bus==null||bus.isBlank()||generator==null||generator.isBlank())throw new IllegalArgumentException("Device identity required"); }
    }
    /** eligible includes the source's bus type, link validity and device status. */
    public record Device(Key key,double mva,double kz,double kw,double activeMW,double reactiveMvar,boolean eligible) {
        public Device {
            Objects.requireNonNull(key);finite(mva,kz,kw,activeMW,reactiveMvar);
            if(mva<0||kz<0||kw<0)throw new IllegalArgumentException("This aggregation requires nonnegative ratings and participation factors");
        }
        boolean participating(){return eligible&&mva>0&&(kz!=0||kw!=0);}
    }
    public record Share(boolean participating,double unitMva,double activeWeight,double reactiveWeight) {}
    /** raw signals correspond to downstream input VARs; weighted increments are
     * the subsequent gain outputs. They must not both be applied to a device. */
    public record Dispatch(boolean enabled,double rawActive,double rawReactive,double activeIncrement,double reactiveIncrement) {}
    /** calculatedMva is the live device-rating sum. A native allocator may put
     * the explicitly configured base in its calculated-base plot slot instead;
     * that reporting initialization is not a different physical device sum. */
    public record Snapshot(double plantMva,double calculatedMva,double weightedKz,double weightedKw,
            double initialActive,double initialReactive,boolean ignored,Map<Key,Share> shares) {
        public Snapshot { shares=Map.copyOf(shares); }
        public Dispatch dispatch(Key key,double deltaActivePlant,double deltaReactivePlant) {
            finite(deltaActivePlant,deltaReactivePlant);var s=shares.get(key);
            if(s==null)throw new IllegalArgumentException("Unknown hybrid downstream device: "+key);
            // Disabled is a hold/no-command indication, not an instruction to
            // overwrite the ignored downstream model's held outputs with zero.
            if(ignored||!s.participating())return new Dispatch(false,0,0,0,0);
            double p=deltaActivePlant*plantMva/s.unitMva(),q=deltaReactivePlant*plantMva/s.unitMva();
            double dp=p*s.activeWeight(),dq=q*s.reactiveWeight();finite(p,q,dp,dq);return new Dispatch(true,p,q,dp,dq);
        }
        /** Pause resets the calculated reporting fields, not the held plant base
         * or initial references. Resume obtains a fresh live snapshot. */
        public Snapshot pausedView(){return new Snapshot(plantMva,0,0,0,initialActive,initialReactive,ignored,shares);}
    }
    private final double configuredMva,initialActive,initialReactive;
    private final Set<Key> members;
    public HplntdAggregation(double configuredMva,List<Device> devices) {
        finite(configuredMva);if(configuredMva<0)throw new IllegalArgumentException("Nonnegative plant MVA required");
        this.configuredMva=configuredMva;var initial=aggregate(devices);members=Set.copyOf(initial.shares().keySet());
        double p=0,q=0;for(var d:devices)if(d.participating()){p+=d.activeMW();q+=d.reactiveMvar();}
        initialActive=initial.plantMva()>0?p/initial.plantMva():0;initialReactive=initial.plantMva()>0?q/initial.plantMva():0;
        finite(initialActive,initialReactive);
    }
    public Snapshot refresh(List<Device> devices) {
        var s=aggregate(devices);if(!s.shares().keySet().equals(members))throw new IllegalArgumentException("Hybrid membership identities cannot be replaced during a run");
        return new Snapshot(s.plantMva(),s.calculatedMva(),s.weightedKz(),s.weightedKw(),initialActive,initialReactive,s.ignored(),s.shares());
    }
    private Snapshot aggregate(List<Device> devices) {
        double mva=0,kz=0,kw=0;var keys=new HashSet<Key>();
        for(var d:devices) {
            if(!keys.add(d.key()))throw new IllegalArgumentException("Duplicate hybrid downstream device: "+d.key());
            if(d.participating()){mva+=d.mva();kz+=d.kz()*d.mva();kw+=d.kw()*d.mva();}
        }
        finite(mva,kz,kw);double plant=configuredMva==0?mva:configuredMva;var shares=new LinkedHashMap<Key,Share>();
        for(var d:devices)shares.put(d.key(),new Share(d.participating(),d.mva(),d.participating()&&kz!=0?d.kz()*d.mva()/kz:0,d.participating()&&kw!=0?d.kw()*d.mva()/kw:0));
        return new Snapshot(plant,mva,kz,kw,0,0,plant==0,shares);
    }
    private static void finite(double... values){for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("Finite hybrid aggregate values required");}
}
