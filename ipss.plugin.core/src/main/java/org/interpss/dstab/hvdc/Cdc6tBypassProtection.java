package org.interpss.dstab.hvdc;

/** Inverter bypass history, separate from blocking and from trial electrical evaluation. */
public final class Cdc6tBypassProtection {
    private final double dcThreshold,acThreshold,pickupDelay,recoveryVoltage,recoveryDelay,minimumDuration;
    private final double[] accepted={0,Double.NaN,Double.NaN,Double.NaN,Double.NaN},trial=new double[5];
    private double acceptedTime=Double.NEGATIVE_INFINITY;

    /** DC threshold is volts; AC thresholds are per unit and delays are seconds. */
    public Cdc6tBypassProtection(double dcThreshold,double acThreshold,double pickupDelay,
            double recoveryVoltage,double recoveryDelay,double minimumDuration) {
        finite(dcThreshold);finite(acThreshold);finite(recoveryVoltage);
        nonnegative(pickupDelay);nonnegative(recoveryDelay);nonnegative(minimumDuration);
        this.dcThreshold=dcThreshold;this.acThreshold=acThreshold;this.pickupDelay=pickupDelay;
        this.recoveryVoltage=recoveryVoltage;this.recoveryDelay=recoveryDelay;this.minimumDuration=minimumDuration;
    }
    /** Writes bypass flag, AC pickup deadline, minimum duration deadline, recovery deadline and unbypass time. */
    public void preview(double time,double inverterAcPu,double inverterDcVolts,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,5,output.length);
        finite(time);nonnegative(inverterAcPu);nonnegative(inverterDcVolts);
        if(time<acceptedTime)throw new IllegalArgumentException("Protection cannot precede accepted time");
        double bypass=accepted[0],pickup=accepted[1],minimum=accepted[2],recovery=accepted[3],restart=accepted[4];
        if(bypass==0) {
            if(inverterAcPu<acThreshold) {
                if(Double.isNaN(pickup))pickup=deadline(time,pickupDelay);
            } else pickup=Double.NaN;
            if(inverterDcVolts<dcThreshold||!Double.isNaN(pickup)&&time>=pickup) {
                bypass=1;pickup=Double.NaN;minimum=deadline(time,minimumDuration);recovery=Double.NaN;
            }
        } else {
            if(inverterAcPu>recoveryVoltage) {
                if(Double.isNaN(recovery))recovery=deadline(time,recoveryDelay);
                if(time>=minimum&&time>=recovery) {
                    bypass=0;minimum=Double.NaN;recovery=Double.NaN;restart=time;
                }
            } else recovery=Double.NaN;
        }
        output[offset]=bypass;output[offset+1]=pickup;output[offset+2]=minimum;
        output[offset+3]=recovery;output[offset+4]=restart;
    }
    public void accept(double time,double inverterAcPu,double inverterDcVolts) {
        preview(time,inverterAcPu,inverterDcVolts,trial,0);
        System.arraycopy(trial,0,accepted,0,5);acceptedTime=time;
    }
    public boolean isBypassed() {return accepted[0]!=0;}
    public double lastUnbypassTime() {return accepted[4];}
    public void readHistory(double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,5,output.length);System.arraycopy(accepted,0,output,offset,5);
    }
    private static double deadline(double time,double delay) {double result=time+delay;finite(result);return result;}
    private static void finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite bypass input required");
    }
    private static void nonnegative(double value) {
        finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative bypass input required");
    }
}
