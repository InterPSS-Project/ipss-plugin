package org.interpss.dstab.renewable;

/** Complete 16-parameter PSS/E WTTQA1 (WECC WTTQ_A) record. */
public record Wttqa1Data(
        int tFlag, double kpp, double kip, double tp, double twref,
        double teMax, double teMin,
        double p1, double sp1, double p2, double sp2,
        double p3, double sp3, double p4, double sp4, double turbineMva) {
    public Wttqa1Data {
        if (tFlag < 0 || tFlag > 2) {
            throw new IllegalArgumentException("WTTQA1 TFLAG must be 0, 1 or the native-compatible value 2");
        }
        for(double value:new double[]{kpp,kip,tp,twref,teMax,teMin,p1,sp1,p2,sp2,p3,sp3,p4,sp4,turbineMva})
            if(!Double.isFinite(value))throw new IllegalArgumentException("WTTQA1 constants must be finite");
        if (tp < 0.0 || twref < 0.0) {
            throw new IllegalArgumentException("WTTQA1 time constants must be non-negative");
        }
        if (teMax < teMin) {
            throw new IllegalArgumentException("WTTQA1 Temax must be >= Temin");
        }
        boolean defaultCurve=p1==0&&sp1==0&&p2==0&&sp2==0&&p3==0&&sp3==0&&p4==0&&sp4==0;
        if(!defaultCurve&&p4<=p1)throw new IllegalArgumentException("WTTQA1 curve endpoints must increase");
        // Native records retain their point order, including collapsed or
        // decreasing intervals. The startup validator reports those inputs.
    }
    /** Native Model Library section 26.1: all-zero curve selects fixed Wref=1. */
    public boolean hasDefaultSpeedCurve() {
        return p1==0 && sp1==0 && p2==0 && sp2==0 && p3==0 && sp3==0 && p4==0 && sp4==0;
    }
    public boolean hasIncreasingPowerPoints(){return hasDefaultSpeedCurve()||p1<p2&&p2<p3&&p3<p4;}
    /** The independently observed selector 2 follows the nonzero power branch. */
    public boolean isPowerControl(){return tFlag!=0;}
}
