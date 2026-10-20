package org.interpss.dstab.control.oel.psse.basoel2;

/** Exact three-selector/twelve-constant combined limiter data; no cross-tool corrections. */
public record Basoel2Data(int oelFlag,int vhzFlag,int inputFlag,double inputFilterTime,
        double proportionalGain,double integralGain,double shortLimit,double shortDelay,
        double mediumLimit,double mediumDelay,double continuousLimit,double continuousDelay,
        double speedFilterTime,double vhzGain,double minimumVhzInput) {
    public Basoel2Data {
        if(inputFlag<0||inputFlag>2)
            throw new IllegalArgumentException("BASOEL2 requires input selector 0/1/2");
        for(double value:new double[]{inputFilterTime,proportionalGain,integralGain,shortLimit,shortDelay,
                mediumLimit,mediumDelay,continuousLimit,continuousDelay,speedFilterTime,vhzGain,minimumVhzInput})
            if(!Double.isFinite(value))throw new IllegalArgumentException("BASOEL2 constants must be finite");
        if(inputFilterTime<0||speedFilterTime<0||shortDelay<0||mediumDelay<0||continuousDelay<0)
            throw new IllegalArgumentException("BASOEL2 time constants and delays must be nonnegative");
        if(!Double.isFinite(shortDelay+mediumDelay+continuousDelay))
            throw new IllegalArgumentException("BASOEL2 cumulative timer delay overflow");
    }
    public boolean oelEnabled(){return oelFlag!=0;}
    public boolean vhzEnabled(){return vhzFlag!=0;}
    public double[] thresholds(){return new double[]{shortLimit,mediumLimit,continuousLimit};}
    public double[] cumulativeDelays(){return new double[]{shortDelay,shortDelay+mediumDelay,shortDelay+mediumDelay+continuousDelay};}
}
