package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.analysis.Pss5cLocalLinearizer;
import org.interpss.dstab.analysis.Pss5cTransducerCoordinates;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeData;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeStabilizer;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Exact static Hlow=1, not a minimum-time substitution or input differentiator. */
public class Pss5cStaticLowTest {
    @TempDir Path temporary;
    private static double[] parameters(int highMode) {
        var p=Pss5cNativeModelTest.parameters();p[0]=p[1]=p[2]=0;
        if(highMode>=0)p[highMode==0?4:5]=0;return p;
    }

    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void strictNativeReaderAttachesTheProperStaticAndMixedOrders(int highMode) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var p=parameters(highMode);
        var text=new StringBuilder("1 'PSS5C' '1'");for(double v:p)text.append(' ').append(v);
        var file=temporary.resolve("static-low.dyr");Files.writeString(file,text.append(" /\n").toString());
        var reader=new PSSEDStabDirectParser(b).setStrictImport(true);reader.parseDynFile(file.toString());
        var model=assertInstanceOf(Pss5cNativeStabilizer.class,b.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer());
        assertArrayEquals(p,model.getData().parameters(),0);assertTrue(reader.getLastImportReport().isStrictlyComplete());
        assertTrue(model.getData().hasStaticLowTransducer());assertFalse(model.getData().hasFirstOrderLowTransducer());
    }

    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void lowSignalAndBothBandSlopesUseTheirOwnEndpointInput(int highMode) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters(highMode);m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",p);
        assertTrue(model.initStates(m.getDStabBus(),m));double h=.0002;m.setSpeed(1.001);double u0=m.getSpeed()-1;
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);assertEquals(u0,model.getLowIntermediateInput(),0);
        double rate=p[26]*p[10],trial=h*p[3]*u0*rate;
        assertEquals(trial,model.getNamedStates().get("veryLowUpperLag"),1e-17);
        m.setSpeed(1.002);double u1=m.getSpeed()-1;
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);
        assertEquals(.5*h*(p[3]*u0*rate+(p[3]*u1-trial)*rate),model.getNamedStates().get("veryLowUpperLag"),1e-17);
        assertEquals(u1,model.getLowIntermediateInput(),0);
        assertEquals(0,model.getNamedStates().get("lowPosition"),0);assertEquals(0,model.getNamedStates().get("lowRate"),0);
    }

    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void matrixReconstructsAllCoordinatesWithDirectLowBandInput(int highMode) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);var p=parameters(highMode);var model=b.addPss5cNative("Bus1","1",p);
        assertTrue(model.initStates(m.getDStabBus(),m));var local=Pss5cLocalLinearizer.analyze(model.getData());
        var a=local.stateMatrix();var input=local.inputVector();
        for(int i=0;i<2;i++){assertArrayEquals(new double[13],a[i],0);assertEquals(0,input[i],0);}
        for(int i=5;i<11;i++) {
            assertEquals(0,a[i][0],0);assertEquals(0,a[i][1],0);
            int band=(i-5)/2;assertEquals(p[3]*p[10+4*band]*p[26-(i-5)%2],input[i],1e-12);
        }
        for(int step=0;step<60;step++) {
            double h=.0002;var old=vector(model.getNamedStates());var rate=rates(a,input,old,m.getSpeed()-1);
            var trial=old.clone();for(int i=0;i<13;i++)trial[i]+=h*rate[i];
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);assertArrayEquals(trial,vector(model.getNamedStates()),1e-12);
            m.setSpeed(1+.001*Math.sin((step+1)*.3));var end=rates(a,input,trial,m.getSpeed()-1);
            for(int i=0;i<13;i++)old[i]+=.5*h*(rate[i]+end[i]);
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);assertArrayEquals(old,vector(model.getNamedStates()),1e-12);
        }
    }

    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void nonNominalEquilibriumInitializesOnlyActiveStorage(int highMode) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1.002);var model=b.addPss5cNative("Bus1","1",parameters(highMode));
        assertTrue(model.initStates(m.getDStabBus(),m));var before=model.getNamedStates();
        for(int i=0;i<50;i++){model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,0);model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,1);}
        assertEquals(before,model.getNamedStates());assertEquals(0,before.get("lowPosition"),0);
        assertEquals(0,before.get("lowRate"),0);assertEquals(m.getSpeed()-1,model.getLowIntermediateInput(),0);
        assertEquals(0,model.getOutput(m),1e-13);
    }

    @Test void instantaneousLowInputHonorsBothOutputLimitsWithoutStateMutation() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters(-1);p[25]=1;p[26]=2;p[27]=2;p[21]=0;
        m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",p);assertTrue(model.initStates(m.getDStabBus(),m));
        var before=model.getNamedStates();m.setSpeed(1.001);assertEquals(.15,model.getOutput(m),0);
        assertEquals(.01,model.getBandOutput(0),0);m.setSpeed(.999);assertEquals(-.15,model.getOutput(m),0);
        assertEquals(-.01,model.getBandOutput(0),0);assertEquals(before,model.getNamedStates());
    }

    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void inactiveCoordinatesAreFrozenModesNotHiddenDynamicLags(int highMode) {
        var local=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(parameters(highMode)));
        assertFalse(local.bypassed());int expected=2+(highMode>=0?1:0);
        assertEquals(expected,local.eigenvalues().stream().filter(z->z.abs()<1e-8).count());
    }

    @Test void bandMagnitudeAndPhaseMatchTheIndependentStaticLowTransfer() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters(-1);p[9]=p[17]=p[21]=0;p[13]=2;p[14]=1;
        m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",p);assertTrue(model.initStates(m.getDStabBus(),m));
        double h=.0005,w=2*Math.PI,amplitude=1e-5,sine=0,cosine=0;int count=0;
        for(int i=0;i<60000;i++) {
            double t=i*h;m.setSpeed(1+amplitude*Math.sin(w*t));model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);
            m.setSpeed(1+amplitude*Math.sin(w*(t+h)));model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);
            if(i>=40000){double y=model.getOutput(m);sine+=y*Math.sin(w*(t+h));cosine+=y*Math.cos(w*(t+h));count++;}
        }
        var s=new Complex(0,w);var upper=s.divide(p[27]).add(1).divide(s.divide(p[26]).add(1));
        var lower=s.divide(p[26]).add(1).divide(s.divide(p[25]).add(1));var expected=upper.subtract(lower).multiply(p[3]*p[13]*amplitude);
        double allowance=expected.abs()*.002; // Existing analytic frequency-response family allowance.
        assertEquals(expected.getReal(),2*sine/count,allowance);assertEquals(expected.getImaginary(),2*cosine/count,allowance);
    }

    @Test void onlyTheExactProperStaticPolynomialIsAccepted() {
        var p=parameters(-1);p[0]=Double.MIN_VALUE;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
        p[0]=0;p[1]=Double.MIN_NORMAL;assertFalse(new Pss5cNativeData(p).hasStaticLowTransducer());
        p[1]=0;p[2]=Double.MIN_NORMAL;assertTrue(new Pss5cNativeData(p).hasFirstOrderLowTransducer());
        p[2]=-1;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
    }

    @Test void documentedWholeModelBypassStillAvoidsUnsupportedHighEquations() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters(0);p[5]=0;p[25]=0;var model=b.addPss5cNative("Bus1","1",p);
        assertNotNull(model);assertTrue(model.initStates(m.getDStabBus(),m));m.setSpeed(1.1);
        model.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,0);assertEquals(0,model.getOutput(m),0);
        assertEquals(0,model.getLowIntermediateInput(),0);assertTrue(model.isBypassed());
    }

    @Test void fullOrderCascadeObserverRejectsTheStaticLowCase() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var model=b.addPss5cNative("Bus1","1",parameters(-1));
        assertThrows(IllegalArgumentException.class,()->Pss5cTransducerCoordinates.observeCascade(model));
    }

    private static double[] vector(Map<String,Double> states) {
        return Pss5cLocalLinearizer.STATE_NAMES.stream().mapToDouble(states::get).toArray();
    }
    private static double[] rates(double[][] a,double[] b,double[] x,double input) {
        double[] out=new double[13];for(int i=0;i<13;i++){out[i]=b[i]*input;for(int j=0;j<13;j++)out[i]+=a[i][j]*x[j];}return out;
    }
}
