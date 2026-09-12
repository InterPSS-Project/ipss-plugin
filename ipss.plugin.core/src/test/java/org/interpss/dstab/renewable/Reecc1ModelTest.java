package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class Reecc1ModelTest {
    private static final double TOL = 1.0e-10;

    @Test
    void exposesAllSevenPublishedStatesAndIntegratesBatteryEnergy() {
        Reecc1Model model = new Reecc1Model(data(1, .60, .10, .90));
        model.initialize(.40, .10, 1.0);

        assertEquals(Map.of(
                "Voltage Measurement Filter", 1.0,
                "Real Power Filter", .40,
                "Reactive Power PI", 0.0,
                "Voltage Error PI", .10,
                "Reactive Current Lag", .10,
                "Power Order Lag", .40,
                "Battery Energy Output", 0.0), model.getNamedStates());

        model.step(1.0, .40, .10, 1.0, 1.0);
        assertEquals(.04, model.getEnergyOutput(), TOL);
        assertEquals(.56, model.getStateOfCharge(), TOL);
        assertThrows(UnsupportedOperationException.class,
                () -> model.getNamedStates().put("extra", 0.0));
    }

    @Test
    void supportsBidirectionalCurrentAndAppliesDirectionalSocStops() {
        Reecc1Model interior = new Reecc1Model(data(1, .50, .10, .90));
        interior.initialize(-.40, 0.0, 1.0);
        assertEquals(-.40, interior.getIpcmd(), TOL);

        Reecc1Model full = new Reecc1Model(data(1, .90, .10, .90));
        full.initialize(-.40, 0.0, 1.0);
        assertEquals(0.0, full.getIpcmd(), TOL);

        Reecc1Model empty = new Reecc1Model(data(1, .10, .10, .90));
        empty.initialize(.40, 0.0, 1.0);
        assertEquals(0.0, empty.getIpcmd(), TOL);
    }

    @Test
    void voltageDependentTableAndPriorityConstrainTheCurrentCircle() {
        Reecc1Model model = new Reecc1Model(data(0, .50, .10, .90));
        model.initialize(.90, .80, 1.0);

        assertEquals(-.80, model.getIqcmd(), TOL);
        assertEquals(.60, model.getIpcmd(), TOL);
        assertEquals(1.0, Math.hypot(model.getIpcmd(), model.getIqcmd()), TOL);
    }

    @Test
    void allZeroVoltageDependentTablesAreDisabled() {
        Reecc1Model model = new Reecc1Model(
                dataWithOverrides(1, 10.0, .50, .10, .90, false));
        model.initialize(.40, .10, 1.0);

        assertEquals(.40, model.getIpcmd(), TOL);
        assertEquals(-.10, model.getIqcmd(), TOL);
    }

    @Test
    void voltageDipFreezesPowerOrderButNotTheAlgebraicInjection() {
        Reecc1Model model = new Reecc1Model(data(1, .50, .10, .90));
        model.initialize(.40, 0.0, 1.0);
        model.setAuxiliaryPower(.10);

        model.step(.01, .20, 0.0, .80, 1.0);

        assertEquals(.40, model.getActivePowerOrder(), TOL);
        assertEquals(.10, model.getAuxiliaryPower(), TOL);
        assertTrue(model.getIqcmd() < 0.0);
    }

    @Test
    void rejectsInvalidSelectorsDischargeTimeAndSocRange() {
        assertThrows(IllegalArgumentException.class,
                () -> dataWithOverrides(2, 10.0, .5, .1, .9));
        assertThrows(IllegalArgumentException.class,
                () -> dataWithOverrides(1, 0.0, .5, .1, .9));
        assertThrows(IllegalArgumentException.class,
                () -> dataWithOverrides(1, 10.0, .95, .1, .9));
    }

    private static Reecc1Data data(int pqFlag, double initialSoc,
            double minimumSoc, double maximumSoc) {
        return dataWithOverrides(pqFlag, 10.0, initialSoc, minimumSoc, maximumSoc);
    }

    private static Reecc1Data dataWithOverrides(int pqFlag, double dischargeTime,
            double initialSoc, double minimumSoc, double maximumSoc) {
        return dataWithOverrides(pqFlag, dischargeTime, initialSoc, minimumSoc,
                maximumSoc, true);
    }

    private static Reecc1Data dataWithOverrides(int pqFlag, double dischargeTime,
            double initialSoc, double minimumSoc, double maximumSoc,
            boolean voltageDependentLimits) {
        double iqLimit = voltageDependentLimits ? .80 : 0.0;
        double ipLimit = voltageDependentLimits ? 1.0 : 0.0;
        double secondVoltage = voltageDependentLimits ? 2.0 : 0.0;
        return new Reecc1Data(
                0, 0, 1, 0, pqFlag,
                .90, 1.10, .02, -.05, .05,
                .50, .80, -.80, 1.0, .03,
                1.0, -1.0, 1.2, -1.2,
                .10, .20, 1.0, 5.0, .02,
                2.0, -2.0, 1.0, -1.0,
                1.0, .04,
                0.0, iqLimit, secondVoltage, iqLimit, 0.0, 0.0, 0.0, 0.0,
                0.0, ipLimit, secondVoltage, ipLimit, 0.0, 0.0, 0.0, 0.0,
                dischargeTime, initialSoc, maximumSoc, minimumSoc);
    }
}
