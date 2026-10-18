package org.interpss.dstab.renewable;

import java.util.*;

/** Exact bus-controller data foundation: no parser attachment or runtime alias.
 * Powers use plant MVA base except the four MSS thresholds, which use Mvar. */
public record HplntdData(int remoteBus,RepccData.Branch measurementBranch,int compensationFlag,
        int referenceFlag,int frequencyFlag,int feedbackFlag,int feedforwardFlag,int switchingFlag,
        RepccData.Branch switchingBranch,List<RepccData.Shunt> shunts,int frequencyBus,int reactiveFlag,
        double[] constants) {
    public static final String NATIVE_MODEL="HPLNTDU";
    public static final int USER_BUS_TYPE=504,ICON_COUNT=35,CON_COUNT=61,STATE_COUNT=13,VAR_COUNT=45;
    public enum Parameter {
        TFLTR,KP,KI,TFT,TFV,VFRZL,RC,XC,KC,EMAX,EMIN,DBD1,DBD2,QMAX,QMIN,KPG,KIG,TP,
        FDBD1,FDBD2,PFEMAX,PFEMIN,PMAX,PMIN,TG,DDN,DUP,VREFMAX,VREFMIN,QREFMAX,QREFMIN,
        DQREFMAX,DQREFMIN,QRMAX,QRMIN,DPRMAX,DPRMIN,PFMAX,PFMIN,DPRMAX2,DPRMIN2,PIMAX,PIMIN,
        TC,QDN1,QDN2,QUP1,QUP2,TDELAY1,TDELAY2,TMSSBRK,TOUT,TFRZ,TFRQ,VFRQ,DFMAX,DFMIN,
        PLANT_MVA,FRMAX,FRMIN,VFRZH
    }
    public HplntdData {
        Objects.requireNonNull(measurementBranch);Objects.requireNonNull(switchingBranch);
        shunts=List.copyOf(shunts);constants=Objects.requireNonNull(constants).clone();
        if(remoteBus<0||frequencyBus<0||shunts.size()!=10||constants.length!=CON_COUNT)
            throw new IllegalArgumentException("Exact hybrid plant bus, shunt-slot and CON layout required");
        if(referenceFlag<0||referenceFlag>2)throw new IllegalArgumentException("Hybrid RefFlag must be zero, one or two");
        for(int f:new int[]{compensationFlag,frequencyFlag,feedbackFlag,feedforwardFlag,switchingFlag,reactiveFlag})
            if(f!=0&&f!=1)throw new IllegalArgumentException("Binary hybrid plant flags required");
        for(double c:constants)if(!Double.isFinite(c))throw new IllegalArgumentException("Finite hybrid plant CONs required");
        for(Parameter p:new Parameter[]{Parameter.TFLTR,Parameter.TFT,Parameter.TFV,Parameter.TP,Parameter.TG,Parameter.TC,
                Parameter.TDELAY1,Parameter.TDELAY2,Parameter.TMSSBRK,Parameter.TOUT,Parameter.TFRZ,Parameter.TFRQ,Parameter.PLANT_MVA})
            if(constants[p.ordinal()]<0)throw new IllegalArgumentException("Nonnegative hybrid time/base required: "+p);
        for(Parameter[] pair:new Parameter[][]{{Parameter.EMIN,Parameter.EMAX},{Parameter.QMIN,Parameter.QMAX},
                {Parameter.PFEMIN,Parameter.PFEMAX},{Parameter.PMIN,Parameter.PMAX},{Parameter.VREFMIN,Parameter.VREFMAX},
                {Parameter.QREFMIN,Parameter.QREFMAX},{Parameter.DQREFMIN,Parameter.DQREFMAX},{Parameter.QRMIN,Parameter.QRMAX},
                {Parameter.DPRMIN,Parameter.DPRMAX},{Parameter.PFMIN,Parameter.PFMAX},{Parameter.DPRMIN2,Parameter.DPRMAX2},
                {Parameter.PIMIN,Parameter.PIMAX},{Parameter.DFMIN,Parameter.DFMAX},{Parameter.FRMIN,Parameter.FRMAX},{Parameter.VFRZL,Parameter.VFRZH}})
            if(constants[pair[0].ordinal()]>constants[pair[1].ordinal()])throw new IllegalArgumentException("Ordered hybrid limits required: "+pair[0]);
        if(constants[Parameter.DBD1.ordinal()]>0||constants[Parameter.DBD2.ordinal()]<0
                ||constants[Parameter.FDBD1.ordinal()]>0||constants[Parameter.FDBD2.ordinal()]<0
                ||constants[Parameter.FRMIN.ordinal()]>0||constants[Parameter.FRMAX.ordinal()]<0
                ||constants[Parameter.DFMIN.ordinal()]>0||constants[Parameter.DFMAX.ordinal()]<0
                ||constants[Parameter.DDN.ordinal()]<0||constants[Parameter.DUP.ordinal()]<0)
            throw new IllegalArgumentException("Hybrid deadband, droop and frequency-rate signs are invalid");
    }
    @Override public double[] constants(){return constants.clone();}
    public double value(Parameter p){return constants[p.ordinal()];}
}
