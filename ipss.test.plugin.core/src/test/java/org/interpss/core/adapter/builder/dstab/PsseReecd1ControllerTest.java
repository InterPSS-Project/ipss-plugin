package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.interpss.dstab.renewable.Reecd1Data;
import org.interpss.dstab.renewable.Reecd1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Regcb1Data;
import org.interpss.dstab.renewable.Regcb1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

/** Exact public schema, attachment, state, limiter, and timer checks for REECD1. */
public class PsseReecd1ControllerTest {
    private static final String REGCA =
            "1 'REGCA1' '1' 1 .02 10 .9 .5 1.22 1.2 .8 .4 -1.3 .02 .7 0 0 .8 /\n";
    private static final String PARAMETERS =
            "0 1 1 0 0 1 "
            + ".82 1.18 .025 -.03 .04 2.3 .95 -.92 0 .07 .06 .08 .03 "
            + ".85 -.75 1.12 .88 .4 3.1 .6 4.2 .02 .04 1.7 -1.5 1.05 -.9 1.24 .05 "
            + ".20 .30 .35 .55 .50 .75 .65 .95 .80 1.10 .95 1.18 1.05 1.16 "
            + "1.15 1.05 1.25 .80 1.35 .20 "
            + ".20 .25 .35 .50 .50 .70 .65 .90 .80 1.05 .95 1.20 1.05 1.15 "
            + "1.15 1.00 1.25 .70 1.35 .10 "
            + ".01 .07 .03 .04 .5 .25 1.45 .09";

    @Test
    void flatAndWrappedFormsAttachTheSameSevenStateController(@TempDir Path tempDir)
            throws Exception {
        Reecd1Model flat = parse(tempDir.resolve("flat.dyr"),
                REGCA + "1 'REECD' '1' " + PARAMETERS + " /\n");
        Reecd1Model wrapped = parse(tempDir.resolve("wrapped.dyr"),
                REGCA + "1 'USRMDL' '1' 'REECDU1' 102 0 6 77 7 20 "
                        + PARAMETERS + " /\n");

        assertSameScalarData(flat.getData(), wrapped.getData());
        assertArrayEquals(flat.getData().reactiveVoltagePoints(),
                wrapped.getData().reactiveVoltagePoints());
        assertArrayEquals(flat.getData().reactiveCurrentPoints(),
                wrapped.getData().reactiveCurrentPoints());
        assertArrayEquals(flat.getData().activeVoltagePoints(),
                wrapped.getData().activeVoltagePoints());
        assertArrayEquals(flat.getData().activeCurrentPoints(),
                wrapped.getData().activeCurrentPoints());
        assertEquals(Set.of("Vmeas", "Pmeas", "PIQ", "PIV", "Q_V", "Pord", "Vcomp"),
                flat.getNamedStates().keySet());
        var descriptor = DynamicModelCatalog.find("REECDU1").orElseThrow();
        assertEquals("REECD", descriptor.canonicalName());
        assertEquals(Set.of(83, 89), descriptor.recordSchema().acceptedParameterCounts());
    }

