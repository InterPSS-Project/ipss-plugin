package org.interpss.core.dstab.reference;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseDStabSolver;

/** All-bus no-event diagnostic; acceptance requires a complete imported stack as a separate gate. */
public final class Texas7kFlatStartAudit {
    private Texas7kFlatStartAudit(){}
    private static final class AuditSolver extends PsseDStabSolver {
        AuditSolver(DynamicSimuAlgorithm algorithm,double tolerance){super(algorithm);converge_tol=tolerance;}
    }
    public static void main(String[] args)throws Exception {
        if(args.length<2||args.length>4)throw new IllegalArgumentException("Expected case, new output directory, optional duration and network tolerance");
        run(Path.of(args[0]),Path.of(args[1]),args.length>=3?Double.parseDouble(args[2]):10,args.length==4?Double.parseDouble(args[3]):1e-8);
    }
    public static Map<String,Object> run(Path input,Path output)throws Exception {
        return run(input,output,10);
    }
    public static Map<String,Object> run(Path input,Path output,double duration)throws Exception {
        return run(input,output,duration,1e-8);
    }
    public static Map<String,Object> run(Path input,Path output,double duration,double tolerance)throws Exception {
        if(!Double.isFinite(duration)||duration<=0)throw new IllegalArgumentException("Positive finite duration required");
        if(!Double.isFinite(tolerance)||tolerance<=0)throw new IllegalArgumentException("Positive finite network tolerance required");
        if(Files.exists(output))throw new IllegalArgumentException("Refusing to replace audit evidence");
        Files.createDirectories(output);var result=new LinkedHashMap<String,Object>();
        long started=System.nanoTime();
        try {
            IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
            var loader=new PSSEMultiFileLoader();
            var context=loader.loadDStab(input.resolve("Texas7k_20210804.RAW").toString(),input.resolve("Texas7k_20210804.dyr").toString());
            var report=loader.getLastDynamicImportReport();report.writeJson(output.resolve("import-report.json"));
            result.put("import_complete",report.isStrictlyComplete());result.put("import_summary",report.failureSummary());
            result.put("bsg_entries",loader.getLastResponseEntries().size());
            var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();var lf=algorithm.getAclfAlgorithm();
            net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I);
            net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
            net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadZPartInYMatrix(true);
            lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);lf.getDataCheckConfig().setTurnOffIslandBus(true);
            lf.setNonDivergent(true);lf.setMaxIterations(50);lf.setTolerance(.001);
            if(!lf.loadflow())throw new IllegalStateException("Power flow failed: "+net.maxMismatch(com.interpss.core.algo.AclfMethodType.NR));
            result.put("power_flow_mismatch",net.maxMismatch(com.interpss.core.algo.AclfMethodType.NR).toString());
            algorithm.setSimuStepSec(1./240);algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algorithm.setTotalSimuTimeSec(duration);algorithm.setSolver(new AuditSolver(algorithm,tolerance));
            result.put("network_voltage_correction_tolerance_pu",tolerance);
            algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            if(!algorithm.initialization())throw new IllegalStateException("Dynamic initialization failed");
            var disabled=new ArrayList<String>();var machines=new ArrayList<Machine>();
            for(var bus:net.getBusList())for(var gen:bus.getContributeGenList())if(gen.isActive()&&gen.getMach()!=null) {
                var m=gen.getMach();machines.add(m);
                for(var controller:m.getControllerList())if(!controller.isActive())disabled.add(m.getId()+"/"+controller.getName());
            }
            result.put("disabled_controllers",disabled);
            Texas2kInitializationAudit.write(net,output.resolve("initial-states.json"));
            double[] initialV=new double[net.getBusList().size()],initialAngle=new double[machines.size()];
            for(int i=0;i<initialV.length;i++)initialV[i]=net.getBusList().get(i).getVoltageMag();
            for(int i=0;i<machines.size();i++)initialAngle[i]=machines.get(i).getAngle();
            double maxV=0,maxSpeed=0,maxAngle=0;String worstV="",worstSpeed="",worstAngle="";
            int ticks=(int)Math.ceil(duration*240),progressInterval=Math.max(1,ticks/10);
            for(int tick=0;tick<ticks;tick++) {
                if(!algorithm.solveDEqnStep(true))throw new IllegalStateException("Simulation failed at "+algorithm.getSimuTime());
                if(tick==0)Texas2kInitializationAudit.write(net,output.resolve("first-step-states.json"));
                for(int i=0;i<initialV.length;i++) {
                    var bus=net.getBusList().get(i);if(!bus.isActive())continue;
                    double error=Math.abs(bus.getVoltageMag()-initialV[i]);
                    if(!Double.isFinite(error))throw new IllegalStateException("Nonfinite voltage "+bus.getId());
                    if(error>maxV){maxV=error;worstV=bus.getId();}
                }
                for(int i=0;i<machines.size();i++) {
                    var m=machines.get(i);double speed=Math.abs(m.getSpeed()-1),angle=Math.abs(Math.toDegrees(m.getAngle()-initialAngle[i]));
                    if(!Double.isFinite(speed)||!Double.isFinite(angle))throw new IllegalStateException("Nonfinite machine state "+m.getId());
                    if(speed>maxSpeed){maxSpeed=speed;worstSpeed=m.getId();}
                    if(angle>maxAngle){maxAngle=angle;worstAngle=m.getId();}
                }
                if((tick+1)%progressInterval==0)System.out.println("TEXAS7K_PROGRESS time="+algorithm.getSimuTime()+" voltage="+maxV+" speed="+maxSpeed+" angle="+maxAngle);
            }
            Texas2kInitializationAudit.write(net,output.resolve("final-states.json"));
            result.put("max_voltage_drift_pu",maxV);result.put("worst_voltage_bus",worstV);
            result.put("max_speed_drift_pu",maxSpeed);result.put("worst_speed_machine",worstSpeed);
            result.put("max_angle_drift_deg",maxAngle);result.put("worst_angle_machine",worstAngle);
            result.put("simulated_seconds",algorithm.getSimuTime());
            result.put("status","diagnostic-complete");
        }catch(Exception failure){result.put("status","failed");result.put("failure",failure.toString());throw failure;}
        finally {
            result.put("total_seconds",(System.nanoTime()-started)/1e9);
            Files.writeString(output.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n",StandardOpenOption.CREATE_NEW);
            System.out.println("TEXAS7K_AUDIT "+result);
        }
        return result;
    }
}
