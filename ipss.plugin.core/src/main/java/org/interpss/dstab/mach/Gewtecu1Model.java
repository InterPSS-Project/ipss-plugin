package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Published eighteen-state electrical controller for the GE wind-converter host. */
public final class Gewtecu1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Gewtecu1Data data;
    private final Gewtgcu1Model generator;
    private State state;
    private State oldState;
    private Derivative predictor;
    private double voltageReference;
    private double reactiveReference;
    private double powerReference;
    private double powerFactorAngle;
    private double rotorSpeed;
    private double windVelocity;
    private double availablePower;
    private double systemFrequency = 1.0;
    private double auxiliarySignal;
    private double externalActiveSignal;
    private double qDroopInput;
    private double measuredMachineP = Double.NaN;
    private double measuredMachineQ = Double.NaN;
    private boolean initialized;
    private boolean tripped;

    public Gewtecu1Model(Gewtecu1Data data, Gewtgcu1Model generator) {
        this.data = data;
        this.generator = generator;
        windVelocity = data.initialWindSpeed();
    }

    public boolean initialize() {
        BaseDStabBus<?, ?> bus = generator.getDStabBus();
        if (bus == null) return false;
        double voltage = bus.getVoltageMag();
        if (voltage <= EPS) return false;
        double machineP = machineP();
        double p = toTurbineBase(machineP);
        double q = machineQ();
        qDroopInput = q;
        double currentSquared = currentSquared(voltage, machineP, q);
        double droop = reactiveDroopTarget(currentSquared);
        voltageReference = voltage + currentSquared * data.lineDropReactance()
                + data.reactiveDroopGain() * droop;
        reactiveReference = q;
        powerReference = p;
        powerFactorAngle = Math.atan2(q, machineP);
        rotorSpeed = speedReference(p);
        availablePower = p / Math.max(EPS, data.onlineFraction());
        double reactiveCommand = generator.getReactiveState();
        state = new State(q, 0.0, p, p / Math.max(EPS, 1.0 + rotorSpeed),
                voltage, rotorSpeed,
                voltage, reactiveCommand, q, machineP, availablePower, 0.0,
                voltage, 0.0, 0.0, droop, 0.0, 0.0);
        initialized = true;
        tripped = outsideOperatingEnvelope();
        updateCommands();
        return finiteState(state);
    }

    public boolean step(double dt, int flag) {
        if (!initialized || dt <= 0.0 || (flag != 0 && flag != 1)) return false;
        if (flag == 0) {
            oldState = state;
            predictor = derivatives(state);
            state = advance(oldState, predictor, dt);
        } else {
            if (oldState == null || predictor == null) return false;
            Derivative corrector = derivatives(state);
            state = advance(oldState, predictor.add(corrector), 0.5 * dt);
            oldState = null;
            predictor = null;
        }
        applyStateLimits();
        applyAlgebraicBypasses();
        tripped |= outsideOperatingEnvelope();
        updateCommands();
        return finiteState(state);
    }

    private Derivative derivatives(State s) {
        double terminalVoltage = Math.max(EPS,
                measuredVoltage(generator.getDStabBus().getVoltageMag()));
        double machineP = machineP();
        double p = toTurbineBase(machineP);
        double q = machineQ();
        double currentSquared = currentSquared(terminalVoltage, machineP, q);
        double sensedVoltage = algebraicOrState(terminalVoltage, s.voltageSensor,
                data.voltageSensorTime());
        double droopInput = reactiveDroopTarget(currentSquared);
        double droopSignal = algebraicOrState(droopInput, s.reactiveDroopFilter,
                data.reactiveDroopFilterTime());
        double voltageError = clamp(voltageReference - sensedVoltage
                        - currentSquared * data.lineDropReactance()
                        - data.reactiveDroopGain() * droopSignal,
                data.reactiveErrorMinimum(), data.reactiveErrorMaximum());
        boolean freezeReactive = terminalVoltage < data.reactiveFreezeVoltage();
        double voltageIntegralRate = freezeReactive ? 0.0
                : boundedRate(s.voltageIntegrator,
                        data.voltageIntegralGain() * voltageError,
                        data.reactiveMinimum(), data.reactiveMaximum());
        double windVarRate = freezeReactive ? 0.0
                : rate(data.voltageProportionalGain() * voltageError,
                        s.windVarLag, data.windVarTime());
        double voltageRegulatorOutput = clamp(reactiveReference
                        + s.voltageIntegrator + s.windVarLag,
                data.reactiveMinimum(), data.reactiveMaximum());
        double voltageFilterRate = rate(voltageRegulatorOutput,
                s.voltageFilter, data.voltageFilterTime());

        double qOrder = reactiveOrder(s, machineP);
        double rawVoltMvarRate = data.internalVoltageGain()
                * (s.mvarVoltIntegrator + auxiliarySignal - terminalVoltage);
        boolean voltMvarBlocked = blocksOutward(s.voltMvarIntegrator,
                rawVoltMvarRate, data.internalVoltageMinimum(),
                data.internalVoltageMaximum());
        double mvarVoltRate = voltMvarBlocked ? 0.0
                : boundedRate(s.mvarVoltIntegrator,
                        data.reactiveVoltageGain() * (qOrder - q),
                        data.voltageMinimum(), data.voltageMaximum());
        double voltMvarRate = boundedRate(s.voltMvarIntegrator,
                rawVoltMvarRate, data.internalVoltageMinimum(),
                data.internalVoltageMaximum());

        double speedReference = speedReference(p + externalActiveSignal);
        double speedReferenceRate = rate(speedReference, s.powerReferenceFilter,
                data.powerReferenceFilterTime());
        double speedError = rotorSpeed - algebraicOrState(speedReference,
                s.powerReferenceFilter, data.powerReferenceFilterTime());
        double torqueIntegralRate = boundedRate(s.torqueIntegrator,
                data.torqueIntegralGain() * speedError,
                data.powerMinimum(), data.powerMaximum());
        double torqueRequest = clamp((s.torqueIntegrator
                        + data.torqueProportionalGain() * speedError)
                        * (1.0 + rotorSpeed),
                data.powerMinimum(), data.powerMaximum());
        double torqueFilterRate = rate(torqueRequest, s.torqueFilter,
                data.torqueFilterTime());

        double availableRate = rate(availablePower, s.availablePowerFilter,
                data.availablePowerFilterTime());
        double frequencyPower = data.activePowerControlFlag() == 1
                ? frequencyPower(systemFrequency) : s.availablePowerFilter;
        double targetPower = Math.min(s.torqueFilter, frequencyPower);
        double powerCommandRate = rateLimitStorageDerivative(targetPower,
                s.powerCommandRate);
        double lvplRate = rate(terminalVoltage, s.lvplLimit, data.lvplSensorTime());

        double frequencyError = deadband(1.0 - systemFrequency - externalActiveSignal,
                data.windInertiaDeadband());
        double inertiaFilterRate = rate(frequencyError, s.windInertiaFilter,
                data.windInertiaFilterTime());
        double inertiaWashoutRate = rate(s.windInertiaFilter, s.windInertiaWashout,
                data.windInertiaWashoutTime());
        double lvplFactor = Math.min(1.0, Math.max(0.0,
                s.lvplLimit / Math.max(EPS, data.lvplBreakpoint())));
        double commandedPower = targetPower * lvplFactor;
        double brakingEnergyRate = Math.max(0.0, commandedPower - p)
                - brakingPower(s.brakingIntegrator, targetPower, p, terminalVoltage);
        double highWindTripRate = windVelocity > data.highWindTripThreshold() ? 1.0 : 0.0;

        return new Derivative(voltageFilterRate, voltageIntegralRate,
                torqueFilterRate, torqueIntegralRate,
                rate(terminalVoltage, s.voltageSensor, data.voltageSensorTime()),
                speedReferenceRate, mvarVoltRate, voltMvarRate, windVarRate,
                rate(machineP, s.fastPowerFactorFilter,
                        data.fastPowerFactorFilterTime()),
                availableRate, powerCommandRate, lvplRate, highWindTripRate,
                brakingEnergyRate,
                rate(droopInput, s.reactiveDroopFilter,
                        data.reactiveDroopFilterTime()),
                inertiaFilterRate, inertiaWashoutRate);
    }

    private double reactiveOrder(State s, double p) {
        if (data.varFlag() == 1) {
            return clamp(s.voltageFilter, data.reactiveMinimum(),
                    data.reactiveMaximum());
        }
        if (data.powerFactorFlag() == 1) {
            double filteredP = algebraicOrState(p, s.fastPowerFactorFilter,
                    data.fastPowerFactorFilterTime());
            return filteredP * Math.tan(powerFactorAngle);
        }
        return reactiveReference;
    }

    private void updateCommands() {
        if (tripped) {
            generator.setCommands(0.0, 0.0);
            return;
        }
        double voltage = Math.max(0.01, generator.getDStabBus().getVoltageMag());
        double lvpl = Math.min(1.0, Math.max(0.0,
                algebraicOrState(voltageReference, state.lvplLimit,
                        data.lvplSensorTime()) / Math.max(EPS, data.lvplBreakpoint())));
        double inertia = windInertiaOutput(state);
        double targetPower = Math.min(state.torqueFilter,
                data.activePowerControlFlag() == 1
                        ? frequencyPower(systemFrequency) : state.availablePowerFilter);
        double active = toMachineBase(targetPower - state.powerCommandRate)
                * lvpl / voltage + inertia;
        double reactive = state.voltMvarIntegrator;
        double currentLimit = Math.min(data.converterCurrentLimit(),
                Math.hypot(data.hardActiveCurrentLimit(),
                        data.hardReactiveCurrentLimit()));
        active = clamp(active, -data.hardActiveCurrentLimit(),
                Math.min(data.activeCurrentMaximum(), data.hardActiveCurrentLimit()));
        reactive = clamp(reactive, -data.hardReactiveCurrentLimit(),
                data.hardReactiveCurrentLimit());
        double magnitude = Math.hypot(active, reactive);
        if (magnitude > currentLimit) {
            if (data.pqPriorityFlag() == 0) {
                active = Math.copySign(Math.sqrt(Math.max(0.0,
                        currentLimit * currentLimit - reactive * reactive)), active);
            } else {
                reactive = Math.copySign(Math.sqrt(Math.max(0.0,
                        currentLimit * currentLimit - active * active)), reactive);
            }
        }
        generator.setCommands(active, reactive);
    }

    private double measuredVoltage(double voltage) {
        if (data.remoteBus() != 0) {
            BaseDStabBus<?, ?> remote = (BaseDStabBus<?, ?>) generator.getDStabBus()
                    .getNetwork().getBus("Bus" + data.remoteBus());
            if (remote != null && remote.getVoltageMag() > EPS) return remote.getVoltageMag();
        }
        return voltage;
    }

    private static double currentSquared(double voltage, double p, double q) {
        return (p * p + q * q) / Math.max(EPS, voltage * voltage);
    }

    private double speedReference(double power) {
        double absolute = power < .46
                ? -.75 * power * power + 1.59 * power + .63 : 1.2;
        return absolute - 1.0;
    }

    private double rateLimitStorageDerivative(double target, double storage) {
        if (data.powerCommandRateTime() <= EPS) return 0.0;
        double unconstrained = -storage / data.powerCommandRateTime();
        double limited = clamp(unconstrained, data.powerRateMinimum(),
                data.powerRateMaximum());
        return unconstrained - limited;
    }

    public double frequencyPower(double frequency) {
        if (frequency <= data.frequencyA()) return data.powerAtFrequencyA();
        if (frequency <= data.frequencyB()) return interpolate(frequency,
                data.frequencyA(), data.powerAtFrequencyA(),
                data.frequencyB(), data.powerAtFrequencyB());
        if (frequency <= data.frequencyC()) return interpolate(frequency,
                data.frequencyB(), data.powerAtFrequencyB(),
                data.frequencyC(), data.powerAtFrequencyC());
        if (frequency <= data.frequencyD()) return interpolate(frequency,
                data.frequencyC(), data.powerAtFrequencyC(),
                data.frequencyD(), data.powerAtFrequencyD());
        return data.powerAtFrequencyD();
    }

    private double windInertiaOutput(State s) {
        double raw = data.windInertiaGain()
                * (s.windInertiaFilter - s.windInertiaWashout);
        return clamp(raw, data.windInertiaPowerMinimum(),
                data.windInertiaPowerMaximum());
    }

    private double brakingPower(double energy, double powerCommand,
            double p, double voltage) {
        if (!generator.getData().fullConverter()) return 0.0;
        double excess = powerCommand - p;
        double requested = excess + data.brakingControllerGain()
                * Math.max(0.0, energy - data.brakingEnergyThreshold());
        return clamp(requested, 0.0, data.brakingPowerMaximum());
    }

    private boolean outsideOperatingEnvelope() {
        return 1.0 + rotorSpeed < data.lowRotorSpeedTrip()
                || windVelocity < data.minimumWindSpeed()
                || windVelocity > data.maximumWindSpeed()
                || (state != null && state.highWindTripIntegrator >= 1.0);
    }

    private void applyAlgebraicBypasses() {
        double voltage = measuredVoltage(generator.getDStabBus().getVoltageMag());
        if (data.voltageFilterTime() <= EPS) state = state.withVoltageFilter(
                clamp(reactiveReference + state.voltageIntegrator + state.windVarLag,
                        data.reactiveMinimum(), data.reactiveMaximum()));
        if (data.voltageSensorTime() <= EPS) state = state.withVoltageSensor(
                voltage);
        if (data.fastPowerFactorFilterTime() <= EPS) {
            state = state.withFastPowerFactorFilter(machineP());
        }
        if (data.availablePowerFilterTime() <= EPS) {
            state = state.withAvailablePowerFilter(availablePower);
        }
        if (data.lvplSensorTime() <= EPS) state = state.withLvplLimit(voltage);
        if (data.reactiveDroopFilterTime() <= EPS) {
            state = state.withReactiveDroopFilter(reactiveDroopTarget(
                    currentSquared(voltage, machineP(), machineQ())));
        }
        if (data.windVarTime() <= EPS) {
            double squared = currentSquared(voltage, machineP(), machineQ());
            double error = clamp(voltageReference - state.voltageSensor
                            - squared * data.lineDropReactance()
                            - data.reactiveDroopGain() * state.reactiveDroopFilter,
                    data.reactiveErrorMinimum(), data.reactiveErrorMaximum());
            state = state.withWindVarLag(data.voltageProportionalGain() * error);
        }
    }

    public void setExternalSignals(double rotorSpeed, double windVelocity,
            double availablePower, double systemFrequency, double auxiliarySignal,
            double externalActiveSignal, double qDroopInput) {
        double[] values = {rotorSpeed, windVelocity, availablePower,
                systemFrequency, auxiliarySignal, externalActiveSignal, qDroopInput};
        for (double value : values) if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("GEWTECU1 signals must be finite");
        }
        this.rotorSpeed = rotorSpeed;
        this.windVelocity = windVelocity;
        this.availablePower = availablePower;
        this.systemFrequency = systemFrequency;
        this.auxiliarySignal = auxiliarySignal;
        this.externalActiveSignal = externalActiveSignal;
        this.qDroopInput = qDroopInput;
    }

    /** Updates only the shaft-speed input while preserving the other staged inputs. */
    public void setRotorSpeed(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("GEWTECU1 rotor speed must be positive and finite");
        }
        rotorSpeed = value;
    }

    /** Override measured terminal power for deterministic staged-input replay. */
    public void setMeasuredPower(double machineBaseP, double machineBaseQ) {
        if (!Double.isFinite(machineBaseP) || !Double.isFinite(machineBaseQ)) {
            throw new IllegalArgumentException("GEWTECU1 measured power must be finite");
        }
        measuredMachineP = machineBaseP;
        measuredMachineQ = machineBaseQ;
        qDroopInput = machineBaseQ;
    }

    public void clearMeasuredPowerOverride() {
        measuredMachineP = Double.NaN;
        measuredMachineQ = Double.NaN;
    }

    private double machineP() {
        return Double.isNaN(measuredMachineP) ? generator.getP() : measuredMachineP;
    }
    private double machineQ() {
        return Double.isNaN(measuredMachineQ) ? generator.getQ() : measuredMachineQ;
    }

    private void applyStateLimits() {
        double[] values = state.values();
        values[1] = clamp(values[1], data.reactiveMinimum(), data.reactiveMaximum());
        values[3] = clamp(values[3], data.powerMinimum(), data.powerMaximum());
        values[6] = clamp(values[6], data.voltageMinimum(), data.voltageMaximum());
        values[7] = clamp(values[7], data.internalVoltageMinimum(),
                data.internalVoltageMaximum());
        values[13] = clamp(values[13], 0.0, 1.0);
        state = State.of(values);
    }

    private double reactiveDroopTarget(double currentSquared) {
        // With no monitored branch the published selector feeds QELEC directly.
        // The synthesizing impedance belongs only to the branch-flow path.
        return data.qDroopEnabled()
                ? qDroopInput + currentSquared * data.reactiveDroopReactance()
                : qDroopInput;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("Filter in Voltage regulator", state.voltageFilter);
        values.put("Integrator in Voltage regulator", state.voltageIntegrator);
        values.put("Filter in Torque regulator", state.torqueFilter);
        values.put("Integrator in Torque regulator", state.torqueIntegrator);
        values.put("Voltage sensor", state.voltageSensor);
        values.put("Power reference filter", state.powerReferenceFilter);
        values.put("Mvar/Volt integrator", state.mvarVoltIntegrator);
        values.put("Volt/Mvar integrator", state.voltMvarIntegrator);
        values.put("Lag of the WindVar controller", state.windVarLag);
        values.put("Input filter of PELEC for fast PF controller", state.fastPowerFactorFilter);
        values.put("Input filter for Pavail", state.availablePowerFilter);
        values.put("Power response rate limit", state.powerCommandRate);
        values.put("LVPL limit", state.lvplLimit);
        values.put("High wind speed trip integrator", state.highWindTripIntegrator);
        values.put("Braking resistor integrator", state.brakingIntegrator);
        values.put("Filter in Reactive Droop", state.reactiveDroopFilter);
        values.put("Filter in WindInertia", state.windInertiaFilter);
        values.put("Washout in WindInertia", state.windInertiaWashout);
        return Map.copyOf(values);
    }

    /** Active-power order supplied to the associated pitch compensator. */
    public double getPowerOrder() {
        if (!initialized) throw new IllegalStateException("GEWTECU1 is not initialized");
        return state.torqueFilter;
    }

    private static double algebraicOrState(double input, double stored, double time) {
        return time <= EPS ? input : stored;
    }
    private static double rate(double input, double stored, double time) {
        return time <= EPS ? 0.0 : (input - stored) / time;
    }
    private double toTurbineBase(double machineBaseValue) {
        return machineBaseValue * generator.getDeviceBaseMva()
                / Math.max(EPS, generator.getAggregateRatedMw());
    }
    private double toMachineBase(double turbineBaseValue) {
        return turbineBaseValue * generator.getAggregateRatedMw()
                / Math.max(EPS, generator.getDeviceBaseMva());
    }
    private static double boundedRate(double state, double derivative,
            double minimum, double maximum) {
        return blocksOutward(state, derivative, minimum, maximum)
                ? 0.0 : derivative;
    }
    private static boolean blocksOutward(double state, double derivative,
            double minimum, double maximum) {
        return (state >= maximum && derivative > 0.0)
                || (state <= minimum && derivative < 0.0);
    }
    private static double deadband(double value, double width) {
        if (value > width) return value - width;
        if (value < -width) return value + width;
        return 0.0;
    }
    private static double interpolate(double x, double x1, double y1,
            double x2, double y2) {
        if (Math.abs(x2 - x1) <= EPS) return y2;
        return y1 + (y2 - y1) * (x - x1) / (x2 - x1);
    }
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static boolean finiteState(State s) {
        for (double value : s.values()) if (!Double.isFinite(value)) return false;
        return true;
    }
    private static State advance(State s, Derivative d, double h) {
        double[] a = s.values();
        double[] b = d.values();
        for (int i = 0; i < a.length; i++) a[i] += h * b[i];
        return State.of(a);
    }

    public Gewtecu1Data getData() { return data; }
    public boolean isTripped() { return tripped; }

    private record State(double voltageFilter, double voltageIntegrator,
            double torqueFilter, double torqueIntegrator, double voltageSensor,
            double powerReferenceFilter, double mvarVoltIntegrator,
            double voltMvarIntegrator, double windVarLag,
            double fastPowerFactorFilter, double availablePowerFilter,
            double powerCommandRate, double lvplLimit,
            double highWindTripIntegrator, double brakingIntegrator,
            double reactiveDroopFilter, double windInertiaFilter,
            double windInertiaWashout) {
        double[] values() { return new double[]{voltageFilter, voltageIntegrator,
                torqueFilter, torqueIntegrator, voltageSensor, powerReferenceFilter,
                mvarVoltIntegrator, voltMvarIntegrator, windVarLag,
                fastPowerFactorFilter, availablePowerFilter, powerCommandRate,
                lvplLimit, highWindTripIntegrator, brakingIntegrator,
                reactiveDroopFilter, windInertiaFilter, windInertiaWashout}; }
        static State of(double[] v) { return new State(v[0],v[1],v[2],v[3],v[4],v[5],
                v[6],v[7],v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15],v[16],v[17]); }
        State withVoltageFilter(double v) { double[] a=values(); a[0]=v; return of(a); }
        State withVoltageSensor(double v) { double[] a=values(); a[4]=v; return of(a); }
        State withFastPowerFactorFilter(double v) { double[] a=values(); a[9]=v; return of(a); }
        State withAvailablePowerFilter(double v) { double[] a=values(); a[10]=v; return of(a); }
        State withLvplLimit(double v) { double[] a=values(); a[12]=v; return of(a); }
        State withReactiveDroopFilter(double v) { double[] a=values(); a[15]=v; return of(a); }
        State withWindVarLag(double v) { double[] a=values(); a[8]=v; return of(a); }
    }
    private record Derivative(double voltageFilter, double voltageIntegrator,
            double torqueFilter, double torqueIntegrator, double voltageSensor,
            double powerReferenceFilter, double mvarVoltIntegrator,
            double voltMvarIntegrator, double windVarLag,
            double fastPowerFactorFilter, double availablePowerFilter,
            double powerCommandRate, double lvplLimit,
            double highWindTripIntegrator, double brakingIntegrator,
            double reactiveDroopFilter, double windInertiaFilter,
            double windInertiaWashout) {
        double[] values() { return new double[]{voltageFilter, voltageIntegrator,
                torqueFilter, torqueIntegrator, voltageSensor, powerReferenceFilter,
                mvarVoltIntegrator, voltMvarIntegrator, windVarLag,
                fastPowerFactorFilter, availablePowerFilter, powerCommandRate,
                lvplLimit, highWindTripIntegrator, brakingIntegrator,
                reactiveDroopFilter, windInertiaFilter, windInertiaWashout}; }
        Derivative add(Derivative o) { double[] a=values(), b=o.values();
            for (int i=0;i<a.length;i++) a[i]+=b[i];
            return new Derivative(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],
                    a[9],a[10],a[11],a[12],a[13],a[14],a[15],a[16],a[17]); }
    }
}
