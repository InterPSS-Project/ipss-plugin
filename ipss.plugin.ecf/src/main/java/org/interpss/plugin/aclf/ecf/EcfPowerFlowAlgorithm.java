package org.interpss.plugin.aclf.ecf;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.sparse.impl.csj.CSJSparseEqnDoubleImpl;
import com.interpss.core.sparse.impl.klu.KLUSparseEqnDoubleImpl;
import static org.interpss.plugin.aclf.ecf.EcfNetworkModel.Kind.*;
import static org.interpss.plugin.aclf.ecf.EcfPowerFlowResult.Status.*;

/** Rectangular current-balance Newton PF with explicit PV reactive variables.
 * Each invocation owns all state and factors; instances may be reused or called
 * concurrently for networks that are not being modified by another caller. */
public final class EcfPowerFlowAlgorithm {
    public EcfPowerFlowResult solve(BaseAclfNetwork<?, ?> network) {
        return solve(network, EcfPowerFlowConfig.defaults());
    }
    public EcfPowerFlowResult solve(BaseAclfNetwork<?, ?> network, EcfPowerFlowConfig config) {
        Objects.requireNonNull(config, "config");
        try {
            return solve(EcfNetworkModel.compile(network, config.freezeAutomaticControls()), config);
        } catch (UnsupportedOperationException e) {
            return failure(UNSUPPORTED_MODEL, e.getMessage());
        } catch (IllegalArgumentException e) {
            return failure(INVALID_INPUT, e.getMessage());
        }
    }
    public EcfPowerFlowResult solve(EcfNetworkModel model, EcfPowerFlowConfig config) {
        Objects.requireNonNull(model); Objects.requireNonNull(config);
        var work = new Workspace(model, config);
        EcfPowerFlowResult invalid = work.initialize();
        if (invalid != null) return invalid;
        for (int iteration = 0; iteration <= config.maximumIterations(); iteration++) {
            var evaluation = work.evaluate(work.voltage, work.q);
            if (!Double.isFinite(evaluation.norm())) return work.result(NUMERICAL_FAILURE, "Nonfinite residual", iteration, evaluation);
            if (evaluation.current <= config.tolerance() && evaluation.power <= config.tolerance()
                    && evaluation.constraint <= config.tolerance())
                return work.result(CONVERGED, "Current and original power balance verified", iteration, evaluation);
            if (iteration == config.maximumIterations())
                return work.result(NO_CONVERGENCE, "Maximum Newton iterations", iteration, evaluation);
            try {
                var matrix = work.jacobian();
                for (int i = 0; i < work.dimension; i++) matrix.setBi(-evaluation.residual[i], i);
                matrix.solveEqn(true);
                double[] step = new double[work.dimension];
                for (int i = 0; i < step.length; i++) {
                    step[i] = matrix.getXi(i);
                    if (!Double.isFinite(step[i])) throw new ArithmeticException("Nonfinite Newton correction");
                }
                work.checkLinearResidual(matrix, step, evaluation.residual);
                double scale = 1;
                for (int i = 0; i < model.busCount(); i++) if (work.column[i] >= 0) {
                    int c = work.column[i];
                    double change = Math.hypot(step[c], step[c + 1]);
                    if (change > config.voltageStepLimit()) scale = Math.min(scale, config.voltageStepLimit() / change);
                }
                boolean accepted = false;
                for (int backtrack = 0; backtrack < config.maximumBacktracks(); backtrack++, scale *= .5) {
                    Complex[] trial = work.voltage.clone(); double[] q = work.q.clone();
                    for (int i = 0; i < model.busCount(); i++) {
                        int c = work.column[i];
                        if (c >= 0) trial[i] = trial[i].add(new Complex(scale * step[c], scale * step[c + 1]));
                        if (work.qColumn[i] >= 0) q[i] += scale * step[work.qColumn[i]];
                    }
                    var next = work.evaluate(trial, q);
                    if (Double.isFinite(next.norm()) && next.norm() <= evaluation.norm() * (1 - 1e-4 * scale)) {
                        work.voltage = trial; work.q = q; accepted = true; break;
                    }
                }
                if (!accepted) return work.result(NO_CONVERGENCE, "No decreasing finite Newton trial", iteration, evaluation);
            } catch (IpssNumericException | ArithmeticException e) {
                return work.result(NUMERICAL_FAILURE, e.getMessage(), iteration, evaluation);
            }
        }
        throw new AssertionError("Unreachable iteration exit");
    }
    private static EcfPowerFlowResult failure(EcfPowerFlowResult.Status status, String detail) {
        return new EcfPowerFlowResult(status, detail, 0, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Map.of(), Map.of(), Map.of());
    }
    private record Evaluation(double[] residual, double current, double power, double constraint) {
        double norm() { return Math.max(Math.max(current, power), constraint); }
    }
    private static final class Workspace {
        final EcfNetworkModel model;
        final EcfPowerFlowConfig config;
        final int[] column, qColumn;
        final int dimension;
        Complex[] voltage;
        double[] q;
        Workspace(EcfNetworkModel model, EcfPowerFlowConfig config) {
            this.model = model; this.config = config;
            column = new int[model.busCount()]; qColumn = column.clone();
            Arrays.fill(column, -1); Arrays.fill(qColumn, -1);
            int next = 0;
            for (int i = 0; i < column.length; i++) if (model.bus(i).kind() != REFERENCE) { column[i] = next; next += 2; }
            for (int i = 0; i < column.length; i++) if (model.bus(i).kind() == PV) qColumn[i] = next++;
            dimension = next; voltage = new Complex[column.length]; q = new double[column.length];
        }
        EcfPowerFlowResult initialize() {
            boolean[] seen = new boolean[column.length];
            for (int start = 0; start < seen.length; start++) if (!seen[start]) {
                var queue = new ArrayDeque<Integer>(); var island = new java.util.ArrayList<Integer>();
                queue.add(start); seen[start] = true; int references = 0; double angle = 0;
                while (!queue.isEmpty()) {
                    int i = queue.remove(); island.add(i);
                    if (model.bus(i).kind() == REFERENCE) { references++; angle = model.bus(i).desiredAngle(); }
                    for (var entry : model.row(i)) if (entry.column() != i && !seen[entry.column()]) {
                        queue.add(entry.column()); seen[entry.column()] = true;
                    }
                }
                if (references != 1) {
                    boolean supplied = island.stream().anyMatch(i -> model.bus(i).kind() != PQ || model.bus(i).generation().abs() > 0);
                    return failure(supplied ? INVALID_INPUT : UNSUPPLIED_ISLAND,
                            "Exactly one swing bus required per island containing " + model.bus(start).id());
                }
                for (int i : island) {
                    var bus = model.bus(i); q[i] = bus.generation().getImaginary();
                    if (bus.kind() == REFERENCE) voltage[i] = polar(bus.desiredMagnitude(), bus.desiredAngle());
                    else if (config.initialization() == EcfPowerFlowConfig.Initialization.SUPPLIED) voltage[i] = bus.initialVoltage();
                    else voltage[i] = polar(bus.kind() == PV ? bus.desiredMagnitude() : 1, angle);
                    if (voltage[i] == null || voltage[i].isNaN() || voltage[i].isInfinite() || voltage[i].abs() < 1e-12)
                        return failure(INVALID_INPUT, "Finite nonzero initial voltage required at " + bus.id());
                }
            }
            return null;
        }
        private static Complex polar(double magnitude, double angle) {
            return new Complex(magnitude * Math.cos(angle), magnitude * Math.sin(angle));
        }
        Evaluation evaluate(Complex[] v, double[] reactive) {
            double[] residual = new double[dimension];
            double maxI = 0, maxPq = 0, maxV = 0;
            var networkCurrent = model.currents(v);
            for (int i = 0; i < v.length; i++) {
                if (column[i] < 0) continue;
                var bus = model.bus(i); double x = v[i].getReal(), y = v[i].getImaginary(), d = x*x + y*y;
                if (!(d > 1e-24) || !Double.isFinite(d))
                    return new Evaluation(residual, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
                var netLoad = new EcfLoadModel(bus.load().p() - bus.generation().getReal(), bus.load().q() - reactive[i]);
                var current = networkCurrent[i].add(netLoad.evaluate(x, y, d).current());
                int c = column[i]; residual[c] = current.getReal(); residual[c+1] = current.getImaginary();
                var mismatch = v[i].multiply(current.conjugate());
                maxI = Math.max(maxI, Math.max(Math.abs(residual[c]), Math.abs(residual[c+1])));
                maxPq = Math.max(maxPq, Math.max(Math.abs(mismatch.getReal()), Math.abs(mismatch.getImaginary())));
                if (qColumn[i] >= 0) {
                    double constraint = d - bus.desiredMagnitude()*bus.desiredMagnitude();
                    residual[qColumn[i]] = constraint; maxV = Math.max(maxV, Math.abs(constraint));
                }
            }
            return new Evaluation(residual, maxI, maxPq, maxV);
        }
        ISparseEqnDouble jacobian() {
            ISparseEqnDouble matrix = config.backend() == EcfPowerFlowConfig.Backend.KLU
                    ? new KLUSparseEqnDoubleImpl(dimension) : new CSJSparseEqnDoubleImpl(dimension);
            for (int i = 0; i < voltage.length; i++) {
                int row = column[i]; if (row < 0) continue;
                for (var entry : model.row(i)) {
                    int c = column[entry.column()]; if (c < 0) continue;
                    double g = entry.value().getReal(), b = entry.value().getImaginary();
                    matrix.addToAij(g, row, c); matrix.addToAij(-b, row, c+1);
                    matrix.addToAij(b, row+1, c); matrix.addToAij(g, row+1, c+1);
                }
                var bus = model.bus(i); double x = voltage[i].getReal(), y = voltage[i].getImaginary(), d = x*x+y*y;
                var local = new EcfLoadModel(bus.load().p()-bus.generation().getReal(), bus.load().q()-q[i]).evaluate(x, y, d);
                var dx = local.dReal().add(local.dSquaredMagnitude().multiply(2*x));
                var dy = local.dImaginary().add(local.dSquaredMagnitude().multiply(2*y));
                matrix.addToAij(dx.getReal(), row, row); matrix.addToAij(dy.getReal(), row, row+1);
                matrix.addToAij(dx.getImaginary(), row+1, row); matrix.addToAij(dy.getImaginary(), row+1, row+1);
                int qc = qColumn[i];
                if (qc >= 0) {
                    matrix.addToAij(-y/d, row, qc); matrix.addToAij(x/d, row+1, qc);
                    matrix.addToAij(2*x, qc, row); matrix.addToAij(2*y, qc, row+1);
                }
            }
            return matrix;
        }
        void checkLinearResidual(ISparseEqnDouble matrix, double[] step, double[] residual) {
            double error = 0, normA = 0, normX = 0, normB = 0;
            for (double x : step) normX = Math.max(normX, Math.abs(x));
            for (int i = 0; i < dimension; i++) {
                double sum = 0, rowSum = 0;
                // Auditing only populated network/diagonal/PV columns keeps this O(nnz).
                // Both selected backends expose the original sparse row storage.
                var row = ((com.interpss.core.sparse.impl.AbstractSparseEqnDoubleImpl) matrix).getElem(i);
                sum = row.aii * step[i]; rowSum = Math.abs(row.aii);
                for (var entry : row.aijList) { sum += entry.aij * step[entry.j]; rowSum += Math.abs(entry.aij); }
                error = Math.max(error, Math.abs(sum + residual[i]));
                normA = Math.max(normA, rowSum); normB = Math.max(normB, Math.abs(residual[i]));
            }
            if (!Double.isFinite(error) || error > 1e-10 * Math.max(1e-30, normA*normX + normB))
                throw new ArithmeticException("Newton linear solve failed backward-error check");
        }
        EcfPowerFlowResult result(EcfPowerFlowResult.Status status, String detail, int iterations, Evaluation evaluation) {
            var voltages = new LinkedHashMap<String, Complex>(); var generation = new LinkedHashMap<String, Complex>();
            var currents = model.currents(voltage);
            for (int i = 0; i < voltage.length; i++) {
                var bus = model.bus(i); voltages.put(bus.id(), voltage[i]);
                Complex power = bus.kind() == REFERENCE
                        ? voltage[i].multiply(currents[i].conjugate()).add(new Complex(bus.load().p(), bus.load().q()))
                        : new Complex(bus.generation().getReal(), q[i]);
                generation.put(bus.id(), power);
            }
            var flows = new LinkedHashMap<String, EcfPowerFlowResult.TerminalFlow>();
            for (var branch : model.branches()) {
                var f = voltage[branch.from()]; var t = voltage[branch.to()];
                flows.put(branch.id(), new EcfPowerFlowResult.TerminalFlow(branch.fromCurrent(f,t), branch.toCurrent(f,t),
                        branch.fromPower(f,t), branch.toPower(f,t)));
            }
            return new EcfPowerFlowResult(status, detail, iterations, evaluation.current, evaluation.power,
                    evaluation.constraint, voltages, generation, flows);
        }
    }
}
