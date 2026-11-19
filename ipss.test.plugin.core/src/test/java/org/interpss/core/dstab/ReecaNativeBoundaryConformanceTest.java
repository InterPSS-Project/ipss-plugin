package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.interpss.dstab.renewable.Reeca1Data;
import com.interpss.dstab.renewable.Reeca1Model;
import org.junit.jupiter.api.Test;

/** Native bias and documented voltage/current table boundaries. */
public class ReecaNativeBoundaryConformanceTest {
    static Reeca1Data data(int pf,int vf,int qf,int priority,double bias,boolean descending) {
        double[] q=descending?new double[]{-1,.1,.5,.2,1,.15,2,.05}:new double[]{-1,.1,2,.1,0,0,0,0};
        return new Reeca1Data(0,pf,vf,qf,0,priority,.85,1.15,.02,-.01,.01,2,1.1,-1.1,0,0,0,0,.02,.6,-.6,1.2,.8,1,5,1,10,bias,.02,99,-99,1,0,.26,.02,
                q[0],q[1],q[2],q[3],q[4],q[5],q[6],q[7],-1,.25,2,.25,0,0,0,0);
    }

    @Test void powerFactorVoltageModeCalculatesItsBiasInsteadOfUsingTheIgnoredEnteredValue() {
        var model=Reeca1Model.fromNativeRecord(data(1,0,1,0,0,false),null);model.initialize(.24,.0474445894361,1.01);
        model.step(.0005,.24,.0474445894361,1.01,1);
        assertEquals(0,model.getVoltageControlError(),1e-12,"Native calculated bias must retain a stationary flat operating point");
        assertEquals(-.0474445894361/1.01,model.getIqcmd(),1e-12);
    }

    @Test void twoPointReactiveCurveIsActiveWhenTrailingPairsAreUnused() {
        var model=driven(0,false);
        assertEquals(.1,model.getReactiveCurrentLimit(),1e-12);
        assertEquals(.1,Math.abs(model.getIqcmd()),1e-12);
        assertEquals(.24,model.getActiveCurrentLimit(),1e-12);
    }

    @Test void twoPointActiveCurveParticipatesInRealPowerPriority() {
        var model=driven(1,false);
        assertEquals(.25,model.getActiveCurrentLimit(),1e-12);
        assertEquals(.25,model.getIpcmd(),1e-12);
        assertEquals(Math.sqrt(.26*.26-.25*.25),model.getReactiveCurrentLimit(),1e-12);
    }

    @Test void decreasingCurrentOrdinatesRemainAValidVoltageSortedCurve() {
        var model=driven(0,true);double expected=.2-.1*(model.getMeasuredVoltage()-.5);
        assertEquals(expected,model.getReactiveCurrentLimit(),1e-12);
        assertEquals(expected,Math.abs(model.getIqcmd()),1e-12);
    }

    @Test void nativeObservationsAreDefensiveReadOnlyAndSelectOnlyActiveCoordinates() {
        var model=Reeca1Model.fromNativeRecord(data(1,0,1,0,0,false),null);
        model.initialize(.24,.0474445894361,1.01);
        var named=model.getNamedStates();var memory=model.getControlMemory();
        var states=model.getNativeStates();states[0]=99;
        assertEquals(1.01,model.getNativeStates()[0],0);
        assertEquals(0,model.getNativeStates()[2],0);assertEquals(0,model.getNativeStates()[4],0);
        assertEquals(.9625554105639,model.getInitializedVoltageBias(),1e-14);
        assertEquals(List.of("Measured Voltage","Measured Active Power","Voltage Control Integral","Active Power Order"),
                new java.util.ArrayList<>(model.getIntegrationStates().keySet()));
        assertThrows(UnsupportedOperationException.class,()->model.getIntegrationStates().put("Measured Voltage",0.));
        assertEquals(named,model.getNamedStates());assertEquals(memory,model.getControlMemory());
        assertThrows(IllegalStateException.class,()->new Reeca1Model(data(0,0,1,0,0,false),null).getNativeStates());
    }

    private static Reeca1Model driven(int priority,boolean descending) {
        var model=new Reeca1Model(data(0,1,0,priority,0,descending),null);model.initialize(.24,.0474445894361,1.01);
        for(int i=0;i<200;i++)model.step(.0005,.24,.0474445894361,.76,1);
        return model;
    }
}
