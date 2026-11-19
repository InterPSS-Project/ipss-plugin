package com.interpss.dstab.controller.gov.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3Governor;
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3GovernorData;

/** Shared equations and numerical storage retain accepted backlash and hysteresis histories. */
public class Hyg3SharedStateTest {
    private static final double H = .0005;
    private static final String[] NAMES = {"Input filter", "Integral", "Derivative lag", "Valve", "Gate",
            "Electrical power", "Water flow", "Double derivative 1", "Double derivative 2"};
    private static volatile double sink;

    @Test void ordinaryStagesRejectInvalidIntervalsAndOwnersBeforeMutation() throws Exception {
        var governor=model(1,false);var machine=governor.getMachine();var other=model(1,false).getMachine();
        var state=governor.getIntegrationStates();var history=governor.getIntegrationHistories();
        var snapshot=governor.getStateDerivativeSnapshot();double output=governor.getOutput(machine);
        for(double bad:new double[]{-H,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->governor.nextStep(bad,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertThrows(IllegalStateException.class,()->governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        assertThrows(IllegalStateException.class,()->governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,other,0));
        assertEquals(state,governor.getIntegrationStates());assertEquals(history,governor.getIntegrationHistories());
        assertEquals(snapshot,governor.getStateDerivativeSnapshot());assertEquals(output,governor.getOutput(machine),0.);
        governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,machine,0);
        var predicted=governor.getIntegrationStates();var predictedSnapshot=governor.getStateDerivativeSnapshot();
        for(int stage:new int[]{0,1})
            assertThrows(IllegalStateException.class,()->governor.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,machine,stage));
        assertThrows(IllegalStateException.class,()->governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertThrows(IllegalStateException.class,()->governor.nextStep(H/2,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        assertEquals(predicted,governor.getIntegrationStates());assertEquals(predictedSnapshot,governor.getStateDerivativeSnapshot());
        governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,machine,1);
        assertEquals(state,governor.getIntegrationStates());
    }

    @Test void rejectedOrdinaryArithmeticPreservesPredictorAndValidRetry() throws Exception {
        for(int mode:new int[]{0,1}) {
            var governor=model(mode,false);var control=model(mode,false);var machine=governor.getMachine();
            for(var g:List.of(governor,control)) g.getMachine().setSpeed(1.002);
            var initial=governor.getIntegrationStates();var initialSnapshot=governor.getStateDerivativeSnapshot();
            machine.setPe(Double.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,()->governor.nextStep(Double.MAX_VALUE,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertEquals(initial,governor.getIntegrationStates());assertEquals(initialSnapshot,governor.getStateDerivativeSnapshot());
            machine.setPe(.4);
            for(var g:List.of(governor,control)) g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),0);
            var predicted=governor.getIntegrationStates();var history=governor.getIntegrationHistories();
            var snapshot=governor.getStateDerivativeSnapshot();double output=governor.getOutput(machine);
            machine.setPe(Double.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,()->governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,machine,1));
            assertEquals(predicted,governor.getIntegrationStates());assertEquals(history,governor.getIntegrationHistories());
            assertEquals(snapshot,governor.getStateDerivativeSnapshot());assertEquals(output,governor.getOutput(machine),0.);
            machine.setPe(.4);
            for(var g:List.of(governor,control)) g.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,g.getMachine(),1);
            assertEquals(control.getIntegrationStates(),governor.getIntegrationStates());
            assertEquals(control.getIntegrationHistories(),governor.getIntegrationHistories());
            assertEquals(control.getOutput(control.getMachine()),governor.getOutput(machine),0.);
        }
    }

    @Test void sharedRejectionPreservesStateOutputsHistoryAndCallerBuffer() throws Exception {
        for(int mode:new int[]{0,1}) {
            var governor=model(mode,false);var machine=governor.getMachine();double[] values=new double[9],rates=new double[9];
            governor.readStaggeredState(values,0);var state=governor.getIntegrationStates();
            var history=governor.getIntegrationHistories();var snapshot=governor.getStateDerivativeSnapshot();
            double output=governor.getOutput(machine);values[6]=1e150;double[] trial=values.clone();
            assertThrows(IllegalArgumentException.class,()->governor.applyStaggeredState(values,0,H));
            assertEquals(state,governor.getIntegrationStates());assertArrayEquals(trial,values,0.);
            for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
                machine.setSpeed(bad);Arrays.fill(rates,73.);
                assertThrows(IllegalArgumentException.class,()->governor.evaluateStaggered(H,rates,0));
                assertThrows(IllegalArgumentException.class,()->governor.completeStaggeredStep(H));
                for(double rate:rates)assertEquals(73.,rate,0.);
            }
            assertEquals(state,governor.getIntegrationStates());assertEquals(history,governor.getIntegrationHistories());
            assertEquals(snapshot,governor.getStateDerivativeSnapshot());assertEquals(output,governor.getOutput(machine),0.);
            machine.setSpeed(1.);governor.readStaggeredState(values,0);governor.applyStaggeredState(values,0,H);
            governor.evaluateStaggered(H,rates,0);governor.completeStaggeredStep(H);
            assertEquals(state,governor.getIntegrationStates());
        }
    }

