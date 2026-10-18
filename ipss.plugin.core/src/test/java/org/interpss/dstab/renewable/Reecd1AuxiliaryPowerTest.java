package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Reecd1AuxiliaryPowerTest {
    private static Reecd1Model controller(int priority,double imax){
        double[] table=new double[10];
        return new Reecd1Model(new Reecd1Data(0,0,0,0,priority,0,
                .2,2,0,0,0,0,1,-1,1,0,0,0,0,2,-2,2,-2,0,0,0,0,0,0,100,-100,
                2,-2,imax,.02,0,0,0,0,0,0,3,0,table,table,table,table));
    }
    @Test void positiveAndNegativeAuxiliaryPowerAreConvertedAtTheMeasuredVoltage(){
        for(double voltage:new double[]{.5,.8,1.2})for(double paux:new double[]{-.1,.1}){
            var m=controller(1,2);m.initialize(.3,0,1);m.setAuxiliaryPower(paux);m.step(.001,.3,0,voltage,1);
            assertEquals((.3+paux)/voltage,m.getIpcmd(),1e-14);
            assertEquals(.3,m.getNamedStates().get("Pord"),1e-14,"Paux must not alter the power-order lag");
        }
    }
    @Test void pauxParticipatesInPreliminaryActivePriorityCurrentLimits(){
        var m=controller(1,1);m.initialize(.2,.3,1);m.setAuxiliaryPower(.4);m.step(.001,.2,.3,.8,1);
        assertEquals(.75,m.getIpcmd(),1e-14);
        assertEquals(Math.sqrt(1-.75*.75),m.getReactiveCurrentMaximum(),1e-14);
    }
    @Test void finalCurrentLimitStillAppliesToTheCombinedPower(){
        var m=controller(1,1);m.initialize(.2,0,1);m.setAuxiliaryPower(1);m.step(.001,.2,0,.8,1);
        assertEquals(1,m.getIpcmd(),0);assertEquals(.2,m.getNamedStates().get("Pord"),0);
    }
    @Test void zeroAuxiliaryPowerPreservesTheOldCommandAndNonfiniteInputsAreRejected(){
        var m=controller(1,2);m.initialize(.3,0,1);m.step(.001,.3,0,.8,1);
        assertEquals(.3/.8,m.getIpcmd(),0);
        assertThrows(IllegalArgumentException.class,()->m.setAuxiliaryPower(Double.NaN));
    }
    @Test void algebraicAuxiliaryInputRefreshesCurrentWithoutAdvancingStates(){
        var m=controller(1,2);m.initialize(.3,0,1);m.step(.001,.3,0,.8,1);
        var before=m.getNamedStates();m.setAuxiliaryPower(.1);
        assertEquals(.5,m.getIpcmd(),1e-14);assertEquals(before,m.getNamedStates());
        m.setAuxiliaryPower(0);assertEquals(.375,m.getIpcmd(),1e-14);assertEquals(before,m.getNamedStates());
    }
    @Test void releasingActivePriorityRestoresThePrelimitReactiveRequest(){
        var m=controller(1,1);m.initialize(.6,.6,1);var before=m.getNamedStates();
        m.setAuxiliaryPower(.3);assertEquals(.9,m.getIpcmd(),1e-14);
        assertEquals(-Math.sqrt(1-.9*.9),m.getIqcmd(),1e-14);
        m.setAuxiliaryPower(0);assertEquals(-.6,m.getIqcmd(),1e-14);
        assertEquals(before,m.getNamedStates());
    }
}
