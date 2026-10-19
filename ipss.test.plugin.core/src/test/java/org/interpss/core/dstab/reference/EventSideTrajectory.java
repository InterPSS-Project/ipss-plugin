package org.interpss.core.dstab.reference;

import java.util.*;

/** Immutable diagnostic history with explicit one-sided sampling at discontinuities. */
public final class EventSideTrajectory {
    public enum Side { BEFORE, AFTER }
    private final List<Map<String,Double>> rows;

    public EventSideTrajectory(List<? extends Map<String,Double>> input) {
        if(input.isEmpty())throw new IllegalArgumentException("Empty trajectory");
        var copy=new ArrayList<Map<String,Double>>();double previous=Double.NEGATIVE_INFINITY;int duplicates=0;
        var schema=input.getFirst().keySet();
        for(var row:input) {
            if(!row.keySet().equals(schema) || !row.containsKey("time_s"))throw new IllegalArgumentException("Inconsistent trajectory schema");
            for(var value:row.values())if(value==null || !Double.isFinite(value))throw new IllegalArgumentException("Nonfinite trajectory value");
            double time=row.get("time_s");
            if(time<previous)throw new IllegalArgumentException("Unordered trajectory");
            duplicates=time==previous?duplicates+1:0;
            if(duplicates>1)throw new IllegalArgumentException("More than two event sides at one timestamp");
            copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));previous=time;
        }
        rows=List.copyOf(copy);
    }

    public Map<String,Double> sample(double time,Side side) {
        Objects.requireNonNull(side);
        if(!Double.isFinite(time) || time<rows.getFirst().get("time_s") || time>rows.getLast().get("time_s"))
            throw new IllegalArgumentException("Sampling outside the recorded trajectory");
        int lo=0,hi=rows.size();
        while(lo<hi) {int mid=(lo+hi)>>>1;if(rows.get(mid).get("time_s")<time)lo=mid+1;else hi=mid;}
        if(rows.get(lo).get("time_s")==time) {
            if(side==Side.AFTER && lo+1<rows.size() && rows.get(lo+1).get("time_s")==time)lo++;
            return rows.get(lo);
        }
        var left=rows.get(lo-1);var right=rows.get(lo);
        double fraction=(time-left.get("time_s"))/(right.get("time_s")-left.get("time_s"));
        var result=new LinkedHashMap<String,Double>();
        for(String key:left.keySet())result.put(key,key.equals("time_s")?time:left.get(key)+fraction*(right.get(key)-left.get(key)));
        return Collections.unmodifiableMap(result);
    }
}
