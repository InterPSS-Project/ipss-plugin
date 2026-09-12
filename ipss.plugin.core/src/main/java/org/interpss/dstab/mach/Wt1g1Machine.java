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

/** PSS/E WT1G1 direct-connected Type-1 induction generator. */
public final class Wt1g1Machine extends DynamicMachineImpl implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Wt1g1Data data;
    private final Wt1g1Solver solver;
    private Wt12t1Model driveTrain;
    private Complex compensationY = Complex.ZERO;

    public Wt1g1Machine(Wt1g1Data data) {
        this.data = data;
        this.solver = new Wt1g1Solver();
        this._dEqnSolver = solver;
    }

    public Wt1g1Data getWt1g1Data() { return data; }
    public double getSlip() { return 1.0 - getSpeed(); }
    public double getElectricalTorque() { return solver.electricalTorque; }
    public double getCompensationSusceptance() { return compensationY.getImaginary(); }
    public Wt12t1Model getDriveTrain() { return driveTrain; }
    public void setDriveTrain(Wt12t1Model driveTrain) { this.driveTrain = driveTrain; }

    @Override
    public boolean checkData(DataCheckConfiguration config) {
        return getRating() > EPS && getRatedVoltage() > EPS && getRa() >= 0.0;
    }

    @Override
    public double calculateIfd(MachineIfdBase base) { return 0.0; }

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
        return point.machineCurrent.negate().add(fixedY.multiply(voltage))
                .multiply(getIMultiFactor());
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        boolean result = super.updateAttributes(netChange);
        if (solver.initialized) solver.updateOutputs();
        return result;
    }

    /** Rotor speed belongs to WT12T1; without that model PSS/E holds it fixed. */
    @Override
    public boolean nextStepMechanical(double dt, DynamicSimuMethod method,
            Network network, int flag) {
        if (driveTrain != null) {
            driveTrain.step(dt, getPe(), flag);
            setDriveTrainSpeed(driveTrain.getGeneratorSpeed());
        }
        solver.updateOutputs();
        return true;
    }

    public void setDriveTrainSpeed(double speed) {
        if (!Double.isFinite(speed) || speed <= 0.0) {
            throw new IllegalArgumentException("WT1G1 drive-train speed must be positive and finite");
        }
        setSpeed(speed);
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!solver.initialized) return Map.of();
        Complex ep = solver.transientVoltage(solver.rotorFlux1);
        // PSS/E exposes the second-cage rotor flux itself as Eq'' + j Ed''.
        // It is not the Thevenin source voltage behind X''.
        Complex epp = Complex.I.multiply(solver.rotorFlux2);
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("E'q", ep.getReal());
        states.put("E'd", ep.getImaginary());
        states.put("E''q", epp.getReal());
        states.put("E''d", epp.getImaginary());
        return Map.copyOf(states);
    }

    private Complex machineVoltage() {
        return getDStabBus().getVoltage().divide(getVMultiFactor());
    }

    private final class Wt1g1Solver extends DynamicSimuAdapterImpl {
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
        private double electricalTorque;
        private double machineQ;
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
            double requestedP = getParentGen().getGen().getReal() / getRating();
            if (requestedP <= EPS) {
                throw new IllegalStateException("WT1G1 requires a positive-P generator operating point");
            }
            double slip = solveSlip(requestedP, voltage.abs());
            setSpeed(1.0 - slip);

            SteadyCircuit steady = steadyCircuit(slip, voltage);
            rotorFlux1 = steady.airGapFlux.subtract(steady.rotorCurrent1.multiply(xr1));
            rotorFlux2 = data.twoCage()
                    ? steady.airGapFlux.subtract(steady.rotorCurrent2.multiply(xr2))
                    : Complex.ZERO;

            Complex actualGeneratorCurrent = getParentGen().getGen().divide(bus.getVoltage())
                    .conjugate().divide(getIMultiFactor());
            Complex rawCompensation = steady.machineCurrent.negate()
                    .subtract(actualGeneratorCurrent).divide(voltage);
            compensationY = new Complex(0.0, rawCompensation.getImaginary());
            initialized = true;
            setPm(requestedP);
            setEfd(0.0);
            updateOutputs();
            if (driveTrain != null) {
                driveTrain.initialize(electricalTorque, getSpeed(), bus.getNetwork().getFrequency());
            }
            return true;
        }

        @Override
        public boolean nextStepElectricalModifiedEuler(double dt, DynamicSimuMethod method,
                Network<?, ?> network, int flag) {
            if (method != DynamicSimuMethod.MODIFIED_EULER) {
                throw new UnsupportedOperationException("WT1G1 supports MODIFIED_EULER only");
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
            double saturation = 0.0;
            CircuitPoint point = circuitAtSaturation(voltage, psi1, psi2, saturation);
            for (int iteration = 0; iteration < 30; iteration++) {
                double updated = saturation(point.airGapFlux.abs());
                if (Math.abs(updated - saturation) <= 1.0e-12) return point;
                saturation = updated;
                point = circuitAtSaturation(voltage, psi1, psi2, saturation);
            }
            return point;
        }

        private CircuitPoint circuitAtSaturation(Complex voltage, Complex psi1,
                Complex psi2, double saturation) {
            double a = (1.0 + saturation) / xm + 1.0 / xr1
                    + (data.twoCage() ? 1.0 / xr2 : 0.0);
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

        private double saturation(double flux) {
            if (data.se1() <= EPS || data.se2() <= EPS || data.e2() <= data.e1()
                    || flux <= 0.0) return 0.0;
            double alpha = Math.sqrt(data.e2() * data.se2() / (data.e1() * data.se1()));
            if (Math.abs(alpha - 1.0) < EPS) return data.se1();
            double threshold = (alpha * data.e1() - data.e2()) / (alpha - 1.0);
            if (flux <= threshold) return 0.0;
            double coefficient = data.se1() * data.e1() / Math.pow(data.e1() - threshold, 2.0);
            return coefficient * Math.pow(flux - threshold, 2.0) / flux;
        }

        private double solveSlip(double requestedP, double voltage) {
            double right = -1.0e-8;
            double rightError = generatorPower(right, voltage) - requestedP;
            double left = right;
            double leftError = rightError;
            double magnitude = 1.0e-7;
            while (magnitude <= 1.0) {
                left = -magnitude;
                leftError = generatorPower(left, voltage) - requestedP;
                if (leftError >= 0.0) break;
                right = left;
                rightError = leftError;
                magnitude *= 1.25;
            }
            if (leftError < 0.0 || rightError > 0.0) {
                throw new IllegalStateException("WT1G1 operating point has no stable slip solution");
            }
            for (int iteration = 0; iteration < 100; iteration++) {
                double middle = 0.5 * (left + right);
                double error = generatorPower(middle, voltage) - requestedP;
                if (Math.abs(error) < 1.0e-12) return middle;
                if (error >= 0.0) left = middle;
                else right = middle;
            }
            return 0.5 * (left + right);
        }

        private double generatorPower(double slip, double voltage) {
            return -voltage * voltage * steadyAdmittance(slip).getReal();
        }

        private SteadyCircuit steadyCircuit(double slip, Complex voltage) {
            double saturation = 0.0;
            SteadyCircuit point = steadyCircuitAtSaturation(slip, voltage, saturation);
            for (int iteration = 0; iteration < 30; iteration++) {
                double updated = saturation(point.airGapFlux.abs());
                if (Math.abs(updated - saturation) <= 1.0e-12) return point;
                saturation = updated;
                point = steadyCircuitAtSaturation(slip, voltage, saturation);
            }
            return point;
        }

        private SteadyCircuit steadyCircuitAtSaturation(double slip, Complex voltage,
                double saturation) {
            Complex ym = Complex.ONE.divide(new Complex(0.0, xm / (1.0 + saturation)));
            Complex y1 = Complex.ONE.divide(new Complex(rr1 / slip, xr1));
            Complex y2 = data.twoCage()
                    ? Complex.ONE.divide(new Complex(rr2 / slip, xr2)) : Complex.ZERO;
            Complex parallelY = ym.add(y1).add(y2);
            Complex airGapVoltage = voltage.divide(
                    Complex.ONE.add(new Complex(getRa(), data.xl()).multiply(parallelY)));
            Complex machineCurrent = parallelY.multiply(airGapVoltage);
            Complex airGapFlux = airGapVoltage.divide(Complex.I);
            return new SteadyCircuit(machineCurrent, y1.multiply(airGapVoltage),
                    y2.multiply(airGapVoltage), airGapFlux);
        }

        private Complex steadyAdmittance(double slip) {
            return steadyCircuit(slip, Complex.ONE).machineCurrent;
        }

        private void updateOutputs() {
            CircuitPoint point = circuit(machineVoltage(), rotorFlux1, rotorFlux2);
            Complex power = machineVoltage().multiply(point.machineCurrent.conjugate());
            electricalTorque = -power.getReal();
            machineQ = power.getImaginary();
            setPe(electricalTorque);
            setQGen(-machineQ + Math.pow(machineVoltage().abs(), 2.0)
                    * compensationY.getImaginary());
        }

        @Override
        public Hashtable<String, Object> getStates(Object ref) {
            updateOutputs();
            Hashtable<String, Object> states = new Hashtable<>();
            states.putAll(getNamedStates());
            states.put("WT1G1 Machine Q", -machineQ * getIMultiFactor());
            states.put("WT1G1 Telec", electricalTorque);
            states.put("WT1G1 Q Compensation", compensationY.getImaginary()
                    * getIMultiFactor());
            states.put("WT1G1 Slip", getSlip());
            return states;
        }
    }

    private record CircuitPoint(Complex machineCurrent, Complex rotorCurrent1,
            Complex rotorCurrent2, Complex airGapFlux) { }
    private record SteadyCircuit(Complex machineCurrent, Complex rotorCurrent1,
            Complex rotorCurrent2, Complex airGapFlux) { }
    private record Derivatives(Complex rotor1, Complex rotor2) { }
}
