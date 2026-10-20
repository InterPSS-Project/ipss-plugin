package org.interpss.core.aclf;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.GeneratorFirstShuntLoadflow;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.config.ControlInitializationMode;

/** Independent two-bus equations for generator priority and secondary support. */
class GeneratorFirstShuntLoadflowTest {
    @TempDir Path root;
    @BeforeAll static void initialize() { IpssCorePlugin.init(); }

    private record Machine(double p, double min, double max, double target) { }

    private LoadflowAlgorithm network(double slack, double shuntTarget, double initialB,
            Machine... machines) throws Exception {
        var gen = new StringBuilder();
        for (int i = 0; i < machines.length; i++) {
            var g = machines[i];
            gen.append(String.format(Locale.ROOT,
                    "1,'%d',%.12f,0,%.12f,%.12f,%.12f,0,100,0,1,0,0,1,1,100,9999,-9999,1,1%n",
                    i + 1, g.p * 100, g.max * 100, g.min * 100, g.target));
        }
        String raw = String.format(Locale.ROOT, """
0,100.0,33,0,0,60.0
ANALYTICAL GENERATOR PRIORITY
LOSSLESS TWO BUS NETWORK
1,'GROUP',230,2,1,1,1,1.0,0,1.1,.9,1.1,.9
2,'SLACK',230,3,1,1,1,1.0,0,1.1,.9,1.1,.9
0 / END OF BUS DATA, BEGIN LOAD DATA
0 / END OF LOAD DATA, BEGIN FIXED SHUNT DATA
0 / END OF FIXED SHUNT DATA, BEGIN GENERATOR DATA
%s2,'1',0,0,9999,-9999,%.12f,0,100,0,1,0,0,1,1,100,9999,-9999,1,1
0 / END OF GENERATOR DATA, BEGIN BRANCH DATA
1,2,'1',0,.1,0,0,0,0,0,0,0,0,1,1,0,1,1
0 / END OF BRANCH DATA, BEGIN TRANSFORMER DATA
0 / END OF TRANSFORMER DATA, BEGIN AREA DATA
0 / END OF AREA DATA, BEGIN TWO-TERMINAL DC DATA
0 / END OF TWO-TERMINAL DC DATA, BEGIN VSC DC LINE DATA
0 / END OF VSC DC LINE DATA, BEGIN IMPEDANCE CORRECTION DATA
0 / END OF IMPEDANCE CORRECTION DATA, BEGIN MULTI-TERMINAL DC DATA
0 / END OF MULTI-TERMINAL DC DATA, BEGIN MULTI-SECTION LINE DATA
0 / END OF MULTI-SECTION LINE DATA, BEGIN ZONE DATA
0 / END OF ZONE DATA, BEGIN INTER-AREA TRANSFER DATA
0 / END OF INTER-AREA TRANSFER DATA, BEGIN OWNER DATA
0 / END OF OWNER DATA, BEGIN FACTS DEVICE DATA
0 / END OF FACTS DEVICE DATA, BEGIN SWITCHED SHUNT DATA
1,2,0,1,%.12f,%.12f,0,100,'',%.12f,1,50
0 / END OF SWITCHED SHUNT DATA, BEGIN GNE DATA
0 / END OF GNE DATA, BEGIN INDUCTION MACHINE DATA
0 / END OF INDUCTION MACHINE DATA
Q
""", gen, slack, shuntTarget, shuntTarget, initialB * 100);
        Path input = Files.createTempFile(root, "priority-", ".raw");
        Files.writeString(input, raw);
        var ctx = new PSSEMultiFileLoader().loadDStab(input.toString());
        var lf = ctx.getDynSimuAlgorithm().getAclfAlgorithm();
        ctx.getDStabilityNet().setAllowGenWithoutMach(true);
        lf.getDataCheckConfig().setAllowGenWithoutMachine(true);
        lf.setInitBusVoltage(true); lf.setTolerance(1.e-11); lf.setMaxIterations(100);
        lf.getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1.e-9);
        lf.getLfAdjAlgo().getLimitCtrlConfig().setControlInitializationMode(ControlInitializationMode.COLD_START);
        return lf;
    }

    private void check(LoadflowAlgorithm lf, double voltage, double q, double b) throws Exception {
        var bus = lf.getNetwork().getBus("Bus1");
        var shunt = bus.getSwitchedShuntList().get(0);
        double target = shunt.getDesiredControlRange().getMiddle();
        var limits = bus.getContributeGenList().stream().map(g -> g.getQGenLimit()).toList();
        var powers = bus.getContributeGenList().stream().map(g -> g.getGen().getReal()).toList();
        var targets = bus.getContributeGenList().stream().map(g -> g.getDesiredVoltMag()).toList();
        var result = GeneratorFirstShuntLoadflow.solve(lf);
        assertTrue(result.converged()); assertEquals(1, result.groups().size());
        assertEquals(powers.size(), result.groups().get(0).generators());
        assertEquals(voltage, bus.getVoltageMag(), 2.e-8);
        assertEquals(q, bus.getGenQ(), 2.e-8);
        assertEquals(b, shunt.getBActual(), 2.e-8);
        assertEquals(target, shunt.getDesiredControlRange().getMiddle(), 0.);
        assertTrue(shunt.isStatus()); assertTrue(shunt.isControlStatus());
        double sum = 0.;
        for (int i = 0; i < powers.size(); i++) {
            var g = bus.getContributeGenList().get(i);
            assertSame(limits.get(i), g.getQGenLimit());
            assertEquals(powers.get(i), g.getGen().getReal(), 0.);
            assertEquals(targets.get(i), g.getDesiredVoltMag(), 0.);
            assertTrue(g.getGen().getImaginary() >= limits.get(i).getMin() - 1.e-9);
            assertTrue(g.getGen().getImaginary() <= limits.get(i).getMax() + 1.e-9);
            sum += g.getGen().getImaginary();
        }
        // Independent branch equation: S = V * conjugate((V - E)/(jX)).
        var v = bus.getVoltage();
        var e = lf.getNetwork().getBus("Bus2").getVoltage();
        var current = v.subtract(e).divide(new org.apache.commons.math3.complex.Complex(0., .1));
        var power = v.multiply(current.conjugate());
        assertEquals(power.getImaginary(), sum + b * voltage * voltage, 2.e-8);
        assertEquals(power.getReal(), powers.stream().mapToDouble(Double::doubleValue).sum(), 2.e-8);
    }

    @Test void retainsEnergizedShuntWhenGeneratorCanRegulate() throws Exception {
        double v = 1.03, total = v * (v - 1.) / .1;
        check(network(1., 1.06, .5, new Machine(0, -.4, .6, v)), v, total - .5*v*v, .5);
    }

    @Test void fixedReactiveGeneratorLeavesShuntInControlOfItsOwnTarget() throws Exception {
        double v = 1.03, p = .2;
        var lf = network(1., v, 0., new Machine(p, 0., 0., 1.));
        var bus = lf.getNetwork().getBus("Bus1");
        var generator = bus.getContributeGenList().get(0);
        var limits = generator.getQGenLimit();
        assertFalse(bus.isPVBusLimit(), "Fixed-Q import has no primary voltage controller");

        var result = GeneratorFirstShuntLoadflow.solve(lf);

        assertTrue(result.converged());
        assertTrue(result.groups().isEmpty());
        assertTrue(lf.getTerminationReport().controlsSettled());
        assertEquals(v, bus.getVoltageMag(), 2.e-8);
        assertEquals(p, generator.getGen().getReal(), 0.);
        assertEquals(0., generator.getGen().getImaginary(), 0.);
        assertSame(limits, generator.getQGenLimit());
        assertEquals(1., generator.getDesiredVoltMag(), 0.);
        double q = (v*v - v*Math.sqrt(1.-Math.pow(p*.1/v, 2)))/.1;
        var shunt = bus.getSwitchedShuntList().get(0);
        assertEquals(q/(v*v), shunt.getBActual(), 2.e-8);
        assertEquals(v, shunt.getDesiredControlRange().getMiddle(), 0.);
    }

    @Test void missingPrimaryControllerWithAdjustableReactiveRangeStillFails() throws Exception {
        var lf = network(1., 1.03, .2, new Machine(.2, 0., 0., 1.));
        var bus = lf.getNetwork().getBus("Bus1");
        bus.getContributeGenList().get(0).setQGenLimit(
                new org.interpss.numeric.datatype.LimitType(.1, -.1));
        assertFalse(bus.isPVBusLimit());
        assertThrows(IllegalArgumentException.class, () -> GeneratorFirstShuntLoadflow.solve(lf));
        assertEquals(.2, bus.getSwitchedShuntList().get(0).getBActual(), 0.);
    }

    @Test void retainsZeroShuntWhenGeneratorCanRegulate() throws Exception {
        double v = 1.03;
        check(network(1., 1.06, 0., new Machine(0, -.4, .6, v)), v, v*(v-1.)/.1, 0.);
    }

    @Test void participationSharesPlantAndShuntWithoutRetainingInitialSetting() throws Exception {
        double v = 1.03, total = v*(v-1.)/.1;
        var lf = network(1., 1.06, .5, new Machine(0., -.4, .6, v));
        var bus = lf.getNetwork().getBus("Bus1");
        var result = GeneratorFirstShuntLoadflow.solve(lf,
                GeneratorFirstShuntLoadflow.ReactiveAllocation.PARTICIPATION_FACTORS);
        assertTrue(result.converged());
        assertEquals(v, bus.getVoltageMag(), 2.e-8);
        assertEquals(total/2, bus.getContributeGenList().get(0).getGen().getImaginary(), 2.e-8);
        assertEquals(total/2/(v*v), bus.getSwitchedShuntList().get(0).getBActual(), 2.e-8);
        assertEquals(1.06, bus.getSwitchedShuntList().get(0).getDesiredControlRange().getMiddle(), 0.);
    }

    @Test void participationCountsOnePlantAndHonorsFixedReactiveMachine() throws Exception {
        double v = 1.03, p = .4;
        double total = (v*v-v*Math.sqrt(1.-Math.pow(p*.1/v,2)))/.1;
        var lf = network(1., 1.06, .5, new Machine(.2, -.4, .6, v), new Machine(.2, 0., 0., v));
        var bus = lf.getNetwork().getBus("Bus1");
        bus.getSwitchedShuntList().get(0).setRemoteControlPercentage(300.);
        assertTrue(GeneratorFirstShuntLoadflow.solve(lf,
                GeneratorFirstShuntLoadflow.ReactiveAllocation.PARTICIPATION_FACTORS).converged());
        assertEquals(total/4, bus.getContributeGenList().get(0).getGen().getImaginary(), 2.e-8);
        assertEquals(0., bus.getContributeGenList().get(1).getGen().getImaginary(), 0.);
        assertEquals(3*total/4/(v*v), bus.getSwitchedShuntList().get(0).getBActual(), 2.e-8);
    }

    @Test void participationClampsPlantAndTransfersResidualToShunt() throws Exception {
        double v = 1.03, total = v*(v-1.)/.1;
        var lf = network(1., 1.06, 0., new Machine(0., -.4, .05, v));
        var bus = lf.getNetwork().getBus("Bus1");
        assertTrue(GeneratorFirstShuntLoadflow.solve(lf,
                GeneratorFirstShuntLoadflow.ReactiveAllocation.PARTICIPATION_FACTORS).converged());
        assertEquals(.05, bus.getContributeGenList().get(0).getGen().getImaginary(), 2.e-8);
        assertEquals((total-.05)/(v*v), bus.getSwitchedShuntList().get(0).getBActual(), 2.e-8);
    }

    @Test void participationClampsShuntLowerBoundForNegativeRequirement() throws Exception {
        var lf = network(1.02, 1.03, .5, new Machine(0., -.4, .6, 1.));
        var bus = lf.getNetwork().getBus("Bus1");
        assertTrue(GeneratorFirstShuntLoadflow.solve(lf,
                GeneratorFirstShuntLoadflow.ReactiveAllocation.PARTICIPATION_FACTORS).converged());
        assertEquals(-.2, bus.getContributeGenList().get(0).getGen().getImaginary(), 2.e-8);
        assertEquals(0., bus.getSwitchedShuntList().get(0).getBActual(), 2.e-8);
    }

    @Test void participationClampsShuntUpperBoundAndTransfersResidualToPlant() throws Exception {
        double v = 1.1, total = v*(v-1.)/.1;
        var lf = network(1., 1.12, 0., new Machine(0., -.4, 2., v));
        var bus = lf.getNetwork().getBus("Bus1");
        bus.getSwitchedShuntList().get(0).setRemoteControlPercentage(300.);
        assertTrue(GeneratorFirstShuntLoadflow.solve(lf,
                GeneratorFirstShuntLoadflow.ReactiveAllocation.PARTICIPATION_FACTORS).converged());
        assertEquals(.5, bus.getSwitchedShuntList().get(0).getBActual(), 2.e-8);
        assertEquals(total-.5*v*v, bus.getContributeGenList().get(0).getGen().getImaginary(), 2.e-8);
    }

    @Test void inconsistentPlantParticipationIsRejectedBeforeMutation() throws Exception {
        var lf = network(1., 1.03, .5, new Machine(.1, -.2, .4, 1.), new Machine(.2, -.2, .4, 1.));
        var bus = lf.getNetwork().getBus("Bus1");
        bus.getContributeGenList().get(1).setMvarControlPFactor(2.);
        assertThrows(IllegalArgumentException.class, () -> GeneratorFirstShuntLoadflow.solve(lf,
                GeneratorFirstShuntLoadflow.ReactiveAllocation.PARTICIPATION_FACTORS));
        assertEquals(.5, bus.getSwitchedShuntList().get(0).getBActual(), 0.);
    }

    @Test void upperLimitActivatesSecondary() throws Exception {
        double v = 1.03, total = v*(v-1.)/.1;
        check(network(1., 1.06, 0., new Machine(0, -.4, .1, v)), v, .1, (total-.1)/(v*v));
    }

    @Test void lowerLimitReducesSecondary() throws Exception {
        double v = 1.01, total = v*(v-1.)/.1;
        check(network(1., 1.03, .5, new Machine(0, -.1, .6, v)), v, -.1, (total+.1)/(v*v));
    }

    @Test void bothLowerLimitsLeaveUnmetVoltageTarget() throws Exception {
        double v = (1.05 + Math.sqrt(1.05*1.05 - 4*.1*.4))/2;
        check(network(1.05, 1.03, .5, new Machine(0, -.4, .6, 1.)), v, -.4, 0.);
    }

    @Test void bothUpperLimitsLeaveUnmetVoltageTarget() throws Exception {
        double v = (10 + Math.sqrt(101.9))/19;
        check(network(1., 1.03, 0., new Machine(0, -.4, .05, 1.1)), v, .05, .5);
    }

    @Test void plantUsesBothMachinesBeforeMovingShunt() throws Exception {
        double v = 1.03, total = v*(v-1.)/.1;
        check(network(1., 1.06, 0., new Machine(0, -.2, .2, v), new Machine(0, -.2, .2, v)),
                v, total, 0.);
    }

    @Test void plantAllocationKeepsFixedReactiveMachineAtZero() throws Exception {
        double v = 1.03, p = .4;
        double q = (v*v - v*Math.sqrt(1.-Math.pow(p*.1/v, 2)))/.1;
        check(network(1., 1.06, 0., new Machine(.2, -.4, .6, v), new Machine(.2, 0., 0., v)),
                v, q, 0.);
    }

    @Test void zeroActivePowerAllocationStillHonorsFixedReactiveMachine() throws Exception {
        double v = 1.03, q = v*(v-1.)/.1;
        check(network(1., 1.06, 0., new Machine(0., -.4, .6, v), new Machine(0., 0., 0., v)),
                v, q, 0.);
    }

    @Test void fixedReactiveAllocationAlsoAppliesWithoutSecondaryShunt() throws Exception {
        checkFixedReactiveAllocationWithoutShunt(.2);
    }

    @Test void zeroActivePowerFixedReactiveAllocationAlsoAppliesWithoutShunt() throws Exception {
        checkFixedReactiveAllocationWithoutShunt(0.);
    }

    private void checkFixedReactiveAllocationWithoutShunt(double p) throws Exception {
        double v = 1.03;
        var lf = network(1., 1.06, 0., new Machine(p, -.4, .6, v), new Machine(p, 0., 0., v));
        var bus = lf.getNetwork().getBus("Bus1");
        bus.getSwitchedShuntList().clear();
        var limits = bus.getContributeGenList().stream().map(g -> g.getQGenLimit()).toList();
        var result = GeneratorFirstShuntLoadflow.solve(lf);
        assertTrue(result.converged());
        assertTrue(result.groups().isEmpty());
        assertEquals(v, bus.getVoltageMag(), 2.e-8);
        double q = (v*v - v*Math.sqrt(1.-Math.pow(2*p*.1/v, 2)))/.1;
        assertEquals(q, bus.calNetGenResults().getImaginary(), 2.e-8);
        assertEquals(q, bus.getContributeGenList().get(0).getGen().getImaginary(), 2.e-8);
        assertEquals(0., bus.getContributeGenList().get(1).getGen().getImaginary(), 0.);
        for (int i = 0; i < 2; i++) {
            var generator = bus.getContributeGenList().get(i);
            assertEquals(p, generator.getGen().getReal(), 0.);
            assertSame(limits.get(i), generator.getQGenLimit());
            assertEquals(v, generator.getDesiredVoltMag(), 0.);
        }
    }

    @Test void plantAtUpperLimitActivatesSecondary() throws Exception {
        double v = 1.03, total = v*(v-1.)/.1;
        check(network(1., 1.06, 0., new Machine(0, -.2, .05, v), new Machine(0, -.2, .05, v)),
                v, .1, (total-.1)/(v*v));
    }

    @Test void nonzeroMachinePowerIsPreserved() throws Exception {
        double v = 1.03, p = .3;
        double total = (v*v - v*Math.sqrt(1.-Math.pow(p*.1/v, 2)))/.1;
        check(network(1., 1.06, .2, new Machine(.1, -.2, .4, v), new Machine(.2, -.2, .4, v)),
                v, total-.2*v*v, .2);
    }

    @Test void repeatedSolveRetainsSecondarySetting() throws Exception {
        double v = 1.03, total = v*(v-1.)/.1, b = (total-.1)/(v*v);
        var lf = network(1., 1.06, 0., new Machine(0, -.4, .1, v));
        check(lf, v, .1, b); check(lf, v, .1, b);
    }

    @Test void mixedGeneratorTargetsAreRejectedBeforeMutation() throws Exception {
        var lf = network(1., 1.03, .5, new Machine(0, -.2, .2, 1.), new Machine(0, -.2, .2, 1.01));
        var bus = lf.getNetwork().getBus("Bus1");
        var limit = bus.getContributeGenList().get(0).getQGenLimit();
        assertThrows(IllegalArgumentException.class, () -> GeneratorFirstShuntLoadflow.solve(lf));
        assertEquals(.5, bus.getSwitchedShuntList().get(0).getBActual(), 0.);
        assertSame(limit, bus.getContributeGenList().get(0).getQGenLimit());
    }

    @Test void exceptionRestoresPhysicalDevicesAndClearsConvergence() throws Exception {
        var lf = network(1., 1.03, .5, new Machine(.1, -.4, .6, 1.));
        var bus = lf.getNetwork().getBus("Bus1");
        var gen = bus.getContributeGenList().get(0);
        var limits = gen.getQGenLimit(); var power = gen.getGen(); var code = bus.getGenCode();
        var failing = (LoadflowAlgorithm)Proxy.newProxyInstance(LoadflowAlgorithm.class.getClassLoader(),
                new Class<?>[]{LoadflowAlgorithm.class}, (proxy, method, args) -> {
                    if (method.getName().equals("loadflow")) throw new IllegalStateException("injected solve failure");
                    try { return method.invoke(lf, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        assertThrows(IllegalStateException.class, () -> GeneratorFirstShuntLoadflow.solve(failing));
        assertSame(limits, gen.getQGenLimit()); assertEquals(power, gen.getGen());
        assertEquals(code, bus.getGenCode());
        assertEquals(.5, bus.getSwitchedShuntList().get(0).getBActual(), 0.);
        assertTrue(bus.getSwitchedShuntList().get(0).isControlStatus());
        assertFalse(lf.getNetwork().isLfConverged());
    }
}
