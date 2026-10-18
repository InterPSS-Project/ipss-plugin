package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Atomic startup equations independently observed in the commercial lag probe. */
public class PsseRenewableTwoStepIntegrationTest extends CorePluginTestSetup {
    private static final double H = .0005;

    @Test
    void commonOldEndpointMatchesTheTwoCommercialStartupSteps() throws Exception {
        Regca1Model converter = fixture();
        Reeca1Model electrical = converter.getReeca1Controller();
        assertEquals(.8775, electrical.getWindControlStack().getGeneratorSpeed(), 1.e-14);
        converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0);
        assertEquals(.6230859375, electrical.getActivePowerOrder(), 1.e-14);
        assertEquals(.625, converter.getIpRegulatorState(), 1.e-14,
                "converter derivative must see old Pord rather than advanced Pord");
        var states = electrical.getNamedStates();
        converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 1);
        assertEquals(states, electrical.getNamedStates(), "flag 1 cannot integrate again");
        // Algebraic event/network refreshes cannot consume previous derivatives.
        converter.nextStep(0.0, DynamicSimuMethod.MODIFIED_EULER, 0);
        converter.nextStep(0.0, DynamicSimuMethod.MODIFIED_EULER, 1);
        converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0);
        assertEquals(.6218577473958333, electrical.getActivePowerOrder(), 1.e-14);
        assertEquals(.62492822265625, converter.getIpRegulatorState(), 1.e-14);
        assertEquals(.625, electrical.getWindControlStack().getTorqueController().getFilteredPower(),
                1.e-14, "torque filter must still see the common old converter power");
    }

    @Test
    void voltageDipHoldsPowerOrderEvenWithNonzeroDerivativeHistory() throws Exception {
        Regca1Model converter = fixture();
        converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0);
        converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 1);
        Reeca1Model electrical = converter.getReeca1Controller();
        double held = electrical.getActivePowerOrder();
        converter.getDStabBus().setVoltage(new Complex(.5, 0.0));
        converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0);
        assertEquals(held, electrical.getActivePowerOrder(), 0.0);
        assertTrue(electrical.getMeasuredVoltage() < 1.0);
        assertTrue(electrical.isVoltageDip());
        var converterStates = converter.getNamedStates();
        var electricalStates = electrical.getNamedStates();
        for (int i = 0; i < 10; i++) converter.getOutputObject();
        assertEquals(converterStates, converter.getNamedStates());
        assertEquals(electricalStates, electrical.getNamedStates());
    }

    @Test
    void reinitializationClearsEveryControllerDerivativeHistory() throws Exception {
        Regca1Model converter = fixture();
        for (int i = 0; i < 6; i++) {
            converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0);
            converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 1);
        }
        assertTrue(converter.initStates(converter.getDStabBus()));
        converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0);
        assertEquals(.6230859375, converter.getReeca1Controller().getActivePowerOrder(), 1.e-14);
        assertEquals(.625, converter.getIpRegulatorState(), 1.e-14);
    }

    @Test
    void twoStepOptionRejectsUnsupportedControllerBeforeAdvancing() throws Exception {
        Regca1Model converter = fixture();
        converter.setReeca1Controller(null);
        assertThrows(IllegalStateException.class,
                () -> converter.nextStep(H, DynamicSimuMethod.MODIFIED_EULER, 0));
    }

    private static Regca1Model fixture() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        var bus = builder.getDStabNetwork().getDStabBus("Bus1");
        bus.setVoltage(Complex.ONE);
        DStabGen gen = (DStabGen)bus.getContributeGen("1");
        gen.setMvaBase(100.0);
        gen.setGen(new Complex(.625, 0.0));
        Regca1Model converter = builder.addRegca1("Bus1", "1", new Regca1Data(
                1, .02, 10, .9, .5, 1.22, 1.2, .8, .4, -1.3, .02, .7, 0, 0, .8));
        Reeca1Model electrical = new Reeca1Model(new Reeca1Data(
                0, 0, 1, 1, 1, 0, .85, 1.15, .02, 0, 0, 5, 1.1, -1.1,
                0, 0, 0, .5, .02, .436, -.436, 1.1, .9, 0, .1, 0, 39.5,
                0, .02, 99, -99, 1, 0, 1.3, .03,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), converter);
        WindControlStack wind = new WindControlStack();
        wind.setTorqueController(new Wttqa1Model(new Wttqa1Data(
                1, 2.4, .5, .04, 60, 1.002, 0, .2, .58, .4, .72, .6, .86, .8, 1, 0)));
        electrical.setWindControlStack(wind);
        electrical.setWindSpeedConvention(Reeca1Model.WindSpeedConvention.PSSE_GENERATOR_SPEED);
        converter.setReeca1Controller(electrical);
        converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);
        assertTrue(converter.initStates(bus));
        return converter;
    }
}
