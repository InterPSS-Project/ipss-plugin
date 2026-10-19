package org.interpss.dstab.control.pss.psse.ieeest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IeeestVoltageDerivativeTest {
    @Test void smoothingIsSampledRatherThanAPhysicalTimeConstant() {
        for (double step : new double[]{.0005, .00025}) {
            var d = new IeeestVoltageDerivative(); d.initialize(1);
            d.accept(step, 1+2*step, step);
            assertEquals(.4, d.getDerivative(), 1e-12);
            d.accept(2*step, 1+step, step);
            assertEquals(.12, d.getDerivative(), 1e-12);
        }
    }

    @Test void eventSynchronizationDoesNotDifferentiateAnAlgebraicJump() {
        var d = new IeeestVoltageDerivative(); d.initialize(1);
        d.accept(.001, 1.001, .001); double held = d.getDerivative();
        d.synchronizeVoltage(.6);
        assertEquals(held, d.getDerivative(), 0);
        assertEquals(.001, d.getAcceptedTime(), 0);
        d.accept(.002, .601, .001);
        assertEquals(.8*held+.2, d.getDerivative(), 1e-12);
    }

    @Test void duplicatesZeroDurationAndReadsCannotAdvanceMemory() {
        var d = new IeeestVoltageDerivative(); d.initialize(1);
        d.accept(.001, 1.001, .001);
        double held=d.getDerivative(), memory=d.getVoltage();
        for (int i=0;i<10;i++) { d.accept(.001, .5, .001); d.accept(.002, .5, 0); }
        assertEquals(held,d.getDerivative(),0); assertEquals(memory,d.getVoltage(),0);
        assertEquals(.001,d.getAcceptedTime(),0);
    }

    @Test void reinitializationClearsDerivativeAndEndpointClock() {
        var d = new IeeestVoltageDerivative(); d.initialize(1); d.accept(.001,.99,.001);
        d.initialize(.8);
        assertEquals(.8,d.getVoltage(),0);assertEquals(0,d.getDerivative(),0);assertEquals(0,d.getAcceptedTime(),0);
    }

    @Test void invalidEndpointsRejectWithoutChangingStorage() {
        var d = new IeeestVoltageDerivative(); d.initialize(1);d.accept(.001,.99,.001);
        double held=d.getDerivative();
        assertThrows(IllegalArgumentException.class,()->d.accept(.0005,1,.001));
        assertThrows(IllegalArgumentException.class,()->d.accept(.002,1,-.001));
        assertThrows(IllegalArgumentException.class,()->d.accept(.002,Double.NaN,.001));
        assertThrows(IllegalArgumentException.class,()->d.synchronizeVoltage(Double.POSITIVE_INFINITY));
        assertEquals(held,d.getDerivative(),0);assertEquals(.99,d.getVoltage(),0);
    }
}
