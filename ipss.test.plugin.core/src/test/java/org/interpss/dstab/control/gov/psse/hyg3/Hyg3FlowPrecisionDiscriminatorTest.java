package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Diagnoses a long-run source-state plateau without changing the governor's arithmetic. */
public class Hyg3FlowPrecisionDiscriminatorTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/hyg3-long-duration-agent-20261104/captures.zip");
    private static final String SHA="0ca02f1095d2a8505158311eac18a9a3b42f674e17476383bad157e975be989b";

    @Test void completeReportedStateVectorsHaveBinary32ObservationPrecision(@TempDir Path staged)throws Exception {
        var fixture=new Hyg3ReferenceFixture(staged,ARCHIVE,SHA);
        for(String tag:new String[]{"m0-standard-h0p00208333333333-t10-a",
                "m1-standard-h0p00208333333333-t10-a"}) {
            var rows=Hyg3StateConformanceTest.rows(fixture.stage(tag).resolve("native.csv"));
            int moving=0;
            double firstFlow=rows.getFirst().get("STATE6");
            for(var row:rows) {
                for(int state=0;state<9;state++) {
                    double reported=row.get("STATE"+state);
                    assertEquals((double)(float)reported,reported,1e-12,
                            tag+" published state "+state+" at "+row.get("time_s"));
                }
                if(row.get("STATE6")!=firstFlow)moving++;
            }
            assertTrue(moving>1000,"Quantization must be observed on a moving state, not only equilibrium");
        }
    }

    @Test void retainedFlowPlateauIsConsistentWithSinglePrecisionIncrements(@TempDir Path staged)throws Exception {
        var fixture=new Hyg3ReferenceFixture(staged,ARCHIVE,SHA);
        for(String tag:new String[]{"m0-standard-h0p00208333333333-t10-a",
                "m1-standard-h0p00416666666667-t10-a",
                "m1-standard-h0p00208333333333-t10-a"}) {
            var rows=Hyg3StateConformanceTest.rows(fixture.stage(tag).resolve("native.csv"));
            double h=tag.contains("h0p004")?1./240.:1./480.;
            var constants=fixture.captureManifest(tag).path("initialized_constants");
            double initialHead=constants.get(27).asDouble(),waterTime=constants.get(29).asDouble();
            assertEquals(1.,initialHead,0.);assertEquals(1.,waterTime,0.);
            int checked=0;double sum=0.;
            for(int i=0;i+1<rows.size();i++) {
                var left=rows.get(i);var right=rows.get(i+1);
                double time=left.get("time_s"),next=right.get("time_s");
                if(time<5.||next>10.||next<=time)continue;
                double flow=left.get("STATE6"),gate=left.get("VAR3");
                double increment=h*(initialHead-Math.pow(flow/gate,2))/waterTime;
                assertEquals(flow,right.get("STATE6"),0.,tag+" source flow plateau");
                assertTrue(increment>0.&&increment<.5*Math.ulp((float)flow),
                        tag+" nonzero hydraulic increment below binary32 half-ULP");
                assertEquals((float)flow,(float)(flow+increment),0.f,
                        tag+" one-step binary32 projection");
                sum+=increment;checked++;
            }
            assertTrue(checked>1000,tag+" retained postfault interval");
            assertTrue(sum>1e-5,tag+" unrounded increments can accumulate materially");
        }
    }
}
