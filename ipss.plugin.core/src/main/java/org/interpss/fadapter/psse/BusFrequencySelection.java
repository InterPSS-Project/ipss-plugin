package org.interpss.fadapter.psse;

import java.util.HashSet;
import java.util.Set;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.mach.Machine;

/**
 * Omit passive-bus measurements only for audited model families. Generator buses,
 * configured remote inputs and requested outputs stay measured. Unknown families
 * and callbacks retain all buses; model wiring changes require initialization.
 */
final class BusFrequencySelection {
    // Audited families use local machine/bus inputs except the remote selectors below.
    private static final Set<String> LOCAL_CONTROLLERS = Set.of(
        "org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter",
        "org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter",
        "org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter",
        "org.interpss.dstab.control.exc.psse.esac1a.Esac1aExciter",
        "org.interpss.dstab.control.exc.psse.esac6a.Esac6aExciter",
        "org.interpss.dstab.control.exc.psse.esdc1a.Esdc1aExciter",
        "org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter",
        "org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter",
        "org.interpss.dstab.control.exc.psse.exac2.Exac2Exciter",
        "org.interpss.dstab.control.exc.psse.expic1.Expic1Exciter",
        "org.interpss.dstab.control.exc.psse.scrx.ScrxExciter",
        "org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor",
        "org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor",
        "org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor",
        "org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor",
        "org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor",
        "org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor");
    record Result(Set<String> required, String reason) { }
    static boolean isAuditedController(Object controller) {
        return LOCAL_CONTROLLERS.contains(controller.getClass().getName())
            ||controller.getClass()==IeeestStabilizer.class||controller.getClass()==Ieee1992PSS2AStabilizer.class;
    }
    static Result select(DynamicSimuAlgorithm algorithm) {
        var network=algorithm.getNetwork();var required=new HashSet<String>();
        if(network.getClass()!=com.interpss.dstab.impl.DStabilityNetworkImpl.class)
            return all("custom network");
        if(!network.eAdapters().isEmpty())return all("network observer");
        if(algorithm.getScriptOutputHandler()!=null)return all("script output callback");
        var currents=network.getCustomBusCurrInjHashtable();
        if(currents!=null&&currents.getClass()!=java.util.Hashtable.class)return all("custom current callback");
        var sequenceCurrents=network.getCustom3SeqBusCurrInjHashtable();
        if(sequenceCurrents!=null&&!sequenceCurrents.isEmpty())return all("sequence current callback");
        var output=algorithm.getSimuOutputHandler();
        if(output!=null) {
            var ids=output.requiredBusFrequencyIds();
            if(ids==null)return all("output dependencies are undeclared");
            required.addAll(ids);
        }
        for(var event:network.getDynamicEventList())
            if(event.getType()==com.interpss.dstab.devent.DynamicSimuEventType.LOAD_CHANGE)
                return all("load-change event may depend on frequency");
        for(var branch:network.getBranchList())
            if(branch.getClass()!=com.interpss.dstab.impl.DStabBranchImpl.class
                    ||!branch.eAdapters().isEmpty()||!branch.getDynamicBranchDeviceList().isEmpty())
                return all("custom branch, dynamic device or observer");
        for(var bus:network.getBusList()) {
            if(bus.getClass()!=com.interpss.dstab.impl.DStabBusImpl.class)return all("custom bus");
            if(!bus.eAdapters().isEmpty())return all("bus observer");
            var block=bus.getBusFreqMeasureBlock();
            if(block!=null&&block.getClass()!=com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl.class)
                return all("custom frequency measurement");
            // Keep even initially inactive devices: switching can activate a consumer later.
            if(bus.getDynLoadModelList()!=null&&!bus.getDynLoadModelList().isEmpty())return all("dynamic load");
            for(var device:bus.getDynamicBusDeviceList())
                if(!(device instanceof Regca1Model))return all("unclassified bus device: "+device.getClass().getName());
            for(var generator:bus.getContributeGenList()) {
                if(generator.getClass()!=com.interpss.dstab.impl.DStabGenImpl.class)return all("custom generator");
                required.add(bus.getId());
                var device=generator.getDynamicGenDevice();
                if(device==null)continue;
                if(device instanceof Machine machine) {
                    if(!machine.eAdapters().isEmpty())return all("machine observer");
                    if(machine.getClass()!=org.interpss.dstab.mach.IeeeVcRoundRotorMachine.class
                        &&machine.getClass()!=org.interpss.dstab.mach.IeeeVcSalientPoleMachine.class
                        &&machine.getClass()!=org.interpss.dstab.mach.IeeeVcEConstMachine.class
                        &&machine.getClass()!=com.interpss.dstab.mach.impl.EConstMachineImpl.class)
                        return all("unclassified machine: "+machine.getClass().getName());
                    for(var controller:machine.getControllerList()) {
                        if(!controller.eAdapters().isEmpty())return all("controller observer");
                        var issue=nonstandardField(controller);
                        if(issue!=null)return all(controller.getClass().getSimpleName()+": "+issue);
                        if(machine.getStabilizer() instanceof Ieee1992PSS2AStabilizer parent
                                && parent.isBuiltInSignalConditioner(controller)) {
                            if(!parent.eAdapters().isEmpty())return all("controller observer");
                            var parentIssue=nonstandardField(parent);
                            if(parentIssue!=null)return all("PSS2A: "+parentIssue);
                            required.addAll(parent.requiredBusFrequencyIds());
                            continue;
                        }
                        if(controller.getClass()==IeeestStabilizer.class) {
                            var data=((IeeestStabilizer)controller).getData();
                            if(data.mode()==2&&data.remoteBus()!=0)required.add("Bus"+data.remoteBus());
                        }else if(controller.getClass()==Ieee1992PSS2AStabilizer.class)
                            required.addAll(((Ieee1992PSS2AStabilizer)controller).requiredBusFrequencyIds());
                        else if(!LOCAL_CONTROLLERS.contains(controller.getClass().getName()))
                            return all("unclassified controller: "+controller.getClass().getName());
                    }
                }else if(device instanceof Regca1Model converter) {
                    if(!converterDependencies(converter,required))return all("unclassified converter control");
                }else return all("unclassified generator: "+device.getClass().getName());
            }
            for(var device:bus.getDynamicBusDeviceList())if(device instanceof Regca1Model converter) {
                required.add(bus.getId());
                if(!converterDependencies(converter,required))return all("unclassified converter control");
            }
        }
        return new Result(Set.copyOf(required),"known local, remote and output dependencies");
    }
    private static boolean converterDependencies(Regca1Model converter,Set<String> required) {
        var electrical=converter.getActiveElectricalController();
        if(electrical==null)return true;
        if(electrical.getClass()!=Reeca1Model.class&&electrical.getClass()!=Reecb1Model.class
                &&electrical.getClass()!=ConverterCurrentCommands.class)return false;
        if(electrical.getRepccController()!=null||electrical.getHybridController()!=null
                ||electrical.getInertialResponseController()!=null)return false;
        var plant=electrical.getPlantController();
        if(plant!=null&&plant.getData().remoteBus()!=0)required.add("Bus"+plant.getData().remoteBus());
        return true;
    }
    private static Result all(String reason){return new Result(null,reason);}
    private static String nonstandardField(Object controller) {
        if(!(controller instanceof com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController model))return null;
        // Inactive models may not have a compiled graph yet. Inspect their actual
        // configured fields; absent blocks will be constructed by the known model.
        for(Class<?> owner=model.getClass();owner!=null;owner=owner.getSuperclass())
            for(var field:owner.getDeclaredFields()) {
                if(!field.isAnnotationPresent(com.interpss.dstab.controller.cml.annotate.AnControllerField.class)
                        &&!field.isAnnotationPresent(com.interpss.dstab.controller.cml.annotate.AnFunctionField.class))continue;
                Object value;
                try {if(!field.trySetAccessible())return "inaccessible field "+field.getName();value=field.get(model);}
                catch(IllegalAccessException failure){return "inaccessible field "+field.getName();}
                if(value==null||field.getType()==value.getClass())continue;
                boolean ownImplementation=false;
                for(Class<?> type=value.getClass().getEnclosingClass();type!=null;type=type.getEnclosingClass())
                    if(type==field.getDeclaringClass()){ownImplementation=true;break;}
                if(!ownImplementation)return "custom field "+field.getName()+" ("+value.getClass().getName()+")";
            }
        return null;
    }
}
