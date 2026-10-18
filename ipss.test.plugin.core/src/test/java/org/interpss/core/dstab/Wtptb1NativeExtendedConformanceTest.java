package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.interpss.core.dstab.reference.Wtptb1NativeReference;
import org.interpss.core.dstab.reference.Wtptb1NativeExtendedReference;
import org.interpss.core.dstab.reference.Wtptb1NativeReference.Profile;
import org.junit.jupiter.api.Test;

public class Wtptb1NativeExtendedConformanceTest {
    @Test void zeroTimeMovingReferenceAndActiveLimitsHaveTwoStepRecordedInputChecks() {
        int checked=0;
        for(Profile profile:Wtptb1NativeExtendedReference.profiles()) {
            if(profile.name().startsWith("fault"))continue;
            Wtptb1NativePartialConformanceTest.replay(profile,bounds(profile));checked++;
        }
        assertEquals(8,checked);
    }
    @Test void branchReferencesActuallyReachBothPiLimitsAndLowerAngleAndMoveTheReference() {
        for(Profile profile:Wtptb1NativeExtendedReference.profiles()) {
            var rows=profile.states().lines().map(Wtptb1NativeExtendedConformanceTest::numbers).toList();
            if(profile.name().startsWith("lower-active"))
                assertTrue(rows.stream().anyMatch(r->Math.abs(r[2]-profile.parameters().thetaMin())<2e-7));
            if(profile.name().startsWith("pi-active")) {
                for(int channel:new int[]{3,4}) {
                    double lower=channel==3?profile.parameters().speedMin():profile.parameters().compensationMin();
                    double upper=channel==3?profile.parameters().speedMax():profile.parameters().compensationMax();
                    assertTrue(rows.stream().anyMatch(r->Math.abs(r[channel]-lower)<2e-7));
                    assertTrue(rows.stream().anyMatch(r->Math.abs(r[channel]-upper)<2e-7));
                }
            }
            if(profile.name().startsWith("moving-wref"))assertTrue(rows.stream().anyMatch(r->r[8]<.999));
        }
    }
    @Test void zeroTimeNativeOutputObeysAlgebraicIdentityRatherThanSlewLimit() {
        for(Profile profile:Wtptb1NativeExtendedReference.profiles())if(profile.name().startsWith("zero")) {
            var data=profile.parameters();double[] previous=null;double slew=0;
            for(double[] row:profile.states().lines().map(Wtptb1NativeExtendedConformanceTest::numbers).toList()) {
                double error=row[6]-row[5];
                double output=clip(clip(row[3]+data.kpw()*(1+row[7]-row[8]+data.kcc()*error),data.speedMin(),data.speedMax())
                        +clip(row[4]+data.kpc()*error,data.compensationMin(),data.compensationMax()),data.thetaMin(),data.thetaMax());
                assertEquals(row[2],output,3e-7,"Native zero-time identity");
                if(previous!=null && row[1]>previous[1])slew=Math.max(slew,Math.abs(row[2]-previous[2])/(row[1]-previous[1]));
                previous=row;
            }
            assertTrue(slew>data.rateMax(),"This branch must distinguish algebraic bypass from a rate-limited lag");
        }
    }
    @Test void extendedBodiesAndFrozenIdentityBundleRejectMutation() {
        StringBuilder bundle=new StringBuilder();
        for(Profile p:Wtptb1NativeExtendedReference.profiles()) {
            p.verify();bundle.append(p.rawSha256()).append('|').append(p.inputsSha256()).append('|').append(p.statesSha256()).append('\n');
            assertThrows(IllegalArgumentException.class,()->p.verify("9"+p.inputs().substring(1),p.states()));
            assertThrows(IllegalArgumentException.class,()->p.verify(p.inputs(),"9"+p.states().substring(1)));
        }
        assertEquals(10,Wtptb1NativeExtendedReference.profiles().size());
        assertEquals("f90e7d7b29ae9661d89bcfa2740c089faa5e7f4dd976238d242204daf93091ba",Wtptb1NativeReference.digest(bundle.toString()));
    }
    private static double[] bounds(Profile p) {
        boolean coarse=p.step()==.0005;
        if(p.name().startsWith("zero"))return coarse?new double[]{4e-6,3.5e-6,3e-6}:new double[]{9e-6,9e-6,8e-7};
        if(p.name().startsWith("lower-active"))return coarse?new double[]{3.3e-4,3.7e-5,6.5e-5}:new double[]{1.4e-4,6e-5,3.3e-5};
        if(p.name().startsWith("pi-active"))return coarse?new double[]{2.2e-5,6e-6,4.5e-6}:new double[]{3.2e-5,1.8e-5,4.5e-6};
        if(p.name().startsWith("moving-wref"))return coarse?new double[]{4e-5,1.1e-5,3e-6}:new double[]{1.1e-5,3e-5,8e-7};
        throw new IllegalArgumentException("No declared branch bounds: "+p.name());
    }
    private static double clip(double x,double low,double high){return Math.max(low,Math.min(high,x));}
    private static double[] numbers(String line){return Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();}
}
