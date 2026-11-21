package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.junit.jupiter.api.Test;

/** First post-event recurrence diagnosis; not complete trajectory acceptance. */
public class Svsmo3t2ZeroLagStageDiscriminatorTest extends org.interpss.CorePluginTestSetup {
    @Test void sensorCorrectorUsesNewNetworkVoltageWhileReferenceTwoStepUsesLeftEndpoint() throws Exception {
        var folder=Svsmo3t2DeadbandRelockTest.reference().resolve("bypass-a");
        var lines=Files.readAllLines(folder.resolve("reference.csv"));var names=Arrays.asList(lines.getFirst().split(","));
        var rows=lines.stream().skip(1).map(l->Arrays.stream(l.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
        int event=-1;for(int i=1;i<rows.size()-1;i++)if(rows.get(i)[0]>.0499&&rows.get(i)[0]<.0501&&rows.get(i)[0]==rows.get(i-1)[0]){event=i;break;}
        assertTrue(event>0);double[] right=rows.get(event),next=rows.get(event+1);double h=.0005;
        var m=Svsmo3t2DeadbandRelockTest.model(folder);var d=m.getData();assertEquals(0,d.firingTime(),0);assertEquals(0,d.tc1(),0);assertEquals(d.tc2(),d.tb2(),0);
        double[] initial=new double[6];for(int i=0;i<6;i++)initial[i]=right[names.indexOf("A_STATE_"+i)];
        double schedule=right[names.indexOf("A_VAR_25")],leftVoltage=right[names.indexOf("V_3")],newVoltage=next[names.indexOf("V_3")];
        m.setScheduledVoltage(schedule);m.applyStaggeredState(initial,0,0);m.getDStabBus().setVoltage(new Complex(leftVoltage,0));
        double[] prepared=new double[6];m.readStaggeredState(prepared,0);double preparedCommand=prepared[2];
        double error=schedule-initial[0];assertTrue(Math.abs(error)>d.deadband());
        double firstSensor=(leftVoltage-initial[0])/d.tb1();
        double predictedSensor=initial[0]+h*firstSensor;
        double expectedSensor=initial[0]+.5*h*(firstSensor+(newVoltage-predictedSensor)/d.tb1());
        m.beginPhysicalStepTrial(.05,h);assertTrue(m.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0));m.publishPhysicalStepTrial();
        assertEquals(preparedCommand,m.getCurrent(),0,"Both methods publish the same pre-step algebraic command");
        m.getDStabBus().setVoltage(new Complex(newVoltage,0));assertTrue(m.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1));m.acceptPhysicalStepTrial();
        double[] actual=new double[6];m.readStaggeredState(actual,0);assertEquals(expectedSensor,actual[0],2e-15);
        assertEquals(initial[1]+.5*h*d.ki()*(error+schedule-predictedSensor),actual[1],2e-15);
        double f5=(error-initial[5])/d.tb2(),predicted5=initial[5]+h*f5;
        assertEquals(initial[5]+.5*h*(f5+(schedule-predictedSensor-predicted5)/d.tb2()),actual[5],2e-15);
        // Independent event-averaged AB2 increment: pre-event derivative is zero.
        assertEquals(initial[0]+.75*h*firstSensor,next[names.indexOf("A_STATE_0")],1e-7);
        assertEquals(initial[1]+.75*h*d.ki()*error,next[names.indexOf("A_STATE_1")],5e-8);
        assertEquals(initial[5]+.75*h*f5,next[names.indexOf("A_STATE_5")],5e-8);
        assertTrue(actual[0]-next[names.indexOf("A_STATE_0")]>1e-4,"Sensor divergence precedes the next publication mismatch");
        assertEquals(initial[2],next[names.indexOf("A_VAR_0")],0);
        assertEquals(preparedCommand,m.getCurrent(),0,"Corrector changes command, not this interval's published current");
        assertTrue(Math.abs(actual[2]-next[names.indexOf("A_STATE_2")])>.002);
    }
}
