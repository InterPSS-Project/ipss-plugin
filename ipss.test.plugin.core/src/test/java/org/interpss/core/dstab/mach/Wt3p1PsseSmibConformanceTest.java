package org.interpss.core.dstab.mach;

import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt3g2Model;
import org.interpss.dstab.mach.Wt3p1Model;
import org.interpss.dstab.mach.Wt3p1Data;
import org.interpss.dstab.mach.Wt3t1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-loop WT3P1 comparison against the independent native trajectory. */
public class Wt3p1PsseSmibConformanceTest {
    private static final double STEP = .0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "smib-wt3p1", "psse.csv");

    @Test
    void pitchAndMechanicalStatesMatchFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt3p1_psse36.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", network,
                SimpleFaultCode.GROUND_3P, new Complex(0.0, .1), null, .05, .05), "WindFault");
        assertTrue(algorithm.initialization());
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt3g2Model generator = (Wt3g2Model) gen.getDynamicGenDevice();
        Wt3t1Model drive = generator.getDriveTrain();
        Wt3p1Model pitch = drive.getPitchController();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, generator, drive, pitch);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, generator, drive, pitch);
        }
        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        String[] names = {"V_BUS1", "V_BUS2", "P_PU", "Q_PU", "PITCH",
                "PITCH_CONTROL", "PITCH_COMPENSATION", "SHAFT_ANGLE",
                "TURBINE_SPEED_DEV", "GENERATOR_SPEED_DEV", "GENERATOR_ANGLE_DEV",
                "WPCMND", "WNDSP1"};
        double[] maximum = new double[names.length];
        double[] maximumTime = new double[names.length];
        for (double[] expected : reference.rows) {
            double time = expected[0];
            if (time < -1e-9 || time > 1.0 + 1e-8 || Math.abs(time-.05) < STEP
                    || Math.abs(time-.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            for (int c = 0; c < names.length; c++) {
                double error = Math.abs(row[c+1] - expected[reference.columns.get(names[c])]);
                if (error > maximum[c]) { maximum[c] = error; maximumTime[c] = time; }
            }
        }
        System.out.println("WT3P1 native max errors: " + Arrays.toString(maximum));
        System.out.println("WT3P1 native max-error times: " + Arrays.toString(maximumTime));
        double[] ceilings = {.0055,.011,.0085,.0014,.075,.0025,1e-12,.026,
                .001,.0015,.10,.0032,.00030};
        for (int c = 0; c < names.length; c++) assertTrue(maximum[c] <= ceilings[c], names[c]);
    }

    @Test
    void recordedNativeInputsCloseThePitchControllerEquations() throws Exception {
        Csv reference = read(REFERENCE);
        double[] first = reference.rows.get(0);
        Wt3p1Model model = new Wt3p1Model(new Wt3p1Data(
                .27, 135, 23, 2.7, 27, 0, 25, 9, .92));
        model.initialize(first[reference.columns.get("PITCH")],
                first[reference.columns.get("GENERATOR_SPEED_DEV")],
                first[reference.columns.get("WPCMND")]);
        double[] maximum = new double[3];
        double[] maximumTime = new double[3];
        double maximumPitchRateResidual = 0.0;
        double maximumPitchRateTime = 0.0;
        double[] previous = first;
        for (int i = 1; i < reference.rows.size(); i++) {
            double[] current = reference.rows.get(i);
            double dt = current[0] - previous[0];
            if (dt <= 1.0e-10) { previous = current; continue; }
            double time = previous[0];
            if (Math.abs(time - .05) >= .001 && Math.abs(time - .10) >= .001) {
                double expectedRate = Wt3p1Model.publishedPitchRate(model.getData(),
                        previous[reference.columns.get("PITCH")],
                        previous[reference.columns.get("PITCH_CONTROL")],
                        previous[reference.columns.get("PITCH_COMPENSATION")],
                        previous[reference.columns.get("GENERATOR_SPEED_DEV")],
                        previous[reference.columns.get("WNDSP1")],
                        previous[reference.columns.get("WPCMND")], dt);
                double observedRate = (current[reference.columns.get("PITCH")]
                        - previous[reference.columns.get("PITCH")]) / dt;
                double residual = Math.abs(expectedRate - observedRate);
                if (residual > maximumPitchRateResidual) {
                    maximumPitchRateResidual = residual;
                    maximumPitchRateTime = time;
                }
            }
            model.step(dt, previous[reference.columns.get("GENERATOR_SPEED_DEV")],
                    previous[reference.columns.get("WNDSP1")],
                    previous[reference.columns.get("WPCMND")], 0);
            model.step(dt, current[reference.columns.get("GENERATOR_SPEED_DEV")],
                    current[reference.columns.get("WNDSP1")],
                    current[reference.columns.get("WPCMND")], 1);
            double[] errors = {Math.abs(model.getPitch()-current[reference.columns.get("PITCH")]),
                    Math.abs(model.getPitchControlState()-current[reference.columns.get("PITCH_CONTROL")]),
                    Math.abs(model.getPitchCompensationState()-current[reference.columns.get("PITCH_COMPENSATION")])};
            for (int c=0;c<3;c++) if(errors[c]>maximum[c]){maximum[c]=errors[c];maximumTime[c]=current[0];}
            previous = current;
        }
        System.out.println("WT3P1 native-input play-in max errors: "
                + Arrays.toString(maximum));
        System.out.println("WT3P1 native-input play-in max-error times: "
                + Arrays.toString(maximumTime));
        System.out.println("WT3P1 native pitch-rate residual: "
                + maximumPitchRateResidual + " at " + maximumPitchRateTime);
        assertTrue(maximum[0] < .0085, "PITCH play-in");
        assertTrue(maximum[1] < .0001, "PITCH_CONTROL");
        assertTrue(maximum[2] < 1.0e-12, "PITCH_COMPENSATION");
        assertTrue(maximumPitchRateResidual < .075, "PITCH derivative");
    }

    private static void record(List<double[]> rows, double t,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Wt3g2Model generator,
            Wt3t1Model drive, Wt3p1Model pitch) {
        rows.add(new double[]{t, network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(), generator.getP(), generator.getQ(),
                pitch.getPitch(), pitch.getPitchControlState(),
                pitch.getPitchCompensationState(), drive.getShaftAngle(),
                drive.getTurbineSpeed()-1.0, drive.getGeneratorSpeed()-1.0,
                drive.getGeneratorAngleDeviation(), generator.getElectricalController().getPowerOrder(),
                generator.getElectricalController().getSpeedReferenceState()});
    }
    private static Csv read(Path path) throws Exception {
        List<String> lines=EmbeddedNativeTrajectoryValues.lines(path); String[] head=lines.get(0).split(",");
        Map<String,Integer> columns=new LinkedHashMap<>();
        for(int i=0;i<head.length;i++) columns.put(head[i],i);
        return new Csv(columns,lines.stream().skip(1).map(s->Arrays.stream(s.split(","))
                .mapToDouble(Double::parseDouble).toArray()).toList());
    }
    private static double[] interpolate(List<double[]> rows,double target){
        for(int i=0;i<rows.size();i++){double[] lo=rows.get(i);if(Math.abs(lo[0]-target)<1e-8)return lo;
            if(i+1<rows.size()&&rows.get(i+1)[0]>target){double[] hi=rows.get(i+1);double f=(target-lo[0])/(hi[0]-lo[0]);double[] out=new double[lo.length];out[0]=target;for(int c=1;c<out.length;c++)out[c]=lo[c]+f*(hi[c]-lo[c]);return out;}}
        return rows.get(rows.size()-1);
    }
    private record Csv(Map<String,Integer> columns,List<double[]> rows){}
}
