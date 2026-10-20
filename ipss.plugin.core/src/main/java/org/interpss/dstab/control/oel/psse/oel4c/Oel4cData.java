package org.interpss.dstab.control.oel.psse.oel4c;

/** Five-constant documented OEL4C contract; zero pickup requests the owner's reactive limit. */
public record Oel4cData(double reactivePickup,double delay,double proportionalGain,
        double integralGain,double minimumOutput) {
    public Oel4cData {
        for(double value:new double[]{reactivePickup,delay,proportionalGain,integralGain,minimumOutput})
            if(!Double.isFinite(value))throw new IllegalArgumentException("OEL4C constants must be finite");
        if(reactivePickup<0||delay<0||integralGain<0||minimumOutput>0)
            throw new IllegalArgumentException("OEL4C requires nonnegative pickup/delay/integral gain and nonpositive lower output");
        if(proportionalGain==0&&integralGain==0)
            throw new IllegalArgumentException("OEL4C needs a nonzero PI gain; automatic gain correction is not applied");
    }
    public double effectivePickup(double reactiveMaximum) {
        if(reactivePickup!=0)return reactivePickup;
        if(!Double.isFinite(reactiveMaximum)||reactiveMaximum<0)
            throw new IllegalArgumentException("OEL4C zero pickup requires a finite nonnegative machine-base reactive maximum");
        return reactiveMaximum;
    }
}
