package org.interpss.dstab.hvdc;

/** Prepared non-capacitor-commutated LCC terminal equations, independent of controller integration. */
public final class LccConverterBoundary {
    private final double valveVoltsPerPu,noLoadDcVoltsPerPu,commutatingOhms,reactanceOhms,baseVA,powerSign,lossOhms,magnitudeDropOhms;
    double commutatingResistanceOhms(){return commutatingOhms;}
    boolean isRectifier(){return powerSign>0;}
    /** Signed terminal DC voltage before any externally owned protection short. */
    public double dcVoltageAtFiringAngle(double acVoltagePu,double amperes,double angleRadians) {
        firingInputs(acVoltagePu,amperes,angleRadians);
        double value=powerSign*(noLoadDcVoltsPerPu*acVoltagePu*Math.cos(angleRadians)-commutatingOhms*amperes);
        finite(value);return value;
    }
    /** Recover the firing angle from a solved signed terminal voltage, without changing equipment. */
    public double firingAngleForDcVoltage(double acVoltagePu,double dcVolts,double amperes) {
        positive(acVoltagePu);finite(dcVolts);finite(amperes);
        if(amperes<0)throw new IllegalArgumentException("Nonnegative converter current required");
        return Math.acos(unit((powerSign*dcVolts+commutatingOhms*amperes)/(noLoadDcVoltsPerPu*acVoltagePu)));
    }
    /** Controller angle is an observation coordinate of positive terminal DC magnitude.
     * At the inverter its winding drop has the opposite sign from the signed
     * electrical-voltage equation; zero resistance and rectifier coordinates coincide. */
    public double controllerAngleForDcVoltage(double acVoltagePu,double dcVolts,double amperes) {
        positive(acVoltagePu);finite(dcVolts);finite(amperes);
        if(amperes<0)throw new IllegalArgumentException("Nonnegative converter current required");
        return Math.acos(unit((powerSign*dcVolts+magnitudeDropOhms*amperes)/(noLoadDcVoltsPerPu*acVoltagePu)));
    }
    /** Translate a controller angle back to the electrical coordinate at the
     * present voltage/current without changing the physical converter equation. */
    public double electricalAngleForController(double acVoltagePu,double amperes,double controllerAngleRadians) {
        firingInputs(acVoltagePu,amperes,controllerAngleRadians);
        if(commutatingOhms==magnitudeDropOhms)return controllerAngleRadians;
        return Math.acos(unit(Math.cos(controllerAngleRadians)
                +(commutatingOhms-magnitudeDropOhms)*amperes/(noLoadDcVoltsPerPu*acVoltagePu)));
    }
    /** Fresh extinction margin for protection. Loss of a feasible commutation margin
     * returns zero; this does not replace the terminal by a short or define its AC power. */
    public double extinctionAtFiringAngle(double acVoltagePu,double amperes,double angleRadians) {
        firingInputs(acVoltagePu,amperes,angleRadians);
        double cosine=-Math.cos(angleRadians)+Math.sqrt(2)*amperes*reactanceOhms/(valveVoltsPerPu*acVoltagePu);
        finite(cosine);return Math.acos(Math.max(-1,Math.min(1,cosine)));
    }
    private static void firingInputs(double voltage,double current,double angle) {
        positive(voltage);finite(current);finite(angle);
        if(current<0||angle<0||angle>Math.PI)throw new IllegalArgumentException("Nonnegative current and a firing angle in [0,pi] required");
    }
    public LccConverterBoundary(boolean rectifier,int bridges,double acBaseVolts,double transformerRatio,
            double tap,double reactanceOhms,double networkBaseVA) {
        this(rectifier,bridges,acBaseVolts,transformerRatio,tap,0,reactanceOhms,networkBaseVA);
    }
    /** Resistance and reactance are ohms per bridge; converter losses are 2*N*R*I squared. */
    public LccConverterBoundary(boolean rectifier,int bridges,double acBaseVolts,double transformerRatio,
            double tap,double resistanceOhms,double reactanceOhms,double networkBaseVA) {
        positive(acBaseVolts);positive(transformerRatio);positive(tap);positive(networkBaseVA);
        finite(resistanceOhms);
        if(resistanceOhms<0)throw new IllegalArgumentException("Nonnegative converter resistance required");
        finite(reactanceOhms);
        if(bridges<1||reactanceOhms<0)throw new IllegalArgumentException("Positive bridge count and nonnegative reactance required");
        valveVoltsPerPu=acBaseVolts*transformerRatio/tap;positive(valveVoltsPerPu);
        noLoadDcVoltsPerPu=3*Math.sqrt(2)/Math.PI*bridges*valveVoltsPerPu;positive(noLoadDcVoltsPerPu);
        double overlapOhms=3/Math.PI*bridges*reactanceOhms;finite(overlapOhms);
        lossOhms=2.0*bridges*resistanceOhms;finite(lossOhms);
        this.reactanceOhms=reactanceOhms;baseVA=networkBaseVA;powerSign=rectifier?1:-1;
        commutatingOhms=lossOhms==0?overlapOhms:overlapOhms+lossOhms;
        magnitudeDropOhms=lossOhms==0?overlapOhms:overlapOhms+powerSign*lossOhms;
        finite(commutatingOhms);finite(magnitudeDropOhms);
    }
    /** Maximum conducting DC voltage at the supplied minimum angle, with taps held fixed. */
    public double maximumDcVoltage(double acVoltagePu,double amperes,double minimumAngleRadians) {
        positive(acVoltagePu);finite(amperes);finite(minimumAngleRadians);
        if(amperes<0||minimumAngleRadians<0||minimumAngleRadians>=Math.PI/2)
            throw new IllegalArgumentException("Nonnegative current and an angle below pi/2 required");
        double result=noLoadDcVoltsPerPu*acVoltagePu*Math.cos(minimumAngleRadians)-magnitudeDropOhms*amperes;
        finite(result);return result;
    }
    /** Writes P into converter, Q into converter (both pu), and firing/extinction angle (radians).
     * Input DC voltage is positive at each terminal. Controllers must enforce feasible orders first.
     * Invalid trials leave the caller's output buffer unchanged. */
    public void evaluateDcVoltage(double acVoltagePu,double dcVolts,double amperes,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,3,output.length);
        positive(acVoltagePu);finite(dcVolts);finite(amperes);
        if(dcVolts<0||amperes<0)throw new IllegalArgumentException("Nonnegative conducting DC boundary required");
        double valveVolts=valveVoltsPerPu*acVoltagePu;
        positive(valveVolts);double noLoadVolts=noLoadDcVoltsPerPu*acVoltagePu;positive(noLoadVolts);
        double cosine=(dcVolts+magnitudeDropOhms*amperes)/noLoadVolts;
        double angle=Math.acos(unit(cosine));
        double overlapEnd=Math.acos(unit(Math.cos(angle)-Math.sqrt(2)*amperes*reactanceOhms/valveVolts));
        double power=dcVolts*amperes/baseVA;
        double reactive=reactivePower(noLoadVolts,amperes,angle,overlapEnd);
        finite(power);finite(reactive);finite(angle);
        double acPower=lossOhms==0?powerSign*power:powerSign*power+lossOhms*amperes*amperes/baseVA;
        finite(acPower);
        output[offset]=acPower;output[offset+1]=reactive;output[offset+2]=angle;
    }
    /** Firing-angle interface at either terminal. Writes signed DC volts, AC P/Q in pu,
     * then extinction angle in radians. Current is positive from rectifier to inverter;
     * AC P/Q are positive into the converter. Signed voltage permits rectifier inversion.
     * Infeasible overlap is rejected, not replaced by a protected/shorted circuit.
     * Invalid trials leave the caller's buffer unchanged; inputs are live on every call. */
    public void evaluateFiringAngle(double acVoltagePu,double amperes,double firingAngleRadians,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,4,output.length);
        positive(acVoltagePu);finite(amperes);finite(firingAngleRadians);
        if(amperes<0||firingAngleRadians<0||firingAngleRadians>Math.PI)
            throw new IllegalArgumentException("Nonnegative current and a firing angle in [0,pi] required");
        double valveVolts=valveVoltsPerPu*acVoltagePu;
        positive(valveVolts);double noLoadVolts=noLoadDcVoltsPerPu*acVoltagePu;positive(noLoadVolts);
        double cosine=Math.cos(firingAngleRadians);
        double overlapEnd=Math.acos(unit(cosine-Math.sqrt(2)*amperes*reactanceOhms/valveVolts));
        double signedVolts=noLoadVolts*cosine-commutatingOhms*amperes;
        double power=signedVolts*amperes/baseVA;
        if(lossOhms!=0)power+=lossOhms*amperes*amperes/baseVA;
        double reactive=reactivePower(noLoadVolts,amperes,firingAngleRadians,overlapEnd);
        double extinction=Math.PI-overlapEnd;
        finite(signedVolts);finite(power);finite(reactive);finite(extinction);
        output[offset]=powerSign*signedVolts;output[offset+1]=power;output[offset+2]=reactive;output[offset+3]=extinction;
    }
    private double reactivePower(double noLoadVolts,double amperes,double angle,double overlapEnd) {
        double overlap=overlapEnd-angle,halfSum=.5*(angle+overlapEnd),sine=Math.sin(halfSum);
        double square=overlap*overlap;
        double inverseSinc=Math.abs(overlap)<1e-4?1+square/6+7*square*square/360:overlap/Math.sin(overlap);
        // Analytic cancellation avoids P*tan(phi)'s singularity at zero DC power.
        return amperes==0||sine==0?0:noLoadVolts*amperes/baseVA*Math.cos(.5*overlap)
                *(inverseSinc-Math.cos(angle+overlapEnd))/(2*sine);
    }
    private static double unit(double value) {
        finite(value);
        if(value< -1-1e-12||value>1+1e-12)throw new IllegalArgumentException("Infeasible converter voltage/current or overlap");
        return Math.max(-1,Math.min(1,value));
    }
    private static void positive(double value){finite(value);if(value<=0)throw new IllegalArgumentException("Positive converter coefficient required");}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite converter value required");}
}
