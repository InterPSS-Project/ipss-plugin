package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.annotate.util.AnControllerInitializer;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciter;
import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineController;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.simu.SimuContext;

/** Diagnostic parity run for the complete ANDES WECC179 dynamic-data stack. */
class Wecc179FullDyrDiagnosticTest {
    private static final Path CASE_DIR = Path.of(System.getProperty("andes.wecc179.case.dir",
            Path.of("target", "andes-python", "andes", "cases", "wecc").toString()));
    private static final Path RAW = CASE_DIR.resolve("wecc.raw");
    private static final Path DYR = CASE_DIR.resolve("wecc_full.dyr");

    @Test
    void recordsAllStatesBeforeTheFirstDivergence() throws Exception {
        assumeTrue(Files.isRegularFile(RAW), "Install ANDES or set -Dandes.wecc179.case.dir");
        assumeTrue(Files.isRegularFile(DYR), "Install ANDES or set -Dandes.wecc179.case.dir");
        IpssCorePlugin.init();
        SparseEqnSolverProvider.useJavaKlu();

        Path selectedDyr = selectedDyr();
        SimuContext context = new PSSEMultiFileLoader().loadDStab(
                RAW.toString(), selectedDyr.toString());
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        network.setBypassDataCheck(true);
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        if (Boolean.getBoolean("wecc179.full.default.solver")) {
            algorithm.setSolver(new DStabSolverImpl(algorithm));
        }
        LoadflowAlgorithm loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(false);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-10);
        assertTrue(loadflow.loadflow(), "WECC179 load flow must converge");

