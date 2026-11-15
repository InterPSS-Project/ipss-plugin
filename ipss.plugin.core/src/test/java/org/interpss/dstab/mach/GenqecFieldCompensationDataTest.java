package org.interpss.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Signed finite multiplier contract; the bounded product belongs to the runtime. */
class GenqecFieldCompensationDataTest {
    private static GenqecData data(double kw){return new GenqecData(4.4,.07,0,2.05,1.74,.34,.49,.089,.12,.05,6.4,.68,.032,.043,.05,.24,0,0,0,kw,1);}
    @Test void everyFiniteSignedMultiplierIsRetainedWithoutAParameterCap(){
        for(double kw:new double[]{-Double.MAX_VALUE,-1,-.15,-Double.MIN_VALUE,0,.25,Math.nextDown(1.),1.,1.5,Double.MAX_VALUE})assertEquals(kw,assertDoesNotThrow(()->data(kw)).kw(),0);
    }
    @Test void nonfiniteMultipliersAreRejected(){
        for(double kw:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->data(kw));
    }
}
