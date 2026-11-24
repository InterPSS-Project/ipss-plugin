package org.interpss.fadapter.psse;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.numeric.datatype.LimitType;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.*;
import com.interpss.dstab.BaseDStabNetwork;

/** Ordered, local import-time device preparation; never executes application scripts or processes. */
public final class PsseCasePreparationProcessor {
    public enum Status { APPLIED, DEFERRED, ERROR }
    /** Original fields are retained, including metadata not represented by engine objects. */
    public record Entry(String source, int line, String command, Status status,
            String message, List<String> fields) {
        public Entry { fields=List.copyOf(fields); }
    }
    private record Plant(double voltage, String remote, double participation) { }
    private final BaseDStabNetwork<?,?> network;
    private final AclfNetworkBuilder builder;
    private final org.interpss.fadapter.builder.AclfNetworkObjectFactory objectFactory;
    private final List<Entry> entries=new ArrayList<>();
    private final Set<Path> visited=new LinkedHashSet<>(), stack=new LinkedHashSet<>();
    private final Map<String,Plant> plants=new HashMap<>();
    private final Set<PsseGnetIdvProcessor.GeneratorKey> gnet=new LinkedHashSet<>();
    private final Set<PsseGnetIdvProcessor.GeneratorKey> removed=new LinkedHashSet<>();
    private final Set<PsseModelRemoveIdvProcessor.Directive> removals=new LinkedHashSet<>();
    private final Set<Path> dynamicFiles=new LinkedHashSet<>();
    private final Set<String> editedGeneratorBuses=new LinkedHashSet<>(),editedLoadBuses=new LinkedHashSet<>();
    private final boolean explicitDyr;

    PsseCasePreparationProcessor(BaseDStabNetwork<?,?> network, boolean explicitDyr) {
        this(network,explicitDyr,null);
    }
    PsseCasePreparationProcessor(BaseDStabNetwork<?,?> network, boolean explicitDyr,
            org.interpss.fadapter.builder.AclfNetworkObjectFactory objectFactory) {
        this.network=network;this.objectFactory=objectFactory;
        builder=new AclfNetworkBuilder(network,objectFactory);this.explicitDyr=explicitDyr;
    }
    public List<Entry> entries() { return List.copyOf(entries); }
    Set<PsseGnetIdvProcessor.GeneratorKey> gnetRemoved() { return Set.copyOf(gnet); }
    Set<PsseGnetIdvProcessor.GeneratorKey> modelRemoved() { return Set.copyOf(removed); }
    Set<PsseModelRemoveIdvProcessor.Directive> removalDirectives() { return Set.copyOf(removals); }
    Set<Path> dynamicFiles() { return Set.copyOf(dynamicFiles); }

    /** Rebuild aggregates and controls once, after all ordered electrical edits. */
    void finalizeElectricalData() throws InterpssException {
        // RAW parsing built the old bus-level controls. IDV edits may create a
        // new PV plant, change its limits, or remove distributed generation.
        // Merely editing child records leaves stale/missing PV/remote-Q limits
        // and can allocate impossible Q to a small DER at the subsequent solve.
        // Use the engine's normal aggregation path before attaching dynamics;
        // an explicit 0/0 Q limit is a fixed boundary, not an absent limit.
        for(String id:editedGeneratorBuses) {
            var target=bus(id);
            if(!new com.interpss.core.aclf.impl.func.bus.AclfBusInitContriGenLoadHelper(target).initContributeGen(false))
                throw new InterpssException("Cannot finalize prepared generator data at "+id);
            var q=target.getQGenLimit();
            if(target.isGenPV() && q!=null && q.getMax()==q.getMin()) {
                // A plant with no reactive range cannot regulate voltage.
                // Establish that physical fixed-Q boundary before saved-state
                // replay/iteration caps select ordinary variable-range PV
                // controls. Keep its PV-limit owner for normal result allocation
                // and auditing; do not disable the other adjustment families.
                target.setGenCode(AclfGenCode.GEN_PQ);target.setGenQ(q.getMax());
                if(target.isPVBusLimit())target.getPVBusLimit().setAdjustStatus(false);
            }
        }
        for(String id:editedLoadBuses)
            if(!new com.interpss.core.aclf.impl.func.bus.AclfBusInitContriGenLoadHelper(bus(id)).initContributeLoad(false))
                throw new InterpssException("Cannot finalize prepared load data at "+id);
        editedGeneratorBuses.clear();editedLoadBuses.clear();
    }

