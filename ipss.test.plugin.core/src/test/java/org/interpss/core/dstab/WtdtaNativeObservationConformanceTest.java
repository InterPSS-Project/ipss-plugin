package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.*;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.junit.jupiter.api.Test;

/** Native mechanical coordinates and actual evaluation rates remain pure observations. */
public class WtdtaNativeObservationConformanceTest extends CorePluginTestSetup {
    private static Wtdta1Model nativeModel(double fraction,double frequency) {
        return Wtdta1Model.fromNativeRecord(new Wtdta1Data(5,.1,fraction,1.5,.2),frequency);
    }

    @Test void nativeEquilibriumAndCanonicalStatesPreserveLegacyObservationKeys() {
        var model=nativeModel(.8,60);model.initialize(.24,.92);double pm=.24+.1*.92*(.92-1);
        assertEquals(pm,model.getInitialInputPower(),1e-15);assertEquals(.24/.92+.1*(.92-1),model.getShaftTorque(),1e-15);
        assertEquals(4,model.getIntegrationStates().size());assertEquals(7,model.getNamedStates().size());
        assertEquals(.92,model.getNamedStates().get("Generator Speed"),0);assertEquals(Stage.NOT_EVALUATED,model.getStateDerivativeSnapshot().stage());
        double[] before=model.getNativeStates();var named=model.getNamedStates();var copy=model.getNativeStates();copy[0]=99;
        assertArrayEquals(before,model.getNativeStates(),0);assertThrows(UnsupportedOperationException.class,()->named.clear());
        for(int i=0;i<100;i++)model.step(.001,pm,.24);
        assertArrayEquals(before,model.getNativeStates(),1e-13);
        for(var entry:named.entrySet())assertEquals(entry.getValue(),model.getNamedStates().get(entry.getKey()),1e-13);
        assertEquals(model.getNamedStates(),model.getNamedStates());
    }

    @Test void stagedObservationRecordsThePredictedEvaluationNotTheCorrectedState() {
        var model=nativeModel(.8,60);model.initialize(.24,1);double phi=model.getNativeStates()[0];
        model.step(.001,.24,.30,0);var predictor=model.getStateDerivativeSnapshot();
        assertEquals(Stage.PREDICTOR,predictor.stage());assertEquals(-.03,predictor.rates().get("Generator Speed Deviation"),1e-15);
        assertEquals(0,predictor.evaluationStates().get("Generator Speed Deviation"),0);
        model.step(.001,.24,.29,1);var corrected=model.getStateDerivativeSnapshot();double angular=2*Math.PI*60;
        assertEquals(Stage.CORRECTOR,corrected.stage());assertEquals(-.00003,corrected.evaluationStates().get("Generator Speed Deviation"),1e-15);
        assertEquals(angular*.00003,corrected.rates().get("Shaft Twist Angle"),1e-15);
        assertEquals(-angular*.00003,corrected.rates().get("Generator Rotor Angle Deviation"),1e-15);
        assertEquals(phi+.0005*angular*.00003,model.getNativeStates()[0],1e-15);
        model.step(0,.24,.29,0);assertEquals(corrected,model.getStateDerivativeSnapshot());
    }

    @Test void oneMassExposesOnlyActiveIntegrationCoordinatesAndRetainsUnusedNativeStorage() {
        var model=nativeModel(0,60);model.initialize(.24,.92);var before=model.getNativeStates();
        assertEquals(2,model.getIntegrationStates().size());assertEquals(4,model.getNativeStates().length);
        model.step(.001,model.getInitialInputPower(),.30,0);
        assertEquals(before[0],model.getNativeStates()[0],0);assertEquals(before[1],model.getNativeStates()[1],0);
        assertEquals(before[2]-.001*.06/.92/10,model.getNativeStates()[2],1e-15);
        assertEquals(2,model.getStateDerivativeSnapshot().rates().size());
    }

    @Test void frequencyAndLegacyTypedConventionsAreExplicitAndDoNotMutateParameters() {
        var data=new Wtdta1Data(5,.1,.8,1.5,.2);var legacy=new Wtdta1Model(data);var hz50=Wtdta1Model.fromNativeRecord(data,50);var hz60=Wtdta1Model.fromNativeRecord(data,60);
        assertFalse(legacy.isNativeRecordConvention());assertTrue(hz50.isNativeRecordConvention());assertEquals(data,hz50.getData());
        legacy.initialize(.24,.92);hz50.initialize(.24,.92);hz60.initialize(.24,.92);
        assertEquals(3.6,legacy.shaftStiffness(),1e-14);assertEquals(3.6*Math.pow(2*Math.PI,2),hz50.shaftStiffness(),1e-12);
        assertEquals(5./6,hz50.getNativeStates()[0]/hz60.getNativeStates()[0],1e-15);
        assertThrows(IllegalStateException.class,legacy::getNativeStates);
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->Wtdta1Model.fromNativeRecord(data,invalid));
    }
}
