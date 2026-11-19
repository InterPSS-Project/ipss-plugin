package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Power-flow, coverage and diagnostic dynamic execution gates for the privately supplied Texas7k case. */
public class Texas7kSimulationTest {
    private static final Path ROOT=Path.of("testData/private/texas7k");
    @BeforeAll static void initialize() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }
    @Test void completeCaseHoldsAllBusAndControllerStatesForTenSeconds(@TempDir Path directory)throws Exception {
        assertCompleteCaseEquilibrium(false,directory);
    }
    @Test void optimizedCaseHoldsAllBusAndControllerStatesForTenSeconds(@TempDir Path directory)throws Exception {
        assertCompleteCaseEquilibrium(true,directory);
    }
    private void assertCompleteCaseEquilibrium(boolean optimized,Path directory)throws Exception {
        assumeTrue(Files.isRegularFile(ROOT.resolve("Texas7k_20210804.dyr")),"Private Texas7k inputs unavailable");
        Path output=directory.resolve("flat");
        var result=org.interpss.core.dstab.reference.Texas7kFlatStartAudit.run(ROOT,output,10,1e-4,optimized);
        assertEquals(true,result.get("import_complete"));assertEquals(120,result.get("bsg_entries"));
        assertEquals(java.util.List.of(),result.get("disabled_controllers"));
        if(optimized) {
            var counts=(java.util.Map<?,?>)result.get("resolved_exciter_substeps");
            assertEquals(137,counts.size(),"Every additional active exciter must be bound");
            assertEquals(2,counts.get("Bus200169-mach1"),"The stiff EXAC2 mode requires local resolution");
        }
        assertTrue((double)result.get("max_voltage_drift_pu")<1e-6);
        assertTrue((double)result.get("max_speed_drift_pu")<1e-8);
        assertTrue((double)result.get("max_angle_drift_deg")<.002);
        var before=com.google.gson.JsonParser.parseString(Files.readString(output.resolve("initial-states.json"))).getAsJsonArray();
        var after=com.google.gson.JsonParser.parseString(Files.readString(output.resolve("final-states.json"))).getAsJsonArray();
        assertEquals(before.size(),after.size());int coordinates=0;
        for(int i=0;i<before.size();i++) {
            var a=before.get(i).getAsJsonObject();var b=after.get(i).getAsJsonObject();
            assertEquals(a.get("bus"),b.get("bus"));assertEquals(a.get("id"),b.get("id"));
            for(var entry:a.entrySet())if(entry.getValue().isJsonObject()) {
                var initial=entry.getValue().getAsJsonObject();
                String key=initial.has("integration_states")?"integration_states":initial.has("class")&&initial.has("states")?"states":null;
                if(key==null)continue;
                var x=initial.getAsJsonObject(key);var y=b.getAsJsonObject(entry.getKey()).getAsJsonObject(key);
                assertEquals(x.keySet(),y.keySet());
                for(String name:x.keySet()) {
                    double old=x.get(name).getAsDouble(),value=y.get(name).getAsDouble();
                    assertTrue(Double.isFinite(value));
                    assertEquals(old,value,1e-8,a.get("bus")+"/"+entry.getKey()+"/"+name);coordinates++;
                }
            }
        }
        assertTrue(coordinates>1000,"Controller-state coverage unexpectedly missing");
        System.out.println("TEXAS7K_FLAT_STATE_COORDINATES "+coordinates);
    }
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
    @Test void completeCaseRunsAFiveCycleThreePhaseBusFault()throws Exception {
        assumeTrue(Files.isRegularFile(ROOT.resolve("Texas7k_20210804.dyr")),"Private Texas7k inputs unavailable");
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(ROOT.resolve("Texas7k_20210804.RAW").toString(),ROOT.resolve("Texas7k_20210804.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();var lf=algorithm.getAclfAlgorithm();
        lf.getDataCheckConfig().setAutoTurnLine2Xfr(true);lf.getDataCheckConfig().setTurnOffIslandBus(true);
        lf.setNonDivergent(true);lf.setMaxIterations(50);lf.setTolerance(.001);assertTrue(lf.loadflow());
        net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I);
        net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadZPartInYMatrix(true);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(1./240);algorithm.setTotalSimuTimeSec(.5);
        var monitor=new StateMonitor();monitor.addBusStdMonitor(new String[]{"Bus111180","Bus110794","Bus190123"});
        algorithm.setSimuOutputHandler(monitor);
        net.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus111180",net,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,org.apache.commons.math3.complex.Complex.ZERO,
                org.apache.commons.math3.complex.Complex.ZERO,.1,5./60),"FiveCycleFault@Bus111180");
        assertTrue(algorithm.initialization());assertTrue(algorithm.performSimulation());
        for(var series:monitor.getBusVoltTable().values())assertTrue(series.values().stream().allMatch(v->Double.isFinite(v.value)));
        assertTrue(monitor.getBusVoltTable().get("Bus111180").values().stream().mapToDouble(v->v.value).min().orElseThrow()<.001);
        for(var bus:net.getBusList())if(bus.isActive()) {
            assertTrue(Double.isFinite(bus.getVoltageMag()),bus.getId());
            for(com.interpss.dstab.DStabGen gen:bus.getContributeGenList())if(gen.isActive()) {
                var machine=gen.getMach();
                if(machine!=null)assertTrue(Double.isFinite(machine.getAngle())&&Double.isFinite(machine.getSpeed())&&Double.isFinite(machine.getEfd()),machine.getId());
                if(gen.getDynamicGenDevice() instanceof com.interpss.dstab.controller.cml.ICMLStateProvider provider)
                    for(var state:provider.getNamedStates().entrySet())assertTrue(Double.isFinite(state.getValue()),bus.getId()+"/"+state.getKey());
            }
        }
        System.out.println("TEXAS7K_FIVE_CYCLE_FAULT completed at "+algorithm.getSimuTime());
    }
    @Test void completeStackRunsNoEventWithoutDisablingGovernors() throws Exception {
        assumeTrue(Files.isRegularFile(ROOT.resolve("Texas7k_20210804.dyr")),"Private Texas7k inputs unavailable");
        var loader=new PSSEMultiFileLoader();
        var context=loader.loadDStab(ROOT.resolve("Texas7k_20210804.RAW").toString(),ROOT.resolve("Texas7k_20210804.dyr").toString());
        assertEquals(120,loader.getLastResponseEntries().size());
        var report=loader.getLastDynamicImportReport();
        assertTrue(report.isStrictlyComplete(),report.failureSummary());
        var gaps=new java.util.TreeMap<String,Long>();
        for(var entry:report.failures())gaps.merge(entry.canonicalModelName(),1L,Long::sum);
        assertTrue(gaps.isEmpty(),gaps.toString());
        assertEquals(86,report.entries().stream().filter(entry->entry.canonicalModelName().equals("EXPIC1")
                &&entry.status()==org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.ATTACHED).count());
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