    void apply(Path requested) throws InterpssException {
        apply(requested,false);
    }
    private void apply(Path requested,boolean included) throws InterpssException {
        Path file;
        try { file=requested.toRealPath(); }
        catch(IOException e) { throw new InterpssException("Cannot read local preparation file "+requested+": "+e.getMessage()); }
        if(stack.contains(file))throw new InterpssException("Cyclic IDEV include: "+file);
        if(!included&&visited.contains(file))return;
        if(stack.size()>=32)throw new InterpssException("IDEV nesting exceeds 32 files: "+file);
        stack.add(file);
        try {
            List<String> lines=Files.readAllLines(file);
            boolean plainGnet=file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gnet")
                    && lines.stream().map(PsseCasePreparationProcessor::clean).filter(s->!s.isBlank())
                            .noneMatch(s->s.equalsIgnoreCase("GNET"));
            if(plainGnet)lines=new ArrayList<>(lines);
            if(plainGnet)lines.add(0,"GNET");
            for(int i=0;i<lines.size();i++) {
                String text=clean(lines.get(i));
                if(text.isBlank())continue;
                int line=i+1-(plainGnet?1:0);
                List<String> f;
                try { f=fields(text); }
                catch(IllegalArgumentException e) {
                    add(file,line,"LEXING",Status.ERROR,e.getMessage(),List.of(text));
                    throw new InterpssException(file+":"+line+": "+e.getMessage());
                }
                String command=f.get(0).toUpperCase(Locale.ROOT);
                try {
                    if(command.equals("GNET")) {
                        Set<String> buses=new LinkedHashSet<>();boolean terminated=false;
                        for(i++;i<lines.size();i++) {
                            String row=clean(lines.get(i));if(row.isBlank())continue;
                            List<String> values=fields(row);
                            if(values.size()!=1)throw new IllegalArgumentException("GNET expects one bus per line");
                            int number=integer(values,0,-1);
                            if(number==0){terminated=true;break;}
                            if(number<0)throw new IllegalArgumentException("GNET bus must be positive");
                            buses.add("Bus"+number);
                        }
                        if(!terminated)throw new IllegalArgumentException("Unterminated GNET block");
                        var result=PsseGnetIdvProcessor.applyBusIds(network,buses,file+":"+line,objectFactory);
                        gnet.addAll(result.convertedGeneratorKeys());
                        editedGeneratorBuses.addAll(buses);editedLoadBuses.addAll(buses);
                        add(file,line,command,Status.APPLIED,"Converted "+result.convertedGenerators()+" generators at "+result.convertedBuses()+" buses",f);
                    } else if(command.equals("IDEV")||command.equals("@INPUT")) {
                        String target=argument(f,1);
                        if(target.isBlank()) {
                            if(++i>=lines.size())throw new IllegalArgumentException("Missing IDEV filename");
                            target=unquote(clean(lines.get(i)));
                        }
                        Path includePath=local(file,target);
                        add(file,line,command,Status.APPLIED,"Included "+includePath.getFileName(),List.of(target));
                        apply(includePath,true);
                    } else if(command.equals("DYRE")) {
                        String target=argument(f,1);
                        if(target.isBlank()) {
                            if(++i>=lines.size())throw new IllegalArgumentException("Missing DYRE filename");
                            target=unquote(clean(lines.get(i)));
                        }
                        if(!explicitDyr)dynamicFiles.add(local(file,target).toRealPath());
                        add(file,line,command,Status.DEFERRED,explicitDyr?"Explicit DYR input takes precedence":"DYR attachment scheduled after case preparation",List.of(target));
                    } else if(command.equals("BAT_BUS_CHNG_4")) { changeBus(f);applied(file,line,command,f); }
                    else if(command.equals("BAT_LOAD_CHNG_6")) { changeLoad(f);applied(file,line,command,f); }
                    else if(command.equals("BAT_PLANT_DATA_4")) { changePlant(f);applied(file,line,command,f); }
                    else if(command.equals("BAT_MACHINE_DATA_4")) { changeMachine(f);applied(file,line,command,f); }
                    else if(command.equals("BAT_PLMOD_REMOVE")) {
                        String targetBus=busId(f,1),targetId=required(f,2);var bus=bus(targetBus);
                        var gen=find(bus.getContributeGenList(),targetId);
                        if(gen==null)throw new IllegalArgumentException("Missing generator "+targetBus+"/"+targetId);
                        var key=new PsseGnetIdvProcessor.GeneratorKey(targetBus,gen.getId());
                        var directive=new PsseModelRemoveIdvProcessor.Directive(key,integer(f,3,-1));
                        removals.add(directive);if(directive.modelType()==1)removed.add(key);
                        applied(file,line,command,f);
                    } else if(deferred(command)) {
                        add(file,line,command,Status.DEFERRED,"Application/solve/run operation is not executed by case import",f);
                    } else if(command.startsWith("BAT_")) {
                        throw new IllegalArgumentException("Unsupported import-time IDV command "+command);
                    } else {
                        // Interactive setup menus contain filenames and numeric
                        // answers. Keep every line visible; do not interpret
                        // them as executable shell, native code or solver calls.
                        add(file,line,command,Status.DEFERRED,"Interactive setup data retained; no application execution",f);
                    }
                } catch(Exception e) {
                    add(file,line,command,Status.ERROR,e.getMessage(),f);
                    throw new InterpssException(file+":"+line+" "+command+": "+e.getMessage());
                }
            }
            visited.add(file);
        } catch(IOException e) { throw new InterpssException("Error reading preparation file "+file+": "+e.getMessage()); }
        finally { stack.remove(file); }
    }

