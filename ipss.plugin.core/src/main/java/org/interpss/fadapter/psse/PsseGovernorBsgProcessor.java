package org.interpss.fadapter.psse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.controller.gov.ggov1.PsseGgov1Governor;
import com.interpss.dstab.controller.gov.hygov.PsseHygovGovernor;
import com.interpss.dstab.controller.gov.tgov1.PsseTGov1SteamTurGovernor;
import com.interpss.dstab.controller.gov.ieeehydro1981type3.Ieee1981Type3HydroGovernor;
import com.interpss.dstab.controller.gov.weccwshygp.WshygpGovernor;
import com.interpss.dstab.controller.gov.weccwsieg1.Wsieg1Governor;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;

/** Initial governor travel policy from comma-separated, single-quoted BSG records.
 * This is distinct from renewable BASEGEN response policy. Reading/registering
 * never changes a model. Replay belongs after final numerical initialization. */
public final class PsseGovernorBsgProcessor {
    public enum Status { REGISTERED, NORMAL, APPLIED, UNCHANGED, MISSING_TARGET, INACTIVE_TARGET,
        UNSUPPORTED_MODEL, DUPLICATE_OWNER, ERROR }
    /** One source-located report convention serves registration and replay; no state snapshot. */
    public record Entry(String source, int line, GeneratorKey generator, String resolvedGeneratorId, int code,
            List<String> fields, Status status, String model, Double position,
            Double oldMaximum, Double newMaximum, String message) {
        public Entry { fields=List.copyOf(fields); }
        // The policy key is source evidence. Resolution is separate metadata and
        // is recomputed on replay so a prior result cannot leak into a new network.
        Entry resolvedTo(String id) {
            return new Entry(source,line,generator,id,code,fields,status,model,position,oldMaximum,newMaximum,message);
        }
        Entry outcome(Status value,String model,Double position,Double oldMax,Double newMax,String message) {
            return new Entry(source,line,generator,resolvedGeneratorId,code,fields,value,model,position,oldMax,newMax,message);
        }
    }
    private PsseGovernorBsgProcessor() { }

    /** Dispatch by content, never by the shared .bsg suffix alone. Empty files are ambiguous. */
    public static boolean isGovernorFile(Path file) throws IOException {
        for(String line:Files.readAllLines(file)) {
            String text=line.strip();
            if(text.isEmpty()||text.startsWith("//"))continue;
            return text.matches("^\\d+\\s*,.*");
        }
        throw new IOException("Empty BSG policy: "+file);
    }

    public static List<Entry> read(Path file) throws IOException {
        List<Entry> entries=new ArrayList<>();Map<GeneratorKey,Integer> seen=new HashMap<>();
        int number=0;
        for(String raw:Files.readAllLines(file)) {
            number++;String text=raw.strip();
            if(text.isEmpty()||text.startsWith("//"))continue;
            try {
                List<String> f=fields(text);
                if(f.size()!=5)throw new IllegalArgumentException("Expected bus, single-quoted name, kV, single-quoted ID, code");
                long bus=Long.parseLong(f.get(0));double kv=Double.parseDouble(f.get(2));
                String id=f.get(3).trim();int code=Integer.parseInt(f.get(4));
                if(bus<=0||!Double.isFinite(kv)||kv<=0||id.isEmpty()||code<0||code>2)
                    throw new IllegalArgumentException("Invalid bus/kV/ID or governor code (0, 1, 2)");
                var key=new GeneratorKey("Bus"+bus,id);
                Integer previous=seen.putIfAbsent(key,code);
                if(previous!=null&&previous!=code)throw new IllegalArgumentException("Conflicting governor codes for "+key);
                entries.add(new Entry(file.toString(),number,key,null,code,f,Status.REGISTERED,"",null,null,null,
                        "Awaiting final numerical initialization"));
            } catch(IllegalArgumentException failure) {
                throw new IOException(file+":"+number+": "+failure.getMessage(),failure);
            }
        }
        if(entries.isEmpty())throw new IOException("Empty governor policy: "+file);
        return List.copyOf(entries);
    }

