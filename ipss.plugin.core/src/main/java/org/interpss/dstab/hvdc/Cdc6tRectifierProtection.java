package org.interpss.dstab.hvdc;

/** Rectifier-only blocking history. Network trials must use preview; only accepted events commit. */
public final class Cdc6tRectifierProtection {
    private final double instantVoltage,delayedVoltage,blockDelay,recoveryVoltage,recoveryDelay,minimumBlockTime;
    private final double[] accepted={0,Double.NaN,Double.NaN,Double.NaN,Double.NaN};
    private final double[] trial=new double[5];
    private double acceptedTime=Double.NEGATIVE_INFINITY;

    /** Prepare the source record's legacy -999 recovery-delay sentinel as zero additional delay.
     * Minimum blocking time and the strict recovery-voltage condition remain active.
     * Other negative delay values are not inferred from this sentinel. */
    public static Cdc6tRectifierProtection fromSource(double instantVoltage,double delayedVoltage,double blockDelay,
            double recoveryVoltage,double recoveryDelay,double minimumBlockTime){
        return new Cdc6tRectifierProtection(instantVoltage,delayedVoltage,blockDelay,recoveryVoltage,
                recoveryDelay==-999?0:recoveryDelay,minimumBlockTime);
    }

    /** Delays are seconds; explicit nonnegative delays are required by this qualified component. */
    public Cdc6tRectifierProtection(double instantVoltage,double delayedVoltage,double blockDelay,
            double recoveryVoltage,double recoveryDelay,double minimumBlockTime) {
        finite(instantVoltage);finite(delayedVoltage);finite(recoveryVoltage);
        nonnegative(blockDelay);nonnegative(recoveryDelay);nonnegative(minimumBlockTime);
        this.instantVoltage=instantVoltage;this.delayedVoltage=delayedVoltage;this.blockDelay=blockDelay;
        this.recoveryVoltage=recoveryVoltage;this.recoveryDelay=recoveryDelay;this.minimumBlockTime=minimumBlockTime;
    }

    /** Writes blocked flag, pickup deadline, minimum-block deadline, recovery deadline and last restart time.
     * NaN denotes an inactive deadline. No internal history changes, even on repeated or rejected trials. */
    public void preview(double time,double acVoltage,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,5,output.length);
        finite(time);nonnegative(acVoltage);
        if(time<acceptedTime)throw new IllegalArgumentException("Protection cannot precede accepted time");
        double blocked=accepted[0],pickup=accepted[1],minimum=accepted[2],recovery=accepted[3],restart=accepted[4];
        if(blocked==0) {
            if(acVoltage<delayedVoltage) {
                if(Double.isNaN(pickup))pickup=deadline(time,blockDelay);
            } else pickup=Double.NaN;
            if(acVoltage<instantVoltage||!Double.isNaN(pickup)&&time>=pickup) {
                blocked=1;minimum=deadline(time,minimumBlockTime);pickup=Double.NaN;recovery=Double.NaN;
            }
        } else {
            if(acVoltage>recoveryVoltage) {
                if(Double.isNaN(recovery))recovery=deadline(time,recoveryDelay);
                if(time>=minimum&&time>=recovery) {
                    blocked=0;minimum=Double.NaN;recovery=Double.NaN;restart=time;
                }
            } else recovery=Double.NaN;
        }
        output[offset]=blocked;output[offset+1]=pickup;output[offset+2]=minimum;
        output[offset+3]=recovery;output[offset+4]=restart;
    }

    /** Accept one electrical/event observation; a second observation at the same time may resolve a discontinuity. */
    public void accept(double time,double acVoltage) {
        preview(time,acVoltage,trial,0);
        System.arraycopy(trial,0,accepted,0,5);acceptedTime=time;
    }
    public boolean isBlocked() {return accepted[0]!=0;}
    Cdc6tRectifierProtection workingCopy() {
        var copy=new Cdc6tRectifierProtection(instantVoltage,delayedVoltage,blockDelay,recoveryVoltage,recoveryDelay,minimumBlockTime);
        copy.copyHistoryFrom(this);return copy;
    }
    void copyHistoryFrom(Cdc6tRectifierProtection source){System.arraycopy(source.accepted,0,accepted,0,5);acceptedTime=source.acceptedTime;}
    void resetHistory(){java.util.Arrays.fill(accepted,Double.NaN);accepted[0]=0;acceptedTime=Double.NEGATIVE_INFINITY;}
    public double lastRestartTime() {return accepted[4];}
    public void readHistory(double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,5,output.length);
        System.arraycopy(accepted,0,output,offset,5);
    }
    private static double deadline(double time,double delay) {
        double result=time+delay;finite(result);return result;
    }
    private static void finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite protection input required");
    }
    private static void nonnegative(double value) {
        finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative protection input required");
    }
}
