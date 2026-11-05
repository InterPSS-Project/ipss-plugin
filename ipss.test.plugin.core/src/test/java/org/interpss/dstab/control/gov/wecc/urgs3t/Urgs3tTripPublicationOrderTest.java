package org.interpss.dstab.control.gov.wecc.urgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.common.DStabSimuException;

/** A premature run publication changes held mechanical rates despite identical trip states. */
public class Urgs3tTripPublicationOrderTest {
    @BeforeAll static void setup()throws Exception{Urgs3tReactivationReferenceTest.setup();}
    private record Observation(double beforePower,double publishedPower,double speed,double inertia,
            double heldRate,double disconnectedSpeed) { }
    private static Observation run(boolean shared,boolean premature)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-trip-publication-");
        try(var z=new ZipFile(Path.of("testData/private/verification-artifacts/urgs3t-reactivation-contract-20261104/captures.zip").toFile())) {
            for(String file:List.of("manifest.json","native.csv","network.raw","dynamic.dyr"))
                try(var in=z.getInputStream(z.getEntry("offline-control-standard-h0p0005-t0p5-trip1-machines-a/"+file))){Files.copy(in,out.resolve(file));}
        }
        double[] observed=new double[6],rates=new double[6];
        Urgs3tReferenceTest.simulate(out,shared,1e-6,(algorithm,recording)-> {
            recording.publishBeforeHostTrip=premature;
            if(shared)return new Urgs3tReferenceTest.Shared(algorithm,recording,1e-6) {
                {setHotReactivationMachineIds(Set.of("Bus1-mach1"));}
                @Override public void beforeStep(double t)throws DStabSimuException {
                    var m=algorithm.getNetwork().getMachine("Bus1-mach1");
                    if(Math.abs(t-.15)<1e-9)captureBefore(m,observed);
                    super.beforeStep(t);
                    if(Math.abs(t-.15)<1e-9){reactivation.readHeldRates(m,rates,0);observed[4]=rates[0];}
                    if(Math.abs(t-.2)<1e-9)observed[5]=m.getSpeed();
                }
            };
            return new Urgs3tReferenceTest.Ordinary(algorithm,recording,1e-6) {
                {setHotReactivationMachineIds(Set.of("Bus1-mach1"));}
                @Override public void beforeStep(double t)throws DStabSimuException {
                    var m=algorithm.getNetwork().getMachine("Bus1-mach1");
                    if(Math.abs(t-.15)<1e-9)captureBefore(m,observed);
                    super.beforeStep(t);
                    if(Math.abs(t-.15)<1e-9){reactivation.readHeldRates(m,rates,0);observed[4]=rates[0];}
                    if(Math.abs(t-.2)<1e-9)observed[5]=m.getSpeed();
                }
            };
        });
        return new Observation(observed[0],observed[1],observed[2],observed[3],observed[4],observed[5]);
    }
    private static void captureBefore(com.interpss.dstab.mach.Machine m,double[] v) {
        v[0]=m.getPm();v[1]=((Urgs3tGovernor)m.getGovernor()).getIntegrationHistories().get("Accepted turbine power");
        v[2]=m.getSpeed();v[3]=m.getH();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void prematurePublicationExactlyExplainsTheExtraDisconnectedAcceleration(boolean shared)throws Exception {
        var fixed=run(shared,false);var negative=run(shared,true);
        assertEquals(fixed.beforePower(),negative.beforePower(),0.);
        assertEquals(fixed.speed(),negative.speed(),0.);
        double difference=(fixed.publishedPower()-fixed.beforePower())/(fixed.speed()*2*fixed.inertia());
        assertTrue(difference>1e-5,"Negative control must exercise a measurable accepted-output lag");
        assertEquals(difference,negative.heldRate()-fixed.heldRate(),2e-14);
        // One AB2 first advance and 99 constant-rate advances during the 0.05-s outage.
        assertEquals(.05025*difference,negative.disconnectedSpeed()-fixed.disconnectedSpeed(),3e-14);
        System.out.println("Trip publication shared="+shared+" fixed="+fixed+" negative="+negative);
    }
}
