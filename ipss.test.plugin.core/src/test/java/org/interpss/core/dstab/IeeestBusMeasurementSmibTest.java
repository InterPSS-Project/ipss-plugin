package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import org.interpss.core.dstab.reference.IeeestMeasurementReference;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GenrouNativeReference;
import com.interpss.dstab.controller.pss.ieeest.SecondOrderLagBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine;
import com.interpss.dstab.mach.ieeevc.IeeeVcData;

/** Local and remote bus measurements must not alias machine signals. */
public class IeeestBusMeasurementSmibTest extends CorePluginTestSetup {
    static IeeestPowerInputSmibTest.Fixture load(int mode, int remote, Path directory) throws Exception {
        var p = GenrouNativeReference.profiles().stream().filter(x -> x.name().equals("flat-coarse")).findFirst().orElseThrow();
        String dynamic = p.input() + "1 'ESST1A' '1' 1 1 .02 .8 -.8 .2 .8 .1 .4 80 .04 99 -99 7.3 -7.3 0 .1 1 0 1 /\n"
                + "1 'IEEEST' '1' " + mode + " " + remote + " .061 .0017 .02 0 0 0 .3 .03 .3 .03 5 5 5 .05 -.05 0 0 /\n";
        return IeeestPowerInputSmibTest.load(p.network(), dynamic, p.step(), directory);
    }

