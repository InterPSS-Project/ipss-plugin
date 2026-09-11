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
 * Candidate small-signal analyzer for the unsaturated local-voltage
 * REPC_A/REEC_A/REGC_A reactive-control loop.
 *
 * <p>The analyzer combines the solved network's incremental reactive-current
 * to voltage-magnitude sensitivity with analytic controller derivatives. It
 * reports regional Jacobians for incremental REECA/REPCA PI outputs that are
 * exactly on anti-windup limits. These distinguish an inward-moving active PI
 * branch from an outward-moving clamped branch. Each regional mode also reports
 * whether a real phase of its right eigenvector lies in the assumed state
 * tangent cone. This is an instantaneous directional test; it does not prove
 * vector-field branch consistency, persistence during an oscillation, or the
 * switching sequence. Other limit switching, voltage-dip logic, active-power
 * coupling, remote measurements, and non-local plant-control configurations
 * remain excluded. This is a model-localization diagnostic, not a general
 * DStab eigenanalysis. The returned operating-point constraints must be empty
 * before interpreting the original state matrix as a conventional two-sided
 * linearization.</p>
 */
public final class LocalRenewableQvEigenAnalyzer {
    public static final int STATES_PER_DEVICE = 6;
    private static final int MAX_ENUMERATED_BOUNDARIES = 8;
    private static final double SWING_GROUND_ADMITTANCE = 1.0e10;
    private static final double CONE_TOLERANCE = 1.0e-9;

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
        double[][] stateMatrix = stateMatrix(coupling, devices, null, null);
        List<OperatingPointConstraint> constraints = operatingPointConstraints(devices);
        RegionSet regions = limiterRegions(coupling, devices, constraints);
        return new Analysis(selectedBuses, coupling, devices, stateMatrix,
                dominantMode(stateMatrix, devices), constraints, regions.modes(),
                regions.complete());
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
                        plantData.tfv(), reecaData.vmin(), reecaData.vmax(),
                        plantData.qmin(), plantData.qmax()));
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

    private static List<OperatingPointConstraint> operatingPointConstraints(
            List<Device> devices) {
        List<OperatingPointConstraint> constraints = new ArrayList<>();
        for (Device device : devices) {
            // The linearized states are perturbations from their initialized
            // values. REECA PIQ initializes at the absolute sensed voltage, so
            // its absolute VMIN/VMAX limits must be translated by V0 before
            // testing whether the zero perturbation is on a boundary.
            addBoundaryConstraint(constraints, device.deviceId(), "REECA_PIQ", 0.0,
                    Math.min(device.reecaVmin(), device.v0()) - device.v0(),
                    Math.max(device.reecaVmax(), device.v0()) - device.v0());
            addBoundaryConstraint(constraints, device.deviceId(), "REPCA_Q_PI", 0.0,
                    Math.min(device.plantQmin(), 0.0), Math.max(device.plantQmax(), 0.0));
        }
        return List.copyOf(constraints);
    }

    private static void addBoundaryConstraint(List<OperatingPointConstraint> constraints,
            String deviceId, String signal, double value, double lower, double upper) {
        double tolerance = 1.0e-10 * Math.max(1.0, Math.max(Math.abs(lower), Math.abs(upper)));
        if (Math.abs(value - lower) <= tolerance || Math.abs(value - upper) <= tolerance) {
            constraints.add(new OperatingPointConstraint(deviceId, signal, value, lower, upper,
                    "initial output is on an anti-windup limit; the candidate Jacobian "
                            + "is not a valid two-sided linearization"));
        }
    }

    private static double[][] stateMatrix(double[][] coupling, List<Device> devices,
            boolean[] plantPiActive, boolean[] reecaPiActive) {
        int count = devices.size();
        double[][] state = new double[STATES_PER_DEVICE * count]
                [STATES_PER_DEVICE * count];
        for (int i = 0; i < count; i++) {
            Device device = devices.get(i);
            boolean plantActive = plantPiActive == null || plantPiActive[i];
            boolean reecaActive = reecaPiActive == null || reecaPiActive[i];
            double leadRatio = device.tft() / device.tfv();
            int base = STATES_PER_DEVICE * i;
            state[base][base] = -1.0 / device.tfltr();
            if (plantActive) {
                state[base + 1][base] = -device.plantKi();
                state[base + 2][base] = -device.plantKp() / device.tfv();
                state[base + 2][base + 1] = 1.0 / device.tfv();
            }
            state[base + 2][base + 2] = -1.0 / device.tfv();

            double[] qError = new double[state.length];
            if (plantActive) {
                qError[base] = -leadRatio * device.plantKp();
                qError[base + 1] = leadRatio;
            }
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
            if (reecaActive) {
                for (int column = 0; column < state.length; column++) {
                    state[base + 3][column] += device.kqi() * qError[column];
                    state[base + 4][column] += device.kvi() * device.kqp()
                            * qError[column];
                    state[base + 5][column] += device.kvp() * device.kqp()
                            * qError[column] / device.tg();
                }
                state[base + 4][base + 3] += device.kvi();
                state[base + 5][base + 3] += device.kvp() / device.tg();
            }
            state[base + 5][base + 4] += 1.0 / device.tg();
            state[base + 5][base + 5] -= 1.0 / device.tg();
        }
        return state;
    }

    private static RegionSet limiterRegions(double[][] coupling, List<Device> devices,
            List<OperatingPointConstraint> constraints) {
        int boundaryCount = constraints.size();
        boolean complete = boundaryCount <= MAX_ENUMERATED_BOUNDARIES;
        long combinations = complete ? 1L << boundaryCount : 2L;
        List<RegionalMode> modes = new ArrayList<>((int) combinations);
        for (long region = 0; region < combinations; region++) {
            boolean allActiveEnvelope = !complete && region == 1;
            boolean[] plantActive = allTrue(devices.size());
            boolean[] reecaActive = allTrue(devices.size());
            List<LimiterAssumption> assumptions = new ArrayList<>(boundaryCount);
            for (int boundary = 0; boundary < boundaryCount; boundary++) {
                OperatingPointConstraint constraint = constraints.get(boundary);
                boolean active = complete ? (region & (1L << boundary)) != 0
                        : allActiveEnvelope;
                int deviceIndex = deviceIndex(devices, constraint.deviceId());
                if (constraint.signal().equals("REECA_PIQ")) {
                    reecaActive[deviceIndex] = active;
                } else if (constraint.signal().equals("REPCA_Q_PI")) {
                    plantActive[deviceIndex] = active;
                } else {
                    throw new IllegalStateException(
                            "Unknown limiter-boundary signal: " + constraint.signal());
                }
                assumptions.add(new LimiterAssumption(constraint.deviceId(),
                        constraint.signal(), active ? BoundaryBranch.INWARD_ACTIVE
                                : BoundaryBranch.OUTWARD_CLAMPED));
            }
            double[][] matrix = stateMatrix(coupling, devices, plantActive, reecaActive);
            Mode mode = dominantMode(matrix, devices);
            modes.add(new RegionalMode(assumptions, matrix, mode,
                    tangentConeAssessment(assumptions, constraints, devices, mode)));
        }
        return new RegionSet(List.copyOf(modes), complete);
    }

    private static TangentConeAssessment tangentConeAssessment(
            List<LimiterAssumption> assumptions,
            List<OperatingPointConstraint> constraints,
            List<Device> devices, Mode mode) {
        List<TangentConstraint> tangentConstraints = new ArrayList<>(assumptions.size());
        List<Double> phaseBoundaries = new ArrayList<>();
        phaseBoundaries.add(0.0);
        for (int index = 0; index < assumptions.size(); index++) {
            LimiterAssumption assumption = assumptions.get(index);
            OperatingPointConstraint constraint = constraints.get(index);
            int stateIndex = STATES_PER_DEVICE
                    * deviceIndex(devices, assumption.deviceId())
                    + boundaryStateIndex(assumption.signal());
            StateComponent component = mode.components().get(stateIndex);
            BoundarySide side = boundarySide(constraint);
            tangentConstraints.add(new TangentConstraint(assumption.deviceId(),
                    assumption.signal(), assumption.branch(), side,
                    component.normalizedReal(), component.normalizedImaginary()));
            if (Math.hypot(component.normalizedReal(),
                    component.normalizedImaginary()) > CONE_TOLERANCE) {
                double root = normalizePhase(Math.PI / 2.0
                        - Math.atan2(component.normalizedImaginary(),
                                component.normalizedReal()));
                phaseBoundaries.add(root);
                phaseBoundaries.add(normalizePhase(root + Math.PI));
            }
        }

        List<Double> candidates = new ArrayList<>();
        if (Math.abs(mode.imaginary()) <= CONE_TOLERANCE) {
            candidates.add(0.0);
            candidates.add(Math.PI);
        } else {
            phaseBoundaries.sort(Double::compareTo);
            for (int index = 0; index < phaseBoundaries.size(); index++) {
                double start = phaseBoundaries.get(index);
                double end = index + 1 < phaseBoundaries.size()
                        ? phaseBoundaries.get(index + 1)
                        : phaseBoundaries.get(0) + 2.0 * Math.PI;
                candidates.add(start);
                candidates.add(normalizePhase(start + (end - start) / 2.0));
            }
        }
        for (double phase : candidates) {
            if (projectedModeNorm(mode, phase) > CONE_TOLERANCE
                    && tangentConstraints.stream().allMatch(
                            constraint -> satisfies(constraint, phase))) {
                return new TangentConeAssessment(true, phase, tangentConstraints);
            }
        }
        return new TangentConeAssessment(false, Double.NaN, tangentConstraints);
    }

    private static int boundaryStateIndex(String signal) {
        return switch (signal) {
            case "REPCA_Q_PI" -> 1;
            case "REECA_PIQ" -> 3;
            default -> throw new IllegalStateException(
                    "Unknown limiter-boundary signal: " + signal);
        };
    }

    private static BoundarySide boundarySide(OperatingPointConstraint constraint) {
        double tolerance = 1.0e-10 * Math.max(1.0,
                Math.max(Math.abs(constraint.lower()), Math.abs(constraint.upper())));
        boolean lower = Math.abs(constraint.value() - constraint.lower())
                <= tolerance;
        boolean upper = Math.abs(constraint.value() - constraint.upper())
                <= tolerance;
        if (lower && upper) return BoundarySide.BOTH;
        if (lower) return BoundarySide.LOWER;
        if (upper) return BoundarySide.UPPER;
        throw new IllegalStateException("Limiter constraint is not on a boundary: "
                + constraint);
    }

    private static boolean satisfies(TangentConstraint constraint, double phase) {
        double displacement = projected(constraint.normalizedReal(),
                constraint.normalizedImaginary(), phase);
        if (constraint.branch() == BoundaryBranch.OUTWARD_CLAMPED
                || constraint.side() == BoundarySide.BOTH) {
            return Math.abs(displacement) <= CONE_TOLERANCE;
        }
        return constraint.side() == BoundarySide.LOWER
                ? displacement >= -CONE_TOLERANCE
                : displacement <= CONE_TOLERANCE;
    }

    private static double projectedModeNorm(Mode mode, double phase) {
        double sum = 0.0;
        for (StateComponent component : mode.components()) {
            double value = projected(component.normalizedReal(),
                    component.normalizedImaginary(), phase);
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private static double projected(double real, double imaginary, double phase) {
        return real * Math.cos(phase) - imaginary * Math.sin(phase);
    }

    private static double normalizePhase(double phase) {
        double normalized = phase % (2.0 * Math.PI);
        return normalized < 0.0 ? normalized + 2.0 * Math.PI : normalized;
    }

    private static boolean[] allTrue(int size) {
        boolean[] values = new boolean[size];
        java.util.Arrays.fill(values, true);
        return values;
    }

    private static int deviceIndex(List<Device> devices, String deviceId) {
        for (int index = 0; index < devices.size(); index++) {
            if (devices.get(index).deviceId().equals(deviceId)) return index;
        }
        throw new IllegalStateException("Limiter constraint has no device: " + deviceId);
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
            double tft, double tfv, double reecaVmin, double reecaVmax,
            double plantQmin, double plantQmax) {
        public Device(String busId, String unitId, int busIndex,
                double systemScale, double v0, double iq0, double tg,
                double kqp, double kqi, double kvp, double kvi,
                double tfltr, double plantKp, double plantKi,
                double tft, double tfv) {
            this(busId, unitId, busIndex, systemScale, v0, iq0, tg,
                    kqp, kqi, kvp, kvi, tfltr, plantKp, plantKi, tft, tfv,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        public String deviceId() { return busId + ":" + unitId; }
    }

    public record OperatingPointConstraint(String deviceId, String signal,
            double value, double lower, double upper, String explanation) { }

    public enum BoundaryBranch {
        /** Perturbation moves the PI output into its admissible interval. */
        INWARD_ACTIVE,
        /** Perturbation drives outward and the PI output remains clamped. */
        OUTWARD_CLAMPED
    }

    public enum BoundarySide {
        LOWER,
        UPPER,
        BOTH
    }

    public record LimiterAssumption(String deviceId, String signal,
            BoundaryBranch branch) { }

    public record TangentConstraint(String deviceId, String signal,
            BoundaryBranch branch, BoundarySide side,
            double normalizedReal, double normalizedImaginary) { }

    /**
     * Whether at least one nonzero real representative of the regional right
     * eigenvector lies in every assumed limiter state tangent simultaneously.
     * The witness phase is in radians and is NaN when no such representative
     * exists. This does not test the unconstrained vector field or subsequent
     * limiter switching.
     */
    public record TangentConeAssessment(boolean feasible,
            double witnessPhaseRadians, List<TangentConstraint> constraints) {
        public TangentConeAssessment {
            constraints = List.copyOf(constraints);
            if (feasible && !Double.isFinite(witnessPhaseRadians)) {
                throw new IllegalArgumentException(
                        "a feasible tangent cone requires a finite witness phase");
            }
            if (!feasible && !Double.isNaN(witnessPhaseRadians)) {
                throw new IllegalArgumentException(
                        "an infeasible tangent cone must use a NaN witness phase");
            }
        }
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

    public record RegionalMode(List<LimiterAssumption> assumptions,
            double[][] stateMatrix, Mode dominantMode,
            TangentConeAssessment tangentCone) {
        public RegionalMode {
            assumptions = List.copyOf(assumptions);
            stateMatrix = copy(stateMatrix);
            Objects.requireNonNull(dominantMode, "dominantMode");
            Objects.requireNonNull(tangentCone, "tangentCone");
        }

        @Override public double[][] stateMatrix() { return copy(stateMatrix); }
    }

    public record Analysis(List<String> busIds, double[][] couplingMatrix,
            List<Device> devices, double[][] stateMatrix, Mode dominantMode,
            List<OperatingPointConstraint> operatingPointConstraints,
            List<RegionalMode> limiterRegionModes,
            boolean limiterRegionEnumerationComplete) {
        public Analysis {
            busIds = List.copyOf(busIds);
            couplingMatrix = copy(couplingMatrix);
            devices = List.copyOf(devices);
            stateMatrix = copy(stateMatrix);
            Objects.requireNonNull(dominantMode, "dominantMode");
            operatingPointConstraints = List.copyOf(operatingPointConstraints);
            limiterRegionModes = List.copyOf(limiterRegionModes);
        }

        @Override public double[][] couplingMatrix() { return copy(couplingMatrix); }
        @Override public double[][] stateMatrix() { return copy(stateMatrix); }
        public boolean isTwoSidedLinearizationValid() {
            return operatingPointConstraints.isEmpty();
        }

        /**
         * True when {@link #limiterRegionModes()} contains every active/clamped
         * combination. For more than eight simultaneous boundaries it contains
         * only the all-clamped and all-active envelopes to avoid exponential
         * computation.
         */
        public boolean limiterRegionEnumerationComplete() {
            return limiterRegionEnumerationComplete;
        }
    }

    private record RegionSet(List<RegionalMode> modes, boolean complete) { }

    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int row = 0; row < matrix.length; row++) result[row] = matrix[row].clone();
        return result;
    }
}
