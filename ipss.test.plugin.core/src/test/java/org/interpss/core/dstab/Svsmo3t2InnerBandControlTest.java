package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Complete retained pulse discriminator for entered inner-band controls. */
public class Svsmo3t2InnerBandControlTest extends CorePluginTestSetup {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/svs-inner-band-20261105/captures.zip");
    private static final String SHA="bcb3607dba3de2579846b6e56402eac81c72b54ee1df1cb7e3029f521f8c3ee3";
    private static final String[] RUNS={"r2","r2-b","r10","r10-b"};
    private static final double H=.0005;

    @Test void retainedCompleteSourceControlsDistinguishEnteredParameters() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            List<String[]> lines=new ArrayList<>();
            for(String run:RUNS) {
                var entry=zip.getEntry(run+"/reference.csv");assertNotNull(entry);
                assertTrue(entry.getSize()>0 && entry.getSize()<2_000_000);
                try(var input=zip.getInputStream(entry)) {
                    lines.add(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\\R"));
                }
            }
            for(String[] capture:lines)assertEquals(2006,capture.length);
            assertArrayEquals(lines.get(0),lines.get(1),"Cold baseline repeats");
            var header=Arrays.asList(lines.get(0)[0].split(","));
            assertEquals(header,Arrays.asList(lines.get(2)[0].split(",")));
            int unassigned=header.indexOf("A_VAR_18"),time=header.indexOf("time_s");
            int reference=header.indexOf("A_VAR_6"),sensor=header.indexOf("A_STATE_0"),error=header.indexOf("A_VAR_5");
            assertTrue(unassigned>=0&&time>=0&&reference>=0&&sensor>=0&&error>=0);
            int heldOutsideInner=0,duplicates=0;
            for(int row=1;row<lines.get(0).length;row++) {
                var baseline=lines.get(0)[row].split(",");
                var altered=lines.get(2)[row].split(",");
                var repeat=lines.get(3)[row].split(",");
                assertEquals(header.size(),baseline.length);
                for(int column=0;column<baseline.length;column++) {
                    if(column==unassigned)continue; // Unassigned diagnostic storage is not a model signal.
                    assertEquals(baseline[column],altered[column],"Altered control row "+row+" column "+header.get(column));
                    assertEquals(baseline[column],repeat[column],"Cold repeat row "+row+" column "+header.get(column));
                }
                if(row>1 && baseline[time].equals(lines.get(0)[row-1].split(",")[time]))duplicates++;
                double deviation=Math.abs(Double.parseDouble(baseline[reference])-Double.parseDouble(baseline[sensor]));
                if(Double.parseDouble(baseline[time])>.11 && deviation>.0005 && deviation<.005
                        && Double.parseDouble(baseline[error])==0) heldOutsideInner++;
            }
            assertEquals(2,duplicates,"Retain both event-side duplicates");
            assertTrue(heldOutsideInner>100,"The pulse must distinguish inner from outer deadband");
            var json=new ObjectMapper();
            double[] low=json.readValue(zip.getInputStream(zip.getEntry("r2/con-readbacks.json")),MapData.class).after_start;
            double[] high=json.readValue(zip.getInputStream(zip.getEntry("r10/con-readbacks.json")),MapData.class).after_start;
            assertEquals(2.,low[10],0);assertEquals(10.,high[10],0);
            assertEquals(.01,low[11],1e-8);assertEquals(.5,high[11],0);
        }
    }

    public static final class MapData { public double[] before_start,after_start,after_run; }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothIntegrationMethodsPreserveTheMeasuredProfileInvariant(boolean staggered) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            var baseline=simulate(zip,"r2",staggered);
            var altered=simulate(zip,"r10",staggered);
            assertEquals(2003,baseline.size());assertEquals(baseline.size(),altered.size());
            for(int i=0;i<baseline.size();i++)assertArrayEquals(baseline.get(i),altered.get(i),0.,
                    "Integration="+staggered+" sample="+i);
        }
    }

    private static List<double[]> simulate(ZipFile zip,String profile,boolean staggered) throws Exception {
        Path staged=Files.createTempDirectory(Path.of("target"),"svs-inner-band-");
        for(String filename:List.of("smib.raw","smib.dyr")) {
            var entry=zip.getEntry(profile+"/"+filename);assertNotNull(entry);
            try(var input=zip.getInputStream(entry)){Files.copy(input,staged.resolve(filename));}
        }
        var context=new PSSEMultiFileLoader().loadDStab(staged.resolve("smib.raw").toString(),
                staged.resolve("smib.dyr").toString());
        var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();
        network.setStaticLoadIncludedInYMatrix(true);
        network.setStaticLoadModel(StaticLoadModel.CONST_Z);
        network.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(H);algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuOutputHandler(new StateMonitor());
        var solver=staggered?new StaggeredEulerSolver(algorithm):new DefaultDynamicSimulationSolver(algorithm) {{converge_tol=1e-6;}};
        if(staggered) {
            var shared=(StaggeredEulerSolver)solver;
            shared.setNetworkSolutionTolerance(1e-6);
            shared.setEndpointTimePrecision(StaggeredEulerSolver.EndpointTimePrecision.SINGLE);
        }
        algorithm.setSolver(solver);assertTrue(algorithm.initialization());
        var model=(Svsmo3t2Model)network.getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                .filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
        double schedule=model.getScheduledVoltage();var frames=new ArrayList<double[]>();
        for(int tick=0;tick<=2000;tick++) {
            frames.add(frame(algorithm,model));if(tick==2000)break;
            if(tick==100||tick==200) {
                model.setScheduledVoltage(schedule+(tick==100?.007:0));
                assertTrue(model.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));
                if(staggered)((StaggeredEulerSolver)solver).notifyDiscontinuity();
                assertTrue(solver.networkSolutionStep());frames.add(frame(algorithm,model));
            }
            assertTrue(algorithm.solveDEqnStep(true));
        }
        return frames;
    }

    private static double[] frame(com.interpss.dstab.algo.DynamicSimuAlgorithm algorithm,Svsmo3t2Model model) {
        double[] result=new double[12];
        for(int i=1;i<=3;i++)result[i-1]=algorithm.getNetwork().getBus("Bus"+i).getVoltageMag();
        result[3]=model.getCurrent();result[4]=model.getMvarOutput();result[5]=model.getVoltageReference();
        model.readStaggeredState(result,6);return result;
    }
}
