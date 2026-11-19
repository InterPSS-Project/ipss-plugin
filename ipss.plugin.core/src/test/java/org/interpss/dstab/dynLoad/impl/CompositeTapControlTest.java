package org.interpss.dstab.dynLoad.impl;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CompositeTapControlTest {
    private CompositeTapControl model() {
        var m=new CompositeTapControl(.91,1.09,.005,.01,.01); m.initialize(1.05); return m;
    }
    @Test void independentDelaysAndIncrementalBounds() {
        for (int direction : new int[]{-1,1}) {
            var m=model(); double v=direction>0 ? .9 : 1.2;
            for(int n=1;n<40;n++) { m.advance(.0005,v,1.015,1.045); assertEquals(1.05,m.tap()); }
            m.advance(.0005,v,1.015,1.045); assertEquals(1.05+direction*.005,m.tap(),1e-14);
            double old=m.tap();
            for(int n=0;n<1000;n++) {
                m.advance(.0005,v,1.015,1.045); assertTrue(Math.abs(m.tap()-old)<.00500000001); old=m.tap();
            }
            assertEquals(direction>0 ? 1.09 : .91,m.tap(),1e-14);
        }
    }
    @Test void returnToBandCancelsPendingActionWithoutBoundJump() {
        var m=model();
        for(int i=0;i<39;i++)m.advance(.0005,.9,1.015,1.045);
        assertTrue(Double.isFinite(m.due()));
        for(int i=0;i<100;i++)m.advance(.0005,1.03,1.015,1.045);
        assertEquals(1.05,m.tap()); assertEquals(0,m.control()); assertFalse(Double.isFinite(m.due()));
    }
    @Test void reversalUsesCurrentDirectionAndRetainsControlMemory() {
        var m=model(); for(int i=0;i<39;i++)m.advance(.0005,.9,1.015,1.045);
        m.advance(.0005,1.2,1.015,1.045);
        assertEquals(1.045,m.tap(),1e-14); assertEquals(.95,m.control(),1e-14);
        for(int i=0;i<20;i++)m.advance(.0005,1.2,1.015,1.045);
        assertEquals(1.045,m.tap(),1e-14);
    }
    @Test void zeroIntervalsAndSnapshotRestoreDoNotLeakHistory() {
        var m=model(); var saved=model();
        for(int i=0;i<25;i++)m.advance(.0005,.9,1.015,1.045);
        saved.copyFrom(m); double t=m.time(),due=m.due();
        for(int i=0;i<100;i++)m.advance(0,1.2,1.015,1.045);
        assertEquals(t,m.time()); assertEquals(due,m.due());
        for(int i=0;i<30;i++)m.advance(.0005,.9,1.015,1.045);
        assertNotEquals(saved.tap(),m.tap()); m.copyFrom(saved);
        assertEquals(saved.tap(),m.tap()); assertEquals(saved.due(),m.due()); assertEquals(saved.time(),m.time());
    }
    @Test void zeroDelaysStillPermitOnlyOneBoundedStepPerInterval() {
        var m=new CompositeTapControl(.9,1.1,.005,0,0);m.initialize(1);
        m.advance(.1,.8,1,1.02);assertEquals(1.005,m.tap());
        assertThrows(IllegalArgumentException.class,()->m.advance(Double.NaN,.8,1,1.02));
        assertThrows(IllegalArgumentException.class,()->new CompositeTapControl(.9,1.1,.005,-1,0));
    }
}
