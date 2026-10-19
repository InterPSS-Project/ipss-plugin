package org.interpss.core.dstab.reference;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import com.interpss.dstab.mach.impl.SalientPoleMachineImpl;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter;
import org.interpss.dstab.renewable.Regca1Model;
/**
 * Read-only benchmark initialization audit using the core state accessors.
 * Cached derivatives must be evaluated by the solver before this is called.
 * Missing rate access is reported explicitly; finite initial outputs alone do
 * not establish equilibrium or agreement with a reference trajectory.
 */
public final class Texas2kInitializationAudit {
  static Map<String,Object> controller(Object c) {
    var row=new LinkedHashMap<String,Object>();
    if(c==null)return row;
    row.put("class",c.getClass().getSimpleName());
    var states=new LinkedHashMap<String,Double>();
    var rates=new LinkedHashMap<String,Double>();
    var missing=new ArrayList<String>();
    if(c instanceof com.interpss.dstab.controller.cml.ICMLStateProvider p)states.putAll(p.getNamedStates());
    if(c instanceof AbstractAnnotateController a) {
      collect(a,"",states,rates,missing,Collections.newSetFromMap(new IdentityHashMap<>()));
    } else {
      missing.add("Custom controller: retained derivative accessor unavailable");
    }
    for(var value:states.values())if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite initial state in "+c.getClass().getSimpleName());
    for(var value:rates.values())if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite retained derivative in "+c.getClass().getSimpleName());
    row.put("states",states);row.put("rates",rates);row.put("rate_coverage_gaps",missing);
    return row;
  }
  private static void collect(AbstractAnnotateController c,String prefix,Map<String,Double> states,
      Map<String,Double> rates,List<String> missing,Set<Object> visited) {
    if(!visited.add(c))return;
    c.getNamedStates().forEach((name,value)->states.put(prefix+name,value));
    for(var entry:c.getNamedStateBlocks().entrySet()) {
      if(entry.getValue() instanceof com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock compound) {
        int i=0;
        for(var block:compound.getIntegrationBlocks()) {
          String name=prefix+entry.getKey()+".section"+i++;
          states.put(name,block.getStateX());rates.put(name,block.getDX_dt());
        }
      } else if(entry.getValue() instanceof org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock block) {
        rates.put(prefix+entry.getKey(),block.getStateDerivative());
      } else if(entry.getValue() instanceof CMLControlBlock1stOrderAdapter b)rates.put(prefix+entry.getKey(),b.getDX_dt());
      else missing.add(prefix+entry.getKey()+": retained derivative accessor unavailable");
    }
    if(c.getFieldWrapperList()==null)missing.add(prefix+"CML fields not initialized");
    else for(var field:c.getFieldWrapperList())
        if(field.getField() instanceof AbstractAnnotateController child)
          collect(child,prefix+field.getFieldName()+".",states,rates,missing,visited);
  }
  static void write(BaseDStabNetwork<?,?> net,Path file)throws Exception {
    var rows=new ArrayList<Map<String,Object>>();
    for(var bus:net.getBusList())if(bus.isActive())for(var gen:bus.getContributeGenList())if(gen.isActive()) {
      var row=new LinkedHashMap<String,Object>();row.put("bus",bus.getId());row.put("id",gen.getId());
      row.put("voltage",bus.getVoltageMag());row.put("voltage_angle",bus.getVoltageAng());
      var device=gen.getDynamicGenDevice();
      if(device instanceof Machine m) {
        row.put("type",m.getClass().getSimpleName());row.put("efd",m.getEfd());row.put("pm",m.getPm());row.put("pe",m.getPe());
        row.put("mechanical",((com.interpss.dstab.mach.impl.MachineImpl)m).captureMechanicalIntegrationState());
        if(m instanceof RoundRotorMachineImpl r)row.put("electrical",r.captureRoundElectricalState());
        else if(m instanceof SalientPoleMachineImpl s)row.put("electrical",s.captureSalientElectricalState());
        row.put("exciter",controller(m.getExciter()));row.put("governor",controller(m.getGovernor()));row.put("stabilizer",controller(m.getStabilizer()));
      }else if(device instanceof Regca1Model r) {
        row.put("type","REGCA1");row.put("converter",r.getNamedStates());
        var e=r.getReeca1Controller();if(e!=null) {
        row.put("electrical_control",e.getNamedStates());
        row.put("rate_coverage_gaps",List.of("Renewable equations: inspect state changes across startup steps; retained derivative accessor unavailable"));
          if(e.getPlantController()!=null)row.put("plant",e.getPlantController().getNamedStates());
        }
      }
      rows.add(row);
    }
    Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(rows)+"\n",StandardOpenOption.CREATE_NEW);
  }
}
