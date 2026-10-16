package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.test.TestConfig;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Exports all IEEE39 bus and generator states; acceptance is in the comparison report. */
@Tag("extended")
public class Ieee39PsseBenchmarkTest {
    private static final TestConfig CONFIG = TestConfig.load(
            "/test-config/ipss.test.plugin.core.json", "Ieee39PsseBenchmarkTest");
    private static final Path CASE = Path.of("testData/adpter/psse/v30/IEEE39Bus");
    private static final double NETWORK_TOLERANCE = 1.e-6;

    static Stream<Arguments> benchmarkCases() {
        return Stream.of(CONFIG.string("ieee39.methods", "modified-euler,psse-staggered-euler").split(","))
                .flatMap(method -> Stream.of(CONFIG.string("ieee39.steps", "0.00025").split(","))
                        .flatMap(step -> Stream.of("flat", "fault")
                                .map(scenario -> Arguments.of(scenario, Double.parseDouble(step), method))));
    }

    @ParameterizedTest
    @MethodSource("benchmarkCases")
    void exportBenchmark(String scenario, double step, String method) throws Exception {
        // Keep events and the endpoint on the fixed integration grid in both simulators.
        double faultStart = Math.round(1.0 / step) * step;
        double faultClear = Math.round(1.05 / step) * step;
        double stop = Math.round(5.0 / step) * step;
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("IEEE39bus_v30.raw").toString(), CASE.resolve("IEEE39bus.dyr").toString());
        var net = context.getDStabilityNet();
        var algo = context.getDynSimuAlgorithm();
        net.setStaticLoadModel(StaticLoadModel.CONST_Z);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algo.getAclfAlgorithm().setTolerance(1e-10);
        algo.getAclfAlgorithm().setMaxIterations(50);
        assertTrue(algo.getAclfAlgorithm().loadflow(), "IEEE39 power flow");
        String directory = step==.001 ? "target/ieee39-psse-benchmark"
                : step==.0005 ? "target/ieee39-psse-benchmark-fine"
                : String.format(Locale.ROOT,"target/ieee39-psse-benchmark-step-%.6f",step);
        assertTrue(method.equals("modified-euler") || method.equals("psse-staggered-euler"),
                "Unknown integration method: " + method);
        boolean staggered = method.equals("psse-staggered-euler");
        directory += staggered ? "-staggered" : "-modified-euler";
        directory += CONFIG.string("ieee39.outputSuffix", "");
        Path out = Path.of(directory, scenario);
        Files.createDirectories(out);
        StringBuilder pf = new StringBuilder("bus,v_pu,angle_deg\n");
        for (int i=1;i<=39;i++) {
            var bus=net.getBus("Bus"+i);
            pf.append(String.format(Locale.ROOT,"%d,%.12g,%.12g%n",i,bus.getVoltageMag(),Math.toDegrees(bus.getVoltageAng())));
        }
        Files.writeString(out.resolve("interpss-powerflow.csv"),pf);
        final java.io.Writer[] output = new java.io.Writer[1];
        algo.setSolver(staggered ? new PsseStaggeredEulerSolver(algo) {
            { setNetworkSolutionTolerance(NETWORK_TOLERANCE); setMaxIterationTimes(100); }

            @Override public boolean networkSolutionStep() throws DStabSimuException {
                if (!super.networkSolutionStep())
                    throw new DStabSimuException("IEEE39 network did not converge to 1e-6 at "
                            + algo.getSimuTime());
                return true;
            }

            @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)
                    throws DStabSimuException {
                super.rebaseDynamicModelInputs(method);
                // Preserve both sides of each algebraic discontinuity, including
                // zero-time post-event values before the first integration step.
                try { write(output[0],algo.getSimuTime(),record(net)); }
                catch (Exception e) { throw new DStabSimuException(e.toString()); }
            }
        } : new DStabSolverImpl(algo) {
            { converge_tol = NETWORK_TOLERANCE; setMaxIterationTimes(100); }

            @Override public boolean networkSolutionStep() throws DStabSimuException {
                if (!super.networkSolutionStep())
                    throw new DStabSimuException("IEEE39 network did not converge to 1e-6 at "
                            + algo.getSimuTime());
                return true;
            }

            @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)
                    throws DStabSimuException {
                super.rebaseDynamicModelInputs(method);
                try { write(output[0],algo.getSimuTime(),record(net)); }
                catch (Exception e) { throw new DStabSimuException(e.toString()); }
            }
        });
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(step);
        algo.setTotalSimuTimeSec(stop);
        algo.setSimuOutputHandler(new StateMonitor());
        algo.setOutPutPerSteps(1);
        if (scenario.equals("fault")) {
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus17",net,
                    SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,faultStart,faultClear-faultStart),"IEEE39Fault");
        }
        assertTrue(algo.initialization(), "IEEE39 initialization");
        var initial=record(net);
        try (var writer=Files.newBufferedWriter(out.resolve("interpss.csv"))) {
            output[0] = writer;
            writer.write("time_s,"+String.join(",",initial.keySet())+"\n");
            write(writer,0.,initial);
            while (algo.getSimuTime()<stop-step/2) {
                assertTrue(algo.solveDEqnStep(true),"IEEE39 integration");
                write(writer,algo.getSimuTime(),record(net));
            }
        }
        Files.writeString(out.resolve("interpss-settings.json"), new com.google.gson.GsonBuilder()
                .setPrettyPrinting().create().toJson(Map.of(
                        "step_s", step, "network_tolerance", NETWORK_TOLERANCE,
                        "network_max_iterations", 100, "frequency_filter_s", .05,
                        "stop_s", stop, "scenario", scenario,
                        "fault_start_s", faultStart, "fault_clear_s", faultClear,
                        "integration_method", staggered ? "psse-staggered-euler" : "modified-euler")) + "\n");
    }

    private static void write(java.io.Writer writer,double time,Map<String,Double> values) throws Exception {
        writer.write(String.format(Locale.ROOT,"%.12g",time));
        for (var entry:values.entrySet()) {
            assertTrue(Double.isFinite(entry.getValue()),entry.getKey()+" at "+time);
            writer.write(String.format(Locale.ROOT,",%.12g",entry.getValue()));
        }
        writer.write("\n");
    }

    private static Map<String,Double> record(BaseDStabNetwork<?,?> net) {
        var row=new LinkedHashMap<String,Double>();
        for(int bus=1;bus<=39;bus++) {
            var b=net.getBus("Bus"+bus);
            row.put("B"+bus+".VPU",b.getVoltageMag());
            row.put("B"+bus+".FHZ",60*b.getFreq());
        }
        for(int bus=30;bus<=39;bus++) {
            var m=net.getMachine("Bus"+bus+"-mach1");
            String k="M"+bus+".";
            var v=m.getDStabBus().getVoltage();
            var current=m.getIgen().subtract(v.multiply(m.getYgen()));
            var power=v.multiply(current.conjugate());
            row.put(k+"PE_SYS",power.getReal()); row.put(k+"QE_SYS",power.getImaginary());
            row.put(k+"EFD_OUT",m.getEfd()); row.put(k+"PMECH",m.getPm());
            if(m instanceof RoundRotorMachine r) {
                row.put(k+"EQ1",r.getEq1()); row.put(k+"ED1",r.getEd1());
                row.put(k+"PSIKD",r.getPsikd()); row.put(k+"PSIKQ",r.getPsikq());
            }
            row.put(k+"SPEED_DEV",m.getSpeed()-1); row.put(k+"ANGLE_RAD",m.getAngle());
            if(bus==39) continue;
            var exc=(Ieee1968Type1Exciter)m.getExciter();
            var states=exc.getNamedStates();
            row.put("E"+bus+".VMEAS",exc.getSensedVoltage());
            row.put("E"+bus+".VR",exc.getRegulatorOutput());
            row.put("E"+bus+".EFD",exc.getFieldVoltageState());
            // PSS/E stores KF*filtered EFD; CML stores KF/TF*filtered EFD.
            row.put("E"+bus+".RFSTATE",exc.tf*states.get("washoutBlock"));
            var gov=(AbstractAnnotateController)m.getGovernor();
            states=gov.getNamedStates();
            String[] names,blocks;
            if(gov instanceof PsseGASTGasTurGovernor) {
                names=new String[]{"VALVE","FUEL","TEMP"};
                blocks=new String[]{"Fuel Valve","Fuel Flow","Exhaust Temperature"};
            } else if(bus==31||bus==34||bus==35) {
                names=new String[]{"VALVE","TURBINE"};
                blocks=new String[]{"t1DelayBlock","t2t3FilterBlock"};
            } else if(bus==32||bus==38) {
                names=new String[]{"FILTER","SERVO","TURBINE1","TURBINE2","TURBINE3"};
                blocks=new String[]{"filterBlock","t3DelayBlock","t4DelayBlock","t5DelayBlock","t6DelayBlock"};
            } else {
                names=new String[]{"FILTER","VALVE","TURBINE1","TURBINE2","TURBINE3","TURBINE4"};
                blocks=new String[]{"filterBlock","intBlock","chDelayBlock","rh1DelayBlock","rh2DelayBlock","coDelayBlock"};
            }
            for(int i=0;i<names.length;i++) {
                var block = gov.getNamedStateBlocks().get(blocks[i]);
                // PSS/E also fills bypassed turbine-delay slots with their output.
                double value = block instanceof DelayControlBlock delay && delay.getT() <= 0.
                        ? delay.getY() : states.get(blocks[i]);
                row.put("G"+bus+"."+names[i], value);
            }
            if(gov instanceof PsseTGov1SteamTurGovernor tgov) {
                // PSS/E's reheater integrator is T3 times the CML filter state.
                row.put("G"+bus+".TURBINE",tgov.t3*states.get("t2t3FilterBlock"));
            }
            // A bypassed PSS/E filter slot reports its algebraic output. CML's
            // unused storage state remains zero when the denominator time is zero.
            if ((gov instanceof IeeeSteamTCDRGovernor g1 && g1.t1==0.)
                    || (gov instanceof PsseIEESGOSteamTurGovernor gs && gs.t1==0.)) {
                // IEESGO places K1 in PSS/E's first filter; CML places it in T3.
                double gain=gov instanceof PsseIEESGOSteamTurGovernor gs ? gs.k1 : 1.;
                row.put("G"+bus+".FILTER",gain*gov.getNamedStateBlocks().get("filterBlock").getY());
            }
        }
        return row;
    }
}
