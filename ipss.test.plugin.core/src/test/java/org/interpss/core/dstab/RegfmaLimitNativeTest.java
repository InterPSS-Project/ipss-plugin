package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.interpss.core.dstab.reference.RegfmaLimitReplayProbe;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Additional limiter profiles; regular endpoints are distinct from retained history/event-post rows. */
public class RegfmaLimitNativeTest {
    @TempDir Path temporary;
    private static final Map<String,String> DIGESTS=Map.of(
        "lower-shunt-coarse","85d74833c60752ad7d391d7a0ffcdc1458fe640d6e451ba324a44872c3fe8b08",
        "lower-shunt-fine","010162592ab690f49d81a880215a654b07901a5e6116240a8c2cf3545545e4ac",
        "pq-voltage-pi-coarse","634b8e426fc72b1b40df46529780757f8cbebaaaac60409ec540562b86a660b0",
        "pq-voltage-pi-fine","50349be613090ad03aad54792709753aed702fa91920994d07264e923c3cfad4");
    // Unchanged established terminal-voltage and P/Q profile envelopes. These
    // are declared before evaluating this profile, not derived from its errors.
    private static final double[] TERMINAL={3e-4,1e-12,1e-12,5e-6,.003,.00065,1e-12,1e-12,7e-6,
        9e-5,.00015,1e-12,1e-12,1e-12,1e-12,.0004,1e-6,4.5e-5,2.4e-5,2.1e-6,4.2e-5,.00023,4.5e-5,6e-6};
    private static final double[] PQ={.00031,4.7e-8,7e-7,9e-6,.003,.0007,.00014,2.3e-5,1e-12,
        .00047,.00047,4.5e-7,3.2e-6,.00038,1.8e-5,.00027,1e-6,.00015,7.7e-5,7e-6,.00012,.0007,.00015,9.5e-6};

    private record Comparison(double[] maxima,double[] bounds,List<String> failures) { }

    @ParameterizedTest @ValueSource(strings={"lower-shunt-coarse","lower-shunt-fine",
        "pq-voltage-pi-coarse","pq-voltage-pi-fine"})
    void matchedMethodMeetsTheUnchangedEnvelopesAtEveryRegularEndpoint(String key) throws Exception {
        var result=compare(key,true);assertTrue(result.failures.isEmpty(),String.join("\n",result.failures));
    }

    @ParameterizedTest @ValueSource(strings={"lower-shunt","pq-voltage-pi"})
    void defaultMethodRetainsCoarseResidualsAndMeetsUnchangedBoundsAtHalfStep(String profile) throws Exception {
        var coarse=compare(profile+"-coarse",false);var fine=compare(profile+"-fine",false);
        assertTrue(fine.failures.isEmpty(),String.join("\n",fine.failures));
        for(int c=0;c<24;c++)if(coarse.maxima[c]>coarse.bounds[c])
            assertTrue(fine.maxima[c]<coarse.maxima[c],"Every coarse out-of-envelope channel must improve: "+c);
        // A passing recurrence/refinement gate does not turn the retained
        // coarse out-of-envelope comparison into trajectory acceptance.
    }

