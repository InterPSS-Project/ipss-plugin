package org.interpss.core.dstab.reference;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import com.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Test-only boundary replay; no production solver, parameter or initial-state rewrite. */
public final class RegfmaLimitReplayProbe {
    private RegfmaLimitReplayProbe() { }
    private static final int[] STORAGE_TO_NATIVE={3,0,4,5,8,1,2,6,7};
    private static final Field P=field("p"),Q=field("q");
    private static Field field(String name) {
        try {var f=Regfma1Model.class.getDeclaredField(name);f.setAccessible(true);return f;}
        catch(ReflectiveOperationException ex){throw new ExceptionInInitializerError(ex);}
    }
    private static void require(boolean condition,String message) {
        if(!condition)throw new IllegalStateException(message);
    }
    private static double[] state(Regfma1Model m) {
        return m.getPsseStateSnapshot().values().stream().mapToDouble(Double::doubleValue).toArray();
    }
    private static double[] storage(double[] nativeState) {
        double[] x=new double[9];for(int i=0;i<9;i++)x[i]=nativeState[STORAGE_TO_NATIVE[i]];return x;
    }
    private static double upper(double x,double f){return x>=0 && f>0?0:f;}
    private static double lower(double x,double f){return x<=0 && f<0?0:f;}
    private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static double[] rates(double[] x,double[] input,double[] p,double pref,double vref) {
        double[] f=new double[9];double cmd=vref-p[11]*x[4]
            +Math.min(0,p[14]*(p[8]-x[4])+x[6])+Math.max(0,p[14]*(p[9]-x[4])+x[7]);
        f[0]=(2*input[21]-x[0])/p[0];
        f[1]=upper(x[1],p[13]*(p[6]-x[0]));f[2]=lower(x[2],p[13]*(p[7]-x[0]));
        f[3]=2*Math.PI*60*(p[10]*(pref-x[0])
            +Math.min(0,p[12]*(p[6]-x[0])+x[1])+Math.max(0,p[12]*(p[7]-x[0])+x[2]));
        f[4]=(2*input[22]-x[4])/p[1];f[5]=(input[18]-x[5])/p[2];
        f[6]=upper(x[6],p[15]*(p[8]-x[4]));f[7]=lower(x[7],p[15]*(p[9]-x[4]));
        double error=cmd-x[5];f[8]=((x[8]>=p[4] && error>0)||(x[8]<=p[5] && error<0))?0:p[17]*error;
        return f;
    }
    private static double[] projected(double[] x,double[] p) {
        x[1]=Math.min(0,x[1]);x[2]=Math.max(0,x[2]);x[6]=Math.min(0,x[6]);x[7]=Math.max(0,x[7]);
        x[8]=clip(x[8],p[5],p[4]);return x;
    }
    private static double error(double[] a,double[] b) {
        double worst=0;for(int i=0;i<a.length;i++)worst=Math.max(worst,Math.abs(a[i]-b[i]));return worst;
    }
    private static void boundary(Regfma1Model model,double[] input) throws IllegalAccessException {
        // Only measured physical boundary inputs are replayed. Private access
        // deliberately stays in this test utility rather than adding a runtime
        // setter which could break the Norton/network consistency contract.
        P.setDouble(model,2*input[21]);Q.setDouble(model,2*input[22]);
        model.getDStabBus().setVoltage(new Complex(input[18],0));
    }

