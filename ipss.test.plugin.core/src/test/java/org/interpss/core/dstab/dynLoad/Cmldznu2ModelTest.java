package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.IDStabSimuOutputHandler;

class Cmldznu2ModelTest {
    private static final Path DATA = Path.of("testData", "adpter", "psse", "v33");
    private static final double[][] NATIVE_CHECKPOINTS = {
        {0.025, 0.995770215988, 0.867777840499407, 0.911911925565266,
                -0.0145515305921, 0.998636841774, 2.45085001183e-08, 0.0820726528764},
        {0.049, 0.995770215988, 0.867778625334632, 0.911912574775620,
                -0.0145521918312, 0.998636841774, 1.39202416261e-09, 0.0820726528764},
        {0.051, 0.666286408901, 0.859864730593812, 0.884129870979700,
                -0.0160122588277, 0.988950014114, 1.65400382279e-09, 0.0820792317390},
        {0.075, 0.642195165157, 0.649305573191594, 0.642128168130938,
                -0.0321216695011, 0.770987093449, -0.00124753324781, 0.0823971927166},
        {0.099, 0.634616971016, 0.542477989381010, 0.553885425110049,
                -0.0350374802947, 0.671463847160, -0.000443400029326, 0.0828464105725},
        {0.101, 0.964021742344, 0.544235435365148, 0.576198895057674,
                -0.0337883494794, 0.676364243031, -0.000403504585847, 0.0828719064593},
        {0.125, 0.988496184349, 0.721340537262476, 0.796908963919990,
                -0.0161307808012, 0.860187828541, 0.00150223867968, 0.0828695446253},
        {0.150, 0.998145341873, 0.821095829373164, 0.879134531121032,
                -0.0127940168604, 0.946626007557, 0.00165915430989, 0.0828685984015},
        {0.200, 1.002755880360, 0.873253607955471, 0.919276452279925,
                -0.0138207552955, 1.000820040700, 0.00152692198753, 0.0828641504049},
        {0.250, 1.003957986830, 0.878872568644313, 0.923995716515125,
                -0.0122758923098, 1.010350108150, 0.00160058168694, 0.0828592032194},
        {0.300, 1.004900693890, 0.881159024700486, 0.926049383479020,
                -0.0124281337485, 1.012781143190, 0.00168923288584, 0.0828539878130},
        {0.400, 1.002094149590, 0.876922689132069, 0.921500943346914,
                -0.0121914651245, 1.008718013760, 0.00184996891767, 0.0828448906541},
        {0.500, 1.000784754750, 0.875771681009823, 0.920210518755777,
                -0.0119630815461, 1.007464408870, 0.00216257316060, 0.0828365907073}
    };
    private static final double[] NATIVE_TOLERANCES = {
            0.0070, 0.0062, 0.0072, 0.0011, 0.0081, 0.0016, 0.0068};

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
        String rawText = Files.readString(DATA.resolve("ieee9_v33.raw"));
        String originalLoadPrefix = "5,'1 ',1,   1,   1,";
        assertTrue(rawText.contains(originalLoadPrefix));
        Path raw = tempDir.resolve("public-ieee9-load-zone.raw");
        Files.writeString(raw, rawText.replace(originalLoadPrefix,
                "5,'1 ',1,   7,   9,"));
        var context = new PSSEMultiFileLoader().loadDStab(
                raw.toString(),
                DATA.resolve("ieee9_perc1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var network = context.getDStabilityNet();
        network.getBusList().forEach(bus -> {
            if (bus.getInfoOnlyDynModel() instanceof org.interpss.dstab.dynLoad.impl.Perc1Model) {
                bus.setInfoOnlyDynModel(null);
            }
            if (bus.getDynLoadModelList() != null) bus.getDynLoadModelList().clear();
        });

        assertEquals(1, network.getDStabBus("Bus5").getZone().getNumber());
        int zone = 9;
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
        assertNull(network.getDStabBus("Bus6").getInfoOnlyDynModel());
        assertNull(network.getDStabBus("Bus8").getInfoOnlyDynModel());
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
        model.getLoadBus().setFreq(0.50);
        model.getDStabBus().setFreq(0.98);
        double dt = 0.01;
        double predictor = 1.0 + dt * (0.98 - 1.0) / 0.07;
        double expectedFrequency = 1.0 + 0.5 * dt
                * ((0.98 - 1.0) / 0.07 + (0.98 - predictor) / 0.07);
        assertTrue(model.get1PhaseACMotor().nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.get1PhaseACMotor().nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertEquals(expectedFrequency, model.get1PhaseACMotor().getMeasuredFrequency(), 1.0e-12);
    }

    @ParameterizedTest
    @CsvSource({"0.001,true", "0.0005,true", "0.001,false", "0.0005,false"})
    void syntheticFaultTrajectoryRemainsFiniteAndExercisesProtection(
            double timeStep, boolean motorDominant, @TempDir Path tempDir)
            throws Exception {
        String rawText = Files.readString(DATA.resolve("ieee9_v33.raw"));
        String originalLoadPrefix = "5,'1 ',1,   1,   1,";
        assertTrue(rawText.contains(originalLoadPrefix));
        Path raw = tempDir.resolve("public-ieee9-composite-fault.raw");
        Files.writeString(raw, rawText.replace(originalLoadPrefix,
                "5,'1 ',1,   7,   9,"));
        Path dyr = tempDir.resolve("synthetic-composite-fault.dyr");
        double[] faultConstants = syntheticConstants();
        if (motorDominant) {
            faultConstants[18] = 0.80;
            faultConstants[21] = 0.0;
            faultConstants[22] = 0.0;
        }
        String constants = Arrays.stream(faultConstants)
                .mapToObj(Double::toString).collect(Collectors.joining(" "));
        Files.writeString(dyr,
                "1 'GENCLS' '1' 3.173 0 /\n"
                + "2 'GENCLS' '1' 4.237 0 /\n"
                + "3 'GENCLS' '1' 3.691 0 /\n"
                + "9 'USRLOD' '*' 'CMLDZNU2' 12 3 2 133 27 146 48 0 0 "
                + constants + " /\n");

        var context = new PSSEMultiFileLoader().loadDStab(raw.toString(), dyr.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(timeStep);
        algorithm.setTotalSimuTimeSec(0.5);
        algorithm.setRefMachine(network.getMachine("Bus1-mach1"));
        algorithm.setSimuOutputHandler(noOpOutput("Bus5"));
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5", network,
                SimpleFaultCode.GROUND_3P,
                new Complex(0.0, motorDominant ? 0.05 : 0.20), null,
                0.05, 0.05), "SyntheticBus5Fault");
        assertTrue(algorithm.initialization());
        Cmldznu2Model model = assertInstanceOf(Cmldznu2Model.class,
                network.getDStabBus("Bus5").getInfoOnlyDynModel());
        var motorA = model.getInductionMotorA();
        assertEquals(0.8400438116205978, motorA.getNamedState("EPrimeQ"), 1.0e-12);
        assertEquals(-0.21162634927156448, motorA.getNamedState("EPrimeD"), 1.0e-12);
        assertEquals(0.8892794025809891, motorA.getNamedState("EDoublePrimeQ"), 1.0e-12);
        assertEquals(-0.19474031161006025, motorA.getNamedState("EDoublePrimeD"), 1.0e-12);
        double minimumVoltage = network.getDStabBus("Bus5").getVoltageMag();
        double minimumOnlineFraction = model.getInductionMotorA().getFonline();
        int checkpointIndex = 0;
        double[] maximumNativeError = new double[NATIVE_TOLERANCES.length];
        while (algorithm.getSimuTime() < 0.5 - timeStep / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true), () -> "failed at t="
                    + algorithm.getSimuTime() + ", states=" + model.getNamedStates());
            minimumVoltage = Math.min(minimumVoltage,
                    network.getDStabBus("Bus5").getVoltageMag());
            minimumOnlineFraction = Math.min(minimumOnlineFraction,
                    model.getInductionMotorA().getFonline());
            assertTrue(Double.isFinite(model.getLoadBus().getVoltageMag()));
            if (!motorDominant && checkpointIndex < NATIVE_CHECKPOINTS.length
                    && algorithm.getSimuTime() >= NATIVE_CHECKPOINTS[checkpointIndex][0]
                            - timeStep / 2.0) {
                Map<String, Double> state = model.getNamedStates();
                double[] actual = {
                    network.getDStabBus("Bus5").getVoltageMag(),
                    Math.hypot(state.get("MotorA.EPrimeQ"), state.get("MotorA.EPrimeD")),
                    Math.hypot(state.get("MotorA.EDoublePrimeQ"),
                            state.get("MotorA.EDoublePrimeD")),
                    state.get("MotorA.SpeedDeviation"), state.get("MotorD.Vmeas"),
                    state.get("MotorD.Fmeas") - 1.0, state.get("MotorD.TemperatureA")
                };
                for (int channel = 0; channel < actual.length; channel++) {
                    maximumNativeError[channel] = Math.max(maximumNativeError[channel],
                            Math.abs(actual[channel]
                                    - NATIVE_CHECKPOINTS[checkpointIndex][channel + 1]));
                }
                checkpointIndex++;
            }
        }
        assertTrue(minimumVoltage < 0.75, "fault must exercise the low-voltage response");
        if (motorDominant) {
            assertEquals(0.82, minimumOnlineFraction, 1.0e-12,
                    "the independently selected first trip stage removes 18% of Motor A");
            assertEquals(1.0, model.getInductionMotorA().getFonline(), 1.0e-12,
                    "Motor A must reclose after its synthetic restart delay");
        } else {
            assertEquals(NATIVE_CHECKPOINTS.length, checkpointIndex);
            String[] channels = {"terminal voltage", "|E'|", "|Ek|", "speed deviation",
                    "Motor-D sensed voltage", "Motor-D sensed frequency", "Motor-D thermal"};
            for (int channel = 0; channel < maximumNativeError.length; channel++) {
                assertTrue(maximumNativeError[channel] <= NATIVE_TOLERANCES[channel],
                        channels[channel] + " error " + maximumNativeError[channel]
                                + " exceeds " + NATIVE_TOLERANCES[channel]);
            }
        }
    }

    private static IDStabSimuOutputHandler noOpOutput(String... ids) {
        return new IDStabSimuOutputHandler() {
            private java.util.List<String> outputIds = java.util.List.of(ids);
            @Override public boolean onSimuEvent(
                    com.interpss.dstab.datatype.DStabSimuEvent event) { return true; }
            @Override public boolean init(String id,
                    com.interpss.dstab.BaseDStabNetwork<?, ?> network) { return true; }
            @Override public boolean close() { return true; }
            @Override public boolean isOutputFilter() { return true; }
            @Override public void setOutputFilter(boolean filter) { }
            @Override public java.util.List<String> getOutputVarIdList() { return outputIds; }
            @Override public void setOutputVarIdList(String[] values) {
                outputIds = java.util.List.of(values);
            }
        };
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
