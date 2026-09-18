package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Opt-in positive-Q REGCA1 recovery-boundary diagnostic. */
public class RegcaPositiveQRecoveryDiagnosticTest {
    private static final double STEP = .0005;
    private static final Path CASE = Path.of(System.getProperty(
            "regca.positiveq.diagnostic.case",
            Path.of("..", "target", "private-regca-positiveq-smib").toString()))
            .normalize();
    private static final String DYR = System.getProperty(
            "regca.positiveq.diagnostic.dyr", "case.dyr");

    @Test
    void positiveQFaultKeepsTheZeroUpperRecoveryRateBoundaryActive() throws Exception {
        assumeTrue(Boolean.getBoolean("regca.positiveq.diagnostic.enabled"));
        assumeTrue(Files.isRegularFile(CASE.resolve("case.raw")));
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("case.raw").toString(), CASE.resolve(DYR).toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.5);
        algorithm.setOutPutPerSteps(1);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P, Complex.ZERO, null,
                .05, .05), "PositiveQFault");
        assertTrue(algorithm.initialization());

        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
        Reeca1Model controller = converter.getReeca1Controller();
        double initialIq = converter.getIqRegulatorState();
        double minimumIq = initialIq;
        double maximumIq = initialIq;
        double minimumVoltage = network.getBus("Bus1").getVoltageMag();
        double maximumVoltage = minimumVoltage;
        double minimumPiv = controller.getVoltageControlIntegral();
        double maximumPiv = minimumPiv;
        while (algorithm.getSimuTime() < .5 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            minimumIq = Math.min(minimumIq, converter.getIqRegulatorState());
            maximumIq = Math.max(maximumIq, converter.getIqRegulatorState());
            minimumVoltage = Math.min(minimumVoltage, network.getBus("Bus1").getVoltageMag());
            maximumVoltage = Math.max(maximumVoltage, network.getBus("Bus1").getVoltageMag());
            minimumPiv = Math.min(minimumPiv, controller.getVoltageControlIntegral());
            maximumPiv = Math.max(maximumPiv, controller.getVoltageControlIntegral());
        }
        System.out.printf(Locale.ROOT,
                "Positive-Q recovery: V=[%.12g, %.12g] REGCA_IQ=[%.12g, %.12g] "
                        + "initial=%.12g REECA_PIV=[%.12g, %.12g] finalV=%.12g%n",
                minimumVoltage, maximumVoltage, minimumIq, maximumIq, initialIq,
                minimumPiv, maximumPiv, network.getBus("Bus1").getVoltageMag());
    }
}
