package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Exports all IEEE39 bus and generator states; acceptance is in the comparison report. */
@Tag("extended")
public class Ieee39PsseBenchmarkTest {
    private static final Path CASE = Path.of("testData/adpter/psse/v30/IEEE39Bus");

    @ParameterizedTest
    @CsvSource({"flat,0.001", "fault,0.001", "flat,0.0005", "fault,0.0005"})
    void exportBenchmark(String scenario, double step) throws Exception {
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
        Path out = Path.of(step==0.001 ? "target/ieee39-psse-benchmark" : "target/ieee39-psse-benchmark-fine", scenario);
        Files.createDirectories(out);
        StringBuilder pf = new StringBuilder("bus,v_pu,angle_deg\n");
        for (int i=1;i<=39;i++) {
            var bus=net.getBus("Bus"+i);
            pf.append(String.format(Locale.ROOT,"%d,%.12g,%.12g%n",i,bus.getVoltageMag(),Math.toDegrees(bus.getVoltageAng())));
        }
        Files.writeString(out.resolve("interpss-powerflow.csv"),pf);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(step);
        algo.setTotalSimuTimeSec(5.0);
        algo.setSimuOutputHandler(new StateMonitor());
        algo.setOutPutPerSteps(1);
        if (scenario.equals("fault")) {
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus17",net,
                    SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,1.,.05),"IEEE39Fault");
        }
        assertTrue(algo.initialization(), "IEEE39 initialization");
        var initial=record(net);
        try (var writer=Files.newBufferedWriter(out.resolve("interpss.csv"))) {
            writer.write("time_s,"+String.join(",",initial.keySet())+"\n");
            write(writer,0.,initial);
            while (algo.getSimuTime()<5.-step/2) {
                assertTrue(algo.solveDEqnStep(true),"IEEE39 integration");
                write(writer,algo.getSimuTime(),record(net));
            }
        }
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
            for(int i=0;i<names.length;i++) row.put("G"+bus+"."+names[i],states.get(blocks[i]));
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
