package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.dstab.renewable.Repca1Model.Measurement;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.cache.StateMonitor;

/** Boundary-only localization called by the registered plant-controller tests. */
public final class RepcaNativeVerification {
    private RepcaNativeVerification() {}
    public static List<Map<String,Double>> rows(String body) {
        var lines=body.lines().toList();var labels=lines.getFirst().split(",");var rows=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())) {
            var fields=line.split(",");assertEquals(labels.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int i=0;i<labels.length;i++)row.put(labels[i],Double.valueOf(fields[i]));rows.add(row);
        }
        return rows;
    }
    private static Complex voltage(Map<String,Double> row,int bus) {
        double angle=Math.toRadians(row.get("A"+bus+"_DEG")),v=row.get("V"+bus);
        return new Complex(v*Math.cos(angle),v*Math.sin(angle));
    }
    private static Measurement measurement(RepcaNativeReference.Profile p,Map<String,Double> row) {
        var data=p.data();int remote=data.remoteBus()==0?1:data.remoteBus();boolean branch=data.branchFromBus()!=0;
        double active=branch?row.get("PLANT_VAR4")/100:row.get("P_PU");
        double reactive=branch?row.get("PLANT_VAR5")/100:row.get("Q_PU");
        Complex current=new Complex(active,reactive).divide(voltage(row,remote)).conjugate();
        return new Measurement(active,reactive,voltage(row,remote),current,1+row.get("F"+remote+"_DEV"));
    }
    public static void replayAllStates()throws Exception {
        for(var p:RepcaNativeReference.profiles()) {
            var rows=rows(p.body());assertEquals(p.samples(),rows.size());assertEquals(57,rows.getFirst().size());
            for(boolean history:new boolean[]{false,true}) {
                var first=rows.getFirst();var previous=first;var model=Repca1Model.fromNativeRecord(p.data());
                model.initializeMeasured(first.get("P_PU"),first.get("Q_PU"),measurement(p,first));
                double[] maximum=new double[7],at=new double[7];int events=0;
                var trace=new StringBuilder("time_s");for(int i=0;i<7;i++)trace.append(",ipss_state").append(i).append(",native_state").append(i);trace.append('\n');
                for(var row:rows) {
                    if(row.get("time_s")>previous.get("time_s")) {
                        if(history)model.stepTwoStepMeasured(p.step(),measurement(p,previous));
                        else{model.stepMeasured(p.step(),measurement(p,previous),0);model.stepMeasured(p.step(),measurement(p,row),1);}
                    }else if(row!=first&&row.get("time_s")>=0) {
                        events++;
                        if(history){model.rebaseNextTwoStep();model.stepTwoStepMeasured(0,measurement(p,row));}
                        else model.stepMeasured(0,measurement(p,row),1);
                    }
                    trace.append(row.get("time_s"));double[] actual=model.getNativeStates();
                    for(int i=0;i<7;i++){assertTrue(Double.isFinite(actual[i]));double target=row.get("PLANT_STATE"+i),error=Math.abs(actual[i]-target);if(error>maximum[i]){maximum[i]=error;at[i]=row.get("time_s");}trace.append(',').append(actual[i]).append(',').append(target);}trace.append('\n');previous=row;
                }
                assertEquals(2,events);var file=Path.of("target/repca-native-replay",p.name()+(history?"-history":"-staged")+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
                System.out.println("REPCA replay "+p.name()+" history="+history+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
                double[] bounds=history?p.historyBounds():p.stagedBounds();
                for(int i=0;i<7;i++)assertTrue(maximum[i]<=bounds[i],p.name()+" history="+history+" state "+i+": "+maximum[i]+" > "+bounds[i]);
            }
        }
    }

    public static void regulatedVoltageHasAnIndependentNativeInitializationWitness() {
        int witnesses=0;
        for(var p:RepcaNativeReference.profiles())if(p.name().startsWith("ldc-")) {
            var first=rows(p.body()).getFirst();var model=Repca1Model.fromNativeRecord(p.data());var measured=measurement(p,first);
            model.initializeMeasured(first.get("P_PU"),first.get("Q_PU"),measured);
            assertEquals(first.get("PLANT_STATE0"),model.getNativeStates()[0],2e-8);
            if(!p.name().startsWith("ldc-zero")) {
                var originCurrent=new Complex(measured.p(),measured.q()).divide(voltage(first,1)).conjugate();
                model.initializeMeasured(first.get("P_PU"),first.get("Q_PU"),new Measurement(measured.p(),measured.q(),measured.voltage(),originCurrent,measured.frequency()));
                assertTrue(Math.abs(model.getNativeStates()[0]-first.get("PLANT_STATE0"))>4e-4,"Origin-current substitution is not native compensation");
                witnesses++;
            }
        }
        assertEquals(2,witnesses);
    }

    public static void importAndRunLocalProfiles(Path staged)throws Exception {
        for(var p:RepcaNativeReference.profiles()) {
            Path directory=staged.resolve(p.name());Files.createDirectories(directory);
            Files.writeString(directory.resolve("network.raw"),p.network());Files.writeString(directory.resolve("dynamic.dyr"),p.input());
            var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("network.raw").toString());
            var network=context.getDStabilityNet();network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
            var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(network)).setStrictImport(true);
            parser.parseDynFile(directory.resolve("dynamic.dyr").toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(4,parser.getLastImportReport().totalRecordCount());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(p.step());algorithm.setSimuOutputHandler(new StateMonitor());
            algorithm.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
            if(p.hasFault())network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus2",network,
                    com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new Complex(0,100.0/(230.0*230.0/100.0)),null,.05,.05),"PlantFault");
            assertTrue(algorithm.initialization());
            var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
            var model=converter.getReeca1Controller().getPlantController();assertTrue(model.isNativeRecordConvention());assertEquals(p.data(),model.getData());
            var trace=new StringBuilder("time_s,V1,V2,V3,P_PU,Q_PU");for(int i=0;i<7;i++)trace.append(",state").append(i);trace.append('\n');
            for(int tick=0;tick<=Math.round(.5/p.step());tick++) {
                trace.append(tick*p.step());
                for(int bus=1;bus<=3;bus++){double value=network.getBus("Bus"+bus).getVoltageMag();assertTrue(Double.isFinite(value));trace.append(',').append(value);}
                var states=converter.getStates(null);
                for(String key:new String[]{"REGCA1_P","REGCA1_Q"}){double value=((Number)states.get(key)).doubleValue();assertTrue(Double.isFinite(value));trace.append(',').append(value);}
                for(double value:model.getNativeStates()){assertTrue(Double.isFinite(value));trace.append(',').append(value);}trace.append('\n');
                if(tick<Math.round(.5/p.step()))assertTrue(algorithm.solveDEqnStep(true));
            }
            var file=Path.of("target/repca-coupled-diagnostics",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
        }
    }
}
