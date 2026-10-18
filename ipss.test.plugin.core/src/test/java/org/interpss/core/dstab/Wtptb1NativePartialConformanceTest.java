package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.interpss.core.dstab.reference.Wtptb1NativeReference;
import org.interpss.core.dstab.reference.Wtptb1NativeReference.Profile;
import org.interpss.dstab.renewable.Wtptb1Model;
import org.junit.jupiter.api.Test;

/** Recorded-input source-kernel evidence, not native import or coupled-network acceptance. */
public class Wtptb1NativePartialConformanceTest {
    @Test void baselinePlaybackChecksAllStatesAndBothEventSidesAtTwoSteps() {
        var profiles=Wtptb1NativeReference.profiles();
        double[] coarse=replay(profiles.get(0),new double[]{2e-5,1.5e-5,3e-6});
        double[] fine=replay(profiles.get(1),new double[]{6e-6,1.8e-5,8e-7});
        assertTrue(fine[0]<.6*coarse[0],"Pitch trajectory must improve under step halving");
        assertTrue(fine[2]<.5*coarse[2],"Compensation trajectory must improve under step halving");
        // Small float-state speed-integral differences are not monotonically
        // refined; do not claim convergence for this coordinate.
    }

    @Test void nativeUpperPitchProfileActuallyReachesItsAngleLimit() {
        Profile profile=Wtptb1NativeReference.profiles().get(2);
        assertTrue(profile.states().lines().map(Wtptb1NativePartialConformanceTest::numbers)
                .anyMatch(row->Math.abs(row[2]-profile.parameters().thetaMax())<1e-7));
        replay(profile,new double[]{8e-5,2.5e-5,7e-5});
    }

    @Test void recordedBodiesAndIndependentIdentityBundleRejectMutation() {
        StringBuilder bundle=new StringBuilder();
        for(Profile profile:Wtptb1NativeReference.profiles()) {
            profile.verify();
            bundle.append(profile.rawSha256()).append('|').append(profile.inputsSha256())
                    .append('|').append(profile.statesSha256()).append('\n');
            assertThrows(IllegalArgumentException.class,
                    ()->profile.verify("9"+profile.inputs().substring(1),profile.states()));
            assertThrows(IllegalArgumentException.class,
                    ()->profile.verify(profile.inputs(),"9"+profile.states().substring(1)));
        }
        assertEquals("20a54e3b8f42c396a012dbd2b3ca1db239ca65619e7c38dcfca453d4ec5ce993",
                Wtptb1NativeReference.digest(bundle.toString()));
    }

    private static double[] replay(Profile profile,double[] bounds) {
        profile.verify();
        var inputs=profile.inputs().lines().map(Wtptb1NativePartialConformanceTest::numbers).toList();
        var checkpoints=profile.states().lines().map(Wtptb1NativePartialConformanceTest::numbers).toList();
        assertEquals(profile.samples(),inputs.size());
        assertEquals(profile.checkpoints(),checkpoints.size());
        var model=new Wtptb1Model(profile.parameters());
        model.initialize(profile.initialPitch());
        double[] maximum=new double[3],at=new double[3],previous=null;
        int next=0,posts=0;
        for(int row=0;row<inputs.size();row++) {
            double[] input=inputs.get(row);
            if(row>0 && !profile.isPost(row)) {
                // Declared native timestep, not the drifting serialized float
                // clock. All recorded input endpoints and event duplicates stay.
                model.step(profile.step(),previous[0],previous[1],1+previous[2],previous[3],0);
                model.step(profile.step(),input[0],input[1],1+input[2],input[3],1);
            } else if(row>0) posts++;
            if(next<checkpoints.size() && (int)checkpoints.get(next)[0]==row) {
                double[] expected=checkpoints.get(next++),actual=model.getNativeStates();
                for(int state=0;state<3;state++) {
                    double error=Math.abs(actual[state]-expected[state+2]);
                    if(error>maximum[state]) { maximum[state]=error;at[state]=expected[1]; }
                    assertTrue(error<=bounds[state],profile.name()+" state "+state+" at "+expected[1]
                            +": "+error+" > "+bounds[state]);
                }
            }
            previous=input;
        }
        assertEquals(2,posts);
        assertEquals(checkpoints.size(),next);
        assertEquals(inputs.size()-1,(int)checkpoints.getLast()[0],"Do not drop the extra fine terminal tick");
        System.out.println("WTPTB1 source replay "+profile.name()+" max="+Arrays.toString(maximum)
                +" at="+Arrays.toString(at));
        return maximum;
    }
    private static double[] numbers(String row) {
        return Arrays.stream(row.split(",")).mapToDouble(Double::parseDouble).toArray();
    }
}
