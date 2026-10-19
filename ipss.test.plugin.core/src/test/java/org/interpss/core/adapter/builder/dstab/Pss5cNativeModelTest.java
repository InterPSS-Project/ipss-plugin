package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.pss.psse.pss5c.Pss5cNativeData;
import org.interpss.dstab.control.pss.psse.pss5c.Pss5cNativeStabilizer;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Native contract and published-equation guards, not fitted trajectory envelopes. */
public class Pss5cNativeModelTest {
    @TempDir Path temporary;
    public static double[] parameters() {
        return new double[] {.001759,.00012739,.017823,66,1,1,81,80,66,
            .5,.01,.01,-.01,3,.07,.075,-.075,20,.6,.6,-.6,80,9,.6,-.6,
            5.736,6.883,8.259,.15,-.15};
    }

    @Test void strictRecordMapsAllThirtyConstantsWithoutAnIcon() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();
        var p=parameters();StringBuilder text=new StringBuilder("1 'PSS5C' '1'");
        for(double value:p)text.append(' ').append(value);
        Path file=temporary.resolve("native.dyr");Files.writeString(file,text.append(" /\n").toString());
        var reader=new PSSEDStabDirectParser(b).setStrictImport(true);reader.parseDynFile(file.toString());
        var machine=b.getDStabNetwork().getMachine("Bus1-mach1");
        var model=assertInstanceOf(Pss5cNativeStabilizer.class,machine.getStabilizer());
        assertArrayEquals(p,model.getData().parameters(),0);
        assertSame(machine,model.getMachine());assertTrue(reader.getLastImportReport().isStrictlyComplete());
    }

    @ParameterizedTest @ValueSource(ints={21,29,31})
    void strictReaderRejectsOtherWidthsWithoutReplacingDevice(int width) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();
        var existing=b.addPss5cNative("Bus1","1",parameters());assertNotNull(existing);
        var text=new StringBuilder("1 'PSS5C' '1'");for(int i=0;i<width;i++)text.append(" 1");
        Path file=temporary.resolve("wrong.dyr");Files.writeString(file,text.append(" /\n").toString());
        var reader=new PSSEDStabDirectParser(b).setStrictImport(true);
        assertThrows(InterpssException.class,()->reader.parseDynFile(file.toString()));
        assertEquals(1,reader.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        assertSame(existing,b.getDStabNetwork().getMachine("Bus1-mach1").getStabilizer());
    }

    @Test void dataIsFiniteExactAndDefensivelyCopied() {
        var p=parameters();var d=new Pss5cNativeData(p);p[3]=999;
        assertEquals(66,d.parameter(3),0);d.parameters()[3]=123;assertEquals(66,d.parameter(3),0);
        p[0]=Double.NaN;assertThrows(IllegalArgumentException.class,()->new Pss5cNativeData(p));
        assertThrows(IllegalArgumentException.class,()->new Pss5cNativeData(new double[31]));
    }

    @Test void predictorAndCorrectorUseAtomicThirteenStateSlopes() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);var p=parameters();var model=b.addPss5cNative("Bus1","1",p);
        assertTrue(model.initStates(m.getDStabBus(),m));
        double h=.0005,u=.001;m.setSpeed(1+u);u=m.getSpeed()-1;
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);
        var x=model.getNamedStates();assertEquals(13,x.size());
        assertEquals(h*u/p[1],x.get("lowRate"),1e-16);
        assertEquals(h*u*p[7]/(p[4]*p[5]),x.get("highAcceleration"),1e-16);
        assertEquals(0,x.get("lowPosition"),0);assertEquals(0,x.get("veryLowUpperLag"),0);
        model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);x=model.getNamedStates();
        double lowTrial=h*u/p[1],highTrial=h*u*p[7]/(p[4]*p[5]);
        assertEquals(.5*h*lowTrial,x.get("lowPosition"),1e-16);
        assertEquals(h*u/p[1]-.5*h*p[2]*lowTrial/p[1],x.get("lowRate"),1e-16);
        assertEquals(.5*h*highTrial,x.get("highRate"),1e-16);
        assertEquals(.5*h*p[3]*p[0]*lowTrial*p[26]*p[10],x.get("veryLowUpperLag"),1e-16);
        assertEquals(.5*h*p[8]*highTrial*p[26]*p[22],x.get("highUpperLag"),1e-16);
        var frozen=model.getNamedStates();double out=model.getOutput(m);
        for(int i=0;i<10;i++){assertEquals(out,model.getOutput(m),0);model.getBandOutput(i%4);}
        assertEquals(frozen,model.getNamedStates());assertThrows(UnsupportedOperationException.class,()->frozen.put("x",1.));
    }

    @Test void nonNominalConstantSpeedInitializesAtAZeroOutputEquilibrium() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1.001);var model=b.addPss5cNative("Bus1","1",parameters());
        assertTrue(model.initStates(m.getDStabBus(),m));var initial=model.getNamedStates();
        for(int i=0;i<50;i++){model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,0);model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,1);}
        assertEquals(initial,model.getNamedStates());assertEquals(0,model.getOutput(m),1e-14);
    }

    @Test void bypassRulesAndUnimplementedDegenerateTransducersAreExplicit() throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");
        var p=parameters();p[25]=0;var model=b.addPss5cNative("Bus1","1",p);
        assertTrue(model.initStates(m.getDStabBus(),m));assertTrue(model.isBypassed());m.setSpeed(1.1);
        model.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,m,0);assertEquals(0,model.getOutput(m),0);
        p=parameters();p[10]=0;assertTrue(new Pss5cNativeData(p).isBypassed());
        p[9]=0;assertFalse(new Pss5cNativeData(p).isBypassed());
        p=parameters();p[1]=-1;assertNull(b.addPss5cNative("Bus1","1",p));
    }

    @Test void limitsSwapWithoutForcingTheirSigns() {
        var p=parameters();p[11]=-.3;p[12]=-.1;p[28]=.04;p[29]=.02;
        var d=new Pss5cNativeData(p);assertEquals(-.1,d.upper(11),0);assertEquals(-.3,d.lower(11),0);
        assertEquals(.04,d.upper(28),0);assertEquals(.02,d.lower(28),0);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void nativeInputGainsAndTransducersMatchTheAnalyticFrequencyResponse(boolean high) throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(1);
        var p=parameters();for(int band=0;band<4;band++){p[9+4*band]=0;p[11+4*band]=10;p[12+4*band]=-10;}
        int band=high?3:1;p[9+4*band]=2;p[10+4*band]=1;p[3]=33;p[8]=99;
        var model=b.addPss5cNative("Bus1","1",p);assertTrue(model.initStates(m.getDStabBus(),m));
        double h=.0005,w=2*Math.PI*.7,amplitude=1e-5,sine=0,cosine=0;int count=0;
        for(int i=0;i<60000;i++){
            double t=i*h;m.setSpeed(1+amplitude*Math.sin(w*t));model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,0);
            m.setSpeed(1+amplitude*Math.sin(w*(t+h)));model.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,m,1);
            if(i>=40000){double out=model.getOutput(m);sine+=out*Math.sin(w*(t+h));cosine+=out*Math.cos(w*(t+h));count++;}
        }
        Complex s=new Complex(0,w),one=Complex.ONE;
        Complex sensor=high?s.multiply(s).multiply(p[7]).divide(s.multiply(p[4]).add(one)
            .multiply(s.multiply(s).multiply(p[5]).add(s.multiply(p[6])).add(p[7]))):
            s.multiply(p[0]).add(one).divide(s.multiply(s).multiply(p[1]).add(s.multiply(p[2])).add(one));
        Complex upper=s.divide(p[27]).add(one).divide(s.divide(p[26]).add(one));
        Complex lower=s.divide(p[26]).add(one).divide(s.divide(p[25]).add(one));
        double expected=amplitude*sensor.multiply(high?p[8]:p[3]).multiply(upper.subtract(lower)).multiply(2).abs();
        double actual=2*Math.hypot(sine,cosine)/count;
        assertEquals(expected,actual,expected*.002,"Independent published transfer function; fixed second-order numerical allowance");
    }
}
