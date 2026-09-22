package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt3g2Model;
import org.interpss.dstab.mach.Wt3p1Data;
import org.interpss.dstab.mach.Wt3p1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.cache.StateMonitor;

/** Exact schema, attachment, initialization, and named-state checks for WT3P1. */
public class PsseWt3p1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void initializesPublishedThreeCoordinatesWithoutPitchTransient() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt3p1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt3g2Model generator = assertInstanceOf(Wt3g2Model.class,
                gen.getDynamicGenDevice());
        Wt3p1Model model = generator.getDriveTrain().getPitchController();
        assertEquals(new Wt3p1Data(.27, 135, 23, 2.7, 27, 0, 25, 9, .92),
                model.getData());
        assertEquals(9, DynamicModelCatalog.find("WT3P1").orElseThrow().parameterCount());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        assertEquals(3, model.getNamedStates().size());
        assertTrue(generator.getNamedStates().containsKey("Blade output lag"));
        assertEquals(0.0, model.getPitch(), 0.0);
        assertEquals(1.134, model.getPitchControlState(), 2.0e-6);
        assertEquals(0.0, model.getPitchCompensationState(), 0.0);
        Wt3p1Data data = model.getData();
        assertEquals(9.0, Wt3p1Model.publishedPitchRate(data, 0, 25, 0,
                .2, 0, 1.0, .0005), 0.0);
        assertEquals(-9.0, Wt3p1Model.publishedPitchRate(data, 10, 0, 0,
                -.2, 0, 0, .0005), 0.0);
        assertEquals(0.0, Wt3p1Model.publishedPitchRate(data, 0, 0, 0,
                -.2, 0, 0, .0005), 0.0);
    }

    @Test
    void rejectsAnExtraConstant(@TempDir Path tempDir) throws Exception {
        Path source = CASE.resolve("SMIB_v33_wt3p1.dyr");
        Path dyr = tempDir.resolve("wt3p1-extra.dyr");
        Files.writeString(dyr, Files.readString(source).replace(
                "0.0 25.0 9.0 0.92 /",
                "0.0 25.0 9.0 0.92 99.0 /"));
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(), dyr.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet()
                .getBus("Bus1").getContributeGen("1");
        Wt3g2Model generator = assertInstanceOf(Wt3g2Model.class,
                gen.getDynamicGenDevice());
        assertEquals(null, generator.getDriveTrain().getPitchController());
    }
}
