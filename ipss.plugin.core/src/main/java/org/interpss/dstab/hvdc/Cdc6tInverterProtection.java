package org.interpss.dstab.hvdc;

/** Inverter-origin blocking with a latched communication request and accepted-event history. */
public final class Cdc6tInverterProtection {
    private final double pickupVoltage,communicationDelay,recoveryVoltage,recoveryDelay,minimumBlockTime;
    private final double[] accepted={0,Double.NaN,Double.NaN,Double.NaN,Double.NaN};
    private final double[] trial=new double[5];
    private double acceptedTime=Double.NEGATIVE_INFINITY;

    public Cdc6tInverterProtection(double pickupVoltage,double communicationDelay,
            double recoveryVoltage,double recoveryDelay,double minimumBlockTime) {
        nonnegative(pickupVoltage);nonnegative(communicationDelay);nonnegative(recoveryVoltage);
        nonnegative(recoveryDelay);nonnegative(minimumBlockTime);
        this.pickupVoltage=pickupVoltage;this.communicationDelay=communicationDelay;
        this.recoveryVoltage=recoveryVoltage;this.recoveryDelay=recoveryDelay;this.minimumBlockTime=minimumBlockTime;
    }
    /** Writes blocked flag, communication deadline, minimum-block deadline, recovery deadline and restart time.
     * NaN means inactive. Preview never changes committed history. */
    public void preview(double time,double acVoltage,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,5,output.length);
        finite(time);nonnegative(acVoltage);
        if(time<acceptedTime)throw new IllegalArgumentException("Protection cannot precede accepted time");
        double blocked=accepted[0],request=accepted[1],minimum=accepted[2],recovery=accepted[3],restart=accepted[4];
        if(blocked==0) {
            if(acVoltage<pickupVoltage&&Double.isNaN(request))request=deadline(time,communicationDelay);
            // A communicated low-voltage request survives recovery before its delivery time.
            if(!Double.isNaN(request)&&time>=request) {
                blocked=1;minimum=deadline(time,minimumBlockTime);recovery=Double.NaN;
            }
        } else {
            if(acVoltage>recoveryVoltage) {
                if(Double.isNaN(recovery))recovery=deadline(time,recoveryDelay);
                if(time>=minimum&&time>=recovery) {
                    blocked=0;request=Double.NaN;minimum=Double.NaN;recovery=Double.NaN;restart=time;
                }
            } else recovery=Double.NaN;
        }
        output[offset]=blocked;output[offset+1]=request;output[offset+2]=minimum;
        output[offset+3]=recovery;output[offset+4]=restart;
    }
    public void accept(double time,double acVoltage) {
        preview(time,acVoltage,trial,0);System.arraycopy(trial,0,accepted,0,5);acceptedTime=time;
    }
    public boolean isBlocked() {return accepted[0]!=0;}
    public double lastRestartTime() {return accepted[4];}
    public void readHistory(double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,5,output.length);System.arraycopy(accepted,0,output,offset,5);
    }
    private static double deadline(double time,double delay) {double result=time+delay;finite(result);return result;}
    private static void finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite protection input required");
    }
    private static void nonnegative(double value) {
        finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative protection input required");
    }
}
