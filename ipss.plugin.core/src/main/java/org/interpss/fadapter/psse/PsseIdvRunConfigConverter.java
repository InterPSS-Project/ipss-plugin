package org.interpss.fadapter.psse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import com.google.gson.GsonBuilder;
import org.interpss.plugin.dstab.config.DStabRunConfigRec;
import org.interpss.plugin.dstab.config.LoadModelThresholds;

/** Converts ordered local run scripts into settings, event plans and an explicit coverage report.
 * No application, shell, compiled script, saved snapshot or native output writer is executed. */
public final class PsseIdvRunConfigConverter {
    public enum Disposition { CONVERTED, PREPARATION, OUTPUT_ONLY, UNSUPPORTED }
    public record Command(String source,int line,String text,Disposition disposition,String explanation) { }
    public record Event(String kind,double timeSec,String bus,String toBus,String circuit,
            double conductanceMva,double susceptanceMva,Integer status) { }
    public record RunSegment(double endSec,int outputEverySteps) { }
    public record Result(int schemaVersion,DStabRunConfigRec config,List<Event> events,
            List<RunSegment> runSegments,List<Command> commands,List<String> sourceFiles,
            List<String> governorPolicyFiles) {
        public Result {events=List.copyOf(events);runSegments=List.copyOf(runSegments);commands=List.copyOf(commands);
            sourceFiles=List.copyOf(sourceFiles);governorPolicyFiles=List.copyOf(governorPolicyFiles);}
        public boolean fullyConverted() {return commands.stream().noneMatch(c->c.disposition()==Disposition.UNSUPPORTED);}
        public void requireFullyConverted() {
            if(!fullyConverted())throw new IllegalArgumentException("IDV contains unsupported operations; inspect the source-located conversion report");
        }
        public String toJson(){return new GsonBuilder().setPrettyPrinting().create().toJson(this);}
    }
    private final DStabRunConfigRec config=new DStabRunConfigRec();
    private final List<Event> events=new ArrayList<>();
    private final List<RunSegment> segments=new ArrayList<>();
    private final List<Command> commands=new ArrayList<>();
    private final Set<Path> stack=new LinkedHashSet<>(),files=new LinkedHashSet<>(),governorFiles=new LinkedHashSet<>();
    private double time;
    private Path root;
    private static final String NUMBER="([+\\-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[EeDd][+\\-]?[0-9]+)?)";
    private static final Pattern RUN=Pattern.compile("RUN(?: WITH BR)? TO\\s+"+NUMBER+"\\s+SECONDS(?:,.*?PLOT\\s*=\\s*([0-9]+))?.*",Pattern.CASE_INSENSITIVE);
    private static final Pattern FAULT=Pattern.compile("APPLY FAULT BUS ([0-9]+) ADMITTANCE\\s+"+NUMBER+"\\s+"+NUMBER+"\\s+MVA",Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAR=Pattern.compile("CLEAR FAULT BUS ([0-9]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern TRIP=Pattern.compile("TRIP TIE FROM BUS ([0-9]+) TO BUS ([0-9]+) CIRCUIT (\\S+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern DISCONNECT=Pattern.compile("DISCONNECT BUS ([0-9]+)",Pattern.CASE_INSENSITIVE);

    public static Result convert(Path... orderedFiles)throws IOException {
        if(orderedFiles.length==0)throw new IllegalArgumentException("At least one local IDV is required");
        var reader=new PsseIdvRunConfigConverter();
        // Serialize the imported-case default explicitly so replaying this JSON
        // on another network reproduces the same static fallback conversion.
        reader.config.activeStaticLoadModel=com.interpss.dstab.StaticLoadModel.CONST_I;
        reader.config.reactiveStaticLoadModel=com.interpss.dstab.StaticLoadModel.CONST_Z;
        reader.root=orderedFiles[0].toAbsolutePath().normalize().getParent();
        for(Path p:orderedFiles)reader.read(p);
        reader.config.validate();
        return new Result(1,reader.config,reader.events,reader.segments,reader.commands,
                reader.files.stream().map(reader::relative).toList(),reader.governorFiles.stream().map(reader::relative).toList());
    }
    private String relative(Path path){return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\','/');}
    private void record(Path file,int line,String text,Disposition disposition,String why){commands.add(new Command(relative(file),line,text,disposition,why));}
    private static double number(String value) {
        double result=Double.parseDouble(value.replace('D','E').replace('d','e'));
        if(!Double.isFinite(result))throw new IllegalArgumentException("Nonfinite IDV number");return result;
    }
    private static int integer(String value) {
        double v=number(value);if(v!=Math.rint(v)||v<Integer.MIN_VALUE||v>Integer.MAX_VALUE)throw new IllegalArgumentException("Expected integer");return (int)v;
    }
    private static boolean binary(String v){int n=integer(v);if(n!=0&&n!=1)throw new IllegalArgumentException("Expected 0 or 1");return n==1;}
    private static void count(List<String> f,int n){if(f.size()!=n)throw new IllegalArgumentException("Wrong command field count: "+f);}
    private static String clean(String input) {
        // A single slash starts a source comment outside quotes. Quoted local
        // filenames retain slash separators and doubled quote escapes.
        char quote=0;for(int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if(quote!=0){if(c==quote){if(i+1<input.length()&&input.charAt(i+1)==quote)i++;else quote=0;}continue;}
            if(c=='\''||c=='"')quote=c;else if(c=='/')return PsseCasePreparationProcessor.clean(input.substring(0,i));
        }
        return PsseCasePreparationProcessor.clean(input);
    }
    private static Path local(Path file,String name)throws IOException {
        if(name.isBlank()||name.contains("://")||name.startsWith("\\\\"))throw new IllegalArgumentException("Expected local input path");
        Path value=Path.of(name);return (value.isAbsolute()?value:file.getParent().resolve(value)).toRealPath();
    }
    private int next(List<String> lines,int index){for(int i=index+1;i<lines.size();i++)if(!clean(lines.get(i)).isBlank())return i;throw new IllegalArgumentException("Missing interactive response");}
    private void runTo(double end,int every) {
        if(end<time||every<1)throw new IllegalArgumentException("Nonmonotone run time or invalid output cadence");
        time=end;config.totalTimeSec=end;segments.add(new RunSegment(end,every));
    }
    private void read(Path requested)throws IOException {
        Path file=requested.toRealPath();if(stack.contains(file)||stack.size()>=32)throw new IllegalArgumentException("Cyclic or excessive IDV include: "+file);
        stack.add(file);files.add(file);
        try {
            List<String> lines=Files.readAllLines(file);
            for(int i=0;i<lines.size();i++) {
                String text=clean(lines.get(i));if(text.isBlank())continue;
                int line=i+1;var f=PsseCasePreparationProcessor.fields(text);String command=f.get(0).toUpperCase(Locale.ROOT);Matcher m;
                try {
                    if(f.stream().allMatch(String::isBlank))record(file,line,text,Disposition.PREPARATION,"Blank workspace dialogue defaults retained");
                    else if((m=RUN.matcher(text)).matches()){runTo(number(m.group(1)),m.group(2)==null?1:integer(m.group(2)));record(file,line,text,Disposition.CONVERTED,"Run horizon and recording cadence");}
                    else if(command.equals("BAT_RUN")){if(f.size()!=6)throw new IllegalArgumentException("Expected five BAT_RUN arguments");runTo(number(f.get(2)),integer(f.get(4)));record(file,line,text,Disposition.CONVERTED,"Run horizon and recording cadence");}
                    else if((m=FAULT.matcher(text)).matches()){events.add(new Event("fault",time,"Bus"+m.group(1),"","",number(m.group(2)),number(m.group(3)),null));record(file,line,text,Disposition.CONVERTED,"Three-phase shunt fault; admittance retained in source MVA");}
                    else if((m=CLEAR.matcher(text)).matches()){events.add(new Event("clear",time,"Bus"+m.group(1),"","",0,0,null));record(file,line,text,Disposition.CONVERTED,"Fault clearance at current run endpoint");}
                    else if((m=TRIP.matcher(text)).matches()){events.add(new Event("trip",time,"Bus"+m.group(1),"Bus"+m.group(2),m.group(3),0,0,null));record(file,line,text,Disposition.CONVERTED,"Permanent branch trip");}
                    else if((m=DISCONNECT.matcher(text)).matches()){events.add(new Event("disconnect",time,"Bus"+m.group(1),"","",0,0,null));record(file,line,text,Disposition.CONVERTED,"Disconnect bus and its connected equipment");}
                    else if(command.equals("BAT_SET_ZSORCE_RECONCILE_FLAG")){count(f,2);config.sourceImpedanceReconciliationEnabled=binary(f.get(1));record(file,line,text,Disposition.CONVERTED,"Explicit source-impedance initialization policy");}
                    else if(command.equals("BAT_SET_LOAD_MODEL_THRESH")){count(f,4);config.loadModelThresholds=new LoadModelThresholds(number(f.get(1)),number(f.get(2)),number(f.get(3)));record(file,line,text,Disposition.CONVERTED,"Model-specific initialization screening, not global model removal");}
                    else if(command.equals("BAT_SET_NETFRQ")){count(f,2);config.networkFrequencyDependenceEnabled=binary(f.get(1));record(file,line,text,Disposition.CONVERTED,"Select frequency corrections for passive branches, shunts, missing-machine equivalents, and GENQEC/GENQECU stators");}
                    else if(command.equals("BAT_SET_RELANG")){count(f,4);config.relativeAnglesEnabled=binary(f.get(1));config.angleReferenceBus=integer(f.get(2));config.angleReferenceMachineId=f.get(3);record(file,line,text,Disposition.CONVERTED,"Output angle reference retained; does not alter physical rotor states");}
                    else if(command.equals("BAT_STRT_2")){count(f,4);binary(f.get(1));config.allowGeneratorsWithoutMachine=binary(f.get(2));record(file,line,text,Disposition.CONVERTED,"Initialization netting option; source monitor/output filename retained as metadata");}
                    else if(command.equals("BAT_DYNAMICS_SOLUTION_PARAM_2")){
                        count(f,17);if(!f.get(1).isBlank())config.maxNetworkIterations=integer(f.get(1));
                        if(!f.get(9).isBlank())config.sourceNetworkAccelerationFactor=number(f.get(9));
                        if(!f.get(10).isBlank())config.networkTolerance=number(f.get(10));
                        if(!f.get(11).isBlank())config.stepSec=number(f.get(11));
                        boolean extra=false;for(int j=2;j<=8;j++)extra|=!f.get(j).isBlank();for(int j=12;j<=16;j++)extra|=!f.get(j).isBlank();
                        record(file,line,text,extra?Disposition.UNSUPPORTED:Disposition.CONVERTED,extra?"Native workspace allocation or additional simulation-mode controls require explicit support":"Network limits, tolerance, source acceleration and physical step");
                    }
                    else if(command.equals("GNET")){
                        int last=i;boolean terminated=false;
                        for(int j=i+1;j<lines.size();j++) {
                            String row=clean(lines.get(j));if(row.isBlank())continue;
                            int bus=integer(row);if(bus<0)throw new IllegalArgumentException("Negative GNET bus");
                            if(bus==0){last=j;terminated=true;break;}
                        }
                        if(!terminated)throw new IllegalArgumentException("Unterminated GNET block");
                        record(file,line,String.join(" | ",lines.subList(i,last+1)),Disposition.PREPARATION,"Ordered generator-netting block belongs to electrical preparation");i=last;
                    }
                    else if(command.equals("DYRE")){
                        if(f.size()==1) {
                            int dyr=next(lines,i),conec=next(lines,dyr),conet=next(lines,conec),allocation=next(lines,conet);
                            var slots=PsseCasePreparationProcessor.fields(clean(lines.get(allocation)));
                            if(!slots.stream().allMatch(String::isBlank))throw new IllegalArgumentException("Unsupported DYRE allocation dialogue");
                            record(file,line,String.join(" | ",lines.subList(i,allocation+1)),Disposition.PREPARATION,"Dynamic input and generated connection-file references; use explicit combined-loader inputs");i=allocation;
                        }else record(file,line,text,Disposition.PREPARATION,"Dynamic input reference belongs to the combined case loader");
                    }
                    else if(command.equals("ALTR")){
                        // The documented parameter-change dialogue selects menu 6,
                        // frequency/acceleration, then the initial time/step row.
                        int menu=next(lines,i),yes=next(lines,menu),freq=next(lines,yes),yes2=next(lines,freq),dt=next(lines,yes2);
                        if(!clean(lines.get(menu)).equals("6")||!clean(lines.get(yes)).equalsIgnoreCase("Y")||!clean(lines.get(yes2)).equalsIgnoreCase("Y"))throw new IllegalArgumentException("Unsupported ALTR dialogue; use explicit batch settings");
                        var a=PsseCasePreparationProcessor.fields(clean(lines.get(freq)));var b=PsseCasePreparationProcessor.fields(clean(lines.get(dt)));count(a,2);count(b,3);
                        config.frequencyHz=number(a.get(0));config.sourceNetworkAccelerationFactor=number(a.get(1));config.stepSec=number(b.get(2));
                        if(!b.get(0).isBlank()||!b.get(1).isBlank())throw new IllegalArgumentException("ALTR initial time/channel cadence requires explicit support");
                        record(file,line,String.join(" | ",lines.subList(i,dt+1)),Disposition.CONVERTED,"Frequency, source acceleration and physical step from parameter dialogue");i=dt;
                        // Consume only the exact trailing no-change answers. Other
                        // commands remain visible instead of being swallowed by a menu parser.
                        for(String response:new String[]{"N","N","0","0"}){int n=next(lines,i);if(!clean(lines.get(n)).equalsIgnoreCase(response))break;i=n;}
                    }
                    else if(command.equals("IDEV")||command.equals("@INPUT")){
                        String name=f.size()>1?f.get(1):"";if(name.isBlank()){i=next(lines,i);name=PsseCasePreparationProcessor.fields(clean(lines.get(i))).get(0);}
                        record(file,line,text,Disposition.CONVERTED,"Ordered local include");read(local(file,name));
                    }
                    else if(command.equals("EXEC")){
                        String program=f.size()>1?f.get(1):"";
                        if(program.equalsIgnoreCase("Gov_blk_R345.IRF")){
                            i=next(lines,i);String name=PsseCasePreparationProcessor.fields(clean(lines.get(i))).get(0);Path policy=local(file,name);
                            if(!PsseGovernorBsgProcessor.isGovernorFile(policy))throw new IllegalArgumentException("Expected governor policy BSG");
                            governorFiles.add(policy);i=next(lines,i);binary(clean(lines.get(i)));
                            record(file,line,text,Disposition.CONVERTED,"Governor BSG policy reference; use registered Java policy replay, never execute IRF");
                        }else record(file,line,text,Disposition.UNSUPPORTED,"Compiled program requires a qualified Java implementation");
                    }
                    else if(command.equals("BAT_LOAD_CHNG_5")){
                        if(f.size()<4)throw new IllegalArgumentException("Missing load event fields");for(int j=4;j<f.size();j++)if(!f.get(j).isBlank())throw new IllegalArgumentException("Only status-only load events supported");
                        events.add(new Event("loadStatus",time,"Bus"+integer(f.get(1)),"",f.get(2),0,0,binary(f.get(3))?1:0));record(file,line,text,Disposition.CONVERTED,"Source load status event");
                    }
                    else if(Set.of("BAT_BUS_CHNG_4","BAT_LOAD_CHNG_6","BAT_PLANT_DATA_4","BAT_MACHINE_DATA_4","BAT_PLMOD_REMOVE","GNET","DYRE","CONG","CONL","ORDR","FACT","TYSL","BAT_RSOL","BAT_FNSL").contains(command)){
                        record(file,line,text,Disposition.PREPARATION,"Electrical preparation belongs to the combined case loader, before dynamic initialization");
                    }
                    else if(Set.of("PDEV","ODEV","PSAS").contains(command)){
                        record(file,line,text,Disposition.OUTPUT_ONLY,"Native report routing/menu; no numerical setting");i=next(lines,i);String answer=clean(lines.get(i));
                        if(answer.equals("2")&&(command.equals("PDEV")||command.equals("ODEV")))i=next(lines,i);
                    }
                    else if(Set.of("PASSTHRU","FIN","TIMER","END","@END","MENU","BAT_LINES_PER_PAGE_ONE_DEVICE","BAT_PROGRESS_OUTPUT","BAT_DELETE_ALL_PLOT_CHANNELS","BAT_SET_CHNFIL_TYPE","BAT_CHSB","BAT_BSYSDEF","BAT_BSYS").contains(command))
                        record(file,line,text,Disposition.OUTPUT_ONLY,"Wrapper, reporting, channel selection or output-only subsystem declaration retained");
                    else if(command.equals("CASE")||command.equals("SNAP")||command.equals("SAVE")){
                        if(f.size()==1)i=next(lines,i);record(file,line,text,Disposition.UNSUPPORTED,"Binary case/snapshot operation cannot be reconstructed from run settings; caller must select matching import inputs");
                    }
                    else record(file,line,text,Disposition.UNSUPPORTED,"No qualified translation; never silently ignored");
                }catch(RuntimeException e){throw new IllegalArgumentException(relative(file)+":"+line+": "+e.getMessage(),e);}
            }
        }finally{stack.remove(file);}
    }
    /** Standalone conversion entrypoint: ordered IDVs followed by two output paths. */
    public static void main(String[] args)throws Exception {
        List<Path> inputs=new ArrayList<>();Path configOut=null,reportOut=null;
        for(int i=0;i<args.length;i++)switch(args[i]) {
            case "--idv" -> inputs.add(Path.of(args[++i]));
            case "--config-out" -> configOut=Path.of(args[++i]);
            case "--report-out" -> reportOut=Path.of(args[++i]);
            default -> throw new IllegalArgumentException("Unknown option: "+args[i]);
        }
        if(configOut==null||reportOut==null)throw new IllegalArgumentException("Use --idv FILE [--idv FILE] --config-out FILE --report-out FILE");
        Result result=convert(inputs.toArray(Path[]::new));
        Files.writeString(configOut,result.config().toJson(),java.nio.file.StandardOpenOption.CREATE_NEW);
        Files.writeString(reportOut,result.toJson(),java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
