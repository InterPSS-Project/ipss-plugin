package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.interpss.dstab.analysis.Pss5cLocalLinearizer;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeData;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeStabilizer;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Exact AL=0 reduction of (1+D*s)/(1+B*s+A*s*s), without timestep heuristics. */
public class Pss5cFirstOrderLowTest {
    @TempDir Path temporary;

    private static double[] parameters() {
        var p=Pss5cNativeModelTest.parameters();p[1]=0;return p;
    }

    @Test void strictNativeReaderAttachesTheZeroQuadraticCoefficient() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var p=parameters();
        var text=new StringBuilder("1 'PSS5C' '1'");for(double v:p)text.append(' ').append(v);
        var file=temporary.resolve("first-order.dyr");Files.writeString(file,text.append(" /\n").toString());
        var reader=new PSSEDStabDirectParser(b).setStrictImport(true);reader.parseDynFile(file.toString());
        var model=assertInstanceOf(Pss5cNativeStabilizer.class,b.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer());
        assertArrayEquals(p,model.getData().parameters(),0);assertTrue(reader.getLastImportReport().isStrictlyComplete());
        assertFalse(model.getData().hasFullOrderTransducers());assertTrue(model.getData().hasFirstOrderLowTransducer());
    }

    @ParameterizedTest @ValueSource(doubles={0,.004})
    void bothStagesMatchIndependentLeadLagAndBandEquations(double numerator) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters();p[0]=numerator;m.setSpeed(1);
        var model=b.addPss5cNative("Bus1","1",p);assertNotNull(model);assertTrue(model.initStates(m.getDStabBus(),m));
        double h=.0003;m.setSpeed(1.001);double u0=m.getSpeed()-1,ratio=p[0]/p[2];
        double lag0=h*u0/p[2],bandRate=p[26]*p[10];
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);
        assertEquals(lag0,model.getNamedStates().get("lowPosition"),1e-17);
        assertEquals(h*p[3]*ratio*u0*bandRate,model.getNamedStates().get("veryLowUpperLag"),1e-17);
        assertEquals(lag0+ratio*(u0-lag0),model.getLowIntermediateInput(),1e-17);
        double band0=model.getNamedStates().get("veryLowUpperLag");
        m.setSpeed(1.002);double u1=m.getSpeed()-1;
        double expectedLag=.5*h*(u0/p[2]+(u1-lag0)/p[2]);
        double expectedBand=.5*h*(p[3]*ratio*u0*bandRate+
                (p[3]*(lag0+ratio*(u1-lag0))-band0)*bandRate);
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);
        assertEquals(expectedLag,model.getNamedStates().get("lowPosition"),1e-17);
        assertEquals(expectedBand,model.getNamedStates().get("veryLowUpperLag"),1e-17);
        assertEquals(0,model.getNamedStates().get("lowRate"),0);
    }

    @Test void matrixMatchesAllRuntimeCoordinatesIncludingInputFeedthrough() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",parameters());assertTrue(model.initStates(m.getDStabBus(),m));
        var local=Pss5cLocalLinearizer.analyze(model.getData());var a=local.stateMatrix();var input=local.inputVector();
        assertTrue(input[5]>0);assertArrayEquals(new double[13],a[1],0);
        assertTrue(local.eigenvalues().stream().anyMatch(z->z.abs()<1e-12),"Frozen coordinate; eigensolver roundoff only");
        for(int step=0;step<80;step++) {
            double h=.0002;var old=vector(model.getNamedStates());var rate=rates(a,input,old,m.getSpeed()-1);
            var trial=old.clone();for(int i=0;i<13;i++)trial[i]+=h*rate[i];
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);assertArrayEquals(trial,vector(model.getNamedStates()),1e-13);
            m.setSpeed(1+.001*Math.sin((step+1)*.3));var end=rates(a,input,trial,m.getSpeed()-1);
            for(int i=0;i<13;i++)old[i]+=.5*h*(rate[i]+end[i]);
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);assertArrayEquals(old,vector(model.getNamedStates()),1e-13);
        }
    }

    @Test void directOutputRespondsToSpeedWithoutMutatingStatesAndHonorsBothLimits() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters();p[25]=1;p[26]=2;p[27]=2; // Nonzero differential feedthrough activates both bounds.
        m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",p);assertTrue(model.initStates(m.getDStabBus(),m));
        var old=model.getNamedStates();m.setSpeed(1.1);
        assertEquals(.15,model.getOutput(m),0);assertEquals(.01,model.getBandOutput(0),0);
        m.setSpeed(.9);assertEquals(-.15,model.getOutput(m),0);assertEquals(-.01,model.getBandOutput(0),0);
        assertEquals(old,model.getNamedStates());
    }

    @Test void nonNominalInitializationIsEquilibriumForReducedOrder() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1.002);var model=b.addPss5cNative("Bus1","1",parameters());assertTrue(model.initStates(m.getDStabBus(),m));
        var old=model.getNamedStates();for(int i=0;i<50;i++) {
            model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,0);model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,1);
        }
        assertEquals(old,model.getNamedStates());assertEquals(m.getSpeed()-1,model.getLowIntermediateInput(),1e-17);
        assertEquals(0,model.getOutput(m),1e-13);
    }

    @Test void positiveQuadraticCoefficientNeverTriggersHiddenOrderReduction() {
        var p=parameters();p[1]=Double.MIN_NORMAL;var data=new Pss5cNativeData(p);
        assertTrue(data.hasFullOrderTransducers());assertFalse(data.hasFirstOrderLowTransducer());
        p[1]=-1;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
        p[1]=0;p[2]=0;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
        p[2]=.02;p[4]=0;p[5]=0;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
    }

    private static double[] vector(Map<String,Double> states) {
        return Pss5cLocalLinearizer.STATE_NAMES.stream().mapToDouble(states::get).toArray();
    }
    private static double[] rates(double[][] a,double[] b,double[] x,double input) {
        double[] out=new double[13];for(int i=0;i<13;i++) {
            out[i]=b[i]*input;for(int j=0;j<13;j++)out[i]+=a[i][j]*x[j];
        }return out;
    }
}
