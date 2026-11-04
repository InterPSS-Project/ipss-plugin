package org.interpss.dstab.hvdc;

/** Immutable source constants prepared outside the integration loop.
 * Creates independently owned numerical runtimes; does not enable automatic attachment. */
public final class Cdc6tParameters {
    public static final int CONSTANT_COUNT=32;
    private final String linkName;
    private final double[] source;
    private final Cdc6tVoltageCurrentLimit ceiling;

    /** Solved static-network inputs: power setpoint in MW or current setpoint in A,
     * scheduled/threshold voltages in kV, compensation in ohms, solved boundary in V/A. */
    public record OperatingPoint(Cdc6tCurrentOrder.Mode mode,double signedSetpoint,double scheduledKilovolts,
            double compensationOhms,double modeThresholdKilovolts,double inverterVolts,double currentAmperes,double rectifierVolts){
        public OperatingPoint{
            java.util.Objects.requireNonNull(mode);
            for(double value:new double[]{signedSetpoint,scheduledKilovolts,compensationOhms,modeThresholdKilovolts,inverterVolts,currentAmperes,rectifierVolts})
                if(!Double.isFinite(value))throw new IllegalArgumentException("Finite static operating point required");
            if(scheduledKilovolts<=0||modeThresholdKilovolts<0||inverterVolts<=0||rectifierVolts<=0||currentAmperes<=0)
                throw new IllegalArgumentException("Positive solved DC boundary and nonnegative mode threshold required");
            new Cdc6tCurrentOrder(mode,signedSetpoint,scheduledKilovolts,compensationOhms);
        }
    }
    /** The caller owns both mutable objects exclusively for one link. */
    public record Prepared(Cdc6tNormalRuntime runtime,Cdc6tProtectionCoordinator protection) {}

    public Cdc6tParameters(String linkName,double[] constants){
        if(linkName==null||linkName.isBlank())throw new IllegalArgumentException("Named DC link required");
        java.util.Objects.requireNonNull(constants);
        if(constants.length!=CONSTANT_COUNT)throw new IllegalArgumentException("CDC6T requires exactly 32 constants");
        this.linkName=linkName;source=constants.clone();
        for(int i=0;i<source.length;i++)if(!Double.isFinite(source[i]))throw new IllegalArgumentException("Nonfinite CDC6T constant "+(i+1));
        for(int i:new int[]{0,1})if(source[i]<0||source[i]>=90)throw new IllegalArgumentException("Conducting angle must be in [0,90) degrees");
        new Cdc6tMeasurements(source[2],source[3],source[31]);
        ceiling=Cdc6tVoltageCurrentLimit.fromSourcePoints(source[15],source[16],source[17],source[18],source[19],source[20]);
        if(source[14]<0||source[14]>=source[16])throw new IllegalArgumentException("C0 must be nonnegative and strictly below C1");
        // Validate all context-independent protection coefficients before accepting a source record.
        rectifier();inverter();bypass();new Cdc6tModeSwitch(true,0,source[21]);
        new Cdc6tRecoveryRamp(source[10]*1000,1,source[11],1,source[12],source[13]);
    }
    public String linkName(){return linkName;}
    /** Preserve original units and sentinel values; callers cannot mutate prepared coefficients. */
    public double[] copySourceConstants(){return source.clone();}
    /** A binding initial ceiling is a physical startup transfer from the static solution. */
    boolean initiallyLimitsCurrent(OperatingPoint point){
        return ceiling.ceilingAmperes(point.inverterVolts())<point.currentAmperes();
    }
    /** Assemble the qualified fixed-tap, ordinary dynamic-angle path. Static-network
     * ownership, constant-gamma controls and other converter variants are not inferred here. */
    public Prepared prepare(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,double lineOhms,OperatingPoint point){
        return prepare(rectifier,inverter,lineOhms,point,Math.toRadians(source[1]),Cdc6tConductingLink.InverterControl.SCHEDULED_VOLTAGE,0);
    }
    /** Equal static inverter angle limits select gamma control and supersede the dynamic gamma minimum. */
    public Prepared prepareConstantGamma(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,
            double lineOhms,OperatingPoint point,double staticGammaDegrees){
        return prepareConstantGamma(rectifier,inverter,lineOhms,point,staticGammaDegrees,0);
    }
    /** Constant-gamma current-margin transfer is prepared from the static owner. */
    public Prepared prepareConstantGamma(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,
            double lineOhms,OperatingPoint point,double staticGammaDegrees,double currentMargin){
        return prepare(rectifier,inverter,lineOhms,point,Math.toRadians(staticGammaDegrees),Cdc6tConductingLink.InverterControl.CONSTANT_GAMMA,currentMargin);
    }
    private Prepared prepare(Cdc6tConverterBoundary rectifier,Cdc6tConverterBoundary inverter,double lineOhms,
            OperatingPoint point,double gammaRadians,Cdc6tConductingLink.InverterControl inverterControl,double currentMargin){
        java.util.Objects.requireNonNull(point);
        var command=new Cdc6tCurrentOrder(point.mode(),point.signedSetpoint(),point.scheduledKilovolts(),point.compensationOhms());
        var link=new Cdc6tConductingLink(rectifier,inverter,lineOhms,Math.toRadians(source[0]),gammaRadians,inverterControl,currentMargin);
        var runtime=new Cdc6tNormalRuntime(new Cdc6tMeasurements(source[2],source[3],source[31]),command,ceiling,link,source[14]);
        runtime.initialize(point.inverterVolts(),point.currentAmperes(),point.rectifierVolts());
        var protection=new Cdc6tProtectionCoordinator(rectifier(),inverter(),bypass(),
                new Cdc6tModeSwitch(point.mode()==Cdc6tCurrentOrder.Mode.POWER,point.modeThresholdKilovolts()*1000,source[21]),
                new Cdc6tRecoveryRamp(source[10]*1000,point.scheduledKilovolts()*1000,source[11],command.recoveryBaseAmperes(),source[12],source[13]),
                Cdc6tProtectionCoordinator.TimerSampling.BLOCK_INTERVAL_MIDPOINT);
        return new Prepared(runtime,protection);
    }
    private Cdc6tRectifierProtection rectifier(){
        return Cdc6tRectifierProtection.fromSource(source[4],source[22],source[23],source[5],source[24],source[6]);
    }
    private Cdc6tInverterProtection inverter(){return new Cdc6tInverterProtection(source[25],source[26],source[8],source[29],source[6]);}
    private Cdc6tBypassProtection bypass(){return new Cdc6tBypassProtection(source[7]*1000,source[27],source[28],source[8],source[30],source[9]);}
}