    @ParameterizedTest @ValueSource(strings={"lower-shunt-coarse","lower-shunt-fine",
        "pq-voltage-pi-coarse","pq-voltage-pi-fine"})
    void everyRecordedInputStageMatchesIndependentEquationsAndModifiedEuler(String key) throws Exception {
        Path folder=temporary.resolve(key);Files.createDirectory(folder);
        Files.copy(Path.of("testData/adpter/psse/v33/renewable/regfma1_native.raw"),folder.resolve("network.raw"));
        Files.write(folder.resolve("native.csv"),referenceBytes(key));Files.writeString(folder.resolve("dynamic.dyr"),dynamic(key));
        Path output=Path.of("target/regfma-limits-replays");Files.createDirectories(output);
        // The probe refuses overwrite; a test-local path keeps each invocation
        // independent while the coupled diagnostic exports remain separate.
        Path replay=folder.resolve("replay.csv");RegfmaLimitReplayProbe.main(new String[]{folder.toAbsolutePath().toString(),
            replay.toAbsolutePath().toString(),key.endsWith("fine")?".00025":".0005"});
        Files.copy(replay,output.resolve(key+".csv"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static double[] constants(String key) {
        double[] p={.01,.012,.014,1.5,1.5,.5,.9,-.9,.8,-.8,.02,.03,.01,.1,.1,20,.25,5.86};
        if(key.startsWith("lower")){p[4]=1.1;p[5]=1.075;}else {p[6]=.405;p[7]=.395;p[8]=.3;p[9]=.2;}
        return p;
    }
    private static String dynamic(String key) {
        return "1 'REGFMA1' '1' 1 1 "+String.join(" ",Arrays.stream(constants(key)).mapToObj(Double::toString).toList())
            +" /\n3 'GENCLS' '1' 9999999 0 /\n";
    }

    @ParameterizedTest @CsvSource({"1.075,-.01,0","1.075,.01,.0586",
        "1.1,.01,0","1.1,-.01,-.0586","1.08,-.1,-.586","1.08,.1,.586"})
    void voltageIntegralHasIndependentBoundsAndAllowsInwardRelease(double integral,double error,double expectedRate) throws Exception {
        Path dyr=temporary.resolve("lower-unit.dyr");Files.writeString(dyr,dynamic("lower-shunt-coarse"));
        IpssCorePlugin.init();var context=new PSSEMultiFileLoader().loadDStab(
            "testData/adpter/psse/v33/renewable/regfma1_native.raw",dyr.toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();net.setAllowGenWithoutMach(true);
        alg.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        alg.getAclfAlgorithm().setTolerance(1e-10);assertTrue(alg.getAclfAlgorithm().loadflow());
        alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);alg.setSimuStepSec(.0005);
        alg.setSimuOutputHandler(new StateMonitor());assertTrue(alg.initialization());
        var m=(Regfma1Model)((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        double[] x=new double[9];m.readStaggeredState(x,0);x[4]=integral;m.applyStaggeredState(x,0,.0005);
        double command=m.getPsseVariableSnapshot().get("Voltage command");
        // A prescribed blockwise error is a test input, not a fitted native trajectory.
        m.setReferenceOffsets(0,x[3]+error-command);var before=m.getIntegrationStates();
        assertTrue(m.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
        assertEquals(expectedRate,m.getStateDerivativeSnapshot().rates().get("Voltage Integral"),1e-12);
        assertEquals(before,m.getStateDerivativeSnapshot().evaluationStates());
        assertEquals(Math.max(1.075,Math.min(1.1,integral+.0005*expectedRate)),m.getVoltageIntegral(),1e-12);
        if(integral==1.08)assertEquals(error<0?1.075:1.1,m.getInternalVoltage(),0,
            "Algebraic output saturation must not freeze an interior integral");
    }

    private Comparison compare(String key,boolean shared) throws Exception {
        boolean lower=key.startsWith("lower");double step=key.endsWith("fine")?.00025:.0005;
        List<double[]> reference=reference(key);int steps=(int)Math.round(.3/step);
        assertEquals(steps+5,reference.size());
        double[] p=constants(key);
        Path dyr=temporary.resolve(key+".dyr");
        Files.writeString(dyr,dynamic(key));
        IpssCorePlugin.init();var context=new PSSEMultiFileLoader().loadDStab(
            "testData/adpter/psse/v33/renewable/regfma1_native.raw",dyr.toString());
        var net=context.getDStabilityNet();var alg=context.getDynSimuAlgorithm();
        net.setAllowGenWithoutMach(true);alg.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        alg.getAclfAlgorithm().setTolerance(1e-10);assertTrue(alg.getAclfAlgorithm().loadflow());
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");
        var model=assertInstanceOf(Regfma1Model.class,gen.getDynamicGenDevice());
        alg.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);alg.setSimuStepSec(step);alg.setTotalSimuTimeSec(.3);
        alg.setSimuOutputHandler(new StateMonitor());
        // On a 230-kV/100-MVA base, -500 ohm is -j5 pu, a capacitive shunt.
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,
            new Complex(0,lower?-5:1),null,.05,.05),"LimiterDiscriminator");
        if(shared)alg.setSolver(new PsseStaggeredEulerSolver(alg));assertTrue(alg.initialization());
        List<double[]> actual=new ArrayList<>();
        for(int tick=0;tick<=steps;tick++) {
            assertEquals(tick*step,alg.getSimuTime(),1e-10);model.getOutputObject();var before=model.getNamedStates();
            var values=new ArrayList<Double>();values.addAll(model.getPsseStateSnapshot().values());
            values.addAll(model.getPsseVariableSnapshot().values());
            for(String bus:List.of("Bus1","Bus2","Bus3"))values.add(net.getBus(bus).getVoltageMag());
            values.add(model.getActivePower()*.5);values.add(model.getReactivePower()*.5);
            values.add(net.getBus("Bus1").getVoltageMag());values.add(model.getSpeed()-1);
            double[] row=values.stream().mapToDouble(Double::doubleValue).toArray();assertEquals(24,row.length);
            assertTrue(Arrays.stream(row).allMatch(Double::isFinite));assertEquals(before,model.getNamedStates());
            assertTrue(row[8]>=p[5] && row[8]<=p[4]);actual.add(row);
            if(tick<steps)assertTrue(alg.solveDEqnStep(true));
        }
        if(lower) {
            assertEquals(p[5],actual.stream().mapToDouble(r->r[8]).min().orElseThrow(),1e-12);
            assertTrue(actual.get(steps)[8]>p[5]+1e-5,"Lower integral must release inward");
        } else for(int index:new int[]{1,2,6,7})
            assertTrue(actual.stream().anyMatch(r->Math.abs(r[index])>1e-10),"Every power-limit integral must activate");
        var output=Path.of("target/regfma-limits");Files.createDirectories(output);
        StringBuilder trajectory=new StringBuilder("time_s");
        for(int c=0;c<24;c++)trajectory.append(",channel_").append(c);trajectory.append('\n');
        for(int i=0;i<actual.size();i++) {
            trajectory.append(i*step);for(double v:actual.get(i))trajectory.append(',').append(v);trajectory.append('\n');
        }
        Files.writeString(output.resolve(key+"-"+(shared?"shared":"default")+"-actual.csv"),trajectory);
        StringBuilder csv=new StringBuilder("raw_index,raw_time_s,nominal_time_s,status,max_channel_error\n");
        var seen=new HashSet<Integer>();int history=0,posts=0;double[] maxima=new double[24],times=new double[24];
        var failures=new ArrayList<String>();double[] bounds=new double[24];
        for(int c=0;c<24;c++)bounds[c]=lower?TERMINAL[c]:Math.max(TERMINAL[c],PQ[c]);
        for(int i=0;i<reference.size();i++) {
            double[] raw=reference.get(i);int tick=(int)Math.round(raw[0]/step);String status;double largest=0;
            if(raw[0]<0){history++;status="history-unscored";}
            else if(!seen.add(tick)){posts++;status="instantaneous-event-post-unscored";}
            else {
                status="regular-endpoint";assertEquals(tick*step,raw[0],step/2);
                for(int c=0;c<24;c++) {
                    double error=Math.abs(raw[c+1]-actual.get(tick)[c]);largest=Math.max(largest,error);
                    if(error>maxima[c]){maxima[c]=error;times[c]=raw[0];}
                    double bound=bounds[c];
                    // This slice does not invent half-step bounds. Both steps
                    // must pass the same established family envelope.
                    if(error>bound && failures.size()<24)failures.add("row="+i+" channel="+c+" error="+error+" bound="+bound);
                }
            }
            csv.append(i).append(',').append(raw[0]).append(',').append(tick*step).append(',').append(status).append(',').append(largest).append('\n');
        }
        Files.writeString(output.resolve(key+"-"+(shared?"shared":"default")+".csv"),csv);
        assertEquals(2,history);assertEquals(2,posts);assertEquals(steps+1,seen.size());
        System.out.println("REGFMA limit "+key+" shared="+shared+" maxima="+Arrays.toString(maxima)+" times="+Arrays.toString(times));
        System.out.println("REGFMA limit "+key+" shared="+shared+" withinEstablishedEnvelope="+failures.isEmpty()
            +" instantaneousEventPostAcceptance=false");
        return new Comparison(maxima,bounds,List.copyOf(failures));
    }

    private static List<double[]> reference(String key) throws Exception {
        byte[] bytes=referenceBytes(key);
        var lines=new String(bytes,StandardCharsets.UTF_8).lines().toList();assertEquals(25,lines.get(0).split(",").length);
        var out=new ArrayList<double[]>();for(String line:lines.subList(1,lines.size())) {
            double[] row=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
            assertEquals(25,row.length);assertTrue(Arrays.stream(row).allMatch(Double::isFinite));out.add(row);
        }
        return out;
    }
    private static byte[] referenceBytes(String key) throws Exception {
        byte[] encoded;
        try(var stream=RegfmaLimitNativeTest.class.getResourceAsStream("/regfma-limits/"+key+".b64")) {
            assertNotNull(stream);encoded=Base64.getMimeDecoder().decode(stream.readAllBytes());
        }
        byte[] bytes;try(var gzip=new GZIPInputStream(new ByteArrayInputStream(encoded))){bytes=gzip.readAllBytes();}
        assertEquals(DIGESTS.get(key),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return bytes;
    }
}
