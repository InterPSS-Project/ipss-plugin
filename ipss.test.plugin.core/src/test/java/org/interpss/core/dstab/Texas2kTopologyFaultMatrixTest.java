package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;

/**
 * Opt-in topology-selected three-cycle fault matrix for the private Texas2k
 * Series-24 PSS/E cases. The committed test contains no licensed case data.
 */
public class Texas2kTopologyFaultMatrixTest {
    private static final double STEP = 1.0 / 240.0;
    private static final Path SITE_MANIFEST = Path.of("testData", "expected",
            "texas2k_topology_fault_sites.csv");
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final List<CaseFile> CASES = List.of(
            new CaseFile(1, "Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW", "dynamic_models_case1.dyr"),
            new CaseFile(2, "Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW", "dynamic_models_case2.dyr"),
            new CaseFile(3, "Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW", "dynamic_models_case3.dyr"),
            new CaseFile(4, "Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW", "dynamic_models_case4.dyr"),
            new CaseFile(5, "Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW", "dynamic_models_case5.dyr"),
            new CaseFile(6, "Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW", "dynamic_models_case6.dyr"));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void reviewedFaultSiteManifestCoversRequiredTopologyClasses() throws Exception {
        List<FaultSite> sites = faultSites();
        assertTrue(sites.size() == 15, "Reviewed fault-site manifest must contain 15 events");
        assertTrue(sites.stream().filter(site -> site.category().equals("conventional-terminal"))
                .count() == 6, "Every case must include the conventional terminal");
        assertTrue(sites.stream().filter(site -> site.category().equals("renewable-plant"))
                .count() == 4, "Cases 3-6 must include the renewable plant");
        assertTrue(sites.stream().filter(site -> site.category().equals("weak-grid"))
                .count() == 4, "Cases 3-6 must include the weak-grid bus");
        assertTrue(sites.stream().filter(site -> site.category().equals("gfm-terminal"))
                .count() == 1, "Case 6 must include the GFM terminal");
        assertTrue(sites.stream().allMatch(site -> site.caseNumber() >= 1
                        && site.caseNumber() <= CASES.size() && site.busId().startsWith("Bus")
                        && !site.selectionReason().isBlank()),
                "Every reviewed site must identify a valid case, bus, and selection reason");
    }

    @Test
    void runTopologySelectedFaultMatrix() throws Exception {
        assumeTrue(Boolean.getBoolean("texas2k.fault.matrix.enabled"),
                "Enable with -Dtexas2k.fault.matrix.enabled=true");
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);

        List<FaultSite> sites = faultSites();
        assertTrue(sites.size() == 15, "Reviewed fault-site manifest must contain 15 events");
        List<MatrixResult> results = new ArrayList<>();
        for (FaultSite site : sites) {
            CaseFile source = CASES.stream().filter(candidate -> candidate.number() == site.caseNumber())
                    .findFirst().orElseThrow();
            results.add(run(source, site));
        }

