package org.interpss.core.dstab.reference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.google.gson.JsonParser;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Diagnostic exact-speed playback. It does not set trajectory acceptance bounds.
 * Invoke with a generated native run directory and a fresh output CSV path.
 * Inputs use the entered model's readback precision, never optimized values.
 */
public final class Pss5cNativeReplayProbe {
    private Pss5cNativeReplayProbe() {}

    public static void main(String[] arguments) throws Exception {
        if(arguments.length!=2)throw new IllegalArgumentException("Expected native run folder and fresh CSV output");
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
        alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);alg.setSimuStepSec(manifest.get("step_s").getAsDouble());
        alg.setSimuOutputHandler(new StateMonitor());if(!alg.initialization())throw new IllegalStateException("Probe initialization failed");
        var machine=net.getMachine("Bus1-mach1");
        var lines=Files.readAllLines(folder.resolve("native.csv"));var header=Arrays.asList(lines.get(0).split(","));
        int timeIndex=header.indexOf("time_s"),speedIndex=header.indexOf("MACH_SPEED");
        if(timeIndex<0||speedIndex<0)throw new IllegalArgumentException("Missing exact recorded boundary");
        var csv=new StringBuilder("time_s,low_signal,high_signal,VVL,VL,VI,VH,Vstab\n");
        double previousTime=0,previousInput=0;boolean first=true;
        for(String line:lines.subList(1,lines.size())) {
            String[] row=line.split(",");double time=Double.parseDouble(row[timeIndex]),input=Double.parseDouble(row[speedIndex]);
            if(first){machine.setSpeed(1+input);if(!model.initStates(machine.getDStabBus(),machine))throw new IllegalStateException("Replay init failed");}
            else if(time>previousTime) {
                machine.setSpeed(1+previousInput);model.nextStep(time-previousTime,DynamicSimuMethod.MODIFIED_EULER,machine,0);
                machine.setSpeed(1+input);model.nextStep(time-previousTime,DynamicSimuMethod.MODIFIED_EULER,machine,1);
            } else if(time<previousTime)throw new IllegalArgumentException("Nonmonotonic native clock");
            csv.append(String.format(Locale.ROOT,"%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g%n",time,
                model.getLowIntermediateInput(),model.getHighInput(),model.getBandOutput(0),model.getBandOutput(1),
                model.getBandOutput(2),model.getBandOutput(3),model.getOutput(machine)));
            previousTime=time;previousInput=input;first=false;
        }
        Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,csv.toString());
    }
}
