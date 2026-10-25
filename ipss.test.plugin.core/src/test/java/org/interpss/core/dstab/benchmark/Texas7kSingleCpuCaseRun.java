package org.interpss.core.dstab.benchmark;

import java.nio.file.*;
import java.nio.*;
import java.io.*;
import java.util.*;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.core.dstab.reference.EndpointAlignedStateMonitor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;

/** Ten-second fault run with all-bus voltage recovery checks and retained endpoint samples. */
public final class Texas7kSingleCpuCaseRun {
    static final class TimedSolver extends org.interpss.dstab.algo.DefaultDynamicSimulationSolver {
        long calls,iterations,nanos,integrationNanos; int maximum;
        @Override public void diffEqnIntegrationStep(double t,double dt,DynamicSimuMethod method,int flag)throws com.interpss.dstab.common.DStabSimuException {long start=System.nanoTime();try{super.diffEqnIntegrationStep(t,dt,method,flag);}finally{integrationNanos+=System.nanoTime()-start;}}
        final java.util.Map<String,long[]> stages=new java.util.LinkedHashMap<>();
        TimedSolver(com.interpss.dstab.algo.DynamicSimuAlgorithm a){super(a);}
        @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
            long start=System.nanoTime(); boolean ok=super.networkSolutionStep();nanos+=System.nanoTime()-start;
            var d=getLastNetworkSolutionDiagnostics();calls++;iterations+=d.iterations();maximum=Math.max(maximum,d.iterations());
            var stage=stages.computeIfAbsent(d.stage(),k->new long[2]);stage[0]++;stage[1]+=d.iterations();return ok;
        }
    }
    public static void main(String[] args)throws Exception {
        var configuration=com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[3]))).getAsJsonObject().getAsJsonObject("optimization_properties");
        long totalStart=System.nanoTime();var phaseTimes=new LinkedHashMap<String,Double>();long phaseStart=totalStart;
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        if(Files.exists(output))throw new IllegalArgumentException("Refusing to overwrite results");
        Files.createDirectories(output);
        org.interpss.IpssCorePlugin.init();org.interpss.IpssCorePlugin.configureSparseSolver("java-klu");
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(input.resolve("Texas7k_20210804.RAW").toString(),input.resolve("Texas7k_20210804.dyr").toString());
        if(!loader.getLastDynamicImportReport().isStrictlyComplete())throw new AssertionError(loader.getLastDynamicImportReport().failureSummary());
        phaseTimes.put("import_and_setup_s",(System.nanoTime()-phaseStart)/1e9);phaseStart=System.nanoTime();
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        var timed=new TimedSolver(algorithm);algorithm.setSolver(timed);if(configuration.get("ipss.profile.frequencyAll").getAsBoolean())timed.setBusFrequencyMode(org.interpss.dstab.algo.DefaultDynamicSimulationSolver.BusFrequencyMode.ALL);var lf=algorithm.getAclfAlgorithm();
        timed.getClass().getMethod("setAcceptedEndpointReuseEligibility",java.util.function.BooleanSupplier.class).invoke(timed,(java.util.function.BooleanSupplier)()->true);
        lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);lf.getDataCheckConfig().setTurnOffIslandBus(true);
        lf.setNonDivergent(true);lf.setMaxIterations(50);lf.setTolerance(.001);
        if(!lf.loadflow())throw new AssertionError("Power flow failed");
        phaseTimes.put("power_flow_s",(System.nanoTime()-phaseStart)/1e9);phaseStart=System.nanoTime();
        String mismatch=net.maxMismatch(com.interpss.core.algo.AclfMethodType.NR).toString();
        net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I);
        net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadZPartInYMatrix(true);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(1./240);algorithm.setTotalSimuTimeSec(10);
        algorithm.setOutPutPerSteps(1);
        var monitor=new EndpointAlignedStateMonitor("Bus111180");monitor.declareAdditionalFrequencyInputs(java.util.Set.of());
        monitor.addBusStdMonitor(new String[]{"Bus111180"});
        if(configuration.get("ipss.experiment.preparedRecording").getAsBoolean())monitor.prepareMonitoring();
        algorithm.setSimuOutputHandler(monitor);
        net.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus111180",net,
            com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,org.apache.commons.math3.complex.Complex.ZERO,
            org.apache.commons.math3.complex.Complex.ZERO,.1,5./60),"FiveCycleFault@Bus111180");
        if(configuration.get("ipss.profile.rectangular").getAsBoolean())net.setPolarCoordinate(false);
        if(configuration.get("ipss.experiment.networkAnderson").getAsBoolean())timed.setExperimentAccelerationEligibility(()->true);
        if(!algorithm.initialization())throw new AssertionError("Initialization failed");
        phaseTimes.put("dynamic_configuration_and_initialization_s",(System.nanoTime()-phaseStart)/1e9);phaseStart=System.nanoTime();
        var indexedMethod=net.getClass().getMethod("setIndexedFixedCurrentInjections",java.util.Map.class);indexedMethod.invoke(net,net.getCustomBusCurrInjHashtable());net.setCustomBusCurrInjHashtable(null);
        int st4Direct=0,pssDirect=0,typedStages=0;
        if(configuration.get("ipss.profile.compiledControllers").getAsBoolean()) {
            int compiled=0,uncovered=0;
            for(var bus:net.getBusList())for(var generator:bus.getContributeGenList()) {
                var machine=generator.getMach();if(machine==null)continue;
                for(Object controller:new Object[]{machine.getExciter(),machine.getGovernor(),machine.getStabilizer()})
                    if(controller instanceof com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController c) {
                        try {c.compileRuntimePlan();compiled++;if(c instanceof org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer && c.hasCompiledStageEvaluator())typedStages++;
                            if(c instanceof org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter st4){st4.enableDirectEvaluationExperiment();st4Direct++;}
                            if(c instanceof org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer pss){pss.enableDirectEvaluationExperiment();pssDirect++;}
                        }
                        catch(com.interpss.common.exp.InterpssException unsupported){uncovered++;}
                    }
            }
            System.out.println("COMPILED_CONTROLLERS compiled="+compiled+" uncovered="+uncovered+" st4_direct="+st4Direct+" pss_direct="+pssDirect);
        }
        phaseTimes.put("fixed_current_binding_and_controller_compilation_s",(System.nanoTime()-phaseStart)/1e9);phaseStart=System.nanoTime();
        List<BaseDStabBus<?,?>> buses=new ArrayList<>();for(var bus:net.getBusList())buses.add(bus);
        int count=buses.size();boolean[] active=new boolean[count];double[] initial=new double[count];
        var machines=new ArrayList<Machine>();
        for(String id:new String[]{"Bus111180-mach1","Bus111181-mach1","Bus111183-mach1"}) {
            var machine=net.getMachine(id);if(machine==null)throw new AssertionError("Missing "+id);machines.add(machine);
        }
        for(int i=0;i<count;i++) {
            active[i]=buses.get(i).isActive();initial[i]=active[i]?buses.get(i).getVoltageMag():0;
            if(active[i]&&!(initial[i]>0&&Double.isFinite(initial[i])))throw new AssertionError("Invalid initial voltage "+buses.get(i).getId());
        }
        int width=count+5*machines.size();long maximumBytes=12L+4801L*(20L+8L*width);
        if(maximumBytes>512L*1024*1024)throw new IllegalArgumentException("Trace budget exceeded");
        if(Files.getFileStore(output).getUsableSpace()-maximumBytes<2L*1024*1024*1024)throw new IOException("Recording requires a two-GiB free-space reserve");
        monitor.initializeStateCapture(algorithm.getSimuTime(),()-> {
            double[] values=new double[width];
            for(int i=0;i<count;i++)values[i]=buses.get(i).isActive()?buses.get(i).getVoltageMag():0;
            int k=count;
            for(var machine:machines){values[k++]=Math.toDegrees(machine.getAngle());values[k++]=machine.getSpeed();values[k++]=machine.getPe();values[k++]=machine.getPm();values[k++]=machine.getEfd();}
            return values;
        });
        var frames=monitor.frames();String failure=null;long dynamicStart=System.nanoTime();phaseTimes.put("recording_setup_s",(dynamicStart-phaseStart)/1e9);
        try {
            for(int tick=0;tick<2400;tick++) {
                monitor.beginStep(algorithm.getSimuTime(),1./240);
                if(!algorithm.solveDEqnStep(true))throw new IllegalStateException("Simulation step failed");
                monitor.endStep(algorithm.getSimuTime());frames=monitor.frames();
                if((tick+1)%240==0)System.out.println("PROFILE_PROGRESS "+algorithm.getSimuTime()+" calls="+timed.calls+" iterations="+timed.iterations+" max="+timed.maximum+" primitive="+((com.interpss.dstab.impl.BaseDStabNetworkImpl<?,?>)net).getPrimitiveCurrentSolveCount()+" network_s="+timed.nanos/1e9);
            }
        } catch(Exception problem){failure=problem.toString();}
        net.getClass().getMethod("closePackedWorkers").invoke(net);timed.getClass().getMethod("closeOwnedMachineWorkers").invoke(timed);
        double dynamicSeconds=(System.nanoTime()-dynamicStart)/1e9;phaseTimes.put("stepping_and_in_memory_recording_s",dynamicSeconds);phaseTimes.put("network_s",timed.nanos/1e9);phaseTimes.put("device_and_measurement_integration_s",timed.integrationNanos/1e9);phaseStart=System.nanoTime();
        var last=frames.getLast();double[] finalValues=last.states();
        double[] finalDeviation=new double[count],tailMax=new double[count],postMax=new double[count],settled=new double[count];Arrays.fill(settled,Double.NaN);
        int activeCount=0;for(boolean flag:active)if(flag)activeCount++;
        var names=new ArrayList<String>();for(var bus:buses)names.add("V_pu__"+bus.getId());
        for(var machine:machines)for(String quantity:new String[]{"angle_deg","speed_pu","airgap_power_pu_mbase","mechanical_power_pu_mbase","efd_pu"})names.add(quantity+"__"+machine.getId());
        var json=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        Files.writeString(output.resolve("channels.json"),json.toJson(Map.of("magic","IPSSVLT1","byte_order","big_endian","channels",names,"bus_count",count,"record_layout","physical_time_f64,solver_time_f64,sample_kind_i32,channels_f64","sample_kinds",Arrays.stream(EndpointAlignedStateMonitor.SampleKind.values()).map(Enum::name).toList()))+"\n",StandardOpenOption.CREATE_NEW);
        long binaryNanos=0,recoveryNanos=0;
        try(var binary=new BufferedOutputStream(Files.newOutputStream(output.resolve("all_bus_voltage_and_selected_machines.bin"),StandardOpenOption.CREATE_NEW));
            var time=Files.newBufferedWriter(output.resolve("voltage_recovery_time_series.csv"),StandardOpenOption.CREATE_NEW)) {
            binary.write(ByteBuffer.allocate(12).put("IPSSVLT1".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(width).array());
            ByteBuffer row=ByteBuffer.allocate(20+8*width);
            time.write("time_s,solver_time_s,sample_kind,buses_outside_2pct,max_abs_deviation_pct,worst_bus,min_voltage_ratio,max_voltage_ratio\n");
            for(var frame:frames) {
                long rowStart=System.nanoTime();double[] values=frame.states();row.clear();row.putDouble(frame.time()).putDouble(frame.solverTime()).putInt(frame.kind().ordinal());
                row.asDoubleBuffer().put(values);binary.write(row.array());binaryNanos+=System.nanoTime()-rowStart;rowStart=System.nanoTime();
                int outside=0;double worst=-1,minimum=Double.POSITIVE_INFINITY,maximum=0;String worstBus="";
                for(int i=0;i<count;i++)if(active[i]) {
                    double ratio=values[i]/initial[i],deviation=100*Math.abs(ratio-1);
                    if(deviation>2)outside++;
                    if(deviation>worst){worst=deviation;worstBus=buses.get(i).getId();}
                    minimum=Math.min(minimum,ratio);maximum=Math.max(maximum,ratio);
                    if(frame.time()>.1+5./60+1e-9) {
                        postMax[i]=Math.max(postMax[i],deviation);
                        if(deviation>2)settled[i]=Double.NaN;else if(Double.isNaN(settled[i]))settled[i]=frame.time();
                    }
                    if(frame.time()>=9-1e-9)tailMax[i]=Math.max(tailMax[i],deviation);
                }
                time.write(frame.time()+","+frame.solverTime()+","+frame.kind()+","+outside+","+worst+","+worstBus+","+minimum+","+maximum+"\n");recoveryNanos+=System.nanoTime()-rowStart;
            }
        }
        var violations=new ArrayList<Map<String,Object>>();int finalPassing=0,tailPassing=0;double worst=0;String worstBus="";
        try(var table=Files.newBufferedWriter(output.resolve("all_bus_recovery.csv"),StandardOpenOption.CREATE_NEW)) {
            table.write("bus,initially_active,initial_voltage_pu,final_voltage_pu,final_signed_deviation_pct,within_2pct_at_end,max_abs_deviation_pct_last_second,within_2pct_last_second,max_postclear_abs_deviation_pct,last_reentry_time_s\n");
            for(int i=0;i<count;i++) {
                double v=finalValues[i];String bus=buses.get(i).getId();
                if(!active[i]){table.write(bus+",false,"+initial[i]+","+v+",,,,,,\n");continue;}
                double deviation=100*(v/initial[i]-1);finalDeviation[i]=deviation;
                boolean pass=Math.abs(deviation)<=2,tailPass=last.time()>=10-1e-7&&tailMax[i]<=2;
                if(pass)finalPassing++;if(tailPass)tailPassing++;
                if(Math.abs(deviation)>worst){worst=Math.abs(deviation);worstBus=bus;}
                if(!pass)violations.add(Map.of("bus",bus,"initial_voltage_pu",initial[i],"final_voltage_pu",v,"deviation_pct",deviation));
                table.write(bus+",true,"+initial[i]+","+v+","+deviation+","+pass+","+tailMax[i]+","+tailPass+","+postMax[i]+","+(Double.isFinite(settled[i])?Double.toString(settled[i]):"")+"\n");
            }
        }
        violations.sort(Comparator.comparingDouble(a->-Math.abs(((Number)a.get("deviation_pct")).doubleValue())));
        boolean complete=failure==null&&Math.abs(last.time()-10)<1e-7;
        var summary=new LinkedHashMap<String,Object>();summary.put("folded_zip",configuration.get("ipss.experiment.foldedZip").getAsBoolean());summary.put("jdk_voltage_angle",configuration.get("ipss.experiment.jdkVoltageAngle").getAsBoolean());summary.put("folded_static_rows",net.getClass().getMethod("getFoldedStaticRows").invoke(net));summary.put("passive_bus_update_skips",timed.getPassiveBusUpdateSkips());summary.put("prepared_recording",monitor.isMonitoringPrepared());
        int requestedDeviceOutputs=0,availableDevices=0;
        for(var bus:net.getBusList())for(var generator:bus.getContributeGenList())if(generator.isActive()&&generator.getMach()==null&&generator.getDynamicGenDevice() instanceof com.interpss.dstab.device.DynamicBusDevice device&&device.isActive()){
            availableDevices++;if(monitor.wantsOutput(com.interpss.dstab.datatype.DStabSimuEvent.PlotStepDynamicBusDeviceStates,device.getExtendedDeviceId()))requestedDeviceOutputs++;
        }
        summary.put("available_nonmachine_devices",availableDevices);summary.put("requested_nonmachine_device_state_outputs",requestedDeviceOutputs);
        int synchronous=0,activeGenerators=0,exciters=0,governors=0,stabilizers=0,inactiveControllers=0;
        for(var bus:net.getBusList())if(bus.isActive())for(var generator:bus.getContributeGenList())if(generator.isActive()){
            var device=generator.getDynamicGenDevice();if(device!=null&&device.isActive())activeGenerators++;
            var machine=generator.getMach();if(machine==null||!machine.isActive())continue;synchronous++;
            if(machine.getExciter()!=null){if(machine.getExciter().isActive())exciters++;else inactiveControllers++;}
            if(machine.getGovernor()!=null){if(machine.getGovernor().isActive())governors++;else inactiveControllers++;}
            if(machine.getStabilizer()!=null){if(machine.getStabilizer().isActive())stabilizers++;else inactiveControllers++;}
        }
        summary.put("active_device_inventory",Map.of("generators",activeGenerators,"synchronous_machines",synchronous,"converter_bundles",availableDevices,
            "exciters",exciters,"governors",governors,"stabilizers",stabilizers,"inactive_controllers",inactiveControllers));summary.put("bus_frequency_report",timed.getBusFrequencyReport());summary.put("bus_frequency_mode",timed.getBusFrequencyMode().name());summary.put("typed_ieeest_stages",typedStages);summary.put("direct_ieeest_stage",configuration.get("ipss.experiment.directIeeestStage").getAsBoolean());summary.put("bound_ieeest_parameters",configuration.get("ipss.experiment.bindIeeestParameters").getAsBoolean());summary.put("serial_traversal",configuration.get("ipss.experiment.serialTraversal").getAsBoolean());summary.put("empty_branch_phases_skipped",timed.getClass().getMethod("getEmptyBranchPhasesSkipped").invoke(timed));summary.put("lean_packed_entries",net.getClass().getMethod("getLeanPackedEntries").invoke(net));summary.put("accepted_endpoint_captures",net.getClass().getMethod("getAcceptedEndpointCaptures").invoke(net));summary.put("accepted_endpoint_probes",net.getClass().getMethod("getAcceptedEndpointProbes").invoke(net));summary.put("accepted_endpoint_reuses",net.getClass().getMethod("getAcceptedEndpointReuses").invoke(net));summary.put("owned_guard_seconds",((Number)timed.getClass().getMethod("getOwnedGuardNanos").invoke(timed)).longValue()/1e9);summary.put("owned_job_seconds",((Number)timed.getClass().getMethod("getOwnedJobNanos").invoke(timed)).longValue()/1e9);summary.put("owned_worker_nanos",timed.getClass().getMethod("getOwnedWorkerNanos").invoke(timed));summary.put("owned_machine_count",timed.getClass().getMethod("getOwnedMachineCount").invoke(timed));summary.put("owned_machine_advances",timed.getClass().getMethod("getOwnedMachineAdvances").invoke(timed));phaseTimes.put("raw_binary_encode_write_s",binaryNanos/1e9);phaseTimes.put("recovery_time_series_compute_write_s",recoveryNanos/1e9);summary.put("scalar_short_expressions",configuration.get("ipss.experiment.scalarShortExpressions").getAsBoolean());phaseTimes.put("serialization_and_recovery_tables_s",(System.nanoTime()-phaseStart)/1e9);summary.put("phase_times",phaseTimes);summary.put("parallel_packed_rows",configuration.get("ipss.experiment.parallelPackedRows").getAsBoolean());summary.put("uncompensated_sources",net.getClass().getMethod("getUncompensatedSources").invoke(net));summary.put("uncompensated_solves",net.getClass().getMethod("getUncompensatedSolves").invoke(net));summary.put("uncompensated_factorizations",net.getClass().getMethod("getUncompensatedFactorizations").invoke(net));summary.put("uncompensated_fallbacks",net.getClass().getMethod("getUncompensatedFallbacks").invoke(net));summary.put("st4_direct",st4Direct);summary.put("pss2a_direct",pssDirect);summary.put("packed_calls",net.getClass().getMethod("getPackedIterationCalls").invoke(net));summary.put("packed_solves",net.getClass().getMethod("getPackedIterationSolves").invoke(net));summary.put("primitive_current_solves",((com.interpss.dstab.impl.BaseDStabNetworkImpl<?,?>)net).getPrimitiveCurrentSolveCount());summary.put("primitive_static_rows",((com.interpss.dstab.impl.BaseDStabNetworkImpl<?,?>)net).getPrimitiveStaticRowCount());summary.put("network_iteration_limit",timed.getMaxIterationTimes());summary.put("network_stage_calls_iterations",timed.stages);summary.put("acceleration_mixes",timed.getExperimentMixes());summary.put("network_calls",timed.calls);summary.put("network_iterations",timed.iterations);summary.put("network_maximum_iterations",timed.maximum);summary.put("network_seconds",timed.nanos/1e9);summary.put("status",complete?"completed":"failed");summary.put("failure",failure);
        summary.put("source_commit",args[2]);summary.put("bus_count",count);summary.put("initially_active_buses",activeCount);summary.put("initially_inactive_buses",count-activeCount);
        summary.put("import_complete",true);summary.put("dynamic_records",loader.getLastDynamicImportReport().totalRecordCount());summary.put("bsg_entries",loader.getLastResponseEntries().size());
        summary.put("fault_bus",111180);summary.put("fault_applied_s",.1);summary.put("fault_cleared_s",.1+5./60);summary.put("fault_cycles",5);summary.put("frequency_hz",60);
        summary.put("step_s",1./240);summary.put("physical_final_time_s",last.time());summary.put("solver_final_time_s",algorithm.getSimuTime());summary.put("samples",frames.size());
        summary.put("threshold","abs(Vfinal/Vinitial - 1) <= 0.02");summary.put("buses_within_2pct_final",finalPassing);summary.put("buses_outside_2pct_final",activeCount-finalPassing);
        summary.put("buses_within_2pct_last_second",tailPassing);summary.put("max_abs_final_deviation_pct",worst);summary.put("worst_bus",worstBus);
        summary.put("all_active_buses_recovered_at_10_s",complete&&finalPassing==activeCount);summary.put("worst_violations",violations.subList(0,Math.min(20,violations.size())));
        summary.put("initial_power_flow_mismatch",mismatch);summary.put("dynamic_network_tolerance_pu",1e-4);summary.put("solver","java-klu");
        summary.put("dynamic_wall_seconds_unwarmed",dynamicSeconds);summary.put("total_wall_seconds_before_summary",(System.nanoTime()-totalStart)/1e9);
        Files.writeString(output.resolve("run.json"),json.toJson(summary)+"\n",StandardOpenOption.CREATE_NEW);
        System.out.println("TEN_SECOND_RECOVERY "+json.toJson(summary));
        if(!complete)throw new IllegalStateException("Incomplete simulation; partial samples retained: "+failure);
    }
}
