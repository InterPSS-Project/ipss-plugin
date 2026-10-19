package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.renewable.Wtara1Data;
import org.interpss.dstab.renewable.Wtara1Model;
import org.junit.jupiter.api.Test;

/** Algebraic power, finite-input boundaries and reinitialization without invented states. */
public class WtaraAlgebraicConformanceTest {
    @Test void nonfiniteParametersAreRejectedWithoutChangingTheNativeCardinality() {
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new Wtara1Data(invalid,0));
            assertThrows(IllegalArgumentException.class,()->new Wtara1Data(.007,invalid));
        }
        assertThrows(IllegalArgumentException.class,()->new Wtara1Data(-.1,0));
        assertEquals(2,Wtara1Data.class.getRecordComponents().length);
    }

    @Test void nonfiniteBoundaryInputsFailBeforeMutatingTheAlgebraicOutput() {
        var model=new Wtara1Model(new Wtara1Data(.007,5));model.initialize(.24);model.step(7);
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->model.step(invalid));
            assertEquals(7,model.getPitch(),0);assertEquals(.142,model.getMechanicalPower(),1e-15);
            assertThrows(IllegalArgumentException.class,()->model.initialize(invalid));
            assertEquals(7,model.getPitch(),0);assertEquals(.142,model.getMechanicalPower(),1e-15);
        }
    }

    @Test void publishedProductUsesPitchTimesDeviationInDegreesAndDoesNotClipPower() {
        var model=new Wtara1Model(new Wtara1Data(.007,5));model.initialize(.24);
        for(double pitch:new double[]{5,7,3,0,-1,9,27,5}) {
            model.step(pitch);assertEquals(.24-.007*pitch*(pitch-5),model.getMechanicalPower(),1e-14);
            assertEquals(pitch,model.getPitch(),0);
        }
        model.step(9);assertTrue(model.getMechanicalPower()<0,"No undocumented zero-power clamp");
    }

    @Test void zeroGainAndReinitializationRetainTheDeclaredInitialMechanicalPower() {
        var model=new Wtara1Model(new Wtara1Data(0,5));model.initialize(.23264);model.step(27);
        assertEquals(.23264,model.getMechanicalPower(),0);
        model.initialize(.31);assertEquals(5,model.getPitch(),0);assertEquals(.31,model.getMechanicalPower(),0);
        assertEquals(.31,model.getInitialPower(),0);
        for(int i=0;i<100;i++)assertEquals(.31,model.getMechanicalPower(),0);
    }
}
