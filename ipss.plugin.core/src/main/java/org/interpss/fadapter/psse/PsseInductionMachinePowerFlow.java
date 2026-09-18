/*
 * Copyright (C) 2006-2026 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */
package org.interpss.fadapter.psse;

import org.apache.commons.math3.complex.Complex;

/**
 * Initializes a PSS/E induction-machine record as a steady-state ACLF load.
 *
 * <p>The InterPSS ACLF data model has no contributing-load subtype whose slip
 * is an algebraic power-flow state.  The direct adapters therefore solve the
 * standard induction-machine equivalent circuit at the saved terminal voltage
 * and retain the resulting terminal P/Q as an independent constant-power
 * contribution.  This preserves the machine's base-case injection instead of
 * silently dropping it, while keeping its ID distinct from ordinary RAW loads.
 */
final class PsseInductionMachinePowerFlow {
    private static final double MIN_SLIP = 1.0e-7;
    private static final double MAX_SLIP = 1.0;
    private static final int ROOT_SCAN_STEPS = 4000;
    private static final int BISECTION_STEPS = 80;

    private PsseInductionMachinePowerFlow() {}

    record Data(int tcode, int dcode, double mbase, double rateKv, int pcode,
            double pset, double a, double b, double d, double e,
            double ra, double xa, double xm, double r1, double x1,
            double r2, double x2, double x3) {}

    record Result(Complex loadPu, double slip, boolean stalled, boolean tripped) {}

    static Result initialize(Data data, double savedBusVoltagePu,
            double busBaseKv, double systemBaseMva) {
        Parameters parameters = parameters(data);
        validate(data, parameters, systemBaseMva);
        double machineBase = data.mbase() > 0.0 ? data.mbase() : systemBaseMva;
        double terminalVoltage = savedBusVoltagePu;
        if (data.rateKv() > 0.0 && busBaseKv > 0.0) {
            terminalVoltage *= busBaseKv / data.rateKv();
        }
        final double operatingVoltage = Math.max(0.0, terminalVoltage);

        if (data.pset() == 0.0) {
            State state = state(parameters, operatingVoltage, MIN_SLIP, machineBase);
            return result(state, MIN_SLIP, false, systemBaseMva);
        }

        double direction = Math.copySign(1.0, data.pset());
        double ratedSlip = findRunningRoot(direction,
                slip -> scheduledError(data, parameters, 1.0, slip, machineBase));
        if (!Double.isFinite(ratedSlip)) {
            if (direction < 0.0) {
                return tripped();
            }
            return result(state(parameters, operatingVoltage, MAX_SLIP, machineBase),
                    MAX_SLIP, true, systemBaseMva);
        }
        State rated = state(parameters, 1.0, ratedSlip, machineBase);
        double ratedSpeed = 1.0 - ratedSlip;
        double torqueScale = rated.mechanicalTorque()
                / torqueCurve(data, ratedSpeed);

        double operatingSlip = findRunningRoot(direction, slip -> {
            State operating = state(parameters, operatingVoltage, slip, machineBase);
            return operating.mechanicalTorque()
                    - torqueScale * torqueCurve(data, 1.0 - slip);
        });
        boolean stalled = !Double.isFinite(operatingSlip);
        if (stalled) {
            if (direction < 0.0) {
                return tripped();
            }
            operatingSlip = MAX_SLIP;
        }
        return result(state(parameters, operatingVoltage, operatingSlip, machineBase),
                operatingSlip, stalled, systemBaseMva);
    }

    private static Result result(State state, double slip, boolean stalled,
            double systemBaseMva) {
        Complex loadPu = new Complex(state.inputPowerMw() / systemBaseMva,
                state.reactivePowerMvar() / systemBaseMva);
        return new Result(loadPu, slip, stalled, false);
    }

    private static Result tripped() {
        return new Result(Complex.ZERO, Double.NaN, false, true);
    }

    private static double scheduledError(Data data, Parameters parameters,
            double voltage, double slip, double machineBase) {
        State state = state(parameters, voltage, slip, machineBase);
        return (data.pcode() == 2 ? state.inputPowerMw()
                : state.mechanicalPowerMw()) - data.pset();
    }

    private static double torqueCurve(Data data, double speed) {
        double safeSpeed = Math.max(1.0e-6, speed);
        if (data.tcode() == 2) {
            // WECC form, normalized to unity at synchronous speed.
            double constant = 1.0 - data.a() - data.b() - data.d();
            double value = data.a() * safeSpeed * safeSpeed
                    + data.b() * safeSpeed + constant
                    + data.d() * Math.pow(safeSpeed, data.e());
            return nonZero(value);
        }
        return nonZero(Math.pow(safeSpeed, data.d()));
    }

    private static double nonZero(double value) {
        if (Math.abs(value) >= 1.0e-10) {
            return value;
        }
        return Math.copySign(1.0e-10, value == 0.0 ? 1.0 : value);
    }