    @Test void replacedMachineOwnerRejectsOrdinaryAndSharedUpdates() throws Exception {
        var governor=model(1,false);var original=governor.getMachine();var replacement=model(1,false).getMachine();
        var state=governor.getIntegrationStates();var history=governor.getIntegrationHistories();
        governor.setMachine(replacement);
        assertThrows(IllegalStateException.class,()->governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,replacement,0));
        assertThrows(IllegalStateException.class,()->governor.evaluateStaggered(H,new double[9],0));
        assertEquals(state,governor.getIntegrationStates());assertEquals(history,governor.getIntegrationHistories());
        governor.setMachine(original);governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,original,0);
        governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,original,1);
    }
    @BeforeAll static void setup() { IpssCorePlugin.init(); }

    private static PsseHyg3Governor model(int mode, boolean bypass) throws Exception {
        Path root = Path.of("testData/adpter/psse/v33/HspModels");
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),
                root.resolve("hyg3u1-" + mode + ".dyr").toString());
        var machine = context.getDStabilityNet().getMachine("Bus1-mach1");
        machine.setPm(.4); machine.setPe(.4); machine.setSpeed(1.);
        var governor = (PsseHyg3Governor) machine.getGovernor();
        if (bypass) {
            governor.getData().setZeroDerivativeFilterConvention(PsseHyg3GovernorData.ZeroDerivativeFilterConvention.BYPASS);
            governor.getData().setTd(0.); governor.getData().setTf(0.);
            governor.getData().setTp(0.); governor.getData().setTt(0.); governor.getData().setTw(0.);
        }
        governor.configureIntegrationStep(H);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return governor;
    }

    @Test void importedZeroDerivativeTimeUsesOuterIntervalAcrossBothMethodsAndLocalSteps() throws Exception {
        for (int mode : new int[]{0, 1}) for (boolean shared : new boolean[]{false, true}) {
            var zero = model(mode, false); var explicit = model(mode, false);
            assertEquals(PsseHyg3GovernorData.ZeroDerivativeFilterConvention.FOUR_OUTER_INTERVALS,
                    zero.getData().getZeroDerivativeFilterConvention(), "Import and builder must preserve preparation policy");
            zero.getData().setTf(0.); explicit.getData().setTf(4*H);
            assertTrue(zero.initStates(zero.getMachine().getDStabBus(), zero.getMachine()));
            assertTrue(explicit.initStates(explicit.getMachine().getDStabBus(), explicit.getMachine()));
            double[] z = new double[9], e = new double[9], rates = new double[9], published = new double[9];
            for (int tick = 0; tick < 40; tick++) {
                double local = tick < 20 ? H/2 : H/4;
                for (var governor : List.of(zero, explicit)) {
                    governor.getMachine().setSpeed(1.+.0002*Math.sin(.1*tick));
                    governor.getMachine().setPe(.4+.001*Math.cos(.1*tick));
                    if (shared) {
                        governor.readStaggeredState(e, 0); governor.evaluateStaggered(local, rates, 0);
                        for (int i=0;i<9;i++) e[i] += local*rates[i];
                        governor.applyStaggeredState(e, 0, local); governor.completeStaggeredStep(local);
                    } else {
                        governor.nextStep(local, DynamicSimuMethod.MODIFIED_EULER, governor.getMachine(), 0);
                        governor.nextStep(local, DynamicSimuMethod.MODIFIED_EULER, governor.getMachine(), 1);
                    }
                }
                zero.readStaggeredState(z,0); explicit.readStaggeredState(e,0); assertArrayEquals(e,z,0.);
                zero.readPublishedState(z,0); explicit.readPublishedState(published,0); assertArrayEquals(published,z,0.);
                assertEquals(explicit.getOutput(explicit.getMachine()),zero.getOutput(zero.getMachine()),0.);
                assertEquals(4*H,zero.getEffectiveDerivativeFilterTime(),0.);
                assertEquals(0.,zero.getData().getTf(),0.,"Preparation leaves entered constants unchanged");
            }
            assertTrue(Math.abs(z[2])>1e-6,"Derivative branch must remain active");
            zero.configureIntegrationStep(H/2); assertEquals(4*H,zero.getEffectiveDerivativeFilterTime(),0.);
            assertTrue(zero.initStates(zero.getMachine().getDStabBus(),zero.getMachine()));
            assertEquals(2*H,zero.getEffectiveDerivativeFilterTime(),0.);
            zero.getData().setTf(H); assertEquals(H,zero.getEffectiveDerivativeFilterTime(),0.,"Positive Tf is not floored");
        }
    }

    @Test void importedZeroDerivativeTimeRequiresOuterIntervalButTypedBypassRemainsAvailable() throws Exception {
        var governor=model(0,false); governor.getData().setTf(0.); governor.configureIntegrationStep(0.);
        assertFalse(governor.initStates(governor.getMachine().getDStabBus(),governor.getMachine()));
        governor.getData().setZeroDerivativeFilterConvention(PsseHyg3GovernorData.ZeroDerivativeFilterConvention.BYPASS);
        assertTrue(governor.initStates(governor.getMachine().getDStabBus(),governor.getMachine()));
        assertEquals(0.,governor.getEffectiveDerivativeFilterTime(),0.);
    }

    @Test void combinedImportedBypassesSolveLivePidFeedbackWithoutAdvancingStoredInputOrHistory() throws Exception {
        for (boolean shared : new boolean[]{false,true}) {
            var governor=model(1,false); var data=governor.getData();
            data.setTd(0.); data.setTt(0.); data.setTp(0.); data.setTf(0.);
            assertTrue(governor.initStates(governor.getMachine().getDStabBus(),governor.getMachine()));
            double[] state=new double[9],rates=new double[9],published=new double[9];
            governor.readStaggeredState(state,0); state[0]=.9; state[2]=.00001; state[3]=.01; state[5]=.9;
            governor.getMachine().setSpeed(1.0002); governor.getMachine().setPe(.41);
            governor.applyStaggeredState(state,0,H);
            var before=governor.getIntegrationStates(); var history=governor.getIntegrationHistories();
            double tf=4*H, derivativeGain=data.getK1()/tf;
            double input=(governor.getRefPoint()-.0002-data.getRelec()*.41
                    -data.getRgate()*(state[1]-derivativeGain*state[2]))
                    /(1+data.getRgate()*(data.getK2()+derivativeGain));
            double command=state[1]+data.getK2()*input+derivativeGain*(input-state[2]);
            if (shared) governor.evaluateStaggered(H/4,rates,0);
            else {
                governor.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,governor.getMachine(),0);
                var snapshot=governor.getStateDerivativeSnapshot();
                for (int i=0;i<9;i++) rates[i]=snapshot.rates().get(NAMES[i]);
            }
            governor.readPublishedState(published,0);
            assertEquals(input,published[0],1e-15); assertEquals(.41,published[5],0.);
            assertEquals(command,governor.getControlValveCommand(),1e-14);
            assertEquals(data.getKi()*input,rates[1],1e-15);
            assertEquals((input-state[2])/tf,rates[2],1e-12);
            assertEquals(Math.max(data.getVelclose(),Math.min(data.getVelopen(),
                    data.getKg()*(command-governor.getGateOutput()))),published[3],1e-14);
            for(int i:new int[]{0,3,5,7,8}) assertEquals(0.,rates[i],0.);
            assertEquals(before,governor.getIntegrationStates()); assertEquals(history,governor.getIntegrationHistories());
            assertEquals(0.,data.getTf(),0.);
        }
    }

    @Test void pureRhsMatchesOrdinaryEquationsAndPreservesHistoryAcrossBothModesAndBypasses() throws Exception {
        for (int mode : new int[]{0, 1}) for (boolean bypass : new boolean[]{false, true}) {
            var shared = model(mode, bypass); var ordinary = model(mode, bypass);
            double[] values = new double[13], rates = new double[13];
            Arrays.fill(values, 71.); Arrays.fill(rates, 93.);
            shared.readStaggeredState(values, 2);
            for (int i = 0; i < 9; i++) values[i+2] += .00001 * Math.sin(i+1);
            for (var governor : List.of(shared, ordinary)) {
                governor.applyStaggeredState(values, 2, H);
                governor.getMachine().setSpeed(1.002); governor.getMachine().setPe(.43);
            }
            var states = shared.getIntegrationStates(); var histories = shared.getIntegrationHistories();
            shared.evaluateStaggered(H, rates, 2);
            assertTrue(ordinary.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, ordinary.getMachine(), 0));
            var expected = ordinary.getStateDerivativeSnapshot();
            for (int i = 0; i < 9; i++) {
                assertEquals(expected.rates().get(NAMES[i]), rates[i+2], 0., NAMES[i]);
                assertEquals(states.get(NAMES[i]), values[i+2], 1e-15);
            }
            assertEquals(ordinary.getOutput(ordinary.getMachine()), shared.getOutput(shared.getMachine()), 0.);
            for (int repeat = 0; repeat < 10; repeat++) {
                shared.evaluateStaggered(H, rates, 2); shared.getK1Output(); shared.getK2SecondOutput();
                assertEquals(states, shared.getIntegrationStates()); assertEquals(histories, shared.getIntegrationHistories());
            }
            assertEquals(71., values[0]); assertEquals(71., values[12]);
            assertEquals(93., rates[0]); assertEquals(93., rates[12]);
            if (bypass) for (int index : new int[]{0, 2, 3, 5, 6, 7, 8}) assertEquals(0., rates[index+2], 0.);
        }
    }

    @Test void externalModifiedEulerMatchesOrdinaryIntegrationWithLiveInputsAndCommittedHistories() throws Exception {
        for (int mode : new int[]{0, 1}) for (boolean bypass : new boolean[]{false, true}) {
            var shared = model(mode, bypass); var ordinary = model(mode, bypass);
            double[] start = new double[9], a = new double[9], b = new double[9], trial = new double[9];
            for (int tick = 0; tick < 800; tick++) {
                for (var governor : List.of(shared, ordinary)) {
                    governor.getMachine().setSpeed(1. + .001 * Math.sin(tick * .017));
                    governor.getMachine().setPe(.4 + .01 * Math.cos(tick * .021));
                    governor.setAuxiliaryInput(.0001 * Math.sin(tick * .031));
                }
                shared.readStaggeredState(start, 0); shared.evaluateStaggered(H, a, 0);
                for (int i = 0; i < 9; i++) trial[i] = start[i] + a[i]*H;
                shared.applyStaggeredState(trial, 0, H); shared.evaluateStaggered(H, b, 0);
                for (int i = 0; i < 9; i++) trial[i] = start[i] + (a[i]+b[i])/2.*H;
                shared.applyStaggeredState(trial, 0, H); shared.completeStaggeredStep(H);
                ordinary.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, ordinary.getMachine(), 0);
                ordinary.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, ordinary.getMachine(), 1);
                for (String name : NAMES) assertEquals(ordinary.getIntegrationStates().get(name),
                        shared.getIntegrationStates().get(name), 2e-14, name);
                assertEquals(ordinary.getOutput(ordinary.getMachine()), shared.getOutput(shared.getMachine()), 2e-14);
                assertEquals(ordinary.getIntegrationHistories(), shared.getIntegrationHistories());
            }
        }
    }

    @Test void trialProjectionAndObservationCannotCommitBacklashOrHysteresis() throws Exception {
        var governor = model(1, false); var data = governor.getData();
        data.setDb2(10.); data.setFrequencyDeadbandConvention(PsseHyg3GovernorData.FrequencyDeadbandConvention.HZ_HYSTERESIS);
        data.setBacklashConvention(PsseHyg3GovernorData.BacklashConvention.MW_ON_GOVERNOR_BASE);
        data.setDbH(.036); data.setDbL(-.024); data.setEps(.012);
        double[] initial = new double[9], trial = new double[9], rates = new double[9];
        governor.readStaggeredState(initial, 0); System.arraycopy(initial, 0, trial, 0, 9);
        var histories = governor.getIntegrationHistories();
        governor.getMachine().setSpeed(1.002);
        trial[1] = 2.; trial[3] = 5.; trial[4] = 2.;
        governor.applyStaggeredState(trial, 0, H); governor.evaluateStaggered(H, rates, 0);
        assertEquals(trial[1], governor.getIntegralState(), 0., "The actuator bounds do not constrain the governor integral");
        assertEquals(data.getPmax(), governor.getGatePosition(), 0.);
        assertEquals(data.getVelopen(), governor.getValveState(), 0.);
        assertTrue(rates[3] <= 0.); assertTrue(rates[4] <= 0.);
        assertEquals(histories, governor.getIntegrationHistories());
        governor.applyStaggeredState(initial, 0, H);
        assertEquals(histories, governor.getIntegrationHistories());
        governor.applyStaggeredState(trial, 0, H); governor.completeStaggeredStep(H);
        var committed = governor.getIntegrationHistories();
        assertNotEquals(histories, committed); assertEquals(governor.getGateOutput(), committed.get("Committed gate output"), 0.);
        assertEquals(2., committed.get("Committed deadband mode"), 0.);
        assertThrows(UnsupportedOperationException.class, () -> committed.put("x", 1.));
        governor.getMachine().setSpeed(1.0005);
        governor.evaluateStaggered(H, rates, 0); assertEquals(committed, governor.getIntegrationHistories());
        governor.completeStaggeredStep(H); assertEquals(2., governor.getIntegrationHistories().get("Committed deadband mode"), 0.);
        governor.getMachine().setSpeed(1.0003); governor.completeStaggeredStep(H);
        assertEquals(1., governor.getIntegrationHistories().get("Committed deadband mode"), 0.);
        assertEquals(2., committed.get("Committed deadband mode"), 0., "Detached snapshot");
    }

    @Test void invalidTrialsAndUnfinishedPredictorRejectSharedAccessBeforeMutation() throws Exception {
        var governor = model(0, false); double[] values = new double[9], rates = new double[9];
        governor.readStaggeredState(values, 0); var original = governor.getIntegrationStates();
        values[0] = .5; values[8] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> governor.applyStaggeredState(values, 0, H));
        assertEquals(original, governor.getIntegrationStates());
        assertThrows(IndexOutOfBoundsException.class, () -> governor.readStaggeredState(new double[8], 0));
        for (double step : new double[]{0., -.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> governor.evaluateStaggered(step, rates, 0));
        var machine = governor.getMachine(); governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        assertThrows(IllegalStateException.class, () -> governor.evaluateStaggered(H, rates, 0));
        assertThrows(IllegalStateException.class, () -> governor.readStaggeredState(values, 0));
        governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, machine, 1); governor.evaluateStaggered(H, rates, 0);
        governor.getData().setKg(-1.); assertFalse(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(Stage.NOT_EVALUATED, governor.getStateDerivativeSnapshot().stage());
        assertThrows(IllegalStateException.class, () -> governor.evaluateStaggered(H, rates, 0));
        governor.getData().setKg(5.); assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(Stage.INITIALIZATION, governor.getStateDerivativeSnapshot().stage());
    }

    @Test void derivativeSnapshotsRetainEvaluationCoordinatesAndDoNotAdvanceHistory() throws Exception {
        var governor = model(0, false); var snapshot = governor.getStateDerivativeSnapshot();
        assertEquals(Stage.INITIALIZATION, snapshot.stage()); assertEquals(9, snapshot.rates().size());
        snapshot.rates().values().forEach(rate -> assertEquals(0., rate, 1e-14));
        var before = governor.getIntegrationStates(); var history = governor.getIntegrationHistories();
        governor.getMachine().setSpeed(1.001); double[] rates = new double[9]; governor.evaluateStaggered(H, rates, 0);
        var evaluated = governor.getStateDerivativeSnapshot();
        assertEquals(Stage.STAGGERED, evaluated.stage()); assertEquals(before, evaluated.evaluationStates());
        assertEquals(history, governor.getIntegrationHistories()); assertEquals(before, snapshot.evaluationStates());
        for (int i = 0; i < 9; i++) assertEquals(rates[i], evaluated.rates().get(NAMES[i]), 0.);
        assertThrows(UnsupportedOperationException.class, () -> evaluated.rates().clear());
    }

    @Test void replacingDataOwnerRequiresCoherentReinitialization() throws Exception {
        var governor = model(1, false); double[] rates = new double[9];
        java.lang.reflect.Field owner = null;
        for (Class<?> type = governor.getClass(); type != null && owner == null; type = type.getSuperclass()) {
            try { owner = type.getDeclaredField("_data"); } catch (NoSuchFieldException inherited) { }
        }
        assertNotNull(owner); owner.setAccessible(true); owner.set(governor, new PsseHyg3GovernorData());
        assertThrows(IllegalStateException.class, () -> governor.evaluateStaggered(H, rates, 0));
        var machine = governor.getMachine(); assertTrue(governor.initStates(machine.getDStabBus(), machine));
        governor.evaluateStaggered(H, rates, 0);
        assertEquals(1., governor.getTurbineHead(), 1e-14);
    }

    @Test void publishedCompensationCoordinatesPreserveStorageAndDerivativeHistory() throws Exception {
        for (int mode : new int[]{0, 1}) for (boolean bypass : new boolean[]{false, true}) {
            var governor = model(mode, bypass); var data = governor.getData();
            double[] state = new double[9], published = new double[13];
            governor.readStaggeredState(state, 0);
            state[0] = .003; state[2] = .001; state[7] = -.002; state[8] = .0007;
            governor.applyStaggeredState(state, 0, H);
            var storage = governor.getIntegrationStates(); var history = governor.getIntegrationHistories();
            var snapshot = governor.getStateDerivativeSnapshot(); Arrays.fill(published, 79.);
            for (double gain : new double[]{.4, 1.7}) {
                data.setK2(gain); data.setK1(gain/3.);
                governor.readPublishedState(published, 2);
                assertEquals(79., published[0]); assertEquals(79., published[12]);
                for (int i : new int[]{1, 4, 6}) assertEquals(state[i], published[i+2], 0.);
                assertEquals(bypass ? 0. : state[0], published[2], 1e-15);
                assertEquals(bypass ? 0. : state[3], published[5], 1e-15);
                assertEquals(bypass ? governor.getMeasuredElectricalPower() : state[5], published[7], 0.);
                double tf = data.getTf();
                assertEquals(bypass ? 0. : data.getK1()/tf*state[2], published[4], 0.);
                assertEquals(bypass ? 0. : gain/tf*state[7], published[9], 0.);
                assertEquals(bypass ? 0. : gain/tf*state[8], published[10], 0.);
                if (!bypass) {
                    assertEquals(data.getK1()/tf*state[0]-published[4], governor.getK1Output(), 1e-16);
                    if (mode == 0) assertEquals(gain/(tf*tf)*state[0]-published[9]/tf-published[10],
                            governor.getK2SecondOutput(), 1e-15);
                }
                assertEquals(storage, governor.getIntegrationStates()); assertEquals(history, governor.getIntegrationHistories());
                assertEquals(snapshot, governor.getStateDerivativeSnapshot());
            }
            assertThrows(IndexOutOfBoundsException.class, () -> governor.readPublishedState(new double[8], 0));
        }
    }

    @Test void doubleDerivativeControlIncludesDirectFilteredSpeedPath() throws Exception {
        var governor = model(0, true); var data = governor.getData(); var machine = governor.getMachine();
        data.setRgate(0.); data.setRelec(0.);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        machine.setSpeed(1.003);
        double expected = -data.getKi()*(machine.getSpeed()-1.);
        double[] rates = new double[9]; governor.evaluateStaggered(H, rates, 0);
        assertEquals(expected, rates[1], 1e-15, "The diagram sums direct speed and both derivative paths");
        governor.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        assertEquals(expected, governor.getStateDerivativeSnapshot().rates().get("Integral"), 1e-15);
    }

    @Test void publishedBypassesUseAlgebraicOutputsWithoutChangingStoredStatesOrHistory() throws Exception {
        for (int mode : new int[]{0, 1}) for (boolean shared : new boolean[]{false, true}) {
            var governor = model(mode, false); var data = governor.getData(); var machine = governor.getMachine();
            data.setTd(0.); data.setTp(0.); data.setTt(0.);
            assertTrue(governor.initStates(machine.getDStabBus(), machine));
            machine.setSpeed(1.002); machine.setPe(.44);
            if (shared) governor.evaluateStaggered(H, new double[9], 0);
            else {
                governor.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, machine, 0);
                governor.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, machine, 1);
            }
            var states = governor.getIntegrationStates(); var history = governor.getIntegrationHistories();
            var rates = governor.getStateDerivativeSnapshot(); double[] published = new double[9];
            double speed = machine.getSpeed()-1., measured = governor.getMeasuredElectricalPower();
            double input = mode == 0 ? speed :
                    (governor.getRefPoint()-speed-data.getRelec()*measured
                            -data.getRgate()*(governor.getIntegralState()-governor.getDerivativeCompensationState()))
                            /(1.+data.getRgate()*(data.getK2()+data.getK1()/data.getTf()));
            double valve = Math.max(data.getVelclose(), Math.min(data.getVelopen(),
                    data.getKg()*(governor.getControlValveCommand()-governor.getGateOutput())));
            for (int repeat=0; repeat<4; repeat++) {
                governor.readPublishedState(published, 0);
                assertEquals(input, published[0], 1e-15);
                assertEquals(valve, published[3], 1e-15);
                assertEquals(measured, published[5], 0.);
                assertNotEquals(states.get("Input filter"), published[0]);
                assertNotEquals(states.get("Electrical power"), published[5]);
                assertEquals(states, governor.getIntegrationStates());
                assertEquals(history, governor.getIntegrationHistories());
                assertEquals(rates, governor.getStateDerivativeSnapshot());
            }
        }
    }

    @Test void gatePositionBoundsDoNotClipGovernorIntegralOrCommand() throws Exception {
        for (int mode : new int[]{0,1}) {
            var governor = model(mode,false); double initial = governor.getGatePosition();
            governor.getData().setPmax(initial+.001); governor.getData().setPmin(initial-.001);
            var machine = governor.getMachine(); assertTrue(governor.initStates(machine.getDStabBus(),machine));
            double[] trial = new double[9]; governor.readStaggeredState(trial,0);
            trial[1] = initial+.05; trial[4] = initial+.02;
            governor.applyStaggeredState(trial,0,H);
            assertEquals(trial[1],governor.getIntegralState(),0.,"Only physical gate position uses the gate-position bounds");
            assertEquals(trial[1],governor.getControlValveCommand(),1e-15,"The governor command can exceed the actuator gate bounds");
            assertEquals(initial+.001,governor.getGatePosition(),0.);
            double reference = governor.getRefPoint();
            assertThrows(IllegalArgumentException.class,()->governor.setRefPoint(Double.NaN));
            assertEquals(reference,governor.getRefPoint(),0.);
        }
    }

    @Test void zeroDurationModifiedEulerRefreshCannotAcceptBacklashHistory() throws Exception {
        for (int mode : new int[]{0,1}) {
            var governor=model(mode,false); governor.getData().setDb2(.02);
            double[] trial=new double[9]; governor.readStaggeredState(trial,0); trial[4]+=.03;
            var history=governor.getIntegrationHistories(); governor.applyStaggeredState(trial,0,H);
            var states=governor.getIntegrationStates(); var machine=governor.getMachine();
            governor.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,machine,0);
            governor.nextStep(0.,DynamicSimuMethod.MODIFIED_EULER,machine,1);
            assertEquals(states,governor.getIntegrationStates()); assertEquals(history,governor.getIntegrationHistories());
            governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,machine,0);
            governor.nextStep(H,DynamicSimuMethod.MODIFIED_EULER,machine,1);
            assertNotEquals(history,governor.getIntegrationHistories());
        }
    }

    @Test void nativeBacklashWidthIsPerUnitGate() throws Exception {
        var governor = model(0,false); governor.getData().setDb2(.02);
        double initial = governor.getGateOutput(); double[] trial = new double[9];
        governor.readStaggeredState(trial,0); trial[4] += .01;
        governor.applyStaggeredState(trial,0,H);
        assertEquals(initial,governor.getGateOutput(),1e-15,
                "A native 0.02 pu backlash must hold a 0.01 pu gate movement");
    }

    @Test void warmedBothMethodUpdatesReuseNumericalBuffers() throws Exception {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocations = (com.sun.management.ThreadMXBean) bean;
        org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        for (boolean staggered : new boolean[]{false, true}) for (int mode : new int[]{0, 1})
                for (int profile : new int[]{0, 1, 2}) {
            boolean bypass = profile == 1;
            var governor = model(mode, bypass); double[] states = new double[9], rates = new double[9];
            if (profile == 2) {
                governor.getData().setTf(0.);
                assertTrue(governor.initStates(governor.getMachine().getDStabBus(), governor.getMachine()));
            }
            for (int i = 0; i < 80000; i++) update(governor, staggered, states, rates);
            long thread = Thread.currentThread().threadId(), start = allocations.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100000; i++) update(governor, staggered, states, rates);
            long bytes = allocations.getThreadAllocatedBytes(thread)-start;
            System.out.println("HYG3 reusable updates mode="+mode+" staggered="+staggered+" profile="+profile+" allocated="+bytes);
            assertTrue(bytes <= 1024, "Reusable numerical loop allocated "+bytes);
            assertTrue(Double.isFinite(sink));
        }
    }

    private static void update(PsseHyg3Governor governor, boolean staggered, double[] states, double[] rates) {
        if (staggered) {
            governor.readStaggeredState(states, 0); governor.evaluateStaggered(H, rates, 0);
            governor.applyStaggeredState(states, 0, H); governor.completeStaggeredStep(H);
        } else {
            governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, governor.getMachine(), 0);
            governor.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, governor.getMachine(), 1);
        }
        sink = governor.getOutput(governor.getMachine());
        governor.readPublishedState(states, 0);
    }
}
