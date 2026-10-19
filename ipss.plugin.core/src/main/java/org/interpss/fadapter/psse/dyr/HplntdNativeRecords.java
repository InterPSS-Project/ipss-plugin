package org.interpss.fadapter.psse.dyr;

import java.util.*;
import org.interpss.dstab.renewable.*;

/** Exact hybrid bus/unit wrapper decoder. Decoding is not runtime attachment;
 * no standard-name alias, alternate allocation or cross-vendor layout is inferred. */
public final class HplntdNativeRecords {
    private HplntdNativeRecords() {}
    public static final class Plant {
        private final PsseDyrRecord source;private final HplntdData data;
        private Plant(PsseDyrRecord source,HplntdData data){this.source=source;this.data=data;}
        public PsseDyrRecord source(){return source;}
        public HplntdData data(){return data;}
    }
    public static final class Unit {
        private final PsseDyrRecord source;private final ReaxdData data;
        private Unit(PsseDyrRecord source,ReaxdData data){this.source=source;this.data=data;}
        public PsseDyrRecord source(){return source;}
        public ReaxdData data(){return data;}
    }
    public static Plant plant(PsseDyrRecord r) {
        try {
            header(r,"USRBUS",HplntdData.NATIVE_MODEL,3,102,504,0,35,61,13,45);
            quoted(r,12);quoted(r,21);
            for(int i=0;i<10;i++)if(r.intParameter(19+2*i)!=0||!r.parameter(20+2*i).equals("0"))quoted(r,23+2*i);
            int s=6;var shunts=new ArrayList<RepccData.Shunt>();
            for(int i=0;i<10;i++)shunts.add(new RepccData.Shunt(r.intParameter(s+13+2*i),r.parameter(s+14+2*i)));
            double[] c=new double[61];for(int i=0;i<c.length;i++)c[i]=r.doubleParameter(s+35+i);
            return new Plant(r,new HplntdData(r.intParameter(s),
                    new RepccData.Branch(r.intParameter(s+1),r.intParameter(s+2),r.parameter(s+3)),
                    r.intParameter(s+4),r.intParameter(s+5),r.intParameter(s+6),r.intParameter(s+7),r.intParameter(s+8),r.intParameter(s+9),
                    new RepccData.Branch(r.intParameter(s+10),r.intParameter(s+11),r.parameter(s+12)),
                    shunts,r.intParameter(s+33),r.intParameter(s+34),c));
        }catch(IllegalArgumentException e){throw located(r,e);}
    }
    public static Unit unit(PsseDyrRecord r) {
        try {
            header(r,"USRMDL",ReaxdData.NATIVE_MODEL,4,23,107,0,9,8,2,7);
            quoted(r,11);
            String id=r.deviceId();
            if(id.isBlank()||id.length()>2||id.contains("'"))throw new IllegalArgumentException("One-/two-character downstream device ID required");
            int[] links=new int[7];double[] c=new double[8];
            for(int i=0;i<links.length;i++)links[i]=r.intParameter(8+i);
            for(int i=0;i<c.length;i++)c[i]=r.doubleParameter(15+i);
            return new Unit(r,new ReaxdData(r.intParameter(6),r.parameter(7),links,c));
        }catch(IllegalArgumentException e){throw located(r,e);}
    }
    private static void header(PsseDyrRecord r,String wrapper,String model,int offset,int count,int... allocation) {
        Objects.requireNonNull(r);
        if(r.busNumber()<=0||Integer.parseInt(r.fields().getFirst())!=r.busNumber()
                ||!r.sourceModelName().equals(model)||!r.fields().get(1).equalsIgnoreCase(wrapper)
                ||r.parameterOffset()!=offset||r.parameterCount()!=count)
            throw new IllegalArgumentException("Exact "+model+" "+wrapper+" wrapper and cardinality required");
        for(int i=0;i<allocation.length;i++)if(r.intParameter(i)!=allocation[i])
            throw new IllegalArgumentException("Incorrect "+model+" allocation at header position "+(i+1));
        for(int i=1;i<offset;i++)quoted(r,i);
    }
    private static void quoted(PsseDyrRecord r,int field){
        var matcher=java.util.regex.Pattern.compile("'(?:[^']|'')*'|[^\\s,]+").matcher(r.rawText());
        for(int i=0;i<=field;i++)if(!matcher.find())throw new IllegalArgumentException("Missing character field "+field);
        if(!matcher.group().startsWith("'"))throw new IllegalArgumentException("Quoted character field required at position "+field);
    }
    private static IllegalArgumentException located(PsseDyrRecord r,IllegalArgumentException error) {
        return new IllegalArgumentException(error.getMessage()+" at "+r.source()+":"+r.startLine(),error);
    }
    /** Build an immutable two-pass plan for the exact hybrid records only.
     * Other model records remain the responsibility of the enclosing importer. */
    public static HplntdBindingPlan plan(List<PsseDyrRecord> records) {
        var plants=new ArrayList<Plant>();var units=new ArrayList<Unit>();
        for(var r:records){
            if(r.sourceModelName().equals(HplntdData.NATIVE_MODEL))plants.add(plant(r));
            else if(r.sourceModelName().equals(ReaxdData.NATIVE_MODEL))units.add(unit(r));
        }
        return HplntdBindingPlan.compile(plants,units);
    }
}
