package org.interpss.core.adapter.builder.dstab;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.util.logging.Level;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import com.interpss.dstab.renewable.RegccModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.google.gson.*;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Complete-run local-integration experiment; CPU placement belongs to the launcher. */
public final class RegccSubstepStudy {
    private static final double H=1./240.;
    private static double networkInterval=H;
    private static final int[] COUNTS={1,2,4,8};
    private static final String HEADER="time_s,STATE0,STATE1,STATE2,STATE3,STATE4,STATE5,STATE6,STATE7,STATE8,P_PU,Q_PU,V1,A1_DEG,V2,A2_DEG,V3,A3_DEG";
    record Run(List<double[]> rows,double seconds,double effectiveTe,double recoveryTime){}
    static Run run(Path input,Path output,int count,double end)throws Exception {
        long start=System.nanoTime();
        var context=new PSSEMultiFileLoader().loadDStab(input.resolve("network.raw").toString(),input.resolve("dynamic.dyr").toString());
        var net=context.getDStabilityNet();var algo=context.getDynSimuAlgorithm();
        if(!algo.getAclfAlgorithm().loadflow())throw new IllegalStateException("Power flow failed");
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(networkInterval);algo.setTotalSimuTimeSec(end);
        algo.setSimuOutputHandler(new StateMonitor());
        var model=(RegccModel)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        model.setLocalSubsteps(count);var rows=new ArrayList<double[]>();
        Runnable capture=()->{
            double[] row=new double[18];row[0]=algo.getSimuTime();System.arraycopy(model.getStateSnapshot(),0,row,1,9);
            var voltage=net.getBus("Bus1").getVoltage();var gen=model.getParentGen();
            var current=((Complex)model.getOutputObject()).subtract(voltage.divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
            var power=voltage.multiply(current.conjugate()).multiply(net.getBaseMva()/gen.getMvaBase());
            row[10]=power.getReal();row[11]=power.getImaginary();
            for(int bus=1;bus<=3;bus++){row[10+2*bus]=net.getBus("Bus"+bus).getVoltageMag();row[11+2*bus]=Math.toDegrees(net.getBus("Bus"+bus).getVoltageAng());}
            for(double value:row)if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite recording");
            rows.add(row);
        };
        algo.setSolver(new DefaultDynamicSimulationSolver(algo){
            @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)throws DStabSimuException {
                super.rebaseDynamicModelInputs(method);capture.run();
            }
        });
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,
                new Complex(0.,100./(230.*230./100.)),null,.05,.05),"Fault");
        if(!algo.initialization())throw new IllegalStateException("Initialization failed");
        model.configureIntegrationStep(H);capture.run();
        while(algo.getSimuTime()<end-networkInterval/2){if(!algo.solveDEqnStep(true))throw new IllegalStateException("Dynamic solve failed at "+algo.getSimuTime());capture.run();}
        try(var writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8)) {
            writer.write(HEADER);writer.newLine();
            for(var row:rows){for(int i=0;i<row.length;i++){if(i>0)writer.write(',');writer.write(Double.toString(row[i]));}writer.newLine();}
        }
        return new Run(rows,(System.nanoTime()-start)/1e9,model.getEffectiveTe(),model.getRecoveryTime());
    }
    static List<double[]> reference(Path file)throws Exception {
        var lines=Files.readAllLines(file);var names=Arrays.asList(lines.getFirst().split(","));
        var requested=HEADER.split(",");var indices=new int[18];for(int i=0;i<18;i++)indices[i]=names.indexOf(requested[i]);
        var rows=new ArrayList<double[]>();
        for(String line:lines.subList(1,lines.size())) {
            var fields=line.split(",");var row=new double[18];for(int i=0;i<18;i++)row[i]=Double.parseDouble(fields[indices[i]]);if(row[0]>=0)rows.add(row);
        }
        return rows;
    }
    static Map<String,Object> compare(List<double[]> actual,List<double[]> expected) {
        // Evaluate every fine reference row against piecewise-linear outer-grid output.
        // At duplicate event times compare the matching left/right samples, never interpolate across an event.
        double[] maximum=new double[17],times=new double[17],sum=new double[17];int left=0,samples=0;
        for(int r=0;r<expected.size();r++) {
            var target=expected.get(r);double t=target[0];
            while(left+1<actual.size() && actual.get(left+1)[0]<t-2e-6)left++;
            if(Math.abs(actual.get(left)[0]-t)>=2e-6 && left+1<actual.size()
                    && Math.abs(actual.get(left+1)[0]-t)<2e-6)left++;
            double[] observed=new double[18];var a=actual.get(left);
            if(Math.abs(a[0]-t)<2e-6) {
                // First occurrence uses the left side; subsequent occurrence uses the right side.
                boolean duplicate=r>0 && Math.abs(expected.get(r-1)[0]-t)<1e-8;
                if(duplicate && left+1<actual.size() && Math.abs(actual.get(left+1)[0]-t)<2e-6)a=actual.get(++left);
                observed=a;
            } else if(left+1<actual.size()) {
                var b=actual.get(left+1);double f=(t-a[0])/(b[0]-a[0]);
                for(int i=1;i<18;i++)observed[i]=a[i]+f*(b[i]-a[i]);
            } else throw new IllegalStateException("Missing comparison endpoint "+t);
            for(int i=0;i<17;i++){double error=Math.abs(observed[i+1]-target[i+1]);sum[i]+=error*error;if(error>maximum[i]){maximum[i]=error;times[i]=t;}}
            samples++;
        }
        for(int i=0;i<17;i++)sum[i]=Math.sqrt(sum[i]/samples);
        return Map.of("maximum",maximum,"maximum_times_s",times,"rmse",sum,"reference_rows_compared",samples,
                "alignment","all nonnegative reference rows; piecewise-linear outer-grid output; duplicate event sides retained");
    }
    public static void main(String[] args)throws Exception {
        if(args.length<3 || args.length>4)throw new IllegalArgumentException("reference-root output-root accuracy|timing [network-interval]");
        if(args.length==4)networkInterval=Double.parseDouble(args[3]);
        Path reference=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        IpssCorePlugin.init();java.util.logging.Logger.getLogger("").setLevel(Level.SEVERE);
        var gson=new GsonBuilder().setPrettyPrinting().create();var results=new ArrayList<Map<String,Object>>();
        for(String profile:List.of("fault","high-gain","minimum")) {
            Path input=reference.resolve("regcc-"+profile+"-h0.000260417-a");
            if(args[2].equals("accuracy")) {
                for(int count:COUNTS){
                    var row=new LinkedHashMap<String,Object>();row.put("profile",profile);row.put("substeps",count);
                    try {
                        var result=run(input,out.resolve(profile+"-n"+count+".csv"),count,.2);
                        row.put("status","completed");row.put("effective_te_s",result.effectiveTe);row.put("recovery_time_s",result.recoveryTime);
                        row.put("recorded_samples",result.rows.size());row.put("recorded_channels",17);
                        row.put("fine_reference",compare(result.rows,reference(input.resolve("native.csv"))));
                        row.put("matched_reference",compare(result.rows,reference(reference.resolve("regcc-"+profile+"-h0.00416667-a/native.csv"))));
                    }catch(Exception error){row.put("status","failed");row.put("error",error.toString());}
                    results.add(row);Files.writeString(out.resolve("accuracy.json"),gson.toJson(results));
                    System.out.println(gson.toJson(row));
                }
            } else if(args[2].equals("timing")) {
                // Complete fresh-network invocations, interleaved counts after four warmups each.
                for(int iteration=0;iteration<9;iteration++)for(int count:COUNTS) {
                    var row=new LinkedHashMap<String,Object>();row.put("profile",profile);row.put("substeps",count);
                    row.put("iteration",iteration);row.put("warmup",iteration<4);
                    try {
                        var result=run(input,out.resolve(profile+"-n"+count+"-run"+iteration+".csv"),count,10.);
                        row.put("status","completed");row.put("whole_seconds",result.seconds);row.put("samples",result.rows.size());
                    }catch(Exception error){row.put("status","failed");row.put("error",error.toString());}
                    results.add(row);Files.writeString(out.resolve("timing.json"),gson.toJson(results));
                }
            } else throw new IllegalArgumentException("Unknown study mode");
        }
    }
}
