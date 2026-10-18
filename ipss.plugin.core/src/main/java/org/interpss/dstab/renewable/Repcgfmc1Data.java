package org.interpss.dstab.renewable;

import java.util.Objects;

/** Exact REPCGFMC1U plant-controller data contract, not a loadable device.
 * Native allocation is seven mixed-type ICONs, 54 CONs, 15 STATEs and 20 VARs.
 * Keep the quoted branch circuit separate from the six integer ICONs. */
public final class Repcgfmc1Data {
    public static final int ICON_COUNT=7, CON_COUNT=54, STATE_COUNT=15, VAR_COUNT=20;
    public enum Parameter {
        FREQMAX,FREQMIN,FRMAX,FRMIN,VFTH,TFF,TFREFD,RLOSS,XLOSS,
        VREFMAX,VREFMIN,TVF,TVREFD,TVD,DBFH,DBFL,DDN,DUP,PFMAX,PFMIN,
        PREFMAX,PREFMIN,TPF,PRERMAX,PRERMIN,KIP,PERRMAX,PERRMIN,TPD,
        QREFMAX,QREFMIN,TQF,KIQ,QRERMAX,QRERMIN,QERRMAX,QERRMIN,TQD,
        VERRMAX,VERRMIN,DBVH,DBVL,KPVC,QVCMAX,QVCMIN,TVC,KIVC,
        FFRLOW,FFRHIGH,PFFRLOW,PFFRHIGH,FFRR,TFFR,PLNTMVA
    }
    private final int frequencyBus,branchFrom,branchTo,vFlag,vrefFlag,ffrFlag;
    private final String circuit;
    private final double[] constants;

    public Repcgfmc1Data(int frequencyBus,int branchFrom,int branchTo,String circuit,
                        int vFlag,int vrefFlag,int ffrFlag,double[] constants) {
        if(frequencyBus<0||branchTo<0||branchFrom==Integer.MIN_VALUE)
            throw new IllegalArgumentException("Invalid REPCGFMC1U measurement bus number");
        this.circuit=Objects.requireNonNull(circuit).trim();
        if(this.circuit.isEmpty()||this.circuit.length()>2||this.circuit.contains("'"))
            throw new IllegalArgumentException("REPCGFMC1U circuit must be a nonempty one/two-character identifier");
        if((vFlag!=0&&vFlag!=1)||(vrefFlag!=0&&vrefFlag!=1))
            throw new IllegalArgumentException("REPCGFMC1U VFlag and VrefFlag must be zero or one");
        this.frequencyBus=frequencyBus;this.branchFrom=branchFrom;this.branchTo=branchTo;
        this.vFlag=vFlag;this.vrefFlag=vrefFlag;this.ffrFlag=ffrFlag;
        Objects.requireNonNull(constants);
        if(constants.length!=CON_COUNT)throw new IllegalArgumentException("REPCGFMC1U requires exactly 54 CONs");
        this.constants=constants.clone();
        for(double value:constants)if(!Double.isFinite(value))throw new IllegalArgumentException("Finite REPCGFMC1U CONs required");
        for(Parameter p:new Parameter[]{Parameter.TFF,Parameter.TFREFD,Parameter.TVF,Parameter.TVREFD,Parameter.TVD,
                Parameter.TPF,Parameter.TPD,Parameter.TQF,Parameter.TQD,Parameter.TVC,Parameter.KIP,Parameter.KIQ,
                Parameter.KPVC,Parameter.KIVC,Parameter.DDN,Parameter.DUP,Parameter.FFRR,Parameter.TFFR,Parameter.PLNTMVA})
            if(value(p)<0)throw new IllegalArgumentException("REPCGFMC1U "+p+" must be nonnegative");
        for(Parameter[] pair:new Parameter[][]{{Parameter.FREQMIN,Parameter.FREQMAX},{Parameter.VREFMIN,Parameter.VREFMAX},
                {Parameter.FRMIN,Parameter.FRMAX},{Parameter.PFMIN,Parameter.PFMAX},{Parameter.PREFMIN,Parameter.PREFMAX},
                {Parameter.PRERMIN,Parameter.PRERMAX},{Parameter.PERRMIN,Parameter.PERRMAX},
                {Parameter.QREFMIN,Parameter.QREFMAX},{Parameter.QRERMIN,Parameter.QRERMAX},
                {Parameter.QERRMIN,Parameter.QERRMAX},{Parameter.VERRMIN,Parameter.VERRMAX},
                {Parameter.QVCMIN,Parameter.QVCMAX},{Parameter.FFRLOW,Parameter.FFRHIGH}})
            if(value(pair[0])>value(pair[1]))throw new IllegalArgumentException("Ordered REPCGFMC1U bounds required: "+pair[0]);
        if(value(Parameter.FREQMIN)<=0||value(Parameter.VREFMIN)<=0||value(Parameter.DBFL)>0||value(Parameter.DBFH)<0
                ||value(Parameter.DBVL)>0||value(Parameter.DBVH)<0||value(Parameter.PFFRLOW)<0||value(Parameter.PFFRHIGH)>0)
            throw new IllegalArgumentException("Invalid REPCGFMC1U reference/deadband/FFR polarity");
    }
    public double value(Parameter parameter){return constants[parameter.ordinal()];}
    public double[] constants(){return constants.clone();}
    public int frequencyBus(){return frequencyBus;}
    public int branchFrom(){return branchFrom;}
    public int branchTo(){return branchTo;}
    public String circuit(){return circuit;}
    public int voltageFlag(){return vFlag;}
    public int voltageReferenceFlag(){return vrefFlag;}
    public int fastFrequencyFlag(){return ffrFlag;}
    /** Native FFR is enabled only for one, not for every nonzero ICON. */
    public boolean fastFrequencyEnabled(){return ffrFlag==1;}
    public boolean arrivingFlow(){return branchFrom<0;}
    public int measurementBus(){return Math.abs(branchFrom);}
    public double plantMva(double machineMva){
        if(!Double.isFinite(machineMva)||machineMva<=0)throw new IllegalArgumentException("Positive finite machine MVA required");
        return value(Parameter.PLNTMVA)>0?value(Parameter.PLNTMVA):machineMva;
    }
    /** Frequency measurement has a model-specific two-step minimum. */
    public double frequencyFilterTime(double step){
        if(!Double.isFinite(step)||step<=0)throw new IllegalArgumentException("Positive finite timestep required");
        return Math.max(value(Parameter.TFF),2*step);
    }
    /** Native zero-integral-gain initialization protects a zero proportional gain. */
    public double effectiveVoltageGain(){
        return value(Parameter.KIVC)==0&&value(Parameter.KPVC)==0?.01:value(Parameter.KPVC);
    }
}
