package org.interpss.plugin.dstab.config;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import org.interpss.fadapter.psse.PsseDStabSolver;

/** Versioned dynamic-run settings. Null means preserve the imported/current value.
 * Source settings apply first; explicit JSON fields overlay them second. File paths,
 * interactive scripts and events belong to the converter result, not solver settings. */
@SuppressWarnings("deprecation")
public final class DStabRunConfigRec {
    public int schemaVersion = 1;
    public Double stepSec, totalTimeSec, frequencyHz, networkTolerance, networkRelaxationFactor;
    public Integer maxNetworkIterations;
    public DynamicSimuMethod integrationMethod;
    /** Omitted fields preserve the imported or caller-selected static conversion. */
    public com.interpss.dstab.StaticLoadModel activeStaticLoadModel, reactiveStaticLoadModel;
    public Boolean sourceImpedanceReconciliationEnabled, allowGeneratorsWithoutMachine;
    public LoadModelThresholds loadModelThresholds;
    // These source policies are retained explicitly. They must never be silently
    // equated to another algorithm or to bus-frequency measurement selection.
    public Boolean networkFrequencyDependenceEnabled;
    public Double sourceNetworkAccelerationFactor;
    public Boolean relativeAnglesEnabled;
    public Integer angleReferenceBus;
    public String angleReferenceMachineId;

