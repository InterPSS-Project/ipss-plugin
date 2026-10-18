package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Wtptb1ModelTest {
    private static Wtptb1Data data(double maximum, double minimum) {
        return new Wtptb1Data(29,177,23.72,2.93,.25,.3,maximum,minimum,10,-10,5,-5,25,-25);
    }

    @Test void zeroErrorEquilibriumHasPureImmutableObservations() {
        var model = new Wtptb1Model(data(27,0));
        model.initialize(2);
        var initial = model.getNativeStates();
        for (int i=0; i<1000; i++) model.step(.001,.24,.24,.88,.88);
        assertArrayEquals(initial,model.getNativeStates(),0);
        assertEquals(3,model.getNamedStates().size());
        assertThrows(UnsupportedOperationException.class,()->model.getNamedStates().put("extra",0.));
        double[] copy=model.getNativeStates(); copy[0]=999;
        assertEquals(2,model.getPitch());
    }

    @Test void predictorAndCorrectorUseBothInputEndpoints() {
        var model=new Wtptb1Model(data(27,0)); model.initialize(2);
        model.step(.001,.34,.24,1.02,1.,0);
        assertArrayEquals(new double[]{2.01,2.001305,.002372},model.getNativeStates(),1e-13);
        // Endpoint errors are deliberately different, not an extra Euler step.
        model.step(.001,.24,.24,1.,1.,1);
        double endPitchRate=(2.001305+.002372-2.01)/.3;
        assertArrayEquals(new double[]{2+.0005*(10+endPitchRate),2+.0005*1.305,.0005*2.372},
                model.getNativeStates(),1e-13);
        assertThrows(IllegalStateException.class,()->model.step(.001,.24,.24,1.,1.,1));
    }

    @Test void clippedPiOutputDoesNotFreezeAnInteriorIntegrator() {
        var model=new Wtptb1Model(new Wtptb1Data(1,200,0,0,0,.3,27,0,10,-10,5,-5,3,0));
        model.initialize(2);
        model.step(.001,.24,.24,1.02,1.);
        assertEquals(2.00002,model.getNativeStates()[1],1e-14,
                "Own anti-windup uses integral state, not the already clipped PI output");
        assertTrue(model.getPitch()>2);
    }

    @Test void independentPiStateLimitsAllowInwardRelease() {
        var model=new Wtptb1Model(new Wtptb1Data(29,177,23.72,2.93,.25,.3,27,0,10,-10,.01,-.01,2.01,1.99));
        model.initialize(2);
        for(int i=0;i<100;i++)model.step(.001,1.24,.24,1.,1.);
        assertEquals(2.01,model.getNativeStates()[1]);
        assertEquals(.01,model.getNativeStates()[2]);
        model.step(.001,.239,.24,1.,1.);
        assertTrue(model.getNativeStates()[1]<2.01);
        assertTrue(model.getNativeStates()[2]<.01);
    }

    @Test void upperPitchLimitStopsOnlyOutwardIntegralDirections() {
        var model=new Wtptb1Model(data(2,0)); model.initialize(2);
        model.step(.001,.34,.24,1.,1.);
        assertArrayEquals(new double[]{2,2,0},model.getNativeStates(),0);
        model.step(.001,.14,.24,1.1,1.);
        assertEquals(2,model.getNativeStates()[1],0,"Positive speed derivative stays blocked");
        assertTrue(model.getNativeStates()[2]<0,"Negative compensation derivative is not frozen");
        model.step(.001,.14,.24,1.,1.);
        assertTrue(model.getPitch()<2);
        assertTrue(model.getNativeStates()[1]<2);
    }

    @Test void lowerPitchLimitStopsOnlyOutwardIntegralDirections() {
        var model=new Wtptb1Model(data(27,0)); model.initialize(0);
        model.step(.001,.14,.24,1.,1.);
        assertArrayEquals(new double[]{0,0,0},model.getNativeStates(),0);
        model.step(.001,.34,.24,.9,1.);
        assertEquals(0,model.getNativeStates()[1],0);
        assertTrue(model.getNativeStates()[2]>0);
        model.step(.001,.34,.24,1.,1.);
        assertTrue(model.getPitch()>0);
    }

    @Test void speedReferenceIsAnInputAndRateLimitsDoNotFreezeThePiStates() {
        var model=new Wtptb1Model(data(27,0)); model.initialize(2);
        model.step(.001,.24,.24,1.,.9);
        assertEquals(2.01,model.getPitch(),1e-14);
        assertEquals(2.0029,model.getNativeStates()[1],1e-14);
        model.step(.001,.24,.24,1.,1.1);
        assertTrue(model.getPitch()<2.01);
    }

    @Test void summedCommandIsNotClippedBeforeTheServoLag() {
        var model=new Wtptb1Model(new Wtptb1Data(0,0,0,1,0,1,2.1,0,10,-10,5,-5,25,0));
        model.initialize(2);
        model.step(.001,1.24,.24,1.,1.,0);
        assertEquals(2.001,model.getPitch(),1e-14,
                "Clipping the target at 2.1 before the lag would incorrectly give 2.0001");
    }

    @Test void reinitializationDiscardsPredictorAndZeroStepDoesNotAdvanceStates() {
        var model=new Wtptb1Model(data(27,0));
        assertThrows(IllegalStateException.class,()->model.step(.001,0,0,1,1));
        model.initialize(2);model.step(.001,.34,.24,1,1,0);model.initialize(3);
        assertThrows(IllegalStateException.class,()->model.step(.001,.34,.24,1,1,1));
        model.step(0,99,0,2,1);
        assertArrayEquals(new double[]{3,3,0},model.getNativeStates());
        assertThrows(IllegalArgumentException.class,()->model.step(-1,0,0,1,1));
        assertThrows(IllegalArgumentException.class,()->model.step(.1,Double.NaN,0,1,1));
        assertThrows(IllegalArgumentException.class,()->model.step(.1,0,0,1,1,2));
    }

    @Test void invalidParametersAndInitialLimitViolationsAreExplicit() {
        assertThrows(IllegalArgumentException.class,()->new Wtptb1Data(1,1,1,1,0,0,27,0,10,-10,5,-5,25,0));
        assertThrows(IllegalArgumentException.class,()->new Wtptb1Data(Double.NaN,1,1,1,0,.3,27,0,10,-10,5,-5,25,0));
        assertThrows(IllegalArgumentException.class,()->new Wtptb1Data(1,1,1,1,0,.3,27,0,0,-10,5,-5,25,0));
        assertThrows(IllegalArgumentException.class,()->new Wtptb1Data(1,1,1,1,0,.3,27,0,10,-10,-5,5,25,0));
        var model=new Wtptb1Model(data(27,0));
        assertThrows(IllegalArgumentException.class,()->model.initialize(-1));
        assertThrows(IllegalArgumentException.class,()->model.initialize(26));
    }
}
