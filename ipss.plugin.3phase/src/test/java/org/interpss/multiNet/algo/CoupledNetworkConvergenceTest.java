package org.interpss.multiNet.algo;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldMatrix;
import org.interpss.IpssCorePlugin;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNetDStabSolverImpl;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.algo.T3seqD3phaseMultiNetDStabSolverImpl;
import org.interpss.multiNet.algo.TposseqD3phaseMultiNetDStabSolverImpl;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.common.DStabSimuException;

/** Exercises the installed plugin's actual iteration loops with prescribed network solutions.
 * Physical coupled-network trajectories are covered separately by integration acceptance tests. */
class CoupledNetworkConvergenceTest {
    @BeforeAll static void init() { IpssCorePlugin.init(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void zeroIterationsCannotReportConvergence(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}});
        fixture.solver.setMaxIterationTimes(0);
        fixture.assertFailure();
        assertEquals(0, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void firstIterationOnlyEstablishesBaseline(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}});
        fixture.solver.setMaxIterationTimes(1);
        fixture.assertFailure();
        assertEquals(1, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptsFirstCompleteComparisonAndStops(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {1, 1}, {2, 2}});
        fixture.solver.setMaxIterationTimes(3);
        assertTrue(fixture.solver.networkSolutionStep());
        assertEquals(2, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptsRecoveryOnLastAllowedIteration(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {.9, .8}, {.9, .8}});
        fixture.solver.setMaxIterationTimes(3);
        assertTrue(fixture.solver.networkSolutionStep());
        assertEquals(3, fixture.network.solves, "Update every bus history even after a mismatch");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void passingLastBusCannotOverwriteEarlierViolation(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {.9, 1}, {.8, 1}});
        fixture.solver.setMaxIterationTimes(3);
        fixture.assertFailure();
        assertEquals(3, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void everyActiveBusMustPassIncludingLastBus(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {1, .9}});
        fixture.solver.setMaxIterationTimes(2);
        fixture.assertFailure();
        assertEquals(2, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nextNetworkSolveCannotReusePreviousSuccess(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {1, 1}, {.8, .8}, {.7, .7}});
        fixture.solver.setMaxIterationTimes(2);
        assertTrue(fixture.solver.networkSolutionStep());
        fixture.assertFailure();
        assertEquals(4, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failedSolveDoesNotLatchFailureInNextSolve(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {.9, .9}, {.8, .8}, {.8, .8}});
        fixture.solver.setMaxIterationTimes(2);
        fixture.assertFailure();
        assertTrue(fixture.solver.networkSolutionStep());
        assertEquals(4, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nonfiniteVoltageCannotPass(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {Double.NaN, 1}});
        fixture.solver.setMaxIterationTimes(2);
        fixture.assertFailure();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inactiveBusesDoNotPreventConvergence(boolean sequences) throws Exception {
        var fixture = new Fixture(sequences, new double[][] {{1, 1}, {1, .5}});
        fixture.network.getBus("Bus2").setStatus(false);
        fixture.solver.setMaxIterationTimes(2);
        assertTrue(fixture.solver.networkSolutionStep());
    }

    @ParameterizedTest @ValueSource(ints = {0, 2})
    void threeSequenceSolverChecksZeroAndNegativeVoltages(int component) throws Exception {
        var fixture = new Fixture(true, new double[][] {{1, 1}, {1, 1}, {1, 1}});
        fixture.network.varyingComponent = component;
        fixture.solver.setMaxIterationTimes(3);
        fixture.assertFailure();
        assertEquals(3, fixture.network.solves);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void rejectsNonfiniteValuesInEverySequenceComponent(int component) throws Exception {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
            var fixture = new Fixture(true, new double[][] {{1, 1}, {1, 1}});
            fixture.network.nonfiniteComponent = component;
            fixture.network.nonfiniteValue = value;
            fixture.solver.setMaxIterationTimes(2);
            fixture.assertFailure();
        }
    }

    private static final class Fixture {
        final ScriptedNetwork network;
        final MultiNetDStabSolverImpl solver;
        final boolean sequences;

        Fixture(boolean sequences, double[][] voltages) throws Exception {
            this.sequences = sequences;
            network = new ScriptedNetwork(voltages);
            var processor = new SubNetworkProcessor(network) {
                @Override public BaseDStabNetwork<?, ?> getExternalSubNetwork() { return network; }
                @Override public List<String> getThreePhaseSubNetIdList() { return List.of(); }
            };
            processor.getSubNetworkList().add(network);
            // No feeders or equivalents are needed to exercise convergence over transmission buses.
            var helper = new MultiNet3Ph3SeqDStabSimuHelper(network, processor) {
                @Override public Hashtable<String, FieldMatrix<Complex>> prepareInterfaceBranchBusIncidenceMatrix() {
                    return new Hashtable<>();
                }
                @Override public void processInterfaceBranchEquiv() { }
            };
            if (sequences) {
                var coupled = new T3seqD3phaseMultiNetDStabSolverImpl(null, helper);
                coupled.setTheveninEquivFlag(false);
                coupled.setDistNetSolvedByPowerflowFlag(true);
                solver = coupled;
            } else {
                var coupled = new TposseqD3phaseMultiNetDStabSolverImpl(null, helper);
                coupled.setTheveninEquivFlag(false);
                coupled.setDistNetSolvedByPowerflowFlag(true);
                solver = coupled;
            }
        }

        void assertFailure() throws Exception {
            if (sequences) {
                var failure = assertThrows(DStabSimuException.class, solver::networkSolutionStep);
                assertTrue(failure.getMessage().contains("did not converge"));
            } else {
                assertFalse(solver.networkSolutionStep());
            }
        }
    }

    private static final class ScriptedNetwork extends DStabNetwork3phaseImpl {
        final double[][] voltages;
        int solves;
        int varyingComponent = -1;
        int nonfiniteComponent = -1;
        double nonfiniteValue;

        ScriptedNetwork(double[][] voltages) throws Exception {
            this.voltages = voltages;
            setId("Transmission");
            for (int i = 0; i < 2; i++) {
                var bus = ThreePhaseObjectFactory.create3PDStabBus("Bus" + (i + 1), this);
                bus.setSortNumber(i);
                bus.setVoltage(Complex.ONE);
                bus.setThreeSeqVoltage(new Complex3x1(Complex.ZERO, Complex.ONE, Complex.ZERO));
            }
            setZeroSeqYMatrix(sequenceMatrix(0));
            setNegSeqYMatrix(sequenceMatrix(2));
        }

        @Override public boolean solvePosSeqNetEqn() {
            assertTrue(solves < voltages.length, "Solver exceeded the expected solution sequence");
            var values = voltages[solves++];
            for (int i = 0; i < values.length; i++) {
                var bus = getBus("Bus" + (i + 1));
                bus.setVoltage(new Complex(nonfiniteComponent == 1 && solves > 1 ? nonfiniteValue : values[i], 0));
                bus.getThreeSeqVoltage().b_1 = bus.getVoltage();
            }
            return true;
        }

        private ISparseEqnComplex sequenceMatrix(int component) {
            return (ISparseEqnComplex) Proxy.newProxyInstance(ISparseEqnComplex.class.getClassLoader(),
                    new Class<?>[] {ISparseEqnComplex.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "setB2Zero" -> null;
                        case "solveEqn" -> true;
                        case "getX" -> new Complex(nonfiniteComponent == component && solves > 1
                                ? nonfiniteValue : varyingComponent == component ? .1 * solves : 0, 0);
                        default -> throw new AssertionError("Unexpected sparse operation: " + method);
                    });
        }
    }
}
