package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import org.interpss.dstab.dynLoad.impl.Cmldznu2Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;

class Cmldznu2ModelTest {
    private static final Path DATA = Path.of("testData", "adpter", "psse", "v33");

    @BeforeAll static void setup() { IpssCorePlugin.init(); }

    @Test
    void exposesExactSchemaAndPublishedStaticEquations() {
        double[] values = syntheticConstants();
        Cmldznu2Data data = new Cmldznu2Data(values);
        assertEquals(133, data.values().length);
        assertEquals(133, Cmldznu2Data.parameterNames().size());
        double ratio = 0.91;
        double frequencyDeviation = -0.012;
        double expectedP = (0.23 * Math.pow(ratio, 1.7)
                + 0.51 * Math.pow(ratio, 0.8) + 0.26) * (1.0 - 0.4 * frequencyDeviation);
        double expectedQ = (0.34 * Math.pow(ratio, 2.2)
                + 0.29 * Math.pow(ratio, 1.1) + 0.37) * (1.0 + 0.6 * frequencyDeviation);
        assertEquals(expectedP, data.staticActiveFactor(ratio, frequencyDeviation), 1.0e-14);
        assertEquals(expectedQ, data.staticReactiveFactor(ratio, frequencyDeviation), 1.0e-14);
        assertEquals(0.5, data.electronicVoltageFraction(0.66), 1.0e-14);
    }

    @Test
    void rejectsInvalidSubsystemBaseAndFraction() {
        double[] values = syntheticConstants();
        values[0] = 40.0;
        double[] positiveBase = values;
        assertThrows(IllegalArgumentException.class, () -> new Cmldznu2Data(positiveBase));
        values = syntheticConstants();
        values[132] = 1.01;
        double[] invalid = values;
        assertThrows(IllegalArgumentException.class, () -> new Cmldznu2Data(invalid));
    }

