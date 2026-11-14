package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import com.interpss.dstab.mach.model.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Complete shared-owner checks; integration consistency is distinct from reference acceptance. */
public class Wt3g2BundleTest {
    private static final Path CASE = Path.of("testData/adpter/psse/v33/SMIB");
    private record Fixture(DynamicSimuAlgorithm algorithm, Wt3g2Model generator,
            Wt3g2IntegrationBundle bundle) {}

    private static Fixture fixture(boolean staggered, double step, boolean fault) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt3p1.dyr").toString());
        var a = context.getDynSimuAlgorithm();
        a.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuStepSec(step); a.setTotalSimuTimeSec(1);
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER); a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(staggered ? new StaggeredEulerSolver(a) : new DefaultDynamicSimulationSolver(a));
        if (fault) a.getNetwork().addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", a.getNetwork(),
                SimpleFaultCode.GROUND_3P, new Complex(0, .1), null, .05, .05), "Fault");
        assertTrue(a.initialization());
        var g = (Wt3g2Model) ((DStabGen) a.getNetwork().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        return new Fixture(a, g, new Wt3g2IntegrationBundle(g));
    }

    private static double[] state(Fixture f) {
        double[] values = new double[f.bundle.staggeredStateCount()];
        f.bundle.readStaggeredState(values, 0); return values;
    }

    @Test void completeTrialIsRestorableAndFailureDoesNotPublishPartialCommands() throws Exception {
        var f = fixture(true, .0005, false);
        assertEquals(22, f.bundle.staggeredStateCount());
        double[] left = state(f), rates = new double[22];
        Object output = f.generator.getOutputObject();
        f.bundle.evaluateStaggered(.0005, rates, 0);
        f.bundle.evaluateStaggered(.0005, rates, 0);
        assertArrayEquals(left, state(f), 0);
        double[] trial = left.clone();
        trial[0] += .02; trial[5] += .01; trial[15] += .01; trial[19] += .01;
        f.bundle.applyStaggeredState(trial, 0, .0005);
        f.bundle.applyStaggeredState(left, 0, .0005);
        assertArrayEquals(left, state(f), 0); assertEquals(output, f.generator.getOutputObject());
        trial[21] = Double.NaN;
        final double[] nanTrial = trial;
        assertThrows(IllegalArgumentException.class, () -> f.bundle.applyStaggeredState(nanTrial, 0, .0005));
        assertArrayEquals(left, state(f), 0);
        f.generator.getElectricalController().step(.0005, 0);
        double[] pending = state(f); Object pendingOutput = f.generator.getOutputObject();
        trial = pending.clone(); trial[0] += .02;
        final double[] invalidTrial = trial;
        assertThrows(IllegalStateException.class, () -> f.bundle.applyStaggeredState(invalidTrial, 0, .0005));
        assertArrayEquals(pending, state(f), 0); assertEquals(pendingOutput, f.generator.getOutputObject());
        f.generator.getElectricalController().cancelPrediction();
        f.bundle.evaluateStaggered(.0005, rates, 0);
    }

    @Test void exactPitchOwnerAndAttachmentIdentityAreRequired() throws Exception {
        var f = fixture(true, .0005, false);
        var other = fixture(true, .0005, false);
        var t = f.generator.getDriveTrain();
        assertThrows(UnsupportedOperationException.class, () -> t.readStaggeredState(new double[4], 0));
        assertThrows(UnsupportedOperationException.class, () -> t.readStaggeredState(new double[4], 0,
                other.generator.getDriveTrain().getPitchController()));
        f.generator.setElectricalController(other.generator.getElectricalController());
        assertFalse(f.bundle.attachmentsUnchanged());
        assertThrows(IllegalStateException.class, () -> f.bundle.evaluateStaggered(.0005, new double[22], 0));
    }

    @Test void liveTrialVoltageRefreshesControllerPowerWithoutAdvancingStates() throws Exception {
        var f = fixture(true, .0005, false);
        double[] left = state(f), before = new double[22], after = new double[22];
        var bus = f.generator.getDStabBus();
        Complex voltage = bus.getVoltage();
        f.bundle.evaluateStaggered(.0005, before, 0);
        double initialP = f.generator.getP();
        bus.setVoltage(voltage.multiply(.8));
        f.bundle.evaluateStaggered(.0005, after, 0);
        assertTrue(Math.abs(initialP-f.generator.getP()) > 1e-3);
        assertTrue(Math.abs(before[17]-after[17]) > 1e-4,
                "drive-train acceleration must see current physical power before commit");
        assertTrue(Math.abs(before[10]-after[10]) > 1e-4,
                "electrical speed-reference filter must see current power measurement");
        assertArrayEquals(left, state(f), 0);
        double activePower=f.generator.getP(), reactivePower=f.generator.getQ(), correction=f.generator.getDeltaQ();
        java.util.Arrays.fill(after, 123);
        bus.setVoltage(Complex.NaN);
        assertThrows(IllegalStateException.class, () -> f.bundle.evaluateStaggered(.0005, after, 0));
        assertEquals(activePower,f.generator.getP(),0); assertEquals(reactivePower,f.generator.getQ(),0);
        assertEquals(correction,f.generator.getDeltaQ(),0);
        for(double value:after) assertEquals(123,value,0,"failed rates must not partly publish");
        assertArrayEquals(left,state(f),0);
        bus.setVoltage(voltage);
        f.bundle.evaluateStaggered(.0005,after,0);
        assertArrayEquals(before,after,0);
    }

    @Test void completeStackAdvancesUnderBothSolversAndRefines() throws Exception {
        var ordinary = run(false, .0005);
        var shared = run(true, .0005);
        var ordinaryFine = run(false, .00025);
        var sharedFine = run(true, .00025);
        double coarse = distance(ordinary.getLast(), shared.getLast());
        double fine = distance(ordinaryFine.getLast(), sharedFine.getLast());
        System.out.println("WT3 complete bundle endpoint differences coarse=" + coarse + " fine=" + fine);
        assertTrue(fine < coarse, "coupled method discrepancy must decrease with refinement");
        assertTrue(fine < .003, "refined complete-vector endpoint discrepancy");
        for (var rows : List.of(ordinary, shared)) {
            double[] first = rows.getFirst(), last = rows.getLast();
            for (int[] range : new int[][]{{0,5},{5,15},{15,19},{19,22}}) {
                double motion = 0;
                for (int i = range[0]; i < range[1]; i++) motion = Math.max(motion, Math.abs(last[i]-first[i]));
                assertTrue(motion > 1e-8, "each attached model must advance");
            }
        }
    }

    private static List<double[]> run(boolean shared, double step) throws Exception {
        var f = fixture(shared, step, true);
        List<double[]> rows = new ArrayList<>(); rows.add(state(f));
        while (f.algorithm.getSimuTime() < 1-step/2) {
            assertTrue(f.algorithm.solveDEqnStep(true));
            double[] values = state(f);
            for (double value : values) assertTrue(Double.isFinite(value));
            rows.add(values);
        }
        assertEquals(1+(int)Math.round(1/step), rows.size());
        return rows;
    }

    private static double distance(double[] a, double[] b) {
        double maximum=0;
        for (int i=0;i<a.length;i++) maximum=Math.max(maximum,Math.abs(a[i]-b[i]));
        return maximum;
    }
}