    private void changeBus(List<String> f) {
        // Command, IBUS, INODE, IDE/AREA/ZONE/OWNER,
        // BASKV/VM/VA/NMAXV/NMINV/EMAXV/EMINV, NAME.
        checkTail(f,15);node(f,2);
        var b=bus(busId(f,1));
        editedGeneratorBuses.add(b.getId());
        int type=integer(f,3,0);
        if(type!=0) {
            switch(type) {
                case 1 -> b.setGenCode(AclfGenCode.NON_GEN);
                case 2 -> b.setGenCode(AclfGenCode.GEN_PV);
                case 3 -> b.setGenCode(AclfGenCode.SWING);
                case 4 -> b.setStatus(false);
                default -> throw new IllegalArgumentException("Unsupported bus type "+type);
            }
            if(type!=4)b.setStatus(true);
        }
        if(has(f,4))b.setArea(builder.addArea(required(f,4),null,null));
        if(has(f,5)){var z=builder.addZone(required(f,5),null,null);z.setNumber(integer(f,5,1));b.setZone(z);}
        if(has(f,6))b.setOwner(builder.addOwner(required(f,6),null));
        if(has(f,7))b.setBaseVoltage(number(f,7,0)*1000);
        if(has(f,8)||has(f,9))b.setVoltage(number(f,8,b.getVoltageMag()),
                Math.toRadians(number(f,9,Math.toDegrees(b.getVoltageAng()))));
        if(has(f,10)||has(f,11))b.setVLimit(new LimitType(number(f,10,b.getVLimit().getMax()),number(f,11,b.getVLimit().getMin())));
        if(has(f,12)||has(f,13))throw new IllegalArgumentException("Emergency bus voltage limits are not yet mapped");
        if(has(f,14))b.setName(argument(f,14));
    }

