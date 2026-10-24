package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Power-flow, coverage and diagnostic dynamic execution gates for the privately supplied Texas7k case. */
public class Texas7kSimulationTest {
    private static final Path ROOT=Path.of("testData/private/texas7k");
    @BeforeAll static void initialize() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }
    @Test void powerFlowConverges() throws Exception {
        assumeTrue(Files.isRegularFile(ROOT.resolve("Texas7k_20210804.RAW")),"Private Texas7k inputs unavailable");
        var context=new PSSEMultiFileLoader().loadDStab(ROOT.resolve("Texas7k_20210804.RAW").toString());
        var network=context.getDStabilityNet();var lf=context.getDynSimuAlgorithm().getAclfAlgorithm();
        lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);lf.getDataCheckConfig().setTurnOffIslandBus(true);
        lf.setNonDivergent(true);lf.setMaxIterations(50);lf.setTolerance(.001);
        assertTrue(lf.loadflow(),()->"Texas7k power flow: "+network.maxMismatch(AclfMethodType.NR));
        assertEquals(6717,network.getNoBus());
        System.out.println("TEXAS7K_POWERFLOW "+network.maxMismatch(AclfMethodType.NR));
    }
    @Test void partialStackRunsDiagnosticNoEventWithoutDisablingGovernors() throws Exception {
        assumeTrue(Files.isRegularFile(ROOT.resolve("Texas7k_20210804.dyr")),"Private Texas7k inputs unavailable");
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(ROOT.resolve("Texas7k_20210804.RAW").toString(),ROOT.resolve("Texas7k_20210804.dyr").toString());
        assertEquals(120,loader.getLastResponseEntries().size());
        var report=loader.getLastDynamicImportReport();
        assertFalse(report.isStrictlyComplete(),"This input is a coverage diagnostic until its missing models are resolved");
        var gaps=new java.util.TreeMap<String,Long>();
        for(var entry:report.failures())gaps.merge(entry.canonicalModelName(),1L,Long::sum);
        assertEquals(java.util.Map.of("EXPIC1",86L,"IEEEST",31L,"WTTQA1",18L,"REAX3BU1",13L,"REAX4BU1",2L),gaps,
                "Review any coverage change before using the case as an accuracy benchmark");
        System.out.println("TEXAS7K_IMPORT "+report.failureSummary()+" gaps="+gaps);
        var algorithm=context.getDynSimuAlgorithm();var lf=algorithm.getAclfAlgorithm();
        lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);lf.getDataCheckConfig().setTurnOffIslandBus(true);
        lf.setNonDivergent(true);lf.setMaxIterations(50);lf.setTolerance(.001);
        assertTrue(lf.loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(1./240);algorithm.setTotalSimuTimeSec(.1);
        var monitor=new StateMonitor();monitor.addBusStdMonitor(new String[]{"Bus111180","Bus150502","Bus230272"});
        monitor.addGeneratorStdMonitor(new String[]{"Bus111180-mach1","Bus230272-mach1"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.initialization());assertTrue(algorithm.performSimulation());
        var disabled=new java.util.ArrayList<String>();
        for(var bus:context.getDStabilityNet().getBusList()) for(com.interpss.dstab.DStabGen generator:bus.getContributeGenList()) {
            var machine=generator.getMach();
            if(machine!=null&&machine.getGovernor()!=null&&!machine.getGovernor().isActive())disabled.add(machine.getId());
        }
        assertTrue(disabled.isEmpty(),"Governor initialization failures: "+disabled);
        for(var series:monitor.getBusVoltTable().values()) {
            assertTrue(series.size()>1);
            assertTrue(series.values().stream().allMatch(value->Double.isFinite(value.value)));
        }
        System.out.println("TEXAS7K_DISABLED_GOVERNORS "+disabled);
    }
}
