package org.interpss.core.dstab.benchmark;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.google.gson.*;

/** Complete warmed benchmark with all-channel integrity and explicit serial execution. */
public final class Texas7kSingleCpuBenchmark {
    private static final String EXPECTED_TRACE="858c877c21e8c75917ac57444479272952a1e4817fac707f62ff377ea5f8d3a8";
    private static final long MAXIMUM_STUDY_BYTES=512L*1024*1024;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    private static String digest(InputStream input)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(input){byte[] buffer=new byte[1024*1024];int count;while((count=input.read(buffer))>=0)digest.update(buffer,0,count);}
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void coverage(JsonObject run) {
        if(!run.get("status").getAsString().equals("completed")||run.get("samples").getAsInt()!=2403
                ||run.get("bus_count").getAsInt()!=6717||run.get("network_iterations").getAsInt()!=26803
                ||run.get("network_iteration_limit").getAsInt()!=50||run.get("accepted_endpoint_reuses").getAsInt()!=2397
                ||run.get("typed_ieeest_stages").getAsInt()!=203||run.getAsJsonObject("bus_frequency_report").get("measuredBuses").getAsInt()!=704
                ||run.get("folded_static_rows").getAsInt()!=4548||run.get("passive_bus_update_skips").getAsLong()!=28862400L
                ||!run.get("prepared_recording").getAsBoolean()||run.get("requested_nonmachine_device_state_outputs").getAsInt()!=0
                ||run.get("bsg_entries").getAsInt()!=120||run.get("owned_machine_advances").getAsLong()!=0
                ||run.get("parallel_packed_rows").getAsBoolean())throw new AssertionError("Runtime/recording coverage changed");
        var inventory=run.getAsJsonObject("active_device_inventory");
        for(var expected:Map.of("generators",630,"synchronous_machines",463,"converter_bundles",167,"exciters",463,"governors",463,"stabilizers",203,"inactive_controllers",0).entrySet())
            if(inventory.get(expected.getKey()).getAsInt()!=expected.getValue())throw new AssertionError("Active inventory changed: "+expected.getKey());
    }
    private static void write(Path path,Object value)throws Exception {Files.writeString(path,JSON.toJson(value)+"\n",StandardOpenOption.CREATE_NEW);}
    private static double median(List<Double> values){var sorted=new ArrayList<>(values);Collections.sort(sorted);int n=sorted.size();return n%2==1?sorted.get(n/2):(sorted.get(n/2-1)+sorted.get(n/2))/2.;}

