package org.interpss.fadapter.psse.dyr;

import java.util.*;
import com.interpss.dstab.renewable.HplntdData;

/** Immutable, order-independent hybrid ownership plan. LINKABLE is structural
 * only: network eligibility, ratings, live status and command-port ownership
 * must still be checked atomically by the runtime attachment coordinator. */
public final class HplntdBindingPlan {
    public record DeviceKey(int bus,String id) {}
    public enum Status { LINKABLE,IGNORED_MISSING_PLANT,IGNORED_PLANT_MODEL,IGNORED_RELATIVE_VARIABLES }
    public record Link(HplntdNativeRecords.Unit unit,HplntdNativeRecords.Plant plant,Status status) {}
    private final Map<Integer,HplntdNativeRecords.Plant> plants;
    private final List<Link> links;
    private HplntdBindingPlan(Map<Integer,HplntdNativeRecords.Plant> plants,List<Link> links){
        this.plants=Collections.unmodifiableMap(new LinkedHashMap<>(plants));this.links=List.copyOf(links);
    }
    public static HplntdBindingPlan compile(List<HplntdNativeRecords.Plant> plantRecords,List<HplntdNativeRecords.Unit> unitRecords) {
        var plants=new LinkedHashMap<Integer,HplntdNativeRecords.Plant>();var identities=new HashSet<DeviceKey>();var links=new ArrayList<Link>();
        for(var p:plantRecords)if(plants.putIfAbsent(p.source().busNumber(),p)!=null)
            throw duplicate("plant at bus "+p.source().busNumber(),p.source());
        for(var u:unitRecords){
            var key=new DeviceKey(u.source().busNumber(),u.source().deviceId());
            if(!identities.add(key))throw duplicate("downstream device "+key,u.source());
            var p=plants.get(u.data().plantBus());Status status;
            if(!u.data().plantModel().equals(HplntdData.NATIVE_MODEL))status=Status.IGNORED_PLANT_MODEL;
            else if(p==null)status=Status.IGNORED_MISSING_PLANT;
            else if(!u.data().validRelativeVariables(HplntdData.VAR_COUNT))status=Status.IGNORED_RELATIVE_VARIABLES;
            else status=Status.LINKABLE;
            links.add(new Link(u,p,status));
        }
        return new HplntdBindingPlan(plants,links);
    }
    private static IllegalArgumentException duplicate(String what,PsseDyrRecord r){return new IllegalArgumentException("Duplicate hybrid "+what+" at "+r.source()+":"+r.startLine());}
    public Map<Integer,HplntdNativeRecords.Plant> plants(){return plants;}
    public List<Link> links(){return links;}
    public List<Link> downstream(int plantBus){return links.stream().filter(l->l.status()==Status.LINKABLE&&l.unit().data().plantBus()==plantBus).toList();}
}