    private static State state(Parameters p, double voltage, double slip,
            double machineBase) {
        Complex cage1 = new Complex(p.r1() / slip, p.x1());
        Complex cageEquivalent = cage1;
        if (p.hasSecondCage()) {
            Complex cage2 = new Complex(p.r2() / slip, p.x2());
            cageEquivalent = parallel(cage1, cage2);
        }
        Complex rotor = cageEquivalent.add(new Complex(0.0, p.x3()));
        Complex airGap = parallel(new Complex(0.0, p.xm()), rotor);
        Complex impedance = new Complex(p.ra(), p.xa()).add(airGap);
        Complex current = new Complex(voltage, 0.0).divide(impedance);
        Complex airGapVoltage = current.multiply(airGap);
        Complex cageVoltage = airGapVoltage.divide(rotor).multiply(cageEquivalent);

        Complex cage1Current = cageVoltage.divide(cage1);
        double mechanicalPowerPu = cage1Current.abs() * cage1Current.abs()
                * p.r1() * (1.0 - slip) / slip;
        if (p.hasSecondCage()) {
            Complex cage2 = new Complex(p.r2() / slip, p.x2());
            Complex cage2Current = cageVoltage.divide(cage2);
            mechanicalPowerPu += cage2Current.abs() * cage2Current.abs()
                    * p.r2() * (1.0 - slip) / slip;
        }

        Complex input = new Complex(voltage, 0.0).multiply(current.conjugate())
                .multiply(machineBase);
        double mechanicalPower = mechanicalPowerPu * machineBase;
        return new State(input.getReal(), input.getImaginary(), mechanicalPower,
                mechanicalPower / (1.0 - slip));
    }

    private static Complex parallel(Complex left, Complex right) {
        return left.multiply(right).divide(left.add(right));
    }

    private static double findRunningRoot(double direction, ScalarFunction function) {
        double previousSlip = direction * MIN_SLIP;
        double previousValue = function.value(previousSlip);
        for (int i = 1; i <= ROOT_SCAN_STEPS; i++) {
            double exponent = Math.log10(MIN_SLIP)
                    + (0.0 - Math.log10(MIN_SLIP)) * i / ROOT_SCAN_STEPS;
            double slip = direction * Math.pow(10.0, exponent);
            double value = function.value(slip);
            if (Double.isFinite(previousValue) && Double.isFinite(value)
                    && previousValue * value <= 0.0) {
                return bisect(previousSlip, slip, function);
            }
            previousSlip = slip;
            previousValue = value;
        }
        return Double.NaN;
    }

    private static double bisect(double first, double second, ScalarFunction function) {
        double low = Math.min(first, second);
        double high = Math.max(first, second);
        double lowValue = function.value(low);
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = 0.5 * (low + high);
            double midValue = function.value(mid);
            if (lowValue * midValue <= 0.0) {
                high = mid;
            } else {
                low = mid;
                lowValue = midValue;
            }
        }
        return 0.5 * (low + high);
    }

    private static Parameters parameters(Data data) {
        if (data.dcode() == 0) {
            return new Parameters(data.ra(), data.xa(), data.xm(), data.r1(),
                    data.x1(), data.r2(), data.x2(), data.x3());
        }
        return switch (data.dcode()) {
            case 1 -> new Parameters(0.030, 0.080, 2.8, 0.015, 0.110, 0.070, 0.060, 0.0);
            case 2 -> new Parameters(0.030, 0.090, 2.8, 0.025, 0.110, 0.150, 0.040, 0.0);
            case 3 -> new Parameters(0.050, 0.080, 3.0, 0.040, 0.180, 0.100, 0.010, 0.0);
            case 4 -> new Parameters(0.050, 0.050, 2.8, 0.115, 0.050, 0.0, 0.0, 0.0);
            case 5 -> new Parameters(0.030, 0.100, 2.8, 0.010, 0.150, 0.0, 0.0, 0.0);
            default -> throw new IllegalArgumentException(
                    "Unsupported PSS/E induction-machine design code: " + data.dcode());
        };
    }

    private static void validate(Data data, Parameters parameters,
            double systemBaseMva) {
        if (!(systemBaseMva > 0.0) || !Double.isFinite(systemBaseMva)) {
            throw new IllegalArgumentException("system base MVA must be positive");
        }
        if (data.pcode() != 1 && data.pcode() != 2) {
            throw new IllegalArgumentException("unsupported active-power code " + data.pcode());
        }
        if (!(parameters.xm() > 0.0) || !(parameters.r1() > 0.0)
                || parameters.x1() < 0.0 || parameters.ra() < 0.0
                || parameters.xa() < 0.0 || parameters.r2() < 0.0
                || parameters.x2() < 0.0 || parameters.x3() < 0.0
                || !parameters.isFinite() || !Double.isFinite(data.pset())) {
            throw new IllegalArgumentException("invalid equivalent-circuit parameters");
        }
    }

    private record Parameters(double ra, double xa, double xm, double r1,
            double x1, double r2, double x2, double x3) {
        boolean hasSecondCage() {
            return !(r2 == 0.0 && x2 == 0.0) && r2 < 900.0 && x2 < 900.0;
        }

        boolean isFinite() {
            return Double.isFinite(ra) && Double.isFinite(xa) && Double.isFinite(xm)
                    && Double.isFinite(r1) && Double.isFinite(x1)
                    && Double.isFinite(r2) && Double.isFinite(x2)
                    && Double.isFinite(x3);
        }
    }

    private record State(double inputPowerMw, double reactivePowerMvar,
            double mechanicalPowerMw, double mechanicalTorque) {}

    @FunctionalInterface
    private interface ScalarFunction {
        double value(double slip);
    }
}
