package org.interpss.dstab.renewable;

import java.util.Objects;
import org.apache.commons.math3.complex.Complex;

/** Exact native PSS/E 36.7 REGFMC1U data, separate from the PowerWorld dialect.
 * RAW ZSOURCE is on machine MVA base, not two extra DYR constants.
 * This contract alone does not provide a loadable dynamic device. */
public final class Regfmc1Data {
    public static final int ICON_COUNT=3, CON_COUNT=34, STATE_COUNT=11, VAR_COUNT=27;
    public enum Flag { FFLAG,PQFLAG,VFLAG }
    public enum Parameter {
        MQ,KPE,KIE,DEMAX,DEMIN,TVR,TE,TVF,TIF,TPF,H,D1,D2,WD,MP,
        DPGFMMAX,DPGFMMIN,DPDMAX,DPDMIN,DWMAX,DWMIN,TWREF,TWM,
        PREFMAX,PREFMIN,QREFMAX,QREFMIN,VMIN,VREF0,KQV,DBVH,DBVL,KF,IMAX
    }
    private final int[] icons;
    private final double[] constants;
    private final Complex source;

    public static Regfmc1Data nativePsse(Complex rawSource,int[] icons,double[] constants) {
        return new Regfmc1Data(rawSource,icons,constants);
    }
    private Regfmc1Data(Complex rawSource,int[] flags,double[] values) {
        source=Objects.requireNonNull(rawSource,"RAW ZSOURCE");
        Objects.requireNonNull(flags);Objects.requireNonNull(values);
        if(flags.length!=ICON_COUNT||values.length!=CON_COUNT)
            throw new IllegalArgumentException("Native REGFMC1U needs three ICONs and 34 CONs");
        icons=flags.clone();constants=values.clone();
        if(!Double.isFinite(source.getReal())||!Double.isFinite(source.getImaginary())||source.getImaginary()<=0)
            throw new IllegalArgumentException("REGFMC1U needs finite RAW ZSOURCE with positive reactance");
        for(double v:constants)if(!Double.isFinite(v))throw new IllegalArgumentException("REGFMC1U CONs must be finite");
        for(Parameter p:new Parameter[]{Parameter.MQ,Parameter.KIE,Parameter.TE,Parameter.H,Parameter.MP,Parameter.IMAX})
            if(value(p)<=0)throw new IllegalArgumentException("REGFMC1U "+p+" must be positive");
        for(Parameter p:new Parameter[]{Parameter.TVR,Parameter.TVF,Parameter.TIF,Parameter.TPF,Parameter.WD,Parameter.TWREF,Parameter.TWM})
            if(value(p)<0)throw new IllegalArgumentException("REGFMC1U "+p+" must be nonnegative");
        for(Parameter[] pair:new Parameter[][]{{Parameter.DEMIN,Parameter.DEMAX},{Parameter.DPGFMMIN,Parameter.DPGFMMAX},
                {Parameter.DPDMIN,Parameter.DPDMAX},{Parameter.DWMIN,Parameter.DWMAX},
                {Parameter.PREFMIN,Parameter.PREFMAX},{Parameter.QREFMIN,Parameter.QREFMAX}})
            if(value(pair[0])>=value(pair[1]))throw new IllegalArgumentException("REGFMC1U limits must be strictly ordered: "+pair[0]);
        if(value(Parameter.KF)<0||value(Parameter.KF)>1)throw new IllegalArgumentException("REGFMC1U Kf must lie in [0,1]; zero means one");
        if(value(Parameter.DBVL)>0||value(Parameter.DBVH)<0)throw new IllegalArgumentException("REGFMC1U deadband must bracket zero");
    }
    public double value(Parameter p){return constants[p.ordinal()];}
    public int rawFlag(Flag f){return icons[f.ordinal()];}
    /** Native note 19 maps any ICON other than zero or one to one. */
    public boolean enabled(Flag f){return rawFlag(f)!=0;}
    public int[] toNativeIcons(){return icons.clone();}
    public double[] constants(){return constants.clone();}
    public Complex sourceImpedance(){return source;}
    public double currentPriorityFactor(){return value(Parameter.KF)==0?1:value(Parameter.KF);}
    public double minimumGflVoltage(){return value(Parameter.VMIN)<=0?.01:value(Parameter.VMIN);}
    public double gflVoltageReference(double initialVoltage) {
        if(!Double.isFinite(initialVoltage)||initialVoltage<=0)throw new IllegalArgumentException("Initial voltage must be positive and finite");
        return value(Parameter.VREF0)==0?initialVoltage:value(Parameter.VREF0);
    }
    /** Native note 10 applies to Te only; do not import PowerWorld's other corrections. */
    public double internalVoltageTimeConstant(double step) {
        if(!Double.isFinite(step)||step<=0)throw new IllegalArgumentException("Step must be positive and finite");
        return Math.max(value(Parameter.TE),2*step);
    }
}