    @Test void bothStagesReadTheSelectedBusAndPhysicalQuantity(@TempDir Path directory) throws Exception {
        for (int mode : List.of(2, 5)) for (int remote : List.of(0, 2)) {
            var f = load(mode, remote, directory.resolve(mode + "-" + remote));
            var local = f.machine().getDStabBus();
            var other = (BaseDStabBus<?, ?>) f.algorithm().getNetwork().getBus("Bus2");
            local.setFreq(local.getFreq() + .01);
            other.setFreq(other.getFreq() - .003);
            local.setVoltage(local.getVoltage().multiply((local.getVoltageMag() - .04) / local.getVoltageMag()));
            other.setVoltage(other.getVoltage().multiply((other.getVoltageMag() + .03) / other.getVoltageMag()));
            f.machine().setSpeed(f.machine().getSpeed() + .3);
            double expected = mode == 2 ? (remote == 0 ? .01 : -.003) : (remote == 0 ? -.04 : .03);
            var first = assertInstanceOf(SecondOrderLagBlock.class, f.stabilizer().getNamedStateBlocks().get("secondOrderLag"));
            for (int stage : List.of(0, 1)) {
                assertTrue(f.stabilizer().nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, f.machine(), stage));
                assertEquals(expected, first.getU0(), 1e-12, mode + "/" + remote + " stage " + stage);
            }
            assertEquals(5, f.stabilizer().getNamedStateBlocks().size(), "Measurement adds no dynamic state");
        }
    }

    @Test void unsupportedOrUnresolvableMeasurementsAreNotSilentlySubstituted(@TempDir Path directory) {
        for (int mode : List.of(1, 3, 4, 7))
            assertThrows(InterpssException.class, () -> load(mode, 2, directory.resolve("mode-" + mode)));
        for (int mode : List.of(2, 5)) for (int remote : List.of(-2, 999))
            assertThrows(InterpssException.class, () -> load(mode, remote, directory.resolve(mode + "-" + remote)));
        assertThrows(InterpssException.class, () -> load(7, 0, directory.resolve("unsupported")));
    }

    @Test void voltageInputIgnoresExciterCompensationAndObserverReadsArePure(@TempDir Path directory) throws Exception {
        var f = load(5, 0, directory);
        var m = assertInstanceOf(IeeeVcRoundRotorMachine.class, f.machine());
        m.setIeeeVcData(new IeeeVcData(.1, .2));
        assertNotEquals(m.getDStabBus().getVoltageMag(), m.getCmlMachineVoltage(), 1e-3,
                "The discriminator must actually change the exciter sensing signal");
        var pss = f.stabilizer();
        assertTrue(pss.initStates(m.getDStabBus(), m));
        var before = pss.getNamedStates();
        for (int i = 0; i < 10; i++) assertEquals(0, pss.busMeasurement.getY(), 0);
        assertEquals(before, pss.getNamedStates());
        assertTrue(pss.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, m, 0));
        var first = assertInstanceOf(SecondOrderLagBlock.class, pss.getNamedStateBlocks().get("secondOrderLag"));
        assertEquals(0, first.getU0(), 1e-12, "Exciter sensing compensation must not become bus voltage");
    }

    private static void impose(BaseDStabBus<?, ?> bus, Map<String, Double> row, int number) {
        bus.setFreq(1 + row.get("FREQ" + number));
        bus.setVoltage(new org.apache.commons.math3.complex.Complex(row.get("V_BUS" + number), 0));
    }

    @Test void remoteMeasurementDoesNotMoveTerminalVoltageCutoutAndReinitRefreshesReference(@TempDir Path directory) throws Exception {
        var f = load(5, 2, directory);
        var pss = f.stabilizer();
        var local = f.machine().getDStabBus();
        var remote = (BaseDStabBus<?, ?>) f.algorithm().getNetwork().getBus("Bus2");
        pss.vcl = .8;
        remote.setVoltage(new org.apache.commons.math3.complex.Complex(.5, 0));
        assertTrue(pss.outputGate.initStateY0(.02));
        assertEquals(.02, pss.outputGate.getY(), 1e-12, "Remote input is not the local cutout sensor");
        local.setVoltage(new org.apache.commons.math3.complex.Complex(.7, 0));
        assertEquals(0, pss.outputGate.getY(), 0);
        local.setVoltage(new org.apache.commons.math3.complex.Complex(1, 0));
        // Undo the deliberately imposed gate signal before equilibrium init;
        // an ordinary washout cannot initialize to a nonzero output.
        assertTrue(pss.outputGate.initStateY0(0));
        assertTrue(pss.initStates(local, f.machine()));
        assertEquals(0, pss.busMeasurement.getY(), 0, "Reinitialization captures the newly selected operating point");
        remote.setStatus(false);
        assertFalse(pss.initStates(local, f.machine()), "Inactive remote measurements cannot silently fall back");
    }

    @Test void replayEveryNativeMeasurementWithoutDroppingHistoryOrEventRows(@TempDir Path directory) throws Exception {
        var maxima = new LinkedHashMap<String, Double>();
        assertEquals(16, IeeestMeasurementReference.profiles().size());
        for (var p : IeeestMeasurementReference.profiles()) {
            var rows = HplntdSwitchingNativeSmibTest.rows(p.body());
            assertEquals(p.samples(), rows.size());
            assertEquals(37, rows.getFirst().size());
            assertTrue(rows.getFirst().get("time_s") < 0);
            var f = IeeestPowerInputSmibTest.load(p.network(), p.input(), p.step(), directory.resolve(p.name()));
            var pss = f.stabilizer();
            int number = p.name().contains("remote") ? 2 : 1;
            var bus = (BaseDStabBus<?, ?>) f.algorithm().getNetwork().getBus("Bus" + number);
            var initial = rows.getFirst();
            impose(bus, initial, number);
            assertTrue(pss.initStates(f.machine().getDStabBus(), f.machine()));
            var first = assertInstanceOf(SecondOrderLagBlock.class, pss.getNamedStateBlocks().get("secondOrderLag"));
            String key = (p.mode() == 2 ? "FREQ" : "V_BUS") + number;
            double previousTime = 0, maximum = 0;
            int duplicates = 0;
            var previous = initial;
            var text = new StringBuilder("raw_row,time_s,expected_input,sampled_input,native_vstab,replayed_vstab\n");
            for (int index = 0; index < rows.size(); index++) {
                var row = rows.get(index);
                double time = row.get("time_s"), dt = time > previousTime ? p.nativeStep() : 0;
                if (index > 0 && time == rows.get(index - 1).get("time_s")) duplicates++;
                for (double value : row.values()) assertTrue(Double.isFinite(value));
                var storage = pss.getNamedStates();
                impose(bus, previous, number);
                assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, f.machine(), 0));
                impose(bus, row, number);
                assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, f.machine(), 1));
                if (dt == 0) for (var state : storage.entrySet())
                    assertEquals(state.getValue(), pss.getNamedStates().get(state.getKey()), 0);
                double expected = row.get(key) - initial.get(key);
                assertEquals(expected, first.getU0(), 1e-12, p.name() + " row " + index);
                double actual = pss.getOutput(f.machine());
                double error = Math.abs(actual - row.get("VOTHSG"));
                maximum = Math.max(maximum, error);
                boolean coarse = p.step() == .0005, local = number == 1;
                double bound = p.mode() == 2
                        ? (p.fault() ? (local ? (coarse ? 1.02e-5 : 2.6e-6) : (coarse ? 1.95e-6 : 4.95e-7))
                                     : (local ? (coarse ? 2.2e-11 : 1.1e-11) : (coarse ? 3.5e-10 : 1.8e-10)))
                        : (p.fault() ? (local ? (coarse ? 2.5e-3 : 1e-3) : (coarse ? 1.27e-3 : 5.4e-4)) : 6e-6);
                assertTrue(error <= bound, p.name()+" raw row "+index+" error="+error+" bound="+bound);
                text.append(index).append(',').append(time).append(',').append(expected).append(',')
                    .append(first.getU0()).append(',').append(row.get("VOTHSG")).append(',').append(actual).append('\n');
                if (time >= 0) { previousTime = time; previous = row; }
            }
            assertEquals(2, duplicates);
            maxima.put(p.name(), maximum);
            Path output = Path.of("target/ieeest-measurement-playback", p.name() + ".csv");
            Files.createDirectories(output.getParent()); Files.writeString(output, text);
            System.out.println("IEEEST measurement playback " + p.name() + " output_maximum=" + maximum);
        }
        for (String signal : List.of("frequency", "voltage")) for (String location : List.of("local", "remote")) {
            String prefix = signal + "-" + location + "-fault-";
            assertTrue(maxima.get(prefix+"fine") < (signal.equals("frequency") ? .3 : .45)*maxima.get(prefix+"coarse"),
                    "Active measurement residual must refine under step halving: " + prefix);
        }
    }

    @Test void coupledFlatAndFaultRunsRetainDiagnosticMeasurementTraces(@TempDir Path directory) throws Exception {
        for (var p : IeeestMeasurementReference.profiles()) {
            var f = IeeestPowerInputSmibTest.load(p.network(), p.input(), p.step(), p.fault(), directory.resolve(p.name()));
            int ticks = (int) Math.ceil(p.end() / p.step());
            f.algorithm().setTotalSimuTimeSec(ticks * p.step());
            var text = new StringBuilder("time_s,V_BUS1,FREQ1,V_BUS2,FREQ2,SPEED_DEV,VOTHSG\n");
            for (int tick = 0; tick <= ticks; tick++) {
                var a = f.machine().getDStabBus();
                var b = (BaseDStabBus<?, ?>) f.algorithm().getNetwork().getBus("Bus2");
                double[] values = {tick*p.step(), a.getVoltageMag(), a.getFreq()-1,
                        b.getVoltageMag(), b.getFreq()-1, f.machine().getSpeed()-1,
                        f.stabilizer().getOutput(f.machine())};
                for (int index = 0; index < values.length; index++) {
                    assertTrue(Double.isFinite(values[index]));
                    if (index > 0) text.append(',');
                    text.append(values[index]);
                }
                text.append('\n');
                if (tick < ticks) assertTrue(f.algorithm().solveDEqnStep(true));
            }
            Path output = Path.of("target/ieeest-measurement-coupled", p.name()+".csv");
            Files.createDirectories(output.getParent()); Files.writeString(output, text);
        }
    }
}
