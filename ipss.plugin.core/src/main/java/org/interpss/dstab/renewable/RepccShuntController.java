package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.RepccData.Parameter.*;
import java.util.*;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Ordered REPCC switched-shunt logic. Called once per accepted solver endpoint,
 * never once per predictor or network iteration. Returned actions require an
 * attachment to update the physical network; this class does not change Y. */
public final class RepccShuntController implements ICMLStateProvider {
    public record Device(RepccData.Shunt key,double susceptance,boolean inService) {
        public Device {
            Objects.requireNonNull(key);
            if(key.bus()<=0||!Double.isFinite(susceptance))throw new IllegalArgumentException("Actual shunt requires a bus and finite susceptance");
        }
    }
    public record Action(int slot,RepccData.Shunt key,boolean inService,double requestedAt,double completedAt) {}
    public record Slot(RepccData.Shunt key,double susceptance,boolean inService,
                       double breakerStart,double dischargeStart,boolean targetInService) {}
    private final RepccData data;
    private final Device[] devices=new Device[10];
    private final boolean[] active=new boolean[10],target=new boolean[10];
    private final double[] breaker=new double[10],discharge=new double[10];
    private double clock,slowStart=-1,fastStart=-1;
    private int previousDirection;
    private boolean enabled,initialized,sampled;

    public RepccShuntController(RepccData data){this.data=Objects.requireNonNull(data);}
    public void initialize(List<Device> actualDevices,boolean measurementBranchFound){
        initialized=false;
        var byKey=new HashMap<RepccData.Shunt,Device>();
        for(var device:actualDevices)if(byKey.putIfAbsent(device.key(),device)!=null)
            throw new IllegalArgumentException("Duplicate actual shunt "+device.key());
        Arrays.fill(devices,null);Arrays.fill(active,false);Arrays.fill(target,false);
        Arrays.fill(breaker,-1);Arrays.fill(discharge,-1);var seen=new HashSet<RepccData.Shunt>();
        enabled=data.switchingFlag()==1&&measurementBranchFound&&data.switchingThresholdsValid();
        for(int i=0;i<10;i++){
            var key=data.shunts().get(i);if(key.bus()==0)continue;
            if(!seen.add(key)){if(enabled)throw new IllegalArgumentException("Duplicate REPCC shunt slot "+key);else continue;}
            var device=byKey.get(key);
            if(device==null){if(enabled)throw new IllegalArgumentException("Configured REPCC shunt not found: "+key);else continue;}
            devices[i]=device;active[i]=target[i]=device.inService();
        }
        clock=0;slowStart=fastStart=-1;previousDirection=0;sampled=false;initialized=true;
    }
    public boolean isEnabled(){requireInitialized();return enabled;}
    public double time(){requireInitialized();return clock;}
    public List<Slot> slots(){
        requireInitialized();var result=new ArrayList<Slot>();
        for(int i=0;i<10;i++)result.add(new Slot(data.shunts().get(i),devices[i]==null?0:devices[i].susceptance(),
                active[i],breaker[i],discharge[i],target[i]));return List.copyOf(result);
    }
    /** Threshold inputs are machine-base reactive power, with the configured
     * measurement direction already resolved. Repeated time is idempotent. */
    public List<Action> advance(double time,double reactivePower){
        requireInitialized();
        if(!Double.isFinite(time)||!Double.isFinite(reactivePower)||time<clock||time<0)
            throw new IllegalArgumentException("Finite monotone endpoint time and reactive power required");
        if(sampled&&time==clock)return List.of();clock=time;sampled=true;
        if(!enabled)return List.of();var actions=new ArrayList<Action>();
        for(int i=0;i<10;i++)if(breaker[i]>=0&&clock>=breaker[i]+v(TMSSBRK))complete(i,actions);
        int fast=direction(reactivePower,v(QDN2),v(QUP2));
        int slow=direction(reactivePower,v(QDN1),v(QUP1));
        if(slow==0||slow!=previousDirection)slowStart=fastStart=-1;
        if(fast!=0&&fastStart<0)fastStart=clock;
        if(fast==0&&slow!=0&&slowStart<0)slowStart=clock;
        previousDirection=slow;
        boolean ready=fast!=0?clock-fastStart>=v(TDELAY2):slow!=0&&clock-slowStart>=v(TDELAY1);
        if(ready){
            int slot=select(fast!=0?fast:slow);if(slot>=0){
                target[slot]=!active[slot];breaker[slot]=clock;
                if(v(TMSSBRK)==0)complete(slot,actions);
            }
        }
        return List.copyOf(actions);
    }
    private static int direction(double q,double low,double high){return q<low?-1:q>high?1:0;}
    private int select(int direction){
        if(direction==0)return -1;
        // Removal of the opposite support takes precedence over insertion,
        // irrespective of an earlier input slot of the other device type.
        int result=find(direction<0,true,false);
        return result>=0?result:find(direction>0,false,true);
    }
    private int find(boolean capacitor,boolean inService,boolean inserting){
        for(int i=0;i<10;i++){
            var d=devices[i];if(d==null||d.susceptance()==0||breaker[i]>=0||active[i]!=inService)continue;
            if((d.susceptance()>0)!=capacitor)continue;
            if(inserting&&capacitor&&discharge[i]>=0&&clock-discharge[i]<v(TOUT))continue;
            return i;
        }return -1;
    }
    private void complete(int slot,List<Action> actions){
        double started=breaker[slot];active[slot]=target[slot];breaker[slot]=-1;
        if(devices[slot].susceptance()>0)discharge[slot]=active[slot]?-1:clock;
        actions.add(new Action(slot,data.shunts().get(slot),active[slot],started,clock));
    }
    @Override public Map<String,Double> getNamedStates(){
        requireInitialized();var result=new LinkedHashMap<String,Double>();result.put("slowStart",slowStart);result.put("fastStart",fastStart);
        for(int i=0;i<10;i++){result.put("shunt."+i+".breakerStart",breaker[i]);result.put("shunt."+i+".dischargeStart",discharge[i]);result.put("shunt."+i+".inService",active[i]?1.:0.);}
        return Collections.unmodifiableMap(result);
    }
    private double v(RepccData.Parameter p){return data.value(p);}
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("Initialize REPCC shunt controller first");}
}
