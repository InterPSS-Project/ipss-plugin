package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.analysis.Pss5cLocalLinearizer;
import org.interpss.dstab.control.pss.psse.pss5c.Pss5cNativeData;
import org.interpss.dstab.control.pss.psse.pss5c.Pss5cNativeStabilizer;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Proper degree-two reductions of the published high sensor, including direct input. */
public class Pss5cSecondOrderHighTest {
    @TempDir Path temporary;

    public static double[] parameters(int mode, boolean lowFirst) {
        var p=Pss5cNativeModelTest.parameters();p[mode==0?4:5]=0;
        if(lowFirst)p[1]=0;return p;
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void strictReaderPreservesEachExactZeroCoefficient(int mode) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var p=parameters(mode,false);
        var text=new StringBuilder("1 'PSS5C' '1'");for(double v:p)text.append(' ').append(v);
        var file=temporary.resolve("high-reduced.dyr");Files.writeString(file,text.append(" /\n").toString());
        var reader=new PSSEDStabDirectParser(b).setStrictImport(true);reader.parseDynFile(file.toString());
        var model=assertInstanceOf(Pss5cNativeStabilizer.class,b.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer());
        assertArrayEquals(p,model.getData().parameters(),0);assertTrue(reader.getLastImportReport().isStrictlyComplete());
        assertTrue(model.getData().hasSecondOrderHighTransducer());assertFalse(model.getData().hasFullOrderTransducers());
    }

