package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.renewable.Regca1Model;
import org.junit.jupiter.api.Test;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;

class Wecc240DStabTest {
    private static final Path CASE_DIR = Path.of(System.getProperty("wecc240.case.dir",
            Path.of("testData", "private", "wecc240",
                    "WECC 240-bus case (2018 summar peak) for 2021 IEEE-NASPI OSL Contest").toString()));
    private static final Path RAW = resolveCaseFile("wecc240.raw.file",
            "240busWECC_2018_PSS.raw", "WECC240_v32.raw");
    private static final Path DYR = resolveCaseFile("wecc240.dyr.file",
            "240busWECC_2018_PSS.dyr", "WECC240_v32.dyr");

    private static Path resolveCaseFile(String property, String preferredName, String alternateName) {
        String configured = System.getProperty(property);
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        Path preferred = CASE_DIR.resolve(preferredName);
        return Files.isRegularFile(preferred) ? preferred : CASE_DIR.resolve(alternateName);
    }

    @Test
    void loadsAndInitializesOfficialWecc240Case() throws Exception {
        assumeTrue(Files.isRegularFile(RAW), "Set -Dwecc240.case.dir to the official WECC 240 case directory");
        assumeTrue(Files.isRegularFile(DYR), "Set -Dwecc240.case.dir to the official WECC 240 case directory");

        IpssCorePlugin.init();
        SimuContext context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        BaseDStabNetwork<?, ?> network = context.getDStabilityNet();

        assertEquals(243, network.getNoBus());
        long renewableCount = network.getBusList().stream()
                .flatMap(bus -> bus.getContributeGenList().stream())
                .filter(gen -> gen instanceof com.interpss.dstab.DStabGen)
                .map(gen -> (com.interpss.dstab.DStabGen) gen)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .count();
        assertEquals(Long.getLong("wecc240.expected.renewables", 37L), renewableCount);
        network.setBypassDataCheck(true);

        DynamicSimuAlgorithm algorithm = context.getDynSimuAlgorithm();
        LoadflowAlgorithm loadflow = algorithm.getAclfAlgorithm();
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        loadflow.setTolerance(1.0e-10);
        boolean converged = loadflow.loadflow();
        assertTrue(converged, () -> "WECC 240 power flow must converge; final mismatch: "
                + network.maxMismatch(AclfMethodType.NR));

        Map<String, org.apache.commons.math3.complex.Complex> generatorPowerFlow = new LinkedHashMap<>();
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(gen -> gen instanceof com.interpss.dstab.DStabGen)
                .map(gen -> (com.interpss.dstab.DStabGen) gen)
                .forEach(gen -> generatorPowerFlow.put(bus.getId() + ":" + gen.getId(), gen.getGen())));

        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(gen -> gen instanceof com.interpss.dstab.DStabGen)
                .map(gen -> (com.interpss.dstab.DStabGen) gen)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .map(gen -> (Regca1Model) gen.getDynamicGenDevice())
                .forEach(model -> {
                    assertTrue(model.getElectricalController() != null,
                            () -> "Missing REECB1 for " + model.getExtendedDeviceId());
                    assertTrue(model.getElectricalController().getPlantController() != null,
                            () -> "Missing REPCA1 for " + model.getExtendedDeviceId());
                    assertEquals(1.11, model.getElectricalController().getData().imax(), 1.0e-9);
                    assertEquals(0, model.getElectricalController().getData().remoteBus());
                    assertEquals(0, model.getElectricalController().getPlantController().getData().remoteBus());
                    assertEquals("0", model.getElectricalController().getPlantController().getData().branchId());
                }));

        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(1.0 / 120.0);
        algorithm.setTotalSimuTimeSec(0.1);
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(new String[] { "Bus2401" });
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization(), "WECC 240 dynamic initialization must converge");
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(gen -> gen instanceof com.interpss.dstab.DStabGen)
                .map(gen -> (com.interpss.dstab.DStabGen) gen)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .forEach(gen -> {
                    Regca1Model model = (Regca1Model) gen.getDynamicGenDevice();
                    var voltageAtInit = bus.getVoltage();
                    var norton = (org.apache.commons.math3.complex.Complex) model.getOutputObject();
                    var impedance = gen.getPosGenZ().multiply(gen.getZMultiFactor());
                    var injected = norton.subtract(voltageAtInit.divide(impedance));
                    var actual = voltageAtInit.multiply(injected.conjugate());
                    var expected = generatorPowerFlow.get(bus.getId() + ":" + gen.getId());
                    assertEquals(expected.getReal(), actual.getReal(), 1.0e-8,
                            "REGCA1 active-power initialization mismatch at " + bus.getId());
                    assertEquals(expected.getImaginary(), actual.getImaginary(), 1.0e-8,
                            "REGCA1 reactive-power initialization mismatch at " + bus.getId());
                }));
        network.getBusList().forEach(bus -> bus.getContributeGenList().stream()
                .filter(gen -> gen instanceof com.interpss.dstab.DStabGen)
                .map(gen -> (com.interpss.dstab.DStabGen) gen)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .map(gen -> (Regca1Model) gen.getDynamicGenDevice())
                .forEach(model -> assertTrue(model.getElectricalController().getPlantController()
                        .isUsingZeroBranchFallback(),
                        () -> "Expected ANDES-compatible zero-branch mode for " + model.getExtendedDeviceId())));
        assertTrue(algorithm.performSimulation(), "WECC 240 dynamic simulation must complete");
        var voltage = monitor.getBusVoltTable().get("Bus2401");
        double minimum = voltage.values().stream().mapToDouble(record -> record.value).min().orElseThrow();
        double maximum = voltage.values().stream().mapToDouble(record -> record.value).max().orElseThrow();
        int finalIndex = voltage.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        double initial = voltage.get(0).value;
        double end = voltage.get(finalIndex).value;
        assertEquals(initial, minimum, 2.0e-5, "undisturbed voltage must not sag from its initial value");
        assertEquals(initial, maximum, 2.0e-5, "undisturbed voltage must not rise from its initial value");
        assertEquals(initial, end, 2.0e-5, "undisturbed case must remain at equilibrium");
    }
}
