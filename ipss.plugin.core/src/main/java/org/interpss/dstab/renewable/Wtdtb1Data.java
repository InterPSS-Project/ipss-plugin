package org.interpss.dstab.renewable;

/** Native PSS/E 36.7 WTDTB1 six-CON layout; Freq1 is Hz, not WTDTA1's exchange convention. */
public record Wtdtb1Data(double h,double damp,double htfrac,double freq1,double dshaft,double tp) {
    public Wtdtb1Data {
        for(double value:new double[]{h,damp,htfrac,freq1,dshaft,tp})
            if(!Double.isFinite(value))throw new IllegalArgumentException("WTDTB1 parameters must be finite");
        if(h<=0||htfrac<0||htfrac>=1||dshaft<0||tp<0)
            throw new IllegalArgumentException("WTDTB1 requires H>0, 0<=Htfrac<1, Dshaft>=0 and Tp>=0");
        if(htfrac>0&&freq1<=0)
            throw new IllegalArgumentException("WTDTB1 two-mass Freq1 must be positive (Hz)");
    }
}