    private void changeLoad(List<String> f) {
        // Command, IBUS, ID, STATUS/AREA/ZONE/OWNER/SCALE/INTRPT/DGNFLG,
        // PL/QL/IP/IQ/YP/YQ/PG/QG, LODTYP. Blanks retain existing values.
        checkTail(f,19);
        String id=busId(f,1),loadId=required(f,2);var b=bus(id);
        editedLoadBuses.add(id);
        var load=find(b.getContributeLoadList(),loadId);
        if(load==null)throw new IllegalArgumentException("Missing load "+id+"/"+loadId);
        loadId=load.getId();
        if(has(f,3))load.setStatus(binary(f,3));
        var old=PsseLoadScopeMetadata.find(network,id,loadId);
        int area=integer(f,4,old!=null?old.areaNumber():b.getArea()!=null?(int)b.getArea().getNumber():1);
        int zone=integer(f,5,old!=null?old.zoneNumber():b.getZone()!=null?(int)b.getZone().getNumber():1);
        int owner=integer(f,6,old!=null?old.ownerNumber():b.getOwner()!=null?(int)b.getOwner().getNumber():1);
        if((has(f,4)&&area<=0)||(has(f,5)&&zone<=0)||(has(f,6)&&owner<=0))
            throw new IllegalArgumentException("Explicit load area/zone/owner must be positive");
        PsseLoadScopeMetadata.put(network,id,load,area,zone,owner);
        if(has(f,9))load.setDistGenStatus(binary(f,9));
        double scale=network.getBaseMva();
        load.setLoadCP(pair(f,10,load.getLoadCP(),scale));
        load.setLoadCI(pair(f,12,load.getLoadCI(),scale));
        Complex oldZ=load.getLoadCZ()==null?Complex.ZERO:load.getLoadCZ();
        // Follow the existing RAW/RAWX admittance sign convention, including
        // the inverse transform for an omitted source YQ field.
        load.setLoadCZ(new Complex(number(f,14,oldZ.getReal()*scale)/scale,
                -number(f,15,-oldZ.getImaginary()*scale)/scale));
        load.setDistGenPower(pair(f,16,load.getDistGenPower(),scale));
        updateLoadCode(load);b.setLoadCode(AclfLoadCode.CONST_P);
        // SCALE/INTRPT/LODTYP have no engine field. The complete command fields
        // stay in the report, without falsely changing load electrical values.
    }

    private void changePlant(List<String> f) {
        // Command, IBUS, INODE, IREG/NREG, VS/RMPCT. Keep plant settings
        // available for a machine created by a later command at the same bus.
        checkTail(f,7);node(f,2);node(f,4);
        String id=busId(f,1);var b=bus(id);
        editedGeneratorBuses.add(id);
        Plant previous=plants.get(id);
        if(previous==null&&!b.getContributeGenList().isEmpty()) {
            var first=(AclfGen)b.getContributeGenList().get(0);
            previous=new Plant(first.getDesiredVoltMag(),first.getRemoteVControlBusId()==null?"":first.getRemoteVControlBusId(),
                    b.getPlantMvarControlPFactor()!=null ? b.getPlantMvarControlPFactor()*100
                            : first.getMvarControlPFactor()*100);
        }
        double voltage=number(f,5,previous!=null?previous.voltage():b.getVoltageMag());
        int remote=integer(f,3,previous!=null&&!previous.remote().isEmpty()?Integer.parseInt(previous.remote().substring(3)):0);
        if(remote<0)throw new IllegalArgumentException("Negative regulated bus");
        String remoteId=remote==0?"":"Bus"+remote;if(remote!=0)bus(remoteId);
        double participation=number(f,6,previous!=null?previous.participation():100);
        if(voltage<=0||participation<0)throw new IllegalArgumentException("Plant voltage must be positive and participation nonnegative");
        // Command values are percentages; engine factors are fractions. Blank
        // fields retain the common plant value, not one machine's repeated data.
        b.setPlantMvarControlPFactor(participation*.01);
        Plant plant=new Plant(voltage,remoteId,participation);plants.put(id,plant);
        for(Object item:b.getContributeGenList())applyPlant((AclfGen)item,plant);
    }

