package com.interpss.dstab.controller.gov.weccwsieg1;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent first-fault-step discriminator for the coupled machine boundary. */
public class Wsieg1ElectricalBoundaryDiscriminatorTest {
    private static final TestConfig CONFIG=TestConfig.load(
            "/test-config/ipss.test.plugin.core.json","Wsieg1MachineEventConformanceTest");

    @BeforeAll static void setup(){Wsieg1SharedSmibTest.setup();}

    @Test void electricalFluxErrorPrecedesGovernorPowerErrorAndFallsWithStepRefinement(
            @TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);
        var fluxError=new HashMap<Double,Double>();
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            double h=entry.path("step_s").asDouble();
            Path input=fixture.stage(entry.path("tag").asText());
            var reference=Wsieg1StateConformanceTest.rows(
                    Files.readAllBytes(input.resolve("native.csv")));
            var actual=Wsieg1SharedSmibTest.simulate(input.resolve("network.raw"),
                    input.resolve("dynamic.dyr"),false,"fault",h,.5,List.of(),a->{},0,true);
            int frame=0,checked=0;
            for(var expected:reference) {
                if(expected.get("time_s")<0.)continue;
                var observed=actual.frames.get(frame++);
                double t=expected.get("time_s");
                if(Math.abs(t-(.05+h))>h*.05)continue;
                assertEquals("GRID",observed.kind());
                double electrical=Math.abs(observed.values().get("GEN_PSIK_D")
                        -expected.get("GEN_PSIK_D"));
                double mechanical=Math.abs(observed.values().get("PMECH")
                        -expected.get("PMECH"));
                assertTrue(electrical>100*mechanical,
                        "First post-fault discrepancy must be electrical: h="+h);
                assertEquals(0.,mechanical,1e-6);
                fluxError.put(h,electrical);
                checked++;
            }
            assertEquals(1,checked,"Exactly one first post-fault sample per profile");
            assertEquals(reference.size(),frame+2,"Keep both negative-time startup observations");
        }
        assertEquals(3,fluxError.size());
        double coarse=fluxError.get(1./240.),half=fluxError.get(1./480.),fine=fluxError.get(.0005);
        assertTrue(coarse>.003&&half<.003&&fine<.001,
                "Machine-flux difference must diminish at the actual network boundary: "+fluxError);
        assertTrue(coarse>half&&half>fine&&coarse>4*fine,
                "Step refinement must distinguish the coupled integration error");
    }
}