        Path report = Path.of("target", "texas2k-topology-fault-matrix.csv");
        Files.createDirectories(report.getParent());
        Files.writeString(report, toCsv(results), StandardCharsets.UTF_8);
        System.out.println("Texas2k topology fault matrix: " + report.toAbsolutePath());
    }

    private static MatrixResult run(CaseFile source, FaultSite site) throws Exception {
        SimuContext context = load(source);
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        assertTrue(network.getBus(site.busId()) != null && network.getBus(site.busId()).isActive(),
                source.directory() + " missing active " + site.category() + " " + site.busId());

        List<String> monitoredBuses = nearbyBuses(network, site.busId());
        List<String> monitoredMachines = monitoredMachines(network, site.busId());
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(monitoredBuses.toArray(String[]::new));
        monitor.addGeneratorStdMonitor(monitoredMachines.toArray(String[]::new));
        algorithm.setSimuOutputHandler(monitor);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.50);
        algorithm.setOutPutPerSteps(1);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                site.busId(), network, SimpleFaultCode.GROUND_3P, Complex.ZERO, null,
                .05, .05), "ThreeCycleFault@" + site.busId());

        assertTrue(algorithm.initialization(), source.directory() + " " + site.category()
                + " initialization");
        assertTrue(algorithm.performSimulation(), source.directory() + " " + site.category()
                + " simulation");
        writeTrajectory(source, site, monitor, monitoredBuses, monitoredMachines);

        double faultMinimum = minimum(monitor, site.busId());
        double faultFinal = last(monitor, site.busId());
        assertTrue(faultMinimum < .10, source.directory() + " " + site.category()
                + " fault did not depress terminal voltage: " + faultMinimum);
        assertTrue(faultFinal > .50 && Double.isFinite(faultFinal), source.directory() + " "
                + site.category() + " terminal did not retain finite recovery: " + faultFinal);

        double maximumNearbyDeviation = 0.0;
        for (String busId : monitoredBuses) {
            var values = monitor.getBusVoltTable().get(busId);
            assertTrue(values.values().stream().allMatch(value -> Double.isFinite(value.value)),
                    source.directory() + " non-finite voltage at " + busId);
            if (!busId.equals(site.busId())) {
                double initial = values.get(0).value;
                maximumNearbyDeviation = Math.max(maximumNearbyDeviation,
                        values.values().stream()
                                .mapToDouble(value -> Math.abs(value.value - initial))
                                .max().orElseThrow());
            }
        }

        double maximumSpeedDeviation = 0.0;
        for (String machineId : monitoredMachines) {
            var values = monitor.getMachSpeedTable().get(machineId);
            assertTrue(values.values().stream().allMatch(value -> Double.isFinite(value.value)),
                    source.directory() + " non-finite speed at " + machineId);
            double initial = values.get(0).value;
            maximumSpeedDeviation = Math.max(maximumSpeedDeviation,
                    values.values().stream().mapToDouble(value -> Math.abs(value.value - initial))
                            .max().orElseThrow());
        }
        assertTrue(maximumSpeedDeviation < .10, source.directory() + " " + site.category()
                + " excessive monitored-machine speed deviation: " + maximumSpeedDeviation);

        MatrixResult result = new MatrixResult(source.number(), site.category(), site.busId(),
                String.join("|", monitoredBuses), String.join("|", monitoredMachines),
                faultMinimum, faultFinal,
                maximumNearbyDeviation, maximumSpeedDeviation);
        System.out.printf(Locale.ROOT,
                "Texas2k fault case=%d category=%s bus=%s min=%.9g final=%.9g "
                        + "nearbyDv=%.9g speedDw=%.9g%n",
                result.caseNumber(), result.category(), result.faultBus(), result.minimumVoltage(),
                result.finalVoltage(), result.maximumNearbyVoltageDeviation(),
                result.maximumMachineSpeedDeviation());
        return result;
    }

    private static SimuContext load(CaseFile source) throws Exception {
        Path directory = ROOT.resolve(source.directory());
        Path raw = directory.resolve(source.raw());
        Path dyr = directory.resolve(source.dyr());
        return load(source, dyr);
    }

    private static SimuContext load(CaseFile source, Path dyr) throws Exception {
        Path directory = ROOT.resolve(source.directory());
        Path raw = directory.resolve(source.raw());
        assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);
        List<String> files = new ArrayList<>(List.of(raw.toString(), dyr.toString()));
        Path sourceDyr = directory.resolve(source.dyr());
        String stem = source.dyr().substring(0, source.dyr().length() - 4);
        for (String suffix : List.of("_gnet.idv", "_MODREMOVE.idv")) {
            Path preparation = sourceDyr.resolveSibling(stem + suffix);
            if (Files.isRegularFile(preparation)) files.add(preparation.toString());
        }
        SimuContext context = new PSSEMultiFileLoader().loadDStab(files.toArray(String[]::new));
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        var loadflow = context.getDynSimuAlgorithm().getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-8);
        assertTrue(loadflow.loadflow(), source.directory() + " load flow");
        network.setBypassDataCheck(false);
        network.checkData(loadflow.getDataCheckConfig());
        assertTrue(network.initDStabNet(), source.directory() + " DStab initialization");
        return context;
    }

    private static List<String> nearbyBuses(BaseDStabNetwork<?, ?> network, String faultBus) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(faultBus);
        network.getBranchList().stream().filter(AclfBranch.class::isInstance)
                .map(AclfBranch.class::cast).filter(AclfBranch::isActive)
                .filter(branch -> faultBus.equals(branch.getFromBus().getId())
                        || faultBus.equals(branch.getToBus().getId()))
                .map(branch -> faultBus.equals(branch.getFromBus().getId())
                        ? branch.getToBus().getId() : branch.getFromBus().getId())
                .filter(id -> network.getBus(id) != null && network.getBus(id).isActive())
                .sorted(Comparator.naturalOrder()).limit(4).forEach(ids::add);
        return List.copyOf(ids);
    }

    private static List<String> monitoredMachines(BaseDStabNetwork<?, ?> network,
            String faultBus) {
        Set<String> ids = new LinkedHashSet<>();
        var bus = network.getBus(faultBus);
        bus.getContributeGenList().stream().filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast).filter(DStabGen::isActive)
                .filter(gen -> gen.getMach() != null).map(gen -> gen.getMach().getId())
                .sorted().forEach(ids::add);
        network.getBusList().stream().flatMap(candidate -> candidate.getContributeGenList().stream())
                .filter(DStabGen.class::isInstance).map(DStabGen.class::cast)
                .filter(DStabGen::isActive).filter(gen -> gen.getMach() != null)
                .map(gen -> gen.getMach().getId()).sorted().filter(id -> !ids.contains(id))
                .limit(Math.max(0, 3 - ids.size())).forEach(ids::add);
        assertTrue(!ids.isEmpty(), "No active synchronous-machine speed monitors");
        return List.copyOf(ids);
    }

    private static List<FaultSite> faultSites() throws Exception {
        assertTrue(Files.isRegularFile(SITE_MANIFEST),
                "Missing reviewed fault-site manifest: " + SITE_MANIFEST);
        List<FaultSite> sites = new ArrayList<>();
        List<String> lines = Files.readAllLines(SITE_MANIFEST, StandardCharsets.UTF_8);
        assertTrue(!lines.isEmpty() && lines.get(0).equals("case,category,bus,selection_reason"),
                "Unexpected fault-site manifest header");
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            String[] fields = lines.get(index).split(",", 4);
            assertTrue(fields.length == 4 && !fields[3].isBlank(),
                    "Invalid fault-site manifest row " + (index + 1));
            sites.add(new FaultSite(Integer.parseInt(fields[0]), fields[1], fields[2], fields[3]));
        }
        return sites;
    }

    private static void writeTrajectory(CaseFile source, FaultSite site, StateMonitor monitor,
            List<String> buses, List<String> machines) throws Exception {
        Path directory = Path.of("target", "texas2k-topology-fault-matrix");
        Files.createDirectories(directory);
        Path path = directory.resolve("case" + source.number() + "-" + site.category()
                + "-" + site.busId() + ".csv");
        StringBuilder csv = new StringBuilder("time_s");
        for (String bus : buses) csv.append(',').append(bus).append("_V")
                .append(',').append(bus).append("_ANGLE");
        for (String machine : machines) csv.append(',').append(machine).append("_SPEED")
                .append(',').append(machine).append("_ANGLE")
                .append(',').append(machine).append("_PE")
                .append(',').append(machine).append("_PM")
                .append(',').append(machine).append("_EFD");
        csv.append('\n');
        int samples = monitor.getBusVoltTable().get(site.busId()).size();
        for (int index = 0; index < samples; index++) {
            csv.append(format(monitor.getBusVoltTable().get(site.busId()).get(index).t));
            for (String bus : buses) {
                csv.append(',').append(format(value(monitor.getBusVoltTable(), bus, index)))
                        .append(',').append(format(value(monitor.getBusAngleTable(), bus, index)));
            }
            for (String machine : machines) {
                csv.append(',').append(format(value(monitor.getMachSpeedTable(), machine, index)))
                        .append(',').append(format(value(monitor.getMachAngleTable(), machine, index)))
                        .append(',').append(format(value(monitor.getMachPeTable(), machine, index)))
                        .append(',').append(format(value(monitor.getMachPmTable(), machine, index)))
                        .append(',').append(format(value(monitor.getMachEfdTable(), machine, index)));
            }
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static double value(Hashtable<String, Hashtable<Integer, MonitorRecord>> table,
            String id, int index) {
        var series = table.get(id);
        assertTrue(series != null && series.get(index) != null,
                "Missing monitored channel " + id + " at sample " + index);
        return series.get(index).value;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private static double minimum(StateMonitor monitor, String busId) {
        return monitor.getBusVoltTable().get(busId).values().stream()
                .mapToDouble(value -> value.value).min().orElseThrow();
    }

    private static double last(StateMonitor monitor, String busId) {
        var values = monitor.getBusVoltTable().get(busId);
        return values.get(values.size() - 1).value;
    }

    private static String toCsv(List<MatrixResult> results) {
        StringBuilder csv = new StringBuilder("case,category,fault_bus,monitored_buses,"
                + "monitored_machines,"
                + "minimum_voltage,final_voltage,max_nearby_voltage_deviation,"
                + "max_machine_speed_deviation\n");
        for (MatrixResult result : results) {
            csv.append(result.caseNumber()).append(',').append(result.category()).append(',')
                    .append(result.faultBus()).append(',').append(result.monitoredBuses()).append(',')
                    .append(result.monitoredMachines()).append(',')
                    .append(String.format(Locale.ROOT, "%.12g", result.minimumVoltage())).append(',')
                    .append(String.format(Locale.ROOT, "%.12g", result.finalVoltage())).append(',')
                    .append(String.format(Locale.ROOT, "%.12g",
                            result.maximumNearbyVoltageDeviation())).append(',')
                    .append(String.format(Locale.ROOT, "%.12g",
                            result.maximumMachineSpeedDeviation())).append('\n');
        }
        return csv.toString();
    }

    private record CaseFile(int number, String directory, String raw, String dyr) { }

    private record FaultSite(int caseNumber, String category, String busId,
            String selectionReason) { }

    private record MatrixResult(int caseNumber, String category, String faultBus,
            String monitoredBuses, String monitoredMachines, double minimumVoltage,
            double finalVoltage,
            double maximumNearbyVoltageDeviation, double maximumMachineSpeedDeviation) { }

}
