package org.interpss.plugin.aclf.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;
import com.interpss.core.algo.config.RemoteQControlMode;

/** Public configuration contracts independent of any large-case recipe. */
class AclfConfigurationPrecedenceTest {
    @TempDir Path directory;

    private LoadflowAlgorithm imported() {
        var net = CoreObjectFactory.createAclfNetwork();
        net.setBaseKva(200000.);
        var builder = PsseLoadflowSolutionSettings.builder(36);
        builder.addLine("GENERAL, THRSHZ=0.00029, PQBRAK=0.65");
        builder.addLine("NEWTON, ITMXN=73, TOLN=0.5, DVLIM=0.4");
        builder.addLine("ADJUST, ADJTHR=0.007, TAPLIM=0.05");
        builder.addLine("SOLVER, FNSL, ACTAPS=0, AREAIN=0, PHSHFT=0, DCTAPS=0, SWSHNT=0, NONDIV=1, VARLIM=2");
        net.getExtraInfo().put(LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY, builder.build());
        return LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
    }

    @Test void emptyJsonPreservesCoreFactoryDefaults() {
        var a = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(CoreObjectFactory.createAclfNetwork());
        int iterations = a.getMaxIterations();
        var method = a.getLfMethod();
        var limit = a.getLfAdjAlgo().getLimitCtrlConfig();
        var voltage = a.getLfAdjAlgo().getVoltAdjConfig();
        var limitType = limit.getAdjustAppType();
        var voltageType = voltage.getAdjustAppType();
        double tapFactor = a.getLfAdjAlgo().getAccFactorConfig().getXfrTapControlAccFactor();
        boolean backoff = limit.isLimitBackoffCheck();
        AclfRunConfigRec.fromJson("{}").applyTo(a);
        assertEquals(iterations, a.getMaxIterations());
        assertEquals(method, a.getLfMethod());
        assertEquals(limitType, limit.getAdjustAppType());
        assertEquals(voltageType, voltage.getAdjustAppType());
        assertEquals(backoff, limit.isLimitBackoffCheck());
        assertEquals(tapFactor, a.getLfAdjAlgo().getAccFactorConfig().getXfrTapControlAccFactor());
        assertEquals(.1, voltage.getDQ_dVThreshold());
    }

    @Test void emptyJsonPreservesImportedNumericalSettings() {
        var a = imported();
        AclfRunConfigRec.fromJson("{}").applyTo(a);
        assertEquals(73, a.getMaxIterations());
        assertEquals(.0025, a.getTolerance());
        assertEquals(.05, a.getTapChangeLimit());
        assertEquals(.65, a.getAclfNet().getBusLoadLowVoltConfig().getVConstPMin());
        assertEquals(.007, a.getLfAdjAlgo().getVoltageAdjustmentThreshold());
        assertTrue(a.isVariableUpdateLimit());
        assertEquals(.4, a.getDeltaVMagLimit());
        assertEquals(Double.MAX_VALUE, a.getDeltaVAngLimit());
    }

    @Test void explicitValuesOverrideOnlyTheirOwnImportedSettings() {
        var a = imported();
        AclfRunConfigRec.fromJson("""
                {"maxIterations":31,"tolerance":0.2,"tolUnitType":"mVA",
                 "variableUpdateLimit":false,"tapChangeLimit":0.025}
                """).applyTo(a);
        assertEquals(31, a.getMaxIterations());
        assertEquals(.001, a.getTolerance());
        assertEquals(.025, a.getTapChangeLimit());
        assertFalse(a.isVariableUpdateLimit());
        assertEquals(.4, a.getDeltaVMagLimit());
    }

    @Test void replayAppliesSupportedActivityFlagsBeforeUserOverrides() {
        var a = imported();
        AclfRunConfigRec.fromJson("""
                {"savedSolutionReplay":true,"xfrTapControl":true,"nonDivergent":false}
                """).applyTo(a);
        var voltage = a.getLfAdjAlgo().getVoltAdjConfig();
        assertTrue(voltage.isXfrTapControl());
        assertFalse(voltage.isSwitchedShuntAdjust());
        assertFalse(voltage.isHvdcTapControl());
        assertFalse(a.getLfAdjAlgo().getPowerAdjConfig().isPsXfrPControl());
        assertFalse(a.getNetAdjAlgo().isAreaInterchangeControlEnabled());
        assertFalse(a.isNonDivergent());
        var report = (PsseLoadflowSolutionSettings.ApplicationReport) a.getAclfNet().getExtraInfo()
                .get(PsseLoadflowSolutionSettings.APPLICATION_REPORT_EXTRA_INFO_KEY);
        assertTrue(report.mappings().stream().anyMatch(m -> m.field().equals("SOLVER.VARLIM")
                && m.status() == PsseLoadflowSolutionSettings.MappingStatus.UNSUPPORTED));
    }

    @Test void fileSaveAndLoadPreserveOmissions() throws Exception {
        var config = AclfRunConfigRec.fromJson("{\"maxIterations\":37}");
        Path file = directory.resolve("test.aclf.json");
        config.saveAclfRunConfig(file.toString());
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertFalse(json.has("tolerance"));
        assertFalse(json.has("xfrTapControl"));
        var a = imported();
        AclfRunConfigRec.loadAclfRunConfig(file.toString()).applyTo(a);
        assertEquals(37, a.getMaxIterations());
        assertEquals(.0025, a.getTolerance());
        assertEquals(.4, a.getDeltaVMagLimit());
    }