    private void changeMachine(List<String> f) {
        // Command, IBUS, ID, STAT/O1/O2/O3/O4/WMOD/BASFLG,
        // PG/QG/QT/QB/PT/PB/MBASE/ZR/ZX/RT/XT/GTAP/F1/F2/F3/F4/WPF, NAME.
        checkTail(f,28);
        String id=busId(f,1),genId=required(f,2);var b=bus(id);
        editedGeneratorBuses.add(id);
        AclfGen gen=find(b.getContributeGenList(),genId);
        Plant plant=plants.get(id);
        if(gen==null) {
            if(plant==null)throw new IllegalArgumentException("New machine requires plant data at "+id);
            gen=builder.addContributeGen(id,genId.toUpperCase(Locale.ROOT),true,0,0,network.getBaseMva(),plant.voltage(),
                    9999/network.getBaseMva(),-9999/network.getBaseMva(),9999/network.getBaseMva(),-9999/network.getBaseMva(),
                    new Complex(0,1),null,1,plant.remote(),plant.participation()*.01,0);
        }
        if(has(f,3))gen.setStatus(binary(f,3));
        double base=network.getBaseMva();
        gen.setGen(pair(f,10,gen.getGen(),base));
        var q=gen.getQGenLimit();var p=gen.getPGenLimit();
        double qmax=number(f,12,q.getMax()*base)/base,qmin=number(f,13,q.getMin()*base)/base;
        double pmax=number(f,14,p.getMax()*base)/base,pmin=number(f,15,p.getMin()*base)/base;
        if(qmax<qmin||pmax<pmin)throw new IllegalArgumentException("Inverted generator limits");
        gen.setQGenLimit(new LimitType(qmax,qmin));gen.setPGenLimit(new LimitType(pmax,pmin));
        double mbase=number(f,16,gen.getMvaBase());if(mbase<=0)throw new IllegalArgumentException("Machine MBASE must be positive");
        gen.setMvaBase(mbase);gen.setSourceZ(pair(f,17,gen.getSourceZ(),1));
        gen.setXfrZ(pair(f,19,gen.getXfrZ(),1));gen.setXfrTap(number(f,21,gen.getXfrTap()));
        if(plant!=null)applyPlant(gen,plant);
        // Ownership fractions, WMOD, BASFLG and WPF are retained in the report;
        // this import step creates the existing engine's electrical device.
    }

