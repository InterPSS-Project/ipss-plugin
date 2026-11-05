package org.interpss.dstab.hvdc;

/** Low-DC-voltage transfer from power control to scheduled current, with accepted-event ownership. */
public final class Cdc6tModeSwitch {
    private final boolean powerControl;
    private final double thresholdVolts,minimumDuration;
    private final double[] accepted={0,Double.NaN},trial=new double[2];
    private double acceptedTime=Double.NEGATIVE_INFINITY;
    public Cdc6tModeSwitch(boolean powerControl,double thresholdVolts,double minimumDuration) {
        nonnegative(thresholdVolts);nonnegative(minimumDuration);
        this.powerControl=powerControl;this.thresholdVolts=thresholdVolts;this.minimumDuration=minimumDuration;
    }
    /** Writes switched flag and earliest return time. Block/bypass inhibits mode transitions. */
    public void preview(double time,double inverterDcVolts,boolean blocked,boolean bypassed,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,2,output.length);finite(time);nonnegative(inverterDcVolts);
        if(time<acceptedTime)throw new IllegalArgumentException("Mode switch cannot precede accepted time");
        double switched=accepted[0],deadline=accepted[1];
        if(powerControl&&!blocked&&!bypassed) {
            if(switched==0&&inverterDcVolts<thresholdVolts) {
                switched=1;deadline=time+minimumDuration;finite(deadline);
            } else if(switched!=0&&inverterDcVolts>thresholdVolts&&time>=deadline) {
                switched=0;deadline=Double.NaN;
            }
        }
        output[offset]=switched;output[offset+1]=deadline;
    }
    public void accept(double time,double inverterDcVolts,boolean blocked,boolean bypassed) {
        preview(time,inverterDcVolts,blocked,bypassed,trial,0);
        System.arraycopy(trial,0,accepted,0,2);acceptedTime=time;
    }
    public boolean isSwitched() {return accepted[0]!=0;}
    Cdc6tModeSwitch workingCopy() {
        var copy=new Cdc6tModeSwitch(powerControl,thresholdVolts,minimumDuration);
        copy.copyHistoryFrom(this);return copy;
    }
    void copyHistoryFrom(Cdc6tModeSwitch source){System.arraycopy(source.accepted,0,accepted,0,2);acceptedTime=source.acceptedTime;}
    void resetHistory(){accepted[0]=0;accepted[1]=Double.NaN;acceptedTime=Double.NEGATIVE_INFINITY;}
    public void readHistory(double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,2,output.length);System.arraycopy(accepted,0,output,offset,2);
    }
    private static void finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite mode-switch input required");
    }
    private static void nonnegative(double value) {
        finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative mode-switch input required");
    }
}