    @Test
    void rejectsAnIncorrectNativeAllocation(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("wrong.dyr");
        Files.writeString(dyr, REGCA
                + "1 'USRMDL' '1' 'REECDU1' 102 0 6 77 7 19 "
                + PARAMETERS + " /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder);
        parser.parseDynFile(dyr.toString());

        assertNull(controller(builder));
        assertTrue(!parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void tenPointTablesInterpolateAndRemainDefensive() {
        Reecd1Data data = operatingData(0, .02, 0.0, 3.0);
        double[] voltage = data.reactiveVoltagePoints();
        double[] current = data.reactiveCurrentPoints();
        voltage[0] = 99.0;
        current[0] = 99.0;

        assertEquals(.2, data.reactiveVoltagePoints()[0], 0.0);
        assertEquals(.4, limitedReactiveMagnitude(data, .30), 1.0e-12);
        assertEquals(.2, limitedReactiveMagnitude(data, .10), 1.0e-12);
        assertEquals(.1, limitedReactiveMagnitude(data, 2.0), 1.0e-12);
    }

    @Test
    void postDipDelayHoldsBothActiveCurrentCommandAndLimit() {
        Reecd1Model model = new Reecd1Model(operatingData(0, .02, 0.0, 2.0));
        model.initialize(.8, 0.0, 1.0);
        model.step(.005, .8, 0.0, .5, 1.0);
        double faultCommand = model.getIpcmd();
        double faultMaximum = model.getActiveCurrentMaximum();
        assertEquals(.8, faultCommand, 1.0e-12);
        assertEquals(.8, faultMaximum, 1.0e-12);

        model.step(.005, .8, 0.0, 1.0, 1.0);
        assertEquals(faultCommand, model.getIpcmd(), 0.0,
                "Thld2 must freeze Ipcmd on voltage-dip exit");
        assertEquals(faultMaximum, model.getActiveCurrentMaximum(), 0.0,
                "Thld2 must freeze Ipmax on voltage-dip exit");
        for (int step = 0; step < 4; step++) {
            model.step(.005, .8, 0.0, 1.0, 1.0);
        }
        assertEquals(.8, model.getIpcmd(), 1.0e-12);
        assertEquals(1.0, model.getActiveCurrentMaximum(), 1.0e-12);
    }

    @Test
    void vdlCurveStopsAtTheFirstTrailingZeroPair() {
        double[] voltage = {0.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        double[] current = {1.3, 1.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        Reecd1Data data = copyWithTablesAndCurrentLimit(
                operatingData(0, 0.0, 0.0, 3.0), voltage, current, voltage, current, 2.0);
        Reecd1Model model = new Reecd1Model(data);
        model.initialize(0.0, 2.0, 1.0);

        assertEquals(1.2, Math.abs(model.getIqcmd()), 1.0e-12);
    }

    @Test
    void reactiveInjectionRemainsLiveInsideTheDipThresholds(@TempDir Path tempDir)
            throws Exception {
        Reecd1Model model = parse(tempDir.resolve("live-injection.dyr"),
                REGCA + "1 'REECD' '1' " + PARAMETERS + " /\n");
        for (int step = 0; step < 20; step++) {
            model.step(.005, .40, .10, 1.08, 1.0);
        }

        assertTrue(!model.isVoltageDip());
        assertTrue(model.getReactiveCurrentInjection() < 0.0,
                "Kqv/deadband arm is live even when the voltage-dip flag is clear");
    }

    @Test
    void blockingThresholdsAreInclusive() {
        Reecd1Model low = new Reecd1Model(operatingData(0, 0.0, .6, 1.4));
        low.initialize(.8, .1, .6);
        assertTrue(low.isBlocked());
        assertEquals(0.0, low.getActiveCurrentMaximum(), 0.0);

        Reecd1Model high = new Reecd1Model(operatingData(0, 0.0, .6, 1.4));
        high.initialize(.8, .1, 1.4);
        assertTrue(high.isBlocked());
        assertEquals(0.0, high.getReactiveCurrentMaximum(), 0.0);
    }

    @Test
    void momentaryCessationBlocksBothCommandsUntilRecoveryDelayExpires() {
        Reecd1Model model = new Reecd1Model(operatingData(0, 0.0, .6, 1.4));
        model.initialize(.8, .1, 1.0);
        model.step(.005, .8, .1, .5, 1.0);
        assertTrue(model.isBlocked());
        assertEquals(0.0, model.getIpcmd(), 0.0);
        assertEquals(0.0, model.getIqcmd(), 0.0);

        model.step(.005, .8, .1, 1.0, 1.0);
        assertTrue(model.isBlocked());
        for (int step = 0; step < 3; step++) {
            model.step(.005, .8, .1, 1.0, 1.0);
        }
        assertTrue(!model.isBlocked());
        assertTrue(model.getIpcmd() > 0.0);
    }

    @Test
    void priorityFlagSelectsWhichCurrentOwnsTheCircle() {
        Reecd1Model qPriority = new Reecd1Model(operatingData(0, 0.0, 0.0, 2.0));
        qPriority.initialize(.9, .6, 1.0);
        assertEquals(.6, -qPriority.getIqcmd(), 1.0e-12);
        assertEquals(.8, qPriority.getIpcmd(), 1.0e-12);

        Reecd1Model pPriority = new Reecd1Model(operatingData(1, 0.0, 0.0, 2.0));
        pPriority.initialize(.9, .6, 1.0);
        assertEquals(.9, pPriority.getIpcmd(), 1.0e-12);
        assertEquals(Math.sqrt(1.0 - .9 * .9), -pPriority.getIqcmd(), 1.0e-12);
    }

    @Test
    void attachesBehindEitherSupportedRenewableConverter() throws Exception {
        DStabNetworkBuilder aBuilder = DStabBuilderTestFixture.createBuilder();
        Regca1Model aConverter = aBuilder.addRegca1("Bus1", "1", regcaData());
        Reecd1Model aController = aBuilder.addReecd1("Bus1", "1",
                operatingData(0, 0.0, 0.0, 2.0));
        assertSame(aController, aConverter.getActiveElectricalController());

        DStabNetworkBuilder bBuilder = DStabBuilderTestFixture.createBuilder();
        Regcb1Model converter = bBuilder.addRegcb1("Bus1", "1",
                new Regcb1Data(0, 0, .02, .01, 10.0, -10.0, 10.0, .01, 1.2));
        Reecd1Model bController = bBuilder.addReecd1("Bus1", "1",
                operatingData(0, 0.0, 0.0, 2.0));
        assertSame(bController, converter.getActiveElectricalController());
        Repca1Model plant = bBuilder.addRepca1("Bus1", "1", plantData());
        assertSame(plant, bController.getPlantController());
    }

    @Test
    void rejectsInvalidChargingFactorAndNegativeActiveLimit() {
        Reecd1Data base = operatingData(0, 0.0, 0.0, 2.0);
        assertThrows(IllegalArgumentException.class,
                () -> copyWithChargingFactor(base, 1.01));

        double[] activeCurrent = base.activeCurrentPoints();
        activeCurrent[3] = -0.01;
        assertThrows(IllegalArgumentException.class,
                () -> copyWithTablesAndCurrentLimit(base,
                        base.reactiveVoltagePoints(), base.reactiveCurrentPoints(),
                        base.activeVoltagePoints(), activeCurrent, base.currentMaximum()));
    }

    @Test
    void syntheticVoltageAndPowerCheckpointsMatchSevenStateResponse(@TempDir Path tempDir)
            throws Exception {
        Reecd1Model model = parse(tempDir.resolve("checkpoint.dyr"),
                REGCA + "1 'REECD' '1' " + PARAMETERS + " /\n");
        double initialP = 0.23999108374118805;
        double initialQ = 0.04282698035240173;
        double initialV = 1.009988784790039;
        model.initialize(initialP, initialQ, initialV);
        for (int index = 0; index < 100; index++) {
            model.step(0.0005, initialP, initialQ, initialV, 1.0);
        }

        double[][] inputs = {
            {0.050500012934207916, 0.21445000171661377, 0.03826911374926567, 0.9025006294250488},
            {0.05100001394748688, 0.2142571210861206, 0.038232672959566116, 0.9016412496566772},
            {0.05150001496076584, 0.21426492929458618, 0.03822892904281616, 0.9015529155731201},
            {0.0520000159740448, 0.21428273618221283, 0.03822546824812889, 0.9014712572097778},
            {0.05250001698732376, 0.21431173384189606, 0.03822225704789162, 0.9013955593109131},
            {0.05500002205371857, 0.2146063596010208, 0.038209281861782074, 0.901089608669281},
            {0.05750002712011337, 0.21510747075080872, 0.03819965198636055, 0.900862455368042},
            {0.06000003218650818, 0.21573129296302795, 0.03818558156490326, 0.9005306959152222},
            {0.06250003725290298, 0.21645529568195343, 0.03816913068294525, 0.9001427292823792},
            {0.06500004231929779, 0.2172619253396988, 0.03815228119492531, 0.8997453451156616},
            {0.0675000473856926, 0.21813088655471802, 0.03813571855425835, 0.8993547558784485},
            {0.0700000524520874, 0.21902552247047424, 0.038116585463285446, 0.8989035487174988},
            {0.07250005751848221, 0.21994459629058838, 0.038097627460956573, 0.8984563946723938},
            {0.07500006258487701, 0.2208733856678009, 0.03807869553565979, 0.8980099558830261},
            {0.07750006765127182, 0.22179965674877167, 0.03805965185165405, 0.8975608348846436},
            {0.08000007271766663, 0.22271431982517242, 0.03804052248597145, 0.8971097469329834},
            {0.08250007778406143, 0.2236167937517166, 0.038022466003894806, 0.8966838717460632},
            {0.08500008285045624, 0.22449341416358948, 0.03800413757562637, 0.8962516784667969},
            {0.08750008791685104, 0.2253408432006836, 0.03798573836684227, 0.895817756652832},
            {0.09000009298324585, 0.22616353631019592, 0.037968579679727554, 0.8954131007194519},
            {0.09250009804964066, 0.2269516885280609, 0.03795141354203224, 0.8950082659721375},
            {0.09500010311603546, 0.22771134972572327, 0.03793551027774811, 0.8946332335472107},
            {0.09750010818243027, 0.2284339964389801, 0.03791961446404457, 0.8942583799362183},
            {0.10000011324882507, 0.2523067891597748, 0.041739702224731445, 0.9843475222587585},
            {0.10250011831521988, 0.2571865916252136, 0.04243624210357666, 1.0007740259170532},
            {0.10500012338161469, 0.2575739920139313, 0.04244859889149666, 1.0010653734207153},
            {0.10750012844800949, 0.2576565444469452, 0.042456209659576416, 1.0012449026107788},
            {0.1100001335144043, 0.25750237703323364, 0.04246076941490173, 1.0013524293899536},
            {0.1125001385807991, 0.2571685016155243, 0.042463745921850204, 1.001422643661499},
            {0.11500014364719391, 0.2567020356655121, 0.04246629402041435, 1.0014827251434326},
            {0.11750014871358871, 0.25614094734191895, 0.04246923327445984, 1.0015519857406616},
            {0.12000015377998352, 0.2555321753025055, 0.0424758605659008, 1.0017082691192627},
            {0.12250015884637833, 0.25490012764930725, 0.04248660430312157, 1.0019617080688477},
            {0.12500016391277313, 0.254253089427948, 0.04249997064471245, 1.00227689743042},
            {0.12750013172626495, 0.2535853385925293, 0.042512644082307816, 1.0025757551193237},
            {0.13000009953975677, 0.2529209852218628, 0.042526792734861374, 1.0029094219207764},
            {0.1325000673532486, 0.25226056575775146, 0.0425410121679306, 1.0032447576522827},
            {0.13500003516674042, 0.25161096453666687, 0.042555272579193115, 1.0035810470581055},
            {0.13750000298023224, 0.2509692907333374, 0.04256816580891609, 1.0038851499557495},
            {0.13999997079372406, 0.2503475248813629, 0.042581021785736084, 1.0041882991790771},
            {0.14249993860721588, 0.24974887073040009, 0.042593855410814285, 1.0044909715652466},
            {0.1449999064207077, 0.24916736781597137, 0.042605284601449966, 1.004760503768921},
            {0.14749987423419952, 0.24861986935138702, 0.04261792451143265, 1.0050586462020874},
            {0.14999984204769135, 0.2480849325656891, 0.042627964168787, 1.0052953958511353},
            {0.15249980986118317, 0.24758516252040863, 0.042639195919036865, 1.005560278892517},
            {0.154999777674675, 0.2470989227294922, 0.04264790192246437, 1.0057655572891235},
            {0.1574997454881668, 0.24664804339408875, 0.04265786334872246, 1.0060005187988281},
            {0.15999971330165863, 0.24621731042861938, 0.04266655817627907, 1.0062055587768555},
            {0.16249968111515045, 0.24581296741962433, 0.04267518222332001, 1.006408929824829},
            {0.16499964892864227, 0.24542741477489471, 0.042682547122240067, 1.0065826177597046},
            {0.1674996167421341, 0.2450668215751648, 0.04268989339470863, 1.0067558288574219},
            {0.16999958455562592, 0.2447301745414734, 0.042697206139564514, 1.0069283246994019},
            {0.17249955236911774, 0.24440942704677582, 0.04270327463746071, 1.0070713758468628},
            {0.17499952018260956, 0.24411043524742126, 0.04270929843187332, 1.0072134733200073},
            {0.17749948799610138, 0.24383221566677094, 0.042715299874544144, 1.007354974746704},
            {0.1799994558095932, 0.24357351660728455, 0.04272124171257019, 1.0074951648712158},
            {0.18249942362308502, 0.24333296716213226, 0.04272708296775818, 1.007632851600647},
            {0.18499939143657684, 0.24310936033725739, 0.04273279756307602, 1.007767677307129},
            {0.18749935925006866, 0.24290132522583008, 0.042738329619169235, 1.0078980922698975},
            {0.18999932706356049, 0.2427077293395996, 0.042743656784296036, 1.008023738861084},
            {0.1924992948770523, 0.24252751469612122, 0.04274876043200493, 1.0081441402435303},
            {0.19499926269054413, 0.24235963821411133, 0.04275362938642502, 1.0082589387893677},
            {0.19749923050403595, 0.24220319092273712, 0.04275824874639511, 1.008367896080017},
            {0.19999919831752777, 0.24205733835697174, 0.042762622237205505, 1.0084710121154785},
        };

        double previousTime = 0.05;
        double previousP = 0.21747349202632904;
        double previousQ = 0.038808662444353104;
        double previousV = 0.9152247905731201;
        for (double[] input : inputs) {
            model.step(input[0] - previousTime, previousP, previousQ, previousV, 1.0);
            previousTime = input[0];
            if (Math.abs(input[0] - 0.10000011324882507) < 1.0e-12) {
                assertCheckpoint(model.getNamedStates(),
                        new double[] {0.912019670009613, 0.0, 1.005510926246643,
                                0.05483430251479149, 0.0, 0.23999108374118805,
                                0.912969172000885},
                        new double[] {9.0e-4, 1.0e-12, 5.0e-6, 3.5e-4,
                                1.0e-12, 1.0e-12, 8.0e-4});
            }
            previousP = input[1];
            previousQ = input[2];
            previousV = input[3];
        }
        assertCheckpoint(model.getNamedStates(),
                new double[] {1.0053412914276123, 0.0, 1.0055792331695557,
                        0.06817366927862167, 0.0, 0.23999108374118805,
                        0.9984113574028015},
                new double[] {9.0e-4, 1.0e-12, 5.0e-6, 3.5e-4,
                        1.0e-12, 1.0e-12, 8.0e-4});
    }

    private static Reecd1Model parse(Path dyr, String contents) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Files.writeString(dyr, contents);
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        Reecd1Model model = assertInstanceOf(Reecd1Model.class, controller(builder));
        model.initialize(.40, .10, 1.0);
        return model;
    }

    private static Object controller(DStabNetworkBuilder builder) {
        Object device = generator(builder).getDynamicGenDevice();
        return device instanceof Regca1Model converter
                ? converter.getActiveElectricalController() : null;
    }

    private static DStabGen generator(DStabNetworkBuilder builder) {
        return (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
    }

    private static Reecd1Data operatingData(int priority, double activeHold,
            double blockLow, double blockHigh) {
        double[] voltage = {.2, .4, .6, .8, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5};
        double[] current = {.2, .6, 1.0, 1.0, 1.0, 1.0, 1.0, .8, .4, .1};
        return new Reecd1Data(
                0, 0, 0, 0, priority, 0,
                .9, 1.1, 0.0, -.02, .02, 0.0, 1.0, -1.0, 1.0,
                0.0, 0.0, activeHold, 0.0, 2.0, -2.0, 1.2, .8,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100.0, -100.0,
                2.0, -2.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                blockLow, blockHigh, .015,
                voltage, current, voltage, current);
    }

    private static Reecd1Data copyWithTablesAndCurrentLimit(Reecd1Data data,
            double[] reactiveVoltage, double[] reactiveCurrent,
            double[] activeVoltage, double[] activeCurrent, double currentLimit) {
        return new Reecd1Data(
                data.powerFactorFlag(), data.voltageFlag(), data.reactiveControlFlag(),
                data.powerFlag(), data.pqPriorityFlag(), data.voltageCompensationFlag(),
                data.voltageDip(), data.voltageUp(), data.voltageMeasurementTime(),
                data.deadbandLow(), data.deadbandHigh(), data.reactiveInjectionGain(),
                data.reactiveInjectionMaximum(), data.reactiveInjectionMinimum(),
                data.voltageReference(), data.frozenReactiveCurrent(), data.reactiveHoldTime(),
                data.activeLimitHoldTime(), data.powerMeasurementTime(),
                data.externalReactiveMaximum(), data.externalReactiveMinimum(),
                data.voltageControlMaximum(), data.voltageControlMinimum(),
                data.reactiveProportionalGain(), data.reactiveIntegralGain(),
                data.voltageProportionalGain(), data.voltageIntegralGain(),
                data.innerVoltageReference(), data.reactiveLagTime(),
                data.powerRampMaximum(), data.powerRampMinimum(), data.powerMaximum(),
                data.powerMinimum(), currentLimit, data.powerOrderTime(),
                data.compensationResistance(), data.compensationReactance(),
                data.compensationFilterTime(), data.reactiveDroopGain(),
                data.chargingCurrentFactor(), data.blockingVoltageLow(),
                data.blockingVoltageHigh(), data.unblockDelay(),
                reactiveVoltage, reactiveCurrent, activeVoltage, activeCurrent);
    }

    private static Reecd1Data copyWithChargingFactor(Reecd1Data data,
            double chargingFactor) {
        return new Reecd1Data(
                data.powerFactorFlag(), data.voltageFlag(), data.reactiveControlFlag(),
                data.powerFlag(), data.pqPriorityFlag(), data.voltageCompensationFlag(),
                data.voltageDip(), data.voltageUp(), data.voltageMeasurementTime(),
                data.deadbandLow(), data.deadbandHigh(), data.reactiveInjectionGain(),
                data.reactiveInjectionMaximum(), data.reactiveInjectionMinimum(),
                data.voltageReference(), data.frozenReactiveCurrent(), data.reactiveHoldTime(),
                data.activeLimitHoldTime(), data.powerMeasurementTime(),
                data.externalReactiveMaximum(), data.externalReactiveMinimum(),
                data.voltageControlMaximum(), data.voltageControlMinimum(),
                data.reactiveProportionalGain(), data.reactiveIntegralGain(),
                data.voltageProportionalGain(), data.voltageIntegralGain(),
                data.innerVoltageReference(), data.reactiveLagTime(),
                data.powerRampMaximum(), data.powerRampMinimum(), data.powerMaximum(),
                data.powerMinimum(), data.currentMaximum(), data.powerOrderTime(),
                data.compensationResistance(), data.compensationReactance(),
                data.compensationFilterTime(), data.reactiveDroopGain(), chargingFactor,
                data.blockingVoltageLow(), data.blockingVoltageHigh(), data.unblockDelay(),
                data.reactiveVoltagePoints(), data.reactiveCurrentPoints(),
                data.activeVoltagePoints(), data.activeCurrentPoints());
    }

    private static void assertCheckpoint(Map<String, Double> actual,
            double[] expected, double[] tolerance) {
        String[] names = {"Vmeas", "Pmeas", "PIQ", "PIV", "Q_V", "Pord", "Vcomp"};
        for (int index = 0; index < names.length; index++) {
            assertEquals(expected[index], actual.get(names[index]), tolerance[index], names[index]);
        }
    }

    private static Regca1Data regcaData() {
        return new Regca1Data(0, .02, 10.0, .9, .4, 1.22, 1.2, .8,
                .4, -1.3, .02, .7, 0.0, 0.0, .8);
    }

    private static Repca1Data plantData() {
        return new Repca1Data(0, 0, 0, "1", 0, 0, 0,
                0.0, 0.0, 0.0, 0.0, 0.0, .7, 0.0, 0.0, 0.0,
                1.0, -1.0, 0.0, 0.0, 1.0, -1.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 1.0, -1.0, 1.0, -1.0, 0.0, 0.0, 0.0, 0);
    }

    private static void assertSameScalarData(Reecd1Data expected, Reecd1Data actual)
            throws ReflectiveOperationException {
        for (var component : Reecd1Data.class.getRecordComponents()) {
            if (!component.getType().isArray()) {
                assertEquals(component.getAccessor().invoke(expected),
                        component.getAccessor().invoke(actual), component.getName());
            }
        }
    }

    private static double limitedReactiveMagnitude(Reecd1Data data, double voltage) {
        Reecd1Model model = new Reecd1Model(data);
        model.initialize(0.0, 2.0, voltage);
        return Math.abs(model.getIqcmd());
    }
}