    @Test
    void importsZoneScopedWrapperWithSyntheticConstants(@TempDir Path tempDir) throws Exception {
        var context = new PSSEMultiFileLoader().loadDStab(
                DATA.resolve("ieee9_v33.raw").toString(),
                DATA.resolve("ieee9_perc1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var network = context.getDStabilityNet();
        network.getBusList().forEach(bus -> {
            if (bus.getInfoOnlyDynModel() instanceof org.interpss.dstab.dynLoad.impl.Perc1Model) {
                bus.setInfoOnlyDynModel(null);
            }
            if (bus.getDynLoadModelList() != null) bus.getDynLoadModelList().clear();
        });

        int zone = Math.toIntExact(network.getDStabBus("Bus5").getZone().getNumber());
        String constants = Arrays.stream(syntheticConstants())
                .mapToObj(Double::toString).collect(Collectors.joining(" "));
        Path input = tempDir.resolve("synthetic-composite-load.dyr");
        Files.writeString(input, zone + " 'USRLOD' '*' 'CMLDZNU2' "
                + "12 3 2 133 27 146 48 0 0 " + constants + " /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(input.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        Cmldznu2Model model = assertInstanceOf(Cmldznu2Model.class,
                network.getDStabBus("Bus5").getInfoOnlyDynModel());
        assertEquals(-0.77, model.getMvaBase(), 0.0);
        assertEquals(0.031, model.getDistEquivalent().getRFdr(), 0.0);
        assertEquals(0.006, model.getInductionMotorA().getTpp0(), 0.0);
        assertEquals(1.3, model.getInductionMotorA().getTorqueExponent(), 0.0);
        assertEquals(0.15, model.getFmD(), 0.0);
        assertEquals(0.15, model.getFel(), 0.0);
        assertEquals(0.60, model.getStaticFraction(), 1.0e-14);
        assertTrue(model.getTargetLoadIds().contains("1"));
        assertTrue(model.initStates());
        assertTrue(model.getNamedStates().containsKey("MotorD.StallTimer"));
        assertTrue(model.getNamedStates().containsKey("STATIC.P"));
        assertTrue(model.getNamedStates().containsKey("ELECTRONIC.Fraction"));
        model.getLoadBus().setVoltage(new Complex(0.66, 0.0));
        model.refreshAlgebraicComponents();
        assertEquals(0.5, model.getNamedState("ELECTRONIC.Fraction"), 1.0e-12);
        model.getLoadBus().setVoltage(Complex.ONE);
        model.refreshAlgebraicComponents();
        assertEquals(0.815, model.getNamedState("ELECTRONIC.Fraction"), 1.0e-12);
        model.getLoadBus().setFreq(0.98);
        double dt = 0.01;
        double predictor = 1.0 + dt * (0.98 - 1.0) / 0.07;
        double expectedFrequency = 1.0 + 0.5 * dt
                * ((0.98 - 1.0) / 0.07 + (0.98 - predictor) / 0.07);
        assertTrue(model.get1PhaseACMotor().nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.get1PhaseACMotor().nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertEquals(expectedFrequency, model.get1PhaseACMotor().getMeasuredFrequency(), 1.0e-12);
    }

    private static double[] syntheticConstants() {
        double[] c = new double[133];
        c[0] = -0.77; c[1] = 0.017; c[2] = 0.031; c[3] = 0.046; c[4] = 0.35;
        c[5] = 0.071; c[6] = 1.01; c[7] = 0.99; c[8] = -1;
        c[9] = 0.91; c[10] = 1.09; c[11] = 0.005; c[12] = 1.015; c[13] = 1.045;
        c[14] = 24; c[15] = 4; c[16] = 0.004; c[17] = 0.009;
        c[18] = 0.10; c[19] = 0; c[20] = 0; c[21] = 0.15; c[22] = 0.15;
        c[23] = 0.96; c[24] = 0.72; c[25] = 0.60; c[26] = 0.94;
        c[27] = 1.7; c[28] = 0.23; c[29] = 0.8; c[30] = 0.51; c[31] = -0.4;
        c[32] = 2.2; c[33] = 0.34; c[34] = 1.1; c[35] = 0.29; c[36] = 0.6;
        configureMotor(c, 37, 1.3, 0.006, 0.68, 0.18);
        configureMotor(c, 57, 1.8, 0.007, 0.64, 0.16);
        configureMotor(c, 77, 2.1, 0.008, 0.61, 0.14);
        c[97] = 0.045; c[98] = 0.33; c[99] = 0.025; c[100] = 0.07;
        c[101] = 0.82; c[102] = 0.95; c[103] = 0.58; c[104] = 0.11; c[105] = 0.13;
        c[106] = 0.04; c[107] = 0.2; c[108] = 1.1; c[109] = 5.4; c[110] = 2.2;
        c[111] = 10.8; c[112] = 3.0; c[113] = 9.7; c[114] = 2.3; c[115] = 0.84;
        c[116] = 0.27; c[117] = 0.93; c[118] = 0.8; c[119] = -2.7;
        c[120] = 0.54; c[121] = 0.43; c[122] = 0.64; c[123] = 0.52;
        c[124] = 13; c[125] = 0.74; c[126] = 1.75; c[127] = 0.12;
        c[128] = 0.62; c[129] = 0.035; c[130] = 0.49; c[131] = 0.12; c[132] = 0.63;
        return c;
    }

    private static void configureMotor(double[] c, int typeIndex, double exponent,
            double subtransientTime, double trip1, double tripFraction1) {
        c[typeIndex] = 3; c[typeIndex + 1] = 0.73; c[typeIndex + 2] = 0.035;
        c[typeIndex + 3] = 1.65; c[typeIndex + 4] = 0.17; c[typeIndex + 5] = 0.12;
        c[typeIndex + 6] = 0.16; c[typeIndex + 7] = subtransientTime;
        c[typeIndex + 8] = 0.31; c[typeIndex + 9] = exponent;
        c[typeIndex + 10] = trip1; c[typeIndex + 11] = 0.04;
        c[typeIndex + 12] = tripFraction1; c[typeIndex + 13] = 0.88;
        c[typeIndex + 14] = 0.21; c[typeIndex + 15] = 0.48;
        c[typeIndex + 16] = 0.06; c[typeIndex + 17] = 0.22;
        c[typeIndex + 18] = 0.79; c[typeIndex + 19] = 0.24;
    }
}
