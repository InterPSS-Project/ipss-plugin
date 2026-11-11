package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact record, attachment, initialization, and state checks for WT4E1. */
public class PsseWt4e1ModelTest {
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    @Test void exactPublishedRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init(); var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt4e1.dyr").toString());
        var network=context.getDStabilityNet(); var algorithm=context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow()); algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005); algorithm.setSimuOutputHandler(new StateMonitor()); assertTrue(algorithm.initialization());
        DStabGen gen=(DStabGen)network.getBus("Bus1").getContributeGen("1");
        Wt4g1Model host=assertInstanceOf(Wt4g1Model.class,gen.getDynamicGenDevice());
        Wt4e1Model model=assertInstanceOf(Wt4e1Model.class,host.getElectricalController());
        assertEquals(27,DynamicModelCatalog.find("WT4E1").orElseThrow().parameterCount());
        assertEquals(10,model.getNamedStates().size());
        assertEquals(0,model.getData().remoteBus()); assertEquals(1,model.getData().varFlag());
        assertEquals(.5,model.getNamedStates().get("Power filter"),2e-6);
        assertEquals(0.0,model.getNamedStates().get("Active-power regulator integrator"),1e-12);
    }

    @Test void remoteWindVarSensorLeavesInnerLoopOnTerminal(@TempDir Path directory) throws Exception {
        var model = loadSensorFixture(directory, 2);
        var host = model.getElectricalController();
        var terminal = model.getDStabBus();
        var remote = (com.interpss.dstab.BaseDStabBus<?, ?>) terminal.getNetwork().getBus("Bus2");
        var initial = host.getNamedStates();
        double vTerminal = terminal.getVoltageMag();
        assertEquals(.98, initial.get("Voltage sensor"), 1e-12);
        assertEquals(vTerminal, initial.get("MVAR/Vref integrator"), 1e-12);

        double dt = .001;
        double remoteVoltage = .85;
        remote.setVoltage(remoteVoltage, 0.0);
        host.step(dt, 0);
        var predictor = host.getNamedStates();
        assertEquals(.98 + dt * (remoteVoltage - .98) / .02,
                predictor.get("Voltage sensor"), 1e-12);
        assertEquals(initial.get("Voltage-error/internal-voltage integrator"),
                predictor.get("Voltage-error/internal-voltage integrator"), 1e-12);
        host.step(dt, 1);
        assertTrue(host.getNamedStates().get("Voltage regulator integrator")
                > initial.get("Voltage regulator integrator"));
    }

    @Test void missingRemoteBusUsesTerminalSensor(@TempDir Path directory) throws Exception {
        var model = loadSensorFixture(directory, 999);
        assertEquals(999, model.getElectricalController().getData().remoteBus());
        assertEquals(model.getDStabBus().getVoltageMag(),
                model.getElectricalController().getNamedStates().get("Voltage sensor"), 1e-12);
    }

    private static Wt4g1Model loadSensorFixture(Path directory, int remote) throws Exception {
        IpssCorePlugin.init();
        Path dyr = directory.resolve("wt4e1.dyr");
        Files.writeString(dyr, Files.readString(CASE.resolve("SMIB_v33_wt4e1.dyr"))
                .replace("'WT4E1' '1' 0 ", "'WT4E1' '1' " + remote + " "));
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(), dyr.toString());
        var terminal = context.getDStabilityNet().getDStabBus("Bus1");
        context.getDStabilityNet().getDStabBus("Bus2").setVoltage(.98, 0.0);
        var model = assertInstanceOf(Wt4g1Model.class,
                ((DStabGen) terminal.getContributeGen("1")).getDynamicGenDevice());
        assertTrue(model.initStates(terminal));
        return model;
    }
}
