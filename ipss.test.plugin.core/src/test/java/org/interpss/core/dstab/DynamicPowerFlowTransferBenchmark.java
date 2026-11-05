package org.interpss.core.dstab;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.google.gson.*;
import org.interpss.core.dstab.benchmark.Texas7kSingleCpuCaseRun;

/** Matched complete fresh-network runs with unchanged integration and full selected recording. */
public final class DynamicPowerFlowTransferBenchmark {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final long BUDGET=512L*1024*1024,RESERVE=2L*1024*1024*1024;
    private static String hash(Path file)throws Exception {
        try(var in=Files.newInputStream(file)){var md=MessageDigest.getInstance("SHA-256");var buffer=new byte[1024*1024];int n;
            while((n=in.read(buffer))>=0)md.update(buffer,0,n);return HexFormat.of().formatHex(md.digest());}
    }
    private static void json(Path path,Object value)throws Exception {Files.writeString(path,JSON.toJson(value)+"\n",StandardOpenOption.CREATE_NEW);}
    private static long uniqueBytes(Path root)throws Exception {
        long total=0;var seen=new HashMap<Long,List<Path>>();
        try(var paths=Files.walk(root)){for(var p:paths.filter(Files::isRegularFile).toList()) {
            var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class);
            var equalSize=seen.computeIfAbsent(a.size(),ignored->new ArrayList<>());boolean shared=false;
            for(var previous:equalSize)if(Files.isSameFile(p,previous)){shared=true;break;}
            if(!shared){equalSize.add(p);total+=a.size();}
        }}return total;
    }
    private static void budget(Path root,long next)throws Exception {
        if(uniqueBytes(root)+next>BUDGET||Files.getFileStore(root).getUsableSpace()<RESERVE+next)
            throw new IOException("Complete run does not fit the bounded study budget; existing evidence retained");
    }
    private static double median(List<Double> data) {var s=new ArrayList<>(data);Collections.sort(s);int n=s.size();return n%2==1?s.get(n/2):(s.get(n/2-1)+s.get(n/2))/2;}
    private static Map<String,Object> distribution(List<Double> data) {return Map.of("median_s",median(data),"minimum_s",Collections.min(data),"maximum_s",Collections.max(data),"all_s",data);}
    public static void main(String[] args)throws Exception {
        if(args.length!=5&&args.length!=7)throw new IllegalArgumentException("Expected input, output, profile, large warmups/pairs and optional small warmups/pairs");
        if(Runtime.getRuntime().availableProcessors()!=1)throw new IllegalStateException("Verified OS affinity and one active JVM processor required");
        Path input=Path.of(args[0]),output=Path.of(args[1]),profile=Path.of(args[2]);
        int warmups=Integer.parseInt(args[3]),measurements=Integer.parseInt(args[4]);
        int smallWarmups=args.length==7?Integer.parseInt(args[5]):warmups;
        int smallMeasurements=args.length==7?Integer.parseInt(args[6]):measurements;
        if(warmups<1||measurements<1||smallWarmups<1||smallMeasurements<1)throw new IllegalArgumentException("Positive warmup and measurement counts required");
        Files.createDirectory(output);
        json(output.resolve("configuration.json"),Map.of("warmups_per_mode",warmups,"measured_pairs",measurements,"small_warmups_per_mode",smallWarmups,"small_measured_pairs",smallMeasurements,
                "jvm_arguments",ManagementFactory.getRuntimeMXBean().getInputArguments(),"profile",JsonParser.parseString(Files.readString(profile)),
                "classpath",ManagementFactory.getRuntimeMXBean().getClassPath(),"budget_bytes",BUDGET,"reserve_bytes",RESERVE,
                "timed_boundary","fresh import, power flow, initialization, preparation, stepping, full selected recording and export; subsequent hashing/comparison excluded"));
        var rows=new ArrayList<Map<String,Object>>();var canonical=new HashMap<String,Path>();
        for(boolean on:new boolean[]{false,true}) {
            var variant=JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
            variant.add("power_flow_transfer",JSON.toJsonTree(Map.of("prepared_loadflow_enabled",on,"complex_csc_reuse_enabled",on,"record_initial_power_flow",true)));
            json(output.resolve(on?"on.json":"off.json"),variant);
        }
        for(String system:List.of("ieee9","texas7k")) {
            int caseWarmups=system.equals("ieee9")?smallWarmups:warmups,caseMeasurements=system.equals("ieee9")?smallMeasurements:measurements;
            Path group=output.resolve(system);Files.createDirectory(group);
            var measuredOff=new ArrayList<Double>();var measuredOn=new ArrayList<Double>();
            for(int pair=0;pair<caseWarmups+caseMeasurements;pair++)for(int order=0;order<2;order++) {
                boolean on=(pair%2==0)==(order==1),measured=pair>=caseWarmups;
                String mode=on?"on":"off";Path folder=group.resolve((measured?"measured-":"warmup-")+(pair+1)+"-"+mode);
                budget(output,system.equals("texas7k")?132L*1024*1024:1024*1024);
                long compileBefore=ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
                var threads=ManagementFactory.getThreadMXBean();var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
                long processBefore=os.getProcessCpuTime(),threadBefore=threads.getCurrentThreadCpuTime(),start=System.nanoTime();
                Path trace;
                if(system.equals("texas7k")) {
                    Texas7kSingleCpuCaseRun.main(new String[]{input.toString(),folder.toString(),"dynamic-pf-transfer-matched-"+mode,output.resolve(on?"on.json":"off.json").toString()});
                    trace=folder.resolve("all_bus_voltage_and_selected_machines.bin");
                } else {
                    Files.createDirectory(folder);var run=DynamicPowerFlowTransferTest.run(on,on);
                    json(folder.resolve("channels.json"),run.channels());
                    trace=folder.resolve("all_states.bin");
                    try(var stream=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(trace,StandardOpenOption.CREATE_NEW)))) {
                        stream.writeInt(run.frames().size());stream.writeInt(run.channels().size());
                        for(var frame:run.frames()) {stream.writeDouble(frame.time());stream.writeDouble(frame.solverTime());stream.writeInt(frame.kind().ordinal());for(double v:frame.states())stream.writeDouble(v);}
                    }
                    json(folder.resolve("run.json"),Map.of("samples",run.frames().size(),"channels",run.channels().size(),"numeric_refills",run.refills(),"status","completed"));
                }
                double seconds=(System.nanoTime()-start)/1e9;
                var row=new LinkedHashMap<String,Object>();row.put("system",system);row.put("mode",mode);row.put("pair",pair);row.put("measured",measured);row.put("whole_s",seconds);
                row.put("process_cpu_s",(os.getProcessCpuTime()-processBefore)/1e9);row.put("main_thread_cpu_s",(threads.getCurrentThreadCpuTime()-threadBefore)/1e9);
                row.put("compiler_ms",ManagementFactory.getCompilationMXBean().getTotalCompilationTime()-compileBefore);
                String sha=hash(trace);row.put("trace_sha256",sha);row.put("trace",output.relativize(trace).toString());
                Path previous=canonical.putIfAbsent(sha,trace);
                if(previous!=null) {
                    if(!trace.toRealPath().startsWith(output.toRealPath())||!previous.toRealPath().startsWith(output.toRealPath()))throw new IOException("Unexpected trace path");
                    Path link=trace.resolveSibling(trace.getFileName()+".verified-link");Files.createLink(link,previous);
                    Files.move(link,trace,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    if(!Files.isSameFile(trace,previous))throw new IOException("Verified duplicate sharing failed");
                }
                row.put("run",JsonParser.parseString(Files.readString(folder.resolve("run.json"))));rows.add(row);json(folder.resolve("timing.json"),row);
                if(measured)(on?measuredOn:measuredOff).add(seconds);
                Files.writeString(output.resolve("progress.json"),JSON.toJson(rows)+"\n");
                System.out.println("MATCHED_COMPLETE "+system+" "+mode+" measured="+measured+" whole_s="+seconds);
            }
            json(group.resolve("summary.json"),Map.of("off",distribution(measuredOff),"on",distribution(measuredOn),"on_over_off",median(measuredOn)/median(measuredOff)));
        }
        var classes=new TreeMap<String,String>();
        for(String name:List.of("com.interpss.core.algo.impl.solver.PreparedPolarJacobian","com.interpss.core.sparse.solver.klu.BaseJKLUEqnComplexSolver",
                "com.interpss.core.sparse.solver.klu.JKLUSquareMatrixEqnComplexSolver","com.interpss.core.sparse.impl.AbstractSparseEqnComplexImpl")) {
            Class<?> type=Class.forName(name);try(var in=type.getResourceAsStream(type.getSimpleName()+".class")){classes.put(name,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}
        }
        json(output.resolve("summary.json"),Map.of("status","completed","loaded_class_sha256",classes,"unique_bytes",uniqueBytes(output),"runs",rows));
    }
}
