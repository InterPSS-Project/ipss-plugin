package org.interpss.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Nonnegative finite multiplier contract; the product limit belongs to the runtime. */
class GenqecFieldCompensationDataTest {
    private static GenqecData data(double kw){return new GenqecData(4.4,.07,0,2.05,1.74,.34,.49,.089,.12,.05,6.4,.68,.032,.043,.05,.24,0,0,0,kw,1);}
    @Test void everyFiniteNonnegativeMultiplierIsRetainedWithoutAParameterCap(){
        for(double kw:new double[]{0,.25,Math.nextDown(1.),1.,1.5,Double.MAX_VALUE})assertEquals(kw,assertDoesNotThrow(()->data(kw)).kw(),0);
    }
    @Test void nonfiniteAndNegativeMultipliersAreRejected(){
        for(double kw:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1,-Double.MIN_VALUE})assertThrows(IllegalArgumentException.class,()->data(kw));
    }
}
