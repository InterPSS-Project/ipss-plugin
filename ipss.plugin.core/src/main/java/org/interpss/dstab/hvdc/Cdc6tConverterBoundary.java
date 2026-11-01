package org.interpss.dstab.hvdc;

/** Prepared non-capacitor-commutated LCC terminal equations, independent of controller integration. */
public final class Cdc6tConverterBoundary {
    private final double valveVoltsPerPu,noLoadDcVoltsPerPu,commutatingOhms,reactanceOhms,baseVA,powerSign;
    public Cdc6tConverterBoundary(boolean rectifier,int bridges,double acBaseVolts,double transformerRatio,
            double tap,double reactanceOhms,double networkBaseVA) {
        positive(acBaseVolts);positive(transformerRatio);positive(tap);positive(networkBaseVA);
        finite(reactanceOhms);
        if(bridges<1||reactanceOhms<0)throw new IllegalArgumentException("Positive bridge count and nonnegative reactance required");
        valveVoltsPerPu=acBaseVolts*transformerRatio/tap;positive(valveVoltsPerPu);
        noLoadDcVoltsPerPu=3*Math.sqrt(2)/Math.PI*bridges*valveVoltsPerPu;positive(noLoadDcVoltsPerPu);
        commutatingOhms=3/Math.PI*bridges*reactanceOhms;finite(commutatingOhms);
        this.reactanceOhms=reactanceOhms;baseVA=networkBaseVA;powerSign=rectifier?1:-1;
    }
    /** Writes P into converter, Q into converter (both pu), and firing/extinction angle (radians).
     * Input DC voltage is positive at each terminal. Controllers must enforce feasible orders first.
     * Invalid trials leave the caller's output buffer unchanged. */
    public void evaluate(double acVoltagePu,double dcVolts,double amperes,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,3,output.length);
        positive(acVoltagePu);finite(dcVolts);finite(amperes);
        if(dcVolts<0||amperes<0)throw new IllegalArgumentException("Nonnegative conducting DC boundary required");
        double valveVolts=valveVoltsPerPu*acVoltagePu;
        positive(valveVolts);double noLoadVolts=noLoadDcVoltsPerPu*acVoltagePu;positive(noLoadVolts);
        double cosine=(dcVolts+commutatingOhms*amperes)/noLoadVolts;
        double angle=Math.acos(unit(cosine));
        double overlapEnd=Math.acos(unit(Math.cos(angle)-Math.sqrt(2)*amperes*reactanceOhms/valveVolts));
        double overlap=overlapEnd-angle;
        // Cancel the vanishing power-factor cosine analytically. P*tan(phi) is
        // indeterminate at zero DC voltage with nonzero commutating current.
        double halfSum=.5*(angle+overlapEnd),sine=Math.sin(halfSum);
        double square=overlap*overlap;
        double inverseSinc=Math.abs(overlap)<1e-4?1+square/6+7*square*square/360:overlap/Math.sin(overlap);
        double power=dcVolts*amperes/baseVA;
        double reactive=amperes==0||sine==0?0:noLoadVolts*amperes/baseVA*Math.cos(.5*overlap)
                *(inverseSinc-Math.cos(angle+overlapEnd))/(2*sine);
        finite(power);finite(reactive);finite(angle);
        output[offset]=powerSign*power;output[offset+1]=reactive;output[offset+2]=angle;
    }
    private static double unit(double value) {
        finite(value);
        if(value< -1-1e-12||value>1+1e-12)throw new IllegalArgumentException("Infeasible converter voltage/current or overlap");
        return Math.max(-1,Math.min(1,value));
    }
    private static void positive(double value){finite(value);if(value<=0)throw new IllegalArgumentException("Positive converter coefficient required");}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite converter value required");}
}
