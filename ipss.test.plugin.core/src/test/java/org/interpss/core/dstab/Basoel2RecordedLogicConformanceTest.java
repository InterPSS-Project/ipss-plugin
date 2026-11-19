package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.interpss.core.dstab.Basoel2PowerWorldArtifactConformanceTest.Profile;
import com.interpss.dstab.controller.oel.basoel2.Basoel2Data;
import com.interpss.dstab.controller.oel.basoel2.Basoel2Kernel;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Sensed-boundary PI/timer playback; input filters, physical routing and network acceptance are excluded. */
public class Basoel2RecordedLogicConformanceTest {
    @TempDir Path directory;
    static Stream<Profile> profiles()throws Exception{return Basoel2PowerWorldArtifactConformanceTest.profiles();}
    @ParameterizedTest @MethodSource("profiles")
    void downstreamLogicUsesOnlyRecordedBoundaryInputsAndItsOwnTimerAndIntegralStates(Profile profile)throws Exception{
        var reference=Basoel2PowerWorldArtifactConformanceTest.load(profile,directory);
        var original=Basoel2PowerWorldArtifactConformanceTest.data(profile);
        // Explicit sub-block isolation: measured signals enter AFTER the two input filters.
        // No original parameter or runtime initializer is modified; excluded filter dynamics are not scored.
        var isolated=new Basoel2Data(original.oelFlag(),original.vhzFlag(),original.inputFlag(),0,
            original.proportionalGain(),original.integralGain(),original.shortLimit(),original.shortDelay(),
            original.mediumLimit(),original.mediumDelay(),original.continuousLimit(),original.continuousDelay(),
            0,original.vhzGain(),original.minimumVhzInput());
        var kernel=new Basoel2Kernel(isolated,Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED);
        int sensed=reference.fieldIndex("Generator","1 1","TSOELState:1"),frequency=reference.fieldIndex("Generator","1 1","TSOELState:3");
        int voltage=reference.fieldIndex("Bus","1","TSVpu"),va=reference.fieldIndex("Generator","1 1","TSExciterState:3");
        int integral=reference.fieldIndex("Generator","1 1","TSOELState:2"),total=reference.fieldIndex("Generator","1 1","TSVOEL");
        var first=reference.samples().getFirst();
        double ka=Basoel2PowerWorldArtifactConformanceTest.readback(profile,"exciter").get("Ka").getAsDouble();
        // The source case has no stabilizer/reference event; this is the AC1C equilibrium equation.
        double vref=first.value(voltage)+first.value(va)/ka-first.value(total);
        String[] keys={"Integral","Reference","OEL output","VHz output","Total output","Timer1 active","Timer1 above","Timer1 seconds",
            "Timer2 active","Timer2 above","Timer2 seconds","Timer3 active","Timer3 above","Timer3 seconds"};
        int[] columns=new int[14];columns[0]=integral;columns[4]=total;
        for(int i=1;i<=3;i++)columns[i]=reference.fieldIndex("Generator","1 1","TSOELOther:"+i);
        for(int i=4;i<=12;i++)columns[i+1]=reference.fieldIndex("Generator","1 1","TSOELOther:"+i);
        double[] squares=new double[14],maximum=new double[14],peakTimes=new double[14];
        Basoel2Kernel.Inputs previous=null;double previousTime=-1;int steps=0,duplicates=0;
        for(var sample:reference.samples()){
            var input=new Basoel2Kernel.Inputs(sample.value(sensed),sample.value(frequency),sample.value(voltage),vref);
            if(previous==null){kernel.initialize(input);assertEquals(0,sample.value(integral));}
            else if(sample.time()>previousTime){
                assertEquals(profile.step(),sample.time()-previousTime,1e-12);
                assertTrue(kernel.step(profile.step(),0,previous));assertTrue(kernel.step(profile.step(),1,input));
                assertTrue(kernel.acceptEndpoint(profile.step(),input));steps++;
            }else{assertEquals(previousTime,sample.time());duplicates++;}
            var s=kernel.signals(input);double[] values=new double[14];
            values[0]=kernel.state().integral();values[1]=s.reference();values[2]=s.oelOutput();values[3]=s.vhzOutput();values[4]=s.total();
            int offset=5;for(var timer:kernel.timers()){
                values[offset++]=timer.active()?1:0;values[offset++]=timer.above()?1:0;values[offset++]=timer.elapsed();
            }
            for(int i=0;i<14;i++){
                double error=Math.abs(values[i]-sample.value(columns[i]));squares[i]+=error*error;
                if(error>maximum[i]){maximum[i]=error;peakTimes[i]=sample.time();}
            }
            previous=input;previousTime=sample.time();
        }
        assertEquals(profile.samples(),reference.samples().size());assertEquals(2,duplicates);assertEquals(Math.round(.5/profile.step()),steps);
        double[] rmses=Arrays.stream(squares).map(value->Math.sqrt(value/profile.samples())).toArray();
        System.out.println("BASOEL2 sensed-boundary "+profile.name()+" RMSE="+Arrays.toString(rmses)+" maxima="+Arrays.toString(maximum)+" peakTimes="+Arrays.toString(peakTimes));
        for(int i=0;i<14;i++)assertTrue(rmses[i]<.001,keys[i]+" RMSE="+rmses[i]);
    }
}
