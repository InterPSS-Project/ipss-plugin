package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;

/** Independent machine-only fault boundary; no controller owns the first mismatch. */
public class MachineOnlyBoundaryDiscriminatorTest {
    private static final Path ARCHIVE=Path.of(
            "testData/private/verification-artifacts/genrou-boundary-20261105/captures.zip");
    private static final String SHA="64100f8159ac585a5b10352c15d5fc8b9f7544c05f34b42d367c1fc7f29cfe70";
    record Frame(double time,String kind,double voltage,double q,double psikd,double pmech) { }
    static class Capture {
        final DynamicSimuAlgorithm algorithm;
        final List<Frame> frames=new ArrayList<>();
        boolean enabled;
        Capture(DynamicSimuAlgorithm algorithm){this.algorithm=algorithm;}
        void before(double time){
            if(enabled&&(Math.abs(time-.05)<1e-9||Math.abs(time-.10)<1e-9))
                frames.add(frame(time,"EVENT_PRE"));
        }
        Frame frame(double time,String kind){
            var network=algorithm.getNetwork();var machine=network.getMachine("Bus1-mach1");
            var round=(org.interpss.dstab.mach.IeeeVcRoundRotorMachine)machine;
            var bus=network.getDStabBus("Bus1");var voltage=bus.getVoltage();
            var power=voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate());
            return new Frame(time,kind,voltage.abs(),power.getImaginary(),round.getPsikd(),machine.getPm());
        }
        void grid(String stage){
            if(enabled&&"INITIAL".equals(stage))frames.add(frame(algorithm.getSimuTime(),"GRID"));
        }
    }
    static class Ordinary extends DefaultDynamicSimulationSolver {
        final Capture capture;
        Ordinary(DynamicSimuAlgorithm algorithm,Capture capture){super(algorithm);this.capture=capture;converge_tol=1e-6;}
        @Override public void beforeStep(double time)throws DStabSimuException{capture.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{
            boolean solved=super.networkSolutionStep();
            if(solved)capture.grid(networkSolutionStage);return solved;
        }
    }
    static class Shared extends StaggeredEulerSolver {
        final Capture capture;
        Shared(DynamicSimuAlgorithm algorithm,Capture capture){super(algorithm);this.capture=capture;
            setNetworkSolutionTolerance(1e-6);setReuseNetworkEndpoint(false);}
        @Override public void beforeStep(double time)throws DStabSimuException{capture.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{
            boolean solved=super.networkSolutionStep();if(solved)capture.grid(networkSolutionStage);return solved;
        }
    }
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @Test void firstFaultOnStepSeparatesMachineIntegrationFromControllers(@TempDir Path staging)throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            for(String profile:List.of("fine","quarter")) {
                byte[] first=zip.getInputStream(zip.getEntry(profile+"-a/native.csv")).readAllBytes();
                byte[] repeat=zip.getInputStream(zip.getEntry(profile+"-b/native.csv")).readAllBytes();
                assertArrayEquals(first,repeat,"Cold-repeated complete reference: "+profile);
                var rows=Tiocr1ResetReferenceTest.csv(first);
                Path folder=Files.createDirectory(staging.resolve(profile));
                for(String name:List.of("network.raw","dynamic.dyr"))
                    Files.write(folder.resolve(name),zip.getInputStream(zip.getEntry(profile+"-a/"+name)).readAllBytes());
                double h=profile.equals("fine")?.0005:1./240.;
                for(boolean staggered:List.of(false,true)) {
                    var loader=new PSSEMultiFileLoader();
                    var context=loader.loadDStab(folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
                    assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
                    var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();
                    var unit=network.getMachine("Bus1-mach1");
                    assertFalse(unit.hasGovernor());assertFalse(unit.hasExciter());assertFalse(unit.hasStabilizer());
                    assertTrue(algorithm.getAclfAlgorithm().loadflow());
                    algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
                    algorithm.setSimuStepSec(h);algorithm.setTotalSimuTimeSec(.15);
                    algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
                    var capture=new Capture(algorithm);
                    algorithm.setSolver(staggered?new Shared(algorithm,capture):new Ordinary(algorithm,capture));
                    network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                            SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"fault");
                    assertTrue(algorithm.initialization());capture.enabled=true;
                    while(algorithm.getSimuTime()<.15-h/2)assertTrue(algorithm.solveDEqnStep(true));
                    capture.frames.add(capture.frame(algorithm.getSimuTime(),"FINAL"));
                    Map<String,Double> source=rows.stream().filter(row->Math.abs(row.get("time_s")-(.05+h))<h*.02)
                            .findFirst().orElseThrow();
                    Frame actual=capture.frames.stream().filter(row->Math.abs(row.time()-(.05+h))<h*.02)
                            .findFirst().orElseThrow();
                    double v=Math.abs(actual.voltage()-source.get("V1"));
                    double q=Math.abs(actual.q()-source.get("M1_Q"));
                    double flux=Math.abs(actual.psikd()-source.get("GEN1_STATE2"));
                    double pm=Math.abs(actual.pmech()-source.get("M1_PMECH"));
                    System.out.printf("MACHINE_ONLY %s staggered=%s V=%.9g Q=%.9g flux=%.9g Pm=%.9g%n",
                            profile,staggered,v,q,flux,pm);
                    assertTrue(Double.isFinite(v+q+flux+pm));
                    assertTrue(pm<1e-6,"Fixed mechanical power must agree without a controller");
                    if(staggered) {
                        assertTrue(v<1e-6,"Staggered first fault-on voltage");
                        assertTrue(q<2e-6,"Staggered first fault-on reactive power");
                        assertTrue(flux<1e-6,"Staggered first fault-on machine flux");
                    }
                }
            }
        }
    }
}
