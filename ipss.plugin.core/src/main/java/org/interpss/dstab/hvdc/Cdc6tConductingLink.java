package org.interpss.dstab.hvdc;

/** Fixed-tap conducting equations with algebraic current-margin transfer; protection history is external. */
public final class Cdc6tConductingLink {
    public enum InverterControl { SCHEDULED_VOLTAGE, CONSTANT_GAMMA }
    private final Cdc6tConverterBoundary rectifier,inverter;
    private final double resistance,alphaMinimum,gammaMinimum,currentMargin;
    private final InverterControl inverterControl;
    private final double[] trial=new double[8];
    public Cdc6tConductingLink(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,
            double lineOhms,double alphaMinimumRadians,double gammaMinimumRadians) {
        this(rectifier,inverter,lineOhms,alphaMinimumRadians,gammaMinimumRadians,InverterControl.SCHEDULED_VOLTAGE);
    }
    public Cdc6tConductingLink(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,
            double lineOhms,double alphaMinimumRadians,double gammaMinimumRadians,InverterControl inverterControl) {
        this(rectifier,inverter,lineOhms,alphaMinimumRadians,gammaMinimumRadians,inverterControl,0);
    }
    public Cdc6tConductingLink(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,
            double lineOhms,double alphaMinimumRadians,double gammaMinimumRadians,InverterControl inverterControl,double currentMargin) {
        this.rectifier=java.util.Objects.requireNonNull(rectifier);this.inverter=java.util.Objects.requireNonNull(inverter);
        this.inverterControl=java.util.Objects.requireNonNull(inverterControl);
        if(!Double.isFinite(lineOhms)||lineOhms<0)throw new IllegalArgumentException("Nonnegative finite line resistance required");
        for(double angle:new double[]{alphaMinimumRadians,gammaMinimumRadians})
            if(!Double.isFinite(angle)||angle<0||angle>=Math.PI/2)throw new IllegalArgumentException("Valid conducting minimum angle required");
        resistance=lineOhms;alphaMinimum=alphaMinimumRadians;gammaMinimum=gammaMinimumRadians;
        if(!Double.isFinite(currentMargin)||currentMargin<0||currentMargin>=1)
            throw new IllegalArgumentException("Current margin must be finite in [0,1)");
        this.currentMargin=currentMargin;
    }
    /** Writes rectifier P,Q,angle; inverter P,Q,angle; rectifier volts; inverter volts.
     * All values are trial algebraic results; evaluation commits no control mode or timer.
     * This instance owns reusable scratch space and is not shared across concurrent solves. */
    public void evaluate(double rectifierAcPu,double inverterAcPu,double currentOrderAmperes,
            double inverterVoltageOrder,double[] output,int offset) {
        evaluateCurrent(rectifierAcPu,inverterAcPu,currentOrderAmperes,inverterVoltageOrder,output,offset);
    }
    /** Returns delivered current, including one margin reduction if the rectifier
     * reaches its minimum angle. Repeated evaluations do not retain a reduction. */
    public double evaluateCurrent(double rectifierAcPu,double inverterAcPu,double currentOrderAmperes,
            double inverterVoltageOrder,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,8,output.length);
        if(!Double.isFinite(inverterVoltageOrder)||inverterVoltageOrder<0)
            throw new IllegalArgumentException("Nonnegative finite voltage order required");
        double drop=resistance*currentOrderAmperes;
        double rectifierCeiling=rectifier.maximumDcVoltage(rectifierAcPu,currentOrderAmperes,alphaMinimum);
        double inverterCeiling=inverter.maximumDcVoltage(inverterAcPu,currentOrderAmperes,gammaMinimum);
        if(currentMargin!=0&&rectifierCeiling-drop<(inverterControl==InverterControl.CONSTANT_GAMMA
                ?inverterCeiling:Math.min(inverterVoltageOrder,inverterCeiling))){
            currentOrderAmperes*=1-currentMargin;
            drop=resistance*currentOrderAmperes;
            rectifierCeiling=rectifier.maximumDcVoltage(rectifierAcPu,currentOrderAmperes,alphaMinimum);
            inverterCeiling=inverter.maximumDcVoltage(inverterAcPu,currentOrderAmperes,gammaMinimum);
        }
        double vi=Math.min(rectifierCeiling-drop,inverterCeiling);
        if(inverterControl==InverterControl.SCHEDULED_VOLTAGE)vi=Math.min(inverterVoltageOrder,vi);
        double vr=vi+drop;
        if(!Double.isFinite(vi)||!Double.isFinite(vr)||vi<0||vr<0)
            throw new IllegalArgumentException("Current order is infeasible in the conducting regime");
        rectifier.evaluate(rectifierAcPu,vr,currentOrderAmperes,trial,0);
        inverter.evaluate(inverterAcPu,vi,currentOrderAmperes,trial,3);
        trial[6]=vr;trial[7]=vi;System.arraycopy(trial,0,output,offset,8);
        return currentOrderAmperes;
    }

    /** Closed inverter bypass: the rectifier maintains the supplied current through the line resistance.
     * The event/controller owner supplies bypass current; ordinary power-order conversion is not applied here. */
    public void evaluateBypassed(double rectifierAcPu,double currentAmperes,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,8,output.length);
        double rectifierVolts=resistance*currentAmperes;
        rectifier.evaluate(rectifierAcPu,rectifierVolts,currentAmperes,trial,0);
        if(trial[2]<alphaMinimum-1e-12)
            throw new IllegalArgumentException("Bypass current exceeds the rectifier minimum-angle capability");
        trial[3]=0;trial[4]=0;trial[5]=Math.PI/2;
        trial[6]=rectifierVolts;trial[7]=0;
        System.arraycopy(trial,0,output,offset,8);
    }
}
