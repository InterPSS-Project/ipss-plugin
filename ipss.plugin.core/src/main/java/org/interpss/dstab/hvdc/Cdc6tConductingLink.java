package org.interpss.dstab.hvdc;

/** Fixed-current, fixed-tap conducting equations. Protection, recovery and current-margin history are external. */
public final class Cdc6tConductingLink {
    private final Cdc6tConverterBoundary rectifier,inverter;
    private final double resistance,alphaMinimum,gammaMinimum;
    private final double[] trial=new double[8];
    public Cdc6tConductingLink(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,
            double lineOhms,double alphaMinimumRadians,double gammaMinimumRadians) {
        this.rectifier=java.util.Objects.requireNonNull(rectifier);this.inverter=java.util.Objects.requireNonNull(inverter);
        if(!Double.isFinite(lineOhms)||lineOhms<0)throw new IllegalArgumentException("Nonnegative finite line resistance required");
        for(double angle:new double[]{alphaMinimumRadians,gammaMinimumRadians})
            if(!Double.isFinite(angle)||angle<0||angle>=Math.PI/2)throw new IllegalArgumentException("Valid conducting minimum angle required");
        resistance=lineOhms;alphaMinimum=alphaMinimumRadians;gammaMinimum=gammaMinimumRadians;
    }
    /** Writes rectifier P,Q,angle; inverter P,Q,angle; rectifier volts; inverter volts.
     * All values are trial algebraic results; evaluation commits no control mode or timer.
     * This instance owns reusable scratch space and is not shared across concurrent solves. */
    public void evaluate(double rectifierAcPu,double inverterAcPu,double currentOrderAmperes,
            double inverterVoltageOrder,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,8,output.length);
        if(!Double.isFinite(inverterVoltageOrder)||inverterVoltageOrder<0)
            throw new IllegalArgumentException("Nonnegative finite voltage order required");
        double drop=resistance*currentOrderAmperes;
        double rectifierCeiling=rectifier.maximumDcVoltage(rectifierAcPu,currentOrderAmperes,alphaMinimum);
        double inverterCeiling=inverter.maximumDcVoltage(inverterAcPu,currentOrderAmperes,gammaMinimum);
        double vi=Math.min(inverterVoltageOrder,Math.min(rectifierCeiling-drop,inverterCeiling));
        double vr=vi+drop;
        if(!Double.isFinite(vi)||!Double.isFinite(vr)||vi<0||vr<0)
            throw new IllegalArgumentException("Current order is infeasible in the conducting regime");
        rectifier.evaluate(rectifierAcPu,vr,currentOrderAmperes,trial,0);
        inverter.evaluate(inverterAcPu,vi,currentOrderAmperes,trial,3);
        trial[6]=vr;trial[7]=vi;System.arraycopy(trial,0,output,offset,8);
    }
}
