package org.interpss.dstab.hvdc;

/** Allocation-free normal-operation command conversion; protection and recovery are separate owners. */
public final class Cdc6tCurrentOrder {
    public enum Mode { POWER, CURRENT }
    private final Mode mode;
    private final boolean rectifierMeasurement;
    private final double setpoint,scheduledVolts,compensationOhms,recoveryBase;

    /** Power setpoints use MW: positive at the rectifier, negative at the inverter. Current uses amperes. */
    public Cdc6tCurrentOrder(Mode mode,double signedSetpoint,double scheduledKilovolts,double compensationOhms) {
        this.mode=java.util.Objects.requireNonNull(mode);
        finite(signedSetpoint);finite(scheduledKilovolts);finite(compensationOhms);
        if(signedSetpoint==0||mode==Mode.CURRENT&&signedSetpoint<0||scheduledKilovolts<=0)
            throw new IllegalArgumentException("Nonzero power or positive current and positive voltage required");
        this.setpoint=Math.abs(signedSetpoint);rectifierMeasurement=signedSetpoint>0;
        scheduledVolts=scheduledKilovolts*1000;finite(scheduledVolts);
        this.compensationOhms=compensationOhms;
        recoveryBase=mode==Mode.CURRENT?setpoint:setpoint*1e6/scheduledVolts;
        finite(recoveryBase);
        if(recoveryBase<=0)throw new IllegalArgumentException("Positive recovery current base required");
    }
    /** Recovery, bypass and temporary current control use the source current order,
     * or absolute MW divided by scheduled kV.
     * It is independent of solved current, voltage measurements and the metering end. */
    public double recoveryBaseAmperes(){return recoveryBase;}
    /** Unconstrained desired current. Call only in normal operation; do not apply during blocked recovery. */
    public double amperes(double measuredInverterVolts,double measuredRectifierVolts,double auxiliaryMegawatts) {
        finite(measuredInverterVolts);finite(measuredRectifierVolts);finite(auxiliaryMegawatts);
        double voltage=rectifierMeasurement?measuredRectifierVolts:measuredInverterVolts;
        if(voltage<=0)throw new IllegalArgumentException("Positive measured command voltage required");
        double result=mode==Mode.POWER?(setpoint+auxiliaryMegawatts)*1e6/voltage
                :setpoint+auxiliaryMegawatts*1e6/voltage;
        finite(result);return result;
    }
    /** Apply after current limitation: scheduled volts minus the compensating resistive drop. */
    public double inverterVoltage(double limitedAmperes) {
        return inverterVoltage(limitedAmperes,1);
    }
    /** Recovery scales the scheduled voltage before the compensating resistive drop. */
    public double inverterVoltage(double limitedAmperes,double recoveryFactor) {
        finite(recoveryFactor);
        if(recoveryFactor<0||recoveryFactor>1)throw new IllegalArgumentException("Recovery factor must be in [0,1]");
        finite(limitedAmperes);double result=scheduledVolts*recoveryFactor-compensationOhms*limitedAmperes;
        finite(result);return result;
    }
    private static void finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite DC command required");
    }
}
