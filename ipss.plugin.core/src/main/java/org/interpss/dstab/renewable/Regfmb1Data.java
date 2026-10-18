package org.interpss.dstab.renewable;

import java.util.Objects;
import org.apache.commons.math3.complex.Complex;

/** Native 36.7 REGFMB1U data contract, not a parser alias or a complete runtime.
 * RAW ZSOURCE is on machine base and is not part of the 25-CON DYR record. */
public final class Regfmb1Data {
    public static final int ICON_COUNT=6, CON_COUNT=25, STATE_COUNT=14, VAR_COUNT=17;
    public enum Parameter {
        MQ,KPV,KIV,MP,DWMAX,DWMIN,KPPLL,KIPLL,DWPLLMAX,DWPLLMIN,
        TP,H,D1,D2,WD,IMAXSS,KF,KI,IMAXF,TPF,TQF,TVF,TIF,KE,VPLLFRZ
    }
    public enum Flag { WFLAG,VDRPFLAG,QVFLAG,PQFLAG,FFLAG,ESFLAG }
    private final int[] icons;
    private final double[] constants;
    private final Complex source;
    public static Regfmb1Data nativePsse(Complex rawSource,int[] icons,double[] constants) {
        return new Regfmb1Data(rawSource,icons,constants);
    }
    private Regfmb1Data(Complex rawSource,int[] flags,double[] values) {
        Objects.requireNonNull(rawSource,"RAW ZSOURCE");Objects.requireNonNull(flags);Objects.requireNonNull(values);
        if(flags.length!=ICON_COUNT||values.length!=CON_COUNT)
            throw new IllegalArgumentException("Native REGFMB1U needs six ICONs and 25 CONs");
        source=rawSource;icons=flags.clone();constants=values.clone();
        if(!Double.isFinite(source.getReal())||!Double.isFinite(source.getImaginary())||source.getImaginary()<=0)
            throw new IllegalArgumentException("REGFMB1U needs finite RAW ZSOURCE with positive reactance");
        for(double value:constants)if(!Double.isFinite(value))throw new IllegalArgumentException("REGFMB1U CONs must be finite");
        for(Parameter p:new Parameter[]{Parameter.TP,Parameter.TPF,Parameter.TQF,Parameter.TVF,Parameter.TIF,Parameter.WD})
            if(value(p)<0)throw new IllegalArgumentException("REGFMB1U "+p+" must be nonnegative");
        for(Parameter p:new Parameter[]{Parameter.KIV,Parameter.MP,Parameter.KIPLL,Parameter.H,Parameter.IMAXSS,Parameter.KI,Parameter.IMAXF})
            if(value(p)<=0)throw new IllegalArgumentException("REGFMB1U "+p+" must be positive");
        if(value(Parameter.DWMIN)>0||value(Parameter.DWMAX)<0||value(Parameter.DWPLLMIN)>0||value(Parameter.DWPLLMAX)<0)
            throw new IllegalArgumentException("REGFMB1U frequency limits must bracket zero");
        if(value(Parameter.KE)<0||value(Parameter.KE)>1)throw new IllegalArgumentException("REGFMB1U Ke must lie in [0,1]");
    }
    public double value(Parameter p){return constants[p.ordinal()];}
    public int rawFlag(Flag flag){return icons[flag.ordinal()];}
    /** Native note 9 interprets every nonzero ICON as one, without guessing aliases. */
    public boolean enabled(Flag flag){return rawFlag(flag)!=0;}
    /** Native note 19 overrides current droop when plant input is Qref. */
    public boolean currentDroop(){return enabled(Flag.VDRPFLAG)&&enabled(Flag.QVFLAG);}
    public Complex sourceImpedance(){return source;}
    public int[] toNativeIcons(){return icons.clone();}
    public double[] constants(){return constants.clone();}
    /** Native note 14 swaps inverted steady/fault limits at initialization. */
    public double steadyCurrentLimit(){return Math.min(value(Parameter.IMAXSS),value(Parameter.IMAXF));}
    public double faultCurrentLimit(){return Math.max(value(Parameter.IMAXSS),value(Parameter.IMAXF));}
    public double pllFreezeVoltage(){return Math.min(.1,value(Parameter.VPLLFRZ));}
}
