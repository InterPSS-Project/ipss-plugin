package org.interpss.dstab.mach;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.net.DataCheckConfiguration;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.impl.DynamicSimuAdapterImpl;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.impl.DynamicMachineImpl;

/**
 * PSS/E CIMTR4 induction motor represented by a negative generator record.
 *
 * <p>The implementation uses the equivalent two-rotor-cage circuit behind the
 * stator leakage reactance. Rotor fluxes are integrated in the synchronous
 * network reference frame, and the fixed initial-condition Mvar difference is
 * retained as the published compensation admittance.</p>
 */
public final class Cimtr4Machine extends DynamicMachineImpl implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Cimtr4Data data;
    private final Cimtr4Solver solver;
    private Complex compensationY = Complex.ZERO; // machine base, load convention
    private double initialMotorPower;
    private double initialSpeed;
    private double oldSpeed;
    private double oldAngle;
    private double speedDerivative;
    private double angleDerivative;

    public Cimtr4Machine(Cimtr4Data data) {
        super();
        this.data = data;
        this.solver = new Cimtr4Solver();
        this._dEqnSolver = solver;
    }

    public Cimtr4Data getCimtr4Data() {
        return data;
    }

    @Override
    public boolean checkData(DataCheckConfiguration config) {
        return getRating() > EPS && getRatedVoltage() > EPS && getH() > EPS
                && getRa() >= 0.0;
    }

    @Override
    public double calculateIfd(MachineIfdBase base) {
        return 0.0;
    }

    @Override
    public Complex getYgen() {
        double scale = getIMultiFactor() / getVMultiFactor();
        Complex sourceY = Complex.ONE.divide(new Complex(getRa(), data.xpp()));
        return sourceY.add(compensationY).multiply(scale);
    }

    @Override
    public Complex getIgen() {
        if (!solver.initialized) return Complex.ZERO;
        Complex voltage = machineVoltage();
        CircuitPoint point = solver.circuit(voltage, solver.rotorFlux1, solver.rotorFlux2);
        Complex fixedY = Complex.ONE.divide(new Complex(getRa(), data.xpp()));
        Complex norton = point.motorCurrent.negate()
                .add(fixedY.multiply(voltage));
        return norton.multiply(getIMultiFactor());
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        boolean result = super.updateAttributes(netChange);
        if (solver.initialized) solver.updateOutputs();
        return result;
    }

    @Override
    public boolean nextStepMechanical(double dt, DynamicSimuMethod method,
            Network network, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new UnsupportedOperationException("CIMTR4 currently supports MODIFIED_EULER only");
        }
        solver.updateOutputs();
        double omega0 = 2.0 * Math.PI * network.getFrequency();
        double dSpeed = (solver.motorPower - initialMotorPower
                - data.d() * (getSpeed() - initialSpeed)) / (2.0 * data.h());
        double dAngle = omega0 * (getSpeed() - initialSpeed);
        if (flag == 0) {
            oldSpeed = getSpeed();
            oldAngle = getAngle();
            speedDerivative = dSpeed;
            angleDerivative = dAngle;
            setSpeed(oldSpeed + dSpeed * dt);
            setAngle(oldAngle + dAngle * dt);
        } else {
            setSpeed(oldSpeed + 0.5 * (speedDerivative + dSpeed) * dt);
            setAngle(oldAngle + 0.5 * (angleDerivative + dAngle) * dt);
        }
        return true;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!solver.initialized) return Map.of();
        Complex ep = solver.transientVoltage(solver.rotorFlux1);
        Complex epp = solver.subtransientVoltage(solver.rotorFlux1, solver.rotorFlux2);
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("E'q", ep.getReal());
        states.put("E'd", ep.getImaginary());
        states.put("E''q", epp.getReal());
        states.put("E''d", epp.getImaginary());
        states.put("Speed Deviation", getSpeed() - 1.0);
        states.put("Angle Deviation", getAngle() - solver.initialAngle);
        return Map.copyOf(states);
    }

    private Complex machineVoltage() {
        return getDStabBus().getVoltage().divide(getVMultiFactor());
    }

    private final class Cimtr4Solver extends DynamicSimuAdapterImpl {
        private double xm;
        private double xr1;
        private double rr1;
        private double xr2;
        private double rr2;
        private Complex rotorFlux1 = Complex.ZERO;
        private Complex rotorFlux2 = Complex.ZERO;
        private Complex oldRotorFlux1 = Complex.ZERO;
        private Complex oldRotorFlux2 = Complex.ZERO;
        private Complex derivative1 = Complex.ZERO;
        private Complex derivative2 = Complex.ZERO;
        private double motorPower;
        private double motorQ;
        private double electricalTorque;
        private double initialAngle;
        private boolean initialized;

        @Override
        public boolean initStates(BaseDStabBus<?, ?> bus) {
            super.initStates(bus);
            double omega0 = 2.0 * Math.PI * bus.getNetwork().getFrequency();
            xm = data.x() - data.xl();
            xr1 = 1.0 / (1.0 / (data.xp() - data.xl()) - 1.0 / xm);
            rr1 = (xr1 + xm) / (omega0 * data.tp());
            if (data.twoCage()) {
                xr2 = 1.0 / (1.0 / (data.xpp() - data.xl()) - 1.0 / xm - 1.0 / xr1);
                rr2 = (xr2 + data.xp() - data.xl()) / (omega0 * data.tpp());
            }

            Complex voltage = machineVoltage();
            double requestedP = -getParentGen().getGen().getReal() / getRating();
            if (requestedP <= EPS) {
                throw new IllegalStateException("CIMTR4 requires a negative-P generator operating point");
            }
            double slip = solveSlip(requestedP, voltage.abs());
            setSpeed(1.0 - slip);
            initialSpeed = getSpeed();
            initialAngle = getAngle();

            SteadyCircuit steady = steadyCircuit(slip, voltage);
            rotorFlux1 = steady.airGapFlux.subtract(steady.rotorCurrent1.multiply(xr1));
            rotorFlux2 = data.twoCage()
                    ? steady.airGapFlux.subtract(steady.rotorCurrent2.multiply(xr2))
                    : Complex.ZERO;

            Complex actualGeneratorCurrent = getParentGen().getGen().divide(bus.getVoltage())
                    .conjugate().divide(getIMultiFactor());
            Complex rawCompensation = steady.motorCurrent.negate()
                    .subtract(actualGeneratorCurrent).divide(voltage);
            compensationY = new Complex(0.0, rawCompensation.getImaginary());
            initialMotorPower = voltage.multiply(steady.motorCurrent.conjugate()).getReal();
            motorPower = initialMotorPower;
            motorQ = voltage.multiply(steady.motorCurrent.conjugate()).getImaginary();
            electricalTorque = initialMotorPower;
            setPm(-initialMotorPower);
            setEfd(0.0);
            initialized = true;
            updateOutputs();
            return true;
        }

        @Override
        public boolean nextStepElectricalModifiedEuler(double dt, DynamicSimuMethod method,
                Network<?, ?> network, int flag) {
            if (method != DynamicSimuMethod.MODIFIED_EULER) {
                throw new UnsupportedOperationException("CIMTR4 currently supports MODIFIED_EULER only");
            }
            Derivatives derivatives = derivatives(machineVoltage(), getSpeed(),
                    rotorFlux1, rotorFlux2, network.getFrequency());
            if (flag == 0) {
                oldRotorFlux1 = rotorFlux1;
                oldRotorFlux2 = rotorFlux2;
                derivative1 = derivatives.rotor1;
                derivative2 = derivatives.rotor2;
                rotorFlux1 = oldRotorFlux1.add(derivative1.multiply(dt));
                if (data.twoCage()) rotorFlux2 = oldRotorFlux2.add(derivative2.multiply(dt));
            } else {
                rotorFlux1 = oldRotorFlux1.add(
                        derivative1.add(derivatives.rotor1).multiply(0.5 * dt));
                if (data.twoCage()) {
                    rotorFlux2 = oldRotorFlux2.add(
                            derivative2.add(derivatives.rotor2).multiply(0.5 * dt));
                }
            }
            updateOutputs();
            return true;
        }

        private Derivatives derivatives(Complex voltage, double speed,
                Complex psi1, Complex psi2, double frequency) {
            CircuitPoint point = circuit(voltage, psi1, psi2);
            double omega0 = 2.0 * Math.PI * frequency;
            double slip = 1.0 - speed;
            Complex d1 = point.rotorCurrent1.multiply(rr1)
                    .subtract(psi1.multiply(Complex.I.multiply(slip))).multiply(omega0);
            Complex d2 = data.twoCage()
                    ? point.rotorCurrent2.multiply(rr2)
                            .subtract(psi2.multiply(Complex.I.multiply(slip))).multiply(omega0)
                    : Complex.ZERO;
            return new Derivatives(d1, d2);
        }

        private CircuitPoint circuit(Complex voltage, Complex psi1, Complex psi2) {
            double effectiveXm = saturatedXm(psi1, psi2);
            double a = 1.0 / effectiveXm + 1.0 / xr1 + (data.twoCage() ? 1.0 / xr2 : 0.0);
            Complex b = psi1.divide(xr1);
            if (data.twoCage()) b = b.add(psi2.divide(xr2));
            Complex zLeakage = new Complex(getRa(), data.xl());
            Complex airGap = voltage.add(zLeakage.multiply(b))
                    .divide(zLeakage.multiply(a).add(Complex.I));
            Complex ir1 = airGap.subtract(psi1).divide(xr1);
            Complex ir2 = data.twoCage() ? airGap.subtract(psi2).divide(xr2) : Complex.ZERO;
            Complex is = airGap.multiply(a).subtract(b);
            return new CircuitPoint(is, ir1, ir2, airGap);
        }

        private Complex transientVoltage(Complex psi1) {
            return Complex.I.multiply(psi1).multiply(xm / (xm + xr1));
        }

        private Complex subtransientVoltage(Complex psi1, Complex psi2) {
            double effectiveXm = saturatedXm(psi1, psi2);
            double a = 1.0 / effectiveXm + 1.0 / xr1
                    + (data.twoCage() ? 1.0 / xr2 : 0.0);
            Complex b = psi1.divide(xr1);
            if (data.twoCage()) b = b.add(psi2.divide(xr2));
            Complex zLeakage = new Complex(getRa(), data.xl());
            Complex denominator = zLeakage.multiply(a).add(Complex.I);
            Complex stateCurrent = zLeakage.multiply(a).divide(denominator)
                    .subtract(Complex.ONE).multiply(b);
            Complex fixedY = Complex.ONE.divide(new Complex(getRa(), data.xpp()));
            return stateCurrent.negate().divide(fixedY);
        }

        private double saturatedXm(Complex psi1, Complex psi2) {
            Complex estimate = data.twoCage() ? psi1.add(psi2).multiply(0.5) : psi1;
            double saturation = saturation(estimate.abs());
            return Math.max(EPS, xm / (1.0 + saturation));
        }

        private double saturation(double flux) {
            if (data.se1() <= EPS || data.se2() <= EPS || data.e2() <= data.e1() || flux <= 0.0) {
                return 0.0;
            }
            double alpha = Math.sqrt(data.e2() * data.se2() / (data.e1() * data.se1()));
            if (Math.abs(alpha - 1.0) < EPS) return data.se1();
            double threshold = (alpha * data.e1() - data.e2()) / (alpha - 1.0);
            if (flux <= threshold) return 0.0;
            double coefficient = data.se1() * data.e1()
                    / Math.pow(data.e1() - threshold, 2.0);
            return coefficient * Math.pow(flux - threshold, 2.0) / flux;
        }

        private double solveSlip(double requestedP, double voltage) {
            double lower = 1.0e-7;
            double upper = 0.999;
            double lowError = steadyPower(lower, voltage) - requestedP;
            double highError = steadyPower(upper, voltage) - requestedP;
            if (lowError * highError > 0.0) {
                upper = 0.2;
                highError = steadyPower(upper, voltage) - requestedP;
            }
            if (lowError * highError > 0.0) {
                throw new IllegalStateException("CIMTR4 operating point has no stable slip solution");
            }
            for (int iteration = 0; iteration < 100; iteration++) {
                double middle = 0.5 * (lower + upper);
                double error = steadyPower(middle, voltage) - requestedP;
                if (Math.abs(error) < 1.0e-12) return middle;
                if (lowError * error <= 0.0) {
                    upper = middle;
                } else {
                    lower = middle;
                    lowError = error;
                }
            }
            return 0.5 * (lower + upper);
        }

        private double steadyPower(double slip, double voltage) {
            Complex y = steadyAdmittance(slip);
            return voltage * voltage * y.getReal();
        }

        private SteadyCircuit steadyCircuit(double slip, Complex voltage) {
            Complex ym = Complex.ONE.divide(new Complex(0.0, xm));
            Complex y1 = Complex.ONE.divide(new Complex(rr1 / slip, xr1));
            Complex y2 = data.twoCage()
                    ? Complex.ONE.divide(new Complex(rr2 / slip, xr2)) : Complex.ZERO;
            Complex parallelY = ym.add(y1).add(y2);
            Complex airGapVoltage = voltage.divide(
                    Complex.ONE.add(new Complex(getRa(), data.xl()).multiply(parallelY)));
            Complex motorCurrent = parallelY.multiply(airGapVoltage);
            Complex airGapFlux = airGapVoltage.divide(Complex.I);
            return new SteadyCircuit(motorCurrent, y1.multiply(airGapVoltage),
                    y2.multiply(airGapVoltage), airGapFlux);
        }

        private Complex steadyAdmittance(double slip) {
            Complex ym = Complex.ONE.divide(new Complex(0.0, xm));
            Complex y1 = Complex.ONE.divide(new Complex(rr1 / slip, xr1));
            Complex y2 = data.twoCage()
                    ? Complex.ONE.divide(new Complex(rr2 / slip, xr2)) : Complex.ZERO;
            Complex parallelY = ym.add(y1).add(y2);
            return Complex.ONE.divide(new Complex(getRa(), data.xl())
                    .add(Complex.ONE.divide(parallelY)));
        }

        private void updateOutputs() {
            CircuitPoint point = circuit(machineVoltage(), rotorFlux1, rotorFlux2);
            Complex power = machineVoltage().multiply(point.motorCurrent.conjugate());
            motorPower = power.getReal();
            motorQ = power.getImaginary();
            electricalTorque = motorPower;
            setPe(-motorPower);
            setQGen(-motorQ + Math.pow(machineVoltage().abs(), 2.0) * compensationY.getImaginary());
        }

        @Override
        public Hashtable<String, Object> getStates(Object ref) {
            updateOutputs();
            Hashtable<String, Object> states = new Hashtable<>();
            states.putAll(getNamedStates());
            states.put("CIMTR4 Motor Q", -motorQ * getIMultiFactor());
            states.put("CIMTR4 Telec", -electricalTorque);
            states.put("CIMTR4 Q Compensation", compensationY.getImaginary()
                    * getIMultiFactor());
            return states;
        }
    }

    private record CircuitPoint(Complex motorCurrent, Complex rotorCurrent1,
            Complex rotorCurrent2, Complex airGapFlux) {}

    private record SteadyCircuit(Complex motorCurrent, Complex rotorCurrent1,
            Complex rotorCurrent2, Complex airGapFlux) {}

    private record Derivatives(Complex rotor1, Complex rotor2) {}
}
