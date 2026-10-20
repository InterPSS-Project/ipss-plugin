package org.interpss.dstab.control.oel.psse.oel3c;

/** Published two-selector/ten-constant OEL3C data, without cross-tool corrections. */
public record Oel3cData(int inputFlag,int exponent,double pickup,double scale,double filterTime,
        double gain,double integralTime,double proportionalGain,double integralMax,double integralMin,
        double outputMax,double outputMin) {
    public Oel3cData {
        for(double x:new double[]{pickup,scale,filterTime,gain,integralTime,proportionalGain,
                integralMax,integralMin,outputMax,outputMin})
            if(!Double.isFinite(x))throw new IllegalArgumentException("OEL3C constants must be finite");
        if(exponent<=0 || pickup<0 || scale<0 || filterTime<0 || integralTime<=0)
            throw new IllegalArgumentException("OEL3C exponent, input scaling or time constants are invalid");
    }
    /** The native selector falls back to field voltage outside the documented three choices. */
    public int effectiveInputFlag(){return inputFlag==2||inputFlag==3?inputFlag:1;}
    /** The source specification explicitly swaps inverted bound pairs. Entered data stay intact. */
    public double lowerIntegral(){return Math.min(integralMin,integralMax);}
    public double upperIntegral(){return Math.max(integralMin,integralMax);}
    public double lowerOutput(){return Math.min(outputMin,outputMax);}
    public double upperOutput(){return Math.max(outputMin,outputMax);}
}
