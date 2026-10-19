package org.interpss.dstab.control.pss.psse.ieeest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;

class IeeestTerminalBlockTest {
    @Test void positiveAndNegativeNumeratorsRetainTheCoreWashoutBitForBit() {
        for(double gain:new double[]{.5,2,-.5}) {
            var expected=new WashoutControlBlock(gain,.4);var actual=new IeeestTerminalBlock(gain,.4,gain*.4);
            assertTrue(expected.initStateU0(.3));assertTrue(actual.initStateU0(.3));
            for(int step=0;step<2000;step++) {
                double input=.3+.02*Math.sin(step*.01);expected.eulerStep1(input,.0005);actual.eulerStep1(input,.0005);same(expected,actual);
                input=.3+.02*Math.sin((step+1)*.01);expected.eulerStep2(input,.0005);actual.eulerStep2(input,.0005);same(expected,actual);
            }
        }
    }
    @Test void zeroNumeratorUsesTheCoreLagRecurrenceBitForBit() {
        var expected=new DelayControlBlock(1,.2);var actual=new IeeestTerminalBlock(0,.2,0);
        assertTrue(expected.initStateY0(-.3));assertTrue(actual.initStateY0(-.3));assertTrue(actual.isZeroNumeratorLag());
        for(int step=0;step<2000;step++) {
            double input=.02*Math.cos(step*.01);expected.eulerStep1(input,.0005);actual.eulerStep1(input,.0005);
            assertEquals(expected.getStateX(),actual.getStateX(),0);assertEquals(expected.getY(),actual.getY(),0);
            expected.eulerStep2(input,.0005);actual.eulerStep2(input,.0005);
            assertEquals(expected.getStateX(),actual.getStateX(),0);assertEquals(expected.getDX_dt(),actual.getDX_dt(),0);assertEquals(expected.getY(),actual.getY(),0);
        }
    }
    @Test void lagInitializationRetainsNonzeroDcInputs() {
        var block=new IeeestTerminalBlock(0,5,0);assertTrue(block.initStateU0(2.5));assertEquals(2.5,block.getY(),0);
        for(int step=0;step<100;step++){block.eulerStep1(2.5,.0005);block.eulerStep2(2.5,.0005);assertEquals(2.5,block.getY(),0);}
        assertTrue(block.initStateY0(-1));assertEquals(-1,block.getU0(),0);assertEquals(-1,block.getY(),0);
    }
    @Test void ordinaryNonzeroOutputInitializationRemainsRejected() {
        var block=new IeeestTerminalBlock(1,5,5);
        assertThrows(RuntimeException.class,()->block.initStateY0(.1));assertTrue(block.initStateY0(0));assertFalse(block.isZeroNumeratorLag());
    }
    @Test void theSpecialConventionUsesExactZeroNotASmallValueThreshold() {
        var zero=new IeeestTerminalBlock(0,.4,0);var small=new IeeestTerminalBlock(1e-12,.4,4e-13);
        assertTrue(zero.isZeroNumeratorLag());assertFalse(small.isZeroNumeratorLag());
        assertTrue(zero.initStateU0(1));assertTrue(small.initStateU0(1));assertEquals(1,zero.getY(),0);assertEquals(0,small.getY(),0);
    }
    private static void same(WashoutControlBlock expected,IeeestTerminalBlock actual) {
        assertEquals(Double.doubleToLongBits(expected.getStateX()),Double.doubleToLongBits(actual.getStateX()));
        assertEquals(Double.doubleToLongBits(expected.getDX_dt()),Double.doubleToLongBits(actual.getDX_dt()));
        assertEquals(Double.doubleToLongBits(expected.getY()),Double.doubleToLongBits(actual.getY()));
    }
}