    @ParameterizedTest @CsvSource({"0,false","0,true","1,false","1,true"})
    void bothStagesMatchIndependentDenominatorAndHighBandEquations(int mode,boolean lowFirst) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters(mode,lowFirst);m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",p);
        assertNotNull(model);assertTrue(model.initStates(m.getDStabBus(),m));
        double quadratic=(p[5]+p[4]*p[6])/p[7],linear=p[4]+p[6]/p[7],h=.0002;
        m.setSpeed(1.001);double u0=m.getSpeed()-1,acc0=u0/quadratic;
        double bandRate=p[26]*p[22],rateTrial=h*acc0;
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);
        assertEquals(0,model.getNamedStates().get("highPosition"),0);
        assertEquals(rateTrial,model.getNamedStates().get("highRate"),1e-16);
        double bandTrial=h*p[8]*acc0*bandRate;
        assertEquals(bandTrial,model.getNamedStates().get("highUpperLag"),1e-15);
        m.setSpeed(1.002);double u1=m.getSpeed()-1,acc1=(u1-linear*rateTrial)/quadratic;
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);
        double expectedPosition=.5*h*rateTrial,expectedRate=.5*h*(acc0+acc1);
        assertEquals(expectedPosition,model.getNamedStates().get("highPosition"),1e-16);
        assertEquals(expectedRate,model.getNamedStates().get("highRate"),1e-16);
        assertEquals(.5*h*(p[8]*acc0*bandRate+(p[8]*acc1-bandTrial)*bandRate),
                model.getNamedStates().get("highUpperLag"),1e-15);
        assertEquals((u1-expectedPosition-linear*expectedRate)/quadratic,model.getHighInput(),1e-15);
        assertEquals(0,model.getNamedStates().get("highAcceleration"),0);
    }

    @ParameterizedTest @CsvSource({"0,false","0,true","1,false","1,true"})
    void analyticalMatrixReconstructsAllThirteenStatesAtBothEndpoints(int mode,boolean lowFirst) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",parameters(mode,lowFirst));
        assertTrue(model.initStates(m.getDStabBus(),m));var local=Pss5cLocalLinearizer.analyze(model.getData());
        var a=local.stateMatrix();var input=local.inputVector();assertTrue(input[11]>0);
        assertArrayEquals(new double[13],a[4],0);assertEquals(0,input[4],0);
        for(int step=0;step<80;step++) {
            double h=.0002;var old=vector(model.getNamedStates());var rate=rates(a,input,old,m.getSpeed()-1);
            var trial=old.clone();for(int i=0;i<13;i++)trial[i]+=h*rate[i];
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);assertArrayEquals(trial,vector(model.getNamedStates()),1e-12);
            m.setSpeed(1+.001*Math.sin((step+1)*.3));var end=rates(a,input,trial,m.getSpeed()-1);
            for(int i=0;i<13;i++)old[i]+=.5*h*(rate[i]+end[i]);
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);assertArrayEquals(old,vector(model.getNamedStates()),1e-12);
        }
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void highSignalMagnitudeAndPhaseMatchTheIndependentPublishedTransferFunction(int mode) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);var p=parameters(mode,false);var model=b.addPss5cNative("Bus1","1",p);
        assertTrue(model.initStates(m.getDStabBus(),m));
        double h=.0005,w=2*Math.PI,amplitude=1e-5,sine=0,cosine=0;int count=0;
        for(int i=0;i<60000;i++) {
            double t=i*h;m.setSpeed(1+amplitude*Math.sin(w*t));model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);
            m.setSpeed(1+amplitude*Math.sin(w*(t+h)));model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);
            if(i>=40000) {
                sine+=model.getHighInput()*Math.sin(w*(t+h));cosine+=model.getHighInput()*Math.cos(w*(t+h));count++;
            }
        }
        var s=new Complex(0,w);var expected=s.multiply(s).multiply(p[7]).divide(
                s.multiply(p[4]).add(1).multiply(s.multiply(s).multiply(p[5]).add(s.multiply(p[6])).add(p[7])));
        double allowance=amplitude*expected.abs()*.002; // Existing analytic frequency-response family allowance.
        assertEquals(amplitude*expected.getReal(),2*sine/count,allowance);
        assertEquals(amplitude*expected.getImaginary(),2*cosine/count,allowance);
    }

    @ParameterizedTest @CsvSource({"0,false","0,true","1,false","1,true"})
    void constantNonNominalSpeedInitializesAtEquilibrium(int mode,boolean lowFirst) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1.002);var model=b.addPss5cNative("Bus1","1",parameters(mode,lowFirst));
        assertTrue(model.initStates(m.getDStabBus(),m));var old=model.getNamedStates();
        for(int i=0;i<50;i++) {
            model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,0);model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,1);
        }
        assertEquals(old,model.getNamedStates());assertEquals(0,model.getHighInput(),0);assertEquals(0,model.getOutput(m),1e-13);
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void instantaneousHighFeedthroughHonorsBothLimitsWithoutStateMutation(int mode) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters(mode,false);p[25]=1;p[26]=2;p[27]=2;p[9]=p[13]=p[17]=0;
        m.setSpeed(1);var model=b.addPss5cNative("Bus1","1",p);assertTrue(model.initStates(m.getDStabBus(),m));
        var old=model.getNamedStates();m.setSpeed(1.001);assertTrue(model.getHighInput()>0);
        assertEquals(.6,model.getBandOutput(3),0);assertEquals(.15,model.getOutput(m),0);
        m.setSpeed(.999);assertEquals(-.6,model.getBandOutput(3),0);assertEquals(-.15,model.getOutput(m),0);
        assertEquals(old,model.getNamedStates());
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void outputBoundsCannotAlterTheReducedInternalStateMatrix(int mode) {
        var p=parameters(mode,false);var before=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        for(int i:new int[]{11,15,19,23,28}){p[i]=99;p[i+1]=-99;}
        var after=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        assertTrue(Arrays.deepEquals(before.stateMatrix(),after.stateMatrix()));assertArrayEquals(before.inputVector(),after.inputVector(),0);
    }

    @Test void improperOrNegativeHighReductionsRemainExplicitlyUnsupported() {
        var p=parameters(0,false);p[5]=0;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
        p=parameters(1,false);p[6]=0;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
        p=parameters(0,false);p[4]=-1;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
        p=parameters(1,false);p[5]=-1;assertFalse(new Pss5cNativeData(p).hasSupportedTransducers());
    }

    @Test void smallPositiveCoefficientsDoNotTriggerImplicitOrderReduction() {
        for(int index:new int[]{4,5}) {
            var p=Pss5cNativeModelTest.parameters();p[index]=Double.MIN_NORMAL;
            var data=new Pss5cNativeData(p);assertTrue(data.hasFullOrderTransducers());assertFalse(data.hasSecondOrderHighTransducer());
        }
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
