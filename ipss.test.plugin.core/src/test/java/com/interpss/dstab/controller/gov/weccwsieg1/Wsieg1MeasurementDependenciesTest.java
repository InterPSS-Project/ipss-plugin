package com.interpss.dstab.controller.gov.weccwsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Audited machine-speed inputs avoid passive-bus work while observers and requested outputs retain it. */
public class Wsieg1MeasurementDependenciesTest {
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}
    @Test void auditedGovernorAllowsActualPreparedExciterExecution(@TempDir Path folder)throws Exception {
        String record=Files.readAllLines(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33_genrou_esst1a_pss2a.dyr"))
                .stream().filter(line->line.contains("'ESST1A'")).findFirst().orElseThrow();
        Path dynamic=folder.resolve("dynamic.dyr");Files.writeString(dynamic,Files.readString(Wsieg1SharedSmibTest.CASE.resolve("wsieg1.dyr"))+"\n"+record+"\n");
        var actual=Wsieg1SharedSmibTest.simulate(Wsieg1SharedSmibTest.CASE.resolve("network.raw"),dynamic,false,"fault",.0005,.5,List.of());
        var report=((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)actual.algorithm.getSolver()).getControllerPreparationReport();
        assertEquals(1,report.st1Controllers(),"The co-located exciter must actually use its audited numerical preparation");
        assertEquals(1,report.compiledControllers());
    }
    @ParameterizedTest @CsvSource({"standard,false","standard,true","dual,false","dual,true"})
    void requiredMeasurementsPreserveEveryStateAndBoundaryAndConservativeFallback(String profile,boolean staggered,@TempDir Path folder)throws Exception {
        boolean dual=profile.equals("dual");int passive=dual?4:3,slack=dual?3:2,kv=dual?230:100;
        String raw=Files.readString(Wsieg1SharedSmibTest.CASE.resolve(dual?"dual-network.raw":"network.raw"));
        raw=raw.replace("0 / END OF BUS DATA",passive+",'PASSIVE',"+kv+",1,1,1,1,1.0,0,1.1,.9,1.1,.9\n0 / END OF BUS DATA");
        raw=raw.replace("0 / END OF LOAD DATA",passive+",'1',1,1,1,10,5,0,0,0,0,1,1,0\n0 / END OF LOAD DATA");
        raw=raw.replace("0 / END OF BRANCH DATA",slack+","+passive+",'1',.01,.1,0,0,0,0,0,0,0,0,1,1,0,1,1.0\n0 / END OF BRANCH DATA");
        Path network=folder.resolve("network.raw");Files.writeString(network,raw);Path dynamic=Wsieg1SharedSmibTest.CASE.resolve(dual?"wsieg1-dual.dyr":"wsieg1.dyr");
        var all=Wsieg1SharedSmibTest.simulate(network,dynamic,staggered,"fault",.0005,.5,List.of(),
                a->((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)a.getSolver()).setBusFrequencyMode(DefaultDynamicSimulationSolver.BusFrequencyMode.ALL));
        var required=Wsieg1SharedSmibTest.simulate(network,dynamic,staggered,"fault",.0005,.5,List.of());
        var report=((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)required.algorithm.getSolver()).getBusFrequencyReport();
        assertEquals(passive,report.activeBuses());assertEquals(passive-1,report.measuredBuses(),report.reason());
        assertNull(required.algorithm.getNetwork().getDStabBus("Bus"+passive).getBusFreqMeasureBlock());
        assertEquals(passive,((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)all.algorithm.getSolver()).getBusFrequencyReport().measuredBuses());
        assertEquals(all.frames.size(),required.frames.size());
        for(int i=0;i<all.frames.size();i++) {
            assertEquals(all.frames.get(i).time(),required.frames.get(i).time(),0.);assertEquals(all.frames.get(i).kind(),required.frames.get(i).kind());
            assertEquals(all.frames.get(i).values(),required.frames.get(i).values(),"Complete physical/state/history record "+i);
        }
        var observed=Wsieg1SharedSmibTest.simulate(network,dynamic,staggered,"flat",.0005,.01,List.of(),
                a->a.getNetwork().getMachine("Bus1-mach1").getGovernor().eAdapters().add(new org.eclipse.emf.common.notify.impl.AdapterImpl()));
        assertEquals(passive,((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)observed.algorithm.getSolver()).getBusFrequencyReport().measuredBuses(),"Controller observer retains all buses");
        var requested=Wsieg1SharedSmibTest.simulate(network,dynamic,staggered,"flat",.0005,.01,List.of(),a->{
            var monitor=new com.interpss.dstab.cache.StateMonitor();monitor.addBusStdMonitor(new String[]{"Bus"+passive});a.setSimuOutputHandler(monitor);
        });
        assertEquals(passive,((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)requested.algorithm.getSolver()).getBusFrequencyReport().measuredBuses(),"Requested passive-bus frequency is measured");
        assertNotNull(requested.algorithm.getNetwork().getDStabBus("Bus"+passive).getBusFreqMeasureBlock());
    }
}
