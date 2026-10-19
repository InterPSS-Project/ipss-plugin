package org.interpss.fadapter.psse.dyr;

import java.util.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;

/** Deferred exact-wrapper import. No global registry and no partial group
 * attachment when schema, identity, host or ownership preflight fails. */
public final class HplntdImporter {
    private HplntdImporter() {}
    public static boolean recognizes(PsseDyrRecord record){
        return record.sourceModelName().equals("HPLNTDU")||record.sourceModelName().equals("REAXDU");
    }

    public static void attach(DStabNetworkBuilder builder,List<PsseDyrRecord> records,
            Map<PsseDyrRecord,DynamicModelImportStatus> removed,DynamicModelImportReport.Builder report){
        if(records.isEmpty())return;
        HplntdBindingPlan plan;
        try{plan=HplntdNativeRecords.plan(records);}
        catch(IllegalArgumentException failure){
            for(var r:records)report.add(r,DynamicModelImportStatus.REJECTED,
                    "Hybrid batch not attached: "+failure.getMessage());
            return;
        }
        for(var link:plan.links())if(link.status()!=HplntdBindingPlan.Status.LINKABLE)
            report.add(link.unit().source(),DynamicModelImportStatus.UNSUPPORTED,
                    "No hybrid command attachment: "+link.status());
        for(var plant:plan.plants().values()){
            var links=plan.downstream(plant.source().busNumber());
            if(links.isEmpty()){
                var removals=new ArrayList<DynamicModelImportStatus>();
                for(var entry:removed.entrySet())try{
                    var unit=HplntdNativeRecords.unit(entry.getKey());
                    if(unit.data().plantBus()==plant.source().busNumber()&&unit.data().plantModel().equals("HPLNTDU"))removals.add(entry.getValue());
                }catch(IllegalArgumentException ignored){ /* Removed records never become runtime data. */ }
                boolean hasLive=plan.links().stream().anyMatch(l->l.unit().data().plantBus()==plant.source().busNumber());
                if(!hasLive&&!removals.isEmpty()){
                    var status=removals.stream().allMatch(s->s==DynamicModelImportStatus.SKIPPED_GNET)
                            ?DynamicModelImportStatus.SKIPPED_GNET:DynamicModelImportStatus.SKIPPED_MODEL_REMOVE;
                    report.add(plant.source(),status,"Bus plant inapplicable: every linked downstream generator was intentionally removed");
                }else report.add(plant.source(),DynamicModelImportStatus.REJECTED,"Bus plant has no supported live downstream links");
                continue;
            }
            var group=new ArrayList<PsseDyrRecord>();group.add(plant.source());
            links.forEach(l->group.add(l.unit().source()));
            var net=builder.getBaseDStabNetwork();String missing=null;
            if(net.getDStabBus("Bus"+plant.source().busNumber())==null)missing="plant bus not found";
            for(var link:links){
                var unit=link.unit().source();var bus=net.getDStabBus("Bus"+unit.busNumber());
                if(bus==null||bus.getContributeGen(unit.deviceId())==null)
                    missing="downstream generator Bus"+unit.busNumber()+"/"+unit.deviceId()+" not found";
            }
            if(missing!=null){for(var r:group)report.add(r,DynamicModelImportStatus.MISSING_TARGET,missing);continue;}
            try{
                var units=links.stream().map(l->new DStabNetworkBuilder.HybridUnit("Bus"+l.unit().source().busNumber(),
                        l.unit().source().deviceId(),l.unit().data())).toList();
                builder.addHplntd("Bus"+plant.source().busNumber(),plant.data(),units);
                for(var r:group)report.add(r,DynamicModelImportStatus.ATTACHED,"Exact bus-owned hybrid group and dedicated downstream command ports");
            }catch(IllegalArgumentException|IllegalStateException failure){
                for(var r:group)report.add(r,DynamicModelImportStatus.REJECTED,"Hybrid group not attached: "+failure.getMessage());
            }
        }
    }
}
