package org.interpss.dstab.relay;

/** Five-point inverse-time curve on the system current base. */
public final class Tiocr1Data {
    private final double[] c;
    public Tiocr1Data(double[] constants) {
        if (constants == null || constants.length != 14)
            throw new IllegalArgumentException("TIOCR1 requires fourteen constants");
        c = constants.clone();
        for (double v : c) if (!Double.isFinite(v)) throw new IllegalArgumentException("Nonfinite relay constant");
        if (c[0] <= 0 || c[1] < 0 || c[12] < 0 || c[13] < 0 || c[13] > 1)
            throw new IllegalArgumentException("Invalid relay threshold, reset, breaker or load fraction");
        for (int i = 0; i < 5; i++) {
            if (c[2+2*i] < 1 || c[3+2*i] <= 0 || (i>0 && c[2+2*i] <= c[2*i]))
                throw new IllegalArgumentException("Relay curve currents must increase and times must be positive");
        }
    }
    public double threshold() { return c[0]; }
    public double resetTime() { return c[1]; }
    public double breakerTime() { return c[12]; }
    public double loadFraction() { return c[13]; }
    public double timeToClose(double current) {
        if (!Double.isFinite(current) || current < 0) throw new IllegalArgumentException("Invalid relay current");
        double m = current/c[0];
        if (m < 1) return Double.POSITIVE_INFINITY;
        double lastM=1, lastT=3600;
        for (int i=0;i<5;i++) {
            double nextM=c[2+2*i],nextT=c[3+2*i];
            if (m<=nextM) return nextM==lastM?nextT:lastT+(m-lastM)*(nextT-lastT)/(nextM-lastM);
            lastM=nextM;lastT=nextT;
        }
        return lastT;
    }
    public double rate(double current) {
        if (!Double.isFinite(current) || current < 0) throw new IllegalArgumentException("Invalid relay current");
        if (current >= c[0]) return 1/timeToClose(current);
        return c[1] == 0 ? Double.NEGATIVE_INFINITY : -(1-Math.pow(current/c[0],2))/c[1];
    }
}
