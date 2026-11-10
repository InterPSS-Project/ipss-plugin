package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.StaticLoadModel;
import org.apache.commons.math3.complex.Complex;

/** Import integration checks reuse the explicit runtime; independent trajectory qualification is separate. */
public class Chvdc2DyrImportTest extends CorePluginTestSetup {
    @TempDir Path folder;
    private com.interpss.simu.SimuContext source(Path directory) throws Exception {
        Files.createDirectories(directory);
        var lines = new ArrayList<>(Files.readAllLines(Path.of("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw")));
        for (int i = 3; i < lines.size() && !lines.get(i).strip().startsWith("0 /"); i++) {
            var fields = lines.get(i).split(",", -1);
            if (Integer.parseInt(fields[0].strip()) == 8) { fields[3] = "4"; lines.set(i, String.join(",", fields)); }
        }
        var raw = directory.resolve("network.raw"); Files.write(raw, lines);
        var context = new PSSEMultiFileLoader().loadDStab(raw.toString());
        parse(context, directory, "1 'GENCLS' 1 99999 0 / 2 'GENCLS' 1 99999 0 / 3 'GENCLS' 1 99999 0 / 4 'GENCLS' 1 99999 0 /");
        return context;
    }
    private PSSEDStabDirectParser parse(com.interpss.simu.SimuContext context, Path directory, String text) throws Exception {
        var file = directory.resolve("models.dyr"); Files.writeString(file, text);
        var parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(file.toString()); return parser;
    }
    private static String record(String name, boolean legacy) {
        return "'" + name.replace("'", "''") + "' " + (legacy ? "'USRDCL' 'CHVDC2U1' 18 1 3 48 18 24 " : "'CHVDC2' ")
                + "0 0 1 " + String.join(" ", Arrays.stream(Chvdc2ParametersTest.constants()).mapToObj(Double::toString).toList()) + " /";
    }
    private static HvdcLine2TLCC<?> owner(com.interpss.simu.SimuContext context) {
        return (HvdcLine2TLCC<?>) context.getDStabilityNet().getSpecialBranchList().getFirst();
    }
    private static void solve(com.interpss.simu.SimuContext context) throws Exception {
        var n = context.getDStabilityNet(); n.setStaticLoadIncludedInYMatrix(true);
        n.setStaticLoadModel(StaticLoadModel.CONST_Z); n.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        var lf = context.getDynSimuAlgorithm().getAclfAlgorithm(); lf.setTolerance(1e-10); lf.setMaxIterations(80);
        lf.getLfAdjAlgo().setApplyAdjustAlgo(false); lf.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(false);
        assertTrue(lf.loadflow());
    }
    @Test void sourceAttachesBeforePowerFlowAndDuplicateOrMissingOwnersAreRejected() throws Exception {
        var context = source(folder); var network = context.getDStabilityNet();
        var parser = parse(context, folder, record(owner(context).getName(), true));
        assertTrue(parser.getLastImportReport().isStrictlyComplete()); assertFalse(network.isLfConverged());
        var model = Chvdc2DyrModel.models(network).getFirst();
        assertNull(model.getBinding()); assertArrayEquals(Chvdc2ParametersTest.constants(), model.getParameters().copySourceConstants());
        assertThrows(Exception.class, () -> parse(context, folder, record(owner(context).getName(), true)));
        assertEquals(1, Chvdc2DyrModel.models(network).size());
        assertThrows(Exception.class, () -> parse(context, folder, record("absent", true)));
        assertThrows(IllegalStateException.class, () -> model.initStates(model.getDStabBus()));
    }
    @Test void reprepareIsIdempotentAndFailureRetainsTheAcceptedPair() throws Exception {
        var context = source(folder); parse(context, folder, record(owner(context).getName(), false)); solve(context);
        var network = context.getDStabilityNet(); var model = Chvdc2DyrModel.models(network).getFirst();
        Chvdc2DyrModel.prepareNetwork(network, .0005); var first = model.getPorts();
        Chvdc2DyrModel.prepareNetwork(network, .0005); var replacement = model.getPorts();
        assertNotSame(first, replacement); assertTrue(replacement.rectifierPort().attachmentIntact());
        assertFalse(network.getBus("Bus7").getDynamicBusDeviceList().contains(first.rectifierPort()));
        assertEquals(2, network.getBus("Bus7").getDynamicBusDeviceList().size());
        owner(context).setStatus(false);
        assertThrows(IllegalArgumentException.class, () -> Chvdc2DyrModel.prepareNetwork(network, .0005));
        assertSame(replacement, model.getPorts());
    }
    @Test void preInitializationStateCopyRetainsSourceAndOwner() throws Exception {
        var context = source(folder); parse(context, folder, record(owner(context).getName(), true));
        var gson = new com.google.gson.Gson();
        var state = gson.toJson(new com.interpss.state.dstab.DStabNetworkState(context.getDStabilityNet()));
        var copy = com.interpss.state.dstab.DStabNetworkState.create(
                gson.fromJson(state, com.interpss.state.dstab.DStabNetworkState.class));
        var clone = Chvdc2DyrModel.models(copy).getFirst();
        assertSame(copy.getBus("Bus7"), clone.getDStabBus());
        assertEquals(owner(context).getName(), clone.getParameters().linkName());
        assertArrayEquals(Chvdc2ParametersTest.constants(), clone.getParameters().copySourceConstants());
        assertNull(clone.getBinding());
    }
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void importedFlatAndFaultTrajectoriesMatchExplicitRuntime(boolean staggered, boolean fault) throws Exception {
        var typed = run(folder.resolve("typed"), false, staggered, fault);
        var imported = run(folder.resolve("imported"), true, staggered, fault);
        assertEquals(typed.size(), imported.size());
        for (int i = 0; i < typed.size(); i++) assertArrayEquals(typed.get(i), imported.get(i), 1e-9, "Sample " + i);
    }
    private List<double[]> run(Path directory, boolean imported, boolean staggered, boolean fault) throws Exception {
        var context = source(directory); var network = context.getDStabilityNet(); var algorithm = context.getDynSimuAlgorithm();
        if (imported) parse(context, directory, record(owner(context).getName(), true));
        solve(context);
        Chvdc2NetworkPreparation.Binding typed = null;
        if (!imported) {
            typed = Chvdc2NetworkPreparation.prepare(network, new Chvdc2Parameters(owner(context).getName(),
                    Chvdc2Parameters.Dialect.CHVDC2U1, new int[]{0,0,1}, Chvdc2ParametersTest.constants()), .0005, 0);
            new Chvdc2TerminalPorts(typed).attach();
        }
        algorithm.setSimuStepSec(.0005); algorithm.setTotalSimuTimeSec(.08); algorithm.setSimuOutputHandler(new StateMonitor());
        if (staggered) algorithm.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm));
        if (fault) network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus7", network,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P, new Complex(0,.2), null, .03, .02), "fault");
        assertTrue(algorithm.initialization());
        var binding = imported ? Chvdc2DyrModel.models(network).getFirst().getBinding() : typed;
        var rows = new ArrayList<double[]>();
        do {
            double[] row = new double[28]; row[0] = network.getBus("Bus7").getVoltageMag(); row[1] = network.getBus("Bus9").getVoltageMag();
            binding.runtime().readStaggeredState(row, 2); binding.runtime().readBoundary(row, 18); rows.add(row);
            if (algorithm.getSimuTime() >= .08 - .00025) break;
            assertTrue(algorithm.solveDEqnStep(true));
        } while (true);
        return rows;
    }
}
