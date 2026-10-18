package org.interpss.dstab.renewable;

import java.util.Arrays;
import java.util.Objects;

/** Exact PSS/E 36.7 REECE data contract, not a REECD alias or a runtime descriptor. */
public record Reece1Data(Selectors selectors, double[] constants) {
    public static final int ICON_COUNT=8, CON_COUNT=79;
    /** Ordinals are native CON offsets, including interleaved VDL tables. */
    public enum Parameter {
        VDIP,VUP,TRV,DBD1,DBD2,KQV,IQH1,IQL1,VREF0,IQFRZ,THLD,THLD2,TP,
        QMAX,QMIN,VMAX,VMIN,KQP,KQI,KVP,KVI,VBIAS,TIQ,DPMAX,DPMIN,PMAX,PMIN,IMAX,TPORD,
        VQ1,IQ1,VQ2,IQ2,VQ3,IQ3,VQ4,IQ4,VQ5,IQ5,VQ6,IQ6,VQ7,IQ7,VQ8,IQ8,VQ9,IQ9,VQ10,IQ10,
        VP1,IP1,VP2,IP2,VP3,IP3,VP4,IP4,VP5,IP5,VP6,IP6,VP7,IP7,VP8,IP8,VP9,IP9,VP10,IP10,
        RC,XC,TR1,KC,KE,VBLKL,VBLKH,TBLK,KPP,KPI
    }
    public record Selectors(int powerFactorFlag,int voltageFlag,int reactiveControlFlag,
            int powerFlag,int pqPriorityFlag,int voltageCompensationFlag,
            int activePowerPiFlag,int faultPriorityFlag) {
        public Selectors {
            for(int flag:new int[]{powerFactorFlag,voltageFlag,powerFlag,pqPriorityFlag,
                    voltageCompensationFlag,activePowerPiFlag,faultPriorityFlag})
                if(flag!=0&&flag!=1)throw new IllegalArgumentException("REECE binary selector must be 0 or 1");
            if(reactiveControlFlag<0||reactiveControlFlag>2)throw new IllegalArgumentException("REECE QFLAG must be 0, 1 or 2");
        }
        public int currentPriority(boolean voltageDip){return voltageDip?faultPriorityFlag:pqPriorityFlag;}
        public int[] toNative(){return new int[]{powerFactorFlag,voltageFlag,reactiveControlFlag,powerFlag,
                pqPriorityFlag,voltageCompensationFlag,activePowerPiFlag,faultPriorityFlag};}
    }
    public Reece1Data {
        Objects.requireNonNull(selectors);Objects.requireNonNull(constants);
        if(constants.length!=CON_COUNT)throw new IllegalArgumentException("REECE requires exactly 79 CONs");
        constants=constants.clone();
        for(double value:constants)if(!Double.isFinite(value))throw new IllegalArgumentException("REECE constants must be finite");
        for(Parameter time:new Parameter[]{Parameter.TRV,Parameter.THLD2,Parameter.TP,Parameter.TIQ,
                Parameter.TPORD,Parameter.TR1,Parameter.TBLK})
            if(constants[time.ordinal()]<0)throw new IllegalArgumentException("REECE time must be nonnegative: "+time);
        for(Parameter[] limits:new Parameter[][]{{Parameter.VDIP,Parameter.VUP},{Parameter.IQL1,Parameter.IQH1},
                {Parameter.QMIN,Parameter.QMAX},{Parameter.VMIN,Parameter.VMAX},{Parameter.DPMIN,Parameter.DPMAX},
                {Parameter.PMIN,Parameter.PMAX},{Parameter.VBLKL,Parameter.VBLKH}})
            if(constants[limits[0].ordinal()]>constants[limits[1].ordinal()])throw new IllegalArgumentException("REECE reversed limits: "+limits[0]);
        double ke=constants[Parameter.KE.ordinal()];
        if(ke<0||ke>1)throw new IllegalArgumentException("REECE Ke must be in [0,1]");
        // WECC's new active PI explicitly requires a positive integral gain.
        if(selectors.activePowerPiFlag()==1&&constants[Parameter.KPI.ordinal()]<=0)
            throw new IllegalArgumentException("REECE active PI requires Kpi > 0");
        double[] vp=new double[10],ip=new double[10];
        for(int i=0;i<10;i++){vp[i]=constants[49+2*i];ip[i]=constants[50+2*i];}
        for(int i=0;i<Reecd1Data.tablePointCount(vp,ip);i++)
            if(ip[i]<0)throw new IllegalArgumentException("REECE active VDL current must be nonnegative");
    }
    public static Reece1Data fromNativeParameters(int[] icons,double[] constants) {
        Objects.requireNonNull(icons);
        if(icons.length!=ICON_COUNT)throw new IllegalArgumentException("REECE requires exactly eight ICONs");
        return new Reece1Data(new Selectors(icons[0],icons[1],icons[2],icons[3],icons[4],icons[5],icons[6],icons[7]),constants);
    }
    @Override public double[] constants(){return constants.clone();}
    public double value(Parameter parameter){return constants[Objects.requireNonNull(parameter).ordinal()];}
    public int[] toNativeIcons(){return selectors.toNative();}
    public double[] toNativeConstants(){return constants();}
    @Override public boolean equals(Object other){return other instanceof Reece1Data data&&selectors.equals(data.selectors)&&Arrays.equals(constants,data.constants);}
    @Override public int hashCode(){return 31*selectors.hashCode()+Arrays.hashCode(constants);}
}
