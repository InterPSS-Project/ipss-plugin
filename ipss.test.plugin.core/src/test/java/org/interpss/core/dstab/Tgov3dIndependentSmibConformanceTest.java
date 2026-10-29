package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.test.TestConfig;
import org.interpss.core.dstab.reference.Tgov3dPsseReference;
import org.interpss.dstab.control.gov.psse.tgov3.PsseTgov3dGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Native PSS/E 36.7 GENROU + TGOV3D comparison, including all governor states. */
public class Tgov3dIndependentSmibConformanceTest {
    private static final TestConfig CONFIG = TestConfig.load(
            "/test-config/ipss.test.plugin.core.json", "Tgov3dIndependentSmibConformanceTest");
    private static final double STEP = 0.0001;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesPsseBoundaryMachineAndGovernorStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_tgov3d.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENROU + TGOV3D SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        List<double[]> actual = new ArrayList<>();
        algorithm.setSolver(new com.interpss.dstab.algo.defaultImpl.DStabSolverImpl(algorithm) {
            { converge_tol = 1.e-6; setMaxIterationTimes(100); }
            @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)
                    throws com.interpss.dstab.common.DStabSimuException {
                super.rebaseDynamicModelInputs(method);
                var m = (RoundRotorMachine) network.getMachine("Bus1-mach1");
                var ref = network.getMachine("Bus2-mach1");
                record(actual, algorithm.getSimuTime(), network, m, ref,
                        (PsseTgov3dGovernor) m.getGovernor(), 0.0);
            }
        });
        assertTrue(algorithm.initialization(), "GENROU + TGOV3D SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        PsseTgov3dGovernor governor = (PsseTgov3dGovernor) machine.getGovernor();
        assertEquals("TGOV3D", governor.getName());
        assertEquals(6, governor.getNamedStates().size());
        assertEquals(governor.getValvePosition(), governor.getNamedState("Valve Position"));
        double initialRelativeAngle = 0.0;
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENROU + TGOV3D solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                    initialRelativeAngle);
        }

        // PSS/E's first governor state stores K*(1-T2/T1)*filtered speed.
        // Reheater pressure is compared directly; no initial-state translation.
        String fullReference = CONFIG.string("tgov3d.nativeReference");
        List<String> lines = fullReference == null ? Tgov3dPsseReference.lines()
                : java.nio.file.Files.readAllLines(Path.of(fullReference));
        if (fullReference == null) assertEquals(Tgov3dPsseReference.CHECKPOINT_SHA256,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", lines).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        String[] header = lines.get(0).split(",");
        var columns = new java.util.HashMap<String,Integer>();
        for (int i=0;i<header.length;i++) columns.put(header[i],i);
        double[] maximum = new double[16];
        double[] maximumTime = new double[16];
        int rowIndex=0, compared=0;
        for (String line:lines.subList(1,lines.size())) {
            double[] r=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
            if (r[0]<-1.e-9) continue;
            double time=Math.round(r[0]/STEP)*STEP;
            while (rowIndex<actual.size()-1 && actual.get(rowIndex)[0]<time-1.e-9) rowIndex++;
            double[] row=actual.get(rowIndex++);
            assertEquals(time,row[0],1.e-9,"Native sample grid/event side");
            java.util.function.ToDoubleFunction<String> v=k->r[columns.get(k)];
            double[] expected = {v.applyAsDouble("V1"),v.applyAsDouble("V2"),
                    100*v.applyAsDouble("PE"),100*v.applyAsDouble("QE"),
                    v.applyAsDouble("ANGLE")-v.applyAsDouble("REF_ANGLE"),
                    v.applyAsDouble("SPEED")-v.applyAsDouble("REF_SPEED"),
                    v.applyAsDouble("MACH0"),v.applyAsDouble("MACH2"),
                    v.applyAsDouble("MACH3"),v.applyAsDouble("MACH1"),
                    v.applyAsDouble("GOV0"),v.applyAsDouble("GOV1"),
                    v.applyAsDouble("GOV2"),v.applyAsDouble("GOV3"),
                    v.applyAsDouble("GOV4"),v.applyAsDouble("GOV5")};
            for (int col=0;col<maximum.length;col++) {
                double error=Math.abs(row[col+1]-expected[col]);
                if (error>maximum[col]) { maximum[col]=error; maximumTime[col]=time; }
            }
            compared++;
        }
        assertEquals(fullReference == null ? Tgov3dPsseReference.CHECKPOINTS : 10003,
                compared,"Native checkpoints and both event sides");
        System.out.printf(Locale.ROOT,
                "TGOV3D PSS/E max errors: v1=%.9g v2=%.9g pMW=%.9g qMvar=%.9g "
                + "angleDeg=%.9g speed=%.9g eqp=%.9g psiDp=%.9g psiQpp=%.9g edp=%.9g "
                + "leadLag=%.9g valve=%.9g bowl=%.9g reheater=%.9g crossover=%.9g intercept=%.9g%n",
                Arrays.stream(maximum).boxed().toArray());
        System.out.println("TGOV3D PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerance = {
                4.0e-5, 1.3e-5, 0.0025, 0.037, 0.00055, 3.0e-7,
                1.4e-5, 1.2e-4, 4.8e-5, 8.5e-6, 6.0e-7, 3.2e-6,
                4.8e-6, 4.7e-6, 1.8e-5, 1.0e-12
        };
        String[] label = {
                "Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle", "relative speed",
                "Eqp", "PsiDp", "PsiQpp", "Edp", "lead-lag", "valve", "steam bowl",
                "reheater pressure", "crossover", "intercept valve"
        };
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] < tolerance[index], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", label[index], maximum[index],
                    maximumTime[index], tolerance[index]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, RoundRotorMachine machine,
            Machine referenceMachine, PsseTgov3dGovernor governor, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {
                time, network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEq1(), machine.getPsikd(),
                machine.getPsikq(), machine.getEd1(), governor.getData().getK() * (1.0 - governor.getData().getT2()
                        / governor.getData().getT1()) * governor.getLeadLagState(),
                governor.getValvePosition(), governor.getSteamBowlOutput(),
                governor.getReheaterPressure(), governor.getCrossoverOutput(),
                governor.getInterceptValvePosition()
        });
    }

}
