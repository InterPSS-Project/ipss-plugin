package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.expic1.*;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Portable equation, boundary, initialization and exact-record regressions. */
public class Expic1ExciterTest extends CorePluginTestSetup {
    private static double[] constants() {
        return new double[]{0,3.5,1,1,-.87,0,0,0,1,-.87,0,1,0,7.5,0,0,0,1,0,1.2,0,6.4,0,.08};
    }

    @Test void exactRecordAttachesAndRejectsExtraOrMissingConstants(@TempDir Path directory) throws Exception {
        var descriptor=DynamicModelCatalog.find("EXPIC1").orElseThrow();
        assertEquals(24,descriptor.parameterCount());
        assertFalse(descriptor.recordSchema().accepts(23));
        assertFalse(descriptor.recordSchema().accepts(25));
        for (int count : new int[]{23,24,25}) {
            var builder=DStabBuilderTestFixture.createWithMachine();
            var record=new StringBuilder("1 'EXPIC1' '1' ");
            double[] c=constants();
            for(int i=0;i<count;i++)record.append(i<24?c[i]:0).append(' ');
            Path file=directory.resolve("exciter-"+count+".dyr");Files.writeString(file,record+"/\n");
            var parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
            if(count==24) {
                parser.parseDynFile(file.toString());
                assertTrue(parser.getLastImportReport().isStrictlyComplete());
                var machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
                var exciter=assertInstanceOf(Expic1Exciter.class,machine.getExciter());
                assertEquals(6.4,exciter.getData().kp());assertEquals(.08,exciter.getData().kc());
            } else assertThrows(Exception.class,()->parser.parseDynFile(file.toString()));
        }
    }

    @Test void nontrivialSevenStateInitializationHasZeroRightHandSideAndStaysAtEquilibrium() {
        double[] c={.02,3.5,.2,1.5,-1,.05,.01,.03,1.2,-.9,.03,.3,.1,8,0,1,.5,.8,.05,1.2,.1,5,.2,.08};
        var kernel=new Expic1Kernel(Expic1Data.fromNativeParameters(c));
        kernel.initialize(1.1,1.03,5,.02);
        assertEquals(Stage.INITIALIZATION,kernel.getStateDerivativeSnapshot().stage());
        for(double rate:kernel.getStateDerivativeSnapshot().rates().values())assertEquals(0,rate,1e-13);
        double[] initial=kernel.getNativeStates();
        for(int i=0;i<2000;i++) {
            kernel.step(.0005,0,1.03,5,.02);kernel.step(.0005,1,1.03,5,.02);
        }
        assertArrayEquals(initial,kernel.getNativeStates(),1e-12);assertEquals(1.1,kernel.output(),1e-12);
    }

    @Test void piPredictorAndCorrectorUseTheirOwnEndpointErrors() {
        var kernel=new Expic1Kernel(Expic1Data.fromNativeParameters(constants()));
        kernel.initialize(1,1,5,0);kernel.setReference(1.02);
        kernel.step(.01,0,1,5,0);
        assertEquals(.2007,kernel.getNativeStates()[1],1e-14);
        assertEquals(.2707,kernel.getPiOutput(),1e-14);
        kernel.step(.01,1,1.01,5,0);
        assertEquals(.200525,kernel.getNativeStates()[1],1e-14);
        assertEquals(.235525,kernel.getPiOutput(),1e-14);
        assertEquals(1.177625,kernel.output(),1e-14);
        assertThrows(IllegalStateException.class,()->kernel.step(.01,1,1,5,0));
    }

    @Test void outputReadsDoNotAdvanceStatesAndZeroTimeRefreshOnlyChangesBypasses() {
        var kernel=new Expic1Kernel(Expic1Data.fromNativeParameters(constants()));
        kernel.initialize(1,1,5,0);double[] initial=kernel.getNativeStates();
        kernel.observe(.9,4,0);
        for(int i=0;i<10;i++){kernel.output();kernel.getNamedStates();kernel.getIntegrationStates();}
        assertArrayEquals(initial,kernel.getNativeStates());
        kernel.step(0,1,.9,4,0);double[] refreshed=kernel.getNativeStates();
        assertEquals(.9,refreshed[0]);assertEquals(.55,refreshed[2],1e-14);assertEquals(.55,refreshed[3],1e-14);
        assertEquals(initial[1],refreshed[1]);assertEquals(initial[4],refreshed[4]);
        assertEquals(2.2,kernel.output(),1e-14);
    }

    @Test void instantaneousFeedbackSatisfiesItsImplicitEquationIncludingClipping() {
        double[] c=constants();c[10]=.03;c[11]=.3;c[5]=.05;c[6]=.01;
        var kernel=new Expic1Kernel(Expic1Data.fromNativeParameters(c));kernel.initialize(1,1,5,0);
        double[] state=kernel.getNativeStates();double feedbackState=state[5];
        for(double reference:new double[]{-10,.2,.95,1,1.02,1.2,10}) {
            kernel.setReference(reference);
            double low=-.87,high=1;
            // Independent monotone root solve for the clipped feedback equation.
            for(int i=0;i<90;i++) {
                double vr=(low+high)/2;
                double feedback=(.03*vr-feedbackState)/.3;
                double va=Math.max(-.87,Math.min(1,state[1]+3.5*(reference-1-feedback)));
                double calculated=Math.max(-.87,Math.min(1,state[2]/.05+.2*va));
                if(vr<calculated)low=vr;else high=vr;
            }
            assertEquals((low+high)/2,kernel.getRegulatorOutput(),1e-13);
        }
    }

    @Test void bridgeUsesComplexTerminalCurrentAndConstantSourceBypass() {
        double[] c=constants();c[21]=5;c[22]=.4;
        var kernel=new Expic1Kernel(Expic1Data.fromNativeParameters(c));
        Complex v=new Complex(.8,.6),i=new Complex(.3,-.2);
        double ve=v.multiply(5).add(new Complex(-i.getImaginary()*.4,i.getReal()*.4)).abs();
        assertEquals(ve-.577*.08*1.2,kernel.bridgeVoltage(v,i,1.2),1e-12);
        c[21]=c[22]=0;
        assertEquals(1,new Expic1Kernel(Expic1Data.fromNativeParameters(c)).bridgeVoltage(Complex.ZERO,i,1.2));
    }

    @Test void rejectsNonfiniteAndUndefinedPoleDataWithoutMutatingInputArray() {
        double[] c=constants(),original=c.clone();Expic1Data.fromNativeParameters(c);assertArrayEquals(original,c);
        for(int index:new int[]{0,1,2,5,6,7,10,11,12,16,23}) {
            double[] invalid=c.clone();invalid[index]=-1;
            assertThrows(IllegalArgumentException.class,()->Expic1Data.fromNativeParameters(invalid));
        }
        for(int index=0;index<24;index++) {
            double[] invalid=c.clone();invalid[index]=Double.NaN;
            assertThrows(IllegalArgumentException.class,()->Expic1Data.fromNativeParameters(invalid));
        }
        assertThrows(IllegalArgumentException.class,()->Expic1Data.fromNativeParameters(Arrays.copyOf(c,23)));
        c[6]=.1;
        assertThrows(IllegalArgumentException.class,()->Expic1Data.fromNativeParameters(c));
    }
}
