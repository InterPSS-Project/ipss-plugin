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

/** PSS/E WT2G1 Type-2 induction generator with controlled rotor resistance. */
public final class Wt2g1Machine extends DynamicMachineImpl implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Wt2g1Data data;
    private final Wt2g1Solver solver = new Wt2g1Solver();
    private Wt2e1Model rotorResistanceController;
    private Complex compensationY = Complex.ZERO;

    public Wt2g1Machine(Wt2g1Data data) {
        this.data = data;
        this._dEqnSolver = solver;
    }

    public Wt2g1Data getWt2g1Data() { return data; }
    public double getSlip() { return 1.0 - getSpeed(); }
    public double getRotorResistance() { return solver.rotorResistance; }
    public double getRotorControlVoltage() { return solver.rotorControlVoltage; }
    public double getElectricalTorque() { return solver.electricalTorque; }
    public double getCompensationSusceptance() { return compensationY.getImaginary(); }
    public Wt2e1Model getRotorResistanceController() { return rotorResistanceController; }
    public void setRotorResistanceController(Wt2e1Model model) {
        rotorResistanceController = model;
    }

    @Override
    public boolean checkData(DataCheckConfiguration config) {
        return getRating() > EPS && getRatedVoltage() > EPS && getRa() >= 0.0;
    }

    @Override
    public double calculateIfd(MachineIfdBase base) { return 0.0; }

    @Override
    public Complex getYgen() {
        double scale = getIMultiFactor() / getVMultiFactor();
        Complex sourceY = Complex.ONE.divide(new Complex(getRa(), data.transientReactance()));
        return sourceY.add(compensationY).multiply(scale);
    }

    @Override
    public Complex getIgen() {
        if (!solver.initialized) return Complex.ZERO;
        Complex voltage = machineVoltage();
        CircuitPoint point = solver.circuit(voltage, solver.rotorFlux);
        Complex fixedY = Complex.ONE.divide(new Complex(getRa(), data.transientReactance()));
        return point.machineCurrent.negate().add(fixedY.multiply(voltage))
                .multiply(getIMultiFactor());
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        boolean result = super.updateAttributes(netChange);
        if (solver.initialized) solver.updateOutputs();
        return result;
    }

    /** WT2G1 obtains speed from its static power/slip characteristic. */
    @Override
    public boolean nextStepMechanical(double dt, DynamicSimuMethod method,
            Network network, int flag) {
        solver.updateOutputs();
        if (rotorResistanceController != null) {
            rotorResistanceController.step(dt, getSpeed() - 1.0,
                    solver.electricalTorque, flag);
            solver.setRotorControlVoltage(rotorResistanceController.getOutput());
        }
        return true;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!solver.initialized) return Map.of();
        Complex ep = solver.transientVoltage(solver.rotorFlux);
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("E'q", ep.getReal());
        states.put("E'd", ep.getImaginary());
        states.put("Internal", solver.internalState);
        return Map.copyOf(states);
    }

    private Complex machineVoltage() {
        return getDStabBus().getVoltage().divide(getVMultiFactor());
    }

    private final class Wt2g1Solver extends DynamicSimuAdapterImpl {
        private Complex rotorFlux = Complex.ZERO;
        private Complex oldRotorFlux = Complex.ZERO;
        private Complex derivative = Complex.ZERO;
        private double rotorResistance;
        private double rotorControlVoltage;
        private double electricalTorque;
        private double machineQ;
        private double internalState;
        private boolean initialized;

        @Override
        public boolean initStates(BaseDStabBus<?, ?> bus) {
            super.initStates(bus);
            Complex voltage = machineVoltage();
            double requestedP = getParentGen().getGen().getReal() / getRating();
            if (requestedP <= EPS) {
                throw new IllegalStateException("WT2G1 requires a positive-P generator operating point");
            }
            double speedDeviation = data.speedDeviation(requestedP);
            setSpeed(1.0 + speedDeviation);
            rotorResistance = solveRotorResistance(requestedP, voltage.abs(), getSlip());

            SteadyCircuit steady = steadyCircuit(getSlip(), voltage, rotorResistance);
            rotorFlux = steady.airGapFlux.subtract(steady.rotorCurrent.multiply(data.x1()));
            rotorControlVoltage = (rotorResistance - data.rotorResistance())
                    / Math.max(EPS, data.maximumRotorResistance() - data.rotorResistance());
            Complex actualGeneratorCurrent = getParentGen().getGen().divide(bus.getVoltage())
                    .conjugate().divide(getIMultiFactor());
            Complex rawCompensation = steady.machineCurrent.negate()
                    .subtract(actualGeneratorCurrent).divide(voltage);
            compensationY = new Complex(0.0, rawCompensation.getImaginary());
            initialized = true;
            setPm(requestedP);
            setEfd(0.0);
            updateOutputs();
            if (rotorResistanceController != null) {
                rotorResistanceController.initialize(getSpeed() - 1.0,
                        electricalTorque, rotorControlVoltage);
            }
            return true;
        }

        @Override
        public boolean nextStepElectricalModifiedEuler(double dt, DynamicSimuMethod method,
                Network<?, ?> network, int flag) {
            if (method != DynamicSimuMethod.MODIFIED_EULER) {
                throw new UnsupportedOperationException("WT2G1 supports MODIFIED_EULER only");
            }
            Complex currentDerivative = derivative(machineVoltage(), rotorFlux, network.getFrequency());
            if (flag == 0) {
                oldRotorFlux = rotorFlux;
                derivative = currentDerivative;
                rotorFlux = oldRotorFlux.add(derivative.multiply(dt));
            } else {
                rotorFlux = oldRotorFlux.add(derivative.add(currentDerivative).multiply(0.5 * dt));
            }
            updateOutputs();
            return true;
        }

        private Complex derivative(Complex voltage, Complex flux, double frequency) {
            CircuitPoint point = circuit(voltage, flux);
            double omega0 = 2.0 * Math.PI * frequency;
            return point.rotorCurrent.multiply(rotorResistance)
                    .subtract(flux.multiply(Complex.I.multiply(getSlip()))).multiply(omega0);
        }

        private void setRotorControlVoltage(double value) {
            rotorControlVoltage = value;
            double resistance = data.rotorResistance() + Math.max(0.0, value)
                    * (data.maximumRotorResistance() - data.rotorResistance());
            rotorResistance = Math.max(data.rotorResistance(),
                    Math.min(data.maximumRotorResistance(), resistance));
        }

        private CircuitPoint circuit(Complex voltage, Complex flux) {
            double saturation = 0.0;
            CircuitPoint point = circuitAtSaturation(voltage, flux, saturation);
            for (int iteration = 0; iteration < 30; iteration++) {
                double updated = saturation(point.airGapFlux.abs());
                if (Math.abs(updated - saturation) <= 1.0e-12) return point;
                saturation = updated;
                point = circuitAtSaturation(voltage, flux, saturation);
            }
            return point;
        }

        private CircuitPoint circuitAtSaturation(Complex voltage, Complex flux,
                double saturation) {
            double a = (1.0 + saturation) / data.xm() + 1.0 / data.x1();
            Complex b = flux.divide(data.x1());
            Complex zLeakage = new Complex(getRa(), data.xa());
            Complex airGap = voltage.add(zLeakage.multiply(b))
                    .divide(zLeakage.multiply(a).add(Complex.I));
            Complex rotorCurrent = airGap.subtract(flux).divide(data.x1());
            Complex statorCurrent = airGap.multiply(a).subtract(b);
            return new CircuitPoint(statorCurrent, rotorCurrent, airGap);
        }

        private Complex transientVoltage(Complex flux) {
            return Complex.I.multiply(flux).multiply(data.xm() / (data.xm() + data.x1()));
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

        private double solveRotorResistance(double requestedP, double voltage, double slip) {
            double lower = data.rotorResistance();
            double upper = data.maximumRotorResistance();
            double lowerError = generatorPower(lower, slip, voltage) - requestedP;
            double upperError = generatorPower(upper, slip, voltage) - requestedP;
            if (lowerError * upperError > 0.0) {
                return Math.abs(lowerError) <= Math.abs(upperError) ? lower : upper;
            }
            for (int iteration = 0; iteration < 100; iteration++) {
                double middle = 0.5 * (lower + upper);
                double error = generatorPower(middle, slip, voltage) - requestedP;
                if (Math.abs(error) < 1.0e-12) return middle;
                if (error * lowerError > 0.0) {
                    lower = middle;
                    lowerError = error;
                } else {
                    upper = middle;
                }
            }
            return 0.5 * (lower + upper);
        }

        private double generatorPower(double resistance, double slip, double voltage) {
            return -voltage * voltage * steadyAdmittance(slip, resistance).getReal();
        }

        private SteadyCircuit steadyCircuit(double slip, Complex voltage, double resistance) {
            double saturation = 0.0;
            SteadyCircuit point = steadyCircuitAtSaturation(slip, voltage, resistance, saturation);
            for (int iteration = 0; iteration < 30; iteration++) {
                double updated = saturation(point.airGapFlux.abs());
                if (Math.abs(updated - saturation) <= 1.0e-12) return point;
                saturation = updated;
                point = steadyCircuitAtSaturation(slip, voltage, resistance, saturation);
            }
            return point;
        }

        private SteadyCircuit steadyCircuitAtSaturation(double slip, Complex voltage,
                double resistance, double saturation) {
            Complex ym = Complex.ONE.divide(new Complex(0.0, data.xm() / (1.0 + saturation)));
            Complex yr = Complex.ONE.divide(new Complex(resistance / slip, data.x1()));
            Complex parallelY = ym.add(yr);
            Complex airGapVoltage = voltage.divide(
                    Complex.ONE.add(new Complex(getRa(), data.xa()).multiply(parallelY)));
            Complex machineCurrent = parallelY.multiply(airGapVoltage);
            Complex airGapFlux = airGapVoltage.divide(Complex.I);
            return new SteadyCircuit(machineCurrent, yr.multiply(airGapVoltage), airGapFlux);
        }

        private Complex steadyAdmittance(double slip, double resistance) {
            return steadyCircuit(slip, Complex.ONE, resistance).machineCurrent;
        }

        private void updateOutputs() {
            CircuitPoint point = circuit(machineVoltage(), rotorFlux);
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
            states.put("WT2G1 Machine Q", -machineQ * getIMultiFactor());
            states.put("WT2G1 Telec", electricalTorque);
            states.put("WT2G1 Hidden Shunt", compensationY.getImaginary()
                    * getIMultiFactor());
            states.put("WT2G1 Speed Deviation", getSpeed() - 1.0);
            states.put("WT2G1 Rotor Resistance", rotorResistance);
            return states;
        }
    }

    private record CircuitPoint(Complex machineCurrent, Complex rotorCurrent,
            Complex airGapFlux) { }
    private record SteadyCircuit(Complex machineCurrent, Complex rotorCurrent,
            Complex airGapFlux) { }
}