    private void applyPlant(AclfGen gen,Plant p) {
        gen.setDesiredVoltMag(p.voltage());gen.setRemoteVControlBusId(p.remote());gen.setMvarControlPFactor(p.participation()*.01);
    }
    private static void updateLoadCode(AclfLoad load) {
        int count=0;AclfLoadCode code=AclfLoadCode.NON_LOAD;
        if(load.getLoadCP().abs()>0){count++;code=AclfLoadCode.CONST_P;}
        if(load.getLoadCI().abs()>0){count++;code=AclfLoadCode.CONST_I;}
        if(load.getLoadCZ().abs()>0){count++;code=AclfLoadCode.CONST_Z;}
        if(load.isDistGenStatus()&&load.getDistGenPower().abs()>0&&count==0)code=AclfLoadCode.CONST_P;
        load.setCode(count>1?AclfLoadCode.ZIP:code);
    }
    private Complex pair(List<String> f,int start,Complex old,double scale) {
        if(old==null)old=Complex.ZERO;
        return new Complex(number(f,start,old.getReal()*scale)/scale,number(f,start+1,old.getImaginary()*scale)/scale);
    }
    private BaseAclfBus<?,?> bus(String id) {
        var bus=(BaseAclfBus<?,?>)network.getBus(id);
        if(bus==null)throw new IllegalArgumentException("Missing bus "+id);return bus;
    }
    private static <T extends com.interpss.core.net.NameTag> T find(List<? extends T> devices,String id) {
        // Native commands resolve identifiers without case. Retain actual
        // imported spelling and reject ambiguous pre-existing case variants.
        T found=null;
        for(T device:devices) {
            if(device.getId().equals(id))return device;
            if(device.getId().equalsIgnoreCase(id)) {
                if(found!=null)throw new IllegalArgumentException("Ambiguous device identifier "+id);
                found=device;
            }
        }
        return found;
    }
    private void applied(Path file,int line,String command,List<String> f) {
        String message=command.equals("BAT_MACHINE_DATA_4")?"Electrical machine/plant data applied; ownership and source-only flags retained in fields":
                command.equals("BAT_LOAD_CHNG_6")?"Electrical load and scope applied; scaling/interruptible/type metadata retained in fields":"Import-time data applied";
        add(file,line,command,Status.APPLIED,message,f);
    }
    private void add(Path file,int line,String cmd,Status status,String message,List<String> fields) {
        entries.add(new Entry(file.toString(),line,cmd,status,message==null?"":message,fields));
    }
    private static boolean deferred(String cmd) {
        return Set.of("MENU","CASE","SAVE","SNAP","CONG","CONL","ORDR","FACT","TYSL","ALTR","RTRN","COMPILE","@END",
                "BAT_LINES_PER_PAGE_ONE_DEVICE","BAT_PROGRESS_OUTPUT","BAT_BUS_SIZE_LEVEL","BAT_NEW_DIMENSION","BAT_RSOL","BAT_FNSL").contains(cmd);
    }
    private static Path local(Path parent,String value) {
        if(value.isBlank()||value.contains("://")||value.startsWith("\\\\"))throw new IllegalArgumentException("Expected a local preparation path");
        Path path=Path.of(value);return (path.isAbsolute()?path:parent.getParent().resolve(path)).normalize();
    }
    private static String busId(List<String> f,int index) { int n=integer(f,index,-1);if(n<=0)throw new IllegalArgumentException("Bus must be positive");return "Bus"+n; }
    private static void node(List<String> f,int index) { if(integer(f,index,0)!=0)throw new IllegalArgumentException("Node-breaker section changes are not supported"); }
    private static boolean binary(List<String> f,int index) { int n=integer(f,index,-1);if(n!=0&&n!=1)throw new IllegalArgumentException("Expected status 0 or 1");return n==1; }
    private static boolean has(List<String> f,int index) { return index<f.size()&&!f.get(index).isBlank(); }
    private static String argument(List<String> f,int index) { return index<f.size()?f.get(index):""; }
    private static String required(List<String> f,int index) { String s=argument(f,index);if(s.isBlank())throw new IllegalArgumentException("Missing field "+index);return s; }
    private static int integer(List<String> f,int index,int fallback) { return has(f,index)?Integer.parseInt(f.get(index)):fallback; }
    private static double number(List<String> f,int index,double fallback) {
        if(!has(f,index))return fallback;double n=Double.parseDouble(f.get(index).replace('D','E').replace('d','e'));
        if(!Double.isFinite(n))throw new IllegalArgumentException("Nonfinite field "+index);return n;
    }
    private static void checkTail(List<String> f,int max) { for(int i=max;i<f.size();i++)if(has(f,i))throw new IllegalArgumentException("Unexpected field "+i); }
    private static String unquote(String s) { return s.length()>=2&&(s.charAt(0)=='\''||s.charAt(0)=='"')&&s.charAt(s.length()-1)==s.charAt(0)?s.substring(1,s.length()-1):s; }
    static String clean(String line) {
        String s=line.trim();if(s.startsWith("\ufeff"))s=s.substring(1).trim();
        if(s.startsWith("@!")||s.startsWith("!"))return "";
        char quote=0;int end=s.length();
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(quote!=0){if(c==quote){if(i+1<s.length()&&s.charAt(i+1)==quote)i++;else quote=0;}continue;}
            if(c=='\''||c=='"'){quote=c;continue;}
            if(c==';'){end=i;break;}
            if(c=='/'&&i+1<s.length()&&(s.charAt(i+1)=='/'||s.charAt(i+1)=='*')){end=i;break;}
        }
        return s.substring(0,end).trim();
    }
    static List<String> fields(String text) {
        List<String> out=new ArrayList<>();StringBuilder token=new StringBuilder();char quote=0;boolean comma=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quote!=0){if(c==quote){if(i+1<text.length()&&text.charAt(i+1)==quote){token.append(c);i++;}else quote=0;}else token.append(c);continue;}
            if(c=='\''||c=='"'){quote=c;continue;}
            if(c==','){out.add(token.toString().trim());token.setLength(0);comma=true;}
            else token.append(c);
        }
        if(quote!=0)throw new IllegalArgumentException("Unterminated quoted field");
        out.add(token.toString().trim());
        if(comma)return out;
        // Command + quoted filename/identifier form. Preserve spaces inside quotes.
        out.clear();var m=java.util.regex.Pattern.compile("'((?:''|[^'])*)'|\"((?:\"\"|[^\"])*)\"|([^\\s]+)").matcher(text);
        while(m.find())out.add(m.group(1)!=null?m.group(1).replace("''","'"):m.group(2)!=null?m.group(2).replace("\"\"","\""):m.group(3));
        return out;
    }
}
