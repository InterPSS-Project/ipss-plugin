package org.interpss.dstab.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;

/**
 * Small-signal analyzer for the unsaturated local-voltage
 * REPC_A/REEC_A/REGC_A reactive-control loop.
 *
 * <p>The analyzer combines the solved network's incremental reactive-current
 * to voltage-magnitude sensitivity with analytic controller derivatives. It
 * intentionally excludes limit switching, voltage-dip logic, active-power
 * coupling, remote measurements, and non-local plant-control configurations.
 * It is a model-localization diagnostic, not a general DStab eigenanalysis.</p>
 */
public final class LocalRenewableQvEigenAnalyzer {
    public static final int STATES_PER_DEVICE = 6;
    private static final double SWING_GROUND_ADMITTANCE = 1.0e10;

    private LocalRenewableQvEigenAnalyzer() { }

    /**
     * Analyze all eligible active converters attached to the selected buses.
     * The network must contain a converged load-flow operating point.
     */
    public static Analysis analyze(BaseDStabNetwork<?, ?> network, List<String> busIds)
            throws IpssNumericException {
        Objects.requireNonNull(network, "network");
        List<String> selectedBuses = validateBuses(network, busIds);
        double[][] coupling = voltageMagnitudeSensitivity(network, selectedBuses);
        List<Device> devices = discoverDevices(network, selectedBuses);
        if (devices.isEmpty()) {
            throw new IllegalArgumentException(
                    "No eligible local-voltage REPC_A/REEC_A/REGC_A devices");
        }
        double[][] stateMatrix = stateMatrix(coupling, devices);
        return new Analysis(selectedBuses, coupling, devices, stateMatrix,
                dominantMode(stateMatrix, devices));
    }

    private static List<String> validateBuses(BaseDStabNetwork<?, ?> network,
            List<String> busIds) {
        Objects.requireNonNull(busIds, "busIds");
        if (busIds.isEmpty()) throw new IllegalArgumentException("busIds must not be empty");
        List<String> result = List.copyOf(busIds);
        if (new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException("busIds must not contain duplicates");
        }
        for (String busId : result) {
            if (network.getBus(busId) == null) {
                throw new IllegalArgumentException("Unknown DStab bus: " + busId);
            }
        }
        return result;
    }

    private static double[][] voltageMagnitudeSensitivity(BaseDStabNetwork<?, ?> network,
            List<String> busIds) throws IpssNumericException {
        ISparseEqnComplex y = network.formYMatrix();
        network.getBusList().stream().filter(bus -> bus.isSwing()).forEach(bus ->
                y.setA(new Complex(0.0, SWING_GROUND_ADMITTANCE),
                        bus.getSortNumber(), bus.getSortNumber()));
        y.factorization(1.0e-20);
        double[][] coupling = new double[busIds.size()][busIds.size()];
        for (int column = 0; column < busIds.size(); column++) {
            y.setB2Zero();
            y.setBi(new Complex(0.0, -1.0),
                    network.getBus(busIds.get(column)).getSortNumber());
            y.solveEqn();
            for (int row = 0; row < busIds.size(); row++) {
                var bus = network.getBus(busIds.get(row));
                Complex voltage = bus.getVoltage();
                Complex deltaVoltage = y.getX(bus.getSortNumber());
                coupling[row][column] = deltaVoltage.multiply(voltage.conjugate()).getReal()
                        / voltage.abs();
            }
        }
        return coupling;
    }

