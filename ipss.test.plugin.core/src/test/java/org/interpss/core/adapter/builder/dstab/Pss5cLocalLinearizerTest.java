package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Map;
import org.interpss.dstab.analysis.Pss5cLocalLinearizer;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeData;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Source-equation Jacobian checks; no native trajectories or fitted coefficients. */
public class Pss5cLocalLinearizerTest {
    @Test void matrixMatchesEveryPredictorAndCorrectorCoordinate() throws Exception {
        var builder = DStabBuilderTestFixture.createWithMachine();
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setSpeed(1);
        double[] p = Pss5cNativeModelTest.parameters();
        p[0]=.003;p[1]=.0002;p[2]=.025;p[3]=33;p[4]=.8;p[5]=1.2;p[6]=70;p[7]=65;p[8]=99;
        var model = builder.addPss5cNative("Bus1","1",p);assertNotNull(model);
        assertTrue(model.initStates(machine.getDStabBus(),machine));
        var result = Pss5cLocalLinearizer.analyze(model.getData());
        double[][] a=result.stateMatrix();double[] b=result.inputVector();double h=.0003;
        for(int i=0;i<60;i++) {
            double[] old=vector(model.getNamedStates()),rate=rates(a,b,old,machine.getSpeed()-1);
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,machine,0);
            double[] predicted=old.clone();for(int k=0;k<13;k++)predicted[k]+=h*rate[k];
            assertArrayEquals(predicted,vector(model.getNamedStates()),2e-13);
            machine.setSpeed(1+.001*Math.sin((i+1)*.2));
            double[] endRate=rates(a,b,predicted,machine.getSpeed()-1);
            model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,machine,1);
            double[] expected=old.clone();for(int k=0;k<13;k++)expected[k]+=.5*h*(rate[k]+endRate[k]);
            assertArrayEquals(expected,vector(model.getNamedStates()),2e-13);
        }
    }

    @Test void spectrumContainsAllPublishedBandPolesAndTheSensorModes() {
        var p=Pss5cNativeModelTest.parameters();var r=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        assertEquals(13,r.eigenvalues().size());assertFalse(r.bypassed());
        for(double pole:r.bandPoles())assertTrue(r.eigenvalues().stream().anyMatch(z->z.subtract(pole).abs()<1e-8));
        assertEquals(2,r.eigenvalues().stream().filter(z->Math.abs(z.getReal()+1)<1e-5&&Math.abs(z.getImaginary())<1e-5).count());
        assertTrue(r.eigenvalues().stream().anyMatch(z->z.getImaginary()>1));
        double trace=0;var a=r.stateMatrix();for(int i=0;i<13;i++)trace+=a[i][i];
        assertEquals(trace,r.eigenvalues().stream().mapToDouble(z->z.getReal()).sum(),1e-7);
    }

    @Test void frequencyScalingFollowsPublishedHertzConvention() {
        var p=Pss5cNativeModelTest.parameters();var first=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        for(int index:new int[]{10,14,18,22})p[index]*=2;
        var second=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        for(int i=0;i<8;i++)assertEquals(2*first.bandPoles()[i],second.bandPoles()[i],0);
        for(int i=0;i<5;i++)assertArrayEquals(first.stateMatrix()[i],second.stateMatrix()[i],0);
        assertArrayEquals(first.inputVector(),second.inputVector(),0);
    }

    @Test void dataAndReturnedArraysCannotMutateTheDiagnostic() {
        var p=Pss5cNativeModelTest.parameters();var r=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        p[1]=100;double original=r.stateMatrix()[1][0];r.stateMatrix()[1][0]=100;
        assertEquals(original,r.stateMatrix()[1][0],0);r.inputVector()[1]=100;
        assertNotEquals(100,r.inputVector()[1]);r.bandPoles()[0]=100;assertNotEquals(100,r.bandPoles()[0]);
        assertThrows(UnsupportedOperationException.class,()->r.stateNames().add("other"));
        assertThrows(UnsupportedOperationException.class,()->r.eigenvalues().clear());
    }

    @Test void inactiveOutputLimitsCannotChangeStateJacobian() {
        var p=Pss5cNativeModelTest.parameters();var before=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        for(int index:new int[]{11,15,19,23,28}){p[index]=99;p[index+1]=-99;}
        var after=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));
        assertTrue(Arrays.deepEquals(before.stateMatrix(),after.stateMatrix()));
        assertArrayEquals(before.inputVector(),after.inputVector(),0);
    }

    @Test void documentedGlobalBypassFreezesAllStates() {
        var p=Pss5cNativeModelTest.parameters();p[25]=0;p[1]=0;
        var r=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));assertTrue(r.bypassed());
        for(double[] row:r.stateMatrix())assertArrayEquals(new double[13],row,0);
        assertArrayEquals(new double[13],r.inputVector(),0);assertArrayEquals(new double[8],r.bandPoles(),0);
        assertTrue(r.eigenvalues().stream().allMatch(z->z.abs()==0));
    }

    @Test void inactiveZeroFrequencyBandHasFrozenMemories() {
        var p=Pss5cNativeModelTest.parameters();p[9]=0;p[10]=0;
        var r=Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p));assertFalse(r.bypassed());
        assertArrayEquals(new double[13],r.stateMatrix()[5],0);assertArrayEquals(new double[13],r.stateMatrix()[6],0);
        assertEquals(0,r.bandPoles()[0],0);assertEquals(0,r.bandPoles()[1],0);
    }

    @Test void unsupportedOrNonfiniteMatricesAreRejectedRatherThanRegularized() {
        var p=Pss5cNativeModelTest.parameters();p[1]=-1;
        assertThrows(IllegalArgumentException.class,()->Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p)));
        p[1]=Double.MIN_VALUE;
        assertThrows(IllegalArgumentException.class,()->Pss5cLocalLinearizer.analyze(new Pss5cNativeData(p)));
    }

    @Test void unstableInternalModesAreReportedWithoutChangingTheParameters() {
        var p=Pss5cNativeModelTest.parameters();p[2]=-p[2];
        var data=new Pss5cNativeData(p);var r=Pss5cLocalLinearizer.analyze(data);
        assertFalse(r.bypassed());assertTrue(r.stateMatrix()[1][1]>0);
        assertTrue(r.eigenvalues().stream().anyMatch(z->z.getReal()>0));
        assertEquals(p[2],data.parameter(2),0,"A diagnostic must not repair an unstable coefficient");
    }

    private static double[] vector(Map<String,Double> states) {
        return Pss5cLocalLinearizer.STATE_NAMES.stream().mapToDouble(states::get).toArray();
    }
    private static double[] rates(double[][] a,double[] b,double[] state,double input) {
        double[] result=new double[13];for(int i=0;i<13;i++){
            result[i]=b[i]*input;for(int j=0;j<13;j++)result[i]+=a[i][j]*state[j];
        }return result;
    }
}
