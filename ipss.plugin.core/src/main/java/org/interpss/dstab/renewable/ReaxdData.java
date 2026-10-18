package org.interpss.dstab.renewable;

import java.util.*;

/** Exact nine-ICON/eight-CON downstream hybrid interface foundation.
 * Relative upstream VAR indexes are one-based; invalid links are retained so
 * attachment can report the source-defined ignored path rather than remap it. */
public record ReaxdData(int plantBus,String plantModel,int[] relativeVariables,double[] constants) {
    public static final String NATIVE_MODEL="REAXDU";
    public static final int USER_MODEL_TYPE=107,ICON_COUNT=9,CON_COUNT=8,STATE_COUNT=2,VAR_COUNT=7;
    public enum Parameter { KZ,TZ,KW,TW,PMAXREF,PMINREF,QMAXREF,QMINREF }
    public ReaxdData {
        plantModel=Objects.requireNonNull(plantModel).trim();relativeVariables=relativeVariables.clone();constants=constants.clone();
        if(plantBus<0||plantModel.isEmpty()||plantModel.length()>12||plantModel.indexOf('\'')>=0
                ||relativeVariables.length!=7||constants.length!=CON_COUNT)
            throw new IllegalArgumentException("Exact downstream hybrid link and CON layout required");
        for(double c:constants)if(!Double.isFinite(c))throw new IllegalArgumentException("Finite downstream CONs required");
        // Source minimum-time correction applies to every time below 2*step;
        // retain the entered value and apply the effective time at evaluation.
        if(constants[5]>constants[4]||constants[7]>constants[6])
            throw new IllegalArgumentException("Ordered downstream limits required");
    }
    @Override public int[] relativeVariables(){return relativeVariables.clone();}
    @Override public double[] constants(){return constants.clone();}
    public double value(Parameter p){return constants[p.ordinal()];}
    public boolean validRelativeVariables(int plantVariableCount) {
        return Arrays.stream(relativeVariables).allMatch(i->i>0&&i<=plantVariableCount);
    }
    public double activeTime(double step){return effectiveTime(constants[1],step);}
    public double reactiveTime(double step){return effectiveTime(constants[3],step);}
    private static double effectiveTime(double configured,double step) {
        if(!Double.isFinite(step)||step<=0||!Double.isFinite(2*step))throw new IllegalArgumentException("Positive finite integration step required");
        return Math.max(configured,2*step);
    }
}