    private static List<Device> discoverDevices(BaseDStabNetwork<?, ?> network,
            List<String> busIds) {
        List<Device> devices = new ArrayList<>();
        for (int busIndex = 0; busIndex < busIds.size(); busIndex++) {
            String busId = busIds.get(busIndex);
            var bus = network.getBus(busId);
            for (var rawGen : bus.getContributeGenList()) {
                if (!(rawGen instanceof DStabGen gen) || !gen.isActive()
                        || !(gen.getDynamicGenDevice() instanceof Regca1Model converter)) {
                    continue;
                }
                Reeca1Model reeca = converter.getReeca1Controller();
                if (reeca == null || reeca.getData().qFlag() != 1
                        || reeca.getData().vFlag() != 1) continue;
                Repca1Model repca = reeca.getPlantController();
                if (repca == null) continue;
                var plantData = repca.getData();
                if (plantData.refFlag() != 1 || plantData.remoteBus() != 0
                        || plantData.branchFromBus() != 0
                        || plantData.branchToBus() != 0) continue;
                var converterData = converter.getData();
                var reecaData = reeca.getData();
                requireDynamicTimeConstant(converterData.tg(), "REGCA1 Tg", busId, gen);
                requireDynamicTimeConstant(plantData.tfltr(), "REPCA1 Tfltr", busId, gen);
                requireDynamicTimeConstant(plantData.tfv(), "REPCA1 Tfv", busId, gen);
                double deviceBase = gen.getMvaBase() > 0.0
                        ? gen.getMvaBase() : network.getBaseMva();
                double v0 = bus.getVoltageMag();
                double iq0 = gen.getGen().getImaginary() * network.getBaseMva()
                        / deviceBase / v0;
                devices.add(new Device(busId, gen.getId(), busIndex,
                        deviceBase / network.getBaseMva(), v0, iq0,
                        converterData.tg(), reecaData.kqp(), reecaData.kqi(),
                        reecaData.kvp(), reecaData.kvi(), plantData.tfltr(),
                        plantData.kp(), plantData.ki(), plantData.tft(),
                        plantData.tfv()));
            }
        }
        return List.copyOf(devices);
    }

    private static void requireDynamicTimeConstant(double value, String name,
            String busId, DStabGen gen) {
        if (!(value > 0.0)) {
            throw new IllegalArgumentException(name + " must be positive for eigenanalysis at "
                    + busId + ":" + gen.getId() + "; algebraic elimination is not supported");
        }
    }

    private static double[][] stateMatrix(double[][] coupling, List<Device> devices) {
        int count = devices.size();
        double[][] state = new double[STATES_PER_DEVICE * count]
                [STATES_PER_DEVICE * count];
        for (int i = 0; i < count; i++) {
            Device device = devices.get(i);
            double leadRatio = device.tft() / device.tfv();
            int base = STATES_PER_DEVICE * i;
            state[base][base] = -1.0 / device.tfltr();
            state[base + 1][base] = -device.plantKi();
            state[base + 2][base] = -device.plantKp() / device.tfv();
            state[base + 2][base + 1] = 1.0 / device.tfv();
            state[base + 2][base + 2] = -1.0 / device.tfv();

            double[] qError = new double[state.length];
            qError[base] = -leadRatio * device.plantKp();
            qError[base + 1] = leadRatio;
            qError[base + 2] = 1.0 - leadRatio;
            for (int j = 0; j < count; j++) {
                Device source = devices.get(j);
                double qSensitivity = device.iq0()
                        * coupling[device.busIndex()][source.busIndex()]
                        * source.systemScale();
                if (i == j) qSensitivity += device.v0();
                int sourceIq = STATES_PER_DEVICE * j + 5;
                qError[sourceIq] -= qSensitivity;
                state[base][sourceIq] += coupling[device.busIndex()][source.busIndex()]
                        * source.systemScale() / device.tfltr();
            }
            for (int column = 0; column < state.length; column++) {
                state[base + 3][column] += device.kqi() * qError[column];
                state[base + 4][column] += device.kvi() * device.kqp()
                        * qError[column];
                state[base + 5][column] += device.kvp() * device.kqp()
                        * qError[column] / device.tg();
            }
            state[base + 4][base + 3] += device.kvi();
            state[base + 5][base + 3] += device.kvp() / device.tg();
            state[base + 5][base + 4] += 1.0 / device.tg();
            state[base + 5][base + 5] -= 1.0 / device.tg();
        }
        return state;
    }

