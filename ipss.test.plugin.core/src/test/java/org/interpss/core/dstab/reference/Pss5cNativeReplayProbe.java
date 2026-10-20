package org.interpss.core.dstab.reference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.analysis.Pss5cLocalLinearizer;
import org.interpss.dstab.analysis.Pss5cTransducerCoordinates;
import org.interpss.dstab.control.pss.psse.pss5c.Pss5cNativeStabilizer;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.google.gson.JsonParser;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Diagnostic exact-speed playback. It does not set trajectory acceptance bounds.
 * Invoke with a generated native run directory and a fresh output CSV path.
 * Inputs use the entered model's readback precision, never optimized values.
 */
public final class Pss5cNativeReplayProbe {
    private Pss5cNativeReplayProbe() {}

    public static void main(String[] arguments) throws Exception {
        if(arguments.length<2||arguments.length>3||arguments.length==3&&!arguments[2].equals("--cascade-transducers"))
            throw new IllegalArgumentException("Expected native run folder, fresh CSV output, optional --cascade-transducers");
        boolean cascade=arguments.length==3;
        Path folder=Path.of(arguments[0]),output=Path.of(arguments[1]);
        if(Files.exists(output))throw new IllegalArgumentException("Preserve prior diagnostic output: "+output.getFileName());
        var manifest=JsonParser.parseString(Files.readString(folder.resolve("manifest.json"))).getAsJsonObject();
        var constants=manifest.getAsJsonObject("components").getAsJsonObject("STAB").getAsJsonArray("actual_constants");
        double[] p=new double[30];if(constants.size()!=30)throw new IllegalArgumentException("Not a native PSS5C profile");
        for(int i=0;i<30;i++)p[i]=constants.get(i).getAsDouble();
        IpssCorePlugin.init();var context=new PSSEMultiFileLoader().loadDStab(
                folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();
        var model=new DStabNetworkBuilder(net).addPss5cNative("Bus1","1",p);
        if(model==null||!alg.getAclfAlgorithm().loadflow())throw new IllegalStateException("Probe setup failed");
        if(cascade&&!model.getData().hasFullOrderTransducers())throw new IllegalArgumentException("Cascade export requires full-order transducers");
        alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);alg.setSimuStepSec(manifest.get("step_s").getAsDouble());
        alg.setSimuOutputHandler(new StateMonitor());if(!alg.initialization())throw new IllegalStateException("Probe initialization failed");
        var machine=net.getMachine("Bus1-mach1");
        var lines=Files.readAllLines(folder.resolve("native.csv"));var header=Arrays.asList(lines.get(0).split(","));
        int timeIndex=header.indexOf("time_s"),speedIndex=header.indexOf("MACH_SPEED");
        if(timeIndex<0||speedIndex<0)throw new IllegalArgumentException("Missing exact recorded boundary");
        var csv=new StringBuilder("time_s,low_signal,high_signal,VVL,VL,VI,VH,Vstab");
        for(String name:Pss5cLocalLinearizer.STATE_NAMES)csv.append(',').append(name);
        if(cascade)csv.append(",cascade_highLagOutput,cascade_highIntegral,cascade_highDerivative");
        csv.append('\n');var stepper=new ReplayStepper(model,machine);
        for(String line:lines.subList(1,lines.size())) {
            String[] row=line.split(",");double time=Double.parseDouble(row[timeIndex]),input=Double.parseDouble(row[speedIndex]);
            stepper.accept(time,input);
            csv.append(String.format(Locale.ROOT,"%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g",time,
                model.getLowIntermediateInput(),model.getHighInput(),model.getBandOutput(0),model.getBandOutput(1),
                model.getBandOutput(2),model.getBandOutput(3),model.getOutput(machine)));
            var states=model.getNamedStates();
            for(String name:Pss5cLocalLinearizer.STATE_NAMES)csv.append(',').append(String.format(Locale.ROOT,"%.12g",states.get(name)));
            if(cascade) {
                var view=Pss5cTransducerCoordinates.observeCascade(model);
                for(double v:new double[]{view.highLagOutput(),view.highIntegral(),view.highDerivative()})
                    csv.append(',').append(String.format(Locale.ROOT,"%.12g",v));
            }
            csv.append('\n');
        }
        Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,csv.toString());
    }

    /** Exact-time endpoint playback; duplicate event-side rows never advance state. */
    public static final class ReplayStepper {
        private final Pss5cNativeStabilizer model;
        private final Machine machine;
        private double previousTime,previousInput;
        private boolean initialized;
        public ReplayStepper(Pss5cNativeStabilizer model,Machine machine) {
            this.model=java.util.Objects.requireNonNull(model,"model");this.machine=java.util.Objects.requireNonNull(machine,"machine");
        }
        public void accept(double time,double input) {
            if(!Double.isFinite(time)||!Double.isFinite(input)||initialized&&time<previousTime)
                throw new IllegalArgumentException("Nonfinite boundary or nonmonotonic native clock");
            if(!initialized) {
                machine.setSpeed(1+input);
                if(!model.initStates(machine.getDStabBus(),machine))throw new IllegalStateException("Replay init failed");
            } else if(time>previousTime) {
                machine.setSpeed(1+previousInput);
                if(!model.nextStep(time-previousTime,DynamicSimuMethod.MODIFIED_EULER,machine,0))throw new IllegalStateException("Replay predictor failed");
                machine.setSpeed(1+input);
                if(!model.nextStep(time-previousTime,DynamicSimuMethod.MODIFIED_EULER,machine,1))throw new IllegalStateException("Replay corrector failed");
            }
            machine.setSpeed(1+input); // Also update algebraic feedthrough on duplicate event-side rows.
            previousTime=time;previousInput=input;initialized=true;
        }
    }
}
