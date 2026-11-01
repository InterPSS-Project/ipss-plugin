package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.dstab.dynLoad.impl.Cmldznu2Model;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** State ownership, zero-time evaluation, restoration and accepted-history boundaries. */
public class Cmldblu1SharedStateTest {
    @BeforeAll static void setup() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }

    static Cmldblu1Model model() throws Exception {
        return model("cmldblu1");
    }
    static Cmldblu1Model model(String fixture) throws Exception {
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),
                Cmldblu1SharedSmibTest.CASE.resolve(fixture + ".dyr").toString());
        var a = context.getDynSimuAlgorithm();
        assertTrue(a.getAclfAlgorithm().loadflow()); a.setSimuStepSec(.0005);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(a.initialization());
        return assertInstanceOf(Cmldblu1Model.class, context.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel());
    }
    static void pure(StaggeredEulerParticipant participant, IntegrationStateAccess access) {
        var states = access.getIntegrationStates(); var histories = access.getIntegrationHistories();
        int n = participant.staggeredStateCount();
        double[] x = new double[n + 4], rates = new double[n + 4]; Arrays.fill(x, 123); Arrays.fill(rates, 456);
        participant.readStaggeredState(x, 2);
        for (int i = 0; i < 20; i++) participant.evaluateStaggered(.0005, rates, 2);
        assertEquals(states, access.getIntegrationStates()); assertEquals(histories, access.getIntegrationHistories());
        assertEquals(123, x[0]); assertEquals(123, x[n + 3]);
        assertEquals(456, rates[0]); assertEquals(456, rates[n + 3]);
        var snapshot = access.getStateDerivativeSnapshot();
        assertEquals(n, snapshot.rates().size());
        assertTrue(snapshot.rates().values().stream().allMatch(Double::isFinite));
        assertTrue(snapshot.rates().values().stream().anyMatch(v -> Math.abs(v) > 1e-4), "Changed inputs must refresh rates");
        participant.applyStaggeredState(x, 2, 0); participant.completeStaggeredStep(0);
        assertEquals(states, access.getIntegrationStates()); assertEquals(histories, access.getIntegrationHistories());
        assertThrows(IndexOutOfBoundsException.class, () -> participant.applyStaggeredState(new double[n - 1], 0, .0005));
        x[n + 1] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> participant.applyStaggeredState(x, 2, .0005));
        assertEquals(states, access.getIntegrationStates()); assertEquals(histories, access.getIntegrationHistories());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rates().put("external", 1.));
    }
    @Test void compositeMotorTrialEvaluationsCannotAdvanceStatesOrProtection() throws Exception {
        var m = model(); m.getLoadBus().setVoltage(new Complex(.81, -.1));
        assertEquals(java.util.Set.of("MotorA", "MotorD"), m.getIntegrationStateOwners().keySet());
        var motor = (InductionMotorImpl) m.getInductionMotorA(); pure(motor, motor);
        var compressor = (LD1PACImpl) m.get1PhaseACMotor(); pure(compressor, compressor);
        assertSame(motor, m.getIntegrationStateOwners().get("MotorA"));
        assertSame(compressor, m.getIntegrationStateOwners().get("MotorD"));
    }
    @Test void ordinaryZeroDurationEventRefreshCannotCommitMotorHistory() throws Exception {
        var m = model(); m.getLoadBus().setVoltage(new Complex(.6, -.1));
        for (var load : m.getLoadBus().getDynLoadModelList()) if (load instanceof IntegrationStateAccess access) {
            var states = access.getIntegrationStates(); var histories = access.getIntegrationHistories();
            for (int i = 0; i < 3; i++) for (int flag = 0; flag < 2; flag++)
                assertTrue(load.nextStep(0, DynamicSimuMethod.MODIFIED_EULER, flag));
            assertEquals(states, access.getIntegrationStates()); assertEquals(histories, access.getIntegrationHistories());
            assertFalse(access.getStateDerivativeSnapshot().rates().isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void standaloneCageStatesAndWarmCopiesShareTheSameRhs(boolean twoCage) throws Exception {
        var net = DStabObjectFactory.createDStabilityNetwork(); net.setBaseKva(100000); net.setFrequency(60);
        var bus = DStabObjectFactory.createDStabBus("Motor", net).get();
        bus.setLoadCode(AclfLoadCode.CONST_P); bus.setBaseVoltage(1000); bus.setVoltage(Complex.ONE);
        bus.setInitLoad(new Complex(.6, .2));
        var motor = new InductionMotorImpl(bus, "M");
        motor.setXm(3); motor.setXl(.07); motor.setRa(.032); motor.setXr1(.2); motor.setRr1(.01);
        motor.setXr2(twoCage ? .15 : 0); motor.setRr2(twoCage ? .02 : 0); motor.setTwoAxisModel(twoCage);
        motor.setMvaBase(50); motor.setH(1); motor.setLoadPercent(50); motor.setRatedVoltage(1000);
        motor.setInitLoadPQ(new Complex(.3, .12)); assertTrue(motor.initStates());
        assertEquals(twoCage ? 5 : 3, motor.staggeredStateCount());
        var restored = new InductionMotorImpl.ModelState(motor).create(bus);
        bus.setVoltage(new Complex(.82, -.04));
        pure(motor, motor); pure(restored, restored);
        double[] rates = new double[motor.staggeredStateCount()], copiedRates = rates.clone();
        motor.evaluateStaggered(.0005, rates, 0); restored.evaluateStaggered(.0005, copiedRates, 0);
        assertArrayEquals(rates, copiedRates, 0);
    }
    @Test void protectionTimersAdvanceOnlyThroughAcceptedAfterStep() throws Exception {
        var m = model(); var motor = (InductionMotorImpl) m.getInductionMotorA();
        var compressor = (LD1PACImpl) m.get1PhaseACMotor();
        m.getLoadBus().setVoltage(new Complex(.1, 0));
        double[] rates = new double[5];
        for (int i = 0; i < 10; i++) { motor.evaluateStaggered(.001, rates, 0); compressor.evaluateStaggered(.001, rates, 0); }
        assertEquals(0., motor.getIntegrationHistories().get("TripTimer1"));
        assertEquals(0., compressor.getIntegrationHistories().get("StallTimer"));
        assertTrue(motor.afterStep(.001)); assertTrue(compressor.afterStep(.001));
        assertEquals(.001, motor.getIntegrationHistories().get("TripTimer1"), 0);
        assertEquals(.001, compressor.getIntegrationHistories().get("StallTimer"), 0);
    }
    @Test void detachedInductionMotorCoastsWithoutResettingTrialElectricalStates() throws Exception {
        var motor = (InductionMotorImpl) model().getInductionMotorA(); motor.setFonline(0);
        var old = motor.getIntegrationStates(); double[] x = new double[5], rates = new double[5];
        motor.evaluateStaggered(.001, rates, 0); assertEquals(old, motor.getIntegrationStates());
        for (int i = 0; i < 4; i++) assertEquals(0, rates[i]); assertTrue(rates[4] > 0);
        motor.readStaggeredState(x, 0); x[4] = 2;
        motor.applyStaggeredState(x, 0, .001);
        assertEquals(Complex.ZERO, motor.getEp()); assertEquals(Complex.ZERO, motor.getEpp());
        assertEquals(1, motor.getSlip()); assertEquals(0, motor.getW());
    }
    @Test void ordinaryDisconnectedMotorReportsItsActualCoastingRate() throws Exception {
        var motor = (InductionMotorImpl) model().getInductionMotorA(); motor.setFonline(0);
        double initialSlip = motor.getSlip();
        assertTrue(motor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 0));
        var snapshot = motor.getStateDerivativeSnapshot();
        assertEquals(initialSlip, snapshot.evaluationStates().get("Slip"));
        assertTrue(snapshot.rates().get("Slip") > 0);
        for (String key : new String[] {"EPrimeQ", "EPrimeD", "EkReal", "EkImag"}) assertEquals(0., snapshot.rates().get(key));
        assertTrue(motor.getSlip() > initialSlip);
    }
    @Test void warmedRhsUsesReusableBuffersAndOnlyTheRequiredComplexCurrentPublication() throws Exception {
        var generic = java.lang.management.ManagementFactory.getThreadMXBean();
        Assumptions.assumeTrue(generic instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) generic;
        Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var model = model(); var motor = (InductionMotorImpl) model.getInductionMotorA();
        var compressor = (LD1PACImpl) model.get1PhaseACMotor();
        double[] rates = new double[5];
        for (int i = 0; i < 50000; i++) { motor.evaluateStaggered(.0005, rates, 0); compressor.evaluateStaggered(.0005, rates, 0); }
        long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20000; i++) compressor.evaluateStaggered(.0005, rates, 0);
        long compressorBytes = bean.getThreadAllocatedBytes(thread) - before;
        before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20000; i++) motor.evaluateStaggered(.0005, rates, 0);
        long motorBytes = bean.getThreadAllocatedBytes(thread) - before;
        System.out.println("Composite RHS allocated bytes per evaluation: compressor=" + compressorBytes / 20000.
                + ", induction motor=" + motorBytes / 20000.);
        assertEquals(0, compressorBytes);
        assertTrue(motorBytes <= 40L * 20000, "One published Complex current remains; scratch arithmetic must not allocate");
    }
    @Test void sharedSolverRejectsLoadMembershipAndCageChangesBeforeAnotherStep() throws Exception {
        for (boolean cageChange : new boolean[] {false, true}) {
            var recording = Cmldblu1SharedRecording.simulate("cmldblu1", true, true, .0005);
            var motor = (InductionMotorImpl) recording.model.getInductionMotorA();
            if (cageChange) motor.setTwoAxisModel(false);
            else recording.model.getLoadBus().getDynLoadModelList().remove(motor);
            var ep = motor.getEp(); double slip = motor.getSlip();
            assertThrows(IllegalStateException.class, () -> recording.algorithm.getSolver()
                    .nextStep(.5, .0005, DynamicSimuMethod.MODIFIED_EULER));
            assertEquals(ep, motor.getEp()); assertEquals(slip, motor.getSlip());
        }
    }
    @Test void aMotorMissingFromTheLoadRegistryCannotBeSilentlyOmitted() throws Exception {
        var m = model(); var bus = m.getLoadBus(); var motor = m.getInductionMotorA();
        assertTrue(bus.getDynamicBusDeviceList().stream().anyMatch(device -> device == motor));
        assertTrue(bus.getDynLoadModelList().remove(motor));
        var algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(
                (com.interpss.dstab.DStabilityNetwork) m.getDStabBus().getNetwork());
        algorithm.setSimuStepSec(.0005);
        var solver = new StaggeredEulerSolver(algorithm);
        var error = assertThrows(IllegalArgumentException.class, solver::initialization);
        assertTrue(error.getMessage().contains("addDynamicLoadModel"), error.toString());
    }
    @Test void devicesCreatedInsideACompositeAreAuditedAfterExpansion() throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),
                Cmldblu1SharedSmibTest.CASE.resolve("cmldblu1.dyr").toString());
        var bus = context.getDStabilityNet().getDStabBus("Bus2");
        var original = (Cmldblu1Model) bus.getInfoOnlyDynModel();
        new Cmldznu2Model("expanded", bus, original.getTargetLoadIds(), new Complex(.4, .1), original.getData()) {
            @Override public boolean initStates() {
                if (!super.initStates()) return false;
                var unknown = new LD1PACImpl() {
                    @Override public boolean initStates() { return true; }
                    @Override public Complex getPosSeqEquivY() { return Complex.ZERO; }
                    @Override public Complex getNortonCurInj() { return Complex.ZERO; }
                    @Override public Object getOutputObject() { return Complex.ZERO; }
                    @Override public boolean updateAttributes(boolean changed) { return true; }
                };
                unknown.setId("custom-expanded-load"); unknown.setDStabBus(getLoadBus());
                unknown.setExtendedDeviceId("custom-expanded-load@" + getLoadBus().getId());
                unknown.setInitLoadPQ(Complex.ZERO); unknown.setLoadPQ(Complex.ZERO); unknown.setEquivY(Complex.ZERO);
                unknown.getStates(null).put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,
                        unknown.getExtendedDeviceId());
                getLoadBus().addDynamicLoadModel(unknown);
                return true;
            }
        };
        var a = context.getDynSimuAlgorithm(); assertTrue(a.getAclfAlgorithm().loadflow());
        a.setSimuStepSec(.0005); a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        a.setSolver(new StaggeredEulerSolver(a));
        var error = assertThrows(IllegalArgumentException.class, a::initialization);
        assertTrue(error.getMessage().contains("Unsupported"), error.toString());
        assertNotNull(((Cmldznu2Model) bus.getInfoOnlyDynModel()).getLoadBus(), "The post-expansion audit must execute");
    }
}