    public static void main(String[] args) throws Exception {
        Path folder=Path.of(args[0]),output=Path.of(args[1]);double step=Double.parseDouble(args[2]);
        require(!Files.exists(output),"Do not overwrite a replay");require(step==.0005||step==.00025,"Unsupported step");
        List<String> lines=Files.readAllLines(folder.resolve("native.csv"));
        List<double[]> reference=new ArrayList<>();for(String line:lines.subList(1,lines.size()))
            reference.add(Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray());
        String[] fields=Files.readAllLines(folder.resolve("dynamic.dyr")).get(0).split("\\s+");
        require(fields.length==24,"Exact two-selector/eighteen-constant record required");
        double[] p=Arrays.stream(fields,5,23).mapToDouble(Double::parseDouble).toArray();
        IpssCorePlugin.init();var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();net.setAllowGenWithoutMach(true);
        alg.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);alg.getAclfAlgorithm().setTolerance(1e-10);
        require(alg.getAclfAlgorithm().loadflow(),"Load flow failed");alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        alg.setSimuStepSec(step);alg.setTotalSimuTimeSec(.3);alg.setSimuOutputHandler(new StateMonitor());
        require(alg.initialization(),"Initialization failed");
        var model=(Regfma1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        double[] initial=state(model);double pref=initial[0],vref=initial[5]+p[11]*initial[4];
        double[] previous=null,maxima=new double[9],times=new double[9];double equationError=0,recurrenceError=0;
        int history=0,duplicates=0,steps=0;
        try(var writer=Files.newBufferedWriter(output)) {
            writer.write("raw_index,raw_time_s,entered_step_s,side");
            for(int i=0;i<9;i++)writer.write(",state_"+i+",native_"+i+",predictor_rate_"+i+",corrector_rate_"+i);
            writer.write("\n");
            for(int index=0;index<reference.size();index++) {
                double[] row=reference.get(index),first=new double[9],second=new double[9];String side;
                if(row[0]<0){history++;side="history-no-advance";}
                else if(previous!=null && row[0]==previous[0]){duplicates++;side="event-input-refresh-no-advance";}
                else if(row[0]==0){side="initialized-zero-no-advance";}
                else {
                    side="integrated-endpoint";steps++;
                    require(previous!=null && previous[0]>=0,"Missing input origin");
                    double[] start=state(model);first=rates(start,previous,p,pref,vref);boundary(model,previous);
                    require(model.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,0),"Predictor failed");
                    double[] observed=model.getStateDerivativeSnapshot().rates().values().stream().mapToDouble(Double::doubleValue).toArray();
                    equationError=Math.max(equationError,error(storage(first),observed));
                    double[] predicted=start.clone();for(int i=0;i<9;i++)predicted[i]+=step*first[i];projected(predicted,p);
                    recurrenceError=Math.max(recurrenceError,error(predicted,state(model)));
                    second=rates(predicted,row,p,pref,vref);boundary(model,row);
                    require(model.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,1),"Corrector failed");
                    observed=model.getStateDerivativeSnapshot().rates().values().stream().mapToDouble(Double::doubleValue).toArray();
                    equationError=Math.max(equationError,error(storage(second),observed));
                    double[] corrected=start.clone();for(int i=0;i<9;i++)corrected[i]+=.5*step*(first[i]+second[i]);projected(corrected,p);
                    recurrenceError=Math.max(recurrenceError,error(corrected,state(model)));
                }
                double[] actual=state(model);writer.write(index+","+row[0]+","+step+","+side);
                for(int i=0;i<9;i++) {
                    double difference=Math.abs(actual[i]-row[i+1]);if(difference>maxima[i]){maxima[i]=difference;times[i]=row[0];}
                    writer.write(","+actual[i]+","+row[i+1]+","+first[i]+","+second[i]);
                }
                writer.write("\n");if(row[0]>=0)previous=row;
            }
        }
        require(history==2 && duplicates==2 && steps==Math.round(.3/step),"History/event accounting mismatch");
        require(equationError<1e-10 && recurrenceError<1e-12,"Equation/stage inconsistency");
        System.out.println("REPLAY rows="+reference.size()+" history="+history+" eventPosts="+duplicates+" steps="+steps
            +" equationError="+equationError+" recurrenceError="+recurrenceError
            +" maxima="+Arrays.toString(maxima)+" times="+Arrays.toString(times)+" trajectoryAcceptance=false");
    }
}