    public static DStabRunConfigRec fromJson(String text) {
        var object=JsonParser.parseString(text).getAsJsonObject();
        var gson=new GsonBuilder().create();
        validateJsonFields(object,true);
        var result=gson.fromJson(object,DStabRunConfigRec.class);result.validate();return result;
    }
    public static DStabRunConfigRec overlayJson(DStabRunConfigRec imported,String override) {
        var gson=new GsonBuilder().create();var merged=gson.toJsonTree(imported).getAsJsonObject();
        var explicit=JsonParser.parseString(override).getAsJsonObject();validateJsonFields(explicit,false);
        for(var entry:explicit.entrySet()) {
            if(entry.getValue().isJsonNull()) throw new IllegalArgumentException("Explicit null is ambiguous: "+entry.getKey());
            if(entry.getKey().equals("loadModelThresholds")&&merged.has(entry.getKey())) {
                var thresholds=merged.getAsJsonObject(entry.getKey());
                for(var field:entry.getValue().getAsJsonObject().entrySet())thresholds.add(field.getKey(),field.getValue());
            }else merged.add(entry.getKey(),entry.getValue());
        }
        return fromJson(gson.toJson(merged));
    }
    private static void validateJsonFields(com.google.gson.JsonObject object,boolean completeThresholds) {
        for(var entry:object.entrySet()) {
            try {
                var field=DStabRunConfigRec.class.getField(entry.getKey());var value=entry.getValue();
                if(value.isJsonNull())throw new IllegalArgumentException("Explicit null is ambiguous: "+entry.getKey());
                if(field.getType()==LoadModelThresholds.class) {
                    var thresholds=value.getAsJsonObject();var names=java.util.Set.of("minimumMw","minimumAbsPOverQ","minimumVoltagePu");
                    if(!names.containsAll(thresholds.keySet())||(completeThresholds&&!thresholds.keySet().containsAll(names)))
                        throw new IllegalArgumentException("Load thresholds require exactly the three documented fields");
                    for(var child:thresholds.entrySet())if(!child.getValue().isJsonPrimitive()||!child.getValue().getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Non-numeric load threshold");
                }else if(field.getType()==Boolean.class) {
                    if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected JSON boolean: "+entry.getKey());
                }else if(field.getType()==Double.class||field.getType()==Integer.class||field.getType()==int.class) {
                    if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected JSON number: "+entry.getKey());
                    if(field.getType()!=Double.class&&(value.getAsDouble()!=Math.rint(value.getAsDouble())||value.getAsDouble()<Integer.MIN_VALUE||value.getAsDouble()>Integer.MAX_VALUE))throw new IllegalArgumentException("Expected in-range integer: "+entry.getKey());
                }else if(field.getType()==DynamicSimuMethod.class)DynamicSimuMethod.valueOf(value.getAsString());
                else if(field.getType()==com.interpss.dstab.StaticLoadModel.class)com.interpss.dstab.StaticLoadModel.valueOf(value.getAsString());
            }catch(NoSuchFieldException e){throw new IllegalArgumentException("Unknown dynamic run setting: "+entry.getKey());}
        }
    }
    public String toJson() { validate();return new GsonBuilder().setPrettyPrinting().create().toJson(this); }
    public void validate() {
        if(schemaVersion!=1)throw new IllegalArgumentException("Unsupported dynamic config schemaVersion: "+schemaVersion);
        for(Double number:new Double[]{stepSec,frequencyHz,networkTolerance,networkRelaxationFactor})
            if(number!=null&&(!Double.isFinite(number)||number<=0))throw new IllegalArgumentException("Run settings must be finite and positive");
        if(totalTimeSec!=null&&(!Double.isFinite(totalTimeSec)||totalTimeSec<0))throw new IllegalArgumentException("Invalid duration");
        if(maxNetworkIterations!=null&&maxNetworkIterations<1)throw new IllegalArgumentException("Invalid network iteration limit");
        if(networkRelaxationFactor!=null&&networkRelaxationFactor>1)throw new IllegalArgumentException("Network relaxation factor exceeds one");
        if(sourceNetworkAccelerationFactor!=null&&(!Double.isFinite(sourceNetworkAccelerationFactor)||sourceNetworkAccelerationFactor<=0))throw new IllegalArgumentException("Invalid source acceleration");
        if(angleReferenceBus!=null&&angleReferenceBus < -1)throw new IllegalArgumentException("Invalid angle reference bus");
    }
    /** Unsupported physical/numerical policies are returned and, in strict mode,
     * rejected before any mutation. A parsed value is not proof it was applied. */
    public List<String> applyTo(DynamicSimuAlgorithm algorithm,boolean requireMatchedSettings) {
        validate();List<String> gaps=new ArrayList<>();
        if(Boolean.TRUE.equals(networkFrequencyDependenceEnabled))gaps.add("Network-parameter frequency dependence is not implemented by this solver");
        if(sourceNetworkAccelerationFactor!=null&&networkRelaxationFactor==null)gaps.add("Source network acceleration has no qualified equivalent; use explicit networkRelaxationFactor for an override");
        if(Boolean.TRUE.equals(relativeAnglesEnabled))gaps.add("Relative-angle policy requires an output recorder using the requested reference; physical rotor states remain absolute");
        if(requireMatchedSettings&&!gaps.isEmpty())throw new IllegalArgumentException(String.join("; ",gaps));
        if(!(algorithm.getSolver() instanceof PsseDStabSolver solver))throw new IllegalArgumentException("Imported dynamic solver required");
        if(stepSec!=null)algorithm.setSimuStepSec(stepSec);
        if(totalTimeSec!=null)algorithm.setTotalSimuTimeSec(totalTimeSec);
        if(frequencyHz!=null)algorithm.getNetwork().setFrequency(frequencyHz);
        if(integrationMethod!=null)algorithm.setSimuMethod(integrationMethod);
        if(networkTolerance!=null)solver.setNetworkVoltageTolerance(networkTolerance);
        if(maxNetworkIterations!=null)solver.setMaxIterationTimes(maxNetworkIterations);
        if(networkRelaxationFactor!=null)solver.setNetworkRelaxationFactor(networkRelaxationFactor);
        if(allowGeneratorsWithoutMachine!=null)algorithm.getNetwork().setAllowGenWithoutMach(allowGeneratorsWithoutMachine);
        if(sourceImpedanceReconciliationEnabled!=null)solver.setSourceImpedanceReconciliationEnabled(sourceImpedanceReconciliationEnabled);
        if(loadModelThresholds!=null)solver.setLoadModelThresholds(loadModelThresholds);
        if(activeStaticLoadModel!=null||reactiveStaticLoadModel!=null) {
            var network=algorithm.getNetwork();
            if(activeStaticLoadModel!=null)network.setStaticLoadModel(activeStaticLoadModel);
            if(reactiveStaticLoadModel!=null)network.setReactiveStaticLoadModel(reactiveStaticLoadModel);
            // A non-Z part needs the core's explicit current-injection path.
            // Z/Z may use the established whole-load admittance representation.
            network.setStaticLoadIncludedInYMatrix(network.getStaticLoadModel()==com.interpss.dstab.StaticLoadModel.CONST_Z
                    &&network.getReactiveStaticLoadModel()==com.interpss.dstab.StaticLoadModel.CONST_Z);
        }
        return List.copyOf(gaps);
    }
}
