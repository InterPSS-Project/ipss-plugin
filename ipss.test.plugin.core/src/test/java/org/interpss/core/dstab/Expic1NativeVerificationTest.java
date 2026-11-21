package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.RepcaNativeVerification;
import com.interpss.dstab.controller.exc.expic1.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;

/** Full-row seven-state boundary diagnostics for the exact EXPIC1 record. */
public class Expic1NativeVerificationTest {
    private static final Path ROOT=Path.of("testData/private/expic1-smib-v2");
    @BeforeAll static void initialize(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}
    private static Complex voltage(Map<String,Double> row){
        double angle=Math.toRadians(row.get("A1_DEG")),v=row.get("V1");return new Complex(v*Math.cos(angle),v*Math.sin(angle));
    }
    private static double bridge(Expic1Kernel kernel,Map<String,Double> row){
        Complex v=voltage(row),current=new Complex(row.get("P_PU"),row.get("Q_PU")).divide(v).conjugate();
        return kernel.bridgeVoltage(v,current,row.get("XADIFD"));
    }
    @Test void replayAllPublishedStatesAndReportOutputPhase() throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private excitation references unavailable");
        for(String grid:List.of("coarse","fine"))
        for(String mode:List.of("flat","step-up","step-down","fault","dynamic","upper","lower",
                "integral-upper","integral-lower","feedback","saturation","constant-source-wide","bridge-current")) {
            var directory=ROOT.resolve(mode+"-"+grid+"-a");
            var manifest=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
            assertEquals("complete",manifest.get("status").getAsString());
            var c=manifest.getAsJsonObject("settings").getAsJsonArray("constants");double[] parameters=new double[24];
            for(int i=0;i<24;i++)parameters[i]=(double)(float)c.get(i).getAsDouble();
            var kernel=new Expic1Kernel(Expic1Data.fromNativeParameters(parameters));
            var rows=RepcaNativeVerification.rows(Files.readString(directory.resolve("native.csv")));var first=rows.getFirst();
            kernel.initialize(first.get("EFD"),first.get("ECOMP"),bridge(kernel,first),0);
            double reference=kernel.getReference(),dt=manifest.getAsJsonObject("settings").get("step_s").getAsDouble(),
                    step=manifest.getAsJsonObject("settings").get("reference_step").getAsDouble();
            boolean changed=false,restored=false;var previous=first;double[] maxima=new double[8],times=new double[8];
            var events=manifest.getAsJsonArray("events");
            double apply=events.isEmpty()?Double.NaN:events.get(0).getAsJsonObject().get("actual_time_s").getAsDouble();
            double restore=events.size()<2?Double.NaN:events.get(1).getAsJsonObject().get("actual_time_s").getAsDouble();
            for(var row:rows){
                double elapsed=row.get("time_s")-previous.get("time_s");
                if(row!=first&&Math.abs(elapsed)<1e-8) {
                    if(!mode.equals("flat")&&!mode.equals("fault")) {
                        if(!changed&&Math.abs(row.get("time_s")-apply)<1e-8){kernel.setReference(reference+step);changed=true;}
                        if(!restored&&Math.abs(row.get("time_s")-restore)<1e-8){kernel.setReference(reference);restored=true;}
                    }
                    kernel.step(0,1,row.get("ECOMP"),bridge(kernel,row),0);
                } else if(elapsed>1e-8){
                    kernel.step(dt,0,previous.get("ECOMP"),bridge(kernel,previous),0);
                    kernel.step(dt,1,row.get("ECOMP"),bridge(kernel,row),0);
                }
                double[] state=kernel.getNativeStates();
                for(int i=0;i<7;i++){
                    double error=Math.abs(state[i]-row.get("EXC_STATE"+i));
                    if(error>maxima[i]){maxima[i]=error;times[i]=row.get("time_s");}
                }
                double outputError=Math.abs(kernel.output()-row.get("EFD"));
                if(outputError>maxima[7]){maxima[7]=outputError;times[7]=row.get("time_s");}
                previous=row;
            }
            System.out.println("EXPIC1_REPLAY "+mode+"-"+grid+" maxima="+Arrays.toString(maxima)+" times="+Arrays.toString(times));
            for(double value:maxima)assertTrue(Double.isFinite(value));
            // Bound integrated coordinates separately from algebraic event-side
            // observations and machine output phase, which remain diagnostic.
            double[] integratedBounds={grid.equals("coarse")?7e-4:4e-4,
                    mode.startsWith("integral-")?.002:1.6e-4,2e-6,1.5e-5,2.5e-5,5e-7,3e-7};
            boolean[] integrated={parameters[0]>0,true,parameters[5]>0,parameters[7]>0,
                    parameters[16]>0,parameters[10]>0,parameters[10]>0&&parameters[12]>0};
            for(int i=0;i<7;i++)if(integrated[i])assertTrue(maxima[i]<=integratedBounds[i],
                    mode+"-"+grid+" integrated state "+i+" error="+maxima[i]);
        }
    }
    @Test void immutableNativeInputsAndAllChannelsRepeatAcrossColdProcesses()throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private excitation references unavailable");
        for(String mode:List.of("flat","step-up","step-down","fault","dynamic","upper","lower",
                "integral-upper","integral-lower","feedback","saturation","constant-source-wide","bridge-current"))
        for(String grid:List.of("coarse","fine")) {
            Path first=ROOT.resolve(mode+"-"+grid+"-a"),second=ROOT.resolve(mode+"-"+grid+"-b");
            for(Path directory:List.of(first,second)) {
                var manifest=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
                assertEquals("complete",manifest.get("status").getAsString());
                for(var entry:manifest.getAsJsonObject("files").entrySet()) {
                    var digest=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(entry.getKey())));
                    assertEquals(entry.getValue().getAsString(),HexFormat.of().formatHex(digest),directory+"/"+entry.getKey());
                }
                var rows=RepcaNativeVerification.rows(Files.readString(directory.resolve("native.csv")));
                assertEquals(manifest.get("sample_count").getAsInt(),rows.size());
                assertEquals(manifest.get("channel_count").getAsInt()+1,rows.getFirst().size());
            }
            assertEquals(-1,Files.mismatch(first.resolve("native.csv"),second.resolve("native.csv")),mode+"-"+grid);
        }
    }
    @Test void exactNativeRecordsAttachAndInitializeTheirOwnSteadyOperatingPoint() throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private excitation references unavailable");
        for(String mode:List.of("flat","dynamic")){
            var directory=ROOT.resolve(mode+"-coarse-a");var loader=new PSSEMultiFileLoader();
            var context=loader.loadDStab(directory.resolve("network.raw").toString(),directory.resolve("dynamic.dyr").toString());
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
            var exciter=assertInstanceOf(Expic1Exciter.class,machine.getExciter());
            double initialOutput=exciter.getOutput(machine),initialAngle=machine.getAngle();
            for(double rate:exciter.getStateDerivativeSnapshot().rates().values())assertEquals(0,rate,1e-10);
            algorithm.setTotalSimuTimeSec(.1);assertTrue(algorithm.performSimulation());
            assertEquals(initialOutput,exciter.getOutput(machine),1e-10);
            assertEquals(1,machine.getSpeed(),1e-12);assertEquals(initialAngle,machine.getAngle(),1e-10);
            System.out.println("EXPIC1_FLAT "+mode+" output="+exciter.getOutput(machine)+" speed="+machine.getSpeed());
        }
    }
}