        String[] buses = network.getBusList().stream().filter(bus -> bus.isActive())
                .map(bus -> bus.getId()).toArray(String[]::new);
        String[] machines = network.getBusList().stream()
                .flatMap(bus -> bus.getContributeGenList().stream())
                .filter(gen -> gen instanceof DStabGen)
                .map(gen -> ((DStabGen) gen).getMach())
                .filter(mach -> mach != null)
                .map(Machine::getId).toArray(String[]::new);
        String focusBus = System.getProperty("wecc179.full.focus.bus", "").trim();
        if (!focusBus.isEmpty()) {
            String focusMachine = "Bus" + focusBus + "-mach1";
            for (String machineId : machines) {
                if (machineId.equals(focusMachine)) continue;
                RoundRotorMachine machine = (RoundRotorMachine) network.getMachine(machineId);
                machine.setH(1.0e12);
                machine.setTd01(1.0e12);
                machine.setTq01(1.0e12);
                machine.setTd011(1.0e12);
                machine.setTq011(1.0e12);
            }
        }
        if (Boolean.getBoolean("wecc179.full.disable.saturation")) {
            for (String machineId : machines) {
                RoundRotorMachine machine = (RoundRotorMachine) network.getMachine(machineId);
                machine.setSe100(0.0);
                machine.setSe120(0.0);
            }
        }
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(buses);
        monitor.addGeneratorStdMonitor(machines);
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(Double.parseDouble(System.getProperty(
                "wecc179.full.time.step", Double.toString(1.0 / 240.0))));
        algorithm.setTotalSimuTimeSec(Double.parseDouble(System.getProperty(
                "wecc179.full.total.time", "1.0")));
        algorithm.setOutPutPerSteps(1);
        String faultBus = System.getProperty("wecc179.full.fault.bus", "").trim();
        if (!faultBus.isEmpty()) {
            double faultStart = Double.parseDouble(System.getProperty(
                    "wecc179.full.fault.start", "1.0"));
            double faultDuration = Double.parseDouble(System.getProperty(
                    "wecc179.full.fault.duration", "0.05"));
            double faultX = Double.parseDouble(System.getProperty(
                    "wecc179.full.fault.x", "0.005"));
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                    "Bus" + faultBus, network, SimpleFaultCode.GROUND_3P,
                    new Complex(0.0, faultX), null, faultStart, faultDuration),
                    "ThreePhaseFault@Bus" + faultBus);
        }

        assertTrue(algorithm.initialization(), "WECC179 full-DYR initialization must converge");
        if (network.getCustomBusCurrInjHashtable() != null) {
            double maximumResidual = network.getCustomBusCurrInjHashtable().values().stream()
                    .mapToDouble(value -> value.abs()).max().orElse(0.0);
            assertTrue(maximumResidual < 1.0e-6,
                    () -> "DStab Norton initialization residual is too large: " + maximumResidual);
        }
        assertCmlControllersInitialized(network, machines);
        Path output = Path.of(System.getProperty("wecc179.full.output.dir",
                Path.of("target", "wecc179-full-dyr-diagnostic").toString()));
        Files.createDirectories(output);
        writeControllerInventory(output.resolve("interpss-controllers.txt"), network, machines);
        writeBranchInventory(output.resolve("interpss-branches.csv"), network);
        writeMachineInventory(output.resolve("interpss-machines.csv"), network, machines);
        writeBusAdmittanceInventory(output.resolve("interpss-bus-admittance.csv"), network);
        String[] stabilizerMachines = java.util.Arrays.stream(machines)
                .filter(machineId -> network.getMachine(machineId).getStabilizer() != null)
                .toArray(String[]::new);
        List<Double> stabilizerTimes = new ArrayList<>();
        List<double[]> stabilizerOutputs = new ArrayList<>();
        List<Double> machineStateTimes = new ArrayList<>();
        List<double[]> machineStates = new ArrayList<>();
        String[] exst1Machines = java.util.Arrays.stream(machines)
                .filter(machineId -> network.getMachine(machineId).getExciter()
                        instanceof IEEE1981ST1Exciter)
                .toArray(String[]::new);
        List<Double> exst1Times = new ArrayList<>();
        List<double[]> exst1States = new ArrayList<>();
        String[] esst3aMachines = java.util.Arrays.stream(machines)
                .filter(machineId -> network.getMachine(machineId).getExciter()
                        instanceof IEEE2005ST3AExciter)
                .toArray(String[]::new);
        List<Double> esst3aTimes = new ArrayList<>();
        List<double[]> esst3aStates = new ArrayList<>();
        String[] ieeeg1Machines = java.util.Arrays.stream(machines)
                .filter(machineId -> network.getMachine(machineId).getGovernor()
                        instanceof IeeeSteamTCDRGovernor)
                .toArray(String[]::new);
        List<Double> ieeeg1Times = new ArrayList<>();
        List<double[]> ieeeg1States = new ArrayList<>();
        String[] esdc2aMachines = java.util.Arrays.stream(machines)
                .filter(machineId -> network.getMachine(machineId).getExciter()
                        instanceof Esdc2aExciter)
                .toArray(String[]::new);
        List<Double> esdc2aTimes = new ArrayList<>();
        List<double[]> esdc2aStates = new ArrayList<>();
        recordStabilizerOutputs(network, stabilizerMachines, stabilizerTimes,
                stabilizerOutputs, algorithm.getSimuTime());
        recordMachineStates(network, machines, machineStateTimes, machineStates,
                algorithm.getSimuTime());
        recordExst1States(network, exst1Machines, exst1Times, exst1States,
                algorithm.getSimuTime());
        recordEsst3aStates(network, esst3aMachines, esst3aTimes, esst3aStates,
                algorithm.getSimuTime());
        recordIeeeg1States(network, ieeeg1Machines, ieeeg1Times, ieeeg1States,
                algorithm.getSimuTime());
        recordEsdc2aStates(network, esdc2aMachines, esdc2aTimes, esdc2aStates,
                algorithm.getSimuTime());
        boolean success = true;
        while (algorithm.getSimuTime() <= algorithm.getTotalSimuTimeSec()) {
            if (!algorithm.solveDEqnStep(true)) {
                success = false;
                break;
            }
            recordStabilizerOutputs(network, stabilizerMachines, stabilizerTimes,
                    stabilizerOutputs, algorithm.getSimuTime());
            recordMachineStates(network, machines, machineStateTimes, machineStates,
                    algorithm.getSimuTime());
            recordExst1States(network, exst1Machines, exst1Times, exst1States,
                    algorithm.getSimuTime());
            recordEsst3aStates(network, esst3aMachines, esst3aTimes, esst3aStates,
                    algorithm.getSimuTime());
            recordIeeeg1States(network, ieeeg1Machines, ieeeg1Times, ieeeg1States,
                    algorithm.getSimuTime());
            recordEsdc2aStates(network, esdc2aMachines, esdc2aTimes, esdc2aStates,
                    algorithm.getSimuTime());
        }

        writeTable(output.resolve("interpss-bus-voltage.csv"), buses,
                monitor.getBusVoltTable());
        writeMachineTable(output.resolve("interpss-machine.csv"), machines, monitor);
        writeStabilizerTable(output.resolve("interpss-pss.csv"), stabilizerMachines,
                stabilizerTimes, stabilizerOutputs);
        writeMachineStateTable(output.resolve("interpss-genrou-state.csv"), machines,
                machineStateTimes, machineStates);
        writeExst1StateTable(output.resolve("interpss-exst1-state.csv"), exst1Machines,
                exst1Times, exst1States);
        writeBlockStateTable(output.resolve("interpss-esst3a-state.csv"), esst3aMachines,
                esst3aTimes, esst3aStates,
                new String[] {"LG_y", "vil", "LL_y", "LAW1_y", "VG_y", "LAW2_y", "vout"});
        writeBlockStateTable(output.resolve("interpss-ieeeg1-state.csv"), ieeeg1Machines,
                ieeeg1Times, ieeeg1States,
                new String[] {"LL_y", "vsl", "IAW_y", "L4_y", "L5_y", "L6_y", "L7_y", "PHP"});
        writeBlockStateTable(output.resolve("interpss-esdc2a-state.csv"), esdc2aMachines,
                esdc2aTimes, esdc2aStates,
                new String[] {"LG_y", "vi", "LL_y", "LA_y", "Se", "INT_y", "WF_y"});
        assertTrue(success, "WECC179 full-DYR simulation failed at t=" + algorithm.getSimuTime());
    }

    private static void writeBusAdmittanceInventory(Path path,
            BaseDStabNetwork<?, ?> network) throws Exception {
        StringBuilder csv = new StringBuilder("bus,sort,y_diag_g,y_diag_b\n");
        for (var bus : network.getBusList()) {
            if (!bus.isActive()) continue;
            int sort = bus.getSortNumber();
            Complex diagonal = network.getYMatrix().getA(sort, sort);
            csv.append(bus.getId()).append(',').append(sort).append(',')
                    .append(format(diagonal.getReal())).append(',')
                    .append(format(diagonal.getImaginary())).append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeMachineInventory(Path path, BaseDStabNetwork<?, ?> network,
            String[] machineIds) throws Exception {
        StringBuilder csv = new StringBuilder(
                "id,bus,mva_base,ra_machine,xd2_machine,xq2_machine,ygen_g_system,ygen_b_system,"
                + "v_re,v_im,ixy_re,ixy_im,igen_re,igen_im,angle_rad,psid11,psiq11,efd\n");
        for (String machineId : machineIds) {
            RoundRotorMachine machine = (RoundRotorMachine) network.getMachine(machineId);
            Complex ygen = machine.getYgen();
            Complex voltage = machine.getDStabBus().getVoltage();
            Complex igen = machine.getIgen();
            Complex ixy = igen.subtract(voltage.multiply(ygen));
            csv.append(machineId).append(',').append(machine.getDStabBus().getId()).append(',')
                    .append(format(machine.getParentGen().getMvaBase())).append(',')
                    .append(format(machine.getRa())).append(',')
                    .append(format(machine.getXd11())).append(',')
                    .append(format(machine.getXq11())).append(',')
                    .append(format(ygen.getReal())).append(',')
                    .append(format(ygen.getImaginary())).append(',')
                    .append(format(voltage.getReal())).append(',')
                    .append(format(voltage.getImaginary())).append(',')
                    .append(format(ixy.getReal())).append(',')
                    .append(format(ixy.getImaginary())).append(',')
                    .append(format(igen.getReal())).append(',')
                    .append(format(igen.getImaginary())).append(',')
                    .append(format(machine.getAngle())).append(',')
                    .append(format(machine.getPsid11())).append(',')
                    .append(format(machine.getPsiq11())).append(',')
                    .append(format(machine.getEfd())).append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeBranchInventory(Path path, BaseDStabNetwork<?, ?> network)
            throws Exception {
        StringBuilder csv = new StringBuilder(
                "id,from_bus,to_bus,circuit,type,r,x,from_tap,to_tap,phase_shift_rad,b_half,g_from,b_from,g_to,b_to\n");
        for (var branch : network.getBranchList()) {
            Complex z = branch.getZ();
            Complex half = branch.getHShuntY();
            Complex from = branch.getFromShuntY();
            Complex to = branch.getToShuntY();
            csv.append(branch.getId()).append(',')
                    .append(branch.getFromBus().getId()).append(',')
                    .append(branch.getToBus().getId()).append(',')
                    .append(branch.getCircuitNumber()).append(',')
                    .append(branch.getBranchCode()).append(',')
                    .append(format(z.getReal())).append(',').append(format(z.getImaginary())).append(',')
                    .append(format(branch.getFromTurnRatio())).append(',')
                    .append(format(branch.getToTurnRatio())).append(',')
                    .append(format(branch.getFromPSXfrAngle())).append(',')
                    .append(format(half.getImaginary())).append(',')
                    .append(format(from.getReal())).append(',').append(format(from.getImaginary())).append(',')
                    .append(format(to.getReal())).append(',').append(format(to.getImaginary())).append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeControllerInventory(Path path, BaseDStabNetwork<?, ?> network,
            String[] machineIds) throws Exception {
        StringBuilder text = new StringBuilder();
        for (String machineId : machineIds) {
            Machine machine = network.getMachine(machineId);
            for (MachineController controller : machine.getControllerList()) {
                text.append(machineId).append(',').append(controller.getType()).append(',')
                        .append(controller.getClass().getName()).append('\n');
            }
        }
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void recordStabilizerOutputs(BaseDStabNetwork<?, ?> network,
            String[] machineIds, List<Double> times, List<double[]> outputs, double time) {
        times.add(time);
        double[] values = new double[machineIds.length];
        for (int index = 0; index < machineIds.length; index++) {
            Machine machine = network.getMachine(machineIds[index]);
            values[index] = machine.getStabilizer().getOutput(machine);
        }
        outputs.add(values);
    }

    private static void recordMachineStates(BaseDStabNetwork<?, ?> network,
            String[] machineIds, List<Double> times, List<double[]> outputs, double time) {
        times.add(time);
        double[] values = new double[machineIds.length * 6];
        for (int index = 0; index < machineIds.length; index++) {
            RoundRotorMachine machine = (RoundRotorMachine) network.getMachine(machineIds[index]);
            int offset = index * 6;
            values[offset] = machine.getEq1();
            values[offset + 1] = machine.getEd1();
            values[offset + 2] = machine.getPsikd();
            values[offset + 3] = machine.getPsikq();
            values[offset + 4] = machine.getPsid11();
            values[offset + 5] = machine.getPsiq11();
        }
        outputs.add(values);
    }

    private static void recordExst1States(BaseDStabNetwork<?, ?> network,
            String[] machineIds, List<Double> times, List<double[]> outputs, double time)
            throws Exception {
        times.add(time);
        String[] fields = {"trDelayBlock", "gainBlock", "filterBlock", "kaDelayBlock",
                "washoutBlock"};
        double[] values = new double[machineIds.length * fields.length];
        for (int index = 0; index < machineIds.length; index++) {
            IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter)
                    network.getMachine(machineIds[index]).getExciter();
            for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
                values[index * fields.length + fieldIndex] =
                        ((ICMLStaticBlock) AnControllerInitializer.getBlock(fields[fieldIndex],
                                exciter.getFieldWrapperList())).getY();
            }
        }
        outputs.add(values);
    }

    private static void recordEsst3aStates(BaseDStabNetwork<?, ?> network,
            String[] machineIds, List<Double> times, List<double[]> outputs, double time)
            throws Exception {
        times.add(time);
        String[] fields = {"trDelayBlock", "viLimitBlock", "filterBlock", "taDelayBlock",
                "kgGainBlock", "tmDelayBlock", "customBlock"};
        double[] values = new double[machineIds.length * fields.length];
        for (int index = 0; index < machineIds.length; index++) {
            IEEE2005ST3AExciter exciter = (IEEE2005ST3AExciter)
                    network.getMachine(machineIds[index]).getExciter();
            for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
                values[index * fields.length + fieldIndex] =
                        ((ICMLStaticBlock) AnControllerInitializer.getBlock(fields[fieldIndex],
                                exciter.getFieldWrapperList())).getY();
            }
        }
        outputs.add(values);
    }

    private static void recordIeeeg1States(BaseDStabNetwork<?, ?> network,
            String[] machineIds, List<Double> times, List<double[]> outputs, double time)
            throws Exception {
        times.add(time);
        String[] fields = {"filterBlock", "gainBlock", "intBlock", "chDelayBlock",
                "rh1DelayBlock", "rh2DelayBlock", "coDelayBlock"};
        double[] values = new double[machineIds.length * 8];
        for (int index = 0; index < machineIds.length; index++) {
            Machine machine = network.getMachine(machineIds[index]);
            IeeeSteamTCDRGovernor governor = (IeeeSteamTCDRGovernor) machine.getGovernor();
            int offset = index * 8;
            for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
                double y = ((ICMLStaticBlock) AnControllerInitializer.getBlock(fields[fieldIndex],
                        governor.getFieldWrapperList())).getY();
                // ANDES defines wd = wref - omega; InterPSS feeds omega - wref.
                values[offset + fieldIndex] = fieldIndex == 0 ? -y : y;
            }
            values[offset + 7] = governor.getOutput(machine);
        }
        outputs.add(values);
    }

    private static void recordEsdc2aStates(BaseDStabNetwork<?, ?> network,
            String[] machineIds, List<Double> times, List<double[]> outputs, double time)
            throws Exception {
        times.add(time);
        double[] values = new double[machineIds.length * 7];
        for (int index = 0; index < machineIds.length; index++) {
            Esdc2aExciter exciter = (Esdc2aExciter)
                    network.getMachine(machineIds[index]).getExciter();
            double efd = exciter.getInternalFieldVoltage();
            int offset = index * 7;
            values[offset] = exciter.getSensedVoltage();
            values[offset + 1] = exciter.getVoltageError();
            values[offset + 2] = exciter.getLeadLagOutput();
            values[offset + 3] = exciter.getRegulatorOutput();
            values[offset + 4] = exciter.saturation.eval(new double[] {efd}) * efd;
            values[offset + 5] = efd;
            values[offset + 6] = exciter.getRateFeedbackOutput();
        }
        outputs.add(values);
    }

    private static void writeMachineStateTable(Path path, String[] machineIds,
            List<Double> times, List<double[]> outputs) throws Exception {
        String[] names = {"e1q", "e1d", "e2d", "e2q", "psi2d", "psiq11"};
        StringBuilder csv = new StringBuilder("time_s");
        for (String machineId : machineIds) {
            for (String name : names) csv.append(',').append(machineId).append('.').append(name);
        }
        csv.append('\n');
        for (int row = 0; row < times.size(); row++) {
            csv.append(format(times.get(row)));
            for (double value : outputs.get(row)) csv.append(',').append(format(value));
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeExst1StateTable(Path path, String[] machineIds,
            List<Double> times, List<double[]> outputs) throws Exception {
        String[] names = {"LG_y", "vl", "LL_y", "LR_y", "WF_y"};
        StringBuilder csv = new StringBuilder("time_s");
        for (String machineId : machineIds) {
            for (String name : names) csv.append(',').append(machineId).append('.').append(name);
        }
        csv.append('\n');
        for (int row = 0; row < times.size(); row++) {
            csv.append(format(times.get(row)));
            for (double value : outputs.get(row)) csv.append(',').append(format(value));
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeBlockStateTable(Path path, String[] machineIds,
            List<Double> times, List<double[]> outputs, String[] names) throws Exception {
        StringBuilder csv = new StringBuilder("time_s");
        for (String machineId : machineIds) {
            for (String name : names) csv.append(',').append(machineId).append('.').append(name);
        }
        csv.append('\n');
        for (int row = 0; row < times.size(); row++) {
            csv.append(format(times.get(row)));
            for (double value : outputs.get(row)) csv.append(',').append(format(value));
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeStabilizerTable(Path path, String[] machineIds,
            List<Double> times, List<double[]> outputs) throws Exception {
        StringBuilder csv = new StringBuilder("time_s");
        for (String machineId : machineIds) csv.append(',').append(machineId).append(".vs");
        csv.append('\n');
        for (int row = 0; row < times.size(); row++) {
            csv.append(format(times.get(row)));
            for (double value : outputs.get(row)) csv.append(',').append(format(value));
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void assertCmlControllersInitialized(BaseDStabNetwork<?, ?> network,
            String[] machines) {
        for (String machineId : machines) {
            Machine machine = network.getMachine(machineId);
            if (!machine.getParentGen().isActive()) continue;
            assertTrue(machine == machine.getParentGen().getDynamicGenDevice(),
                    () -> machineId + " network machine differs from generator dynamic device: "
                            + machine.getParentGen().getDynamicGenDevice().getClass().getName());
            assertCmlControllerInitialized(machineId, "stabilizer", machine.getStabilizer());
            assertCmlControllerInitialized(machineId, "exciter", machine.getExciter());
            assertCmlControllerInitialized(machineId, "governor", machine.getGovernor());
        }
    }

    private static void assertCmlControllerInitialized(String machineId, String role,
            MachineController controller) {
        if (controller != null) {
            assertTrue(controller.isActive(),
                    () -> machineId + " " + role + " failed initialization: "
                            + controller.getClass().getName());
        }
        if (controller instanceof AbstractAnnotateController annotated) {
            if (controller instanceof Esdc2aExciter) {
                // ESDC1A/2A deliberately use direct five-state equations so
                // valid algebraic time constants do not require CML wrappers.
                return;
            }
            assertTrue(annotated.getFieldWrapperList() != null,
                    () -> machineId + " active " + role + " was not initialized: "
                            + controller.getClass().getName());
        }
    }

    private static void writeMachineTable(Path path, String[] machines, StateMonitor monitor)
            throws Exception {
        List<String> columns = new ArrayList<>();
        List<Hashtable<String, Hashtable<Integer, MonitorRecord>>> tables = new ArrayList<>();
        for (String machine : machines) {
            columns.add(machine + ".speed");
            tables.add(monitor.getMachSpeedTable());
            columns.add(machine + ".angle_deg");
            tables.add(monitor.getMachAngleTable());
            columns.add(machine + ".pe");
            tables.add(monitor.getMachPeTable());
            columns.add(machine + ".pm");
            tables.add(monitor.getMachPmTable());
            columns.add(machine + ".efd");
            tables.add(monitor.getMachEfdTable());
        }
        StringBuilder csv = new StringBuilder("time_s");
        columns.forEach(column -> csv.append(',').append(column));
        csv.append('\n');
        int samples = monitor.getMachSpeedTable().get(machines[0]).size();
        for (int index = 0; index < samples; index++) {
            csv.append(format(monitor.getMachSpeedTable().get(machines[0]).get(index).t));
            for (int column = 0; column < columns.size(); column++) {
                String machine = machines[column / 5];
                csv.append(',').append(format(tables.get(column).get(machine).get(index).value));
            }
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeTable(Path path, String[] ids,
            Hashtable<String, Hashtable<Integer, MonitorRecord>> table) throws Exception {
        StringBuilder csv = new StringBuilder("time_s");
        for (String id : ids) csv.append(',').append(id);
        csv.append('\n');
        int samples = table.get(ids[0]).size();
        for (int index = 0; index < samples; index++) {
            csv.append(format(table.get(ids[0]).get(index).t));
            for (String id : ids) csv.append(',').append(format(table.get(id).get(index).value));
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static Path selectedDyr() throws Exception {
        String selection = System.getProperty("wecc179.full.models", "").trim();
        if (selection.isEmpty()) return DYR;
        Set<String> accepted = java.util.Arrays.stream(selection.split(","))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String busSelection = System.getProperty("wecc179.full.buses", "").trim();
        Set<String> acceptedBuses = java.util.Arrays.stream(busSelection.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
        StringBuilder filtered = new StringBuilder();
        for (String record : Files.readString(DYR, StandardCharsets.UTF_8).split("/")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?s)^\\s*-?\\d+\\s+'([^']+)'").matcher(record);
            if (matcher.find() && accepted.contains(matcher.group(1).toUpperCase(Locale.ROOT))
                    && (acceptedBuses.isEmpty()
                            || acceptedBuses.contains(record.trim().split("\\s+", 2)[0]))) {
                filtered.append(record).append("/\n");
            }
        }
        Path output = Path.of("target", "wecc179-full-dyr-diagnostic", "selected.dyr");
        Files.createDirectories(output.getParent());
        Files.writeString(output, filtered, StandardCharsets.UTF_8);
        return output;
    }
}
