package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class Reecd1InertialResponseTest {
    private static Reecd1Model controller() {
        double[] table=new double[10];
        return new Reecd1Model(new Reecd1Data(0,0,0,0,1,0,
                .2,2,0,0,0,0,1,-1,1,0,0,0,0,2,-2,2,-2,0,0,0,0,0,0,100,-100,
                2,-2,2,.02,0,0,0,0,0,0,3,0,table,table,table,table));
    }
    private static WtgibffraModel inertia(double filter) {
        return new WtgibffraModel(new WtgibffraData(0,.005,
                Collections.nCopies(6,new WtgibffraData.Band(.1,.1,.05,.1,.1,.1,1.2)),filter,.2));
    }
    private static WtgibffraModel validInertia(double filter) {
        var bands=new ArrayList<WtgibffraData.Band>();
        for(int i=1;i<=6;i++)bands.add(new WtgibffraData.Band(i*.1,.1,.05,.1,.1,.1,1.2));
        return new WtgibffraModel(new WtgibffraData(0,.005,bands,filter,.2));
    }
    @Test void correctedPauxIsAlgebraicAndDoesNotAdvanceReecdStatesTwice() {
        var m=controller();var aux=validInertia(0);m.setInertialResponseController(aux,()->.98);m.initialize(.3,0,1);
        m.step(.001,.3,0,.8,1,0);var predictor=m.getNamedStates();double predicted=m.getAuxiliaryPower();
        m.step(.001,.3,0,.8,1,1);
        assertEquals(predictor,m.getNamedStates());assertEquals(predicted,m.getAuxiliaryPower(),0);
        assertEquals((.3+aux.getAuxiliaryPower())/.8,m.getIpcmd(),1e-14);
        assertEquals(.3,m.getNamedStates().get("Pord"));assertTrue(aux.getAuxiliaryPower()>0);
    }
    @Test void provisionalFrequencyTriggerIsRolledBackAndCorrectorRefreshesCommand() {
        var m=controller();var aux=validInertia(.1);double[] frequency={1};
        m.setInertialResponseController(aux,()->frequency[0]);m.initialize(.3,0,1);
        frequency[0]=.9;m.step(.01,.3,0,1,1,0);assertEquals(WtgibffraModel.Mode.RISE,aux.getMode());
        var before=m.getNamedStates();frequency[0]=1;m.step(.01,.3,0,1,1,1);
        assertEquals(WtgibffraModel.Mode.READY,aux.getMode());assertEquals(0,m.getAuxiliaryPower());
        assertEquals(.3,m.getIpcmd(),1e-14);assertEquals(before,m.getNamedStates());
    }
    @Test void singleCallAndStagedIntegrationAgreeForConstantMeasuredFrequency() {
        var a=controller();var b=controller();var ia=validInertia(.02);var ib=validInertia(.02);
        a.setInertialResponseController(ia,()->.98);b.setInertialResponseController(ib,()->.98);
        a.initialize(.3,0,1);b.initialize(.3,0,1);
        for(int i=0;i<500;i++) {
            a.step(.0005,.3,0,1,1);b.step(.0005,.3,0,1,1,0);b.step(.0005,.3,0,1,1,1);
            assertArrayEquals(ia.getNativeStates(),ib.getNativeStates(),0);assertEquals(a.getIpcmd(),b.getIpcmd(),0);
        }
    }
    @Test void initializationClearsPreviousResponseAndReinitializesFrequency() {
        var m=controller();var aux=validInertia(0);double[] frequency={.98};
        m.setInertialResponseController(aux,()->frequency[0]);m.initialize(.3,0,1);m.step(.03,.3,0,1,1);
        assertTrue(m.getAuxiliaryPower()>0);frequency[0]=1;m.initialize(.4,0,1.2);
        assertEquals(0,m.getAuxiliaryPower());assertEquals(WtgibffraModel.Mode.READY,aux.getMode());
        assertArrayEquals(new double[]{0,0,0,1},aux.getNativeStates(),0);assertEquals(.4/1.2,m.getIpcmd(),1e-14);
    }
    @Test void attachmentIsAtomicAndCannotReplaceAnInitializedOrExistingController() {
        var m=controller();assertThrows(NullPointerException.class,()->m.setInertialResponseController(validInertia(0),null));
        assertNull(m.getInertialResponseController());var aux=validInertia(0);m.setInertialResponseController(aux,()->1);
        assertThrows(IllegalStateException.class,()->m.setInertialResponseController(validInertia(0),()->1));
        assertSame(aux,m.getInertialResponseController());var initialized=controller();initialized.initialize(.3,0,1);
        assertThrows(IllegalStateException.class,()->initialized.setInertialResponseController(validInertia(0),()->1));
    }
    @Test void disabledInertiaPreservesExistingElectricalBehavior() {
        var a=controller();var b=controller();a.setInertialResponseController(inertia(.02),()->.98);
        a.initialize(.3,.1,1);b.initialize(.3,.1,1);
        for(int i=0;i<100;i++){a.step(.001,.3,.1,.9,1,0);a.step(.001,.3,.1,.9,1,1);b.step(.001,.3,.1,.9,1);}
        assertEquals(a.getNamedStates(),b.getNamedStates());assertEquals(a.getIpcmd(),b.getIpcmd(),0);assertEquals(a.getIqcmd(),b.getIqcmd(),0);
    }
    @Test void externalAndInertialAuxiliaryPowerRemainIndependentAndAddBeforeTheVoltageDivider() {
        var m=controller();var inertia=validInertia(0);m.setInertialResponseController(inertia,()->.98);m.initialize(.3,0,1);
        m.setAuxiliaryPower(.04);m.step(.001,.3,0,.8,1,0);m.step(.001,.3,0,.8,1,1);
        assertEquals(.04,m.getExternalAuxiliaryPower(),0);assertTrue(inertia.getAuxiliaryPower()>0);
        assertEquals(.04+inertia.getAuxiliaryPower(),m.getAuxiliaryPower(),0);
        assertEquals((.3+.04+inertia.getAuxiliaryPower())/.8,m.getIpcmd(),1e-14);
        m.setAuxiliaryPower(0);assertEquals(inertia.getAuxiliaryPower(),m.getAuxiliaryPower(),0);
        assertEquals(.3,m.getNamedStates().get("Pord"));
    }
}