    /** Arguments: input directory, new output directory, source reference, warmups, measurements, configuration. */
    public static void main(String[] args)throws Exception {
        if(args.length!=6)throw new IllegalArgumentException("Expected input, output, source reference, warmup count, measurement count and configuration");
        if(Runtime.getRuntime().availableProcessors()!=1)throw new IllegalStateException("Use one active JVM processor and the affinity-verifying launcher");
        int warmups=Integer.parseInt(args[3]),measurements=Integer.parseInt(args[4]);
        if(warmups<1||measurements<1)throw new IllegalArgumentException("Warmup and measurement counts must be positive");
        var flags=JsonParser.parseString(Files.readString(Path.of(args[5]))).getAsJsonObject().getAsJsonObject("optimization_properties");
        Path output=Path.of(args[1]);Files.createDirectory(output);
        write(output.resolve("configuration.json"),Map.of("optimization_properties",flags,"jvm_arguments",ManagementFactory.getRuntimeMXBean().getInputArguments(),
            "source_reference",args[2],"warmups",warmups,"measurements",measurements,"expected_trace_sha256",EXPECTED_TRACE));
        var results=new ArrayList<Map<String,Object>>();var totals=new ArrayList<Double>();var outerTimes=new ArrayList<Double>();Path canonical=null;
        for(int index=0;index<warmups+measurements;index++) {
            boolean measured=index>=warmups;String phase=(measured?"measured-":"warmup-")+(measured?index-warmups+1:index+1);
            Path folder=output.resolve(phase);var compiler=ManagementFactory.getCompilationMXBean();
            var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();var threads=ManagementFactory.getThreadMXBean();
            long compilationBefore=compiler.getTotalCompilationTime(),processBefore=os.getProcessCpuTime(),threadBefore=threads.getCurrentThreadCpuTime(),wallBefore=System.nanoTime();
            Texas7kSingleCpuCaseRun.main(new String[]{args[0],folder.toString(),args[2],args[5]});
            double outer=(System.nanoTime()-wallBefore)/1e9;
            var cpu=Map.of("outer_wall_seconds",outer,"process_cpu_seconds",(os.getProcessCpuTime()-processBefore)/1e9,"main_thread_cpu_seconds",(threads.getCurrentThreadCpuTime()-threadBefore)/1e9);
            write(folder.resolve("cpu-times.json"),cpu);write(folder.resolve("compilation-time.json"),Map.of("before_ms",compilationBefore,"after_ms",compiler.getTotalCompilationTime()));
            var run=JsonParser.parseString(Files.readString(folder.resolve("run.json"))).getAsJsonObject();coverage(run);
            var channels=JsonParser.parseString(Files.readString(folder.resolve("channels.json"))).getAsJsonObject();
            if(channels.getAsJsonArray("channels").size()!=6732)throw new AssertionError("Recorded channel count changed");
            Path trace=folder.resolve("all_bus_voltage_and_selected_machines.bin");String hash=digest(Files.newInputStream(trace));
            if(!hash.equals(EXPECTED_TRACE))throw new AssertionError("Complete trajectory changed: "+hash);
            boolean shared=canonical!=null;
            if(canonical==null)canonical=trace;
            else {Path link=trace.resolveSibling(trace.getFileName()+".verified-link");Files.createLink(link,canonical);Files.move(link,trace,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);if(!Files.isSameFile(trace,canonical))throw new AssertionError("Trace storage was not shared");}
            var row=new LinkedHashMap<String,Object>();row.put("phase",phase);row.put("measured",measured);row.put("trace_sha256",hash);row.put("shared_storage",shared);row.putAll(cpu);
            row.put("whole_seconds",run.get("total_wall_seconds_before_summary").getAsDouble());row.put("phase_times",run.get("phase_times"));results.add(row);
            if(measured){totals.add((Double)row.get("whole_seconds"));outerTimes.add(outer);}
            long bytes=0;var unique=new ArrayList<Path>();
            try(var paths=Files.walk(output)){for(Path path:paths.filter(Files::isRegularFile).toList()){
                boolean duplicate=false;for(Path previous:unique)if(Files.isSameFile(path,previous)){duplicate=true;break;}
                if(!duplicate){unique.add(path);bytes+=Files.size(path);}
            }}
            if(bytes>MAXIMUM_STUDY_BYTES)throw new IllegalStateException("Study storage budget exceeded");
            Files.writeString(output.resolve("progress.json"),JSON.toJson(results)+"\n");System.out.println("BENCHMARK_PHASE_COMPLETE "+phase+" whole_s="+row.get("whole_seconds")+" outer_s="+outer+" unique_bytes="+bytes);
        }
        var implementations=new TreeMap<String,String>();
        for(String name:List.of("com.interpss.dstab.impl.BaseDStabNetworkImpl","com.interpss.dstab.impl.BaseDStabBusImpl","com.interpss.dstab.algo.defaultImpl.DStabSolverImpl",
                "org.interpss.fadapter.psse.PsseDStabSolver","org.interpss.dstab.renewable.Regca1Model","org.interpss.dstab.control.pss.psse.ieeest.IeeestDirectStage")){
            var type=Class.forName(name);implementations.put(name,digest(type.getResourceAsStream(type.getSimpleName()+".class")));
        }
        write(output.resolve("summary.json"),Map.of("status","completed","warmups",warmups,"measurements",measurements,"measured_median_whole_seconds",median(totals),
            "measured_median_outer_seconds",median(outerTimes),"measured_outer_range_seconds",List.of(Collections.min(outerTimes),Collections.max(outerTimes)),"loaded_class_sha256",implementations,"runs",results));
        System.out.println("BENCHMARK_COMPLETE median_outer_s="+median(outerTimes));
    }
}
