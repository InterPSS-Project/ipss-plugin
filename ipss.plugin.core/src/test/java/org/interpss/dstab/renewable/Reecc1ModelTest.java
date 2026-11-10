package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class Reecc1ModelTest {
    private static final double TOL = 1.0e-10;

    @Test void ordinaryStagesAdvanceAuxiliaryOnceAndDoNotDoubleCountBatteryEnergy() {
        var model=new Reecc1Model(dataWithOverrides(1,10,.5,.1,.9,false));
        var auxiliary=new org.interpss.dstab.mach.Reaxbu1Model("REAX4BU1",
                new org.interpss.dstab.mach.Reaxbu1Data(1,.02,.8,.7,2,-2,2,-2));
        model.setAuxiliaryController(auxiliary);model.initialize(.4,.1,1);
        auxiliary.setExternalSignals(.05,.02);
        model.step(.001,.4,.1,1,1,0);
        assertEquals(.102,auxiliary.getReactiveOutput(),TOL);
        assertEquals(.4007,auxiliary.getActiveOutput(),TOL);
        double energy=model.getEnergyOutput();assertEquals(.00004,energy,TOL);
        model.step(.001,.4,.1,1,1,1);
        assertEquals(.10195,auxiliary.getReactiveOutput(),TOL);
        assertEquals(.4006825,auxiliary.getActiveOutput(),TOL);
        assertEquals(energy,model.getEnergyOutput(),0);
        model.initialize(.3,.2,1);
        assertThrows(IllegalArgumentException.class,()->model.step(.001,.3,.2,1,1,2));
        model.setTwoStepAuxiliaryManaged(true);
        assertThrows(IllegalStateException.class,()->model.step(.001,.3,.2,1,1,0));
    }

    @Test void auxiliaryReferencesUseAbsoluteCoordinatesAndRequireSharedStateOwnership() {
        var model=new Reecc1Model(dataWithOverrides(1,10,.5,.1,.9,false));
        var auxiliary=new org.interpss.dstab.mach.Reaxbu1Model("REAX4BU1",
                new org.interpss.dstab.mach.Reaxbu1Data(1,.02,.8,0,2,-2,2,-2));
        model.setAuxiliaryController(auxiliary);model.initialize(.4,.1,1);
        assertEquals(.1,auxiliary.getReactiveOutput(),TOL);assertEquals(.4,auxiliary.getActiveOutput(),TOL);
        assertThrows(UnsupportedOperationException.class,()->model.step(.001,.4,.1,1,1));
        assertThrows(UnsupportedOperationException.class,()->model.refreshTwoStep(.4,.1,1,1));
        model.setTwoStepAuxiliaryManaged(true);
        auxiliary.setExternalSignals(.05,100);auxiliary.step(.001,0);auxiliary.step(.001,1);
        double q=auxiliary.getReactiveOutput();assertTrue(q>.1);
        for(int i=0;i<20;i++)model.refreshTwoStep(.4,.1,1,1);
        assertEquals(q,auxiliary.getReactiveOutput(),0,"Refresh cannot advance shared states");
        model.stepTwoStep(.001,.4,.1,1,1);
        assertEquals(.4,model.getActivePowerOrder(),TOL);
        assertTrue(model.getReactiveCurrentState()>.1);
        assertEquals(9,model.getNamedStates().size());
        model.initialize(.3,.2,1);
        assertEquals(.3,auxiliary.getActiveOutput(),0);assertEquals(.2,auxiliary.getReactiveOutput(),0);
        assertThrows(UnsupportedOperationException.class,()->model.refreshTwoStep(.3,.2,1,1));
    }

    @Test
    void exposesAllSevenPublishedStatesAndIntegratesBatteryEnergy() {
        Reecc1Model model = new Reecc1Model(data(1, .60, .10, .90));
        model.initialize(.40, .10, 1.0);

        assertEquals(Map.of(
                "Voltage Measurement Filter", 1.0,
                // The power-factor branch is disconnected with PFFLAG=0.
                "Real Power Filter", 0.0,
                "Reactive Power PI", 0.0,
                "Voltage Error PI", .10,
                "Reactive Current Lag", .10,
                "Power Order Lag", .40,
                "Battery Energy Output", 0.0), model.getNamedStates());

        model.step(1.0, .40, .10, 1.0, 1.0);
        assertEquals(0.0, model.getNamedStates().get("Real Power Filter"), TOL);
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