    private static Mode dominantMode(double[][] state, List<Device> devices) {
        EigenDecomposition decomposition = new EigenDecomposition(
                new Array2DRowRealMatrix(state, false));
        int dominant = java.util.stream.IntStream.range(0, state.length).boxed()
                .max(Comparator.comparingDouble(decomposition::getRealEigenvalue))
                .orElseThrow();
        double[] real = decomposition.getEigenvector(dominant).toArray();
        double[] quadrature = new double[real.length];
        double imaginary = decomposition.getImagEigenvalue(dominant);
        double[] magnitude = java.util.Arrays.stream(real).map(Math::abs).toArray();
        if (Math.abs(imaginary) > 1.0e-9) {
            int conjugate = java.util.stream.IntStream.range(0, state.length)
                    .filter(index -> index != dominant)
                    .filter(index -> Math.abs(decomposition.getRealEigenvalue(index)
                                    - decomposition.getRealEigenvalue(dominant)) < 1.0e-8
                            && Math.abs(decomposition.getImagEigenvalue(index) + imaginary)
                                    < 1.0e-8)
                    .findFirst().orElseThrow();
            quadrature = decomposition.getEigenvector(conjugate).toArray();
            for (int index = 0; index < magnitude.length; index++) {
                magnitude[index] = Math.hypot(real[index], quadrature[index]);
            }
        }
        double maximum = java.util.Arrays.stream(magnitude).max().orElseThrow();
        List<StateComponent> components = new ArrayList<>(state.length);
        for (int index = 0; index < state.length; index++) {
            Device device = devices.get(index / STATES_PER_DEVICE);
            components.add(new StateComponent(device.deviceId(),
                    stateName(index % STATES_PER_DEVICE), real[index] / maximum,
                    quadrature[index] / maximum, magnitude[index] / maximum));
        }
        return new Mode(decomposition.getRealEigenvalue(dominant), imaginary,
                List.copyOf(components));
    }

    public static String stateName(int index) {
        return switch (index) {
            case 0 -> "REPCA_VFILT";
            case 1 -> "REPCA_Q_PI";
            case 2 -> "REPCA_LEAD_LAG";
            case 3 -> "REECA_Q_PI";
            case 4 -> "REECA_V_PI";
            case 5 -> "REGCA_IQ";
            default -> throw new IllegalArgumentException("Unknown Q/V state index: " + index);
        };
    }

    public record Device(String busId, String unitId, int busIndex,
            double systemScale, double v0, double iq0, double tg,
            double kqp, double kqi, double kvp, double kvi,
            double tfltr, double plantKp, double plantKi,
            double tft, double tfv) {
        public String deviceId() { return busId + ":" + unitId; }
    }

    public record StateComponent(String deviceId, String state,
            double normalizedReal, double normalizedImaginary, double magnitude) { }

    public record Mode(double real, double imaginary,
            List<StateComponent> components) {
        public Mode {
            components = List.copyOf(components);
        }

        public double frequencyHz() { return Math.abs(imaginary) / (2.0 * Math.PI); }

        /**
         * Right-eigenvector components ranked by normalized magnitude. These
         * are modal-shape components, not left/right eigenvector participation
         * factors.
         */
        public List<StateComponent> participation() {
            return components.stream()
                    .sorted(Comparator.comparingDouble(StateComponent::magnitude).reversed())
                    .toList();
        }
    }

    public record Analysis(List<String> busIds, double[][] couplingMatrix,
            List<Device> devices, double[][] stateMatrix, Mode dominantMode) {
        public Analysis {
            busIds = List.copyOf(busIds);
            couplingMatrix = copy(couplingMatrix);
            devices = List.copyOf(devices);
            stateMatrix = copy(stateMatrix);
            Objects.requireNonNull(dominantMode, "dominantMode");
        }

        @Override public double[][] couplingMatrix() { return copy(couplingMatrix); }
        @Override public double[][] stateMatrix() { return copy(stateMatrix); }
    }

    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int row = 0; row < matrix.length; row++) result[row] = matrix[row].clone();
        return result;
    }
}
