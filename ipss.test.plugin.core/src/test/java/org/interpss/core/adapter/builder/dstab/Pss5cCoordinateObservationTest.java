package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.analysis.Pss5cLocalLinearizer;
import org.interpss.dstab.analysis.Pss5cTransducerCoordinates;
import org.interpss.dstab.analysis.Pss5cTransducerCoordinates.Companion;
import org.interpss.dstab.analysis.Pss5cTransducerCoordinates.Cascade;
import org.interpss.dstab.control.pss.psse.pss5c.Pss5cNativeData;
import org.interpss.core.dstab.reference.Pss5cNativeReplayProbe.ReplayStepper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Equivalent source coordinates and exact-time replay guards; no fitted raw-state aliases. */
public class Pss5cCoordinateObservationTest {
    private static Pss5cNativeData data(boolean modified) {
        var p=Pss5cNativeModelTest.parameters();
        if(modified) {p[0]=.003;p[1]=.0002;p[2]=.025;p[4]=.8;p[5]=1.2;p[6]=70;p[7]=65;}
        return new Pss5cNativeData(p);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void cascadeAndCompanionAreInverseForNontrivialSourceParameters(boolean modified) {
        var d=data(modified);var c=new Cascade(.0002,-.0003,.0004,.0001,-.0002);
        var x=Pss5cTransducerCoordinates.toCompanion(d,c);
        assertEquals(c.highLagOutput()-d.parameter(5)*c.highDerivative()-d.parameter(6)*c.highIntegral(),x.highPosition(),0);
        var returned=Pss5cTransducerCoordinates.toCascade(d,x);
        assertArrayEquals(new double[]{c.lowPosition(),c.lowRate(),c.highLagOutput(),c.highIntegral(),c.highDerivative()},
                new double[]{returned.lowPosition(),returned.lowRate(),returned.highLagOutput(),returned.highIntegral(),returned.highDerivative()},1e-16);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void differentiatedCoordinateIdentityMatchesThePublishedCascadeAndCompanionMatrix(boolean modified) {
        var d=data(modified);var c=new Cascade(.0002,-.0003,.0004,.0001,-.0002);
        var x=Pss5cTransducerCoordinates.toCompanion(d,c);double input=.0007;
        double dr=(input-c.highLagOutput())/d.parameter(4),dz=c.highDerivative();
        double dw=(dr-d.parameter(7)*c.highIntegral()-d.parameter(6)*c.highDerivative())/d.parameter(5);
        double[] expected={c.lowRate(),(input-c.lowPosition()-d.parameter(2)*c.lowRate())/d.parameter(1),
                dr-d.parameter(5)*dw-d.parameter(6)*dz,d.parameter(7)*dz,d.parameter(7)*dw};
        var local=Pss5cLocalLinearizer.analyze(d);var a=local.stateMatrix();var b=local.inputVector();
        double[] values={x.lowPosition(),x.lowRate(),x.highPosition(),x.highRate(),x.highAcceleration()};
        double[] rates=new double[5];for(int i=0;i<5;i++) {rates[i]=b[i]*input;for(int j=0;j<5;j++)rates[i]+=a[i][j]*values[j];}
        assertArrayEquals(expected,rates,1e-12);
    }

    @Test void observerDoesNotReplaceSolverStatesOrAdvanceTheController() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",data(false).parameters());assertTrue(model.initStates(m.getDStabBus(),m));
        m.setSpeed(1.001);for(int i=0;i<20;i++) {
            model.nextStep(.00005,DynamicSimuMethod.MODIFIED_EULER,m,0);model.nextStep(.00005,DynamicSimuMethod.MODIFIED_EULER,m,1);
        }
        var before=model.getNamedStates();double output=model.outputSignal,speed=m.getSpeed();
        var observed=Pss5cTransducerCoordinates.observeCascade(model);
        var returned=Pss5cTransducerCoordinates.toCompanion(model.getData(),observed);
        assertEquals(before.get("highPosition"),returned.highPosition(),1e-15);
        assertEquals(before.get("highRate"),returned.highRate(),1e-15);
        assertEquals(before.get("highAcceleration"),returned.highAcceleration(),1e-15);
        assertEquals(before,model.getNamedStates());assertEquals(output,model.outputSignal,0);assertEquals(speed,m.getSpeed(),0);
    }

    @ParameterizedTest @ValueSource(ints={1,4,5})
    void reducedOrdersAreRejectedRatherThanGivenUnprovenCoordinateAliases(int offset) {
        var p=data(false).parameters();p[offset]=0;var d=new Pss5cNativeData(p);
        assertThrows(IllegalArgumentException.class,()->Pss5cTransducerCoordinates.toCascade(d,new Companion(0,0,0,0,0)));
        assertThrows(IllegalArgumentException.class,()->Pss5cTransducerCoordinates.toCompanion(d,new Cascade(0,0,0,0,0)));
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4})
    void allRecordCoordinatesRejectNonfiniteValues(int offset) {
        double[] v=new double[5];v[offset]=Double.NaN;
        assertThrows(IllegalArgumentException.class,()->new Companion(v[0],v[1],v[2],v[3],v[4]));
        v[offset]=Double.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class,()->new Cascade(v[0],v[1],v[2],v[3],v[4]));
    }

    @Test void nullOrOverflowingMapsCannotProduceSilentNonfiniteObservations() {
        assertThrows(NullPointerException.class,()->Pss5cTransducerCoordinates.toCascade(null,new Companion(0,0,0,0,0)));
        assertThrows(NullPointerException.class,()->Pss5cTransducerCoordinates.toCompanion(data(false),null));
        assertThrows(IllegalArgumentException.class,()->Pss5cTransducerCoordinates.toCascade(data(false),
                new Companion(0,0,Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE)));
    }

    @Test void duplicateEventRowsUpdateInputFeedthroughButNeverAdvanceStates() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=data(false).parameters();p[1]=0;var model=b.addPss5cNative("Bus1","1",p);
        var replay=new ReplayStepper(model,m);replay.accept(0,0);var initial=model.getNamedStates();
        replay.accept(0,.001);assertEquals(initial,model.getNamedStates());
        assertEquals((p[0]/p[2])*(m.getSpeed()-1),model.getLowIntermediateInput(),1e-17);
        replay.accept(0,-.001);assertEquals(initial,model.getNamedStates());assertTrue(model.getLowIntermediateInput()<0);
    }

    @Test void negativeHistoryAndAdjacentEndpointsUseTheUnshiftedRecordedClock() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=data(false).parameters();p[1]=0;var model=b.addPss5cNative("Bus1","1",p);var replay=new ReplayStepper(model,m);
        replay.accept(-.001,0);replay.accept(-.001,.001);double startInput=m.getSpeed()-1;
        replay.accept(-.0005,.002);double endInput=m.getSpeed()-1,h=.0005;
        double trial=h*startInput/p[2],expected=.5*h*(startInput/p[2]+(endInput-trial)/p[2]);
        assertEquals(expected,model.getNamedStates().get("lowPosition"),1e-17);
        var before=model.getNamedStates();replay.accept(-.0005,-.002);assertEquals(before,model.getNamedStates());
    }

    @Test void invalidBoundaryDoesNotMutateStatesOrTheAcceptedClock() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var model=b.addPss5cNative("Bus1","1",data(false).parameters());var replay=new ReplayStepper(model,m);
        replay.accept(0,0);var before=model.getNamedStates();double speed=m.getSpeed();
        assertThrows(IllegalArgumentException.class,()->replay.accept(-.001,.1));
        assertThrows(IllegalArgumentException.class,()->replay.accept(Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->replay.accept(.001,Double.NaN));
        assertEquals(before,model.getNamedStates());assertEquals(speed,m.getSpeed(),0);
        replay.accept(0,0);assertEquals(before,model.getNamedStates());
    }

    @Test void replayRequiresBothControllerAndMachine() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var model=b.addPss5cNative("Bus1","1",data(false).parameters());
        assertThrows(NullPointerException.class,()->new ReplayStepper(null,m));
        assertThrows(NullPointerException.class,()->new ReplayStepper(model,null));
    }
}