    @Test void stringRoundTripPreservesOmissions() {
        var c = AclfRunConfigRec.fromJson("{\"maxIterations\":37}");
        var copy = AclfRunConfigRec.fromJson(c.toString());
        assertFalse(copy.explicitlyConfigures("tolerance"));
        assertTrue(copy.explicitlyConfigures("maxIterations"));
    }

    @Test void overlaysKeepEarlierExplicitAuthority() {
        var base = AclfRunConfigRec.fromJson("{\"tolerance\":0.0007,\"xfrTapControl\":false}");
        var combined = AclfRunConfigRec.overlayJson(base, "{\"maxIterations\":37}");
        var a = imported();
        combined.applyTo(a);
        assertEquals(.0007, a.getTolerance());
        assertEquals(37, a.getMaxIterations());
        assertFalse(a.getLfAdjAlgo().getVoltAdjConfig().isXfrTapControl());
        assertEquals(.4, a.getDeltaVMagLimit());
    }

    @Test void nullValuesDoNotInventOverrides() {
        var a = imported();
        AclfRunConfigRec.fromJson("{\"maxIterations\":null,\"tapChangeLimit\":null}").applyTo(a);
        assertEquals(73, a.getMaxIterations());
        assertEquals(.05, a.getTapChangeLimit());
    }

    @Test void coordinateChoiceCanBeAppliedInBothDirections() {
        var a = imported();
        AclfRunConfigRec.fromJson("{\"polarCoordinate\":false}").applyTo(a);
        assertFalse(a.getAclfNet().isPolarCoordinate());
        AclfRunConfigRec.fromJson("{}").applyTo(a);
        assertFalse(a.getAclfNet().isPolarCoordinate());
        AclfRunConfigRec.fromJson("{\"polarCoordinate\":true}").applyTo(a);
        assertTrue(a.getAclfNet().isPolarCoordinate());
    }

    @Test void sharedEngineControlsApplyWithoutRunnerSpecificCode() {
        var a = imported();
        AclfRunConfigRec.fromJson("""
                {"remoteQControlMode":"INNER_PQV","localContinuousSvcAsPv":true,
                 "maxRemoteQAdjustmentsPerIteration":19,"maxSvcAdjustmentsPerIteration":7,
                 "hvdcOuterAdjustmentFactor":0.3,"initBusVoltage":false,
                 "nrStepSizeOptimization":true}
                """).applyTo(a);
        assertEquals(RemoteQControlMode.INNER_PQV, a.getLfAdjAlgo().getRemoteQControlMode());

        assertEquals(19, a.getLfAdjAlgo().getMaxRemoteQAdjustmentsPerIteration());
        assertEquals(7, a.getLfAdjAlgo().getMaxSvcAdjustmentsPerIteration());
        assertEquals(.3, a.getLfCalculator().getHvdcOuterAdjustmentFactor());
        assertTrue(a.getNrMethodConfig().isStepSizeOptimization());
    }

    @Test void coordinatedSolverUsesTheEffectiveCategorySwitches() {
        var a = imported();
        AclfRunConfigRec.fromJson("""
                {"lfMethod":"NR","savedSolutionReplay":true,
                 "coordinatedControlZbrSolver":true,"remoteQControlMode":"INNER_PQV",
                 "remoteQBusControl":false,"svcFactsAdjust":false,
                 "coordinatedControlEnableInnerTaps":true,"xfrTapControl":false}
                """).applyTo(a);
        var solver = assertInstanceOf(com.interpss.core.algo.impl.solver.CoordinatedControlZbrNrSolver.class,
                a.getLfCalculator().getNrSolver());
        assertTrue(solver.isAugmentedZbrModelEnabled());
        assertFalse(solver.isIncludeRemoteQControls());
        assertFalse(solver.isIncludeRemoteContinuousSvcs());
        assertFalse(solver.isFullNewtonTapControlEnabled());
        assertEquals(.05, solver.getTapChangeLimit());
    }

    @Test void nativePredictorsRetainElectricalZbrOwnershipUntilCoreHandoff() {
        for (String method : new String[] {"PQ", "PQ_NR"}) {
            var a = imported();
            AclfRunConfigRec.fromJson("{\"lfMethod\":\"" + method
                    + "\",\"coordinatedControlZbrSolver\":true,\"coordinatedPqControlEnabled\":true,"
                    + "\"coupledLccPqControlEnabled\":true}").applyTo(a);
            assertInstanceOf(com.interpss.core.algo.impl.solver.ZbrNrSolver.class,
                    a.getLfCalculator().getNrSolver());
            assertFalse(a.getLfCalculator().getNrSolver()
                    instanceof com.interpss.core.algo.impl.solver.CoordinatedControlZbrNrSolver);
            assertTrue(a.isCoupledLccPqControlEnabled());
            assertEquals(method, a.getLfMethod().name());
        }
    }

    @Test void unsupportedPredictorOwnershipIsReportedRatherThanSilentlyChanged() {
        var config = AclfRunConfigRec.fromJson("""
                {"lfMethod":"PQ_NR","coordinatedControlZbrSolver":true,
                 "coordinatedPqControlEnabled":false}
                """);
        assertThrows(IllegalArgumentException.class, () -> config.applyTo(imported()));
    }

    @Test void existingBeanCallersRetainTheirExplicitValues() {
        var a = imported();
        var c = new AclfRunConfigRec();
        c.maxIterations = 29;
        c.tolerance = .0008;
        c.configAclfRun(a, true, false, false);
        assertEquals(29, a.getMaxIterations());
        assertEquals(.0008, a.getTolerance());
        assertFalse(a.getLfAdjAlgo().isApplyAdjustAlgo());
    }
}