    // Small quote-aware scanner: embedded commas and doubled single quotes belong
    // to the name/ID. Reject malformed rows rather than partially interpreting policy.
    private static List<String> fields(String text) {
        List<String> out=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false,usedQuote=false;
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if(ch=='\'') {
                if(quoted&&i+1<text.length()&&text.charAt(i+1)=='\''){field.append(ch);i++;}
                else if(quoted){quoted=false;closed=true;}
                else {if(!field.toString().isBlank()||closed)throw new IllegalArgumentException("Malformed single quote");quoted=true;usedQuote=true;}
            } else if(ch==','&&!quoted){
                if(usedQuote!=(out.size()==1||out.size()==3))throw new IllegalArgumentException("Name and ID must use single quotes; numeric fields must be unquoted");
                out.add(field.toString().trim());field.setLength(0);closed=false;usedQuote=false;
            }
            else {if(closed&&!Character.isWhitespace(ch))throw new IllegalArgumentException("Unexpected text after quote");field.append(ch);}
        }
        if(quoted)throw new IllegalArgumentException("Unterminated single quote");
        if(usedQuote)throw new IllegalArgumentException("Code must be unquoted");
        out.add(field.toString().trim());return out;
    }

    /** Apply once per initialized owner, in file order. Codes 1 and 2 both cap
     * upward travel in the tested source routine; code 0 never changes a limit.
     * Missing/inactive/unsupported hosts remain explicit non-applied outcomes. */
    public static List<Entry> apply(BaseDStabNetwork<?,?> network,List<Entry> policy) throws InterpssException {
        if(!network.isDStabNetInitialized())throw new InterpssException("Initialize numerical models before governor policy replay");
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        List<Entry> report=new ArrayList<>();
        for(Entry registered:policy) {
            Entry row=registered.resolvedTo(null);
            if(row.code()<0||row.code()>2)throw new InterpssException("Invalid registered governor code");
            if(row.code()==0){report.add(row.outcome(Status.NORMAL,"",null,null,null,"Code 0: normal governor operation, no change"));continue;}
            var bus=network.getDStabBus(row.generator().busId());
            var gen=bus==null?null:bus.getContributeGen(row.generator().generatorId());
            // Exact source IDs take precedence, including when other case variants
            // coexist. Only a missing exact match may use native-compatible lookup.
            // Count inactive/no-host candidates too: selecting only an active match
            // could silently apply policy to the wrong physical generator.
            if(gen==null&&bus!=null) {
                String requestedId=row.generator().generatorId();
                var matches=bus.getContributeGenList().stream()
                        .filter(candidate->requestedId.equalsIgnoreCase(candidate.getId())).toList();
                if(matches.size()>1) {
                    var ids=matches.stream().map(candidate->candidate.getId()).sorted().toList();
                    report.add(row.outcome(Status.ERROR,"",null,null,null,
                            "Ambiguous case-insensitive generator ID '"+requestedId+"'; candidates="+ids));
                    continue;
                }
                if(matches.size()==1)gen=matches.getFirst();
            }
            if(gen==null){report.add(row.outcome(Status.MISSING_TARGET,"",null,null,null,"Generator target absent"));continue;}
            row=row.resolvedTo(gen.getId());
            if(!bus.isActive()||!gen.isActive()){report.add(row.outcome(Status.INACTIVE_TARGET,"",null,null,null,"Bus/generator inactive"));continue;}
            var device=((com.interpss.dstab.DStabGen)gen).getDynamicGenDevice();
            if(!(device instanceof Machine machine)||machine.getGovernor()==null){
                report.add(row.outcome(Status.UNSUPPORTED_MODEL,"",null,null,null,"No synchronous machine/governor host"));continue;}
            var governor=machine.getGovernor();var shared=Wsieg1Governor.sharedOwner(governor);
            var owner=shared==null?governor:shared;
            String model=shared==null?governor.getName():"WSIEG1";
            if(!machine.isActive()||!governor.isActive()||!owner.isActive()
                    ||owner.getMachine()==null||!owner.getMachine().isActive()
                    ||!owner.getMachine().getDStabBus().isActive()||!owner.getMachine().getParentGen().isActive()) {
                report.add(row.outcome(Status.INACTIVE_TARGET,model,null,null,null,"Machine/governor owner inactive"));continue;
            }
            Double position=null,old=null;
            try {
                java.util.function.DoubleConsumer setter;
                if(owner.getClass()==PsseTGov1SteamTurGovernor.class&&"TGOV1".equals(model)) {
                    var g=(PsseTGov1SteamTurGovernor)owner;position=g.getValvePosition();old=g.vmax;setter=g::applyInitialUpperTravelLimit;
                } else if(owner.getClass()==PsseGgov1Governor.class&&"GGOV1".equals(model)) {
                    var g=(PsseGgov1Governor)owner;position=g.getValveStroke();old=g.getEffectiveVmax();setter=g::applyInitialUpperTravelLimit;
                } else if(owner.getClass()==PsseHygovGovernor.class&&"HYGOV".equals(model)) {
                    var g=(PsseHygovGovernor)owner;position=g.getGatePosition();old=g.getEffectiveGmax();setter=g::applyInitialUpperTravelLimit;
                } else if(owner.getClass()==Ieee1981Type3HydroGovernor.class&&"IEEEG3".equals(model)) {
                    var g=(Ieee1981Type3HydroGovernor)owner;position=g.getGatePosition();old=g.pmax;setter=g::applyInitialUpperTravelLimit;
                } else if(owner.getClass()==WshygpGovernor.class) {
                    var g=(WshygpGovernor)owner;position=g.getGatePosition();old=g.getEffectiveMaximum();setter=g::applyInitialUpperTravelLimit;
                } else if(shared!=null) {
                    position=shared.getValvePosition();old=shared.getEffectiveMaximum();setter=shared::applyInitialUpperTravelLimit;
                } else {report.add(row.outcome(Status.UNSUPPORTED_MODEL,model,null,null,null,"Governor family not qualified for this policy"));continue;}
                if(!seen.add(owner)){report.add(row.outcome(Status.DUPLICATE_OWNER,model,position,old,null,"Shared/repeated owner already handled; no second update"));continue;}
                double cap=position;
                if(!Double.isFinite(position)||!Double.isFinite(old)||!Double.isFinite(cap))
                    throw new IllegalStateException("Nonfinite initialized travel/limit");
                if(cap==old) {
                    report.add(row.outcome(Status.UNCHANGED,model,position,old,old,
                            "Upper bound already equals initialized travel; no change"));continue;
                }
                setter.accept(cap);
                report.add(row.outcome(Status.APPLIED,model,position,old,cap,
                        shared==null?"Initialized travel; no artificial margin; states/history retained":"Projected initialized valve; no expansion of clipping or entered constants"));
            } catch(RuntimeException failure) {
                report.add(row.outcome(Status.ERROR,model,position,old,null,failure.getMessage()));
            }
        }
        return List.copyOf(report);
    }
}
